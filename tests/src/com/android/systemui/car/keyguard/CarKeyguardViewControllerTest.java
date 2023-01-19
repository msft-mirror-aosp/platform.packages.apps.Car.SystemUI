/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.systemui.car.keyguard;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;

import androidx.test.filters.SmallTest;

import com.android.keyguard.KeyguardUpdateMonitor;
import com.android.keyguard.ViewMediatorCallback;
import com.android.systemui.R;
import com.android.systemui.SysuiTestCase;
import com.android.systemui.car.CarSystemUiTest;
import com.android.systemui.car.systembar.CarSystemBarController;
import com.android.systemui.car.window.OverlayViewGlobalStateController;
import com.android.systemui.car.window.SystemUIOverlayWindowController;
import com.android.systemui.keyguard.domain.interactor.PrimaryBouncerCallbackInteractor;
import com.android.systemui.statusbar.phone.BiometricUnlockController;
import com.android.systemui.statusbar.phone.KeyguardBouncer;
import com.android.systemui.statusbar.policy.KeyguardStateController;
import com.android.systemui.toast.ToastFactory;
import com.android.systemui.util.concurrency.FakeExecutor;
import com.android.systemui.util.time.FakeSystemClock;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@CarSystemUiTest
@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
@SmallTest
public class CarKeyguardViewControllerTest extends SysuiTestCase {

    private CarKeyguardViewController mCarKeyguardViewController;
    private FakeExecutor mExecutor;

    @Mock
    private OverlayViewGlobalStateController mOverlayViewGlobalStateController;
    @Mock
    private SystemUIOverlayWindowController mSystemUIOverlayWindowController;
    @Mock
    private CarKeyguardViewController.OnKeyguardCancelClickedListener mCancelClickedListener;
    @Mock
    private KeyguardBouncer.Factory mKeyguardBouncerFactory;
    @Mock
    private KeyguardBouncer mBouncer;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        ViewGroup mockBaseLayout = new FrameLayout(mContext);

        when(mKeyguardBouncerFactory.create(
                any(ViewGroup.class),
                any(PrimaryBouncerCallbackInteractor.PrimaryBouncerExpansionCallback.class)))
                .thenReturn(mBouncer);
        when(mSystemUIOverlayWindowController.getBaseLayout()).thenReturn(mockBaseLayout);
        mExecutor = new FakeExecutor(new FakeSystemClock());

        mCarKeyguardViewController = new CarKeyguardViewController(
                mContext,
                mExecutor,
                mock(WindowManager.class),
                mock(ToastFactory.class),
                mSystemUIOverlayWindowController,
                mOverlayViewGlobalStateController,
                mock(KeyguardStateController.class),
                mock(KeyguardUpdateMonitor.class),
                () -> mock(BiometricUnlockController.class),
                mock(ViewMediatorCallback.class),
                mock(CarSystemBarController.class),
                mKeyguardBouncerFactory
        );
        mCarKeyguardViewController.inflate((ViewGroup) LayoutInflater.from(mContext).inflate(
                R.layout.sysui_overlay_window, /* root= */ null));
    }

    @Test
    public void onShow_bouncerIsSecure_showsBouncerWithSecuritySelectionReset() {
        when(mBouncer.isSecure()).thenReturn(true);
        mCarKeyguardViewController.show(/* options= */ null);
        waitForDelayableExecutor();

        verify(mBouncer).show(/* resetSecuritySelection= */ true);
    }

    @Test
    public void onShow_bouncerIsSecure_keyguardIsVisible() {
        when(mBouncer.isSecure()).thenReturn(true);
        mCarKeyguardViewController.show(/* options= */ null);

        verify(mOverlayViewGlobalStateController).showView(eq(mCarKeyguardViewController), any());
    }

    @Test
    public void onShow_bouncerNotSecure_hidesBouncerAndDestroysTheView() {
        when(mBouncer.isSecure()).thenReturn(false);
        mCarKeyguardViewController.show(/* options= */ null);
        waitForDelayableExecutor();

        verify(mBouncer).hide(/* destroyView= */ true);
    }

    @Test
    public void onShow_bouncerNotSecure_keyguardIsNotVisible() {
        when(mBouncer.isSecure()).thenReturn(false);
        mCarKeyguardViewController.show(/* options= */ null);
        waitForDelayableExecutor();

        // Here we check for both showView and hideView since the current implementation of show
        // with bouncer being not secure has the following method execution orders:
        // 1) show -> start -> showView
        // 2) show -> reset -> dismissAndCollapse -> hide -> stop -> hideView
        // Hence, we want to make sure that showView is called before hideView and not in any
        // other combination.
        InOrder inOrder = inOrder(mOverlayViewGlobalStateController);
        inOrder.verify(mOverlayViewGlobalStateController).showView(eq(mCarKeyguardViewController),
                any());
        inOrder.verify(mOverlayViewGlobalStateController).hideView(eq(mCarKeyguardViewController),
                any());
    }

    @Test
    public void onHide_keyguardShowing_hidesBouncerAndDestroysTheView() {
        when(mBouncer.isSecure()).thenReturn(true);
        mCarKeyguardViewController.show(/* options= */ null);
        mCarKeyguardViewController.hide(/* startTime= */ 0, /* fadeoutDelay= */ 0);

        verify(mBouncer).hide(/* destroyView= */ true);
    }

    @Test
    public void onHide_keyguardNotShown_doesNotHideOrDestroyBouncer() {
        mCarKeyguardViewController.hide(/* startTime= */ 0, /* fadeoutDelay= */ 0);

        verify(mBouncer, never()).hide(anyBoolean());
    }

    @Test
    public void onHide_KeyguardNotVisible() {
        when(mBouncer.isSecure()).thenReturn(true);
        mCarKeyguardViewController.show(/* options= */ null);
        mCarKeyguardViewController.hide(/* startTime= */ 0, /* fadeoutDelay= */ 0);

        InOrder inOrder = inOrder(mOverlayViewGlobalStateController);
        inOrder.verify(mOverlayViewGlobalStateController).showView(eq(mCarKeyguardViewController),
                any());
        inOrder.verify(mOverlayViewGlobalStateController).hideView(eq(mCarKeyguardViewController),
                any());
    }

    @Test
    public void setOccludedFalse_currentlyOccluded_showsKeyguard() {
        when(mBouncer.isSecure()).thenReturn(true);
        mCarKeyguardViewController.show(/* options= */ null);
        mCarKeyguardViewController.setOccluded(/* occluded= */ true, /* animate= */ false);
        reset(mBouncer);

        mCarKeyguardViewController.setOccluded(/* occluded= */ false, /* animate= */ false);
        waitForDelayableExecutor();

        verify(mBouncer).show(true);
    }

    @Test
    public void onCancelClicked_callsCancelClickedListener() {
        when(mBouncer.isSecure()).thenReturn(true);
        mCarKeyguardViewController.show(/* options= */ null);
        mCarKeyguardViewController.registerOnKeyguardCancelClickedListener(mCancelClickedListener);
        mCarKeyguardViewController.onCancelClicked();

        verify(mCancelClickedListener).onCancelClicked();
    }

    @Test
    public void onEnterSleepModeAndThenShowKeyguard_bouncerNotSecure_keyguardIsVisible() {
        when(mBouncer.isSecure()).thenReturn(false);
        mCarKeyguardViewController.onStartedGoingToSleep();
        mCarKeyguardViewController.show(/* options= */ null);
        waitForDelayableExecutor();

        // We want to make sure that showView is called beforehand and hideView is never called
        // so that the Keyguard is visible as a result.
        InOrder inOrder = inOrder(mOverlayViewGlobalStateController);
        inOrder.verify(mOverlayViewGlobalStateController).showView(eq(mCarKeyguardViewController),
                any());
        inOrder.verify(mOverlayViewGlobalStateController, never()).hideView(
                eq(mCarKeyguardViewController), any());
    }

    @Test
    public void onDeviceWakeUpWhileKeyguardShown_bouncerNotSecure_keyguardIsNotVisible() {
        when(mBouncer.isSecure()).thenReturn(false);
        mCarKeyguardViewController.onStartedGoingToSleep();
        mCarKeyguardViewController.show(/* options= */ null);
        mCarKeyguardViewController.onStartedWakingUp();
        waitForDelayableExecutor();

        // We want to make sure that showView is called beforehand and then hideView is called so
        // that the Keyguard is invisible as a result.
        InOrder inOrder = inOrder(mOverlayViewGlobalStateController);
        inOrder.verify(mOverlayViewGlobalStateController).showView(eq(mCarKeyguardViewController),
                any());
        inOrder.verify(mOverlayViewGlobalStateController).hideView(eq(mCarKeyguardViewController),
                any());
    }

    @Test
    public void onCancelClicked_hidesBouncerAndDestroysTheView() {
        when(mBouncer.isSecure()).thenReturn(true);
        mCarKeyguardViewController.show(/* options= */ null);
        mCarKeyguardViewController.registerOnKeyguardCancelClickedListener(mCancelClickedListener);
        mCarKeyguardViewController.onCancelClicked();

        verify(mBouncer).hide(/* destroyView= */ true);
    }

    private void waitForDelayableExecutor() {
        mExecutor.advanceClockToLast();
        mExecutor.runAllReady();
    }
}
