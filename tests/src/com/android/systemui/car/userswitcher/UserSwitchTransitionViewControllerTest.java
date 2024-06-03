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

package com.android.systemui.car.userswitcher;

import static com.android.systemui.Flags.FLAG_REFACTOR_GET_CURRENT_USER;
import static com.android.systemui.car.Flags.FLAG_USER_SWITCH_KEYGUARD_SHOWN_TIMEOUT;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.ActivityManager;
import android.app.KeyguardManager;
import android.content.pm.UserInfo;
import android.os.RemoteException;
import android.os.UserManager;
import android.platform.test.flag.junit.SetFlagsRule;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;
import android.testing.TestableResources;
import android.view.IWindowManager;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.test.filters.SmallTest;

import com.android.systemui.R;
import com.android.systemui.SysuiTestCase;
import com.android.systemui.car.CarSystemUiTest;
import com.android.systemui.car.window.OverlayViewGlobalStateController;
import com.android.systemui.util.concurrency.FakeExecutor;
import com.android.systemui.util.time.FakeSystemClock;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@CarSystemUiTest
@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
@SmallTest
public class UserSwitchTransitionViewControllerTest extends SysuiTestCase {
    private static final int TEST_USER_1 = 100;
    private static final int TEST_USER_2 = 110;

    @Rule
    public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    private UserSwitchTransitionViewController mCarUserSwitchingDialogController;
    private TestableResources mTestableResources;
    private FakeExecutor mExecutor;
    private FakeSystemClock mClock;
    private ViewGroup mViewGroup;
    @Mock
    private ActivityManager mMockActivityManager;
    @Mock
    private OverlayViewGlobalStateController mOverlayViewGlobalStateController;
    @Mock
    private IWindowManager mWindowManagerService;
    @Mock
    private UserManager mMockUserManager;
    @Mock
    private KeyguardManager mKeyguardManager;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mTestableResources = mContext.getOrCreateTestableResources();
        mClock = new FakeSystemClock();
        mExecutor = new FakeExecutor(mClock);
        when(mKeyguardManager.isDeviceSecure(anyInt())).thenReturn(false);
        mContext.addMockSystemService(KeyguardManager.class, mKeyguardManager);
        mCarUserSwitchingDialogController = new UserSwitchTransitionViewController(
                mContext,
                mTestableResources.getResources(),
                mExecutor,
                mMockActivityManager,
                mMockUserManager,
                mWindowManagerService,
                mOverlayViewGlobalStateController
        );

        mockGetUserInfo(TEST_USER_1);
        mockGetUserInfo(TEST_USER_2);
        mockGlobalShowView();
        mViewGroup = (ViewGroup) LayoutInflater.from(mContext).inflate(
                R.layout.sysui_overlay_window, /* root= */ null);
        mCarUserSwitchingDialogController.inflate(mViewGroup);
    }

    @Test
    public void onHandleShow_newUserSelected_showsDialog() {
        mCarUserSwitchingDialogController.handleShow(/* newUserId= */ TEST_USER_1);
        mExecutor.advanceClockToLast();
        mExecutor.runAllReady();

        verify(mOverlayViewGlobalStateController).showView(eq(mCarUserSwitchingDialogController),
                any());
    }

    @Test
    public void onHandleShow_showsDefaultLoadingMessage() {
        mCarUserSwitchingDialogController.handleShow(/* newUserId= */ TEST_USER_1);
        mExecutor.advanceClockToLast();
        mExecutor.runAllReady();

        TextView textView = mViewGroup.findViewById(R.id.user_loading);
        assertThat(textView.getText().toString()).isEqualTo(
                mTestableResources.getResources().getString(R.string.car_loading_profile));
    }

    @Test
    public void onHandleShow_showsUserSwitchingMessage() {
        String message = "Hello world!";
        when(mMockActivityManager.getSwitchingFromUserMessage()).thenReturn(message);

        mCarUserSwitchingDialogController.handleShow(/* newUserId= */ TEST_USER_1);
        mExecutor.advanceClockToLast();
        mExecutor.runAllReady();

        TextView textView = mViewGroup.findViewById(R.id.user_loading);
        assertThat(textView.getText().toString()).isEqualTo(message);
    }

    @Test
    public void onHandleShow_alreadyShowing_ignoresRequest() {
        mCarUserSwitchingDialogController.handleShow(/* newUserId= */ TEST_USER_1);
        mExecutor.advanceClockToLast();
        mExecutor.runAllReady();
        mCarUserSwitchingDialogController.handleShow(/* newUserId= */ TEST_USER_2);
        mExecutor.advanceClockToLast();
        mExecutor.runAllReady();

        // Verify that the request was processed only once.
        verify(mOverlayViewGlobalStateController).showView(eq(mCarUserSwitchingDialogController),
                any());
    }

    @Test
    public void onHandleShow_sameUserSelected_ignoresRequest() {
        mCarUserSwitchingDialogController.handleShow(/* newUserId= */ TEST_USER_1);
        mExecutor.advanceClockToLast();
        mExecutor.runAllReady();
        mCarUserSwitchingDialogController.handleHide();
        mExecutor.advanceClockToLast();
        mExecutor.runAllReady();
        mCarUserSwitchingDialogController.handleShow(/* newUserId= */ TEST_USER_1);
        mExecutor.advanceClockToLast();
        mExecutor.runAllReady();

        // Verify that the request was processed only once.
        verify(mOverlayViewGlobalStateController).showView(eq(mCarUserSwitchingDialogController),
                any());
    }

    @Test
    public void onHandleShow_noUserRefactor_setsWMState() throws RemoteException {
        mSetFlagsRule.disableFlags(FLAG_REFACTOR_GET_CURRENT_USER);

        mCarUserSwitchingDialogController.handleShow(/* newUserId= */ TEST_USER_1);
        mExecutor.advanceClockToLast();
        mExecutor.runAllReady();

        verify(mWindowManagerService).setSwitchingUser(true);
        verify(mWindowManagerService).lockNow(null);
    }

    @Test
    public void onHandleShow_userRefactor_setsWMState() throws RemoteException {
        mSetFlagsRule.enableFlags(FLAG_REFACTOR_GET_CURRENT_USER);

        mCarUserSwitchingDialogController.handleShow(/* newUserId= */ TEST_USER_1);
        mExecutor.advanceClockToLast();
        mExecutor.runAllReady();

        verify(mWindowManagerService).setSwitchingUser(true);
        verify(mWindowManagerService, never()).lockNow(null);
    }

    @Test
    public void handleSwitching_noUserRefactor_doNothing() throws RemoteException {
        mSetFlagsRule.disableFlags(FLAG_REFACTOR_GET_CURRENT_USER);

        mCarUserSwitchingDialogController.handleSwitching(/* newUserId= */ TEST_USER_1);
        mExecutor.advanceClockToLast();
        mExecutor.runAllReady();

        verify(mWindowManagerService, never()).lockNow(null);
    }

    @Test
    public void handleSwitching_userRefactor_userNotSecure_doNothing() throws RemoteException {
        mSetFlagsRule.enableFlags(FLAG_REFACTOR_GET_CURRENT_USER);

        mCarUserSwitchingDialogController.handleSwitching(/* newUserId= */ TEST_USER_1);
        mExecutor.advanceClockToLast();
        mExecutor.runAllReady();

        verify(mWindowManagerService, never()).lockNow(null);
    }

    @Test
    public void handleSwitching_userRefactor_userSecure_setsWMState() throws RemoteException {
        mSetFlagsRule.enableFlags(FLAG_REFACTOR_GET_CURRENT_USER);
        when(mKeyguardManager.isDeviceSecure(anyInt())).thenReturn(true);

        mCarUserSwitchingDialogController.handleSwitching(/* newUserId= */ TEST_USER_1);
        mExecutor.advanceClockToLast();
        mExecutor.runAllReady();

        verify(mWindowManagerService).lockNow(null);
    }

    @Test
    public void onHide_currentlyShowing_hidesDialog() {
        mCarUserSwitchingDialogController.handleShow(/* newUserId= */ TEST_USER_1);
        mExecutor.advanceClockToLast();
        mExecutor.runAllReady();
        mCarUserSwitchingDialogController.handleHide();
        mExecutor.advanceClockToLast();
        mExecutor.runAllReady();

        verify(mOverlayViewGlobalStateController).hideView(eq(mCarUserSwitchingDialogController),
                any());
    }

    @Test
    public void onHide_notShowing_ignoresRequest() {
        mCarUserSwitchingDialogController.handleShow(/* newUserId= */ TEST_USER_1);
        mExecutor.advanceClockToLast();
        mExecutor.runAllReady();
        mCarUserSwitchingDialogController.handleHide();
        mExecutor.advanceClockToLast();
        mExecutor.runAllReady();
        mCarUserSwitchingDialogController.handleHide();
        mExecutor.advanceClockToLast();
        mExecutor.runAllReady();

        // Verify that the request was processed only once.
        verify(mOverlayViewGlobalStateController).hideView(eq(mCarUserSwitchingDialogController),
                any());
    }

    @Test
    public void onWindowShownTimeoutPassed_viewNotHidden_hidesUserSwitchTransitionView() {
        mCarUserSwitchingDialogController.handleShow(/* newUserId= */ TEST_USER_1);
        mExecutor.advanceClockToLast();
        mExecutor.runAllReady();
        reset(mOverlayViewGlobalStateController);

        mClock.advanceTime(mCarUserSwitchingDialogController.getWindowShownTimeoutMs() + 10);

        verify(mOverlayViewGlobalStateController).hideView(
                eq(mCarUserSwitchingDialogController), any());
    }

    @Test
    public void onWindowShownTimeoutPassed_viewHidden_doesNotHideUserSwitchTransitionViewAgain() {
        mCarUserSwitchingDialogController.handleShow(/* newUserId= */ TEST_USER_1);
        mExecutor.advanceClockToLast();
        mExecutor.runAllReady();
        mCarUserSwitchingDialogController.handleHide();
        mExecutor.advanceClockToLast();
        mExecutor.runAllReady();
        reset(mOverlayViewGlobalStateController);

        mClock.advanceTime(mCarUserSwitchingDialogController.getWindowShownTimeoutMs() + 10);

        verify(mOverlayViewGlobalStateController, never()).hideView(
                eq(mCarUserSwitchingDialogController), any());
    }

    @Test
    public void setupKeyguardShownTimeout_keyguardLocked_doNothing() throws RemoteException {
        mSetFlagsRule.enableFlags(FLAG_USER_SWITCH_KEYGUARD_SHOWN_TIMEOUT);
        when(mWindowManagerService.isKeyguardLocked()).thenReturn(true);

        mCarUserSwitchingDialogController.setupKeyguardShownTimeout();

        verify(mKeyguardManager, never()).addKeyguardLockedStateListener(any(), any());
    }

    @Test
    public void setupKeyguardShownTimeout_keyguardNotLocked_addTimeout() throws RemoteException {
        mSetFlagsRule.enableFlags(FLAG_USER_SWITCH_KEYGUARD_SHOWN_TIMEOUT);
        when(mWindowManagerService.isKeyguardLocked()).thenReturn(false);
        ArgumentCaptor<KeyguardManager.KeyguardLockedStateListener> lockedStateListenerCaptor =
                ArgumentCaptor.forClass(KeyguardManager.KeyguardLockedStateListener.class);

        mCarUserSwitchingDialogController.setupKeyguardShownTimeout();

        verify(mKeyguardManager).addKeyguardLockedStateListener(any(),
                lockedStateListenerCaptor.capture());
        assertThat(mExecutor.numPending()).isGreaterThan(0);
        lockedStateListenerCaptor.getValue().onKeyguardLockedStateChanged(true);
        verify(mKeyguardManager).removeKeyguardLockedStateListener(any());
    }

    private void mockGetUserInfo(int userId) {
        when(mMockUserManager.getUserInfo(userId))
                .thenReturn(new UserInfo(userId, "USER_" + userId, /* flags= */ 0));
    }

    private void mockGlobalShowView() {
        // Because mOverlayViewGlobalStateController is a mock, calls to show view will not execute
        // the runnable parameter. To simulate a more accurate real-world environment, any non-null
        // runnable will be manually executed.
        doAnswer(invocation -> {
            Object[] args = invocation.getArguments();
            if (args[1] == null || !(args[1] instanceof Runnable)) {
                return null;
            }
            Runnable runnable = (Runnable) args[1];
            runnable.run();
            return null;
        }).when(mOverlayViewGlobalStateController).showView(any(), any());
    }
}
