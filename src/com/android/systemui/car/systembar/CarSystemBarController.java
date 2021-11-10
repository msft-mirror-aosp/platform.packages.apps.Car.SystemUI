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

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.Nullable;

import com.android.systemui.R;
import com.android.systemui.broadcast.BroadcastDispatcher;
import com.android.systemui.car.CarServiceProvider;
import com.android.systemui.car.hvac.HvacPanelOverlayViewController;
import com.android.systemui.car.privacy.MicQcPanel;
import com.android.systemui.car.statusbar.UserNameViewController;
import com.android.systemui.car.statusicon.StatusIconPanelController;
import com.android.systemui.dagger.SysUISingleton;
import com.android.systemui.statusbar.policy.ConfigurationController;

import javax.inject.Inject;

import dagger.Lazy;

/** A single class which controls the navigation bar views. */
@SysUISingleton
public class CarSystemBarController {

    private final Context mContext;
    private final CarSystemBarViewFactory mCarSystemBarViewFactory;
    private final CarServiceProvider mCarServiceProvider;
    private final BroadcastDispatcher mBroadcastDispatcher;
    private final ConfigurationController mConfigurationController;
    private final ButtonSelectionStateController mButtonSelectionStateController;
    private final ButtonRoleHolderController mButtonRoleHolderController;
    private final Lazy<UserNameViewController> mUserNameViewControllerLazy;
    private final Lazy<PrivacyChipViewController> mPrivacyChipViewControllerLazy;
    private final Lazy<MicQcPanel.MicPrivacyElementsProvider> mMicPrivacyElementsProviderLazy;

    private final boolean mShowTop;
    private final boolean mShowBottom;
    private final boolean mShowLeft;
    private final boolean mShowRight;

    private View.OnTouchListener mTopBarTouchListener;
    private View.OnTouchListener mBottomBarTouchListener;
    private View.OnTouchListener mLeftBarTouchListener;
    private View.OnTouchListener mRightBarTouchListener;
    private NotificationsShadeController mNotificationsShadeController;
    private HvacPanelController mHvacPanelController;
    private StatusIconPanelController mMicPanelController;
    private StatusIconPanelController mProfilePanelController;
    private HvacPanelOverlayViewController mHvacPanelOverlayViewController;

    private CarSystemBarView mTopView;
    private CarSystemBarView mBottomView;
    private CarSystemBarView mLeftView;
    private CarSystemBarView mRightView;

    @Inject
    public CarSystemBarController(Context context,
            CarSystemBarViewFactory carSystemBarViewFactory,
            CarServiceProvider carServiceProvider,
            BroadcastDispatcher broadcastDispatcher,
            ConfigurationController configurationController,
            ButtonSelectionStateController buttonSelectionStateController,
            Lazy<UserNameViewController> userNameViewControllerLazy,
            Lazy<PrivacyChipViewController> privacyChipViewControllerLazy,
            ButtonRoleHolderController buttonRoleHolderController,
            SystemBarConfigs systemBarConfigs,
            Lazy<MicQcPanel.MicPrivacyElementsProvider> micPrivacyElementsProvider) {
        mContext = context;
        mCarSystemBarViewFactory = carSystemBarViewFactory;
        mCarServiceProvider = carServiceProvider;
        mBroadcastDispatcher = broadcastDispatcher;
        mConfigurationController = configurationController;
        mButtonSelectionStateController = buttonSelectionStateController;
        mUserNameViewControllerLazy = userNameViewControllerLazy;
        mPrivacyChipViewControllerLazy = privacyChipViewControllerLazy;
        mButtonRoleHolderController = buttonRoleHolderController;
        mMicPrivacyElementsProviderLazy = micPrivacyElementsProvider;

        // Read configuration.
        mShowTop = systemBarConfigs.getEnabledStatusBySide(SystemBarConfigs.TOP);
        mShowBottom = systemBarConfigs.getEnabledStatusBySide(SystemBarConfigs.BOTTOM);
        mShowLeft = systemBarConfigs.getEnabledStatusBySide(SystemBarConfigs.LEFT);
        mShowRight = systemBarConfigs.getEnabledStatusBySide(SystemBarConfigs.RIGHT);
    }

    /**
     * Hides all system bars.
     */
    public void hideBars() {
        setTopWindowVisibility(View.GONE);
        setBottomWindowVisibility(View.GONE);
        setLeftWindowVisibility(View.GONE);
        setRightWindowVisibility(View.GONE);
    }

    /**
     * Shows all system bars.
     */
    public void showBars() {
        setTopWindowVisibility(View.VISIBLE);
        setBottomWindowVisibility(View.VISIBLE);
        setLeftWindowVisibility(View.VISIBLE);
        setRightWindowVisibility(View.VISIBLE);
    }

    /** Clean up */
    public void removeAll() {
        mButtonSelectionStateController.removeAll();
        mButtonRoleHolderController.removeAll();
        mUserNameViewControllerLazy.get().removeAll();
        mPrivacyChipViewControllerLazy.get().removeAll();
        mMicPanelController = null;
        mProfilePanelController = null;
    }

    /** Gets the top window if configured to do so. */
    @Nullable
    public ViewGroup getTopWindow() {
        return mShowTop ? mCarSystemBarViewFactory.getTopWindow() : null;
    }

    /** Gets the bottom window if configured to do so. */
    @Nullable
    public ViewGroup getBottomWindow() {
        return mShowBottom ? mCarSystemBarViewFactory.getBottomWindow() : null;
    }

    /** Gets the left window if configured to do so. */
    @Nullable
    public ViewGroup getLeftWindow() {
        return mShowLeft ? mCarSystemBarViewFactory.getLeftWindow() : null;
    }

    /** Gets the right window if configured to do so. */
    @Nullable
    public ViewGroup getRightWindow() {
        return mShowRight ? mCarSystemBarViewFactory.getRightWindow() : null;
    }

    /** Toggles the top nav bar visibility. */
    public boolean setTopWindowVisibility(@View.Visibility int visibility) {
        return setWindowVisibility(getTopWindow(), visibility);
    }

    /** Toggles the bottom nav bar visibility. */
    public boolean setBottomWindowVisibility(@View.Visibility int visibility) {
        return setWindowVisibility(getBottomWindow(), visibility);
    }

    /** Toggles the left nav bar visibility. */
    public boolean setLeftWindowVisibility(@View.Visibility int visibility) {
        return setWindowVisibility(getLeftWindow(), visibility);
    }

    /** Toggles the right nav bar visibility. */
    public boolean setRightWindowVisibility(@View.Visibility int visibility) {
        return setWindowVisibility(getRightWindow(), visibility);
    }

    private boolean setWindowVisibility(ViewGroup window, @View.Visibility int visibility) {
        if (window == null) {
            return false;
        }

        if (window.getVisibility() == visibility) {
            return false;
        }

        window.setVisibility(visibility);
        return true;
    }

    /** Gets the top navigation bar with the appropriate listeners set. */
    @Nullable
    public CarSystemBarView getTopBar(boolean isSetUp) {
        if (!mShowTop) {
            return null;
        }

        mTopView = mCarSystemBarViewFactory.getTopBar(isSetUp);
        setupBar(mTopView, mTopBarTouchListener, mNotificationsShadeController,
                mHvacPanelController, mHvacPanelOverlayViewController);

        if (isSetUp) {
            // We do not want the mic privacy chip or the profile picker to be clickable in
            // unprovisioned mode.
            setupMicQcPanel();
            setupProfilePanel();
        }

        return mTopView;
    }

    /** Gets the bottom navigation bar with the appropriate listeners set. */
    @Nullable
    public CarSystemBarView getBottomBar(boolean isSetUp) {
        if (!mShowBottom) {
            return null;
        }

        mBottomView = mCarSystemBarViewFactory.getBottomBar(isSetUp);
        setupBar(mBottomView, mBottomBarTouchListener, mNotificationsShadeController,
                mHvacPanelController, mHvacPanelOverlayViewController);
        return mBottomView;
    }

    /** Gets the left navigation bar with the appropriate listeners set. */
    @Nullable
    public CarSystemBarView getLeftBar(boolean isSetUp) {
        if (!mShowLeft) {
            return null;
        }

        mLeftView = mCarSystemBarViewFactory.getLeftBar(isSetUp);
        setupBar(mLeftView, mLeftBarTouchListener, mNotificationsShadeController,
                mHvacPanelController, mHvacPanelOverlayViewController);
        return mLeftView;
    }

    /** Gets the right navigation bar with the appropriate listeners set. */
    @Nullable
    public CarSystemBarView getRightBar(boolean isSetUp) {
        if (!mShowRight) {
            return null;
        }

        mRightView = mCarSystemBarViewFactory.getRightBar(isSetUp);
        setupBar(mRightView, mRightBarTouchListener, mNotificationsShadeController,
                mHvacPanelController, mHvacPanelOverlayViewController);
        return mRightView;
    }

    private void setupBar(CarSystemBarView view, View.OnTouchListener statusBarTouchListener,
            NotificationsShadeController notifShadeController,
            HvacPanelController hvacPanelController,
            HvacPanelOverlayViewController hvacPanelOverlayViewController) {
        view.setStatusBarWindowTouchListener(statusBarTouchListener);
        view.setNotificationsPanelController(notifShadeController);
        view.setHvacPanelController(hvacPanelController);
        view.registerHvacPanelOverlayViewController(hvacPanelOverlayViewController);
        mButtonSelectionStateController.addAllButtonsWithSelectionState(view);
        mButtonRoleHolderController.addAllButtonsWithRoleName(view);
        mUserNameViewControllerLazy.get().addUserNameView(view);
        mPrivacyChipViewControllerLazy.get().addPrivacyChipView(view);
    }

    private void setupMicQcPanel() {
        if (mMicPanelController == null) {
            mMicPanelController = new StatusIconPanelController(mContext, mCarServiceProvider,
                    mBroadcastDispatcher, mConfigurationController);
        }

        mMicPanelController.setOnQcViewsFoundListener(qcViews -> qcViews.forEach(qcView -> {
            if (qcView.getLocalQCProvider() instanceof MicQcPanel) {
                MicQcPanel micQcPanel = (MicQcPanel) qcView.getLocalQCProvider();
                micQcPanel.setControllers(mPrivacyChipViewControllerLazy.get(),
                        mMicPrivacyElementsProviderLazy.get());
            }
        }));

        View micPrivacyChipFocusView =
                mTopView.requireViewById(R.id.privacy_chip).requireViewById(R.id.focus_view);
        mMicPanelController.attachPanel(micPrivacyChipFocusView, R.layout.qc_mic_panel,
                R.dimen.car_mic_qc_panel_width, Gravity.TOP | Gravity.END);
    }

    private void setupProfilePanel() {
        View profilePickerView = mTopView.findViewById(R.id.user_name);
        if (mProfilePanelController == null && profilePickerView != null) {
            boolean profilePanelDisabledWhileDriving = mContext.getResources().getBoolean(
                    R.bool.config_profile_panel_disabled_while_driving);
            mProfilePanelController = new StatusIconPanelController(mContext, mCarServiceProvider,
                    mBroadcastDispatcher, mConfigurationController,
                    profilePanelDisabledWhileDriving);
            mProfilePanelController.attachPanel(profilePickerView, R.layout.qc_profile_switcher,
                    R.dimen.car_profile_quick_controls_panel_width);
        }
    }

    /** Sets a touch listener for the top navigation bar. */
    public void registerTopBarTouchListener(View.OnTouchListener listener) {
        mTopBarTouchListener = listener;
        if (mTopView != null) {
            mTopView.setStatusBarWindowTouchListener(mTopBarTouchListener);
        }
    }

    /** Sets a touch listener for the bottom navigation bar. */
    public void registerBottomBarTouchListener(View.OnTouchListener listener) {
        mBottomBarTouchListener = listener;
        if (mBottomView != null) {
            mBottomView.setStatusBarWindowTouchListener(mBottomBarTouchListener);
        }
    }

    /** Sets a touch listener for the left navigation bar. */
    public void registerLeftBarTouchListener(View.OnTouchListener listener) {
        mLeftBarTouchListener = listener;
        if (mLeftView != null) {
            mLeftView.setStatusBarWindowTouchListener(mLeftBarTouchListener);
        }
    }

    /** Sets a touch listener for the right navigation bar. */
    public void registerRightBarTouchListener(View.OnTouchListener listener) {
        mRightBarTouchListener = listener;
        if (mRightView != null) {
            mRightView.setStatusBarWindowTouchListener(mRightBarTouchListener);
        }
    }

    /** Sets a notification controller which toggles the notification panel. */
    public void registerNotificationController(
            NotificationsShadeController notificationsShadeController) {
        mNotificationsShadeController = notificationsShadeController;
        if (mTopView != null) {
            mTopView.setNotificationsPanelController(mNotificationsShadeController);
        }
        if (mBottomView != null) {
            mBottomView.setNotificationsPanelController(mNotificationsShadeController);
        }
        if (mLeftView != null) {
            mLeftView.setNotificationsPanelController(mNotificationsShadeController);
        }
        if (mRightView != null) {
            mRightView.setNotificationsPanelController(mNotificationsShadeController);
        }
    }

    /** Sets an HVAC controller which toggles the HVAC panel. */
    public void registerHvacPanelController(HvacPanelController hvacPanelController) {
        mHvacPanelController = hvacPanelController;
        if (mTopView != null) {
            mTopView.setHvacPanelController(mHvacPanelController);
        }
        if (mBottomView != null) {
            mBottomView.setHvacPanelController(mHvacPanelController);
        }
        if (mLeftView != null) {
            mLeftView.setHvacPanelController(mHvacPanelController);
        }
        if (mRightView != null) {
            mRightView.setHvacPanelController(mHvacPanelController);
        }
    }

    /** Sets the HVACPanelOverlayViewController for views to listen to the panel's state. */
    public void registerHvacPanelOverlayViewController(
            HvacPanelOverlayViewController hvacPanelOverlayViewController) {
        mHvacPanelOverlayViewController = hvacPanelOverlayViewController;
        if (mTopView != null) {
            mTopView.registerHvacPanelOverlayViewController(mHvacPanelOverlayViewController);
        }
        if (mBottomView != null) {
            mBottomView.registerHvacPanelOverlayViewController(mHvacPanelOverlayViewController);
        }
        if (mLeftView != null) {
            mLeftView.registerHvacPanelOverlayViewController(mHvacPanelOverlayViewController);
        }
        if (mRightView != null) {
            mRightView.registerHvacPanelOverlayViewController(mHvacPanelOverlayViewController);
        }
    }

    /**
     * Shows all of the navigation buttons on the valid instances of {@link CarSystemBarView}.
     */
    public void showAllNavigationButtons(boolean isSetUp) {
        checkAllBars(isSetUp);
        if (mTopView != null) {
            mTopView.showButtonsOfType(CarSystemBarView.BUTTON_TYPE_NAVIGATION);
        }
        if (mBottomView != null) {
            mBottomView.showButtonsOfType(CarSystemBarView.BUTTON_TYPE_NAVIGATION);
        }
        if (mLeftView != null) {
            mLeftView.showButtonsOfType(CarSystemBarView.BUTTON_TYPE_NAVIGATION);
        }
        if (mRightView != null) {
            mRightView.showButtonsOfType(CarSystemBarView.BUTTON_TYPE_NAVIGATION);
        }
    }

    /**
     * Shows all of the keyguard specific buttons on the valid instances of
     * {@link CarSystemBarView}.
     */
    public void showAllKeyguardButtons(boolean isSetUp) {
        checkAllBars(isSetUp);
        if (mTopView != null) {
            mTopView.showButtonsOfType(CarSystemBarView.BUTTON_TYPE_KEYGUARD);
        }
        if (mBottomView != null) {
            mBottomView.showButtonsOfType(CarSystemBarView.BUTTON_TYPE_KEYGUARD);
        }
        if (mLeftView != null) {
            mLeftView.showButtonsOfType(CarSystemBarView.BUTTON_TYPE_KEYGUARD);
        }
        if (mRightView != null) {
            mRightView.showButtonsOfType(CarSystemBarView.BUTTON_TYPE_KEYGUARD);
        }
    }

    /**
     * Shows all of the occlusion state buttons on the valid instances of
     * {@link CarSystemBarView}.
     */
    public void showAllOcclusionButtons(boolean isSetUp) {
        checkAllBars(isSetUp);
        if (mTopView != null) {
            mTopView.showButtonsOfType(CarSystemBarView.BUTTON_TYPE_OCCLUSION);
        }
        if (mBottomView != null) {
            mBottomView.showButtonsOfType(CarSystemBarView.BUTTON_TYPE_OCCLUSION);
        }
        if (mLeftView != null) {
            mLeftView.showButtonsOfType(CarSystemBarView.BUTTON_TYPE_OCCLUSION);
        }
        if (mRightView != null) {
            mRightView.showButtonsOfType(CarSystemBarView.BUTTON_TYPE_OCCLUSION);
        }
    }

    /** Toggles whether the notifications icon has an unseen indicator or not. */
    public void toggleAllNotificationsUnseenIndicator(boolean isSetUp, boolean hasUnseen) {
        checkAllBars(isSetUp);
        if (mTopView != null) {
            mTopView.toggleNotificationUnseenIndicator(hasUnseen);
        }
        if (mBottomView != null) {
            mBottomView.toggleNotificationUnseenIndicator(hasUnseen);
        }
        if (mLeftView != null) {
            mLeftView.toggleNotificationUnseenIndicator(hasUnseen);
        }
        if (mRightView != null) {
            mRightView.toggleNotificationUnseenIndicator(hasUnseen);
        }
    }

    /** Interface for controlling the notifications shade. */
    public interface NotificationsShadeController {
        /** Toggles the visibility of the notifications shade. */
        void togglePanel();

        /** Returns {@code true} if the panel is open. */
        boolean isNotificationPanelOpen();
    }

    /** Interface for controlling the HVAC panel. */
    public interface HvacPanelController {
        /** Toggles the visibility of the HVAC shade. */
        void togglePanel();

        /** Returns {@code true} if the panel is open. */
        boolean isHvacPanelOpen();
    }

    private void checkAllBars(boolean isSetUp) {
        mTopView = getTopBar(isSetUp);
        mBottomView = getBottomBar(isSetUp);
        mLeftView = getLeftBar(isSetUp);
        mRightView = getRightBar(isSetUp);
    }
}
