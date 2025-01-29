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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.ActivityManager;
import android.car.app.CarActivityManager;
import android.content.Context;
import android.graphics.Rect;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.systemui.SysuiTestCase;
import com.android.systemui.car.CarServiceProvider;
import com.android.systemui.car.CarSystemUiTest;
import com.android.systemui.car.wm.scalableui.AutoTaskStackHelper;
import com.android.wm.shell.automotive.AutoTaskStackController;
import com.android.wm.shell.automotive.AutoTaskStackTransaction;
import com.android.wm.shell.automotive.RootTaskStack;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@CarSystemUiTest
@RunWith(AndroidJUnit4.class)
@SmallTest
public class TaskPanelTest extends SysuiTestCase{
    private static final String TASK_PANEL_ID = "TASK_PANEL_ID";

    private Context mContext;
    private AutoTaskStackController mAutoTaskStackController;
    private CarServiceProvider mCarServiceProvider;
    private AutoTaskStackHelper mAutoTaskStackHelper;
    private TaskPanel.Factory mFactory;
    private TaskPanel mTaskPanel;
    private RootTaskStack mRootTaskStack;
    private CarActivityManager mCarActivityManager;

    @Before
    public void setUp() {
        mContext = getContext();
        mAutoTaskStackController = mock(AutoTaskStackController.class);
        mCarServiceProvider = mock(CarServiceProvider.class);
        mAutoTaskStackHelper = mock(AutoTaskStackHelper.class);
        mFactory = mock(TaskPanel.Factory.class);
        mTaskPanel = new TaskPanel(mAutoTaskStackController, mContext, mCarServiceProvider,
                mAutoTaskStackHelper, TASK_PANEL_ID);
        when(mFactory.create(any())).thenReturn(mTaskPanel);
        mRootTaskStack = mock(RootTaskStack.class);
        mCarActivityManager = mock(CarActivityManager.class);
    }

    @Test
    public void testInit() {
        mTaskPanel.setDisplayId(0);
        mTaskPanel.init();

        verify(mAutoTaskStackController).createRootTaskStack(anyInt(), any());
    }

    @Test
    public void testReset() {
        mTaskPanel.setVisibility(true);
        Rect bounds = new Rect(0, 0, 100, 100);
        mTaskPanel.setBounds(bounds);
        mTaskPanel.setLayer(1);
        mTaskPanel.setRootTaskStack(mRootTaskStack);
        when(mRootTaskStack.getRootTaskInfo()).thenReturn(
                mock(ActivityManager.RunningTaskInfo.class));
        when(mRootTaskStack.getId()).thenReturn(123);

        mTaskPanel.reset();

        verify(mAutoTaskStackController).startTransition(any(AutoTaskStackTransaction.class));
    }
}
