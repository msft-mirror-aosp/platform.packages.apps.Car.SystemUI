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

import static android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;

import static com.android.systemui.car.systembar.CarSystemBarController.BOTTOM;
import static com.android.systemui.car.systembar.CarSystemBarController.LEFT;
import static com.android.systemui.car.systembar.CarSystemBarController.RIGHT;
import static com.android.systemui.car.systembar.CarSystemBarController.TOP;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.res.Resources;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;
import android.util.ArrayMap;
import android.view.WindowManager;

import androidx.test.filters.SmallTest;

import com.android.systemui.R;
import com.android.systemui.SysuiTestCase;
import com.android.systemui.broadcast.BroadcastDispatcher;
import com.android.systemui.car.CarSystemUiTest;
import com.android.systemui.car.notification.NotificationPanelViewController;
import com.android.systemui.car.notification.NotificationPanelViewMediator;
import com.android.systemui.car.notification.PowerManagerHelper;
import com.android.systemui.car.notification.TopNotificationPanelViewMediator;
import com.android.systemui.settings.UserTracker;
import com.android.systemui.statusbar.policy.ConfigurationController;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@CarSystemUiTest
@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
@SmallTest
public class SystemBarConfigsTest extends SysuiTestCase {
    private static final int SYSTEM_BAR_GIRTH = 100;

    private SystemBarConfigsImpl mSystemBarConfigs;
    @Mock
    private Resources mResources;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        setDefaultValidConfig();
    }

    @Test
    public void onInit_allSystemBarsEnabled_eachHasUniqueBarTypes_doesNotThrowException() {
        mSystemBarConfigs = new SystemBarConfigsImpl(mContext, mResources);
    }

    @Test(expected = RuntimeException.class)
    public void onInit_allSystemBarsEnabled_twoBarsHaveDuplicateType_throwsRuntimeException() {
        when(mResources.getInteger(R.integer.config_topSystemBarType)).thenReturn(0);
        when(mResources.getInteger(R.integer.config_bottomSystemBarType)).thenReturn(0);

        mSystemBarConfigs = new SystemBarConfigsImpl(mContext, mResources);
    }

    @Test
    public void onInit_allSystemBarsEnabled_systemBarTypesSortedByZOrder() {
        mSystemBarConfigs = new SystemBarConfigsImpl(mContext, mResources);
        List<Integer> actualOrder = mSystemBarConfigs.getSystemBarSidesByZOrder();
        List<Integer> expectedOrder = new ArrayList<>();
        expectedOrder.add(LEFT);
        expectedOrder.add(RIGHT);
        expectedOrder.add(TOP);
        expectedOrder.add(BOTTOM);

        assertTrue(actualOrder.equals(expectedOrder));
    }

    @Test(expected = RuntimeException.class)
    public void onInit_intersectingBarsHaveSameZOrder_throwsRuntimeException() {
        when(mResources.getInteger(R.integer.config_topSystemBarZOrder)).thenReturn(33);
        when(mResources.getInteger(R.integer.config_leftSystemBarZOrder)).thenReturn(33);

        mSystemBarConfigs = new SystemBarConfigsImpl(mContext, mResources);
    }

    @Test(expected = RuntimeException.class)
    public void onInit_hideBottomSystemBarForKeyboardValueDoNotSync_throwsRuntimeException() {
        when(mResources.getBoolean(R.bool.config_hideBottomSystemBarForKeyboard)).thenReturn(false);
        when(mResources.getBoolean(
                com.android.internal.R.bool.config_hideNavBarForKeyboard)).thenReturn(
                true);

        mSystemBarConfigs = new SystemBarConfigsImpl(mContext, mResources);
    }

    @Test
    public void onInit_topNotifPanelViewMediatorUsed_topBarEnabled_doesNotThrowException() {
        when(mResources.getBoolean(R.bool.config_enableTopSystemBar)).thenReturn(true);
        when(mResources.getString(R.string.config_notificationPanelViewMediator)).thenReturn(
                TestTopNotificationPanelViewMediator.class.getName());

        mSystemBarConfigs = new SystemBarConfigsImpl(mContext, mResources);
    }

    @Test(expected = RuntimeException.class)
    public void onInit_topNotifPanelViewMediatorUsed_topBarNotEnabled_throwsRuntimeException() {
        when(mResources.getBoolean(R.bool.config_enableTopSystemBar)).thenReturn(false);
        when(mResources.getString(R.string.config_notificationPanelViewMediator)).thenReturn(
                TestTopNotificationPanelViewMediator.class.getName());

        mSystemBarConfigs = new SystemBarConfigsImpl(mContext, mResources);
    }

    @Test
    public void onInit_notificationPanelViewMediatorUsed_topBarNotEnabled_doesNotThrowException() {
        when(mResources.getBoolean(R.bool.config_enableTopSystemBar)).thenReturn(false);
        when(mResources.getString(R.string.config_notificationPanelViewMediator)).thenReturn(
                NotificationPanelViewMediator.class.getName());

        mSystemBarConfigs = new SystemBarConfigsImpl(mContext, mResources);
    }

    @Test
    public void getTopSystemBarLayoutParams_topBarEnabled_returnsTopSystemBarLayoutParams() {
        mSystemBarConfigs = new SystemBarConfigsImpl(mContext, mResources);
        WindowManager.LayoutParams lp = mSystemBarConfigs.getLayoutParamsBySide(
                TOP);

        assertNotNull(lp);
    }

    @Test
    public void getTopSystemBarLayoutParams_containsLayoutInDisplayCutoutMode() {
        mSystemBarConfigs = new SystemBarConfigsImpl(mContext, mResources);
        WindowManager.LayoutParams lp = mSystemBarConfigs.getLayoutParamsBySide(
                TOP);

        assertNotNull(lp);
        assertEquals(lp.layoutInDisplayCutoutMode, LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS);
    }

    @Test
    public void getTopSystemBarLayoutParams_topBarNotEnabled_returnsNull() {
        when(mResources.getBoolean(R.bool.config_enableTopSystemBar)).thenReturn(false);
        mSystemBarConfigs = new SystemBarConfigsImpl(mContext, mResources);
        WindowManager.LayoutParams lp = mSystemBarConfigs.getLayoutParamsBySide(
                TOP);

        assertNull(lp);
    }

    @Test
    public void getTopSystemBarHideForKeyboard_hideBarForKeyboard_returnsTrue() {
        when(mResources.getBoolean(R.bool.config_hideTopSystemBarForKeyboard)).thenReturn(true);
        mSystemBarConfigs = new SystemBarConfigsImpl(mContext, mResources);

        boolean hideKeyboard = mSystemBarConfigs.getHideForKeyboardBySide(TOP);

        assertTrue(hideKeyboard);
    }

    @Test
    public void getTopSystemBarHideForKeyboard_topBarNotEnabled_returnsFalse() {
        when(mResources.getBoolean(R.bool.config_enableTopSystemBar)).thenReturn(false);
        mSystemBarConfigs = new SystemBarConfigsImpl(mContext, mResources);

        boolean hideKeyboard = mSystemBarConfigs.getHideForKeyboardBySide(TOP);

        assertFalse(hideKeyboard);
    }

    @Test
    public void topSystemBarHasHigherZOrderThanHuns_topSystemBarIsSystemBarPanelType() {
        when(mResources.getInteger(R.integer.config_topSystemBarZOrder)).thenReturn(
                SystemBarConfigsImpl.HUN_Z_ORDER + 1);
        mSystemBarConfigs = new SystemBarConfigsImpl(mContext, mResources);
        WindowManager.LayoutParams lp = mSystemBarConfigs.getLayoutParamsBySide(
                TOP);

        assertEquals(lp.type, WindowManager.LayoutParams.TYPE_NAVIGATION_BAR_PANEL);
    }

    @Test
    public void topSystemBarHasLowerZOrderThanHuns_topSystemBarIsStatusBarAdditionalType() {
        when(mResources.getInteger(R.integer.config_topSystemBarZOrder)).thenReturn(
                SystemBarConfigsImpl.HUN_Z_ORDER - 1);
        mSystemBarConfigs = new SystemBarConfigsImpl(mContext, mResources);
        WindowManager.LayoutParams lp = mSystemBarConfigs.getLayoutParamsBySide(
                TOP);

        assertEquals(lp.type, WindowManager.LayoutParams.TYPE_STATUS_BAR_ADDITIONAL);
    }

    @Test
    public void updateInsetPaddings_overlappingBarWithHigherZOrderDisappeared_removesInset() {
        mSystemBarConfigs = new SystemBarConfigsImpl(mContext, mResources);
        CarSystemBarView leftBar = new CarSystemBarView(mContext, /* attrs= */ null);
        Map<Integer, Boolean> visibilities = new ArrayMap<>();
        visibilities.put(TOP, false);
        visibilities.put(BOTTOM, true);
        visibilities.put(LEFT, true);
        visibilities.put(RIGHT, true);

        mSystemBarConfigs.updateInsetPaddings(LEFT, visibilities);
        mSystemBarConfigs.insetSystemBar(LEFT, leftBar);

        assertEquals(0, leftBar.getPaddingTop());
    }

    @Test
    public void updateInsetPaddings_overlappingBarWithHigherZOrderReappeared_addsInset() {
        mSystemBarConfigs = new SystemBarConfigsImpl(mContext, mResources);
        CarSystemBarView leftBar = new CarSystemBarView(mContext, /* attrs= */ null);
        Map<Integer, Boolean> visibilities = new ArrayMap<>();
        visibilities.put(TOP, false);
        visibilities.put(BOTTOM, true);
        visibilities.put(LEFT, true);
        visibilities.put(RIGHT, true);

        mSystemBarConfigs.updateInsetPaddings(LEFT, visibilities);
        mSystemBarConfigs.insetSystemBar(LEFT, leftBar);
        visibilities.put(TOP, true);
        mSystemBarConfigs.updateInsetPaddings(LEFT, visibilities);
        mSystemBarConfigs.insetSystemBar(LEFT, leftBar);

        assertEquals(SYSTEM_BAR_GIRTH, leftBar.getPaddingTop());
    }

    @Test
    public void updateInsetPaddings_overlappingBars_addsPaddingsByOrders() {
        int horizontalBarHorizontalPadding = 150;
        int verticalBarVerticalPadding = 200;

        when(mResources.getDimensionPixelSize(R.dimen.car_top_system_bar_left_padding))
                .thenReturn(horizontalBarHorizontalPadding);
        when(mResources.getDimensionPixelSize(R.dimen.car_top_system_bar_right_padding))
                .thenReturn(horizontalBarHorizontalPadding);
        when(mResources.getDimensionPixelSize(R.dimen.car_bottom_system_bar_left_padding))
                .thenReturn(horizontalBarHorizontalPadding);
        when(mResources.getDimensionPixelSize(R.dimen.car_bottom_system_bar_right_padding))
                .thenReturn(horizontalBarHorizontalPadding);

        when(mResources.getDimensionPixelSize(R.dimen.car_left_system_bar_top_padding))
                .thenReturn(verticalBarVerticalPadding);
        when(mResources.getDimensionPixelSize(R.dimen.car_left_system_bar_bottom_padding))
                .thenReturn(verticalBarVerticalPadding);
        when(mResources.getDimensionPixelSize(R.dimen.car_right_system_bar_top_padding))
                .thenReturn(verticalBarVerticalPadding);
        when(mResources.getDimensionPixelSize(R.dimen.car_right_system_bar_bottom_padding))
                .thenReturn(verticalBarVerticalPadding);

        when(mResources.getDimensionPixelSize(
                R.dimen.car_left_system_bar_width)).thenReturn(SYSTEM_BAR_GIRTH);
        when(mResources.getDimensionPixelSize(
                R.dimen.car_right_system_bar_width)).thenReturn(SYSTEM_BAR_GIRTH);

        when(mResources.getDimensionPixelSize(
                R.dimen.car_top_system_bar_height)).thenReturn(SYSTEM_BAR_GIRTH);
        when(mResources.getDimensionPixelSize(
                R.dimen.car_bottom_system_bar_height)).thenReturn(SYSTEM_BAR_GIRTH);

        when(mResources.getBoolean(R.bool.config_enableTopSystemBar)).thenReturn(true);
        when(mResources.getBoolean(R.bool.config_enableBottomSystemBar)).thenReturn(true);
        when(mResources.getBoolean(R.bool.config_enableLeftSystemBar)).thenReturn(true);
        when(mResources.getBoolean(R.bool.config_enableRightSystemBar)).thenReturn(true);

        when(mResources.getInteger(R.integer.config_showDisplayCompatToolbarOnSystemBar))
                .thenReturn(0);

        when(mResources.getInteger(R.integer.config_topSystemBarZOrder)).thenReturn(7);
        when(mResources.getInteger(R.integer.config_bottomSystemBarZOrder)).thenReturn(10);
        when(mResources.getInteger(R.integer.config_leftSystemBarZOrder)).thenReturn(8);
        when(mResources.getInteger(R.integer.config_rightSystemBarZOrder)).thenReturn(6);

        mSystemBarConfigs = new SystemBarConfigsImpl(mContext, mResources);

        CarSystemBarView topBar = new CarSystemBarView(mContext, /* attrs= */ null);
        CarSystemBarView bottomBar = new CarSystemBarView(mContext, /* attrs= */ null);
        CarSystemBarView leftBar = new CarSystemBarView(mContext, /* attrs= */ null);
        CarSystemBarView rightBar = new CarSystemBarView(mContext, /* attrs= */ null);

        Map<Integer, Boolean> visibilities = new ArrayMap<>();
        visibilities.put(TOP, true);
        visibilities.put(BOTTOM, true);
        visibilities.put(LEFT, true);
        visibilities.put(RIGHT, true);

        mSystemBarConfigs.updateInsetPaddings(TOP, visibilities);
        mSystemBarConfigs.insetSystemBar(TOP, topBar);
        mSystemBarConfigs.updateInsetPaddings(BOTTOM, visibilities);
        mSystemBarConfigs.insetSystemBar(BOTTOM, bottomBar);
        mSystemBarConfigs.updateInsetPaddings(LEFT, visibilities);
        mSystemBarConfigs.insetSystemBar(LEFT, leftBar);
        mSystemBarConfigs.updateInsetPaddings(RIGHT, visibilities);
        mSystemBarConfigs.insetSystemBar(RIGHT, rightBar);

        assertEquals(horizontalBarHorizontalPadding, bottomBar.getPaddingLeft());
        assertEquals(horizontalBarHorizontalPadding, bottomBar.getPaddingRight());
        assertEquals(horizontalBarHorizontalPadding, topBar.getPaddingRight());
        assertEquals(SYSTEM_BAR_GIRTH, topBar.getPaddingLeft());
        assertEquals(verticalBarVerticalPadding, leftBar.getPaddingTop());
        assertEquals(SYSTEM_BAR_GIRTH, leftBar.getPaddingBottom());
        assertEquals(SYSTEM_BAR_GIRTH, rightBar.getPaddingTop());
        assertEquals(SYSTEM_BAR_GIRTH, rightBar.getPaddingBottom());
    }

    // Set valid config where all system bars are enabled.
    private void setDefaultValidConfig() {
        when(mResources.getBoolean(R.bool.config_enableTopSystemBar)).thenReturn(true);
        when(mResources.getBoolean(R.bool.config_enableBottomSystemBar)).thenReturn(true);
        when(mResources.getBoolean(R.bool.config_enableLeftSystemBar)).thenReturn(true);
        when(mResources.getBoolean(R.bool.config_enableRightSystemBar)).thenReturn(true);

        when(mResources.getDimensionPixelSize(
                R.dimen.car_top_system_bar_height)).thenReturn(SYSTEM_BAR_GIRTH);
        when(mResources.getDimensionPixelSize(
                R.dimen.car_bottom_system_bar_height)).thenReturn(SYSTEM_BAR_GIRTH);
        when(mResources.getDimensionPixelSize(R.dimen.car_left_system_bar_width)).thenReturn(
                SYSTEM_BAR_GIRTH);
        when(mResources.getDimensionPixelSize(R.dimen.car_right_system_bar_width)).thenReturn(
                SYSTEM_BAR_GIRTH);

        when(mResources.getInteger(R.integer.config_topSystemBarType)).thenReturn(0);
        when(mResources.getInteger(R.integer.config_bottomSystemBarType)).thenReturn(1);
        when(mResources.getInteger(R.integer.config_leftSystemBarType)).thenReturn(2);
        when(mResources.getInteger(R.integer.config_rightSystemBarType)).thenReturn(3);

        when(mResources.getInteger(R.integer.config_topSystemBarZOrder)).thenReturn(5);
        when(mResources.getInteger(R.integer.config_bottomSystemBarZOrder)).thenReturn(10);
        when(mResources.getInteger(R.integer.config_leftSystemBarZOrder)).thenReturn(2);
        when(mResources.getInteger(R.integer.config_rightSystemBarZOrder)).thenReturn(3);

        when(mResources.getBoolean(R.bool.config_hideTopSystemBarForKeyboard)).thenReturn(false);
        when(mResources.getBoolean(
                com.android.internal.R.bool.config_hideNavBarForKeyboard)).thenReturn(
                false);
        when(mResources.getBoolean(R.bool.config_hideLeftSystemBarForKeyboard)).thenReturn(
                false);
        when(mResources.getBoolean(R.bool.config_hideRightSystemBarForKeyboard)).thenReturn(
                false);
    }

    // Intentionally using a subclass of TopNotificationPanelViewMediator for testing purposes to
    // ensure that OEM's will be able to implement and use their own NotificationPanelViewMediator.
    private class TestTopNotificationPanelViewMediator extends
            TopNotificationPanelViewMediator {
        TestTopNotificationPanelViewMediator(
                Context context,
                CarSystemBarController carSystemBarController,
                NotificationPanelViewController notificationPanelViewController,
                PowerManagerHelper powerManagerHelper,
                BroadcastDispatcher broadcastDispatcher,
                UserTracker userTracker,
                ConfigurationController configurationController) {
            super(context, carSystemBarController, notificationPanelViewController,
                    powerManagerHelper, broadcastDispatcher, userTracker,
                    configurationController);
        }
    }
}
