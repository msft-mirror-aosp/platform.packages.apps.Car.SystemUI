/*
 * Copyright (C) 2024 The Android Open Source Project
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

import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.IdRes;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.android.car.ui.FocusParkingView;
import com.android.car.ui.utils.ViewUtils;
import com.android.systemui.R;
import com.android.systemui.car.hvac.HvacController;
import com.android.systemui.car.hvac.HvacPanelController;
import com.android.systemui.car.hvac.HvacPanelOverlayViewController;
import com.android.systemui.car.notification.NotificationPanelViewController;
import com.android.systemui.car.notification.NotificationsShadeController;
import com.android.systemui.car.systembar.CarSystemBarController.SystemBarSide;
import com.android.systemui.car.systembar.CarSystemBarView.ButtonsType;
import com.android.systemui.car.systembar.element.CarSystemBarElementInitializer;
import com.android.systemui.settings.UserTracker;
import com.android.systemui.util.ViewController;

import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;
import dagger.assisted.AssistedInject;

import java.util.Set;

/**
 * A controller for initializing the CarSystemBarView instances.
 */
public class CarSystemBarViewController extends ViewController<CarSystemBarView> {

    private static final String LAST_FOCUSED_VIEW_ID = "last_focused_view_id";
    private static final String IS_PROFILE_PICKER_OPEN = "is_profile_picker_open";

    private final Context mContext;
    private final UserTracker mUserTracker;
    private final CarSystemBarElementInitializer mCarSystemBarElementInitializer;
    private final HvacController mHvacController;
    private final SystemBarConfigs mSystemBarConfigs;
    private final @SystemBarSide int mSide;

    @AssistedInject
    public CarSystemBarViewController(Context context,
            UserTracker userTracker,
            CarSystemBarElementInitializer elementInitializer,
            HvacController hvacController,
            SystemBarConfigs systemBarConfigs,
            @Assisted @SystemBarSide int side,
            @Assisted CarSystemBarView systemBarView) {
        super(systemBarView);

        mContext = context;
        mUserTracker = userTracker;
        mCarSystemBarElementInitializer = elementInitializer;
        mHvacController = hvacController;
        mSystemBarConfigs = systemBarConfigs;
        mSide = side;
    }

    @Override
    protected void onInit() {
        mView.setupSystemBarButtons(mUserTracker);
        mCarSystemBarElementInitializer.initializeCarSystemBarElements(mView);

        // Include a FocusParkingView at the beginning. The rotary controller "parks" the focus here
        // when the user navigates to another window. This is also used to prevent wrap-around.
        mView.addView(new FocusParkingView(mContext), 0);
    }

    /**
     * Call to save the internal state.
     */
    public void onSaveInstanceState(Bundle outState) {
        // The focused view will be destroyed during re-layout, causing the framework to adjust
        // the focus unexpectedly. To avoid that, move focus to a view that won't be
        // destroyed during re-layout and has no focus highlight (the FocusParkingView), then
        // move focus back to the previously focused view after re-layout.
        outState.putInt(LAST_FOCUSED_VIEW_ID, cacheAndHideFocus(mView));

        boolean isProfilePickerOpen = false;
        View profilePickerView = mView.findViewById(R.id.user_name);
        if (profilePickerView != null) isProfilePickerOpen = profilePickerView.isSelected();
        if (isProfilePickerOpen) {
            profilePickerView.callOnClick();
        }
        outState.putBoolean(IS_PROFILE_PICKER_OPEN, isProfilePickerOpen);
    }

    /**
     * Call to restore the internal state.
     */
    public void onRestoreInstanceState(Bundle savedInstanceState) {
        boolean isProfilePickerOpen = savedInstanceState.getBoolean(IS_PROFILE_PICKER_OPEN, false);
        if (isProfilePickerOpen) {
            View profilePickerView = mView.findViewById(R.id.user_name);
            if (profilePickerView != null) profilePickerView.callOnClick();
        }

        restoreFocus(mView, savedInstanceState.getInt(LAST_FOCUSED_VIEW_ID, View.NO_ID));
    }

    // TODO(b/372065319): will be removed
    public ViewGroup getView() {
        return mView;
    }

    /**
     * Sets the system bar view's disabled state and runnable when disabled.
     */
    public void setDisabledSystemBarButton(int viewId, boolean disabled, Runnable runnable,
                @Nullable String buttonName) {
        mView.setDisabledSystemBarButton(viewId, disabled, runnable, buttonName);
    }

    /**
     * Update home buttom visibility.
     */
    public void updateHomeButtonVisibility(boolean isPassenger) {
        mView.updateHomeButtonVisibility(isPassenger);
    }

    /**
     * Sets the touch listeners that will be called from onInterceptTouchEvent and onTouchEvent
     *
     * @param statusBarWindowTouchListeners List of listeners to call from touch and intercept touch
     */
    public void setStatusBarWindowTouchListeners(
            Set<View.OnTouchListener> statusBarWindowTouchListeners) {
        mView.setStatusBarWindowTouchListeners(statusBarWindowTouchListeners);
    }

    /** Sets the notifications panel controller. */
    public void setNotificationsPanelController(NotificationsShadeController controller) {
        mView.setNotificationsPanelController(controller);
    }

    /**
     * Sets the NotificationPanelViewController and adds button listeners
     */
    public void registerNotificationPanelViewController(
            NotificationPanelViewController controller) {
        mView.registerNotificationPanelViewController(controller);
    }

    /** Sets the HVAC panel controller. */
    public void setHvacPanelController(HvacPanelController controller) {
        mView.setHvacPanelController(controller);
    }

    /**
     * Sets the HvacPanelOverlayViewController and adds HVAC button listeners
     */
    public void registerHvacPanelOverlayViewController(HvacPanelOverlayViewController controller) {
        mView.registerHvacPanelOverlayViewController(controller);
    }

    /**
     * Update control center button visibility.
     */
    public void updateControlCenterButtonVisibility(boolean isMumd) {
        mView.updateControlCenterButtonVisibility(isMumd);
    }

    /**
     * Shows buttons of the specified {@link ButtonsType}.
     *
     * NOTE: Only one type of buttons can be shown at a time, so showing buttons of one type will
     * hide all buttons of other types.
     *
     * @param buttonsType see {@link ButtonsType}
     */
    public void showButtonsOfType(@ButtonsType int buttonsType) {
        mView.showButtonsOfType(buttonsType);
    }

    /**
     * Toggles the notification unseen indicator on/off.
     *
     * @param hasUnseen true if the unseen notification count is great than 0.
     */
    public void toggleNotificationUnseenIndicator(boolean hasUnseen) {
        mView.toggleNotificationUnseenIndicator(hasUnseen);
    }

    /** Gets the touch listeners that will be called from onInterceptTouchEvent and onTouchEvent. */
    @VisibleForTesting
    Set<View.OnTouchListener> getStatusBarWindowTouchListeners() {
        return mView.getStatusBarWindowTouchListeners();
    }

    /** Gets the notifications panel controller. */
    @VisibleForTesting
    NotificationsShadeController getNotificationsPanelController() {
        return mView.getNotificationsPanelController();
    }

    /** Gets the HVAC panel controller. */
    @VisibleForTesting
    HvacPanelController getHvacPanelController() {
        return mView.getHvacPanelController();
    }

    @Override
    protected void onViewAttached() {
        mSystemBarConfigs.insetSystemBar(mSide, mView);
        mHvacController.registerHvacViews(mView);
    }

    @Override
    protected void onViewDetached() {
        mHvacController.unregisterViews(mView);
    }

    @AssistedFactory
    public interface Factory {
        /** Create instance of CarSystemBarViewController for CarSystemBarView */
        CarSystemBarViewController create(@SystemBarSide int side, CarSystemBarView view);
    }

    @VisibleForTesting
    static int cacheAndHideFocus(@Nullable View rootView) {
        if (rootView == null) return View.NO_ID;
        View focusedView = rootView.findFocus();
        if (focusedView == null || focusedView instanceof FocusParkingView) return View.NO_ID;
        int focusedViewId = focusedView.getId();
        ViewUtils.hideFocus(rootView);
        return focusedViewId;
    }

    private static boolean restoreFocus(@Nullable View rootView, @IdRes int viewToFocusId) {
        if (rootView == null || viewToFocusId == View.NO_ID) return false;
        View focusedView = rootView.findViewById(viewToFocusId);
        if (focusedView == null) return false;
        focusedView.requestFocus();
        return true;
    }
}
