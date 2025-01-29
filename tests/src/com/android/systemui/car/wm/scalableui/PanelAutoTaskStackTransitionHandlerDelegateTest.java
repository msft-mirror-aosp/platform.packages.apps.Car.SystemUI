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
import static android.view.WindowManager.TRANSIT_OPEN;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.animation.Animator;
import android.app.ActivityManager;
import android.content.Context;
import android.os.IBinder;
import android.view.SurfaceControl;
import android.window.TransitionInfo;
import android.window.TransitionRequestInfo;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.systemui.SysuiTestCase;
import com.android.systemui.car.CarSystemUiTest;
import com.android.wm.shell.automotive.AutoTaskStackController;
import com.android.wm.shell.automotive.AutoTaskStackState;
import com.android.wm.shell.automotive.AutoTaskStackTransaction;
import com.android.wm.shell.transition.Transitions;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.HashMap;
import java.util.Map;

@CarSystemUiTest
@RunWith(AndroidJUnit4.class)
@SmallTest
public class PanelAutoTaskStackTransitionHandlerDelegateTest extends SysuiTestCase {

    private Context mContext;
    private AutoTaskStackController mAutoTaskStackController;
    private TaskPanelAnimationRunner mTaskPanelAnimationRunner;
    private PanelAutoTaskStackTransitionHandlerDelegate mDelegate;
    private Transitions.TransitionFinishCallback mFinishCallback;

    @Before
    public void setUp() {
        mContext = getContext();
        mAutoTaskStackController = mock(AutoTaskStackController.class);
        mTaskPanelAnimationRunner = mock(TaskPanelAnimationRunner.class);
        mDelegate = new PanelAutoTaskStackTransitionHandlerDelegate(
                mContext, mAutoTaskStackController, mTaskPanelAnimationRunner);
        mFinishCallback = mock(Transitions.TransitionFinishCallback.class);
    }

    @Test
    public void testHandleRequest_shouldHandleByPanels() {
        TransitionRequestInfo request = mock(TransitionRequestInfo.class);
        ActivityManager.RunningTaskInfo taskInfo = new ActivityManager.RunningTaskInfo();
        taskInfo.topActivityType = ACTIVITY_TYPE_HOME;
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
        assertThat(autoTaskStackTransaction).isNull();
    }

    @Test
    public void testStartAnimation_withPendingAnimators() {
        Map<Integer, AutoTaskStackState> changedTaskStacks = new HashMap<>();
        TransitionInfo info = mock(TransitionInfo.class);
        SurfaceControl.Transaction startTransaction = mock(SurfaceControl.Transaction.class);
        SurfaceControl.Transaction finishTransaction = mock(SurfaceControl.Transaction.class);
        Map<String, Animator> pendingAnimators = new HashMap<>();
        pendingAnimators.put("testAnimator", mock(Animator.class));
        mDelegate.mPendingAnimators.putAll(pendingAnimators);

        boolean result = mDelegate.startAnimation(
                mock(IBinder.class),
                changedTaskStacks,
                info,
                startTransaction,
                finishTransaction,
                mFinishCallback);

        assertThat(result).isTrue();
        verify(mTaskPanelAnimationRunner).playPendingAnimations(
                pendingAnimators, mFinishCallback);
    }

    @Test
    public void testStartAnimation_withoutPendingAnimators() {
        Map<Integer, AutoTaskStackState> changedTaskStacks = new HashMap<>();
        TransitionInfo info = mock(TransitionInfo.class);
        SurfaceControl.Transaction startTransaction = mock(SurfaceControl.Transaction.class);
        SurfaceControl.Transaction finishTransaction = mock(SurfaceControl.Transaction.class);

        boolean result = mDelegate.startAnimation(
                mock(IBinder.class),
                changedTaskStacks,
                info,
                startTransaction,
                finishTransaction,
                mFinishCallback);

        assertThat(result).isFalse();
        verify(mTaskPanelAnimationRunner, never()).playPendingAnimations(
                any(), any());
    }

    @Test
    public void testOnTransitionConsumed() {
        mDelegate.onTransitionConsumed(
                mock(IBinder.class),
                mock(Map.class),
                false,
                mock(SurfaceControl.Transaction.class));

        verify(mTaskPanelAnimationRunner).stopRunningAnimations();
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

        verify(mTaskPanelAnimationRunner).stopRunningAnimations();
    }
}
