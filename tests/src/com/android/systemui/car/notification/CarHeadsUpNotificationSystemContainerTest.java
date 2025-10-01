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

package com.android.systemui.car.notification;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;

import androidx.test.filters.SmallTest;

import com.android.car.notification.CarNotificationTypeItem;
import com.android.systemui.CarSysuiTestCase;
import com.android.systemui.car.CarDeviceProvisionedController;
import com.android.systemui.car.CarSystemUiTest;
import com.android.systemui.car.window.OverlayViewGlobalStateController;
import com.android.systemui.car.wm.scalableui.systemwindow.HunWindow;
import com.android.systemui.car.wm.scalableui.systemwindow.SystemUiWindowProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Optional;

@CarSystemUiTest
@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
@SmallTest
public class CarHeadsUpNotificationSystemContainerTest extends CarSysuiTestCase {
    private CarHeadsUpNotificationSystemContainer mCarHeadsUpNotificationSystemContainer;
    @Mock
    private CarDeviceProvisionedController mCarDeviceProvisionedController;
    @Mock
    private OverlayViewGlobalStateController mOverlayViewGlobalStateController;
    @Mock
    private SystemUiWindowProvider mSystemUiWindowProvider;
    @Mock
    private HunWindow mHunWindow;
    @Mock
    private View mNotificationView;
    @Mock
    private View mNotificationView2;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(/* testClass= */this);

        when(mOverlayViewGlobalStateController.shouldShowHUN()).thenReturn(true);
        when(mCarDeviceProvisionedController.isCurrentUserFullySetup()).thenReturn(true);
        when(mSystemUiWindowProvider.getHunWindow()).thenReturn(Optional.of(mHunWindow));
        when(mHunWindow.getLayoutParams()).thenReturn(new WindowManager.LayoutParams());
        when(mNotificationView.getLayoutParams()).thenReturn(
                new ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));
        when(mNotificationView2.getLayoutParams()).thenReturn(
                new ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));

        mContext.ensureTestableResources();

        mCarHeadsUpNotificationSystemContainer = new CarHeadsUpNotificationSystemContainer(mContext,
                mCarDeviceProvisionedController, mOverlayViewGlobalStateController,
                mSystemUiWindowProvider);
    }

    @Test
    public void initializeVisibility_hunWindowPresent_doesNothing() {
        // Set initial visibility to something other than INVISIBLE
        mCarHeadsUpNotificationSystemContainer.getHunRootView().setVisibility(View.VISIBLE);

        mCarHeadsUpNotificationSystemContainer.initializeVisibility();

        // Visibility should not be changed by initializeVisibility when HunWindow is present
        assertThat(mCarHeadsUpNotificationSystemContainer.getHunRootView().getVisibility())
                .isEqualTo(View.VISIBLE);
    }

    @Test
    public void initializeVisibility_hunWindowNotPresent_setsInvisible() {
        when(mSystemUiWindowProvider.getHunWindow()).thenReturn(Optional.empty());
        mCarHeadsUpNotificationSystemContainer = new CarHeadsUpNotificationSystemContainer(mContext,
                mCarDeviceProvisionedController, mOverlayViewGlobalStateController,
                mSystemUiWindowProvider);
        // Set initial visibility to something other than INVISIBLE
        mCarHeadsUpNotificationSystemContainer.getHunRootView().setVisibility(View.VISIBLE);

        mCarHeadsUpNotificationSystemContainer.initializeVisibility();

        assertThat(mCarHeadsUpNotificationSystemContainer.getHunRootView().getVisibility())
                .isEqualTo(View.INVISIBLE);
    }

    @Test
    public void displayNotification_firstNotification_presentsContainer() {
        mCarHeadsUpNotificationSystemContainer.displayNotification(mNotificationView,
                CarNotificationTypeItem.INBOX);
        verify(mHunWindow).show();
    }

    @Test
    public void removeNotification_lastNotification_dismissesContainer() {
        mCarHeadsUpNotificationSystemContainer.displayNotification(mNotificationView,
                CarNotificationTypeItem.INBOX);
        mCarHeadsUpNotificationSystemContainer.removeNotification(mNotificationView);
        verify(mHunWindow).hide();
    }

    @Test
    public void removeNotification_nonLastNotification_doesNotDismissContainer() {
        mCarHeadsUpNotificationSystemContainer.displayNotification(mNotificationView,
                CarNotificationTypeItem.INBOX);
        mCarHeadsUpNotificationSystemContainer.displayNotification(mNotificationView2,
                CarNotificationTypeItem.INBOX);
        reset(mHunWindow);

        mCarHeadsUpNotificationSystemContainer.removeNotification(mNotificationView);

        verify(mHunWindow, never()).hide();
    }

    @Test
    public void displayNotification_userFullySetupTrue_presentsContainer() {
        mCarHeadsUpNotificationSystemContainer.displayNotification(mNotificationView,
                CarNotificationTypeItem.INBOX);
        verify(mHunWindow).show();
    }

    @Test
    public void displayNotification_userFullySetupFalse_doesNotPresentContainer() {
        when(mCarDeviceProvisionedController.isCurrentUserFullySetup()).thenReturn(false);
        mCarHeadsUpNotificationSystemContainer.displayNotification(mNotificationView,
                CarNotificationTypeItem.INBOX);
        verify(mHunWindow, never()).show();
    }

    @Test
    public void displayNotification_overlayWindowStateShouldShowHUNFalse_doesNotPresentContainer() {
        when(mOverlayViewGlobalStateController.shouldShowHUN()).thenReturn(false);
        mCarHeadsUpNotificationSystemContainer.displayNotification(mNotificationView,
                CarNotificationTypeItem.INBOX);
        verify(mHunWindow, never()).show();
    }

    @Test
    public void displayNotification_overlayWindowStateShouldShowHUNTrue_presentsContainer() {
        mCarHeadsUpNotificationSystemContainer.displayNotification(mNotificationView,
                CarNotificationTypeItem.INBOX);
        verify(mHunWindow).show();
    }

    @Test
    public void presentContainer_hunWindowPresent_showIsCalled() {
        mCarHeadsUpNotificationSystemContainer.presentContainer();

        verify(mHunWindow).show();
    }

    @Test
    public void dismissContainer_hunWindowPresent_hideIsCalled() {
        mCarHeadsUpNotificationSystemContainer.dismissContainer();

        verify(mHunWindow).hide();
    }

    @Test
    public void presentContainer_hunWindowNotPresent_containerIsVisible() {
        when(mSystemUiWindowProvider.getHunWindow()).thenReturn(Optional.empty());
        mCarHeadsUpNotificationSystemContainer = new CarHeadsUpNotificationSystemContainer(mContext,
            mCarDeviceProvisionedController, mOverlayViewGlobalStateController,
            mSystemUiWindowProvider);

        mCarHeadsUpNotificationSystemContainer.presentContainer();

        assertThat(mCarHeadsUpNotificationSystemContainer.isVisible()).isTrue();
    }

    @Test
    public void dismissContainer_hunWindowNotPresent_containerIsInvisible() {
        when(mSystemUiWindowProvider.getHunWindow()).thenReturn(Optional.empty());
        mCarHeadsUpNotificationSystemContainer = new CarHeadsUpNotificationSystemContainer(mContext,
            mCarDeviceProvisionedController, mOverlayViewGlobalStateController,
            mSystemUiWindowProvider);
        mCarHeadsUpNotificationSystemContainer.presentContainer();

        mCarHeadsUpNotificationSystemContainer.dismissContainer();

        assertThat(mCarHeadsUpNotificationSystemContainer.isVisible()).isFalse();
    }
}
