/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.systemui.car.systembar;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Intent;
import android.hardware.input.InputManager;
import android.test.suitebuilder.annotation.SmallTest;
import android.testing.AndroidTestingRunner;
import android.view.InputEvent;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.widget.LinearLayout;

import com.android.systemui.R;
import com.android.systemui.SysuiTestCase;
import com.android.systemui.car.CarSystemUiTest;
import com.android.systemui.shared.system.TaskStackChangeListener;
import com.android.systemui.statusbar.AlphaOptimizedImageView;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@CarSystemUiTest
@RunWith(AndroidTestingRunner.class)
@SmallTest
public class AppGridButtonTest extends SysuiTestCase {
    private static final String RECENTS_ACTIVITY_NAME =
            "com.android.car.carlauncher/.recents.CarRecentsActivity";
    private static final String DIALER_ACTIVITY_NAME = "com.android.car.dialer/.ui.TelecomActivity";

    private AppGridButton mAppGridButton;
    private TaskStackChangeListener mTaskStackChangeListener;
    @Mock
    private InputManager mInputManager;
    @Mock
    private ActivityManager.RunningTaskInfo mRecentsRunningTaskInfo;
    @Mock
    private ActivityManager.RunningTaskInfo mDialerRunningTaskInfo;
    @Mock
    private ActivityManager.RunningTaskInfo mNoTopComponentRunningTaskInfo;
    @Mock
    private Intent mDialerBaseIntent;
    @Mock
    private Intent mBaseIntentWithNoComponent;
    @Mock
    private AlphaOptimizedImageView mAlphaOptimizedImageView;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mContext = spy(mContext);
        when(mContext.getSystemService(InputManager.class)).thenReturn(mInputManager);
        mContext.getOrCreateTestableResources().addOverride(
                com.android.internal.R.string.config_recentsComponentName,
                /* value= */ RECENTS_ACTIVITY_NAME);
        mRecentsRunningTaskInfo.topActivity = ComponentName.unflattenFromString(
                RECENTS_ACTIVITY_NAME);
        mDialerRunningTaskInfo.topActivity = ComponentName.unflattenFromString(
                DIALER_ACTIVITY_NAME);
        mNoTopComponentRunningTaskInfo.baseIntent = mBaseIntentWithNoComponent;
        when(mDialerBaseIntent.getComponent()).thenReturn(
                ComponentName.unflattenFromString(DIALER_ACTIVITY_NAME));

        LinearLayout testLayout = (LinearLayout) LayoutInflater.from(mContext).inflate(
                R.layout.app_grid_button_test, /* root= */ null);
        mAppGridButton = testLayout.findViewById(R.id.app_grid_button);
        mTaskStackChangeListener = mAppGridButton.getTaskStackChangeListener();
    }

    @Test
    public void recents_movedToFront_recentsActive() {
        mTaskStackChangeListener.onTaskMovedToFront(mDialerRunningTaskInfo);
        mTaskStackChangeListener.onTaskMovedToFront(mRecentsRunningTaskInfo);

        assertThat(mAppGridButton.getIsRecentsActive()).isTrue();
    }

    @Test
    public void dialer_movedToFront_recentsNotActive() {
        mTaskStackChangeListener.onTaskMovedToFront(mRecentsRunningTaskInfo);
        mTaskStackChangeListener.onTaskMovedToFront(mDialerRunningTaskInfo);

        assertThat(mAppGridButton.getIsRecentsActive()).isFalse();
    }

    @Test
    public void noTopActivityDialerTask_movedToFront_recentsNotActive() {
        mDialerRunningTaskInfo.topActivity = null;
        mDialerRunningTaskInfo.baseIntent = mDialerBaseIntent;

        mTaskStackChangeListener.onTaskMovedToFront(mRecentsRunningTaskInfo);
        mTaskStackChangeListener.onTaskMovedToFront(mDialerRunningTaskInfo);

        assertThat(mAppGridButton.getIsRecentsActive()).isFalse();
    }

    @Test
    public void noTopActivityAndNoBaseIntentTask_movedToFront_recentsNotActive() {
        mTaskStackChangeListener.onTaskMovedToFront(mRecentsRunningTaskInfo);
        mTaskStackChangeListener.onTaskMovedToFront(mNoTopComponentRunningTaskInfo);

        assertThat(mAppGridButton.getIsRecentsActive()).isFalse();
    }

    @Test
    public void longClick_recentsNotActive_returnsTrue() {
        mAppGridButton.setIsRecentsActive(false);

        assertThat(mAppGridButton.performLongClick()).isTrue();
    }

    @Test
    public void longClick_recentsNotActive_keyEventSent() {
        mAppGridButton.setIsRecentsActive(false);

        mAppGridButton.performLongClick();

        verify(mInputManager, times(1)).injectInputEvent(argThat(this::isRecentsKeyEvent),
                anyInt());
    }

    @Test
    public void longClick_recentsActive_returnsFalse() {
        mAppGridButton.setIsRecentsActive(true);

        assertThat(mAppGridButton.performLongClick()).isFalse();
    }

    @Test
    public void longClick_recentsActive_noKeyEventSent() {
        mAppGridButton.setIsRecentsActive(true);

        mAppGridButton.performLongClick();

        verify(mInputManager, never()).injectInputEvent(argThat(this::isRecentsKeyEvent), anyInt());
    }

    @Test
    public void click_recentsNotActive_noKeyEventSent() {
        mAppGridButton.setIsRecentsActive(false);

        mAppGridButton.performClick();

        verify(mInputManager, never()).injectInputEvent(argThat(this::isRecentsKeyEvent), anyInt());
    }

    @Test
    public void click_recentsActive_keyEventSent() {
        mAppGridButton.setIsRecentsActive(true);

        mAppGridButton.performClick();

        verify(mInputManager, times(1)).injectInputEvent(argThat(this::isRecentsKeyEvent),
                anyInt());
    }

    @Test
    public void updateImage_recentsNotActive_iconResourceNotSetToRecentsIcon() {
        mAppGridButton.setIsRecentsActive(false);

        mAppGridButton.updateImage(mAlphaOptimizedImageView);

        verify(mAlphaOptimizedImageView, never()).setImageResource(
                eq(com.android.systemui.R.drawable.car_ic_recents));
    }

    @Test
    public void updateImage_recentsActive_iconResourceSetToRecentsIcon() {
        mAppGridButton.setIsRecentsActive(true);

        mAppGridButton.updateImage(mAlphaOptimizedImageView);

        verify(mAlphaOptimizedImageView, times(1)).setImageResource(
                eq(com.android.systemui.R.drawable.car_ic_recents));
    }

    @Test
    public void refreshIconAlpha_recentsNotActive_iconAlphaSetToSelectedAlpha() {
        mAppGridButton.setIsRecentsActive(false);

        mAppGridButton.refreshIconAlpha(mAlphaOptimizedImageView);

        verify(mAlphaOptimizedImageView, never()).setAlpha(mAppGridButton.getSelectedAlpha());
    }

    @Test
    public void refreshIconAlpha_recentsActive_iconAlphaNotSetToSelectedAlpha() {
        mAppGridButton.setIsRecentsActive(true);

        mAppGridButton.refreshIconAlpha(mAlphaOptimizedImageView);

        verify(mAlphaOptimizedImageView, times(1)).setAlpha(mAppGridButton.getSelectedAlpha());
    }

    private boolean isRecentsKeyEvent(InputEvent event) {
        return event instanceof KeyEvent
                && ((KeyEvent) event).getKeyCode() == KeyEvent.KEYCODE_APP_SWITCH;
    }
}
