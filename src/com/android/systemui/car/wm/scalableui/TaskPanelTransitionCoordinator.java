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

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ValueAnimator;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import android.view.SurfaceControl;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.android.car.internal.dep.Trace;
import com.android.car.scalableui.manager.Event;
import com.android.car.scalableui.manager.PanelTransaction;
import com.android.car.scalableui.manager.StateManager;
import com.android.car.scalableui.model.Transition;
import com.android.car.scalableui.model.Variant;
import com.android.systemui.car.wm.scalableui.panel.TaskPanel;
import com.android.systemui.car.wm.scalableui.panel.TaskPanelPool;
import com.android.wm.shell.automotive.AutoTaskStackController;
import com.android.wm.shell.automotive.AutoTaskStackState;
import com.android.wm.shell.automotive.AutoTaskStackTransaction;
import com.android.wm.shell.dagger.WMSingleton;
import com.android.wm.shell.transition.Transitions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    private final AutoTaskStackController mAutoTaskStackController;
    @GuardedBy("mPendingPanelTransactions")
    private final HashMap<IBinder, PanelTransaction> mPendingPanelTransactions = new HashMap<>();
    private AnimatorSet mRunningAnimatorSet = null;

    @Inject
    public TaskPanelTransitionCoordinator(AutoTaskStackController autoTaskStackController) {
        mAutoTaskStackController = autoTaskStackController;
    }

    /**
     * Start a new transition for a given {@link PanelTransaction}
     */
    public void startTransition(PanelTransaction transaction) {
        synchronized (mPendingPanelTransactions) {
            IBinder transition = mAutoTaskStackController.startTransition(
                    createAutoTaskStackTransaction(transaction));
            mPendingPanelTransactions.put(transition, transaction);
        }
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
            TaskPanel tp = TaskPanelPool.getTaskPanel(taskPanel ->
                    taskPanel.getRootStack() != null
                            && taskPanel.getRootStack().getId() == autoTaskStackId);
            if (tp == null || !tp.isLaunchRoot()) {
                if (DEBUG) {
                    Log.d(TAG, "Panel is null or not launch root" + tp);
                }
                continue;
            }

            // If there is no recorded pending transaction for the changed rootTask, treat it as
            // conflict.
            AutoTaskStackState changedState = entry.getValue();
            boolean findConflict = transaction == null
                    || !isEqual(changedState, transaction.getPanelTransactionState(tp.getId()));
            if (findConflict) {
                Log.e(TAG, "Transition conflicts found on launch root task - " + changedState);
                Event event = new Event(
                        changedState.getChildrenTasksVisible() ? "_System_TaskOpenEvent"
                                : "_System_TaskCloseEvent").addToken("panelId", tp.getId());
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
            if (DEBUG) {
                Log.d(TAG, "No animations for transition " + transition);
            }
            return false;
        }
        if (DEBUG) {
            Log.d(TAG, "playPendingAnimations: " + panelTransaction.getAnimators().size());
        }
        Trace.beginSection(TAG + "#playPendingAnimations");
        stopRunningAnimations();

        mRunningAnimatorSet = new AnimatorSet();

        List<Animator> animationToRun = new ArrayList<>();
        for (Map.Entry<String, Animator> entry : panelTransaction.getAnimators()) {
            String id = entry.getKey();
            Animator animator = entry.getValue();
            TaskPanel taskPanel = TaskPanelPool.getTaskPanel(tp -> tp.getId().equals(id));
            ValueAnimator surfaceAnimator = createSurfaceAnimator(animator.getTotalDuration(),
                    taskPanel);
            animationToRun.add(animator);
            animationToRun.add(surfaceAnimator);
        }
        mRunningAnimatorSet.playTogether(animationToRun);
        mRunningAnimatorSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                Trace.beginSection(TAG + "#onAnimationStart");
                super.onAnimationStart(animation);
                Trace.endSection();
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                Trace.beginSection(TAG + "#onAnimationEnd");
                super.onAnimationEnd(animation);
                if (DEBUG) {
                    Log.d(TAG, "Animation set finished " + finishCallback);
                }
                if (finishCallback != null) {
                    if (DEBUG) {
                        Log.d(TAG, "Finish the transition");
                    }
                    finishCallback.onTransitionFinished(/* wct= */ null);
                }
                synchronized (mPendingPanelTransactions) {
                    mPendingPanelTransactions.remove(transition);
                }
                Trace.endSection();
            }
        });
        mRunningAnimatorSet.start();
        Trace.endSection();
        return true;
    }

    /**
     * Ends any running animations associated with this instance.
     */
    void stopRunningAnimations() {
        if (isAnimationRunning()) {
            if (DEBUG) {
                Log.d(TAG, "stopRunningAnimations: has running animatorSet "
                        + mRunningAnimatorSet.getCurrentPlayTime());
            }
            mRunningAnimatorSet.end();
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
            TaskPanel taskPanel = TaskPanelPool.getTaskPanel(
                    p -> p.getRootStack() != null && p.getId().equals(entry.getKey()));
            if (taskPanel == null) {
                continue;
            }
            AutoTaskStackState autoTaskStackState = new AutoTaskStackState(
                    toVariant.getBounds(),
                    toVariant.isVisible(),
                    toVariant.getLayer());
            autoTaskStackTransaction.setTaskStackState(taskPanel.getRootStack().getId(),
                    autoTaskStackState);
        }

        return autoTaskStackTransaction;
    }

    private ValueAnimator createSurfaceAnimator(long duration, @Nullable TaskPanel taskPanel) {
        Trace.beginSection(TAG + "#createSurfaceAnimator");
        ValueAnimator surfaceAnimator = ValueAnimator.ofFloat(0, 1f);
        surfaceAnimator.setDuration(duration);
        surfaceAnimator.addUpdateListener(animation -> {
            if (taskPanel != null) {
                updatePanelSurface(taskPanel);
            }
        });
        Trace.endSection();
        return surfaceAnimator;
    }

    private void updatePanelSurface(TaskPanel taskPanel) {
        Trace.beginSection(TAG + "#updatePanelSurface");

        SurfaceControl.Transaction tx = new SurfaceControl.Transaction();
        SurfaceControl sc = taskPanel.getLeash();
        if (sc == null) {
            Log.e(TAG, "leash is null for " + taskPanel);
            return;
        }

        if (DEBUG) {
            Log.d(TAG, "With transaction id " + tx.getId() + " with panel" + taskPanel.getId()
                    + ", bounds="
                    + taskPanel.getBounds());
        }

        tx.setVisibility(sc, taskPanel.isVisible());
        tx.setAlpha(sc, taskPanel.getAlpha());
        tx.setLayer(sc, taskPanel.getLayer());
        tx.setPosition(sc, taskPanel.getBounds().left, taskPanel.getBounds().top);
        tx.setWindowCrop(sc, taskPanel.getBounds().width(), taskPanel.getBounds().height());
        tx.apply();
        Trace.endSection();
    }
}
