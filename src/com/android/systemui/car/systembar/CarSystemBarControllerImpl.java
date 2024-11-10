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

import static com.android.systemui.car.Flags.configAwareSystemui;
import static com.android.systemui.shared.statusbar.phone.BarTransitions.MODE_SEMI_TRANSPARENT;
import static com.android.systemui.shared.statusbar.phone.BarTransitions.MODE_TRANSPARENT;

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
import android.os.Bundle;
import android.os.PatternMatcher;
import android.os.RemoteException;
import android.util.ArraySet;
import android.util.Log;
import android.util.SparseArray;
import android.util.SparseBooleanArray;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsets.Type.InsetsType;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.android.internal.statusbar.IStatusBarService;
import com.android.internal.statusbar.LetterboxDetails;
import com.android.internal.statusbar.RegisterStatusBarResult;
import com.android.internal.view.AppearanceRegion;
import com.android.systemui.R;
import com.android.systemui.car.CarDeviceProvisionedController;
import com.android.systemui.car.CarDeviceProvisionedListener;
import com.android.systemui.car.displaycompat.ToolbarController;
import com.android.systemui.car.hvac.HvacPanelOverlayViewController;
import com.android.systemui.car.keyguard.KeyguardSystemBarPresenter;
import com.android.systemui.car.notification.NotificationPanelViewController;
import com.android.systemui.car.notification.NotificationSystemBarPresenter;
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
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** A single class which controls the system bar views. */
@SysUISingleton
public class CarSystemBarControllerImpl implements CarSystemBarController,
        CommandQueue.Callbacks, ConfigurationController.ConfigurationListener,
        KeyguardSystemBarPresenter, NotificationSystemBarPresenter {
    private static final boolean DEBUG = Build.IS_ENG || Build.IS_USERDEBUG;

    private static final String TAG = CarSystemBarController.class.getSimpleName();

    private static final String OVERLAY_FILTER_DATA_SCHEME = "package";

    private final Context mContext;
    private final CarSystemBarViewFactory mCarSystemBarViewFactory;
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
    private final ConfigurationController mConfigurationController;
    private final CarSystemBarRestartTracker mCarSystemBarRestartTracker;
    private final int mDisplayId;
    @Nullable
    private final ToolbarController mDisplayCompatToolbarController;

    protected final UserTracker mUserTracker;

    private HvacPanelOverlayViewController mHvacPanelOverlayViewController;
    private NotificationPanelViewController mNotificationPanelViewController;

    // Saved StatusBarManager.DisableFlags
    private int mStatusBarState;
    // Saved StatusBarManager.Disable2Flags
    private int mStatusBarState2;
    private int mLockTaskMode;

    // If the nav bar should be hidden when the soft keyboard is visible.
    // contains: Map<@SystemBarSide Integer, Boolean>
    private final SparseBooleanArray mHideBarForKeyboardMap = new SparseBooleanArray();
    // System bar windows.
    // contains: Map<@SystemBarSide Integer, ViewGroup>
    private final SparseArray<ViewGroup> mSystemBarWindowMap = new SparseArray<>();
    // System bar views.
    // contains: Map<@SystemBarSide Integer, CarSystemBarViewController>
    private final SparseArray<CarSystemBarViewController> mSystemBarViewControllerMap =
            new SparseArray<>();
    // If the system bar is attached to the window or not.
    // contains: Map<@SystemBarSide Integer, Boolean>
    private final SparseBooleanArray mSystemBarAttachedMap = new SparseBooleanArray();
    // If the system bar is enabled or not.
    // contains: Map<@SystemBarSide Integer, Boolean>
    private final SparseBooleanArray mSystemBarEnabledMap = new SparseBooleanArray();
    // Set of View.OnTouchListener on each system bar.
    // contains: Map<@SystemBarSide Integer, Set<View.OnTouchListener>>
    private final SparseArray<Set<View.OnTouchListener>> mBarTouchListenersMap = new SparseArray();

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
            SystemBarConfigs systemBarConfigs,
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
            ConfigurationController configurationController,
            CarSystemBarRestartTracker restartTracker,
            DisplayTracker displayTracker,
            @Nullable ToolbarController toolbarController) {
        mContext = context;
        mUserTracker = userTracker;
        mCarSystemBarViewFactory = carSystemBarViewFactory;
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

        // Set initial state.
        mSystemBarConfigs.getSystemBarSidesByZOrder().forEach(side -> {
            mHideBarForKeyboardMap.put(side, mSystemBarConfigs.getHideForKeyboardBySide(side));
        });

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
        Map<Integer, Bundle> savedStates = mSystemBarConfigs.getSystemBarSidesByZOrder().stream()
                .collect(HashMap::new,
                        (map, side) -> {
                            Bundle bundle = new Bundle();
                            getBarViewController(side, isDeviceSetupForUser())
                                    .onSaveInstanceState(bundle);
                            map.put(side, bundle);
                        },
                        HashMap::putAll);

        resetSystemBarContent(/* isProvisionedStateChange= */ false);

        // retrieve the previous state
        mSystemBarConfigs.getSystemBarSidesByZOrder().forEach(side -> {
            getBarViewController(side, isDeviceSetupForUser())
                    .onRestoreInstanceState(savedStates.get(side));
        });
    }

    private void readConfigs() {
        mSystemBarConfigs.getSystemBarSidesByZOrder().forEach(side -> {
            mSystemBarEnabledMap.put(side, mSystemBarConfigs.getEnabledStatusBySide(side));
        });
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
        for (int i = 0; i < mSystemBarViewControllerMap.size(); i++) {
            mSystemBarViewControllerMap.valueAt(i).setDisabledSystemBarButton(viewId, disabled,
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
        return mSystemBarEnabledMap.get(side) ? mCarSystemBarViewFactory
                .getSystemBarWindow(side) : null;
    }

    @VisibleForTesting
    @Nullable
    CarSystemBarViewController getBarViewController(@SystemBarSide int side, boolean isSetUp) {

        if (!mSystemBarEnabledMap.get(side)) {
            return null;
        }

        CarSystemBarViewController viewController = mCarSystemBarViewFactory
                .getSystemBarViewController(side, isSetUp);
        setupBar(viewController, mBarTouchListenersMap.get(side), mHvacPanelOverlayViewController,
                mNotificationPanelViewController);

        mSystemBarViewControllerMap.put(side, viewController);
        return viewController;
    }

    @Override
    public void registerBarTouchListener(@SystemBarSide int side, View.OnTouchListener listener) {
        if (mBarTouchListenersMap.get(side) == null) {
            mBarTouchListenersMap.put(side, new ArraySet<>());
        }
        boolean setModified = mBarTouchListenersMap.get(side).add(listener);
        if (setModified && mSystemBarViewControllerMap.get(side) != null) {
            mSystemBarViewControllerMap.get(side)
                    .setStatusBarWindowTouchListeners(mBarTouchListenersMap.get(side));
        }
    }

    private void setupBar(CarSystemBarViewController controller,
            Set<View.OnTouchListener> statusBarTouchListeners,
            HvacPanelOverlayViewController hvacPanelOverlayViewController,
            NotificationPanelViewController notificationPanelViewController) {
        controller.setStatusBarWindowTouchListeners(
                statusBarTouchListeners != null ? statusBarTouchListeners : new ArraySet<>());
        controller.registerNotificationPanelViewController(notificationPanelViewController);
    }

    /** Sets the NotificationPanelViewController for views to listen to the panel's state. */
    @Override
    public void registerNotificationPanelViewController(
            NotificationPanelViewController notificationPanelViewController) {
        mNotificationPanelViewController = notificationPanelViewController;
        mSystemBarConfigs.getSystemBarSidesByZOrder().forEach(side -> {
            if (mSystemBarViewControllerMap.get(side) != null) {
                mSystemBarViewControllerMap.get(side)
                        .registerNotificationPanelViewController(mNotificationPanelViewController);
            }
        });
    }

    /**
     * Shows all of the navigation buttons on the valid instances of {@link CarSystemBarView}.
     */
    @Override
    public void showAllNavigationButtons() {
        showAllNavigationButtons(isDeviceSetupForUser());
    }

    // TODO(b/368407601): can we remove this?
    @VisibleForTesting
    void showAllNavigationButtons(boolean isSetup) {
        checkAllBars(isSetup);
        mSystemBarConfigs.getSystemBarSidesByZOrder().forEach(side -> {
            if (mSystemBarViewControllerMap.get(side) != null) {
                mSystemBarViewControllerMap.get(side)
                        .showButtonsOfType(CarSystemBarView.BUTTON_TYPE_NAVIGATION);
            }
        });
    }

    /**
     * Shows all of the keyguard specific buttons on the valid instances of
     * {@link CarSystemBarView}.
     */
    @Override
    public void showAllKeyguardButtons() {
        showAllKeyguardButtons(isDeviceSetupForUser());
    }

    // TODO(b/368407601): can we remove this?
    @VisibleForTesting
    void showAllKeyguardButtons(boolean isSetUp) {
        checkAllBars(isSetUp);
        mSystemBarConfigs.getSystemBarSidesByZOrder().forEach(side -> {
            if (mSystemBarViewControllerMap.get(side) != null) {
                mSystemBarViewControllerMap.get(side)
                        .showButtonsOfType(CarSystemBarView.BUTTON_TYPE_KEYGUARD);
            }
        });
    }

    /**
     * Shows all of the occlusion state buttons on the valid instances of
     * {@link CarSystemBarView}.
     */
    @Override
    public void showAllOcclusionButtons() {
        showAllOcclusionButtons(isDeviceSetupForUser());
    }

    // TODO(b/368407601): can we remove this?
    @VisibleForTesting
    void showAllOcclusionButtons(boolean isSetUp) {
        checkAllBars(isSetUp);
        mSystemBarConfigs.getSystemBarSidesByZOrder().forEach(side -> {
            if (mSystemBarViewControllerMap.get(side) != null) {
                mSystemBarViewControllerMap.get(side)
                        .showButtonsOfType(CarSystemBarView.BUTTON_TYPE_OCCLUSION);
            }
        });
    }

    private void checkAllBars(boolean isSetUp) {
        mSystemBarViewControllerMap.clear();
        mSystemBarConfigs.getSystemBarSidesByZOrder().forEach(side -> {
            mSystemBarViewControllerMap.put(side, getBarViewController(side, isSetUp));
        });
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

    protected void updateKeyboardVisibility(boolean isKeyboardVisible) {
        mSystemBarConfigs.getSystemBarSidesByZOrder().forEach(side -> {
            if (mHideBarForKeyboardMap.get(side)) {
                setWindowVisibility(getBarWindow(side),
                        isKeyboardVisible ? View.GONE : View.VISIBLE);
            }
        });
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
        mSystemBarConfigs.getSystemBarSidesByZOrder().forEach(side -> {
            mSystemBarWindowMap.put(side, getBarWindow(side));
        });

        if (mDisplayCompatToolbarController != null) {
            if (mSystemBarConfigs
                    .isLeftDisplayCompatToolbarEnabled()) {
                mDisplayCompatToolbarController.init(mSystemBarWindowMap.get(LEFT));
            } else if (mSystemBarConfigs
                    .isRightDisplayCompatToolbarEnabled()) {
                mDisplayCompatToolbarController.init(mSystemBarWindowMap.get(RIGHT));
            }
        }
    }

    private void buildNavBarContent() {
        mSystemBarConfigs.getSystemBarSidesByZOrder().forEach(side -> {
            CarSystemBarViewController viewController = getBarViewController(side,
                    isDeviceSetupForUser());
            ViewGroup systemBarWindow = mSystemBarWindowMap.get(side);
            if (viewController != null && systemBarWindow != null) {
                systemBarWindow.addView(viewController.getView());
            }
        });
    }

    private void attachNavBarWindows() {
        mSystemBarConfigs.getSystemBarSidesByZOrder().forEach(side -> {
            ViewGroup barWindow = mSystemBarWindowMap.get(side);
            boolean isBarAttached = mSystemBarAttachedMap.get(side);
            boolean isBarEnabled = mSystemBarConfigs.getEnabledStatusBySide(side);
            if (DEBUG) {
                Log.d(TAG, "Side = " + side
                        + ", SystemBarWindow = " + barWindow
                        + ", SystemBarAttached=" + isBarAttached
                        + ", enabled=" + isBarEnabled);
            }
            if (barWindow != null && !isBarAttached && isBarEnabled) {
                mWindowManager.addView(barWindow, mSystemBarConfigs.getLayoutParamsBySide(side));
                mSystemBarAttachedMap.put(side, true);
            }
        });
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
                for (int i = 0; i < mSystemBarAttachedMap.size(); i++) {
                    if (mSystemBarAttachedMap.valueAt(i)) {
                        restartSystemBars();
                        break;
                    }
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
            mCarSystemBarViewFactory.resetSystemBarViewCache();
        }
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

        resetSystemBarConfigs();
        clearSystemBarWindow(/* removeUnusedWindow= */ true);
        buildNavBarWindows();
        buildNavBarContent();
        attachNavBarWindows();

        mCarSystemBarRestartTracker.notifyRestartComplete(/* windowRecreated= */ true,
                /* provisionedStateChanged= */ false);
    }

    private void clearSystemBarWindow(boolean removeUnusedWindow) {
        mSystemBarConfigs.getSystemBarSidesByZOrder().forEach(side -> {
            ViewGroup barWindow = getBarWindow(side);
            if (barWindow != null) {
                barWindow.removeAllViews();
                if (removeUnusedWindow) {
                    mWindowManager.removeViewImmediate(barWindow);
                    mSystemBarAttachedMap.put(side, false);
                }
                mSystemBarViewControllerMap.remove(side);
            }
        });
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
