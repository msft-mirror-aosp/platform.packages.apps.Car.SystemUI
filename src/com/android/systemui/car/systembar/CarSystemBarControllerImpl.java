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

import static android.content.Intent.ACTION_OVERLAY_CHANGED;
import static android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS;

import static com.android.systemui.car.systembar.CarSystemBarController.BOTTOM;
import static com.android.systemui.car.systembar.CarSystemBarController.LEFT;
import static com.android.systemui.car.systembar.CarSystemBarController.RIGHT;
import static com.android.systemui.car.systembar.CarSystemBarController.TOP;
import static com.android.systemui.car.Flags.configAwareSystemui;
import static com.android.systemui.shared.statusbar.phone.BarTransitions.MODE_SEMI_TRANSPARENT;
import static com.android.systemui.shared.statusbar.phone.BarTransitions.MODE_TRANSPARENT;

import android.annotation.LayoutRes;
import android.app.ActivityManager;
import android.app.ActivityManager.RunningTaskInfo;
import android.app.StatusBarManager;
import android.app.StatusBarManager.Disable2Flags;
import android.app.StatusBarManager.DisableFlags;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.inputmethodservice.InputMethodService;
import android.os.Build;
import android.os.PatternMatcher;
import android.os.RemoteException;
import android.util.ArraySet;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsets.Type.InsetsType;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.annotation.IdRes;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.android.car.ui.FocusParkingView;
import com.android.car.ui.utils.ViewUtils;
import com.android.internal.statusbar.IStatusBarService;
import com.android.internal.statusbar.LetterboxDetails;
import com.android.internal.statusbar.RegisterStatusBarResult;
import com.android.internal.view.AppearanceRegion;
import com.android.systemui.R;
import com.android.systemui.car.CarDeviceProvisionedController;
import com.android.systemui.car.CarDeviceProvisionedListener;
import com.android.systemui.car.displaycompat.ToolbarController;
import com.android.systemui.car.hvac.HvacController;
import com.android.systemui.car.hvac.HvacPanelController;
import com.android.systemui.car.hvac.HvacPanelOverlayViewController;
import com.android.systemui.car.hvac.HvacSystemBarPresenter;
import com.android.systemui.car.keyguard.KeyguardSystemBarPresenter;
import com.android.systemui.car.notification.NotificationPanelViewController;
import com.android.systemui.car.notification.NotificationSystemBarPresenter;
import com.android.systemui.car.notification.NotificationsShadeController;
import com.android.systemui.car.statusicon.StatusIconPanelViewController;
import com.android.systemui.car.users.CarSystemUIUserUtil;
import com.android.systemui.dagger.SysUISingleton;
import com.android.systemui.dagger.qualifiers.Main;
import com.android.systemui.plugins.DarkIconDispatcher;
import com.android.systemui.settings.DisplayTracker;
import com.android.systemui.settings.UserTracker;
import com.android.systemui.shared.statusbar.phone.BarTransitions;
import com.android.systemui.shared.system.TaskStackChangeListener;
import com.android.systemui.shared.system.TaskStackChangeListeners;
import com.android.systemui.statusbar.AutoHideUiElement;
import com.android.systemui.statusbar.CommandQueue;
import com.android.systemui.statusbar.phone.AutoHideController;
import com.android.systemui.statusbar.phone.LightBarController;
import com.android.systemui.statusbar.phone.PhoneStatusBarPolicy;
import com.android.systemui.statusbar.phone.SysuiDarkIconDispatcher;
import com.android.systemui.statusbar.policy.ConfigurationController;
import com.android.systemui.statusbar.policy.KeyguardStateController;
import com.android.systemui.util.concurrency.DelayableExecutor;

import dagger.Lazy;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import javax.inject.Provider;

/** A single class which controls the system bar views. */
@SysUISingleton
public class CarSystemBarControllerImpl implements CarSystemBarController,
        CommandQueue.Callbacks, ConfigurationController.ConfigurationListener,
        KeyguardSystemBarPresenter, NotificationSystemBarPresenter, HvacSystemBarPresenter {
    private static final boolean DEBUG = Build.IS_ENG || Build.IS_USERDEBUG;

    private static final String TAG = CarSystemBarController.class.getSimpleName();

    private static final String OVERLAY_FILTER_DATA_SCHEME = "package";

    private final Context mContext;
    private final CarSystemBarViewFactory mCarSystemBarViewFactory;
    private final ButtonSelectionStateController mButtonSelectionStateController;
    private final ButtonRoleHolderController mButtonRoleHolderController;
    private final Provider<StatusIconPanelViewController.Builder> mPanelControllerBuilderProvider;
    private final Lazy<MicPrivacyChipViewController> mMicPrivacyChipViewControllerLazy;
    private final Lazy<CameraPrivacyChipViewController> mCameraPrivacyChipViewControllerLazy;
    private final SystemBarConfigs mSystemBarConfigs;
    private final SysuiDarkIconDispatcher mStatusBarIconController;
    private final WindowManager mWindowManager;
    private final CarDeviceProvisionedController mCarDeviceProvisionedController;
    private final CommandQueue mCommandQueue;
    private final AutoHideController mAutoHideController;
    private final ButtonSelectionStateListener mButtonSelectionStateListener;
    private final DelayableExecutor mExecutor;
    private final IStatusBarService mBarService;
    private final DisplayTracker mDisplayTracker;
    private final Lazy<KeyguardStateController> mKeyguardStateControllerLazy;
    private final Lazy<PhoneStatusBarPolicy> mIconPolicyLazy;
    private final HvacController mHvacController;
    private final ConfigurationController mConfigurationController;
    private final CarSystemBarRestartTracker mCarSystemBarRestartTracker;
    private final int mDisplayId;
    @Nullable
    private final ToolbarController mDisplayCompatToolbarController;
    private final Set<View.OnTouchListener> mTopBarTouchListeners = new ArraySet<>();
    private final Set<View.OnTouchListener> mBottomBarTouchListeners = new ArraySet<>();
    private final Set<View.OnTouchListener> mLeftBarTouchListeners = new ArraySet<>();
    private final Set<View.OnTouchListener> mRightBarTouchListeners = new ArraySet<>();

    protected final UserTracker mUserTracker;

    private NotificationsShadeController mNotificationsShadeController;
    private HvacPanelController mHvacPanelController;
    private StatusIconPanelViewController mMicPanelController;
    private StatusIconPanelViewController mCameraPanelController;
    private StatusIconPanelViewController mProfilePanelController;
    private HvacPanelOverlayViewController mHvacPanelOverlayViewController;
    private NotificationPanelViewController mNotificationPanelViewController;

    private int mPrivacyChipXOffset;
    // Saved StatusBarManager.DisableFlags
    private int mStatusBarState;
    // Saved StatusBarManager.Disable2Flags
    private int mStatusBarState2;
    private int mLockTaskMode;

    // If the nav bar should be hidden when the soft keyboard is visible.
    private boolean mHideTopBarForKeyboard;
    private boolean mHideLeftBarForKeyboard;
    private boolean mHideRightBarForKeyboard;
    private boolean mHideBottomBarForKeyboard;

    // Nav bar views.
    private ViewGroup mTopSystemBarWindow;
    private ViewGroup mBottomSystemBarWindow;
    private ViewGroup mLeftSystemBarWindow;
    private ViewGroup mRightSystemBarWindow;
    private CarSystemBarView mTopView;
    private CarSystemBarView mBottomView;
    private CarSystemBarView mLeftView;
    private CarSystemBarView mRightView;
    private boolean mTopSystemBarAttached;
    private boolean mBottomSystemBarAttached;
    private boolean mLeftSystemBarAttached;
    private boolean mRightSystemBarAttached;
    @IdRes
    private int mTopFocusedViewId;
    @IdRes
    private int mBottomFocusedViewId;
    @IdRes
    private int mLeftFocusedViewId;
    @IdRes
    private int mRightFocusedViewId;
    private boolean mShowTop;
    private boolean mShowBottom;
    private boolean mShowLeft;
    private boolean mShowRight;

    // To be attached to the navigation bars such that they can close the notification panel if
    // it's open.
    private boolean mDeviceIsSetUpForUser = true;
    private boolean mIsUserSetupInProgress = false;

    private AppearanceRegion[] mAppearanceRegions = new AppearanceRegion[0];
    @BarTransitions.TransitionMode
    private int mStatusBarMode;
    @BarTransitions.TransitionMode
    private int mSystemBarMode;
    private boolean mStatusBarTransientShown;
    private boolean mNavBarTransientShown;

    private boolean mIsUiModeNight = false;

    private Locale mCurrentLocale;

    public CarSystemBarControllerImpl(Context context,
            UserTracker userTracker,
            CarSystemBarViewFactory carSystemBarViewFactory,
            ButtonSelectionStateController buttonSelectionStateController,
            Lazy<MicPrivacyChipViewController> micPrivacyChipViewControllerLazy,
            Lazy<CameraPrivacyChipViewController> cameraPrivacyChipViewControllerLazy,
            ButtonRoleHolderController buttonRoleHolderController,
            SystemBarConfigs systemBarConfigs,
            Provider<StatusIconPanelViewController.Builder> panelControllerBuilderProvider,
            // TODO(b/156052638): Should not need to inject LightBarController
            LightBarController lightBarController,
            DarkIconDispatcher darkIconDispatcher,
            WindowManager windowManager,
            CarDeviceProvisionedController deviceProvisionedController,
            CommandQueue commandQueue,
            AutoHideController autoHideController,
            ButtonSelectionStateListener buttonSelectionStateListener,
            @Main DelayableExecutor mainExecutor,
            IStatusBarService barService,
            Lazy<KeyguardStateController> keyguardStateControllerLazy,
            Lazy<PhoneStatusBarPolicy> iconPolicyLazy,
            HvacController hvacController,
            ConfigurationController configurationController,
            CarSystemBarRestartTracker restartTracker,
            DisplayTracker displayTracker,
            @Nullable ToolbarController toolbarController) {
        mContext = context;
        mUserTracker = userTracker;
        mCarSystemBarViewFactory = carSystemBarViewFactory;
        mButtonSelectionStateController = buttonSelectionStateController;
        mMicPrivacyChipViewControllerLazy = micPrivacyChipViewControllerLazy;
        mCameraPrivacyChipViewControllerLazy = cameraPrivacyChipViewControllerLazy;
        mButtonRoleHolderController = buttonRoleHolderController;
        mPanelControllerBuilderProvider = panelControllerBuilderProvider;
        mSystemBarConfigs = systemBarConfigs;
        mStatusBarIconController = (SysuiDarkIconDispatcher) darkIconDispatcher;
        mWindowManager = windowManager;
        mCarDeviceProvisionedController = deviceProvisionedController;
        mCommandQueue = commandQueue;
        mAutoHideController = autoHideController;
        mButtonSelectionStateListener = buttonSelectionStateListener;
        mExecutor = mainExecutor;
        mBarService = barService;
        mKeyguardStateControllerLazy = keyguardStateControllerLazy;
        mIconPolicyLazy = iconPolicyLazy;
        mHvacController = hvacController;
        mDisplayId = context.getDisplayId();
        mDisplayTracker = displayTracker;
        mIsUiModeNight = mContext.getResources().getConfiguration().isNightModeActive();
        mCurrentLocale = mContext.getResources().getConfiguration().getLocales().get(0);
        mConfigurationController = configurationController;
        mCarSystemBarRestartTracker = restartTracker;
        mDisplayCompatToolbarController = toolbarController;
    }

    /**
     * Initializes the SystemBars
     */
    public void init() {

        resetSystemBarConfigs();

        mPrivacyChipXOffset = -mContext.getResources()
                .getDimensionPixelOffset(R.dimen.privacy_chip_horizontal_padding);

        // Set initial state.
        mHideTopBarForKeyboard = mSystemBarConfigs.getHideForKeyboardBySide(TOP);
        mHideBottomBarForKeyboard = mSystemBarConfigs.getHideForKeyboardBySide(BOTTOM);
        mHideLeftBarForKeyboard = mSystemBarConfigs.getHideForKeyboardBySide(LEFT);
        mHideRightBarForKeyboard = mSystemBarConfigs.getHideForKeyboardBySide(RIGHT);

        // Connect into the status bar manager service
        mCommandQueue.addCallback(this);

        mAutoHideController.setStatusBar(new AutoHideUiElement() {
            @Override
            public void synchronizeState() {
                // No op.
            }

            @Override
            public boolean isVisible() {
                return mStatusBarTransientShown;
            }

            @Override
            public void hide() {
                clearTransient();
            }
        });

        mAutoHideController.setNavigationBar(new AutoHideUiElement() {
            @Override
            public void synchronizeState() {
                // No op.
            }

            @Override
            public boolean isVisible() {
                return mNavBarTransientShown;
            }

            @Override
            public void hide() {
                clearTransient();
            }
        });

        mDeviceIsSetUpForUser = mCarDeviceProvisionedController.isCurrentUserSetup();
        mIsUserSetupInProgress = mCarDeviceProvisionedController.isCurrentUserSetupInProgress();
        mCarDeviceProvisionedController.addCallback(
                new CarDeviceProvisionedListener() {
                    @Override
                    public void onUserSetupInProgressChanged() {
                        mExecutor.execute(() -> resetSystemBarContentIfNecessary());
                    }

                    @Override
                    public void onUserSetupChanged() {
                        mExecutor.execute(() -> resetSystemBarContentIfNecessary());
                    }

                    @Override
                    public void onUserSwitched() {
                        mExecutor.execute(() -> resetSystemBarContentIfNecessary());
                    }
                });

        mConfigurationController.addCallback(/* listener= */ this);
        registerOverlayChangeBroadcastReceiver();

        createSystemBar();

        TaskStackChangeListeners.getInstance().registerTaskStackListener(
                mButtonSelectionStateListener);
        TaskStackChangeListeners.getInstance().registerTaskStackListener(
                new TaskStackChangeListener() {
                    @Override
                    public void onLockTaskModeChanged(int mode) {
                        refreshSystemBar();
                    }

                    @Override
                    public void onTaskMovedToFront(RunningTaskInfo taskInfo) {
                        if (mDisplayCompatToolbarController != null) {
                            mDisplayCompatToolbarController.update(taskInfo);
                        }
                    }
                });

        // Lastly, call to the icon policy to install/update all the icons.
        // Must be called on the main thread due to the use of observeForever() in
        // mIconPolicy.init().
        mExecutor.execute(() -> {
            mIconPolicyLazy.get().init();
        });
    }

    /**
     * We register for soft keyboard visibility events such that we can hide the navigation bar
     * giving more screen space to the IME. Note: this is optional and controlled by
     * {@code com.android.internal.R.bool.config_hideNavBarForKeyboard}.
     */
    @Override
    public void setImeWindowStatus(int displayId, int visibility, int backDisposition,
            boolean showImeSwitcher) {
        if (mContext.getDisplayId() != displayId) {
            return;
        }

        boolean isKeyboardVisible = (visibility & InputMethodService.IME_VISIBLE) != 0;

        updateKeyboardVisibility(isKeyboardVisible);
    }

    @Override
    public void onSystemBarAttributesChanged(
            int displayId,
            @WindowInsetsController.Appearance int appearance,
            AppearanceRegion[] appearanceRegions,
            boolean navbarColorManagedByIme,
            @WindowInsetsController.Behavior int behavior,
            @InsetsType int requestedVisibleTypes,
            String packageName,
            LetterboxDetails[] letterboxDetails) {
        if (displayId != mDisplayId) {
            return;
        }
        boolean barModeChanged = updateStatusBarMode(
                mStatusBarTransientShown ? MODE_SEMI_TRANSPARENT : MODE_TRANSPARENT);
        int numStacks = appearanceRegions.length;
        boolean stackAppearancesChanged = mAppearanceRegions.length != numStacks;
        for (int i = 0; i < numStacks && !stackAppearancesChanged; i++) {
            stackAppearancesChanged |= !appearanceRegions[i].equals(mAppearanceRegions[i]);
        }
        if (stackAppearancesChanged || barModeChanged) {
            mAppearanceRegions = appearanceRegions;
            updateStatusBarAppearance();
        }
        refreshSystemBar();
    }

    @Override
    public void disable(int displayId, @DisableFlags int state1, @Disable2Flags int state2,
            boolean animate) {
        if (displayId != mDisplayId) {
            return;
        }
        setSystemBarStates(state1, state2);
    }

    @Override
    public void showTransient(int displayId, int types, boolean isGestureOnSystemBar) {
        if (displayId != mDisplayId) {
            return;
        }
        if ((types & WindowInsets.Type.statusBars()) != 0) {
            if (!mStatusBarTransientShown) {
                mStatusBarTransientShown = true;
                handleTransientChanged();
            }
        }
        if ((types & WindowInsets.Type.navigationBars()) != 0) {
            if (!mNavBarTransientShown) {
                mNavBarTransientShown = true;
                handleTransientChanged();
            }
        }
    }

    @Override
    public void abortTransient(int displayId, int types) {
        if (displayId != mDisplayId) {
            return;
        }
        if ((types & (WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars())) == 0) {
            return;
        }
        clearTransient();
    }

    @Override
    public void onConfigChanged(Configuration newConfig) {
        Locale oldLocale = mCurrentLocale;
        mCurrentLocale = newConfig.getLocales().get(0);

        boolean isConfigNightMode = newConfig.isNightModeActive();
        if (isConfigNightMode == mIsUiModeNight
                && ((mCurrentLocale != null && mCurrentLocale.equals(oldLocale))
                || mCurrentLocale == oldLocale)) {
            return;
        }

        // Refresh UI on Night mode or system language changes.
        if (isConfigNightMode != mIsUiModeNight) {
            mIsUiModeNight = isConfigNightMode;
        }

        // cache the current state
        // The focused view will be destroyed during re-layout, causing the framework to adjust
        // the focus unexpectedly. To avoid that, move focus to a view that won't be
        // destroyed during re-layout and has no focus highlight (the FocusParkingView), then
        // move focus back to the previously focused view after re-layout.
        cacheAndHideFocus();
        View profilePickerView = null;
        boolean isProfilePickerOpen = false;
        if (mTopView != null) {
            profilePickerView = mTopView.findViewById(R.id.user_name);
        }
        if (profilePickerView != null) isProfilePickerOpen = profilePickerView.isSelected();
        if (isProfilePickerOpen) {
            profilePickerView.callOnClick();
        }

        resetSystemBarContent(/* isProvisionedStateChange= */ false);

        // retrieve the previous state
        if (isProfilePickerOpen) {
            if (mTopView != null) {
                profilePickerView = mTopView.findViewById(R.id.user_name);
            }
            if (profilePickerView != null) profilePickerView.callOnClick();
        }

        restoreFocus();
    }

    private void readConfigs() {
        mShowTop = mSystemBarConfigs.getEnabledStatusBySide(TOP);
        mShowBottom = mSystemBarConfigs.getEnabledStatusBySide(BOTTOM);
        mShowLeft = mSystemBarConfigs.getEnabledStatusBySide(LEFT);
        mShowRight = mSystemBarConfigs.getEnabledStatusBySide(RIGHT);
    }

    /** Clean up */
    @VisibleForTesting
    void removeAll() {
        mButtonSelectionStateController.removeAll();
        mButtonRoleHolderController.removeAll();
        mMicPrivacyChipViewControllerLazy.get().removeAll();
        mCameraPrivacyChipViewControllerLazy.get().removeAll();

        mMicPanelController = null;
        mCameraPanelController = null;
        mProfilePanelController = null;
    }

    /** Gets the top window if configured to do so. */
    @Nullable
    private ViewGroup getTopWindow() {
        return mShowTop ? mCarSystemBarViewFactory.getTopWindow() : null;
    }

    /** Gets the bottom window if configured to do so. */
    @Nullable
    private ViewGroup getBottomWindow() {
        return mShowBottom ? mCarSystemBarViewFactory.getBottomWindow() : null;
    }

    /** Gets the left window if configured to do so. */
    @Nullable
    private ViewGroup getLeftWindow() {
        return mShowLeft ? mCarSystemBarViewFactory.getLeftWindow() : null;
    }

    /** Gets the right window if configured to do so. */
    @Nullable
    private ViewGroup getRightWindow() {
        return mShowRight ? mCarSystemBarViewFactory.getRightWindow() : null;
    }

    /** Toggles the right nav bar visibility. */
    @VisibleForTesting
    boolean setWindowVisibility(ViewGroup window, @View.Visibility int visibility) {
        if (window == null) {
            return false;
        }

        if (window.getVisibility() == visibility) {
            return false;
        }

        window.setVisibility(visibility);
        return true;
    }

    /**
     * Sets the system bar states - {@code StatusBarManager.DisableFlags},
     * {@code StatusBarManager.Disable2Flags}, lock task mode. When there is a change in state,
     * and refreshes the system bars.
     *
     * @param state {@code StatusBarManager.DisableFlags}
     * @param state2 {@code StatusBarManager.Disable2Flags}
     */
    @VisibleForTesting
    void setSystemBarStates(int state, int state2) {
        int diff = (state ^ mStatusBarState) | (state2 ^ mStatusBarState2);
        int lockTaskMode = getLockTaskModeState();
        if (diff == 0 && mLockTaskMode == lockTaskMode) {
            if (DEBUG) {
                Log.d(TAG, "setSystemBarStates(): status bar states unchanged: state: "
                        + state + " state2: " +  state2 + " lockTaskMode: " + mLockTaskMode);
            }
            return;
        }
        mStatusBarState = state;
        mStatusBarState2 = state2;
        mLockTaskMode = lockTaskMode;
        refreshSystemBar();
    }

    @VisibleForTesting
    int getStatusBarState() {
        return mStatusBarState;
    }

    @VisibleForTesting
    int getStatusBarState2() {
        return mStatusBarState2;
    }

    /**
     * Refreshes system bar views and sets the visibility of certain components based on
     * {@link StatusBarManager} flags and lock task mode.
     * <ul>
     * <li>Home button will be disabled when {@code StatusBarManager.DISABLE_HOME} is set.
     * <li>Phone call button will be disable in lock task mode.
     * <li>App grid button will be disable when {@code StatusBarManager.DISABLE_HOME} is set.
     * <li>Notification button will be disable when
     * {@code StatusBarManager.DISABLE_NOTIFICATION_ICONS} is set.
     * <li>Quick settings and user switcher will be hidden when in lock task mode or when
     * {@code StatusBarManager.DISABLE2_QUICK_SETTINGS} is set.
     * </ul>
     */
    private void refreshSystemBar() {
        boolean homeDisabled = ((mStatusBarState & StatusBarManager.DISABLE_HOME) > 0);
        boolean notificationDisabled =
                ((mStatusBarState & StatusBarManager.DISABLE_NOTIFICATION_ICONS) > 0);
        boolean locked = (mLockTaskMode == ActivityManager.LOCK_TASK_MODE_LOCKED);
        boolean qcDisabled =
                ((mStatusBarState2 & StatusBarManager.DISABLE2_QUICK_SETTINGS) > 0) || locked;
        boolean systemIconsDisabled =
                ((mStatusBarState2 & StatusBarManager.DISABLE2_SYSTEM_ICONS) > 0) || locked;

        setDisabledSystemBarButton(R.id.home, homeDisabled, "home");
        setDisabledSystemBarButton(R.id.passenger_home, homeDisabled, "passenger_home");
        setDisabledSystemBarButton(R.id.phone_nav, locked, "phone_nav");
        setDisabledSystemBarButton(R.id.grid_nav, homeDisabled, "grid_nav");
        setDisabledSystemBarButton(R.id.notifications, notificationDisabled, "notifications");

        if (DEBUG) {
            Log.d(TAG, "refreshSystemBar: locked?: " + locked
                    + " homeDisabled: " + homeDisabled
                    + " notificationDisabled: " + notificationDisabled
                    + " qcDisabled: " + qcDisabled
                    + " systemIconsDisabled: " + systemIconsDisabled);
        }
    }

    private int getLockTaskModeState() {
        return mContext.getSystemService(ActivityManager.class).getLockTaskModeState();
    }

    private void setDisabledSystemBarButton(int viewId, boolean disabled,
                @Nullable String buttonName) {
        for (CarSystemBarView barView : getAllAvailableSystemBarViews()) {
            barView.setDisabledSystemBarButton(viewId, disabled,
                    () -> showAdminSupportDetailsDialog(), buttonName);
        }
    }

    private void showAdminSupportDetailsDialog() {
        // TODO(b/205891123): launch AdminSupportDetailsDialog after moving
        // AdminSupportDetailsDialog out of CarSettings since CarSettings is not and should not
        // be allowlisted for lock task mode.
        Toast.makeText(mContext, "This action is unavailable for your profile",
                Toast.LENGTH_LONG).show();
    }

    @VisibleForTesting
    @Nullable
    ViewGroup getBarWindow(@SystemBarSide int side) {
        switch (side) {
            case BOTTOM:
                return getBottomWindow();
            case LEFT:
                return getLeftWindow();
            case RIGHT:
                return getRightWindow();
            case TOP:
                return getTopWindow();
            default:
                return null;
        }
    }

    @VisibleForTesting
    @Nullable
    CarSystemBarView getBarView(@SystemBarSide int side, boolean isSetUp) {
        switch (side) {
            case BOTTOM:
                return getBottomBar(isSetUp);
            case LEFT:
                return getLeftBar(isSetUp);
            case RIGHT:
                return getRightBar(isSetUp);
            case TOP:
                return getTopBar(isSetUp);
            default:
                return null;
        }
    }

    @Override
    public void registerBarTouchListener(@SystemBarSide int side, View.OnTouchListener listener) {
        switch (side) {
            case BOTTOM:
                registerBottomBarTouchListener(listener);
                break;
            case LEFT:
                registerLeftBarTouchListener(listener);
                break;
            case RIGHT:
                registerRightBarTouchListener(listener);
                break;
            case TOP:
                registerTopBarTouchListener(listener);
                break;
            default:
                break;
        }
    }

    /** Gets the top navigation bar with the appropriate listeners set. */
    @Nullable
    private CarSystemBarView getTopBar(boolean isSetUp) {
        if (!mShowTop) {
            return null;
        }

        mTopView = mCarSystemBarViewFactory.getTopBar(isSetUp);
        setupBar(mTopView, mTopBarTouchListeners, mNotificationsShadeController,
                mHvacPanelController, mHvacPanelOverlayViewController,
                mNotificationPanelViewController);

        if (isSetUp) {
            // We do not want the privacy chips or the profile picker to be clickable in
            // unprovisioned mode.
            mMicPanelController = setupSensorQcPanel(mMicPanelController, R.id.mic_privacy_chip,
                    R.layout.qc_mic_panel);
            mCameraPanelController = setupSensorQcPanel(mCameraPanelController,
                    R.id.camera_privacy_chip, R.layout.qc_camera_panel);
            setupProfilePanel();
        }

        return mTopView;
    }

    /** Gets the bottom navigation bar with the appropriate listeners set. */
    @Nullable
    private CarSystemBarView getBottomBar(boolean isSetUp) {
        if (!mShowBottom) {
            return null;
        }

        mBottomView = mCarSystemBarViewFactory.getBottomBar(isSetUp);
        setupBar(mBottomView, mBottomBarTouchListeners, mNotificationsShadeController,
                mHvacPanelController, mHvacPanelOverlayViewController,
                mNotificationPanelViewController);

        return mBottomView;
    }

    /** Gets the left navigation bar with the appropriate listeners set. */
    @Nullable
    private CarSystemBarView getLeftBar(boolean isSetUp) {
        if (!mShowLeft) {
            return null;
        }

        mLeftView = mCarSystemBarViewFactory.getLeftBar(isSetUp);
        setupBar(mLeftView, mLeftBarTouchListeners, mNotificationsShadeController,
                mHvacPanelController, mHvacPanelOverlayViewController,
                mNotificationPanelViewController);
        return mLeftView;
    }

    /** Gets the right navigation bar with the appropriate listeners set. */
    @Nullable
    private CarSystemBarView getRightBar(boolean isSetUp) {
        if (!mShowRight) {
            return null;
        }

        mRightView = mCarSystemBarViewFactory.getRightBar(isSetUp);
        setupBar(mRightView, mRightBarTouchListeners, mNotificationsShadeController,
                mHvacPanelController, mHvacPanelOverlayViewController,
                mNotificationPanelViewController);
        return mRightView;
    }

    private void setupBar(CarSystemBarView view, Set<View.OnTouchListener> statusBarTouchListeners,
            NotificationsShadeController notifShadeController,
            HvacPanelController hvacPanelController,
            HvacPanelOverlayViewController hvacPanelOverlayViewController,
            NotificationPanelViewController notificationPanelViewController) {
        view.updateHomeButtonVisibility(CarSystemUIUserUtil.isSecondaryMUMDSystemUI());
        view.setStatusBarWindowTouchListeners(statusBarTouchListeners);
        view.setNotificationsPanelController(notifShadeController);
        view.registerNotificationPanelViewController(notificationPanelViewController);
        view.setHvacPanelController(hvacPanelController);
        view.registerHvacPanelOverlayViewController(hvacPanelOverlayViewController);
        view.updateControlCenterButtonVisibility(CarSystemUIUserUtil.isMUMDSystemUI());
        mButtonSelectionStateController.addAllButtonsWithSelectionState(view);
        mButtonRoleHolderController.addAllButtonsWithRoleName(view);
        mMicPrivacyChipViewControllerLazy.get().addPrivacyChipView(view);
        mCameraPrivacyChipViewControllerLazy.get().addPrivacyChipView(view);
    }

    private StatusIconPanelViewController setupSensorQcPanel(
            @Nullable StatusIconPanelViewController panelController, int chipId,
            @LayoutRes int panelLayoutRes) {
        if (panelController == null) {
            View privacyChip = mTopView.findViewById(chipId);
            if (privacyChip != null) {
                panelController = mPanelControllerBuilderProvider.get()
                        .setXOffset(mPrivacyChipXOffset)
                        .setGravity(Gravity.TOP | Gravity.END)
                        .build(privacyChip, panelLayoutRes, R.dimen.car_sensor_qc_panel_width);
                panelController.init();
            }
        }
        return panelController;
    }

    private void setupProfilePanel() {
        View profilePickerView = mTopView.findViewById(R.id.user_name);
        if (mProfilePanelController == null && profilePickerView != null) {
            boolean profilePanelDisabledWhileDriving = mContext.getResources().getBoolean(
                    R.bool.config_profile_panel_disabled_while_driving);
            mProfilePanelController = mPanelControllerBuilderProvider.get()
                    .setGravity(Gravity.TOP | Gravity.END)
                    .setDisabledWhileDriving(profilePanelDisabledWhileDriving)
                    .build(profilePickerView, R.layout.qc_profile_switcher,
                            R.dimen.car_profile_quick_controls_panel_width);
            mProfilePanelController.init();
        }
    }

    /** Sets a touch listener for the top navigation bar. */
    private void registerTopBarTouchListener(View.OnTouchListener listener) {
        boolean setModified = mTopBarTouchListeners.add(listener);
        if (setModified && mTopView != null) {
            mTopView.setStatusBarWindowTouchListeners(mTopBarTouchListeners);
        }
    }

    /** Sets a touch listener for the bottom navigation bar. */
    private void registerBottomBarTouchListener(View.OnTouchListener listener) {
        boolean setModified = mBottomBarTouchListeners.add(listener);
        if (setModified && mBottomView != null) {
            mBottomView.setStatusBarWindowTouchListeners(mBottomBarTouchListeners);
        }
    }

    /** Sets a touch listener for the left navigation bar. */
    private void registerLeftBarTouchListener(View.OnTouchListener listener) {
        boolean setModified = mLeftBarTouchListeners.add(listener);
        if (setModified && mLeftView != null) {
            mLeftView.setStatusBarWindowTouchListeners(mLeftBarTouchListeners);
        }
    }

    /** Sets a touch listener for the right navigation bar. */
    private void registerRightBarTouchListener(View.OnTouchListener listener) {
        boolean setModified = mRightBarTouchListeners.add(listener);
        if (setModified && mRightView != null) {
            mRightView.setStatusBarWindowTouchListeners(mRightBarTouchListeners);
        }
    }

    /** Sets a notification controller which toggles the notification panel. */
    @Override
    public void registerNotificationShadeController(
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

    /** Sets the NotificationPanelViewController for views to listen to the panel's state. */
    @Override
    public void registerNotificationPanelViewController(
            NotificationPanelViewController notificationPanelViewController) {
        mNotificationPanelViewController = notificationPanelViewController;
        if (mTopView != null) {
            mTopView.registerNotificationPanelViewController(mNotificationPanelViewController);
        }
        if (mBottomView != null) {
            mBottomView.registerNotificationPanelViewController(mNotificationPanelViewController);
        }
        if (mLeftView != null) {
            mLeftView.registerNotificationPanelViewController(mNotificationPanelViewController);
        }
        if (mRightView != null) {
            mRightView.registerNotificationPanelViewController(mNotificationPanelViewController);
        }
    }

    /** Sets an HVAC controller which toggles the HVAC panel. */
    @Override
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
    @Override
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
    @Override
    public void showAllNavigationButtons() {
        showAllNavigationButtons(true);
    }

    // TODO(b/368407601): can we remove this?
    @VisibleForTesting
    void showAllNavigationButtons(boolean isSetup) {
        checkAllBars(isSetup);
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
    @Override
    public void showAllKeyguardButtons() {
        showAllKeyguardButtons(true);
    }

    // TODO(b/368407601): can we remove this?
    @VisibleForTesting
    void showAllKeyguardButtons(boolean isSetUp) {
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
    @Override
    public void showAllOcclusionButtons() {
        showAllOcclusionButtons(true);
    }

    // TODO(b/368407601): can we remove this?
    @VisibleForTesting
    void showAllOcclusionButtons(boolean isSetUp) {
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
    @Override
    public void toggleAllNotificationsUnseenIndicator(boolean hasUnseen) {
        toggleAllNotificationsUnseenIndicator(
                mCarDeviceProvisionedController.isCurrentUserFullySetup(), hasUnseen);
    }

    // TODO(b/368407601): can we remove this?
    @VisibleForTesting
    void toggleAllNotificationsUnseenIndicator(boolean isSetUp, boolean hasUnseen) {
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

    private void checkAllBars(boolean isSetUp) {
        mTopView = getTopBar(isSetUp);
        mBottomView = getBottomBar(isSetUp);
        mLeftView = getLeftBar(isSetUp);
        mRightView = getRightBar(isSetUp);
    }

    private List<CarSystemBarView> getAllAvailableSystemBarViews() {
        List<CarSystemBarView> barViews = new ArrayList<>();
        if (mTopView != null) {
            barViews.add(mTopView);
        }
        if (mBottomView != null) {
            barViews.add(mBottomView);
        }
        if (mLeftView != null) {
            barViews.add(mLeftView);
        }
        if (mRightView != null) {
            barViews.add(mRightView);
        }
        return barViews;
    }

    /** Resets the cached Views. */
    protected void resetViewCache() {
        mCarSystemBarViewFactory.resetSystemBarViewCache();
    }

    /**
     * Invalidate SystemBarConfigs and fetch again from Resources.
     * TODO(): b/260206944, Can remove this after we have a fix for overlaid resources not applied.
     */
    @VisibleForTesting
    void resetSystemBarConfigs() {
        mSystemBarConfigs.resetSystemBarConfigs();
        mCarSystemBarViewFactory.resetSystemBarWindowCache();
        readConfigs();
    }

    /** Stores the ID of the View that is currently focused and hides the focus. */
    @VisibleForTesting
    void cacheAndHideFocus() {
        mTopFocusedViewId = cacheAndHideFocus(mTopView);
        if (mTopFocusedViewId != View.NO_ID) return;
        mBottomFocusedViewId = cacheAndHideFocus(mBottomView);
        if (mBottomFocusedViewId != View.NO_ID) return;
        mLeftFocusedViewId = cacheAndHideFocus(mLeftView);
        if (mLeftFocusedViewId != View.NO_ID) return;
        mRightFocusedViewId = cacheAndHideFocus(mRightView);
    }

    @VisibleForTesting
    int cacheAndHideFocus(@Nullable View rootView) {
        if (rootView == null) return View.NO_ID;
        View focusedView = rootView.findFocus();
        if (focusedView == null || focusedView instanceof FocusParkingView) return View.NO_ID;
        int focusedViewId = focusedView.getId();
        ViewUtils.hideFocus(rootView);
        return focusedViewId;
    }

    /** Requests focus on the View that matches the cached ID. */
    private void restoreFocus() {
        if (restoreFocus(mTopView, mTopFocusedViewId)) return;
        if (restoreFocus(mBottomView, mBottomFocusedViewId)) return;
        if (restoreFocus(mLeftView, mLeftFocusedViewId)) return;
        restoreFocus(mRightView, mRightFocusedViewId);
    }

    private boolean restoreFocus(@Nullable View rootView, @IdRes int viewToFocusId) {
        if (rootView == null || viewToFocusId == View.NO_ID) return false;
        View focusedView = rootView.findViewById(viewToFocusId);
        if (focusedView == null) return false;
        focusedView.requestFocus();
        return true;
    }

    protected void updateKeyboardVisibility(boolean isKeyboardVisible) {
        if (mHideTopBarForKeyboard) {
            setWindowVisibility(getTopWindow(), isKeyboardVisible ? View.GONE : View.VISIBLE);
        }

        if (mHideBottomBarForKeyboard) {
            setWindowVisibility(getBottomWindow(), isKeyboardVisible ? View.GONE : View.VISIBLE);
        }

        if (mHideLeftBarForKeyboard) {
            setWindowVisibility(getLeftWindow(), isKeyboardVisible ? View.GONE : View.VISIBLE);
        }
        if (mHideRightBarForKeyboard) {
            setWindowVisibility(getRightWindow(), isKeyboardVisible ? View.GONE : View.VISIBLE);
        }
    }

    protected void createSystemBar() {
        RegisterStatusBarResult result = null;
        try {
            // Register only for Primary User.
            result = mBarService.registerStatusBar(mCommandQueue);

            onSystemBarAttributesChanged(mDisplayId, result.mAppearance, result.mAppearanceRegions,
                    result.mNavbarColorManagedByIme, result.mBehavior,
                    result.mRequestedVisibleTypes,
                    result.mPackageName, result.mLetterboxDetails);

            setImeWindowStatus(mDisplayId, result.mImeWindowVis, result.mImeBackDisposition,
                    result.mShowImeSwitcher);

            // Set up the initial icon state
            int numIcons = result.mIcons.size();
            for (int i = 0; i < numIcons; i++) {
                mCommandQueue.setIcon(result.mIcons.keyAt(i), result.mIcons.valueAt(i));
            }
        } catch (RemoteException ex) {
            ex.rethrowFromSystemServer();
        }

        // Try setting up the initial state of the nav bar if applicable.
        if (result != null) {
            setImeWindowStatus(mDisplayTracker.getDefaultDisplayId(), result.mImeWindowVis,
                    result.mImeBackDisposition, result.mShowImeSwitcher);
        }

        createNavBar();
    }

    protected void createNavBar() {
        buildNavBarWindows();
        buildNavBarContent();
        attachNavBarWindows();
    }

    private void buildNavBarWindows() {
        mTopSystemBarWindow = getTopWindow();
        mBottomSystemBarWindow = getBottomWindow();
        mLeftSystemBarWindow = getLeftWindow();
        mRightSystemBarWindow = getRightWindow();

        if (mDisplayCompatToolbarController != null) {
            if (mSystemBarConfigs
                    .isLeftDisplayCompatToolbarEnabled()) {
                mDisplayCompatToolbarController.init(mLeftSystemBarWindow);
            } else if (mSystemBarConfigs
                    .isRightDisplayCompatToolbarEnabled()) {
                mDisplayCompatToolbarController.init(mRightSystemBarWindow);
            }
        }
    }

    private void buildNavBarContent() {
        mTopView = getTopBar(isDeviceSetupForUser());
        if (mTopView != null) {
            mSystemBarConfigs.insetSystemBar(TOP, mTopView);
            mHvacController.registerHvacViews(mTopView);
            mTopSystemBarWindow.addView(mTopView);
        }

        mBottomView = getBottomBar(isDeviceSetupForUser());
        if (mBottomView != null) {
            mSystemBarConfigs.insetSystemBar(BOTTOM, mBottomView);
            mHvacController.registerHvacViews(mBottomView);
            mBottomSystemBarWindow.addView(mBottomView);
        }

        mLeftView = getLeftBar(isDeviceSetupForUser());
        if (mLeftView != null) {
            mSystemBarConfigs.insetSystemBar(LEFT, mLeftView);
            mHvacController.registerHvacViews(mLeftView);
            mLeftSystemBarWindow.addView(mLeftView);
        }

        mRightView = getRightBar(isDeviceSetupForUser());
        if (mRightView != null) {
            mSystemBarConfigs.insetSystemBar(RIGHT, mRightView);
            mHvacController.registerHvacViews(mRightView);
            mRightSystemBarWindow.addView(mRightView);
        }
    }

    private void attachNavBarWindows() {
        mSystemBarConfigs.getSystemBarSidesByZOrder().forEach(this::attachNavBarBySide);
    }

    private void attachNavBarBySide(int side) {
        switch (side) {
            case TOP:
                if (DEBUG) {
                    Log.d(TAG, "mTopSystemBarWindow = " + mTopSystemBarWindow
                            + ", mTopSystemBarAttached=" + mTopSystemBarAttached
                            + ", enabled=" + mSystemBarConfigs.getEnabledStatusBySide(TOP));
                }
                if (mTopSystemBarWindow != null && !mTopSystemBarAttached
                        && mSystemBarConfigs.getEnabledStatusBySide(TOP)) {
                    mWindowManager.addView(mTopSystemBarWindow,
                            mSystemBarConfigs.getLayoutParamsBySide(TOP));
                    mTopSystemBarAttached = true;
                }
                break;
            case BOTTOM:
                if (DEBUG) {
                    Log.d(TAG, "mBottomSystemBarWindow = " + mBottomSystemBarWindow
                            + ", mBottomSystemBarAttached=" + mBottomSystemBarAttached
                            + ", enabled=" + mSystemBarConfigs.getEnabledStatusBySide(BOTTOM));
                }
                if (mBottomSystemBarWindow != null && !mBottomSystemBarAttached
                        && mSystemBarConfigs.getEnabledStatusBySide(BOTTOM)) {
                    mWindowManager.addView(mBottomSystemBarWindow,
                            mSystemBarConfigs.getLayoutParamsBySide(BOTTOM));
                    mBottomSystemBarAttached = true;
                }
                break;
            case LEFT:
                if (DEBUG) {
                    Log.d(TAG, "mLeftSystemBarWindow = " + mLeftSystemBarWindow
                            + ", mLeftSystemBarAttached=" + mLeftSystemBarAttached
                            + ", enabled=" + mSystemBarConfigs.getEnabledStatusBySide(LEFT));
                }
                if (mLeftSystemBarWindow != null && !mLeftSystemBarAttached
                        && mSystemBarConfigs.getEnabledStatusBySide(LEFT)) {
                    mWindowManager.addView(mLeftSystemBarWindow,
                            mSystemBarConfigs.getLayoutParamsBySide(LEFT));
                    mLeftSystemBarAttached = true;
                }
                break;
            case RIGHT:
                if (DEBUG) {
                    Log.d(TAG, "mRightSystemBarWindow = " + mRightSystemBarWindow
                            + ", mRightSystemBarAttached=" + mRightSystemBarAttached
                            + ", "
                            + "enabled=" + mSystemBarConfigs.getEnabledStatusBySide(RIGHT));
                }
                if (mRightSystemBarWindow != null && !mRightSystemBarAttached
                        && mSystemBarConfigs.getEnabledStatusBySide(RIGHT)) {
                    mWindowManager.addView(mRightSystemBarWindow,
                            mSystemBarConfigs.getLayoutParamsBySide(RIGHT));
                    mRightSystemBarAttached = true;
                }
                break;
            default:
                return;
        }
    }

    private void registerOverlayChangeBroadcastReceiver() {
        if (!configAwareSystemui()) {
            if (DEBUG) {
                Log.d(TAG, "Ignore overlay change for car systemui");
            }
            return;
        }
        IntentFilter overlayFilter = new IntentFilter(ACTION_OVERLAY_CHANGED);
        overlayFilter.addDataScheme(OVERLAY_FILTER_DATA_SCHEME);
        overlayFilter.addDataSchemeSpecificPart(mContext.getPackageName(),
                PatternMatcher.PATTERN_LITERAL);
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (mTopSystemBarAttached || mBottomSystemBarAttached || mLeftSystemBarAttached
                        || mRightSystemBarAttached) {
                    restartSystemBars();
                }
            }
        };
        mContext.registerReceiver(receiver, overlayFilter, /* broadcastPermission= */
                null, /* handler= */ null);
    }

    private void resetSystemBarContentIfNecessary() {
        boolean currentUserSetup = mCarDeviceProvisionedController.isCurrentUserSetup();
        boolean currentUserSetupInProgress = mCarDeviceProvisionedController
                .isCurrentUserSetupInProgress();
        if (mIsUserSetupInProgress != currentUserSetupInProgress
                || mDeviceIsSetUpForUser != currentUserSetup) {
            mDeviceIsSetUpForUser = currentUserSetup;
            mIsUserSetupInProgress = currentUserSetupInProgress;
            resetSystemBarContent(/* isProvisionedStateChange= */ true);
        }
    }

    /**
     * Remove all content from navbars and rebuild them. Used to allow for different nav bars
     * before and after the device is provisioned. . Also for change of density and font size.
     */
    private void resetSystemBarContent(boolean isProvisionedStateChange) {
        mCarSystemBarRestartTracker.notifyPendingRestart(/* recreateWindows= */ false,
                isProvisionedStateChange);

        if (!isProvisionedStateChange) {
            resetViewCache();
        }
        // remove and reattach all components such that we don't keep a reference to unused ui
        // elements
        removeAll();
        clearSystemBarWindow(/* removeUnusedWindow= */ false);

        buildNavBarContent();
        // If the UI was rebuilt (day/night change or user change) while the keyguard was up we need
        // to correctly respect that state.
        if (mKeyguardStateControllerLazy.get().isShowing()) {
            showAllKeyguardButtons(isDeviceSetupForUser());
        } else {
            showAllNavigationButtons(isDeviceSetupForUser());
        }

        // Upon restarting the Navigation Bar, CarFacetButtonController should immediately apply the
        // selection state that reflects the current task stack.
        mButtonSelectionStateListener.onTaskStackChanged();

        mCarSystemBarRestartTracker.notifyRestartComplete(/* windowRecreated= */ false,
                isProvisionedStateChange);
    }

    private boolean isDeviceSetupForUser() {
        return mDeviceIsSetUpForUser && !mIsUserSetupInProgress;
    }

    private void updateStatusBarAppearance() {
        int numStacks = mAppearanceRegions.length;
        final ArrayList<Rect> lightBarBounds = new ArrayList<>();

        for (int i = 0; i < numStacks; i++) {
            final AppearanceRegion ar = mAppearanceRegions[i];
            if (isLight(ar.getAppearance())) {
                lightBarBounds.add(ar.getBounds());
            }
        }

        // If all stacks are light, all icons become dark.
        if (lightBarBounds.size() == numStacks) {
            mStatusBarIconController.setIconsDarkArea(null);
            mStatusBarIconController.getTransitionsController().setIconsDark(
                    /* dark= */ true, /* animate= */ false);
        } else if (lightBarBounds.isEmpty()) {
            // If no one is light, all icons become white.
            mStatusBarIconController.getTransitionsController().setIconsDark(
                    /* dark= */ false, /* animate= */ false);
        } else {
            // Not the same for every stack, update icons in area only.
            mStatusBarIconController.setIconsDarkArea(lightBarBounds);
            mStatusBarIconController.getTransitionsController().setIconsDark(
                    /* dark= */ true, /* animate= */ false);
        }
    }

    private static boolean isLight(int appearance) {
        return (appearance & APPEARANCE_LIGHT_STATUS_BARS) != 0;
    }

    private void handleTransientChanged() {
        updateStatusBarMode(mStatusBarTransientShown ? MODE_SEMI_TRANSPARENT : MODE_TRANSPARENT);
        updateNavBarMode(mNavBarTransientShown ? MODE_SEMI_TRANSPARENT : MODE_TRANSPARENT);
    }

    // Returns true if the status bar mode has changed.
    private boolean updateStatusBarMode(int barMode) {
        if (mStatusBarMode != barMode) {
            mStatusBarMode = barMode;
            mAutoHideController.touchAutoHide();
            return true;
        }
        return false;
    }

    // Returns true if the nav bar mode has changed.
    private boolean updateNavBarMode(int barMode) {
        if (mSystemBarMode != barMode) {
            mSystemBarMode = barMode;
            mAutoHideController.touchAutoHide();
            return true;
        }
        return false;
    }

    @VisibleForTesting
    void restartSystemBars() {
        mCarSystemBarRestartTracker.notifyPendingRestart(/* recreateWindows= */ true,
                /* provisionedStateChanged= */ false);

        removeAll();
        resetSystemBarConfigs();
        clearSystemBarWindow(/* removeUnusedWindow= */ true);
        buildNavBarWindows();
        buildNavBarContent();
        attachNavBarWindows();

        mCarSystemBarRestartTracker.notifyRestartComplete(/* windowRecreated= */ true,
                /* provisionedStateChanged= */ false);
    }

    private void clearSystemBarWindow(boolean removeUnusedWindow) {
        if (mTopSystemBarWindow != null) {
            mTopSystemBarWindow.removeAllViews();
            mHvacController.unregisterViews(mTopView);
            if (removeUnusedWindow) {
                mWindowManager.removeViewImmediate(mTopSystemBarWindow);
                mTopSystemBarAttached = false;
            }
            mTopView = null;
        }

        if (mBottomSystemBarWindow != null) {
            mBottomSystemBarWindow.removeAllViews();
            mHvacController.unregisterViews(mBottomView);
            if (removeUnusedWindow) {
                mWindowManager.removeViewImmediate(mBottomSystemBarWindow);
                mBottomSystemBarAttached = false;
            }
            mBottomView = null;
        }

        if (mLeftSystemBarWindow != null) {
            mLeftSystemBarWindow.removeAllViews();
            mHvacController.unregisterViews(mLeftView);
            if (removeUnusedWindow) {
                mWindowManager.removeViewImmediate(mLeftSystemBarWindow);
                mLeftSystemBarAttached = false;
            }
            mLeftView = null;
        }

        if (mRightSystemBarWindow != null) {
            mRightSystemBarWindow.removeAllViews();
            mHvacController.unregisterViews(mRightView);
            if (removeUnusedWindow) {
                mWindowManager.removeViewImmediate(mRightSystemBarWindow);
                mRightSystemBarAttached = false;
            }
            mRightView = null;
        }
    }

    @VisibleForTesting
    boolean getIsUiModeNight() {
        return mIsUiModeNight;
    }

    private void clearTransient() {
        if (mStatusBarTransientShown) {
            mStatusBarTransientShown = false;
            handleTransientChanged();
        }
        if (mNavBarTransientShown) {
            mNavBarTransientShown = false;
            handleTransientChanged();
        }
    }

    @VisibleForTesting
    boolean isStatusBarTransientShown() {
        return mStatusBarTransientShown;
    }

    @VisibleForTesting
    boolean isNavBarTransientShown() {
        return mNavBarTransientShown;
    }
}
