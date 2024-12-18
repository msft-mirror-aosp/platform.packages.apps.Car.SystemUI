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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;

import androidx.test.filters.SmallTest;

import com.android.systemui.SysuiTestCase;
import com.android.systemui.car.CarServiceProvider;
import com.android.systemui.car.CarSystemUiTest;
import com.android.systemui.settings.UserTracker;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@CarSystemUiTest
@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
@SmallTest
public class UserSwitchTransitionViewMediatorTest extends SysuiTestCase {
    private static final int TEST_USER = 100;

    private UserSwitchTransitionViewMediator mUserSwitchTransitionViewMediator;
    @Mock
    private CarServiceProvider mCarServiceProvider;
    @Mock
    private UserTracker mUserTracker;
    @Mock
    private UserSwitchTransitionViewController mUserSwitchTransitionViewController;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        mUserSwitchTransitionViewMediator = new UserSwitchTransitionViewMediator(mContext,
                mCarServiceProvider, mUserTracker,
                mUserSwitchTransitionViewController);
    }

    @Test
    public void registerListeners_addsUserTrackerCallback() {
        mUserSwitchTransitionViewMediator.registerListeners();

        verify(mUserTracker).addCallback(any(), any());
    }

    @Test
    public void onUserLifecycleEvent_beforeUserSwitching_callsHandleShow() {
        mUserSwitchTransitionViewMediator.mUserChangedCallback.onBeforeUserSwitching(TEST_USER);

        verify(mUserSwitchTransitionViewController).handleShow(TEST_USER);
    }

    @Test
    public void onUserLifecycleEvent_onUserChanging_callsHandleSwitching() {
        mUserSwitchTransitionViewMediator.mUserChangedCallback.onUserChanging(TEST_USER, mContext);

        verify(mUserSwitchTransitionViewController).handleSwitching(TEST_USER);
    }

    @Test
    public void onUserLifecycleEvent_onUserChanged_callsHandleHide() {
        mUserSwitchTransitionViewMediator.mUserChangedCallback.onUserChanged(TEST_USER, mContext);

        verify(mUserSwitchTransitionViewController).handleHide();
    }

    @Test
    public void onShowUserSwitchDialog_callsHandleShow() {
        mUserSwitchTransitionViewMediator.showUserSwitchDialog(TEST_USER);

        verify(mUserSwitchTransitionViewController).handleShow(TEST_USER);
    }
}
