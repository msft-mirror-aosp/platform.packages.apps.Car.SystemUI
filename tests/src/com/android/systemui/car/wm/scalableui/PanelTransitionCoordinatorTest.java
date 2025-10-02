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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.os.Binder;
import android.os.IBinder;
import android.testing.TestableLooper;
import android.view.SurfaceControl;
import android.window.TransitionInfo;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.car.scalableui.model.PanelTransaction;
import com.android.systemui.CarSysuiTestCase;
import com.android.systemui.ShellSyncExecutor;
import com.android.systemui.car.CarSystemUiTest;
import com.android.systemui.car.flags.FlagManager;
import com.android.systemui.car.wm.scalableui.panel.PanelUtils;
import com.android.wm.shell.automotive.AutoLayoutManager;
import com.android.wm.shell.automotive.AutoSurfaceTransaction;
import com.android.wm.shell.automotive.AutoSurfaceTransactionFactory;
import com.android.wm.shell.automotive.AutoTaskStackController;
import com.android.wm.shell.common.ShellExecutor;
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
public class PanelTransitionCoordinatorTest extends CarSysuiTestCase {

    private PanelTransitionCoordinator mPanelTransitionCoordinator;
    private ShellExecutor mMainExecutor;

    @Mock
    private Transitions.TransitionFinishCallback mFinishCallback;
    @Mock
    private SurfaceControl.Transaction mFinishTransaction;
    @Mock
    private TransitionInfo mInfo;
    @Mock
    private AutoTaskStackController mAutoTaskStackController;
    @Mock
    private PanelUtils mPanelUtils;
    @Mock
    private AutoSurfaceTransactionFactory mAutoSurfaceTransactionFactory;
    @Mock
    private AutoSurfaceTransaction mAutoSurfaceTransaction;
    @Mock
    private AutoLayoutManager mAutoLayoutManager;
    @Mock
    private FlagManager mFlagManager;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mMainExecutor = new ShellSyncExecutor();
        mPanelTransitionCoordinator = new PanelTransitionCoordinator(
            mAutoTaskStackController, mAutoSurfaceTransactionFactory, mPanelUtils,
            mAutoLayoutManager, mMainExecutor, mFlagManager);
        when(mAutoSurfaceTransactionFactory.createTransaction(anyString())).thenReturn(
                mAutoSurfaceTransaction);
    }

    @Test
    public void testStartTransition_addsPendingTransaction() {
        IBinder binder = new Binder();
        Animator animator = new ValueAnimator();
        when(mAutoTaskStackController.startTransition(any())).thenReturn(binder);
        PanelTransaction panelTransaction = new PanelTransaction.Builder()
                .addAnimator("testPanel", animator).setHasWindowChanges(true).build();

        mPanelTransitionCoordinator.startTransition(panelTransaction);

        PanelTransaction pendingTransaction =
                mPanelTransitionCoordinator.getPendingPanelTransaction(binder);
        assertThat(pendingTransaction).isNotNull();
        assertThat(pendingTransaction.getAnimators().size()).isEqualTo(1);
    }

    @Test
    public void testPlayPendingAnimations_noTransaction_returnsFalse() {
        IBinder binder = new Binder();
        AtomicBoolean animationStarted = new AtomicBoolean(false);

        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            animationStarted.set(mPanelTransitionCoordinator.playPendingAnimations(binder,
                    mFinishCallback, mFinishTransaction, mInfo));
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
        PanelTransaction panelTransaction = new PanelTransaction.Builder()
                .addAnimator("testPanel", animator).build();
        mPanelTransitionCoordinator.createAutoTaskStackTransaction(binder, panelTransaction,
                /* event= */ null);

        AtomicBoolean animationStarted = new AtomicBoolean(false);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            animationStarted.set(mPanelTransitionCoordinator.playPendingAnimations(binder,
                    mFinishCallback, mFinishTransaction, mInfo));
        });

        assertThat(animationStarted.get()).isTrue();
        assertThat(latch.await(/* timeout= */ 10, TimeUnit.SECONDS)).isTrue();
        assertThat(latch.getCount()).isEqualTo(0);
        assertThat(mPanelTransitionCoordinator.isAnimationRunning()).isFalse();
        // There may be a slight delay between the Animator receiving onAnimationEnd and the
        // AnimatorSet receiving onAnimationEnd.
        verify(mFinishCallback, timeout(1000)).onTransitionFinished(null);
    }

    @Test
    public void testStopRunningAnimationsIfNeed_differentTransition_stopAnimation()
            throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1); // Latch for waiting
        IBinder binder = new Binder();
        IBinder binder2 = new Binder();
        ValueAnimator animator = ValueAnimator.ofFloat(0, 1);
        animator.setDuration(5000L);
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);
                latch.countDown();
            }
        });
        PanelTransaction panelTransaction = new PanelTransaction.Builder()
                .addAnimator("testPanel", animator).build();
        mPanelTransitionCoordinator.createAutoTaskStackTransaction(binder, panelTransaction,
                /* event= */ null);

        // Run the animation on the main looper
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            mPanelTransitionCoordinator.playPendingAnimations(binder, mFinishCallback,
                    mFinishTransaction, mInfo);
        });

        mPanelTransitionCoordinator.stopRunningAnimations(binder2);
        // onAnimationEnd should still be called when cancelled - wait for a small amount of time
        // and expect animation end callback to execute
        assertThat(latch.await(/* timeout= */ 1, TimeUnit.SECONDS)).isTrue();
        assertThat(latch.getCount()).isEqualTo(0);
        // There may be a slight delay between the Animator receiving onAnimationEnd and the
        // AnimatorSet receiving onAnimationEnd.
        verify(mFinishCallback, timeout(1000)).onTransitionFinished(null);
    }

    @Test
    public void testStartTransition_nullTransition_runsAnimationDirectly()
            throws InterruptedException {
        // This test covers the case where a transaction has window changes, but the shell
        // does not create a transition for it, so it must be animated directly.
        when(mAutoTaskStackController.startTransition(any())).thenReturn(null);
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean startCallbackCalled = new AtomicBoolean(false);
        AtomicBoolean endCallbackCalled = new AtomicBoolean(false);

        Runnable startCallback = () -> startCallbackCalled.set(true);
        Runnable endCallback = () -> {
            endCallbackCalled.set(true);
            latch.countDown();
        };

        ValueAnimator animator = ValueAnimator.ofFloat(0, 1);
        animator.setDuration(100L);

        PanelTransaction panelTransaction = new PanelTransaction.Builder()
                .addAnimator("testPanel", animator)
                .setAnimationStartCallbackRunnable(startCallback)
                .setAnimationEndCallbackRunnable(endCallback)
                .setHasWindowChanges(true)
                .build();

        mPanelTransitionCoordinator.startTransition(panelTransaction);

        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            mPanelTransitionCoordinator.startTransition(panelTransaction);
        });

        assertThat(latch.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(startCallbackCalled.get()).isTrue();
        assertThat(endCallbackCalled.get()).isTrue();
    }
}
