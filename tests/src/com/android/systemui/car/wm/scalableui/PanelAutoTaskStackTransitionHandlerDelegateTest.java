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
import static android.view.WindowManager.TRANSIT_CLOSE;
import static android.view.WindowManager.TRANSIT_OPEN;

import static com.android.systemui.car.wm.scalableui.systemevents.SystemEventConstants.EMPTY_EVENT_ID;
import static com.android.systemui.car.wm.scalableui.systemevents.SystemEventConstants.SYSTEM_HOME_EVENT_ID;
import static com.android.systemui.car.wm.scalableui.systemevents.SystemEventConstants.SYSTEM_TASK_CLOSE_EVENT_ID;
import static com.android.systemui.car.wm.scalableui.systemevents.SystemEventConstants.SYSTEM_TASK_OPEN_EVENT_ID;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Intent;
import android.os.IBinder;
import android.view.SurfaceControl;
import android.view.WindowManager;
import android.window.TransitionInfo;
import android.window.TransitionRequestInfo;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.car.scalableui.model.Event;
import com.android.systemui.SysuiTestCase;
import com.android.systemui.car.CarSystemUiTest;
import com.android.systemui.car.wm.scalableui.panel.PanelUtils;
import com.android.systemui.car.wm.scalableui.panel.TaskPanel;
import com.android.systemui.car.wm.scalableui.panel.TaskPanelInfoRepository;
import com.android.wm.shell.automotive.AutoLayoutManager;
import com.android.wm.shell.automotive.AutoTaskStackController;
import com.android.wm.shell.automotive.AutoTaskStackState;
import com.android.wm.shell.automotive.AutoTaskStackTransaction;
import com.android.wm.shell.transition.Transitions;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.HashMap;
import java.util.Map;

@CarSystemUiTest
@RunWith(AndroidJUnit4.class)
@SmallTest
public class PanelAutoTaskStackTransitionHandlerDelegateTest extends SysuiTestCase {

    private static final String TEST_PANEL_ID = "test_panel";
    private static final String TEST_COMPONENT_NAME = "com.test/com.test.TestActivity";

    private PanelAutoTaskStackTransitionHandlerDelegate mDelegate;

    @Mock
    private AutoTaskStackController mAutoTaskStackController;
    @Mock
    private PanelTransitionCoordinator mPanelTransitionCoordinator;
    @Mock
    private Transitions.TransitionFinishCallback mFinishCallback;
    @Mock
    private PanelUtils mPanelUtils;
    @Mock
    private TaskPanelInfoRepository mTaskPanelInfoRepository;
    @Mock
    private AutoLayoutManager mAutoLayoutManager;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        when(mPanelTransitionCoordinator.createAutoTaskStackTransaction(any(),
                any(), any())).thenReturn(new AutoTaskStackTransaction());
        mDelegate = new PanelAutoTaskStackTransitionHandlerDelegate(mContext,
                mAutoTaskStackController, mPanelTransitionCoordinator, mPanelUtils,
                mTaskPanelInfoRepository, mAutoLayoutManager);
    }

    @Test
    public void testHandleRequest_shouldHandleByPanels() {
        TransitionRequestInfo request = mock(TransitionRequestInfo.class);
        ActivityManager.RunningTaskInfo taskInfo = new ActivityManager.RunningTaskInfo();
        taskInfo.topActivityType = ACTIVITY_TYPE_HOME;
        taskInfo.baseIntent = new Intent();
        taskInfo.baseIntent.addCategory(Intent.CATEGORY_HOME);
        when(request.getType()).thenReturn(TRANSIT_OPEN);
        when(request.getTriggerTask()).thenReturn(taskInfo);

        AutoTaskStackTransaction autoTaskStackTransaction = mDelegate.handleRequest(
                mock(IBinder.class), request);

        assertThat(autoTaskStackTransaction).isNotNull();
    }

    @Test
    public void testHandleRequest_shouldNotHandleByPanels() {
        TransitionRequestInfo request = mock(TransitionRequestInfo.class);
        when(request.getTriggerTask()).thenReturn(null);

        AutoTaskStackTransaction autoTaskStackTransaction = mDelegate.handleRequest(
                mock(IBinder.class), request);
        assertThat(autoTaskStackTransaction).isNotNull();
    }

    @Test
    public void testStartAnimation_withPendingAnimators() {
        Map<Integer, AutoTaskStackState> changedTaskStacks = new HashMap<>();
        TransitionInfo info = mock(TransitionInfo.class);
        SurfaceControl.Transaction startTransaction = mock(SurfaceControl.Transaction.class);
        SurfaceControl.Transaction finishTransaction = mock(SurfaceControl.Transaction.class);
        when(mPanelTransitionCoordinator.playPendingAnimations(any(), any(), any(),
                any())).thenReturn(true);

        boolean result = mDelegate.startAnimation(
                mock(IBinder.class),
                changedTaskStacks,
                info,
                startTransaction,
                finishTransaction,
                mFinishCallback);

        assertThat(result).isTrue();
    }

    @Test
    public void testStartAnimation_withoutPendingAnimators() {
        Map<Integer, AutoTaskStackState> changedTaskStacks = new HashMap<>();
        TransitionInfo info = mock(TransitionInfo.class);
        SurfaceControl.Transaction startTransaction = mock(SurfaceControl.Transaction.class);
        SurfaceControl.Transaction finishTransaction = mock(SurfaceControl.Transaction.class);
        when(mPanelTransitionCoordinator.playPendingAnimations(any(), any(), any(),
                any())).thenReturn(false);

        boolean result = mDelegate.startAnimation(
                mock(IBinder.class),
                changedTaskStacks,
                info,
                startTransaction,
                finishTransaction,
                mFinishCallback);

        assertThat(result).isFalse();
    }

    @Test
    public void testOnTransitionConsumed() {
        mDelegate.onTransitionConsumed(
                mock(IBinder.class),
                mock(Map.class),
                false,
                mock(SurfaceControl.Transaction.class));

        verify(mPanelTransitionCoordinator).stopRunningAnimations(any());
    }

    @Test
    public void testMergeAnimation() {
        mDelegate.mergeAnimation(
                mock(IBinder.class),
                mock(Map.class),
                mock(TransitionInfo.class),
                mock(SurfaceControl.Transaction.class),
                mock(IBinder.class),
                mock(Transitions.TransitionFinishCallback.class));

        verify(mPanelTransitionCoordinator).mergeAnimation(any(), any());
    }

    @Test
    public void calculateEvent_nullTriggerTask_returnsEmptyEvent() {
        TransitionRequestInfo request = mock(TransitionRequestInfo.class);
        when(request.getTriggerTask()).thenReturn(null);

        Event event = mDelegate.calculateEvent(request);

        assertThat(event.getId()).isEqualTo(EMPTY_EVENT_ID);
        assertThat(event.getTokens()).isEmpty();
    }

    @Test
    public void calculateEvent_homeCategoryIntent_returnsSystemHomeEvent() {
        TransitionRequestInfo request = mock(TransitionRequestInfo.class);
        ActivityManager.RunningTaskInfo taskInfo = new ActivityManager.RunningTaskInfo();
        taskInfo.baseIntent = new Intent();
        taskInfo.baseIntent.addCategory(Intent.CATEGORY_HOME);
        when(request.getType()).thenReturn(TRANSIT_OPEN);
        when(request.getTriggerTask()).thenReturn(taskInfo);

        Event event = mDelegate.calculateEvent(request);

        assertThat(event.getId()).isEqualTo(SYSTEM_HOME_EVENT_ID);
    }

    @Test
    public void calculateEvent_avoidMoveToFrontFlag_returnsEmptyEvent() {
        TransitionRequestInfo request = mock(TransitionRequestInfo.class);
        ActivityManager.RunningTaskInfo taskInfo = new ActivityManager.RunningTaskInfo();
        taskInfo.baseIntent = new Intent();
        when(request.getType()).thenReturn(TRANSIT_OPEN);
        when(request.getTriggerTask()).thenReturn(taskInfo);
        when(request.getFlags()).thenReturn(WindowManager.TRANSIT_FLAG_AVOID_MOVE_TO_FRONT);

        Event event = mDelegate.calculateEvent(request);

        assertThat(event.getId()).isEqualTo(EMPTY_EVENT_ID);
        assertThat(event.getTokens()).isEmpty();
    }

    @Test
    public void calculateEvent_openingTransition_returnsTaskOpenEvent() {
        TransitionRequestInfo request = mock(TransitionRequestInfo.class);
        TaskPanel panel = mock(TaskPanel.class);
        ActivityManager.RunningTaskInfo taskInfo = new ActivityManager.RunningTaskInfo();
        taskInfo.baseIntent = new Intent();
        when(request.getType()).thenReturn(TRANSIT_OPEN);
        when(request.getTriggerTask()).thenReturn(taskInfo);
        ComponentName componentName = ComponentName.unflattenFromString(TEST_COMPONENT_NAME);
        when(mPanelUtils.getTaskComponentName(taskInfo)).thenReturn(componentName);
        when(mPanelUtils.getTaskPanel(any())).thenReturn(panel);
        when(panel.getPanelId()).thenReturn(TEST_PANEL_ID);

        Event event = mDelegate.calculateEvent(request);

        assertThat(event.getId()).isEqualTo(SYSTEM_TASK_OPEN_EVENT_ID);
        assertThat(event.getPanelId()).isEqualTo(TEST_PANEL_ID);
        assertThat(event.getTokens().get("component")).isEqualTo(TEST_COMPONENT_NAME);
    }

    @Test
    public void calculateEvent_closingTransition_returnsTaskCloseEvent() {
        TransitionRequestInfo request = mock(TransitionRequestInfo.class);
        TaskPanel panel = mock(TaskPanel.class);
        ActivityManager.RunningTaskInfo taskInfo = new ActivityManager.RunningTaskInfo();
        taskInfo.baseIntent = new Intent();
        when(request.getType()).thenReturn(TRANSIT_CLOSE);
        when(request.getTriggerTask()).thenReturn(taskInfo);
        ComponentName componentName = ComponentName.unflattenFromString(TEST_COMPONENT_NAME);
        when(mPanelUtils.getTaskComponentName(taskInfo)).thenReturn(componentName);
        when(mPanelUtils.getTaskPanel(any())).thenReturn(panel);
        when(panel.getPanelId()).thenReturn(TEST_PANEL_ID);

        Event event = mDelegate.calculateEvent(request);

        assertThat(event.getId()).isEqualTo(SYSTEM_TASK_CLOSE_EVENT_ID);
        assertThat(event.getPanelId()).isEqualTo(TEST_PANEL_ID);
        assertThat(event.getTokens().get("component")).isEqualTo(TEST_COMPONENT_NAME);
    }

    @Test
    public void calculateEvent_noPanelAvailable_returnsEmptyEvent() {
        TransitionRequestInfo request = mock(TransitionRequestInfo.class);
        ActivityManager.RunningTaskInfo taskInfo = new ActivityManager.RunningTaskInfo();
        taskInfo.baseIntent = new Intent();
        when(request.getType()).thenReturn(TRANSIT_CLOSE);
        when(request.getTriggerTask()).thenReturn(taskInfo);
        ComponentName componentName = ComponentName.unflattenFromString(TEST_COMPONENT_NAME);
        when(mPanelUtils.getTaskComponentName(taskInfo)).thenReturn(componentName);
        when(mPanelUtils.getTaskPanel(any())).thenReturn(null);

        Event event = mDelegate.calculateEvent(request);

        assertThat(event.getId()).isEqualTo(EMPTY_EVENT_ID);
        assertThat(event.getTokens()).isEmpty();
    }
}
