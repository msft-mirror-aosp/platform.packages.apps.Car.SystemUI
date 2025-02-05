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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.os.Binder;
import android.os.IBinder;
import android.testing.TestableLooper;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.car.scalableui.manager.PanelTransaction;
import com.android.systemui.SysuiTestCase;
import com.android.systemui.car.CarSystemUiTest;
import com.android.wm.shell.automotive.AutoTaskStackController;
import com.android.wm.shell.transition.Transitions;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@CarSystemUiTest
@RunWith(AndroidJUnit4.class)
@TestableLooper.RunWithLooper
@SmallTest
public class TaskPanelTransitionCoordinatorTest extends SysuiTestCase {

    private TaskPanelTransitionCoordinator mTaskPanelTransitionCoordinator;

    @Mock
    private Transitions.TransitionFinishCallback mFinishCallback;
    @Mock
    private AutoTaskStackController mAutoTaskStackController;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mTaskPanelTransitionCoordinator = new TaskPanelTransitionCoordinator(
                mAutoTaskStackController);
    }

    @Test
    public void testStartTransition_addsPendingTransaction() {
        IBinder binder = new Binder();
        Animator animator = new ValueAnimator();
        when(mAutoTaskStackController.startTransition(any())).thenReturn(binder);
        PanelTransaction panelTransaction = new PanelTransaction();
        panelTransaction.setAnimator("testPanel", animator);

        mTaskPanelTransitionCoordinator.startTransition(panelTransaction);

        PanelTransaction pendingTransaction =
                mTaskPanelTransitionCoordinator.getPendingPanelTransaction(binder);
        assertThat(pendingTransaction).isNotNull();
        assertThat(pendingTransaction.getAnimators().size()).isEqualTo(1);
    }

    @Test
    public void testPlayPendingAnimations_noTransaction_returnsFalse() {
        IBinder binder = new Binder();
        AtomicBoolean animationStarted = new AtomicBoolean(false);

        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            animationStarted.set(mTaskPanelTransitionCoordinator.playPendingAnimations(binder,
                    mFinishCallback));
        });

        assertThat(animationStarted.get()).isFalse();
    }

    @Test
    public void testPlayPendingAnimations() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1); // Latch for waiting
        IBinder binder = new Binder();
        ValueAnimator animator = ValueAnimator.ofFloat(0, 1);
        animator.setDuration(1000L);
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);
                latch.countDown();
            }
        });
        PanelTransaction panelTransaction = new PanelTransaction();
        panelTransaction.setAnimator("testPanel", animator);
        mTaskPanelTransitionCoordinator.createAutoTaskStackTransaction(binder, panelTransaction);

        AtomicBoolean animationStarted = new AtomicBoolean(false);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            animationStarted.set(mTaskPanelTransitionCoordinator.playPendingAnimations(binder,
                    mFinishCallback));
        });

        assertThat(animationStarted.get()).isTrue();
        assertThat(latch.await(/* timeout= */ 5, TimeUnit.SECONDS)).isTrue();
        assertThat(latch.getCount()).isEqualTo(0);
        assertThat(mTaskPanelTransitionCoordinator.isAnimationRunning()).isFalse();
        // There may be a slight delay between the Animator receiving onAnimationEnd and the
        // AnimatorSet receiving onAnimationEnd.
        verify(mFinishCallback, timeout(1000)).onTransitionFinished(null);
    }

    @Test
    public void testStopRunningAnimations() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1); // Latch for waiting
        IBinder binder = new Binder();
        ValueAnimator animator = ValueAnimator.ofFloat(0, 1);
        animator.setDuration(5000L);
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);
                latch.countDown();
            }
        });
        PanelTransaction panelTransaction = new PanelTransaction();
        panelTransaction.setAnimator("testPanel", animator);
        mTaskPanelTransitionCoordinator.createAutoTaskStackTransaction(binder, panelTransaction);

        // Run the animation on the main looper
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            mTaskPanelTransitionCoordinator.playPendingAnimations(binder, mFinishCallback);
        });

        mTaskPanelTransitionCoordinator.stopRunningAnimations();
        // onAnimationEnd should still be called when cancelled - wait for a small amount of time
        // and expect animation end callback to execute
        assertThat(latch.await(/* timeout= */ 1, TimeUnit.SECONDS)).isTrue();
        assertThat(latch.getCount()).isEqualTo(0);
        assertThat(mTaskPanelTransitionCoordinator.isAnimationRunning()).isFalse();
        // There may be a slight delay between the Animator receiving onAnimationEnd and the
        // AnimatorSet receiving onAnimationEnd.
        verify(mFinishCallback, timeout(1000)).onTransitionFinished(null);
    }
}
