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

import android.animation.Animator;
import android.content.Context;
import android.graphics.Rect;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import android.view.SurfaceControl;
import android.window.TransitionInfo;
import android.window.TransitionRequestInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.car.internal.dep.Trace;
import com.android.car.scalableui.manager.Event;
import com.android.car.scalableui.manager.PanelTransaction;
import com.android.car.scalableui.manager.StateManager;
import com.android.car.scalableui.model.Transition;
import com.android.car.scalableui.model.Variant;
import com.android.car.scalableui.panel.Panel;
import com.android.car.scalableui.panel.PanelPool;
import com.android.systemui.R;
import com.android.systemui.car.wm.scalableui.panel.TaskPanel;
import com.android.systemui.car.wm.scalableui.panel.TaskPanelPool;
import com.android.wm.shell.automotive.AutoTaskStackController;
import com.android.wm.shell.automotive.AutoTaskStackState;
import com.android.wm.shell.automotive.AutoTaskStackTransaction;
import com.android.wm.shell.automotive.AutoTaskStackTransitionHandlerDelegate;
import com.android.wm.shell.shared.TransitionUtil;
import com.android.wm.shell.transition.Transitions;

import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;

/**
 * Delegate implementation for handling auto task stack transitions using {@link Panel}.
 */
public class PanelAutoTaskStackTransitionHandlerDelegate implements
        AutoTaskStackTransitionHandlerDelegate {
    private static final String TAG =
            PanelAutoTaskStackTransitionHandlerDelegate.class.getSimpleName();

    private static final String EMPTY_EVENT_ID = "empty_event";
    private static final Event EMPTY_EVENT = new Event(EMPTY_EVENT_ID);
    private static final boolean DEBUG = Build.IS_DEBUGGABLE;

    private final AutoTaskStackController mAutoTaskStackController;
    private final TaskPanelAnimationRunner mTaskPanelAnimationRunner;
    private final HashMap<String, Animator> mPendingAnimators;
    private final Context mContext;

    @Inject
    public PanelAutoTaskStackTransitionHandlerDelegate(
            Context context,
            AutoTaskStackController autoTaskStackController,
            TaskPanelAnimationRunner taskPanelAnimationRunner) {
        mAutoTaskStackController = autoTaskStackController;
        mTaskPanelAnimationRunner = taskPanelAnimationRunner;
        mPendingAnimators = new HashMap<>();
        mContext = context;
    }

    /**
     * Init the {@link PanelAutoTaskStackTransitionHandlerDelegate}.
     */
    public void init() {
        if (mContext.getResources().getBoolean(R.bool.config_enableScalableUI)) {
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

        if (shouldHandleByPanels(request)) {
            Event event = calculateEvent(request);
            PanelTransaction panelTransaction = StateManager.handleEvent(event);
            AutoTaskStackTransaction wct = getAutoTaskStackTransaction(panelTransaction);
            if (DEBUG) {
                Log.d(TAG, "handleRequest: COMPLETED " + wct);
            }
            Trace.endSection();
            return wct;
        }
        Trace.endSection();
        return null;
    }

    private AutoTaskStackTransaction getAutoTaskStackTransaction(
            PanelTransaction panelTransaction) {
        mPendingAnimators.clear();
        AutoTaskStackTransaction wct = new AutoTaskStackTransaction();

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
            wct.setTaskStackState(taskPanel.getRootStack().getId(), autoTaskStackState);
        }
        for (Map.Entry<String, Animator> entry : panelTransaction.getAnimators()) {
            //TODO(b/391726254): use a HashMap from IBinder to PanelTransaction.
            mPendingAnimators.put(entry.getKey(), entry.getValue());
        }

        return wct;
    }

    private boolean shouldHandleByPanels(@NonNull TransitionRequestInfo request) {
        if (request.getTriggerTask() == null) {
            return false;
        }
        return TransitionUtil.isOpeningType(request.getType())
                && (TaskPanelPool.handles(request.getTriggerTask().parentTaskId)
                || request.getTriggerTask().topActivityType == ACTIVITY_TYPE_HOME);
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
                    + ", changedTaskStacks" + changedTaskStacks
                    + ", start transaction" + startTransaction.getId()
                    + ", finishTransaction" + finishTransaction.getId()
                    + ", animation count=" + mPendingAnimators.size());
        }

        Trace.beginSection(TAG + "#startAnimation");

        calculateTransaction(startTransaction, info, /* isFinish= */ false);
        calculateTransaction(finishTransaction, info, /* isFinish= */ true);
        startTransaction.apply();

        if (!mPendingAnimators.isEmpty()) {
            mTaskPanelAnimationRunner.playPendingAnimations(mPendingAnimators, finishCallback);
            Trace.endSection();
            return true;
        } else {
            Trace.endSection();
            return false;
        }
    }

    private void calculateTransaction(SurfaceControl.Transaction transaction,
            @NonNull TransitionInfo info, boolean isFinish) {
        SurfaceControl leash = null;
        Rect pos = null;
        for (TransitionInfo.Change change : info.getChanges()) {
            if (change.getTaskInfo() == null) {
                continue;
            }
            TaskPanel taskPanel = TaskPanelPool.getTaskPanel(
                    tp -> tp.getRootTaskId() == change.getTaskInfo().taskId);
            if (taskPanel == null) {
                continue;
            }

            leash = change.getLeash();
            if (isFinish) {
                pos = change.getEndAbsBounds();
            } else {
                pos = change.getStartAbsBounds();
            }
            transaction.setPosition(leash, pos.left, pos.top);
            taskPanel.setLeash(leash);

            transaction.setLayer(leash, taskPanel.getLayer());
        }
    }

    private Event calculateEvent(TransitionRequestInfo request) {
        // TODO(b/383910785): find a way to use config to describe following logic.
        if (request.getTriggerTask() == null) {
            return EMPTY_EVENT;
        } else if (TransitionUtil.isClosingType(request.getType())) {
            // For assistant closing
            return EMPTY_EVENT;
        } else if (isTriggered(request, /* identifier= */ "carlauncher.AppGridActivity")) {
            // make these blocking calls and return a wct from here
            return new Event("open_app_grid_drawer_event");
        } else if (isTriggered(request, /* identifier= */ "activity.AutoAssistantActivity")) {
            // activity.AutoAssistantActivity
            return new Event("open_assistant_event");
        } else if (isTriggered(request, /* identifier= */ "google.android.maps.MapsActivity")) {
            return EMPTY_EVENT;
        } else if (isTriggered(request, /* identifier= */ "com.android.systemui")) {
            return EMPTY_EVENT;
        } else if (isTriggered(request, /* identifier= */ "StubHome")) {
            Panel panel = PanelPool.getInstance().getPanel("widget_bar_panel");
            Log.d(TAG, "calculate home state " + panel);
            return panel.isVisible() ? new Event("home_event") : new Event("home_2_event");
        } else {
            return new Event("open_app_drawer_event", request.getTriggerTask());
        }
    }

    private boolean isTriggered(TransitionRequestInfo request, String identifier) {
        return request.getTriggerTask() != null
                && request.getTriggerTask().baseActivity != null
                && request.getTriggerTask().baseActivity.toString().contains(identifier);
    }

    @Override
    public void onTransitionConsumed(@NonNull IBinder transition,
            @NonNull Map<Integer, AutoTaskStackState> changedTaskStacks, boolean aborted,
            @Nullable SurfaceControl.Transaction finishTransaction) {
        if (DEBUG) {
            Log.d(TAG, "onTransitionConsumed" + aborted);
        }
        Trace.beginSection(TAG + "#onTransitionConsumed");
        mTaskPanelAnimationRunner.stopRunningAnimations();
        Trace.endSection();
    }

    @Override
    public void mergeAnimation(@NonNull IBinder transition,
            @NonNull Map<Integer, AutoTaskStackState> changedTaskStacks,
            @NonNull TransitionInfo info, @NonNull SurfaceControl.Transaction t,
            @NonNull IBinder mergeTarget,
            @NonNull Transitions.TransitionFinishCallback finishCallback) {
        if (DEBUG) {
            Log.d(TAG, "mergeAnimation");
        }
        Trace.beginSection(TAG + "#mergeAnimation");
        mTaskPanelAnimationRunner.stopRunningAnimations();
        Trace.endSection();
    }
}
