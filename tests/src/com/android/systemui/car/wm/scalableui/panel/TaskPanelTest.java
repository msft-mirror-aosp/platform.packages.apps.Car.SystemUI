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
package com.android.systemui.car.wm.scalableui.panel;

import static android.car.app.CarActivityManager.LAUNCH_BEHAVIOR_REMAIN_IN_SOURCE_ROOT_TASK;

import static com.android.car.scalableui.model.Restart.RESTART_POLICY_DEFAULT;
import static com.android.car.scalableui.model.Restart.RESTART_POLICY_LAST;
import static com.android.car.scalableui.model.TaskBehavior.NEW_TASK_LAUNCH_POLICY_REMAIN_IN_SOURCE;

import static org.junit.Assume.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.ActivityManager;
import android.car.Car;
import android.car.app.CarActivityManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Binder;
import android.os.IBinder;
import android.os.UserHandle;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.window.WindowContainerToken;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.car.scalableui.model.Event;
import com.android.car.scalableui.model.PanelState;
import com.android.car.scalableui.model.Restart;
import com.android.car.scalableui.model.TaskBehavior;
import com.android.car.scalableui.panel.PanelUpdatePublisher;
import com.android.systemui.CarSysuiTestCase;
import com.android.systemui.ShellSyncExecutor;
import com.android.systemui.car.CarServiceProvider;
import com.android.systemui.car.CarSystemUiTest;
import com.android.systemui.car.flags.Flag;
import com.android.systemui.car.flags.FlagManager;
import com.android.systemui.car.wm.scalableui.AutoTaskStackHelper;
import com.android.systemui.car.wm.scalableui.EventDispatcher;
import com.android.systemui.car.wm.scalableui.panel.controller.PanelControllerInitializer;
import com.android.wm.shell.ShellTaskOrganizer;
import com.android.wm.shell.automotive.AutoCaptionController;
import com.android.wm.shell.automotive.AutoDecorManager;
import com.android.wm.shell.automotive.AutoLayoutManager;
import com.android.wm.shell.automotive.AutoSurfaceTransaction;
import com.android.wm.shell.automotive.AutoSurfaceTransactionFactory;
import com.android.wm.shell.automotive.AutoTaskStackController;
import com.android.wm.shell.automotive.AutoTaskStackTransaction;
import com.android.wm.shell.automotive.RootTaskStack;
import com.android.wm.shell.automotive.RootTaskStackListener;
import com.android.wm.shell.common.ShellExecutor;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.Optional;

@CarSystemUiTest
@RunWith(AndroidJUnit4.class)
@SmallTest
public class TaskPanelTest extends CarSysuiTestCase {
    private static final String TASK_PANEL_ID = "TASK_PANEL_ID";

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private TaskPanel mTaskPanel;
    private ShellExecutor mMainExecutor;
    private RootTaskStackListener mRootTaskStackListener;

    @Mock
    private AutoTaskStackController mAutoTaskStackController;
    @Mock
    private CarServiceProvider mCarServiceProvider;
    @Mock
    private CarActivityManager mCarActivityManager;
    @Mock
    private AutoTaskStackHelper mAutoTaskStackHelper;
    @Mock
    private AutoCaptionController mAutoCaptionController;
    @Mock
    private ShellTaskOrganizer mShellTaskOrganizer;
    @Mock
    private TaskPanel.Factory mFactory;
    @Mock
    private RootTaskStack mRootTaskStack;
    @Mock
    private AutoDecorManager mAutoDecorManager;
    @Mock
    private PanelUtils mPanelUtils;
    @Mock
    private TaskPanelInfoRepository mTaskPanelInfoRepository;
    @Mock
    private EventDispatcher mEventDispatcher;
    @Mock
    private PanelControllerInitializer mPanelControllerInitializer;
    @Mock
    private AutoLayoutManager mAutoLayoutManager;
    @Mock
    private AutoSurfaceTransactionFactory mAutoSurfaceTransactionFactory;
    @Mock
    private AutoSurfaceTransaction mAutoSurfaceTransaction;
    @Mock
    private PanelState mPanelState;
    @Mock
    private Restart mRestart;
    @Mock
    private Context mUserContext;
    @Mock
    private ActivityManager.RunningTaskInfo mRunningTaskInfo;
    @Mock
    private PanelUpdatePublisher mPanelUpdatePublisher;
    @Mock
    private FlagManager mFlagManager;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mMainExecutor = new ShellSyncExecutor();
        mTaskPanel = Mockito.spy(
                new TaskPanel(mAutoTaskStackController, mUserContext, mCarServiceProvider,
                        mAutoTaskStackHelper, mShellTaskOrganizer, mAutoCaptionController,
                        mPanelUtils, mTaskPanelInfoRepository, mAutoDecorManager, mEventDispatcher,
                        mPanelControllerInitializer, mAutoLayoutManager, mMainExecutor,
                        mAutoSurfaceTransactionFactory, Optional.of(mPanelUpdatePublisher),
                        mFlagManager,
                        TASK_PANEL_ID));
        when(mFactory.create(any())).thenReturn(mTaskPanel);
        when(mAutoSurfaceTransactionFactory.createTransaction(any())).thenReturn(
                mAutoSurfaceTransaction);

        mTaskPanel.setDisplayId(0);
        mTaskPanel.init();
        ArgumentCaptor<CarServiceProvider.CarServiceOnConnectedListener> carServiceConnectCaptor =
                ArgumentCaptor.forClass(CarServiceProvider.CarServiceOnConnectedListener.class);
        verify(mCarServiceProvider).addListener(carServiceConnectCaptor.capture());
        Car car = mock(Car.class);
        when(car.getCarManager(CarActivityManager.class)).thenReturn(mCarActivityManager);
        carServiceConnectCaptor.getValue().onConnected(car);
        ArgumentCaptor<RootTaskStackListener> listenerArgumentCaptor =
                ArgumentCaptor.forClass(RootTaskStackListener.class);
        verify(mAutoTaskStackController).createRootTaskStack(anyInt(), anyString(),
                listenerArgumentCaptor.capture());
        mRootTaskStackListener = listenerArgumentCaptor.getValue();
        doReturn(mPanelState).when(mTaskPanel).getPanelState();
        mTaskPanel.setRootTaskStack(mRootTaskStack);
        when(mRootTaskStack.getRootTaskInfo()).thenReturn(mRunningTaskInfo);
    }

    @Test
    public void testInit() {
        // init() is called in setUp, so we just verify it was called.
        verify(mAutoTaskStackController).createRootTaskStack(anyInt(), anyString(), any());
    }

    @Test
    public void testReset() {
        mTaskPanel.setVisibility(true);
        Rect bounds = new Rect(0, 0, 100, 100);
        mTaskPanel.setBounds(bounds);
        mTaskPanel.setLayer(1);
        when(mRootTaskStack.getId()).thenReturn(123);

        mTaskPanel.reset();

        verify(mAutoTaskStackController).startTransition(any(AutoTaskStackTransaction.class));
    }

    @Test
    public void scheduleRestartAttempt_withLastPolicy_restartsLastTask() {
        mTaskPanel.setVisibility(true);
        assumeTrue(mFlagManager.isEnabled(Flag.ScalableUiTaskAutoRestart));
        Intent intent = new Intent("TEST_ACTION");
        mRunningTaskInfo.baseIntent = intent;
        when(mPanelState.getRestart()).thenReturn(mRestart);
        when(mRestart.getMaxRetry()).thenReturn(1);
        when(mRestart.getPolicy()).thenReturn(RESTART_POLICY_LAST);

        mTaskPanel.scheduleRestartAttempt(mRunningTaskInfo);

        verify(mUserContext).startActivityAsUser(intent, UserHandle.CURRENT);
    }

    @Test
    public void scheduleRestartAttempt_withDefaultPolicy_restartsDefaultTask() {
        mTaskPanel.setVisibility(true);
        assumeTrue(mFlagManager.isEnabled(Flag.ScalableUiTaskAutoRestart));
        Intent intent = new Intent("DEFAULT_ACTION");
        doReturn(intent).when(mTaskPanel).getDefaultIntent();
        when(mPanelState.getRestart()).thenReturn(mRestart);
        when(mRestart.getMaxRetry()).thenReturn(1);
        when(mRestart.getPolicy()).thenReturn(RESTART_POLICY_DEFAULT);

        mTaskPanel.scheduleRestartAttempt(mRunningTaskInfo);

        verify(mUserContext).startActivityAsUser(intent, UserHandle.CURRENT);
    }

    @Test
    public void scheduleRestartAttempt_maxRetriesReached_sendsEmptyEvent() {
        mTaskPanel.setVisibility(true);
        assumeTrue(mFlagManager.isEnabled(Flag.ScalableUiTaskAutoRestart));
        when(mPanelState.getRestart()).thenReturn(mRestart);
        when(mRestart.getMaxRetry()).thenReturn(0);

        mTaskPanel.scheduleRestartAttempt(mRunningTaskInfo);

        verify(mEventDispatcher).executeEvent(any(Event.class));
    }

    @Test
    public void scheduleRestartAttempt_invisiblePanel_skipped() {
        mTaskPanel.setVisibility(false);
        assumeTrue(mFlagManager.isEnabled(Flag.ScalableUiTaskAutoRestart));
        Intent intent = new Intent("DEFAULT_ACTION");
        doReturn(intent).when(mTaskPanel).getDefaultIntent();
        when(mPanelState.getRestart()).thenReturn(mRestart);
        when(mRestart.getMaxRetry()).thenReturn(1);
        when(mRestart.getPolicy()).thenReturn(RESTART_POLICY_DEFAULT);

        mTaskPanel.scheduleRestartAttempt(mRunningTaskInfo);

        verify(mUserContext, never()).startActivityAsUser(intent, UserHandle.CURRENT);
        verify(mEventDispatcher, never()).executeEvent(any(Event.class));
    }

    @Test
    public void trySetRootTaskLaunchBehavior_noRootTask_doesNothing() {
        mTaskPanel.setRootTaskStack(null);

        mTaskPanel.trySetRootTaskLaunchBehavior();

        verify(mCarActivityManager, never()).setLaunchBehaviorForRootTask(any(), anyInt());
    }

    @Test
    public void trySetRootTaskLaunchBehavior_nullLaunchBehavior_doesNothing() {
        when(mPanelState.getTaskBehavior()).thenReturn(null);

        mTaskPanel.trySetRootTaskLaunchBehavior();

        verify(mCarActivityManager, never()).setLaunchBehaviorForRootTask(any(), anyInt());
    }

    @Test
    public void trySetRootTaskLaunchBehavior_setsLaunchBehavior() {
        IBinder binder = new Binder();
        WindowContainerToken wct = mock(WindowContainerToken.class);
        when(wct.asBinder()).thenReturn(binder);
        when(mRunningTaskInfo.getToken()).thenReturn(wct);
        TaskBehavior taskBehavior = mock(TaskBehavior.class);
        when(taskBehavior.getNewTaskLaunchPolicy()).thenReturn(
                NEW_TASK_LAUNCH_POLICY_REMAIN_IN_SOURCE);
        when(mPanelState.getTaskBehavior()).thenReturn(taskBehavior);

        mTaskPanel.trySetRootTaskLaunchBehavior();

        verify(mCarActivityManager).setLaunchBehaviorForRootTask(any(),
                eq(LAUNCH_BEHAVIOR_REMAIN_IN_SOURCE_ROOT_TASK));
    }
}
