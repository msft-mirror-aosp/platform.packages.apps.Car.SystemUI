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

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
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
import com.android.car.scalableui.panel.Panel;
import com.android.systemui.R;
import com.android.systemui.car.wm.scalableui.panel.TaskPanel;
import com.android.systemui.car.wm.scalableui.panel.TaskPanelPool;
import com.android.wm.shell.automotive.AutoTaskStackController;
import com.android.wm.shell.automotive.AutoTaskStackState;
import com.android.wm.shell.automotive.AutoTaskStackTransaction;
import com.android.wm.shell.automotive.AutoTaskStackTransitionHandlerDelegate;
import com.android.wm.shell.shared.TransitionUtil;
import com.android.wm.shell.transition.Transitions;

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
    private final TaskPanelTransitionCoordinator mTaskPanelTransitionCoordinator;
    private final Context mContext;

    @Inject
    public PanelAutoTaskStackTransitionHandlerDelegate(
            Context context,
            AutoTaskStackController autoTaskStackController,
            TaskPanelTransitionCoordinator taskPanelTransitionCoordinator) {
        mAutoTaskStackController = autoTaskStackController;
        mTaskPanelTransitionCoordinator = taskPanelTransitionCoordinator;
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
            PanelTransaction panelTransaction = EventDispatcher.getTransaction(event);
            AutoTaskStackTransaction wct =
                    mTaskPanelTransitionCoordinator.createAutoTaskStackTransaction(transition,
                            panelTransaction);
            if (DEBUG) {
                Log.d(TAG, "handleRequest: COMPLETED " + wct);
            }
            Trace.endSection();
            return wct;
        }
        Trace.endSection();
        return null;
    }

    private boolean shouldHandleByPanels(@NonNull TransitionRequestInfo request) {
        if (request.getTriggerTask() == null) {
            return false;
        }
        return TaskPanelPool.handles(request.getTriggerTask().parentTaskId)
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
                    + ", changedTaskStacks" + changedTaskStacks
                    + ", start transaction" + startTransaction.getId()
                    + ", finishTransaction" + finishTransaction.getId());
        }

        mTaskPanelTransitionCoordinator.maybeResolveConflict(changedTaskStacks, transition);

        Trace.beginSection(TAG + "#startAnimation");

        calculateTransaction(startTransaction, info, /* isFinish= */ false);
        calculateTransaction(finishTransaction, info, /* isFinish= */ true);
        startTransaction.apply();

        boolean animationStarted = mTaskPanelTransitionCoordinator.playPendingAnimations(transition,
                finishCallback);
        Trace.endSection();
        return animationStarted;
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
        if (request.getTriggerTask() == null) {
            return EMPTY_EVENT;
        }

        if (request.getTriggerTask().baseIntent.getCategories() != null
                && request.getTriggerTask().baseIntent.getCategories().contains(
                Intent.CATEGORY_HOME)) {
            return new Event("_System_OnHomeEvent");
        }

        if ((request.getFlags() & TRANSIT_FLAG_AVOID_MOVE_TO_FRONT)
                == TRANSIT_FLAG_AVOID_MOVE_TO_FRONT) {
            if (DEBUG) {
                Log.d(TAG, "Launching activity to the background, no panel action needed.");
            }
            return EMPTY_EVENT;
        }

        ComponentName component;
        if (TransitionUtil.isClosingType(request.getType())) {
            // On a closing event, the baseActivity may be null but the realActivity will still
            // return the component being closed.
            component = request.getTriggerTask().realActivity;
            if (DEBUG) {
                Log.d(TAG, "Closing transition - using realActivity component=" + component);
            }
        } else {
            component = request.getTriggerTask().baseActivity;
            if (DEBUG) {
                Log.d(TAG, "Open transition - using baseActivity component=" + component);
            }
        }
        String componentString = component != null ? component.flattenToString() : null;
        String panelId;
        TaskPanel panel = null;
        if (componentString != null) {
            panel = TaskPanelPool.getTaskPanel(tp -> tp.handles(component));
        }
        if (panel == null) {
            panel = TaskPanelPool.getTaskPanel(TaskPanel::isLaunchRoot);
        }
        if (panel != null) {
            panelId = panel.getId();
        } else {
            // There is no panel ready to handle this event
            // TODO(b/392694590): determine if/how this case should be handled
            Log.e(TAG, "No panel present to handle component " + component);
            return EMPTY_EVENT;
        }

        if (TransitionUtil.isClosingType(request.getType())) {
            return new Event("_System_TaskCloseEvent")
                    .addToken("panelId", panelId)
                    .addToken("component", componentString);
        }
        return new Event("_System_TaskOpenEvent")
                .addToken("panelId", panelId)
                .addToken("component", componentString);
    }

    @Override
    public void onTransitionConsumed(@NonNull IBinder transition,
            @NonNull Map<Integer, AutoTaskStackState> changedTaskStacks, boolean aborted,
            @Nullable SurfaceControl.Transaction finishTransaction) {
        if (DEBUG) {
            Log.d(TAG, "onTransitionConsumed" + aborted);
        }
        Trace.beginSection(TAG + "#onTransitionConsumed");
        mTaskPanelTransitionCoordinator.stopRunningAnimations();
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
        mTaskPanelTransitionCoordinator.stopRunningAnimations();
        Trace.endSection();
    }
}
