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

import static android.view.WindowInsets.Type.systemOverlays;

import static com.android.systemui.car.wm.scalableui.systemevents.SystemEventConstants.PANEL_TOKEN_ID;
import static com.android.systemui.car.wm.scalableui.systemevents.SystemEventConstants.SYSTEM_TASK_CLOSE_EVENT_ID;
import static com.android.systemui.car.wm.scalableui.systemevents.SystemEventConstants.SYSTEM_TASK_OPEN_EVENT_ID;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ValueAnimator;
import android.graphics.Rect;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import android.view.SurfaceControl;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.android.car.internal.dep.Trace;
import com.android.car.scalableui.manager.StateManager;
import com.android.car.scalableui.model.Event;
import com.android.car.scalableui.model.PanelTransaction;
import com.android.car.scalableui.model.Transition;
import com.android.car.scalableui.model.Variant;
import com.android.car.scalableui.panel.Panel;
import com.android.car.scalableui.panel.PanelPool;
import com.android.systemui.car.wm.scalableui.panel.DecorPanel;
import com.android.systemui.car.wm.scalableui.panel.PanelUtils;
import com.android.systemui.car.wm.scalableui.panel.TaskPanel;
import com.android.wm.shell.automotive.AutoLayoutManager;
import com.android.wm.shell.automotive.AutoSurfaceTransaction;
import com.android.wm.shell.automotive.AutoSurfaceTransactionFactory;
import com.android.wm.shell.automotive.AutoTaskStackController;
import com.android.wm.shell.automotive.AutoTaskStackState;
import com.android.wm.shell.automotive.AutoTaskStackTransaction;
import com.android.wm.shell.dagger.WMSingleton;
import com.android.wm.shell.transition.Transitions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;

import javax.annotation.concurrent.GuardedBy;
import javax.inject.Inject;

/**
 * Manages the state transitions of the UI panels.
 * This class is responsible for creating AutoTaskStackTransaction and queuing up panel animations
 * based on event triggers and then applying visual updates to panels based on their current state.
 */
@WMSingleton
public class TaskPanelTransitionCoordinator {
    private static final String TAG = TaskPanelTransitionCoordinator.class.getName();
    private static final boolean DEBUG = Build.IS_DEBUGGABLE;
    private static final String DECOR_TRANSACTION = "DECOR_TRANSACTION";

    private final AutoTaskStackController mAutoTaskStackController;
    @GuardedBy("mPendingPanelTransactions")
    private final HashMap<IBinder, PanelTransaction> mPendingPanelTransactions = new HashMap<>();
    private AnimatorSet mRunningAnimatorSet = null;
    private final AutoSurfaceTransactionFactory mAutoSurfaceTransactionFactory;
    private final PanelUtils mPanelUtils;
    private final AutoLayoutManager mAutoLayoutManager;
    private IBinder mActiveTransition;

    @Inject
    public TaskPanelTransitionCoordinator(AutoTaskStackController autoTaskStackController,
            AutoSurfaceTransactionFactory autoSurfaceTransactionFactory,
            PanelUtils panelUtils, AutoLayoutManager autoLayoutManager) {
        mAutoTaskStackController = autoTaskStackController;
        mAutoSurfaceTransactionFactory = autoSurfaceTransactionFactory;
        mPanelUtils = panelUtils;
        mAutoLayoutManager = autoLayoutManager;
    }

    /**
     * Starts a panel transition using the provided {@link PanelTransaction} that causes window
     * state change.
     *
     * @param transaction The {@link PanelTransaction} object containing the details of the
     *                    transition.
     */
    public void startTransition(PanelTransaction transaction) {
        synchronized (mPendingPanelTransactions) {
            if (transaction.hasWindowChanges()) {
                IBinder transition = mAutoTaskStackController.startTransition(
                        createAutoTaskStackTransaction(transaction));
                mPendingPanelTransactions.put(transition, transaction);
                playPendingAnimations(transition, null);
            } else {
                updatePanelSurface(transaction);
            }
        }
    }

    private void updatePanelSurface(PanelTransaction panelTransaction) {
        logIfDebuggable("updatePanelSurface: " + panelTransaction);
        AutoSurfaceTransaction autoSurfaceTransaction =
                mAutoSurfaceTransactionFactory.createTransaction(DECOR_TRANSACTION);
        for (Map.Entry<String, Transition> entry : panelTransaction.getPanelTransactionStates()) {
            Panel panel = PanelPool.getInstance().getPanel(
                    p -> p.getPanelId().equals(entry.getKey()));
            if (panel == null) {
                logIfDebuggable("Panel is null for " + entry.getKey());
                continue;
            }
            Transition transition = entry.getValue();
            Variant toVariant = transition.getToVariant();
            if (panel instanceof DecorPanel decorPanel && decorPanel.getAutoDecor() != null) {
                logIfDebuggable("move decorPanel=" + decorPanel.getPanelId() + " to"
                        + toVariant.getBounds() + " layer=" + toVariant.getLayer()
                        + " visible=" + toVariant.isVisible());
                autoSurfaceTransaction.setBounds(decorPanel.getAutoDecor(),
                        toVariant.getBounds());
                autoSurfaceTransaction.setVisibility(decorPanel.getAutoDecor(),
                        toVariant.isVisible());
                autoSurfaceTransaction.setZOrder(decorPanel.getAutoDecor(), toVariant.getLayer());
            } else if (panel instanceof TaskPanel taskPanel) {
                if (taskPanel.getRootStack() == null) {
                    Log.e(TAG, "Root stack is null for " + taskPanel.getPanelId());
                    continue;
                }
                logIfDebuggable(
                        "Move taskPanel=" + taskPanel.getPanelId() + " to"
                                + toVariant.getBounds());
                int taskId = taskPanel.getRootStack().getRootTaskInfo().taskId;
                autoSurfaceTransaction.setTaskSurfacePosition(taskId,
                        toVariant.getBounds().left,
                        toVariant.getBounds().top);
                autoSurfaceTransaction.setTaskSurfaceCornerRadius(taskId,
                        toVariant.getCornerRadius());
            } else {
                Log.e(TAG, "Invalid panel " + panel);
            }
        }
        autoSurfaceTransaction.apply();
    }

    /**
     * This is a medium-term workaround to resolve the transition conflicts for cts purpose.
     *
     * <p>Transition conflicts arise when multiple intents occur rapidly, leading to
     * {@code handleRequest} only processing the initial intent. Subsequent intents are handled
     * directly by the Window Manager without invoking the {@code handleRequest} callback. Due to
     * missing task info, window state corrections are limited to scenarios where the launch root
     * task has changed. This change is interpreted as either a task open or close event, determined
     * by the visibility change.
     * TODO(b/397527431) : handle transition conflicts correctly after b/388067743.
     */
    public void maybeResolveConflict(Map<Integer, AutoTaskStackState> changedTaskStacks,
            IBinder transition) {
        PanelTransaction transaction = null;
        synchronized (mPendingPanelTransactions) {
            transaction = mPendingPanelTransactions.get(transition);
        }

        for (Map.Entry<Integer, AutoTaskStackState> entry : changedTaskStacks.entrySet()) {
            int autoTaskStackId = entry.getKey();
            TaskPanel tp = mPanelUtils.getTaskPanel(taskPanel ->
                    taskPanel.getRootStack() != null
                            && taskPanel.getRootStack().getId() == autoTaskStackId);
            if (tp == null || !tp.isLaunchRoot()) {
                logIfDebuggable("Panel is null or not launch root" + tp);
                continue;
            }

            // If there is no recorded pending transaction for the changed rootTask, treat it as
            // conflict.
            AutoTaskStackState changedState = entry.getValue();
            boolean findConflict = transaction == null
                    || !isEqual(changedState,
                    transaction.getPanelTransactionState(tp.getPanelId()));
            if (findConflict) {
                Log.e(TAG, "Transition conflicts found on launch root task - " + changedState);
                Event event = new Event.Builder(
                        changedState.getChildrenTasksVisible() ? SYSTEM_TASK_OPEN_EVENT_ID
                                : SYSTEM_TASK_CLOSE_EVENT_ID)
                        .addToken(PANEL_TOKEN_ID, tp.getPanelId())
                        .build();
                PanelTransaction panelTransaction = StateManager.handleEvent(event);
                mAutoTaskStackController.startTransition(
                        createAutoTaskStackTransaction(panelTransaction));
            }
        }
    }

    private boolean isEqual(@NonNull AutoTaskStackState changedState,
            @Nullable Transition panelTransition) {
        if (panelTransition == null) {
            return false;
        }
        Variant toVariant = panelTransition.getToVariant();
        return changedState.getChildrenTasksVisible() == toVariant.isVisible()
                && changedState.getLayer() == toVariant.getLayer()
                && changedState.getBounds().equals(toVariant.getBounds());
    }

    /**
     * Create a AutoTaskStackTransaction for a given PanelTransaction and set the appropriate
     * pending animators.
     */
    AutoTaskStackTransaction createAutoTaskStackTransaction(IBinder transition,
            PanelTransaction panelTransaction) {
        AutoTaskStackTransaction autoTaskStackTransaction = createAutoTaskStackTransaction(
                panelTransaction);

        synchronized (mPendingPanelTransactions) {
            mPendingPanelTransactions.put(transition, panelTransaction);
        }
        return autoTaskStackTransaction;
    }

    /**
     * Plays the animation in the pending list.
     *
     * @return true if any animations were started
     */
    boolean playPendingAnimations(IBinder transition,
            @Nullable Transitions.TransitionFinishCallback finishCallback) {
        PanelTransaction panelTransaction;
        synchronized (mPendingPanelTransactions) {
            panelTransaction = mPendingPanelTransactions.get(transition);
        }
        if (panelTransaction == null || panelTransaction.getAnimators().isEmpty()) {
            logIfDebuggable("No animations for transition " + transition);
            return false;
        }
        logIfDebuggable("playPendingAnimations: " + panelTransaction.getAnimators().size());
        Trace.beginSection(TAG + "#playPendingAnimations");

        // TODO(b/409121871): resolve potential glitch after stopping previous animation.
        stopRunningAnimations(transition);

        mRunningAnimatorSet = new AnimatorSet();
        mActiveTransition = transition;


        long totalDuration = Long.MIN_VALUE;
        List<Animator> animationToRun = new ArrayList<>();
        for (Map.Entry<String, Animator> entry : panelTransaction.getAnimators()) {
            Animator animator = entry.getValue();
            logIfDebuggable(entry.getKey() + "duration for animator" + animator.getTotalDuration());
            totalDuration = Math.max(totalDuration, animator.getTotalDuration());
            animationToRun.add(animator);
        }

        totalDuration = Math.max(0, totalDuration);

        logIfDebuggable("total duration" + totalDuration);
        animationToRun.add(createSurfaceAnimator(totalDuration, panelTransaction.getAnimators()));
        mRunningAnimatorSet.playTogether(animationToRun);
        mRunningAnimatorSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                Trace.beginSection(TAG + "#onAnimationStart");
                super.onAnimationStart(animation);
                if (panelTransaction.getAnimationStartCallbackRunnable() != null) {
                    panelTransaction.getAnimationStartCallbackRunnable().run();
                }
                Trace.endSection();
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                Trace.beginSection(TAG + "#onAnimationEnd");
                super.onAnimationEnd(animation);
                logIfDebuggable("Animation set finished " + finishCallback);

                if (finishCallback != null) {
                    logIfDebuggable("Finish the transition");
                    finishCallback.onTransitionFinished(/* wct= */ null);
                }

                // Enforce the surface state for decor panel.
                AutoSurfaceTransaction autoSurfaceTransaction =
                        mAutoSurfaceTransactionFactory.createTransaction(DECOR_TRANSACTION);
                for (Map.Entry<String, Transition> entry :
                        panelTransaction.getPanelTransactionStates()) {
                    DecorPanel decorPanel = mPanelUtils.getDecorPanel(
                            dp -> dp.getPanelId().equals(entry.getKey()));
                    updateDecorPanelSurface(decorPanel, autoSurfaceTransaction);
                }
                autoSurfaceTransaction.apply();

                synchronized (mPendingPanelTransactions) {
                    mPendingPanelTransactions.remove(transition);
                    mActiveTransition = null;
                }
                if (panelTransaction.getAnimationEndCallbackRunnable() != null) {
                    panelTransaction.getAnimationEndCallbackRunnable().run();
                }
                Trace.endSection();
            }
        });
        mRunningAnimatorSet.start();
        Trace.endSection();
        return true;
    }

    /**
     * Stops any currently running animation if it belongs to a transition different from the
     * provided one.If an animation is running and its associated transition does not match the
     * incoming{@code transition} token, the animation set is immediately advanced to its end
     * state.
     *
     * @param transition The {@link IBinder} token for the incoming transition request. Used to
     *                   check if the currently running animation is for a different transition.
     */
    void stopRunningAnimations(@NonNull IBinder transition) {
        logIfDebuggable("stopRunningAnimationsIfNeed " + transition);
        if (isAnimationRunning() && transition != mActiveTransition) {
            logIfDebuggable("stopRunningAnimations: has running animatorSet "
                    + mRunningAnimatorSet.getCurrentPlayTime() + ", incoming transition = "
                    + transition + ", active transition = " + mActiveTransition);
            mRunningAnimatorSet.end();
        }
    }

    private static void logIfDebuggable(String msg) {
        if (DEBUG) {
            Log.d(TAG, msg);
        }
    }

    @VisibleForTesting
    boolean isAnimationRunning() {
        return mRunningAnimatorSet != null && mRunningAnimatorSet.isRunning();
    }

    @VisibleForTesting
    PanelTransaction getPendingPanelTransaction(IBinder transition) {
        synchronized (mPendingPanelTransactions) {
            return mPendingPanelTransactions.get(transition);
        }
    }

    private AutoTaskStackTransaction createAutoTaskStackTransaction(
            PanelTransaction panelTransaction) {
        AutoTaskStackTransaction autoTaskStackTransaction = new AutoTaskStackTransaction();

        for (Map.Entry<String, Transition> entry :
                panelTransaction.getPanelTransactionStates()) {
            Transition transition = entry.getValue();
            Variant toVariant = transition.getToVariant();
            TaskPanel taskPanel = mPanelUtils.getTaskPanel(
                    p -> p.getRootStack() != null && p.getPanelId().equals(entry.getKey()));
            if (taskPanel == null) {
                continue;
            }
            AutoTaskStackState autoTaskStackState = new AutoTaskStackState(
                    toVariant.getBounds(),
                    toVariant.isVisible(),
                    toVariant.getLayer());
            autoTaskStackTransaction.setTaskStackState(taskPanel.getRootStack().getId(),
                    autoTaskStackState);

            if (toVariant.isVisible() && taskPanel.isRootTaskEmpty()
                    && mPanelUtils.isUserUnlocked()) {
                taskPanel.setBaseIntent(autoTaskStackTransaction);
                logIfDebuggable("Set base intent for " + taskPanel.getPanelId());
            }
        }

        return autoTaskStackTransaction;
    }

    private ValueAnimator createSurfaceAnimator(long duration,
            @NonNull Set<Map.Entry<String, Animator>> animators) {
        ValueAnimator surfaceAnimator = ValueAnimator.ofFloat(0, 1f);
        surfaceAnimator.setDuration(duration);
        surfaceAnimator.addUpdateListener(animation -> {
            Trace.beginSection(TAG + "#updatePanelSurface");
            logIfDebuggable("Surface animation progress " + animation.getAnimatedFraction());
            AutoSurfaceTransaction autoSurfaceTransaction =
                    mAutoSurfaceTransactionFactory.createTransaction(DECOR_TRANSACTION);

            SurfaceControl.Transaction tx = new SurfaceControl.Transaction();
            for (Map.Entry<String, Animator> entry : animators) {
                String id = entry.getKey();
                Panel panel = PanelPool.getInstance().getPanel(p -> p.getPanelId().equals(id));
                if (panel instanceof TaskPanel taskPanel) {
                    updateTaskPanelSurface(taskPanel, tx, autoSurfaceTransaction);
                } else if (panel instanceof DecorPanel decorPanel) {
                    updateDecorPanelSurface(decorPanel, autoSurfaceTransaction);
                }
            }
            //TODO(b/404959846): migrate to autoSurfaceTransaction here once api is added.
            tx.apply();
            autoSurfaceTransaction.apply();
            Trace.endSection();
        });
        return surfaceAnimator;
    }

    private void updateDecorPanelSurface(@Nullable DecorPanel decorPanel,
            @NonNull AutoSurfaceTransaction autoSurfaceTransaction) {
        if (decorPanel == null || decorPanel.getAutoDecor() == null) {
            Log.e(TAG, "AutoDecor is null for " + decorPanel);
            return;
        }
        logIfDebuggable("updateDecorPanelSurface:" + decorPanel);
        autoSurfaceTransaction.setBounds(decorPanel.getAutoDecor(), decorPanel.getBounds());
        autoSurfaceTransaction.setVisibility(decorPanel.getAutoDecor(), decorPanel.isVisible());
        autoSurfaceTransaction.setZOrder(decorPanel.getAutoDecor(), decorPanel.getLayer());
    }

    private void updateTaskPanelSurface(TaskPanel taskPanel, SurfaceControl.Transaction tx,
            AutoSurfaceTransaction autoSurfaceTransaction) {
        SurfaceControl sc = taskPanel.getLeash();
        if (sc == null) {
            Log.e(TAG, "leash is null for " + taskPanel);
            return;
        }

        if (taskPanel.getRootStack() == null) {
            Log.e(TAG, "RootStack is null for " + taskPanel.getPanelId());
            return;
        }
        logIfDebuggable("updatePanelSurface:" + taskPanel);
        int taskId = taskPanel.getRootStack().getRootTaskInfo().taskId;
        autoSurfaceTransaction.setTaskSurfaceCrop(taskId,
                new Rect(0, 0, taskPanel.getBounds().width(),
                        taskPanel.getBounds().height()));
        autoSurfaceTransaction.setTaskSurfacePosition(taskId, taskPanel.getX1(), taskPanel.getY1());
        autoSurfaceTransaction.setTaskSurfaceCornerRadius(taskId, taskPanel.getCornerRadius());

        //TODO(b/404959846): move following to AutoSurfaceTransaction
        tx.setVisibility(sc, taskPanel.isVisible());
        tx.setAlpha(sc, taskPanel.getAlpha());
        tx.setLayer(sc, taskPanel.getLayer());
        Rect[] panelInsets = mPanelUtils.getTaskPanelInsets(taskPanel);
        IntStream.range(0, panelInsets.length).forEach(sideIndex -> {
            mAutoLayoutManager.addOrUpdateInsets(taskPanel.getRootStack(), sideIndex,
                    systemOverlays(), panelInsets[sideIndex]);
        });
        tx.setPosition(sc, taskPanel.getBounds().left, taskPanel.getBounds().top);
        tx.setWindowCrop(sc, taskPanel.getBounds().width(), taskPanel.getBounds().height());
        Rect insets = taskPanel.getInsets().toRect();
        mAutoLayoutManager.addOrUpdateInsets(taskPanel.getRootStack(),
                /* left */ 0, systemOverlays(),
                new Rect(0, 0, insets.left, taskPanel.getBounds().bottom));
        mAutoLayoutManager.addOrUpdateInsets(taskPanel.getRootStack(),
                /* top */ 1, systemOverlays(),
                new Rect(0, 0, taskPanel.getBounds().right, insets.top));
        mAutoLayoutManager.addOrUpdateInsets(taskPanel.getRootStack(),
                /* right */ 2, systemOverlays(),
                new Rect(
                        taskPanel.getBounds().right - insets.right,
                        0,
                        taskPanel.getBounds().right,
                        taskPanel.getBounds().bottom));
        mAutoLayoutManager.addOrUpdateInsets(taskPanel.getRootStack(),
                /* bottom */ 3, systemOverlays(),
                new Rect(
                        0,
                        taskPanel.getBounds().bottom - insets.bottom,
                        taskPanel.getBounds().right,
                        taskPanel.getBounds().bottom));
        tx.setCornerRadius(sc, taskPanel.getCornerRadius());
        tx.apply();
    }
}
