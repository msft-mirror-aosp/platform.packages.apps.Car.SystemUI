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

import static com.android.car.scalableui.model.Restart.RESTART_POLICY_DEFAULT;
import static com.android.car.scalableui.model.Restart.RESTART_POLICY_LAST;

import static org.junit.Assume.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.os.UserHandle;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.car.scalableui.model.Event;
import com.android.car.scalableui.model.PanelState;
import com.android.car.scalableui.model.Restart;
import com.android.car.scalableui.panel.PanelUpdatePublisher;
import com.android.systemui.ShellSyncExecutor;
import com.android.systemui.SysuiTestCase;
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
public class TaskPanelTest extends SysuiTestCase {
    private static final String TASK_PANEL_ID = "TASK_PANEL_ID";

    private TaskPanel mTaskPanel;
    private ShellExecutor mMainExecutor;
    private RootTaskStackListener mRootTaskStackListener;

    @Mock
    private AutoTaskStackController mAutoTaskStackController;
    @Mock
    private CarServiceProvider mCarServiceProvider;
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
    private FlagManager mFlagManager;
    @Mock
    private PanelUpdatePublisher mPanelUpdatePublisher;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mMainExecutor = new ShellSyncExecutor();
        mTaskPanel = Mockito.spy(
                new TaskPanel(mAutoTaskStackController, mUserContext, mCarServiceProvider,
                        mAutoTaskStackHelper, mShellTaskOrganizer, mAutoCaptionController,
                        mPanelUtils, mTaskPanelInfoRepository, mAutoDecorManager, mEventDispatcher,
                        mPanelControllerInitializer, mAutoLayoutManager, mMainExecutor,
                        mAutoSurfaceTransactionFactory, mFlagManager,
                        Optional.of(mPanelUpdatePublisher), TASK_PANEL_ID));
        when(mFactory.create(any())).thenReturn(mTaskPanel);

        when(mAutoSurfaceTransactionFactory.createTransaction(any())).thenReturn(
                mAutoSurfaceTransaction);

        mTaskPanel.setDisplayId(0);
        mTaskPanel.init();
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
        assumeTrue(mFlagManager.isEnabled(Flag.ScalableUiTaskAutoRestart));
        when(mPanelState.getRestart()).thenReturn(mRestart);
        when(mRestart.getMaxRetry()).thenReturn(0);

        mTaskPanel.scheduleRestartAttempt(mRunningTaskInfo);

        verify(mEventDispatcher).executeEvent(any(Event.class));
    }
}
