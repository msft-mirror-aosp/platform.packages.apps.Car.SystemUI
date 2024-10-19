/*
 * Copyright (C) 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.systemui.car.wm.displayarea

import android.annotation.AnyThread
import android.app.ActivityManager.RunningTaskInfo
import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.graphics.Rect
import android.os.IBinder
import android.util.Slog
import android.view.SurfaceControl
import android.view.SurfaceControl.Transaction
import android.view.WindowManager
import android.view.WindowManager.TRANSIT_CHANGE
import android.view.WindowManager.TRANSIT_OPEN
import android.window.TransitionInfo
import android.window.TransitionRequestInfo
import android.window.WindowContainerToken
import android.window.WindowContainerTransaction
import com.android.systemui.car.Flags.daviewBasedWindowing
import com.android.wm.shell.ShellTaskOrganizer
import com.android.wm.shell.common.ShellExecutor
import com.android.wm.shell.dagger.WMSingleton
import com.android.wm.shell.shared.TransitionUtil
import com.android.wm.shell.shared.annotations.ShellMainThread
import com.android.wm.shell.taskview.TaskViewTransitions
import com.android.wm.shell.transition.Transitions
import com.android.wm.shell.transition.Transitions.TransitionFinishCallback
import com.google.common.annotations.VisibleForTesting
import javax.inject.Inject

/**
 * This class handles the extra transitions work pertaining to shell transitions when using
 * [DaView]. This class only works when shell transitions are enabled.
 */
@WMSingleton
class DaViewTransitions @Inject constructor(
    private val shellTaskOrganizer: ShellTaskOrganizer,
    private val transitions: Transitions,
    @ShellMainThread private val shellMainThread: ShellExecutor,
    private val taskViewTransitions: TaskViewTransitions,
    private val context: Context,
) : Transitions.TransitionHandler {

    // TODO(b/370075926): think about synchronization here as this state might be getting changed
    //  in the shell main thread
    val daCurrState = mutableMapOf<DaView, DaState>()

    private val pendingTransitions = ArrayList<PendingTransition>()
    private var animationHandler: AnimationHandler? = null

    data class DaState(
        /**
         * Signifies if the tasks in the Da should be invisible. Please note that hiding/showing
         * the surface of corresponding [DaView] is still taken care by the animation handler.
         */
        var visible: Boolean = false,
        var bounds: Rect = Rect(),
    )

    // Represents a DaView related operation
    data class DaTransaction(
        /**
         * Represents the state of the [DaView]s that are part of this transaction. It maps the
         * [DaView.id] to its state.
         */
        var daStates: Map<Long, DaState> = mutableMapOf(),

        /**
         * The [DaView.id] of the daview that needs to be focused as part of this transaction. This
         * is useful to ensure that focus ends up at a reasonable place after a transition
         * involving multiple DaViews is completed.
         */
        var focusedDaId: Long? = null,
    )

    /**
     * This interface should be used by the window which hosts the DaViews to hook into transitions
     * happening on the core side.
     * It can be used to animate multiple DaViews when an activity is coming up inside a
     * {@link DaView}
     */
    interface AnimationHandler {
        /**
         * This method is called whenever a task gets started (adb shell, user, an app launch etc)
         * on a DA. This is an opportunity to add more work to this transition and then animate
         * later as part of playAnimation().
         *
         * The returned [DaTransaction] is merged with the change happening in WM.
         * If the  [DaTransaction] doesn't have any participant, this transition will be handled by
         * the default handler and [AnimationHandler.playAnimation] won't be called for that.
         *
         * Note: The returned participants must contain the passed DaView with visibility:true,
         * otherwise it can lead to unexpected state and compliance issues.
         */
        @ShellMainThread
        fun handleOpenTransitionOnDa(
            daView: DaView,
            triggerTaskInfo: RunningTaskInfo,
            wct: WindowContainerTransaction
        ): DaTransaction

        /**
         * Similar to [AnimationHandler.handleOpenTransitionOnDa] but gets called when a
         * display changes its dimensions.
         */
        @ShellMainThread
        fun handleDisplayChangeTransition(
            displayId: Int,
            newSize: Rect
        ): DaTransaction

        /**
         * The penultimate method to play the animation. By this time, the required visibility and
         * bounds change has already been applied to WM. Before this method is called,
         * DaViewTransitions will ensure that the transition surfaces are reparented correctly to
         * the participating DAViews.
         * The handler can animate the DAView participants (using view animations) as per the state
         * passed and trigger the finish callback which notifies the WM that the transition is
         * done.
         */
        @ShellMainThread
        fun playAnimation(
            resolvedDaTransaction: DaTransaction,
            finishCallback: TransitionFinishCallback
        )
    }

    sealed class ChangeType {
        data object None : ChangeType()
        data object Hide : ChangeType()
        data object Show : ChangeType()
        data object Bounds : ChangeType()

        fun logChange(daView: DaView) {
            when (this) {
                Hide -> Slog.d(TAG, "Hiding DA: $daView")
                Show -> Slog.d(TAG, "Showing DA: $daView")
                Bounds -> Slog.d(TAG, "Changing DA: $daView")
                None -> {} // No logging for NONE
            }
        }
    }

    private class DaViewChange(
        var type: ChangeType = ChangeType.None,
        var snapshot: SurfaceControl? = null
    )

    init {
        if (!daviewBasedWindowing()) {
            throw IllegalAccessException("DaView feature not available")
        }
        transitions.addHandler(this)
        sInstance = this
    }

    /**
     * Instantly apply this transaction using the {@link ShellTaskOrganizer}. Should only be
     * used for updating insets.
     */
    fun instantApplyViaTaskOrganizer(wct: WindowContainerTransaction) {
        shellTaskOrganizer.applyTransaction(wct)
    }

    /**
     * Instantly apply this transaction without any custom animation.
     */
    fun instantApplyViaShellTransit(wct: WindowContainerTransaction) {
        transitions.startTransition(TRANSIT_CHANGE, wct, null)
    }

    private fun findPending(claimed: IBinder): PendingTransition? {
        for (pending in pendingTransitions) {
            if (pending.isClaimed !== claimed) continue
            return pending
        }
        return null
    }

    fun setAnimationHandler(handler: AnimationHandler?) {
        animationHandler = handler
    }

    @AnyThread
    fun add(daView: DaView) {
        shellMainThread.execute {
            daViews[daView.id] = daView
            daCurrState[daView] = DaState()
        }
    }

    @AnyThread
    fun remove(daView: DaView) {
        shellMainThread.execute {
            daViews.remove(daView.id)
            daCurrState.remove(daView)
        }
    }

    /**
     * Requests to animate the given DaViews to the specified visibility and bounds. It should be
     * noted that this will send the request to WM but the real playing of the animation should
     * be done as part of {@link AnimationHandler#playAnimation()}.
     *
     * Clients can also set the focus to the desired DaView as part of this transition.
     */
    @AnyThread
    fun startTransaction(daTransaction: DaTransaction) {
        shellMainThread.execute {
            val requestedDaStates = daTransaction.daStates
                    .filter { (key, _) ->
                        when {
                            daViews[key] != null -> true
                            else -> {
                                Slog.w(TAG, "$key is not known to DaViewTransitions")
                                false
                            }
                        }
                    }
                    .mapKeys { (key, _) -> daViews[key]!! }

            val wct = WindowContainerTransaction()
            val diffedRequestedDaViewStates = calculateWctForAnimationDiff(
                requestedDaStates,
                wct
            )
            if (DBG) {
                Slog.d(TAG, "requested da view states = $diffedRequestedDaViewStates")
            }
            if (daTransaction.focusedDaId != null) {
                if (daViews[daTransaction.focusedDaId] != null) {
                    val toBeFocusedDa = daViews[daTransaction.focusedDaId]!!
                    wct.reorder(toBeFocusedDa.daInfo.token, true, true)
                } else {
                    Slog.w(TAG, "DaView not found for ${daTransaction.focusedDaId}")
                }
            }

            pendingTransitions.add(
                PendingTransition(
                    TRANSIT_OPEN, // to signify opening of the DaHideActivity
                    wct,
                    diffedRequestedDaViewStates,
                )
            )
            startNextTransition()
        }
    }

    // The visibility and all will be calculated as part of this
    // Use the same for hide/show/change bounds
    fun calculateWctForAnimationDiff(
        requestedDaStates: Map<DaView, DaState>,
        wct: WindowContainerTransaction
    ): Map<DaView, DaState> {
        val newStates = mutableMapOf<DaView, DaState>()
        requestedDaStates
            .filter { (daView, newReqState) ->
                when {
                    daCurrState[daView] != null -> true
                    else -> {
                        Slog.w(TAG, "$daView is not known to DaViewTransitions")
                        false
                    }
                }
            }
            .forEach { (daView, newReqState) ->
                when {
                    daCurrState[daView]!!.visible && !newReqState.visible -> {
                        // Being hidden
                        prepareHideDaWct(wct, daView, newReqState)
                        newStates[daView] = newReqState
                    }

                    !daCurrState[daView]!!.visible && newReqState.visible -> {
                        // Being shown
                    wct.setBounds(daView.daInfo.token, newReqState.bounds)
                    findAndRemoveDaHideActivity(daView, wct)
                    newStates[daView] = newReqState
                }

                daCurrState[daView]!!.bounds != newReqState.bounds -> {
                    // Changing bounds
                    prepareChangeBoundsWct(wct, daView, daCurrState[daView]!!, newReqState)
                    newStates[daView] = newReqState
                }
                // no changes; doesn't need to be animated
            }
        }
        return newStates
    }

    private fun prepareHideDaWct(
        wct: WindowContainerTransaction,
        daView: DaView,
        newState: DaState
    ) {
        var options = ActivityOptions.makeBasic()
            .setPendingIntentBackgroundActivityStartMode(
                ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS
            )
            .apply {
                this.launchTaskDisplayAreaFeatureId = daView.launchTaskDisplayAreaFeatureId
            }

        var intent = Intent(context, DaHideActivity::class.java)
        intent.setFlags(FLAG_ACTIVITY_NEW_TASK)
        var pendingIntent = PendingIntent.getActivity(
            context,
            /* requestCode= */
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        wct.setBounds(daView.daInfo.token, newState.bounds)
        wct.sendPendingIntent(pendingIntent, intent, options.toBundle())
    }

    private fun prepareChangeBoundsWct(
        wct: WindowContainerTransaction,
        daView: DaView,
        prevState: DaState,
        newReqState: DaState
    ) {
        wct.setBounds(daView.daInfo.token, newReqState.bounds)

        if (DBG) {
            val sizingUp =
                (prevState.bounds.width() == newReqState.bounds.width() &&
                        prevState.bounds.height() < newReqState.bounds.height()) ||
                        (
                                prevState.bounds.width() < newReqState.bounds.width() &&
                                        prevState.bounds.height() == newReqState.bounds.height()
                                ) ||
                        (
                                prevState.bounds.width() < newReqState.bounds.width() &&
                                        prevState.bounds.height() < newReqState.bounds.height()
                                )
            Slog.d(TAG, if (sizingUp) "Sizing up $daView" else "Sizing down $daView")
        }
    }

    fun startNextTransition() {
        if (pendingTransitions.isEmpty()) return
        val pending: PendingTransition = pendingTransitions[0]
        if (pending.isClaimed != null) {
            // Wait for this to start animating.
            return
        }
        pending.isClaimed = transitions.startTransition(pending.mType, pending.wct, this)
    }

    private fun getDaViewFromDisplayAreaToken(token: WindowContainerToken?): DaView? {
        val displayArea = daViews.values.stream().filter {
            it.daInfo.token == token
        }.findAny()
        if (displayArea.isEmpty) {
            return null
        }
        return displayArea.get()
    }

    private fun getDaView(taskInfo: RunningTaskInfo): DaView? = daViews.values.find {
        it.launchTaskDisplayAreaFeatureId == taskInfo.displayAreaFeatureId &&
                it.display.displayId == taskInfo.displayId
    }

    private fun isHideActivity(taskInfo: RunningTaskInfo): Boolean {
        return taskInfo.topActivity == HIDE_ACTIVITY_NAME
    }

    override fun handleRequest(
        transition: IBinder,
        request: TransitionRequestInfo
    ): WindowContainerTransaction? {
        if (DBG) {
            Slog.d(TAG, "handle request, type=${request.type}")
        }
        if (request.displayChange != null && request.displayChange!!.endAbsBounds != null) {
            return handleRequestForDisplayChange(transition, request)
        }

        var triggerTask = request.triggerTask ?: run { return@handleRequest null }

        if (DBG) {
            Slog.d(
                TAG,
                "trigger task feature id = ${triggerTask.displayAreaFeatureId}, " +
                        "type=${request.type}"
            )
        }

        // Note: A DaHideActivity is always started as part of a transition from the handler,
        // so it will never be caught here
        if (TransitionUtil.isOpeningType(request.type)) {
            val daView = getDaView(triggerTask)
            if (daView == null) {
                if (DBG) {
                    Slog.d(TAG, "DA not found")
                }
                return null
            }
            if (!daCurrState.containsKey(daView)) {
                if (DBG) {
                    Slog.d(TAG, "DA state not found")
                }
                return null
            }
            return handleRequestForOpenTransitionOnDa(daView, transition, request)
        } else {
            if (DBG) {
                Slog.d(
                    TAG,
                    "current event either not opening event or an event from blank activity"
                )
            }
        }
        return null
    }

    private fun handleRequestForDisplayChange(
        transition: IBinder,
        request: TransitionRequestInfo
    ): WindowContainerTransaction? {
        var wct: WindowContainerTransaction? = null
        val participants =
                animationHandler?.handleDisplayChangeTransition(
                        request.displayChange!!.displayId,
                        request.displayChange!!.endAbsBounds!!
                )?.daStates?.mapKeys { (key, _) -> daViews[key]!! } ?: mapOf()

        if (participants.isEmpty()) {
            Slog.e(
                    TAG,
                    "No participants in the DA transition, can lead to " +
                            "inconsistent state"
            )
            return null
        }

        wct = wct ?: WindowContainerTransaction()
        var participantsAsPerStateDiff = calculateWctForAnimationDiff(participants, wct)
        val pending = PendingTransition(
            request.type,
            wct,
            participantsAsPerStateDiff,
        )
        pending.isClaimed = transition
        pendingTransitions.add(pending)
        return wct
    }

    private fun handleRequestForOpenTransitionOnDa(
        daView: DaView,
        transition: IBinder,
        request: TransitionRequestInfo
    ): WindowContainerTransaction? {
        var wct: WindowContainerTransaction? = null
        var participantsAsPerStateDiff = mapOf<DaView, DaState>()

        // Even though daHideActivity is nohistory, it still needs to be manually removed here
        // because the newly opened activity might be translucent which would make the
        // DaHideActivity be visible in paused state otherwise; which is not desired.
        wct = findAndRemoveDaHideActivity(daView, wct)
        if (!pendingTransitions.isEmpty() &&
            pendingTransitions.get(0).requestedStates.containsKey(daView) &&
            pendingTransitions.get(0).requestedStates.get(daView)!!.visible == true
        ) {
            // This means it will become visible eventually and hence skip visibility
            if (DBG) {
                Slog.d(TAG, "DA is already requested to be visible and pending animation")
            }
        } else {
            if (DBG) Slog.d(TAG, "try to show the da ${daView.id}")
            wct = wct ?: WindowContainerTransaction()
            val participants =
                animationHandler!!.handleOpenTransitionOnDa(
                    daView,
                    request.triggerTask!!,
                    wct
                ).daStates.mapKeys { (key, _) -> daViews[key]!! }
            if (participants.isEmpty()) {
                Slog.e(
                    TAG,
                    "No participants in the DA transition, can lead to " +
                            "inconsistent state"
                )
                // set wct back to null as this should be handled by the default handler in
                // shell
                wct = null
            } else {
                participantsAsPerStateDiff = calculateWctForAnimationDiff(participants, wct)
                if (participantsAsPerStateDiff.isEmpty()) {
                    wct = null
                }
            }
        }
        if (wct == null) {
            // Should be handled by default handler in shell
            return null
        }
        val pending = PendingTransition(
            request.type,
            wct
                .reorder(request.triggerTask!!.token, true, true),
            participantsAsPerStateDiff,
        )
        pending.isClaimed = transition
        pendingTransitions.add(pending)
        return wct
    }

    private fun findAndRemoveDaHideActivity(
        daView: DaView,
        inputWct: WindowContainerTransaction?
    ): WindowContainerTransaction? {
        var tasks = shellTaskOrganizer.getRunningTasks()
        if (daView.display == null) {
            if (DBG) {
                Slog.d(
                    TAG,
                    "daView.display is null, cannot find and remove the hide " +
                            "activity"
                )
            }
        }
        val daHideTasks =
            tasks.filter {
                it.displayAreaFeatureId == daView.launchTaskDisplayAreaFeatureId &&
                        it.displayId == daView.display.displayId &&
                        it.topActivity == HIDE_ACTIVITY_NAME
                // TODO: Think about handling the home task
            }
        if (daHideTasks.isEmpty()) {
            return inputWct
        }
        val wct = inputWct ?: WindowContainerTransaction()
        for (daHideTask in daHideTasks) {
            wct.removeTask(daHideTask.token)
        }
        return wct
    }

    private fun reSyncDaLeashesToView() {
        // consider this an opportunity to restore the DA surfaces because even if this is a
        // not known transition, it could still involve known DAs which reparent their surfaces.
        val tr = Transaction()
        for (daView in daViews.values) {
            if (daView.surfaceControl == null) {
                continue
            }
            daView.resyncLeashToView(tr)
        }
        tr.apply()
    }

    private fun logChanges(daViewChanges: Map<DaView, DaViewChange>) {
        for ((daView, daViewChange) in daViewChanges) {
            daViewChange.type.logChange(daView)
        }
    }

    override fun startAnimation(
        transition: IBinder,
        info: TransitionInfo,
        startTransaction: Transaction,
        finishTransaction: Transaction,
        finishCallback: TransitionFinishCallback
    ): Boolean {
        if (DBG) Slog.d(TAG, "  changes = " + info.changes)
        val pending: PendingTransition? = findPending(transition)
        if (pending != null) {
            pendingTransitions.remove(pending)
        }
        if (pending == null) {
            // TODO: ideally, based on the info.changes, a new transaction should be created and also
            // routed via client which should eventually result into a new transition.
            // This should be done so that client gets a chance to act on these missed changes.
            Slog.e(TAG, "Found a non-DA related transition")
            reSyncDaLeashesToView()
            return false
        }

        if (pending.isInstant) {
            if (DBG) Slog.d(TAG, "Playing a special instant transition")
            startTransaction.apply()
            finishCallback.onTransitionFinished(null)
            startNextTransition()
            return true
        }

        val daViewChanges = calculateDaViewChangesFromTransition(
            info,
            pending,
            startTransaction,
            finishTransaction
        )
        if (DBG) logChanges(daViewChanges)

        configureTaskLeashesAsPerDaChange(
            info,
            pending,
            startTransaction,
            daViewChanges
        )
        if (pending.requestedStates.isEmpty() || animationHandler == null) {
            startNextTransition()
            return false
        }
        startTransaction.apply()
        animationHandler?.playAnimation(
            DaTransaction(daStates = pending.requestedStates.mapKeys { (key, _) -> key.id }),
            {
                shellMainThread.execute {
                    daCurrState.putAll(pending.requestedStates)
                    finishCallback.onTransitionFinished(null)
                    startNextTransition()
                }
            }
        )
        return true
    }

    private fun calculateDaViewChangesFromTransition(
        info: TransitionInfo,
        pending: PendingTransition,
        startTransaction: Transaction,
        finishTransaction: Transaction
    ): Map<DaView, DaViewChange> {
        val viewChanges = mutableMapOf<DaView, DaViewChange>()
        for (chg in info.changes) {
            var daView = getDaViewFromDisplayAreaToken(chg.container)
            if (daView != null) {
                // It means that the change being processed is a display area level change
                // which will have the snapshot.
                if (chg.snapshot != null) {
                    viewChanges.getOrPut(daView) { DaViewChange() }.snapshot = chg.snapshot!!
                }
                continue
            }

            if (chg.taskInfo == null) {
                continue
            }
            Slog.d(TAG, "------- ${chg.mode} change ${chg.taskInfo!!.topActivity} ")
            // The change being processed is a task level change

            daView = getDaView(chg.taskInfo!!)
            if (daView == null) {
                Slog.e(TAG, "The da being changed isn't known to DaViewTransitions")
                continue
            }

            // Regardless of being in the requested state or not, resync the leashes to view to be
            // on the safe side
            daView.resyncLeashToView(startTransaction)
            daView.resyncLeashToView(finishTransaction)

            if (!pending.requestedStates.contains(daView)) {
                Slog.e(TAG, "The da being changed isn't part of pending.mDas")
                startTransaction.reparent(chg.leash, daView.surfaceControl)
                    .setPosition(chg.leash, 0f, 0f)
                    .setAlpha(chg.leash, 1f)
                continue
            }

            var changeType = viewChanges.getOrDefault(daView, DaViewChange()).type
            if (TransitionUtil.isOpeningType(chg.mode) &&
                HIDE_ACTIVITY_NAME == chg.taskInfo?.topActivity) {
                if (daCurrState.containsKey(daView) && daCurrState[daView]!!.visible == false) {
                    Slog.e(TAG, "The da being hidden, is already hidden")
                    continue
                }
                changeType = ChangeType.Hide
            } else if (
                (TransitionUtil.isClosingType(chg.mode) &&
                        HIDE_ACTIVITY_NAME == chg.taskInfo?.topActivity) ||
                (TransitionUtil.isOpeningType(chg.mode) &&
                        HIDE_ACTIVITY_NAME != chg.taskInfo?.topActivity)
            ) {
                if (daCurrState.containsKey(daView) && daCurrState[daView]!!.visible == true) {
                    Slog.e(TAG, "The da being shown, is already shown")
                    continue
                }
                changeType = ChangeType.Show
            } else {
                if (daCurrState.containsKey(daView) &&
                    daCurrState[daView]!!.bounds == pending.requestedStates[daView]!!.bounds) {
                    Slog.e(TAG, "The da being changed, already has the same bounds")
                    continue
                }
                if (changeType != ChangeType.Show && changeType != ChangeType.Hide) {
                    // A task inside a display area which is being shown or hidden can have a bounds
                    // change as well. Prefer treating the DisplayArea change as SHOW or HIDE
                    // respectively instead of a more generic CHANGE.
                    changeType = ChangeType.Bounds
                }
            }

            viewChanges.getOrPut(daView) { DaViewChange() }.type = changeType
        }

        return viewChanges
    }

    private fun configureTaskLeashesAsPerDaChange(
        info: TransitionInfo,
        pending: PendingTransition,
        startTransaction: Transaction,
        viewChanges: Map<DaView, DaViewChange>
    ) {
        // Attach the snapshots for hiding or changing DaViews
        for ((daView, chg) in viewChanges) {
            if (chg.type == ChangeType.Hide || chg.type == ChangeType.Bounds) {
                if (chg.snapshot != null) {
                    startTransaction.reparent(chg.snapshot!!, daView.surfaceControl)
                }
            }
        }

        // Determine leash visibility and placement for each task level change
        for (chg in info.changes) {
            if (chg.taskInfo == null) continue

            val daView = getDaView(chg.taskInfo!!)
            if (daView == null) {
                Slog.e(TAG, "The da being changed isn't known to DaViewTransitions")
                continue
            }
            val daViewChg = viewChanges[daView]
            if (daViewChg == null) {
                Slog.e(TAG, "The da being change isn't known. $daView")
                continue
            }

            if (!pending.requestedStates.containsKey(daView)) {
                Slog.e(TAG, "The da being changed isn't part of pending.mDas")
                continue
            }

            if (isHideActivity(chg.taskInfo!!)) {
                Slog.e(TAG, "Disregard the change from blank activity as its leash not needed")
                continue
            }

            // TODO(b/357635714), revisit this once the window's surface is stable during da
            //  transition.
            val shouldTaskLeashBeVisible = when (daViewChg.type) {
                ChangeType.Show -> TransitionUtil.isOpeningType(chg.mode)
                ChangeType.Hide -> TransitionUtil.isClosingType(chg.mode) &&
                        daViewChg.snapshot == null
                ChangeType.Bounds -> daViewChg.snapshot == null
                else -> false
            }

            startTransaction.reparent(chg.leash, daView.surfaceControl)
                .apply {
                    if (taskViewTransitions.isTaskViewTask(chg.taskInfo) &&
                        shouldTaskLeashBeVisible) {
                        val daBounds = daCurrState[daView]!!.bounds
                        val taskBounds = chg.taskInfo!!.configuration.windowConfiguration!!.bounds
                        taskBounds.offset(daBounds.left, daBounds.right)
                        setPosition(
                            chg.leash,
                            taskBounds.left.toFloat(),
                            taskBounds.bottom.toFloat()
                        )
                    } else {
                        setPosition(chg.leash, 0f, 0f)
                    }
                }
                .setAlpha(chg.leash, if (shouldTaskLeashBeVisible) 1f else 0f)
        }
    }

    override fun onTransitionConsumed(
        transition: IBinder,
        aborted: Boolean,
        finishTransaction: Transaction?
    ) {
        Slog.d(TAG, "onTransitionConsumed, aborted=$aborted")
        if (!aborted) return
        val pending = findPending(transition) ?: return
        pendingTransitions.remove(pending)
        // Probably means that the UI should adjust as per the last (daCurrState) state.
        // Something should be done but needs more thought.
        // For now just update the local state with what was requested.
        daCurrState.putAll(pending.requestedStates)
        startNextTransition()
    }

    companion object {
        private val TAG: String = DaViewTransitions::class.java.simpleName
        private val DBG = true
        private val HIDE_ACTIVITY_NAME =
            ComponentName("com.android.systemui", DaHideActivity::class.java.name)
        private val daViews = mutableMapOf<Long, DaView>()

        var sInstance: DaViewTransitions? = null
    }

    @VisibleForTesting
    internal class PendingTransition(
        @field:WindowManager.TransitionType @param:WindowManager.TransitionType val mType: Int,
        val wct: WindowContainerTransaction,
        val requestedStates: Map<DaView, DaState> = mutableMapOf<DaView, DaState>(),
        val isInstant: Boolean = false,
    ) {
        var isClaimed: IBinder? = null
    }
}
