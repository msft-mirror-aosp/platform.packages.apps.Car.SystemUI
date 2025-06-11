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
import static android.view.WindowInsets.Type.systemOverlays;
import static android.view.WindowManager.TRANSIT_FLAG_AVOID_MOVE_TO_FRONT;

import static com.android.systemui.car.Flags.scalableUi;
import static com.android.systemui.car.wm.scalableui.systemevents.SystemEventConstants.COMPONENT_TOKEN_ID;
import static com.android.systemui.car.wm.scalableui.systemevents.SystemEventConstants.EMPTY_EVENT_ID;
import static com.android.systemui.car.wm.scalableui.systemevents.SystemEventConstants.PACKAGE_TOKEN_ID;
import static com.android.systemui.car.wm.scalableui.systemevents.SystemEventConstants.PANEL_TOKEN_ID;
import static com.android.systemui.car.wm.scalableui.systemevents.SystemEventConstants.SYSTEM_HOME_EVENT_ID;
import static com.android.systemui.car.wm.scalableui.systemevents.SystemEventConstants.SYSTEM_TASK_CLOSE_EVENT_ID;
import static com.android.systemui.car.wm.scalableui.systemevents.SystemEventConstants.SYSTEM_TASK_OPEN_EVENT_ID;

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
import com.android.car.scalableui.manager.StateManager;
import com.android.car.scalableui.model.Event;
import com.android.car.scalableui.model.PanelState;
import com.android.car.scalableui.model.PanelTransaction;
import com.android.car.scalableui.model.Variant;
import com.android.car.scalableui.panel.Panel;
import com.android.systemui.R;
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
import java.util.stream.IntStream;

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
    private final TaskPanelInfoRepository mPanelInfoRepository;
    private final AutoLayoutManager mAutoLayoutManager;

    @Inject
    public PanelAutoTaskStackTransitionHandlerDelegate(
            Context context,
            AutoTaskStackController autoTaskStackController,
            PanelTransitionCoordinator panelTransitionCoordinator,
            PanelUtils panelUtils,
            TaskPanelInfoRepository panelInfoRepository,
            AutoLayoutManager autoLayoutManager
    ) {
        mAutoTaskStackController = autoTaskStackController;
        mPanelTransitionCoordinator = panelTransitionCoordinator;
        mContext = context;
        mPanelUtils = panelUtils;
        mPanelInfoRepository = panelInfoRepository;
        mAutoLayoutManager = autoLayoutManager;
    }

    /**
     * Init the {@link PanelAutoTaskStackTransitionHandlerDelegate}.
     */
    public void init() {
        if (scalableUi() && mContext.getResources().getBoolean(R.bool.config_enableScalableUI)) {
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
                    mPanelTransitionCoordinator.createAutoTaskStackTransaction(transition,
                            panelTransaction, event);
            mPanelTransitionCoordinator.resetUnpreparedDecorPanel(panelTransaction);
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

        mPanelTransitionCoordinator.maybeResolveConflict(changedTaskStacks, transition);
        mPanelInfoRepository.maybeNotifyTopTaskOnPanelChanged();

        Trace.beginSection(TAG + "#startAnimation");

        // Its expected for the auto transition handler delegate to apply startTransaction for now.
        // TODO(b/421966313) Think about applying this in car-wm-shell instead.
        calculateTransaction(startTransaction, info, /* isFinish= */ false);
        calculateTransaction(finishTransaction, info, /* isFinish= */ true);
        startTransaction.apply();

        boolean animationStarted = mPanelTransitionCoordinator.playPendingAnimations(transition,
                finishCallback);
        Trace.endSection();
        return animationStarted;
    }

    private void calculateTransaction(SurfaceControl.Transaction transaction,
            @NonNull TransitionInfo info, boolean isFinish) {
        SurfaceControl leash = null;
        Rect pos = null;
        boolean visibility;
        float cornerRadius;
        int layer;
        for (TransitionInfo.Change change : info.getChanges()) {
            if (change.getTaskInfo() == null) {
                continue;
            }
            TaskPanel taskPanel = mPanelUtils.getTaskPanel(
                    tp -> tp.getRootTaskId() == change.getTaskInfo().taskId);

            if (taskPanel == null || taskPanel.getLeash() == null) {
                Log.e(TAG, "TaskPanel is null " + change.getTaskInfo() + ", or leash is null"
                        + taskPanel);
                continue;
            }
            leash = taskPanel.getLeash();

            if (isFinish) {
                // Use the PanelState is up to date even before animation, but not Panel.
                PanelState ps = StateManager.getPanelState(
                        taskPanel.getPanelId());
                if (ps == null) {
                    Log.e(TAG, "PanelState is null " + taskPanel.getPanelId());
                    continue;
                }
                Variant currentVariant = ps.getCurrentVariant();
                if (currentVariant == null) {
                    Log.e(TAG, "Current Variant for panelState is null " + taskPanel.getPanelId());
                    continue;
                }
                pos = currentVariant.getBounds();
                visibility = currentVariant.isVisible();
                cornerRadius = currentVariant.getCornerRadius();
                layer = currentVariant.getLayer();
            } else {
                // Start from current panel surface bounds rather than using window bounds from
                // change.
                pos = taskPanel.getBounds();
                visibility = taskPanel.isVisible();
                cornerRadius = taskPanel.getCornerRadius();
                layer = taskPanel.getLayer();
            }
            if (DEBUG) {
                Log.d(TAG, taskPanel.getPanelId() + (isFinish ? "end" : "start") + " bounds=" + pos
                        + ", visibility=" + visibility + ", cornerRadius" + cornerRadius
                        + ", layer=" + layer);
            }
            //TODO(b/404959846): use panel update.
            transaction.setPosition(leash, pos.left, pos.top);
            transaction.setCornerRadius(leash, cornerRadius);
            transaction.setVisibility(leash, visibility);
            transaction.setLayer(leash, layer);
            taskPanel.setLeash(leash);
            Rect[] panelInsets = taskPanel.getInsetRects(/* variant= */ null);
            IntStream.range(0, panelInsets.length).forEach(sideIndex -> {
                mAutoLayoutManager.addOrUpdateInsets(taskPanel.getRootStack(), sideIndex,
                        systemOverlays(), panelInsets[sideIndex]);
            });
        }
    }

    private Event calculateEvent(TransitionRequestInfo request) {
        if (request.getTriggerTask() == null) {
            return EMPTY_EVENT;
        }

        if (request.getTriggerTask().baseIntent.getCategories() != null
                && request.getTriggerTask().baseIntent.getCategories().contains(
                Intent.CATEGORY_HOME)) {
            ComponentName component = request.getTriggerTask().baseActivity;
            String packageString = component != null ? component.getPackageName() : null;
            // Multiple SUW activities have home as categories. Panels should treat them the same.
            return new Event.Builder(SYSTEM_HOME_EVENT_ID).addToken(PACKAGE_TOKEN_ID,
                    packageString).build();
        }

        if ((request.getFlags() & TRANSIT_FLAG_AVOID_MOVE_TO_FRONT)
                == TRANSIT_FLAG_AVOID_MOVE_TO_FRONT) {
            if (DEBUG) {
                Log.d(TAG, "Launching activity to the background, no panel action needed.");
            }
            return EMPTY_EVENT;
        }

        ComponentName component = mPanelUtils.getTaskComponentName(request.getTriggerTask());
        if (DEBUG) {
            Log.d(TAG, "Transition type=" + request.getType()
                    + " using component=" + component);
        }
        String componentString = component != null ? component.flattenToString() : null;
        String panelId;
        TaskPanel panel = null;
        if (component != null) {
            panel = mPanelUtils.getTaskPanel(tp -> tp.handles(component));
        }
        if (panel == null) {
            panel = mPanelUtils.getTaskPanel(TaskPanel::isLaunchRoot);
        }
        if (panel != null) {
            panelId = panel.getPanelId();
        } else {
            // There is no panel ready to handle this event
            // TODO(b/392694590): determine if/how this case should be handled
            Log.e(TAG, "No panel present to handle component " + component);
            return EMPTY_EVENT;
        }

        if (TransitionUtil.isClosingType(request.getType())) {
            return new Event.Builder(SYSTEM_TASK_CLOSE_EVENT_ID)
                    .addToken(PANEL_TOKEN_ID, panelId)
                    .addToken(COMPONENT_TOKEN_ID, componentString)
                    .build();
        } else if (TransitionUtil.isOpeningType(request.getType())) {
            return new Event.Builder(SYSTEM_TASK_OPEN_EVENT_ID)
                    .addToken(PANEL_TOKEN_ID, panelId)
                    .addToken(COMPONENT_TOKEN_ID, componentString)
                    .build();
        } else {
            Log.e(TAG, "Unknown transition type " + request.getType());
            return EMPTY_EVENT;
        }
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
        mPanelTransitionCoordinator.stopRunningAnimations(transition);
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
        mPanelTransitionCoordinator.stopRunningAnimations(transition);
        Trace.endSection();
    }
}
