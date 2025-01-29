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

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.systemui.SysuiTestCase;
import com.android.systemui.car.CarSystemUiTest;
import com.android.wm.shell.transition.Transitions;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@CarSystemUiTest
@RunWith(AndroidJUnit4.class)
@SmallTest
public class TaskPanelAnimationRunnerTest extends SysuiTestCase {

    private TaskPanelAnimationRunner mTaskPanelAnimationRunner;
    private Transitions.TransitionFinishCallback mFinishCallback;

    @Before
    public void setUp() {
        mTaskPanelAnimationRunner = new TaskPanelAnimationRunner();
        mFinishCallback = mock(Transitions.TransitionFinishCallback.class);
    }

    @Test
    public void testPlayPendingAnimations() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1); // Latch for waiting
        Map<String, Animator> pendingAnimators = new HashMap<>();
        ValueAnimator animator = ValueAnimator.ofFloat(0, 1);
        animator.setDuration(2000L);
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);
                latch.countDown();
            }
        });
        pendingAnimators.put("testPanel", animator);

        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            mTaskPanelAnimationRunner.playPendingAnimations(pendingAnimators, mFinishCallback);
        });

        latch.await(3, TimeUnit.SECONDS);
        assertThat(latch.getCount()).isEqualTo(0);
        assertThat(mTaskPanelAnimationRunner.isAnimationRunning()).isFalse();
        verify(mFinishCallback).onTransitionFinished(null);
    }

    @Test
    public void testStopRunningAnimations() {
        CountDownLatch latch = new CountDownLatch(1); // Latch for waiting
        Map<String, Animator> pendingAnimators = new HashMap<>();
        ValueAnimator animator = ValueAnimator.ofFloat(0, 1);
        animator.setDuration(5000L);
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);
                latch.countDown();
            }
        });

        // Run the animation on the main looper
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            mTaskPanelAnimationRunner.playPendingAnimations(pendingAnimators, mFinishCallback);
        });

        mTaskPanelAnimationRunner.stopRunningAnimations();
        assertThat(latch.getCount()).isEqualTo(1);
        assertThat(mTaskPanelAnimationRunner.isAnimationRunning()).isFalse();
        verify(mFinishCallback).onTransitionFinished(null);
    }
}
