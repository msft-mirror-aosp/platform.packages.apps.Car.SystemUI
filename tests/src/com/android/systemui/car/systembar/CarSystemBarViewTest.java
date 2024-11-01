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

package com.android.systemui.car.systembar;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.when;

import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;

import androidx.test.filters.SmallTest;

import com.android.systemui.R;
import com.android.systemui.SysuiTestCase;
import com.android.systemui.car.CarSystemUiTest;
import com.android.systemui.car.notification.NotificationPanelViewController;
import com.android.systemui.car.systembar.element.CarSystemBarElementInitializer;
import com.android.systemui.car.window.OverlayVisibilityMediator;
import com.android.systemui.settings.UserTracker;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collections;

@CarSystemUiTest
@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
@SmallTest
public class CarSystemBarViewTest extends SysuiTestCase {

    private CarSystemBarView mNavBarView;

    @Mock
    private NotificationPanelViewController mNotificationPanelViewController;

    @Mock
    private View.OnTouchListener mNavBarTouchListener;

    @Mock
    private UserTracker mUserTracker;
    @Mock
    private CarSystemBarElementInitializer mCarSystemBarElementInitializer;
    @Mock
    private ButtonRoleHolderController mButtonRoleHolderController;
    @Mock
    private ButtonSelectionStateController mButtonSelectionStateController;
    @Mock
    private MicPrivacyChipViewController mMicPrivacyChipViewController;
    @Mock
    private CameraPrivacyChipViewController mCameraPrivacyChipViewController;
    @Mock
    private OverlayVisibilityMediator mOverlayVisibilityMediator;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @After
    public void tearDown() {
        getContext().getOrCreateTestableResources().addOverride(
                R.bool.config_consumeSystemBarTouchWhenNotificationPanelOpen, false);
        getContext().getOrCreateTestableResources().addOverride(
                R.bool.config_systemBarButtonsDraggable, false);
    }

    @Test
    public void dispatchTouch_shadeOpen_flagOff_doesNotConsumeTouch() {
        getContext().getOrCreateTestableResources().addOverride(
                R.bool.config_consumeSystemBarTouchWhenNotificationPanelOpen, false);
        when(mOverlayVisibilityMediator.getHighestZOrderOverlayViewController())
                .thenReturn(mNotificationPanelViewController);
        when(mNotificationPanelViewController.shouldPanelConsumeSystemBarTouch())
                .thenReturn(true);
        mNavBarView = (CarSystemBarView) LayoutInflater.from(getContext()).inflate(
                R.layout.car_system_bar_view_test, /* root= */ null);
        CarSystemBarViewControllerImpl controller = getSystemBarViewController(mNavBarView);
        controller.setSystemBarTouchListeners(
                Collections.singleton(mNavBarTouchListener));

        boolean consume = controller.onInterceptTouchEvent(
                MotionEvent.obtain(/* downTime= */ 200, /* eventTime= */ 300,
                        MotionEvent.ACTION_MOVE, mNavBarView.getX(),
                        mNavBarView.getY(), /* metaState= */ 0));

        assertThat(consume).isFalse();
    }

    @Test
    public void dispatchTouch_shadeOpen_flagOn_consumesTouch() {
        getContext().getOrCreateTestableResources().addOverride(
                R.bool.config_consumeSystemBarTouchWhenNotificationPanelOpen, true);
        // Prevent the test from failing due to buttons on the system bar not being draggable.
        getContext().getOrCreateTestableResources().addOverride(
                R.bool.config_systemBarButtonsDraggable, true);
        when(mOverlayVisibilityMediator.getHighestZOrderOverlayViewController())
                .thenReturn(mNotificationPanelViewController);
        when(mNotificationPanelViewController.shouldPanelConsumeSystemBarTouch())
                .thenReturn(true);
        mNavBarView = (CarSystemBarView) LayoutInflater.from(getContext()).inflate(
                R.layout.car_system_bar_view_test, /* root= */ null);
        CarSystemBarViewControllerImpl controller = getSystemBarViewController(mNavBarView);
        controller.setSystemBarTouchListeners(
                Collections.singleton(mNavBarTouchListener));

        boolean consume = controller.onInterceptTouchEvent(
                MotionEvent.obtain(/* downTime= */ 200, /* eventTime= */ 300,
                        MotionEvent.ACTION_MOVE, mNavBarView.getX(),
                        mNavBarView.getY(), /* metaState= */ 0));

        assertThat(consume).isTrue();
    }

    private CarSystemBarViewControllerImpl getSystemBarViewController(CarSystemBarView view) {
        SystemBarConfigs systemBarConfigs = new SystemBarConfigs(getContext(),
                getContext().getOrCreateTestableResources().getResources());
        return new CarSystemBarViewControllerImpl(getContext(),
                mUserTracker,
                mCarSystemBarElementInitializer,
                systemBarConfigs,
                mButtonRoleHolderController,
                mButtonSelectionStateController,
                () -> mCameraPrivacyChipViewController,
                () -> mMicPrivacyChipViewController,
                mOverlayVisibilityMediator,
                0,
                view);
    }
}
