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

import static android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS;
import static android.view.WindowInsetsController.APPEARANCE_OPAQUE_STATUS_BARS;
import static android.view.WindowInsetsController.BEHAVIOR_DEFAULT;

import static com.android.systemui.car.systembar.SystemBarConfigs.BOTTOM;
import static com.android.systemui.car.systembar.SystemBarConfigs.LEFT;
import static com.android.systemui.car.systembar.SystemBarConfigs.RIGHT;
import static com.android.systemui.car.systembar.SystemBarConfigs.TOP;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.ActivityManager;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.os.RemoteException;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;
import android.testing.TestableResources;
import android.util.ArrayMap;
import android.view.Display;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowManager;

import androidx.test.filters.SmallTest;

import com.android.internal.statusbar.IStatusBarService;
import com.android.internal.statusbar.RegisterStatusBarResult;
import com.android.internal.view.AppearanceRegion;
import com.android.systemui.R;
import com.android.systemui.SysuiTestCase;
import com.android.systemui.car.CarDeviceProvisionedController;
import com.android.systemui.car.CarSystemUiTest;
import com.android.systemui.car.hvac.HvacController;
import com.android.systemui.car.statusicon.StatusIconPanelViewController;
import com.android.systemui.car.systembar.element.CarSystemBarElementInitializer;
import com.android.systemui.plugins.DarkIconDispatcher;
import com.android.systemui.settings.FakeDisplayTracker;
import com.android.systemui.settings.UserTracker;
import com.android.systemui.statusbar.CommandQueue;
import com.android.systemui.statusbar.phone.AutoHideController;
import com.android.systemui.statusbar.phone.LightBarController;
import com.android.systemui.statusbar.phone.LightBarTransitionsController;
import com.android.systemui.statusbar.phone.PhoneStatusBarPolicy;
import com.android.systemui.statusbar.phone.StatusBarSignalPolicy;
import com.android.systemui.statusbar.phone.SysuiDarkIconDispatcher;
import com.android.systemui.statusbar.phone.ui.StatusBarIconController;
import com.android.systemui.statusbar.policy.ConfigurationController;
import com.android.systemui.statusbar.policy.KeyguardStateController;
import com.android.systemui.util.concurrency.FakeExecutor;
import com.android.systemui.util.time.FakeSystemClock;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Optional;

/**
 * TODO(b/362280147): move related tests to CarSystemBarControllerTest.
 */
@CarSystemUiTest
@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
@SmallTest
public class CarSystemBarTest extends SysuiTestCase {

    private TestableResources mTestableResources;
    private Context mSpiedContext;
    private FakeExecutor mExecutor;
    private CarSystemBarController mCarSystemBarController;

    @Mock
    private UserTracker mUserTracker;
    @Mock
    private ActivityManager mActivityManager;
    @Mock
    private ButtonSelectionStateController mButtonSelectionStateController;
    @Mock
    private ButtonRoleHolderController mButtonRoleHolderController;
    @Mock
    private MicPrivacyChipViewController mMicPrivacyChipViewController;
    @Mock
    private CameraPrivacyChipViewController mCameraPrivacyChipViewController;
    @Mock
    private StatusIconPanelViewController.Builder mPanelControllerBuilder;
    @Mock
    private StatusIconPanelViewController mPanelController;
    @Mock
    private CarSystemBarElementInitializer mCarSystemBarElementInitializer;
    @Mock
    private LightBarController mLightBarController;
    @Mock
    private SysuiDarkIconDispatcher mStatusBarIconController;
    @Mock
    private LightBarTransitionsController mLightBarTransitionsController;
    @Mock
    private WindowManager mWindowManager;
    @Mock
    private CarDeviceProvisionedController mDeviceProvisionedController;
    @Mock
    private AutoHideController mAutoHideController;
    @Mock
    private ButtonSelectionStateListener mButtonSelectionStateListener;
    @Mock
    private IStatusBarService mBarService;
    @Mock
    private KeyguardStateController mKeyguardStateController;
    @Mock
    private PhoneStatusBarPolicy mIconPolicy;
    @Mock
    private StatusBarIconController mIconController;
    @Mock
    private StatusBarSignalPolicy mSignalPolicy;
    @Mock
    private HvacController mHvacController;
    @Mock
    private ConfigurationController mConfigurationController;
    @Mock
    private CarSystemBarRestartTracker mCarSystemBarRestartTracker;
    @Mock
    private CarSystemBarViewFactory mCarSystemBarViewFactory;
    @Mock
    private CarSystemBarView mTopBar;
    @Mock
    private ViewGroup mTopWindow;
    @Mock
    private CarSystemBarView mRigthBar;
    @Mock
    private ViewGroup mRightWindow;
    @Mock
    private CarSystemBarView mLeftBar;
    @Mock
    private ViewGroup mLeftWindow;
    @Mock
    private CarSystemBarView mBottomBar;
    @Mock
    private ViewGroup mBottomWindow;

    private RegisterStatusBarResult mBarResult;
    private AppearanceRegion[] mAppearanceRegions;
    private FakeExecutor mUiBgExecutor;
    private SystemBarConfigs mSystemBarConfigs;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mTestableResources = mContext.getOrCreateTestableResources();
        mExecutor = new FakeExecutor(new FakeSystemClock());
        mUiBgExecutor = new FakeExecutor(new FakeSystemClock());
        mSpiedContext = spy(mContext);
        when(mSpiedContext.getSystemService(ActivityManager.class)).thenReturn(mActivityManager);
        when(mStatusBarIconController.getTransitionsController()).thenReturn(
                mLightBarTransitionsController);
        when(mCarSystemBarViewFactory.getTopBar(anyBoolean())).thenReturn(mTopBar);
        when(mCarSystemBarViewFactory.getTopWindow()).thenReturn(mTopWindow);
        when(mCarSystemBarViewFactory.getRightBar(anyBoolean())).thenReturn(mRigthBar);
        when(mCarSystemBarViewFactory.getRightWindow()).thenReturn(mRightWindow);
        when(mCarSystemBarViewFactory.getBottomBar(anyBoolean())).thenReturn(mBottomBar);
        when(mCarSystemBarViewFactory.getBottomWindow()).thenReturn(mBottomWindow);
        when(mCarSystemBarViewFactory.getLeftBar(anyBoolean())).thenReturn(mLeftBar);
        when(mCarSystemBarViewFactory.getLeftWindow()).thenReturn(mLeftWindow);
        mAppearanceRegions = new AppearanceRegion[]{
                new AppearanceRegion(APPEARANCE_LIGHT_STATUS_BARS, new Rect())
        };
        mBarResult = new RegisterStatusBarResult(
                /* icons= */ new ArrayMap<>(),
                /* disabledFlags1= */ 0,
                /* appearance= */ 0,
                mAppearanceRegions,
                /* imeWindowVis= */ 0,
                /* imeBackDisposition= */ 0,
                /* showImeSwitcher= */ false,
                /* disabledFlags2= */ 0,
                /* navbarColorMangedByIme= */ false,
                BEHAVIOR_DEFAULT,
                WindowInsets.Type.defaultVisible(),
                /* packageName= */ null,
                /* transientBarTypes= */ 0,
                /* letterboxDetails= */ null);
        try {
            when(mBarService.registerStatusBar(any())).thenReturn(mBarResult);
        } catch (RemoteException e) {
            e.printStackTrace();
        }

        // Needed to inflate top navigation bar.
        mDependency.injectMockDependency(DarkIconDispatcher.class);
        mDependency.injectMockDependency(StatusBarIconController.class);

        setupPanelControllerBuilderMocks();

        initCarSystemBar();
    }

    private void initCarSystemBar() {
        SystemBarConfigs systemBarConfigs = new SystemBarConfigs(mTestableResources.getResources());
        FakeDisplayTracker displayTracker = new FakeDisplayTracker(mContext);
        mCarSystemBarController = spy(new CarSystemBarController(mSpiedContext, mUserTracker,
                mCarSystemBarViewFactory, mButtonSelectionStateController,
                () -> mMicPrivacyChipViewController, () -> mCameraPrivacyChipViewController,
                mButtonRoleHolderController, systemBarConfigs, () -> mPanelControllerBuilder,
                mLightBarController, mStatusBarIconController, mWindowManager,
                mDeviceProvisionedController, new CommandQueue(mContext, displayTracker),
                mAutoHideController, mButtonSelectionStateListener, mExecutor, mUiBgExecutor,
                mBarService, () -> mKeyguardStateController, () -> mIconPolicy, mHvacController,
                mSignalPolicy, mConfigurationController, mCarSystemBarRestartTracker,
                displayTracker, Optional.empty(), null));
    }

    @Test
    public void restartNavbars_refreshesTaskChanged() {
        mTestableResources.addOverride(R.bool.config_enableTopSystemBar, true);
        mTestableResources.addOverride(R.bool.config_enableBottomSystemBar, true);
        ArgumentCaptor<CarDeviceProvisionedController.DeviceProvisionedListener>
                deviceProvisionedCallbackCaptor = ArgumentCaptor.forClass(
                CarDeviceProvisionedController.DeviceProvisionedListener.class);
        when(mDeviceProvisionedController.isCurrentUserSetup()).thenReturn(true);
        mCarSystemBarController.init();
        // switching the currentUserSetup value to force restart the navbars.
        when(mDeviceProvisionedController.isCurrentUserSetup()).thenReturn(false);
        verify(mDeviceProvisionedController).addCallback(deviceProvisionedCallbackCaptor.capture());

        deviceProvisionedCallbackCaptor.getValue().onUserSwitched();
        waitForDelayableExecutor();

        verify(mButtonSelectionStateListener).onTaskStackChanged();
    }

    @Test
    public void restartNavBars_newUserNotSetupWithKeyguardShowing_showsKeyguardButtons() {
        mTestableResources.addOverride(R.bool.config_enableTopSystemBar, true);
        mTestableResources.addOverride(R.bool.config_enableBottomSystemBar, true);
        ArgumentCaptor<CarDeviceProvisionedController.DeviceProvisionedListener>
                deviceProvisionedCallbackCaptor = ArgumentCaptor.forClass(
                CarDeviceProvisionedController.DeviceProvisionedListener.class);
        when(mDeviceProvisionedController.isCurrentUserSetup()).thenReturn(true);
        mCarSystemBarController.init();
        when(mKeyguardStateController.isShowing()).thenReturn(true);
        // switching the currentUserSetup value to force restart the navbars.
        when(mDeviceProvisionedController.isCurrentUserSetup()).thenReturn(false);
        verify(mDeviceProvisionedController).addCallback(deviceProvisionedCallbackCaptor.capture());

        deviceProvisionedCallbackCaptor.getValue().onUserSwitched();
        waitForDelayableExecutor();

        verify(mCarSystemBarController).showAllKeyguardButtons(false);
    }

    @Test
    public void restartNavbars_newUserIsSetupWithKeyguardHidden_showsNavigationButtons() {
        mTestableResources.addOverride(R.bool.config_enableTopSystemBar, true);
        mTestableResources.addOverride(R.bool.config_enableBottomSystemBar, true);
        ArgumentCaptor<CarDeviceProvisionedController.DeviceProvisionedListener>
                deviceProvisionedCallbackCaptor = ArgumentCaptor.forClass(
                CarDeviceProvisionedController.DeviceProvisionedListener.class);
        when(mDeviceProvisionedController.isCurrentUserSetup()).thenReturn(true);
        mCarSystemBarController.init();
        when(mKeyguardStateController.isShowing()).thenReturn(true);
        // switching the currentUserSetup value to force restart the navbars.
        when(mDeviceProvisionedController.isCurrentUserSetup()).thenReturn(false);
        verify(mDeviceProvisionedController).addCallback(deviceProvisionedCallbackCaptor.capture());
        deviceProvisionedCallbackCaptor.getValue().onUserSwitched();
        waitForDelayableExecutor();
        when(mDeviceProvisionedController.isCurrentUserSetup()).thenReturn(true);
        when(mKeyguardStateController.isShowing()).thenReturn(false);

        deviceProvisionedCallbackCaptor.getValue().onUserSetupChanged();
        waitForDelayableExecutor();

        verify(mCarSystemBarController).showAllNavigationButtons(true);
    }

    @Test
    public void restartNavBars_lightAppearance_darkensAllIcons() {
        mAppearanceRegions[0] = new AppearanceRegion(APPEARANCE_LIGHT_STATUS_BARS, new Rect());

        mCarSystemBarController.init();

        verify(mLightBarTransitionsController).setIconsDark(
                /* dark= */ true, /* animate= */ false);
    }

    @Test
    public void restartNavBars_opaqueAppearance_lightensAllIcons() {
        mAppearanceRegions[0] = new AppearanceRegion(APPEARANCE_OPAQUE_STATUS_BARS, new Rect());

        mCarSystemBarController.init();

        verify(mLightBarTransitionsController).setIconsDark(
                /* dark= */ false, /* animate= */ false);
    }

    @Test
    public void showTransient_wrongDisplayId_transientModeNotUpdated() {
        mTestableResources.addOverride(R.bool.config_enableTopSystemBar, true);
        mTestableResources.addOverride(R.bool.config_enableBottomSystemBar, true);
        when(mDeviceProvisionedController.isCurrentUserSetup()).thenReturn(true);
        mCarSystemBarController.init();

        int randomDisplay = Display.DEFAULT_DISPLAY + 10;
        int insetTypes = 0;
        mCarSystemBarController.showTransient(randomDisplay, insetTypes, false);

        assertThat(mCarSystemBarController.isStatusBarTransientShown()).isFalse();
    }

    @Test
    public void showTransient_correctDisplayId_noStatusBarInset_transientModeNotUpdated() {
        mTestableResources.addOverride(R.bool.config_enableTopSystemBar, true);
        mTestableResources.addOverride(R.bool.config_enableBottomSystemBar, true);
        when(mDeviceProvisionedController.isCurrentUserSetup()).thenReturn(true);
        mCarSystemBarController.init();

        int insetTypes = 0;
        mCarSystemBarController.showTransient(Display.DEFAULT_DISPLAY, insetTypes, false);

        assertThat(mCarSystemBarController.isStatusBarTransientShown()).isFalse();
    }

    @Test
    public void showTransient_correctDisplayId_statusBarInset_transientModeUpdated() {
        mTestableResources.addOverride(R.bool.config_enableTopSystemBar, true);
        mTestableResources.addOverride(R.bool.config_enableBottomSystemBar, true);
        when(mDeviceProvisionedController.isCurrentUserSetup()).thenReturn(true);
        mCarSystemBarController.init();

        int insetTypes = WindowInsets.Type.statusBars();
        mCarSystemBarController.showTransient(Display.DEFAULT_DISPLAY, insetTypes, false);

        assertThat(mCarSystemBarController.isStatusBarTransientShown()).isTrue();
    }

    @Test
    public void showTransient_correctDisplayId_noNavBarInset_transientModeNotUpdated() {
        mTestableResources.addOverride(R.bool.config_enableTopSystemBar, true);
        mTestableResources.addOverride(R.bool.config_enableBottomSystemBar, true);
        when(mDeviceProvisionedController.isCurrentUserSetup()).thenReturn(true);
        mCarSystemBarController.init();

        int insetTypes = 0;
        mCarSystemBarController.showTransient(Display.DEFAULT_DISPLAY, insetTypes, false);

        assertThat(mCarSystemBarController.isNavBarTransientShown()).isFalse();
    }

    @Test
    public void showTransient_correctDisplayId_navBarInset_transientModeUpdated() {
        mTestableResources.addOverride(R.bool.config_enableTopSystemBar, true);
        mTestableResources.addOverride(R.bool.config_enableBottomSystemBar, true);
        when(mDeviceProvisionedController.isCurrentUserSetup()).thenReturn(true);
        mCarSystemBarController.init();

        int insetTypes = WindowInsets.Type.navigationBars();
        mCarSystemBarController.showTransient(Display.DEFAULT_DISPLAY, insetTypes, false);

        assertThat(mCarSystemBarController.isNavBarTransientShown()).isTrue();
    }

    @Test
    public void abortTransient_wrongDisplayId_transientModeNotCleared() {
        mTestableResources.addOverride(R.bool.config_enableTopSystemBar, true);
        mTestableResources.addOverride(R.bool.config_enableBottomSystemBar, true);
        when(mDeviceProvisionedController.isCurrentUserSetup()).thenReturn(true);
        mCarSystemBarController.init();
        mCarSystemBarController.showTransient(
                Display.DEFAULT_DISPLAY,
                WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars(),
                false);
        assertThat(mCarSystemBarController.isStatusBarTransientShown()).isTrue();
        assertThat(mCarSystemBarController.isNavBarTransientShown()).isTrue();

        int insetTypes = 0;
        int randomDisplay = Display.DEFAULT_DISPLAY + 10;
        mCarSystemBarController.abortTransient(randomDisplay, insetTypes);

        // The transient booleans were not cleared.
        assertThat(mCarSystemBarController.isStatusBarTransientShown()).isTrue();
        assertThat(mCarSystemBarController.isNavBarTransientShown()).isTrue();
    }

    @Test
    public void abortTransient_correctDisplayId_noInsets_transientModeNotCleared() {
        mTestableResources.addOverride(R.bool.config_enableTopSystemBar, true);
        mTestableResources.addOverride(R.bool.config_enableBottomSystemBar, true);
        when(mDeviceProvisionedController.isCurrentUserSetup()).thenReturn(true);
        mCarSystemBarController.init();
        mCarSystemBarController.showTransient(
                Display.DEFAULT_DISPLAY,
                WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars(),
                false);
        assertThat(mCarSystemBarController.isStatusBarTransientShown()).isTrue();
        assertThat(mCarSystemBarController.isNavBarTransientShown()).isTrue();

        int insetTypes = 0;
        mCarSystemBarController.abortTransient(Display.DEFAULT_DISPLAY, insetTypes);

        // The transient booleans were not cleared.
        assertThat(mCarSystemBarController.isStatusBarTransientShown()).isTrue();
        assertThat(mCarSystemBarController.isNavBarTransientShown()).isTrue();
    }

    @Test
    public void abortTransient_correctDisplayId_statusBarInset_transientModeCleared() {
        mTestableResources.addOverride(R.bool.config_enableTopSystemBar, true);
        mTestableResources.addOverride(R.bool.config_enableBottomSystemBar, true);
        when(mDeviceProvisionedController.isCurrentUserSetup()).thenReturn(true);
        mCarSystemBarController.init();
        mCarSystemBarController.showTransient(
                Display.DEFAULT_DISPLAY,
                WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars(),
                false);
        assertThat(mCarSystemBarController.isStatusBarTransientShown()).isTrue();
        assertThat(mCarSystemBarController.isNavBarTransientShown()).isTrue();

        int insetTypes = WindowInsets.Type.statusBars();
        mCarSystemBarController.abortTransient(Display.DEFAULT_DISPLAY, insetTypes);

        // The transient booleans were cleared.
        assertThat(mCarSystemBarController.isStatusBarTransientShown()).isFalse();
        assertThat(mCarSystemBarController.isNavBarTransientShown()).isFalse();
    }

    @Test
    public void abortTransient_correctDisplayId_navBarInset_transientModeCleared() {
        mTestableResources.addOverride(R.bool.config_enableTopSystemBar, true);
        mTestableResources.addOverride(R.bool.config_enableBottomSystemBar, true);
        when(mDeviceProvisionedController.isCurrentUserSetup()).thenReturn(true);
        mCarSystemBarController.init();
        mCarSystemBarController.showTransient(
                Display.DEFAULT_DISPLAY,
                WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars(),
                false);
        assertThat(mCarSystemBarController.isStatusBarTransientShown()).isTrue();
        assertThat(mCarSystemBarController.isNavBarTransientShown()).isTrue();

        int insetTypes = WindowInsets.Type.navigationBars();
        mCarSystemBarController.abortTransient(Display.DEFAULT_DISPLAY, insetTypes);

        // The transient booleans were cleared.
        assertThat(mCarSystemBarController.isStatusBarTransientShown()).isFalse();
        assertThat(mCarSystemBarController.isNavBarTransientShown()).isFalse();
    }

    @Test
    public void disable_wrongDisplayId_notSetStatusBarState() {
        int randomDisplay = Display.DEFAULT_DISPLAY + 10;

        mCarSystemBarController.disable(randomDisplay, 0, 0, false);

        verify(mCarSystemBarController, never()).setSystemBarStates(anyInt(), anyInt());
    }

    @Test
    public void disable_correctDisplayId_setSystemBarStates() {
        mCarSystemBarController.disable(Display.DEFAULT_DISPLAY, 0, 0, false);

        verify(mCarSystemBarController).setSystemBarStates(0, 0);
    }

    @Test
    public void onConfigChanged_toggleNightMode() {
        // get the current mode and then change to the opposite
        boolean isNightMode = mContext.getResources().getConfiguration().isNightModeActive();
        Configuration config = new Configuration();
        config.uiMode =
                isNightMode ? Configuration.UI_MODE_NIGHT_NO : Configuration.UI_MODE_NIGHT_YES;

        mCarSystemBarController.init();
        mCarSystemBarController.onConfigChanged(config);

        assertThat(mCarSystemBarController.getIsUiModeNight()).isNotEqualTo(isNightMode);
    }

    @Test
    public void restartSystemBars_newSystemBarConfig_recreatesSystemBars() {
        mTestableResources.addOverride(R.integer.config_showDisplayCompatToolbarOnSystemBar, 0);
        mTestableResources.addOverride(R.bool.config_enableTopSystemBar, true);
        mTestableResources.addOverride(R.bool.config_enableBottomSystemBar, true);
        mTestableResources.addOverride(R.bool.config_enableLeftSystemBar, false);
        mTestableResources.addOverride(R.bool.config_enableRightSystemBar, false);
        when(mCarSystemBarController.getTopWindow()).thenReturn(mock(ViewGroup.class));
        when(mCarSystemBarController.getBottomWindow()).thenReturn(mock(ViewGroup.class));
        when(mCarSystemBarController.getLeftWindow()).thenReturn(null);
        when(mCarSystemBarController.getRightWindow()).thenReturn(null);

        initCarSystemBar();
        mCarSystemBarController.init();
        assertThat(mCarSystemBarController.getSystemBarWindowBySide(TOP)).isNotNull();
        assertThat(mCarSystemBarController.getSystemBarWindowBySide(BOTTOM)).isNotNull();
        assertThat(mCarSystemBarController.getSystemBarWindowBySide(LEFT)).isNull();
        assertThat(mCarSystemBarController.getSystemBarWindowBySide(RIGHT)).isNull();

        mTestableResources.addOverride(R.bool.config_enableTopSystemBar, true);
        mTestableResources.addOverride(R.bool.config_enableBottomSystemBar, false);
        mTestableResources.addOverride(R.bool.config_enableLeftSystemBar, true);
        mTestableResources.addOverride(R.bool.config_enableRightSystemBar, true);
        mSystemBarConfigs = new SystemBarConfigs(mTestableResources.getResources());
        when(mCarSystemBarController.getTopWindow()).thenReturn(mock(ViewGroup.class));
        when(mCarSystemBarController.getBottomWindow()).thenReturn(null);
        when(mCarSystemBarController.getLeftWindow()).thenReturn(mock(ViewGroup.class));
        when(mCarSystemBarController.getRightWindow()).thenReturn(mock(ViewGroup.class));
        mCarSystemBarController.restartSystemBars();

        verify(mCarSystemBarController, times(1)).removeAll();
        verify(mCarSystemBarController, times(1)).resetSystemBarConfigs();
        assertThat(mCarSystemBarController.getSystemBarWindowBySide(TOP)).isNotNull();
        assertThat(mCarSystemBarController.getSystemBarWindowBySide(BOTTOM)).isNull();
        assertThat(mCarSystemBarController.getSystemBarWindowBySide(LEFT)).isNotNull();
        assertThat(mCarSystemBarController.getSystemBarWindowBySide(RIGHT)).isNotNull();
    }

    private void waitForDelayableExecutor() {
        mExecutor.advanceClockToLast();
        mExecutor.runAllReady();
    }

    private void setupPanelControllerBuilderMocks() {
        when(mPanelControllerBuilder.setXOffset(anyInt())).thenReturn(mPanelControllerBuilder);
        when(mPanelControllerBuilder.setYOffset(anyInt())).thenReturn(mPanelControllerBuilder);
        when(mPanelControllerBuilder.setGravity(anyInt())).thenReturn(mPanelControllerBuilder);
        when(mPanelControllerBuilder.setDisabledWhileDriving(anyBoolean())).thenReturn(
                mPanelControllerBuilder);
        when(mPanelControllerBuilder.setShowAsDropDown(anyBoolean())).thenReturn(
                mPanelControllerBuilder);
        when(mPanelControllerBuilder.build(any(), anyInt(), anyInt())).thenReturn(mPanelController);
    }
}
