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

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.mockitoSession;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.view.Display;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.car.scalableui.panel.Panel;
import com.android.car.scalableui.panel.PanelPool;
import com.android.systemui.CarSysuiTestCase;
import com.android.systemui.car.CarSystemUiTest;
import com.android.systemui.util.concurrency.FakeExecutor;
import com.android.systemui.util.time.FakeSystemClock;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

@CarSystemUiTest
@RunWith(AndroidJUnit4.class)
@SmallTest
public class TaskPanelInfoRepositoryTest extends CarSysuiTestCase {
    private static final String TEST_PANEL_ID = "test_panel";
    private static final ComponentName TEST_COMPONENT_NAME_1 = new ComponentName("com.test.package",
            "com.test.package.Activity1");
    private static final ComponentName TEST_COMPONENT_NAME_2 = new ComponentName("com.test.package",
            "com.test.package.Activity2");
    private static final int TEST_TASK_ID_1 = 10000;
    private TaskPanelInfoRepository mTaskPanelInfoRepository;
    private FakeExecutor mFakeExecutor;

    private MockitoSession mMockingSession;

    @Mock
    private TaskPanelInfoRepository.TaskPanelChangeListener mTaskPanelChangeListener;
    @Mock
    private PanelPool mPanelPool;
    @Mock
    private Panel mTestPanel;

    @Before
    public void setUp() {
        mMockingSession = mockitoSession()
                .initMocks(this)
                .mockStatic(PanelPool.class)
                .strictness(Strictness.WARN)
                .startMocking();

        mFakeExecutor = new FakeExecutor(new FakeSystemClock());
        doReturn(mPanelPool).when(() -> PanelPool.getInstance());
        when(mPanelPool.getPanel(TEST_PANEL_ID)).thenReturn(mTestPanel);
        when(mTestPanel.isVisible()).thenReturn(true);
        mTaskPanelInfoRepository = new TaskPanelInfoRepository(mFakeExecutor);
        mTaskPanelInfoRepository.addChangeListener(mTaskPanelChangeListener);
    }

    @After
    public void tearDown() {
        if (mMockingSession != null) {
            mMockingSession.finishMocking();
        }
        mTaskPanelInfoRepository.removeChangeListener(mTaskPanelChangeListener);
    }

    @Test
    public void onTaskAppearedOnPanel_notifyChange() {
        mTaskPanelInfoRepository.onTaskAppearedOnPanel(TEST_PANEL_ID,
                createTaskInfo(TEST_COMPONENT_NAME_1, TEST_TASK_ID_1));
        mTaskPanelInfoRepository.maybeNotifyTopTaskOnPanelChanged();
        waitForDelayableExecutor();

        verify(mTaskPanelChangeListener).onTopTaskOnPanelChanged();
    }

    @Test
    public void onTaskChangedOnPanel_noChange_noNotifyChange() {
        mTaskPanelInfoRepository.onTaskAppearedOnPanel(TEST_PANEL_ID,
                createTaskInfo(TEST_COMPONENT_NAME_1, TEST_TASK_ID_1));
        mTaskPanelInfoRepository.maybeNotifyTopTaskOnPanelChanged();
        waitForDelayableExecutor();
        Mockito.clearInvocations(mTaskPanelChangeListener);

        mTaskPanelInfoRepository.onTaskChangedOnPanel(TEST_PANEL_ID,
                createTaskInfo(TEST_COMPONENT_NAME_1, TEST_TASK_ID_1));

        verify(mTaskPanelChangeListener, never()).onTopTaskOnPanelChanged();
    }

    @Test
    public void onTaskChangedOnPanel_topActivityChange_notifyChange() {
        mTaskPanelInfoRepository.onTaskAppearedOnPanel(TEST_PANEL_ID,
                createTaskInfo(TEST_COMPONENT_NAME_1, TEST_TASK_ID_1));
        mTaskPanelInfoRepository.maybeNotifyTopTaskOnPanelChanged();
        waitForDelayableExecutor();
        Mockito.clearInvocations(mTaskPanelChangeListener);

        mTaskPanelInfoRepository.onTaskChangedOnPanel(TEST_PANEL_ID,
                createTaskInfo(TEST_COMPONENT_NAME_2, TEST_TASK_ID_1));
        mTaskPanelInfoRepository.maybeNotifyTopTaskOnPanelChanged();
        waitForDelayableExecutor();

        verify(mTaskPanelChangeListener).onTopTaskOnPanelChanged();
    }

    @Test
    public void onTaskChangedOnPanel_visibilityChange_notifyChange() {
        ActivityManager.RunningTaskInfo taskInfo1 = createTaskInfo(TEST_COMPONENT_NAME_1,
                TEST_TASK_ID_1);
        taskInfo1.isVisible = false;
        mTaskPanelInfoRepository.onTaskAppearedOnPanel(TEST_PANEL_ID, taskInfo1);
        mTaskPanelInfoRepository.maybeNotifyTopTaskOnPanelChanged();
        waitForDelayableExecutor();
        Mockito.clearInvocations(mTaskPanelChangeListener);

        mTaskPanelInfoRepository.onTaskChangedOnPanel(TEST_PANEL_ID,
                createTaskInfo(TEST_COMPONENT_NAME_1, TEST_TASK_ID_1));
        mTaskPanelInfoRepository.maybeNotifyTopTaskOnPanelChanged();
        waitForDelayableExecutor();

        verify(mTaskPanelChangeListener).onTopTaskOnPanelChanged();
    }

    @Test
    public void onTaskRemovedOnPanel_notifyChange() {
        mTaskPanelInfoRepository.onTaskAppearedOnPanel(TEST_PANEL_ID,
                createTaskInfo(TEST_COMPONENT_NAME_1, TEST_TASK_ID_1));
        mTaskPanelInfoRepository.maybeNotifyTopTaskOnPanelChanged();
        waitForDelayableExecutor();
        Mockito.clearInvocations(mTaskPanelChangeListener);

        mTaskPanelInfoRepository.onTaskVanishedOnPanel(TEST_PANEL_ID,
                createTaskInfo(TEST_COMPONENT_NAME_1, TEST_TASK_ID_1));
        mTaskPanelInfoRepository.maybeNotifyTopTaskOnPanelChanged();
        waitForDelayableExecutor();

        verify(mTaskPanelChangeListener).onTopTaskOnPanelChanged();
    }

    @Test
    public void testIsPackageVisible() {
        // start as not visible
        assertThat(mTaskPanelInfoRepository.isPackageVisible(
                TEST_COMPONENT_NAME_1.getPackageName())).isFalse();

        // should be visible after task appeared
        mTaskPanelInfoRepository.onTaskAppearedOnPanel(TEST_PANEL_ID,
                createTaskInfo(TEST_COMPONENT_NAME_1, TEST_TASK_ID_1));
        assertThat(mTaskPanelInfoRepository.isPackageVisible(
                TEST_COMPONENT_NAME_1.getPackageName())).isTrue();

        // should not be visible after task vanish
        mTaskPanelInfoRepository.onTaskVanishedOnPanel(TEST_PANEL_ID,
                createTaskInfo(TEST_COMPONENT_NAME_1, TEST_TASK_ID_1));
        assertThat(mTaskPanelInfoRepository.isPackageVisible(
                TEST_COMPONENT_NAME_1.getPackageName())).isFalse();
    }

    @Test
    public void testIsPackageVisibleOnDisplay() {
        // start as not visible
        assertThat(mTaskPanelInfoRepository.isPackageVisibleOnDisplay(
                TEST_COMPONENT_NAME_1.getPackageName(), Display.DEFAULT_DISPLAY)).isFalse();

        // should be visible after task appeared only on default display
        mTaskPanelInfoRepository.onTaskAppearedOnPanel(TEST_PANEL_ID,
                createTaskInfo(TEST_COMPONENT_NAME_1, TEST_TASK_ID_1));
        assertThat(mTaskPanelInfoRepository.isPackageVisibleOnDisplay(
                TEST_COMPONENT_NAME_1.getPackageName(), Display.DEFAULT_DISPLAY)).isTrue();
        assertThat(mTaskPanelInfoRepository.isPackageVisibleOnDisplay(
                TEST_COMPONENT_NAME_1.getPackageName(), Display.DEFAULT_DISPLAY + 1)).isFalse();

        // should not be visible after task vanish
        mTaskPanelInfoRepository.onTaskVanishedOnPanel(TEST_PANEL_ID,
                createTaskInfo(TEST_COMPONENT_NAME_1, TEST_TASK_ID_1));
        assertThat(mTaskPanelInfoRepository.isPackageVisibleOnDisplay(
                TEST_COMPONENT_NAME_1.getPackageName(), Display.DEFAULT_DISPLAY)).isFalse();
    }

    @Test
    public void testIsComponentVisible() {
        // start as not visible
        assertThat(mTaskPanelInfoRepository.isComponentVisible(
                TEST_COMPONENT_NAME_1)).isFalse();

        // should be visible after task appeared
        mTaskPanelInfoRepository.onTaskAppearedOnPanel(TEST_PANEL_ID,
                createTaskInfo(TEST_COMPONENT_NAME_1, TEST_TASK_ID_1));
        assertThat(mTaskPanelInfoRepository.isComponentVisible(
                TEST_COMPONENT_NAME_1)).isTrue();

        // should not be visible after task vanish
        mTaskPanelInfoRepository.onTaskVanishedOnPanel(TEST_PANEL_ID,
                createTaskInfo(TEST_COMPONENT_NAME_1, TEST_TASK_ID_1));
        assertThat(mTaskPanelInfoRepository.isComponentVisible(
                TEST_COMPONENT_NAME_1)).isFalse();
    }

    @Test
    public void testIsComponentVisibleOnDisplay() {
        // start as not visible
        assertThat(mTaskPanelInfoRepository.isComponentVisibleOnDisplay(
                TEST_COMPONENT_NAME_1, Display.DEFAULT_DISPLAY)).isFalse();

        // should be visible after task appeared only on default display
        mTaskPanelInfoRepository.onTaskAppearedOnPanel(TEST_PANEL_ID,
                createTaskInfo(TEST_COMPONENT_NAME_1, TEST_TASK_ID_1));
        assertThat(mTaskPanelInfoRepository.isComponentVisibleOnDisplay(
                TEST_COMPONENT_NAME_1, Display.DEFAULT_DISPLAY)).isTrue();
        assertThat(mTaskPanelInfoRepository.isComponentVisibleOnDisplay(
                TEST_COMPONENT_NAME_1, Display.DEFAULT_DISPLAY + 1)).isFalse();

        // should not be visible after task vanish
        mTaskPanelInfoRepository.onTaskVanishedOnPanel(TEST_PANEL_ID,
                createTaskInfo(TEST_COMPONENT_NAME_1, TEST_TASK_ID_1));
        assertThat(mTaskPanelInfoRepository.isComponentVisibleOnDisplay(
                TEST_COMPONENT_NAME_1, Display.DEFAULT_DISPLAY)).isFalse();
    }

    private ActivityManager.RunningTaskInfo createTaskInfo(ComponentName componentName, int id) {
        ActivityManager.RunningTaskInfo taskInfo = new ActivityManager.RunningTaskInfo();
        taskInfo.taskId = id;
        taskInfo.displayId = Display.DEFAULT_DISPLAY;
        taskInfo.topActivity = componentName;
        taskInfo.isVisible = true;
        taskInfo.isRunning = true;
        taskInfo.isSleeping = false;

        return taskInfo;
    }

    private void waitForDelayableExecutor() {
        mFakeExecutor.advanceClockToLast();
        mFakeExecutor.runAllReady();
    }
}
