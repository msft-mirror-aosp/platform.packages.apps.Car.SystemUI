/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.systemui.car.wm.scalableui;

import static android.app.WindowConfiguration.ACTIVITY_TYPE_HOME;
import static android.view.WindowManager.TRANSIT_FLAG_AVOID_MOVE_TO_FRONT;

import static com.android.systemui.car.wm.scalableui.systemevents.SystemEventConstants.EMPTY_EVENT_ID;
import static com.android.systemui.car.wm.scalableui.systemevents.SystemEventConstants.SYSTEM_HOME_EVENT_ID;
import static com.android.systemui.car.wm.scalableui.systemevents.SystemEventConstants.SYSTEM_TASK_CLOSE_EVENT_ID;
import static com.android.systemui.car.wm.scalableui.systemevents.SystemEventConstants.SYSTEM_TASK_OPEN_EVENT_ID;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.ArraySet;
import android.util.Log;
import android.view.SurfaceControl;
import android.window.TransitionInfo;
import android.window.TransitionRequestInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.android.car.internal.dep.Trace;
import com.android.car.scalableui.model.Event;
import com.android.car.scalableui.model.PanelTransaction;
import com.android.car.scalableui.panel.Panel;
import com.android.systemui.R;
import com.android.systemui.car.flags.Flag;
import com.android.systemui.car.flags.FlagManager;
import com.android.systemui.car.wm.CarWMUserHelper;
import com.android.systemui.car.wm.scalableui.panel.PanelUtils;
import com.android.systemui.car.wm.scalableui.panel.TaskPanel;
import com.android.systemui.car.wm.scalableui.panel.TaskPanelInfoRepository;
import com.android.wm.shell.automotive.AutoLayoutManager;
import com.android.wm.shell.automotive.AutoTaskStackController;
import com.android.wm.shell.automotive.AutoTaskStackState;
import com.android.wm.shell.automotive.AutoTaskStackTransaction;
import com.android.wm.shell.automotive.AutoTaskStackTransitionHandlerDelegate;
import com.android.wm.shell.shared.TransitionUtil;
import com.android.wm.shell.transition.Transitions;

import java.util.Map;
import java.util.Set;

import javax.inject.Inject;

/**
 * Delegate implementation for handling auto task stack transitions using {@link Panel}.
 */
public class PanelAutoTaskStackTransitionHandlerDelegate implements
        AutoTaskStackTransitionHandlerDelegate {
    private static final String TAG =
            PanelAutoTaskStackTransitionHandlerDelegate.class.getSimpleName();

    private static final Event EMPTY_EVENT = new Event.Builder(EMPTY_EVENT_ID).build();
    private static final boolean DEBUG = Build.IS_DEBUGGABLE;

    private final AutoTaskStackController mAutoTaskStackController;
    private final PanelTransitionCoordinator mPanelTransitionCoordinator;
    private final Context mContext;
    private final PanelUtils mPanelUtils;
    private final CarWMUserHelper mUserHelper;
    private final TaskPanelInfoRepository mPanelInfoRepository;
    private final AutoLayoutManager mAutoLayoutManager;
    private final FlagManager mFlagManager;
    private final Set<ComponentName> mIgnoredActivities = new ArraySet<>();

    @Inject
    public PanelAutoTaskStackTransitionHandlerDelegate(
            Context context,
            AutoTaskStackController autoTaskStackController,
            PanelTransitionCoordinator panelTransitionCoordinator,
            PanelUtils panelUtils,
            CarWMUserHelper userHelper,
            TaskPanelInfoRepository panelInfoRepository,
            AutoLayoutManager autoLayoutManager,
            FlagManager flagManager
    ) {
        mAutoTaskStackController = autoTaskStackController;
        mPanelTransitionCoordinator = panelTransitionCoordinator;
        mContext = context;
        mPanelUtils = panelUtils;
        mUserHelper = userHelper;
        mPanelInfoRepository = panelInfoRepository;
        mAutoLayoutManager = autoLayoutManager;
        mFlagManager = flagManager;

        String[] componentNameStrings = mContext.getResources().getStringArray(
                R.array.config_ignoredEventActivities);
        for (int i = componentNameStrings.length - 1; i >= 0; i--) {
            mIgnoredActivities.add(
                    ComponentName.unflattenFromString(componentNameStrings[i]));
        }
    }

    /**
     * Init the {@link PanelAutoTaskStackTransitionHandlerDelegate}.
     */
    public void init() {
        if (mFlagManager.isEnabled(Flag.ScalableUIEnabled)) {
            Log.i(TAG, "ScalableUI is enabled");
            mAutoTaskStackController.setAutoTransitionHandlerDelegate(this);
        }
    }

    @Nullable
    @Override
    public AutoTaskStackTransaction handleRequest(@NonNull IBinder transition,
            @NonNull TransitionRequestInfo request) {
        Trace.beginSection(TAG + "#handleRequest");
        if (DEBUG) {
            Log.d(TAG, "handleRequest: " + request);
        }

        Event event = calculateEvent(request);
        PanelTransaction panelTransaction = EventDispatcher.getTransaction(event);
        AutoTaskStackTransaction wct =
                mPanelTransitionCoordinator.createAutoTaskStackTransaction(transition,
                        panelTransaction, event);
        mPanelTransitionCoordinator.resetUnpreparedDecorPanel(panelTransaction);
        if (DEBUG) {
            Log.d(TAG, "handleRequest: COMPLETED " + wct);
        }
        Trace.endSection();
        return wct;
    }

    private boolean shouldHandleByPanels(@NonNull TransitionRequestInfo request) {
        if (request.getTriggerTask() == null) {
            return false;
        }
        ComponentName component = mPanelUtils.getTaskComponentName(request.getTriggerTask());
        if (mIgnoredActivities.contains(component)) {
            return false;
        }
        return mPanelUtils.handles(request.getTriggerTask().parentTaskId)
                || request.getTriggerTask().topActivityType == ACTIVITY_TYPE_HOME;
    }

    @Override
    public boolean startAnimation(@NonNull IBinder transition,
            @NonNull Map<Integer, AutoTaskStackState> changedTaskStacks,
            @NonNull TransitionInfo info,
            @NonNull SurfaceControl.Transaction startTransaction,
            @NonNull SurfaceControl.Transaction finishTransaction,
            @NonNull Transitions.TransitionFinishCallback finishCallback) {
        if (DEBUG) {
            Log.d(TAG, "startAnimation INFO = " + info
                    + ", changedTaskStacks=" + changedTaskStacks
                    + ", start transaction=" + startTransaction.getId()
                    + ", finishTransaction=" + finishTransaction.getId());
        }

        mPanelTransitionCoordinator.reconcileAutoTaskStackState(transition, changedTaskStacks,
                info);
        mPanelInfoRepository.maybeNotifyTopTaskOnPanelChanged();

        Trace.beginSection(TAG + "#startAnimation");

        mPanelTransitionCoordinator.calculateStartTransaction(startTransaction, info);
        // Its expected for the auto transition handler delegate to apply startTransaction for now.
        // TODO(b/421966313) Think about applying this in car-wm-shell instead.
        startTransaction.apply();

        boolean animationStarted = mPanelTransitionCoordinator.playPendingAnimations(transition,
                finishCallback, finishTransaction, info);
        Trace.endSection();
        return animationStarted;
    }

    @VisibleForTesting
    Event calculateEvent(TransitionRequestInfo request) {
        if (!shouldHandleByPanels(request)) {
            return EMPTY_EVENT;
        }

        if (request.getTriggerTask().baseIntent.getCategories() != null
                && request.getTriggerTask().baseIntent.getCategories().contains(
                Intent.CATEGORY_HOME)) {
            ComponentName component = request.getTriggerTask().baseActivity;
            String packageString = component != null ? component.getPackageName() : null;
            // Multiple SUW activities have home as categories. Panels should treat them the same.
            Event.Builder homeEventBuilder = new Event.Builder(SYSTEM_HOME_EVENT_ID)
                    .setPackageName(packageString)
                    .addApplicableDisplays(
                            mUserHelper.getDisplayIdsForUser(request.getTriggerTask().userId));
            return homeEventBuilder.build();
        }

        if ((request.getFlags() & TRANSIT_FLAG_AVOID_MOVE_TO_FRONT)
                == TRANSIT_FLAG_AVOID_MOVE_TO_FRONT) {
            if (DEBUG) {
                Log.d(TAG, "Launching activity to the background, no panel action needed.");
            }
            return EMPTY_EVENT;
        }

        if (!TransitionUtil.isClosingType(request.getType())
                && !TransitionUtil.isOpeningType(request.getType())) {
            Log.e(TAG, "Unknown transition type " + request.getType());
            return EMPTY_EVENT;
        }

        ComponentName component = mPanelUtils.getTaskComponentName(request.getTriggerTask());
        if (DEBUG) {
            Log.d(TAG, "Transition type=" + request.getType()
                    + " using component=" + component);
        }

        String componentString = component != null ? component.flattenToString() : null;
        TaskPanel panel = mPanelUtils.getTaskPanel(
                tp -> tp.getRootTaskId() == request.getTriggerTask().parentTaskId);
        if (panel == null) {
            // There is no panel ready to handle this event
            // TODO(b/392694590): determine if/how this case should be handled
            Log.e(TAG, "No panel present to handle component " + component);
            return EMPTY_EVENT;
        }
        String panelId = panel.getPanelId();
        String eventName = TransitionUtil.isClosingType(request.getType())
                ? SYSTEM_TASK_CLOSE_EVENT_ID : SYSTEM_TASK_OPEN_EVENT_ID;
        Event.Builder builder = new Event.Builder(eventName).setPanelId(panelId);
        builder.addApplicableDisplays(
                mUserHelper.getDisplayIdsForUser(request.getTriggerTask().userId));
        if (componentString != null) {
            builder.setComponentName(componentString);
        }
        return builder.build();
    }

    @Override
    public void onTransitionConsumed(@NonNull IBinder transition,
            @NonNull Map<Integer, AutoTaskStackState> changedTaskStacks, boolean aborted,
            @Nullable SurfaceControl.Transaction finishTransaction) {
        if (DEBUG) {
            Log.d(TAG, "onTransitionConsumed=" + aborted + ", transition=" + transition
                    + ", changedTaskStacks" + changedTaskStacks);
        }
        Trace.beginSection(TAG + "#onTransitionConsumed");
        boolean stopped = mPanelTransitionCoordinator.stopRunningAnimations(transition);
        if (!stopped && aborted) {
            // If the transition was aborted and the animation was never run, this transition likely
            // had no shell-related changes. Run the animations now to apply non-shell changes.
            mPanelTransitionCoordinator.playPendingAnimations(transition);
        }
        Trace.endSection();
    }

    @Override
    public void mergeAnimation(@NonNull IBinder transition,
            @NonNull Map<Integer, AutoTaskStackState> changedTaskStacks,
            @NonNull TransitionInfo info, @NonNull SurfaceControl.Transaction t,
            @NonNull IBinder mergeTarget,
            @NonNull Transitions.TransitionFinishCallback finishCallback) {
        if (DEBUG) {
            Log.d(TAG, "mergeAnimation " + transition);
        }
        Trace.beginSection(TAG + "#mergeAnimation");
        mPanelTransitionCoordinator.mergeAnimation(transition, mergeTarget);
        Trace.endSection();
    }
}
