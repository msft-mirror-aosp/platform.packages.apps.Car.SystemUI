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

import static android.app.StatusBarManager.DISABLE2_QUICK_SETTINGS;
import static android.app.StatusBarManager.DISABLE_HOME;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.systemui.car.systembar.CarSystemBarController.BOTTOM;
import static com.android.systemui.car.systembar.CarSystemBarController.LEFT;
import static com.android.systemui.car.systembar.CarSystemBarController.RIGHT;
import static com.android.systemui.car.systembar.CarSystemBarController.TOP;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.ActivityManager;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;
import android.testing.TestableResources;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;

import androidx.test.filters.SmallTest;

import com.android.car.dockutil.Flags;
import com.android.car.ui.FocusParkingView;
import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.internal.statusbar.IStatusBarService;
import com.android.internal.statusbar.LetterboxDetails;
import com.android.internal.statusbar.RegisterStatusBarResult;
import com.android.internal.view.AppearanceRegion;
import com.android.systemui.R;
import com.android.systemui.SysuiTestCase;
import com.android.systemui.SysuiTestableContext;
import com.android.systemui.car.CarDeviceProvisionedController;
import com.android.systemui.car.CarSystemUiTest;
import com.android.systemui.car.statusicon.StatusIconPanelViewController;
import com.android.systemui.car.systembar.CarSystemBarController.SystemBarSide;
import com.android.systemui.car.systembar.element.CarSystemBarElementController;
import com.android.systemui.car.systembar.element.CarSystemBarElementInitializer;
import com.android.systemui.car.systembar.element.CarSystemBarElementStateController;
import com.android.systemui.car.systembar.element.CarSystemBarElementStatusBarDisableController;
import com.android.systemui.car.users.CarSystemUIUserUtil;
import com.android.systemui.car.window.OverlayVisibilityMediator;
import com.android.systemui.plugins.DarkIconDispatcher;
import com.android.systemui.settings.FakeDisplayTracker;
import com.android.systemui.settings.UserTracker;
import com.android.systemui.statusbar.CommandQueue;
import com.android.systemui.statusbar.phone.AutoHideController;
import com.android.systemui.statusbar.phone.LightBarController;
import com.android.systemui.statusbar.phone.PhoneStatusBarPolicy;
import com.android.systemui.statusbar.phone.StatusBarSignalPolicy;
import com.android.systemui.statusbar.phone.SysuiDarkIconDispatcher;
import com.android.systemui.statusbar.phone.ui.StatusBarIconController;
import com.android.systemui.statusbar.policy.ConfigurationController;
import com.android.systemui.statusbar.policy.KeyguardStateController;
import com.android.systemui.util.concurrency.FakeExecutor;
import com.android.systemui.util.time.FakeSystemClock;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.inject.Provider;

@CarSystemUiTest
@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
@SmallTest
public class CarSystemBarControllerTest extends SysuiTestCase {
    private static final String TOP_NOTIFICATION_PANEL =
            "com.android.systemui.car.notification.TopNotificationPanelViewMediator";
    private static final String BOTTOM_NOTIFICATION_PANEL =
            "com.android.systemui.car.notification.BottomNotificationPanelViewMediator";
    private CarSystemBarControllerImpl mCarSystemBarController;
    private CarSystemBarViewFactory mCarSystemBarViewFactory;
    private TestableResources mTestableResources;
    private SysuiTestableContext mSpiedContext;
    private MockitoSession mSession;

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
    private StatusIconPanelViewController mPanelController;
    @Mock
    private LightBarController mLightBarController;
    @Mock
    private SysuiDarkIconDispatcher mStatusBarIconController;
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
    private ConfigurationController mConfigurationController;
    @Mock
    private CarSystemBarRestartTracker mCarSystemBarRestartTracker;
    @Mock
    private OverlayVisibilityMediator mOverlayVisibilityMediator;
    private RegisterStatusBarResult mRegisterStatusBarResult;
    private SystemBarConfigs mSystemBarConfigs;

    @Before
    public void setUp() throws Exception {
        mSession = ExtendedMockito.mockitoSession()
            .initMocks(this)
            .spyStatic(CarSystemUIUserUtil.class)
            .strictness(Strictness.LENIENT)
            .startMocking();
        mTestableResources = mContext.getOrCreateTestableResources();
        mSpiedContext = spy(mContext);
        mSpiedContext.addMockSystemService(ActivityManager.class, mActivityManager);
        mSpiedContext.addMockSystemService(WindowManager.class, mWindowManager);
        when(mSpiedContext.createWindowContext(anyInt(), any())).thenReturn(mSpiedContext);
        when(mDeviceProvisionedController.isCurrentUserSetup()).thenReturn(true);
        when(mDeviceProvisionedController.isCurrentUserSetupInProgress()).thenReturn(false);
        Map<Class<?>, Provider<CarSystemBarElementController.Factory>> controllerFactoryMap =
                new ArrayMap<>();
        Provider<CarSystemBarElementController.Factory> homeButtonControllerProvider =
                () -> new HomeButtonController.Factory() {
                    @Override
                    public HomeButtonController create(CarSystemBarButton view) {
                        return new HomeButtonController(view,
                                mock(CarSystemBarElementStatusBarDisableController.class),
                                mock(CarSystemBarElementStateController.class),
                                mUserTracker);
                    }
                };
        controllerFactoryMap.put(HomeButtonController.class, homeButtonControllerProvider);
        Provider<CarSystemBarElementController.Factory> passengerHomeButtonControllerProvider =
                () -> new PassengerHomeButtonController.Factory() {
                    @Override
                    public PassengerHomeButtonController create(CarSystemBarButton view) {
                        return new PassengerHomeButtonController(view,
                                mock(CarSystemBarElementStatusBarDisableController.class),
                                mock(CarSystemBarElementStateController.class),
                                mUserTracker);
                    }
                };
        controllerFactoryMap.put(PassengerHomeButtonController.class,
                passengerHomeButtonControllerProvider);
        CarSystemBarElementInitializer carSystemBarElementInitializer =
                new CarSystemBarElementInitializer(controllerFactoryMap);
        mSystemBarConfigs = new SystemBarConfigs(mSpiedContext, mTestableResources.getResources());
        CarSystemBarViewControllerFactory carSystemBarViewControllerFactory =
                new CarSystemBarViewControllerImpl.Factory() {
                    public CarSystemBarViewControllerImpl create(@SystemBarSide int side,
                            ViewGroup view) {
                        return spy(new CarSystemBarViewControllerImpl(mSpiedContext, mUserTracker,
                                carSystemBarElementInitializer, mSystemBarConfigs,
                                mButtonRoleHolderController, mButtonSelectionStateController,
                                () -> mCameraPrivacyChipViewController,
                                () -> mMicPrivacyChipViewController, mOverlayVisibilityMediator,
                                side, view));
                    }
                };
        Map<@SystemBarSide Integer, CarSystemBarViewControllerFactory> factoriesMap =
                new HashMap<>();
        factoriesMap.put(LEFT, carSystemBarViewControllerFactory);
        factoriesMap.put(TOP, carSystemBarViewControllerFactory);
        factoriesMap.put(RIGHT, carSystemBarViewControllerFactory);
        factoriesMap.put(BOTTOM, carSystemBarViewControllerFactory);
        mCarSystemBarViewFactory = new CarSystemBarViewFactoryImpl(mSpiedContext,
                mSystemBarConfigs, factoriesMap);

        mRegisterStatusBarResult = new RegisterStatusBarResult(new ArrayMap<>(), 0, 0,
                new AppearanceRegion[0], 0, 0, false, 0, false, 0, 0, "", 0,
                new LetterboxDetails[0]);
        when(mBarService.registerStatusBar(any())).thenReturn(mRegisterStatusBarResult);

        // Needed to inflate top navigation bar.
        mDependency.injectMockDependency(DarkIconDispatcher.class);
        mDependency.injectMockDependency(StatusBarIconController.class);

        initCarSystemBar();
    }

    @After
    public void tearDown() throws Exception {
        if (mSession != null) {
            mSession.finishMocking();
        }
    }

    private void initCarSystemBar() {
        FakeDisplayTracker displayTracker = new FakeDisplayTracker(mSpiedContext);
        FakeExecutor executor = new FakeExecutor(new FakeSystemClock());

        mCarSystemBarController = new CarSystemBarControllerImpl(mSpiedContext,
                mUserTracker,
                mCarSystemBarViewFactory,
                mSystemBarConfigs,
                mLightBarController,
                mStatusBarIconController,
                mWindowManager,
                mDeviceProvisionedController,
                new CommandQueue(mSpiedContext, displayTracker),
                mAutoHideController,
                mButtonSelectionStateListener,
                executor,
                mBarService,
                () -> mKeyguardStateController,
                () -> mIconPolicy,
                mConfigurationController,
                mCarSystemBarRestartTracker,
                displayTracker,
                null);
    }

    @Test
    public void testGetTopWindow_topDisabled_returnsNull() {
        mTestableResources.addOverride(R.bool.config_enableTopSystemBar, false);
        mTestableResources.addOverride(R.bool.config_enableBottomSystemBar, true);
        // If Top Notification Panel is used but top navigation bar is not enabled, SystemUI is
        // expected to crash.
        mTestableResources.addOverride(R.string.config_notificationPanelViewMediator,
                BOTTOM_NOTIFICATION_PANEL);
        mCarSystemBarController.init();

        ViewGroup window = mCarSystemBarController.getBarWindow(TOP);

        assertThat(window).isNull();
    }

    @Test
    public void testGetTopWindow_topEnabled_returnsWindow() {
        mTestableResources.addOverride(R.bool.config_enableTopSystemBar, true);
        mCarSystemBarController.init();

        ViewGroup window = mCarSystemBarController.getBarWindow(TOP);

        assertThat(window).isNotNull();
    }

    @Test
    public void testGetTopWindow_topEnabled_calledTwice_returnsSameWindow() {
        mTestableResources.addOverride(R.bool.config_enableTopSystemBar, true);
        mCarSystemBarController.init();

        ViewGroup window1 = mCarSystemBarController.getBarWindow(TOP);
        ViewGroup window2 = mCarSystemBarController.getBarWindow(TOP);

        assertThat(window1).isEqualTo(window2);
    }

    @Test
    public void testGetBottomWindow_bottomDisabled_returnsNull() {
        mTestableResources.addOverride(R.bool.config_enableBottomSystemBar, false);
        mTestableResources.addOverride(R.bool.config_enableTopSystemBar, true);
        // If Bottom Notification Panel is used but bottom navigation bar is not enabled,
        // SystemUI is expected to crash.
        mTestableResources.addOverride(R.string.config_notificationPanelViewMediator,
                TOP_NOTIFICATION_PANEL);
        mCarSystemBarController.init();

        ViewGroup window = mCarSystemBarController.getBarWindow(BOTTOM);

        assertThat(window).isNull();
    }

    @Test
    public void testGetBottomWindow_bottomEnabled_returnsWindow() {
        mTestableResources.addOverride(R.bool.config_enableBottomSystemBar, true);
        mCarSystemBarController.init();

        ViewGroup window = mCarSystemBarController.getBarWindow(BOTTOM);

        assertThat(window).isNotNull();
    }

    @Test
    public void testGetBottomWindow_bottomEnabled_calledTwice_returnsSameWindow() {
        mTestableResources.addOverride(R.bool.config_enableBottomSystemBar, true);
        mCarSystemBarController.init();

        ViewGroup window1 = mCarSystemBarController.getBarWindow(BOTTOM);
        ViewGroup window2 = mCarSystemBarController.getBarWindow(BOTTOM);

        assertThat(window1).isEqualTo(window2);
    }

    @Test
    public void testGetLeftWindow_leftDisabled_returnsNull() {
        mTestableResources.addOverride(R.integer.config_showDisplayCompatToolbarOnSystemBar, 0);
        mTestableResources.addOverride(R.bool.config_enableLeftSystemBar, false);
        mCarSystemBarController.init();
        ViewGroup window = mCarSystemBarController.getBarWindow(LEFT);
        assertThat(window).isNull();
    }

    @Test
    public void testGetLeftWindow_leftEnabled_returnsWindow() {
        mTestableResources.addOverride(R.integer.config_showDisplayCompatToolbarOnSystemBar, 0);
        mTestableResources.addOverride(R.bool.config_enableLeftSystemBar, true);
        mCarSystemBarController.init();

        ViewGroup window = mCarSystemBarController.getBarWindow(LEFT);

        assertThat(window).isNotNull();
    }

    @Test
    public void testGetLeftWindow_leftEnabled_calledTwice_returnsSameWindow() {
        mTestableResources.addOverride(R.integer.config_showDisplayCompatToolbarOnSystemBar, 0);
        mTestableResources.addOverride(R.bool.config_enableLeftSystemBar, true);
        mCarSystemBarController.init();

        ViewGroup window1 = mCarSystemBarController.getBarWindow(LEFT);
        ViewGroup window2 = mCarSystemBarController.getBarWindow(LEFT);

        assertThat(window1).isEqualTo(window2);
    }

    @Test
    public void testGetRightWindow_rightDisabled_returnsNull() {
        mTestableResources.addOverride(R.bool.config_enableRightSystemBar, false);
        mCarSystemBarController.init();

        ViewGroup window = mCarSystemBarController.getBarWindow(RIGHT);

        assertThat(window).isNull();
    }

    @Test
    public void testGetRightWindow_rightEnabled_returnsWindow() {
        mTestableResources.addOverride(R.bool.config_enableRightSystemBar, true);
        mCarSystemBarController.init();

        ViewGroup window = mCarSystemBarController.getBarWindow(RIGHT);

        assertThat(window).isNotNull();
    }

    @Test
    public void testGetRightWindow_rightEnabled_calledTwice_returnsSameWindow() {
        mTestableResources.addOverride(R.bool.config_enableRightSystemBar, true);
        mCarSystemBarController.init();

        ViewGroup window1 = mCarSystemBarController.getBarWindow(RIGHT);
        ViewGroup window2 = mCarSystemBarController.getBarWindow(RIGHT);

        assertThat(window1).isEqualTo(window2);
    }

    @Test
    public void testSetTopWindowVisibility_setTrue_isVisible() {
        mTestableResources.addOverride(R.bool.config_enableTopSystemBar, true);
        mCarSystemBarController.init();

        ViewGroup window = mCarSystemBarController.getBarWindow(TOP);
        mCarSystemBarController.setWindowVisibility(window, View.VISIBLE);

        assertThat(window.getVisibility()).isEqualTo(View.VISIBLE);
    }

    @Test
    public void testSetTopWindowVisibility_setFalse_isGone() {
        mTestableResources.addOverride(R.bool.config_enableTopSystemBar, true);
        mCarSystemBarController.init();

        ViewGroup window = mCarSystemBarController.getBarWindow(TOP);
        mCarSystemBarController.setWindowVisibility(window, View.GONE);

        assertThat(window.getVisibility()).isEqualTo(View.GONE);
    }

    @Test
    public void testSetBottomWindowVisibility_setTrue_isVisible() {
        mTestableResources.addOverride(R.bool.config_enableBottomSystemBar, true);
        mCarSystemBarController.init();

        ViewGroup window = mCarSystemBarController.getBarWindow(BOTTOM);
        mCarSystemBarController.setWindowVisibility(window, View.VISIBLE);

        assertThat(window.getVisibility()).isEqualTo(View.VISIBLE);
    }

    @Test
    public void testSetBottomWindowVisibility_setFalse_isGone() {
        mTestableResources.addOverride(R.bool.config_enableBottomSystemBar, true);
        mCarSystemBarController.init();

        ViewGroup window = mCarSystemBarController.getBarWindow(BOTTOM);
        mCarSystemBarController.setWindowVisibility(window, View.GONE);

        assertThat(window.getVisibility()).isEqualTo(View.GONE);
    }

    @Test
    public void testSetLeftWindowVisibility_setTrue_isVisible() {
        mTestableResources.addOverride(R.integer.config_showDisplayCompatToolbarOnSystemBar, 0);
        mTestableResources.addOverride(R.bool.config_enableLeftSystemBar, true);
        mCarSystemBarController.init();

        ViewGroup window = mCarSystemBarController.getBarWindow(LEFT);
        mCarSystemBarController.setWindowVisibility(window, View.VISIBLE);

        assertThat(window.getVisibility()).isEqualTo(View.VISIBLE);
    }

    @Test
    public void testSetLeftWindowVisibility_setFalse_isGone() {
        mTestableResources.addOverride(R.integer.config_showDisplayCompatToolbarOnSystemBar, 0);
        mTestableResources.addOverride(R.bool.config_enableLeftSystemBar, true);
        mCarSystemBarController.init();

        ViewGroup window = mCarSystemBarController.getBarWindow(LEFT);
        mCarSystemBarController.setWindowVisibility(window, View.GONE);

        assertThat(window.getVisibility()).isEqualTo(View.GONE);
    }

    @Test
    public void testSetRightWindowVisibility_setTrue_isVisible() {
        mTestableResources.addOverride(R.bool.config_enableRightSystemBar, true);
        mCarSystemBarController.init();

        ViewGroup window = mCarSystemBarController.getBarWindow(RIGHT);
        mCarSystemBarController.setWindowVisibility(window, View.VISIBLE);

        assertThat(window.getVisibility()).isEqualTo(View.VISIBLE);
    }

    @Test
    public void testSetRightWindowVisibility_setFalse_isGone() {
        mTestableResources.addOverride(R.bool.config_enableRightSystemBar, true);
        mCarSystemBarController.init();

        ViewGroup window = mCarSystemBarController.getBarWindow(RIGHT);
        mCarSystemBarController.setWindowVisibility(window, View.GONE);

        assertThat(window.getVisibility()).isEqualTo(View.GONE);
    }

    @Test
    public void testRegisterBottomBarTouchListener_createViewFirst_registrationSuccessful() {
        mTestableResources.addOverride(R.bool.config_enableBottomSystemBar, true);
        mCarSystemBarController.init();

        CarSystemBarViewController bottomBar = mCarSystemBarController.getBarViewController(BOTTOM,
                /* isSetUp= */ true);
        View.OnTouchListener mockOnTouchListener = mock(View.OnTouchListener.class);
        Set<View.OnTouchListener> listeners = new ArraySet<>();
        listeners.add(mockOnTouchListener);
        mCarSystemBarController.registerBarTouchListener(BOTTOM, mockOnTouchListener);

        ArgumentCaptor<Set<View.OnTouchListener>> captor = ArgumentCaptor.forClass(Set.class);
        // called 3 times - once for init, once for test getBarViewController call, and once for
        // test registerBarTouchListener call
        verify(bottomBar, times(3)).setSystemBarTouchListeners(captor.capture());

        List<Set<View.OnTouchListener>> allValues = captor.getAllValues();
        assertThat(allValues.contains(listeners));
    }

    @Test
    public void testRegisterBottomBarTouchListener_registerFirst_registrationSuccessful() {
        mTestableResources.addOverride(R.bool.config_enableBottomSystemBar, true);
        mCarSystemBarController.init();

        View.OnTouchListener mockOnTouchListener = mock(View.OnTouchListener.class);
        Set<View.OnTouchListener> listeners = new ArraySet<>();
        listeners.add(mockOnTouchListener);
        mCarSystemBarController.registerBarTouchListener(BOTTOM, mockOnTouchListener);
        CarSystemBarViewController bottomBar = mCarSystemBarController.getBarViewController(BOTTOM,
                /* isSetUp= */ true);

        ArgumentCaptor<Set<View.OnTouchListener>> captor = ArgumentCaptor.forClass(Set.class);
        // called 3 times - once for init, once for test registerBarTouchListener
        // call, and once for test getBarViewController call
        verify(bottomBar, times(3)).setSystemBarTouchListeners(captor.capture());

        List<Set<View.OnTouchListener>> allValues = captor.getAllValues();
        assertThat(allValues.contains(listeners));
    }

    @Test
    public void testShowAllNavigationButtons_bottomEnabled_bottomNavigationButtonsVisible() {
        mTestableResources.addOverride(R.bool.config_enableBottomSystemBar, true);
        mCarSystemBarController.init();
        CarSystemBarViewController bottomBar = mCarSystemBarController.getBarViewController(BOTTOM,
                /* isSetUp= */ true);
        View bottomNavButtons = bottomBar.getView().findViewById(R.id.nav_buttons);

        mCarSystemBarController.showAllNavigationButtons();

        assertThat(bottomNavButtons.getVisibility()).isEqualTo(View.VISIBLE);
    }

    @Test
    public void testShowAllNavigationButtons_bottomEnabled_bottomKeyguardButtonsGone() {
        mTestableResources.addOverride(R.bool.config_enableBottomSystemBar, true);
        mCarSystemBarController.init();
        CarSystemBarViewController bottomBar = mCarSystemBarController.getBarViewController(BOTTOM,
                /* isSetUp= */ true);
        View bottomKeyguardButtons = bottomBar.getView().findViewById(R.id.lock_screen_nav_buttons);

        mCarSystemBarController.showAllNavigationButtons();

        assertThat(bottomKeyguardButtons.getVisibility()).isEqualTo(View.GONE);
    }

    @Test
    public void testShowAllNavigationButtons_bottomEnabled_bottomOcclusionButtonsGone() {
        mTestableResources.addOverride(R.bool.config_enableBottomSystemBar, true);
        mCarSystemBarController.init();
        CarSystemBarViewController bottomBar = mCarSystemBarController.getBarViewController(BOTTOM,
                /* isSetUp= */ true);
        View occlusionButtons = bottomBar.getView().findViewById(R.id.occlusion_buttons);

        mCarSystemBarController.showAllNavigationButtons();

        assertThat(occlusionButtons.getVisibility()).isEqualTo(View.GONE);
    }

    @Test
    public void testShowAllKeyguardButtons_bottomEnabled_bottomKeyguardButtonsVisible() {
        mTestableResources.addOverride(R.bool.config_enableBottomSystemBar, true);
        mCarSystemBarController.init();
        CarSystemBarViewController bottomBar = mCarSystemBarController.getBarViewController(BOTTOM,
                /* isSetUp= */ true);
        View bottomKeyguardButtons = bottomBar.getView().findViewById(R.id.lock_screen_nav_buttons);

        mCarSystemBarController.showAllKeyguardButtons();

        assertThat(bottomKeyguardButtons.getVisibility()).isEqualTo(View.VISIBLE);
    }

    @Test
    public void testShowAllKeyguardButtons_bottomEnabled_bottomNavigationButtonsGone() {
        mTestableResources.addOverride(R.bool.config_enableBottomSystemBar, true);
        mCarSystemBarController.init();
        CarSystemBarViewController bottomBar = mCarSystemBarController.getBarViewController(BOTTOM,
                /* isSetUp= */ true);
        View bottomNavButtons = bottomBar.getView().findViewById(R.id.nav_buttons);

        mCarSystemBarController.showAllKeyguardButtons();

        assertThat(bottomNavButtons.getVisibility()).isEqualTo(View.GONE);
    }

    @Test
    public void testShowAllKeyguardButtons_bottomEnabled_bottomOcclusionButtonsGone() {
        mTestableResources.addOverride(R.bool.config_enableBottomSystemBar, true);
        mCarSystemBarController.init();
        CarSystemBarViewController bottomBar = mCarSystemBarController.getBarViewController(BOTTOM,
                /* isSetUp= */ true);
        View occlusionButtons = bottomBar.getView().findViewById(R.id.occlusion_buttons);

        mCarSystemBarController.showAllKeyguardButtons();

        assertThat(occlusionButtons.getVisibility()).isEqualTo(View.GONE);
    }

    @Test
    public void testShowOcclusionButtons_bottomEnabled_bottomOcclusionButtonsVisible() {
        mTestableResources.addOverride(R.bool.config_enableBottomSystemBar, true);
        mCarSystemBarController.init();
        CarSystemBarViewController bottomBar = mCarSystemBarController.getBarViewController(BOTTOM,
                /* isSetUp= */ true);
        View occlusionButtons = bottomBar.getView().findViewById(R.id.occlusion_buttons);

        mCarSystemBarController.showAllOcclusionButtons();

        assertThat(occlusionButtons.getVisibility()).isEqualTo(View.VISIBLE);
    }

    @Test
    public void testShowOcclusionButtons_bottomEnabled_bottomNavigationButtonsGone() {
        mTestableResources.addOverride(R.bool.config_enableBottomSystemBar, true);
        mCarSystemBarController.init();
        CarSystemBarViewController bottomBar = mCarSystemBarController.getBarViewController(BOTTOM,
                /* isSetUp= */ true);
        View bottomNavButtons = bottomBar.getView().findViewById(R.id.nav_buttons);

        mCarSystemBarController.showAllOcclusionButtons();

        assertThat(bottomNavButtons.getVisibility()).isEqualTo(View.GONE);
    }

    @Test
    public void testShowOcclusionButtons_bottomEnabled_bottomKeyguardButtonsGone() {
        mTestableResources.addOverride(R.bool.config_enableBottomSystemBar, true);
        mCarSystemBarController.init();
        CarSystemBarViewController bottomBar = mCarSystemBarController.getBarViewController(BOTTOM,
                /* isSetUp= */ true);
        View keyguardButtons = bottomBar.getView().findViewById(R.id.lock_screen_nav_buttons);

        mCarSystemBarController.showAllOcclusionButtons();

        assertThat(keyguardButtons.getVisibility()).isEqualTo(View.GONE);
    }

    @Test
    public void testSetSystemBarStates_stateUpdated() {
        mTestableResources.addOverride(R.bool.config_enableBottomSystemBar, true);
        mCarSystemBarController.init();
        clearSystemBarStates();

        mCarSystemBarController.setSystemBarStates(DISABLE_HOME, /* state2= */ 0);

        assertThat(mCarSystemBarController.getStatusBarState()).isEqualTo(DISABLE_HOME);
    }

    @Test
    public void testSetSystemBarStates_state2Updated() {
        mTestableResources.addOverride(R.bool.config_enableBottomSystemBar, true);
        mCarSystemBarController.init();
        clearSystemBarStates();

        mCarSystemBarController.setSystemBarStates(0, DISABLE2_QUICK_SETTINGS);

        assertThat(mCarSystemBarController.getStatusBarState2()).isEqualTo(DISABLE2_QUICK_SETTINGS);
    }

    @Test
    public void cacheAndHideFocus_doesntCallHideFocus_if_focusParkingViewIsFocused() {
        mCarSystemBarController.init();
        View mockFocusParkingView = mock(FocusParkingView.class);
        View mockContainerView = mock(View.class);
        when(mockContainerView.findFocus()).thenReturn(mockFocusParkingView);

        int returnFocusedViewId =
                CarSystemBarViewControllerImpl.cacheAndHideFocus(mockContainerView);

        assertThat(returnFocusedViewId).isEqualTo(View.NO_ID);
    }

    @Test
    public void testDriverHomeOnDriverSystemUI_isVisible() {
        doReturn(false).when(() ->
                CarSystemUIUserUtil.isSecondaryMUMDSystemUI());
        mTestableResources.addOverride(R.bool.config_enableBottomSystemBar, /* value= */ true);
        mCarSystemBarController.init();

        CarSystemBarViewController bottomBar = mCarSystemBarController.getBarViewController(BOTTOM,
                /* isSetUp= */ true);
        View driverHomeButton = bottomBar.getView().findViewById(R.id.home);
        View passengerHomeButton = bottomBar.getView().findViewById(R.id.passenger_home);

        assertThat(driverHomeButton.getVisibility()).isEqualTo(View.VISIBLE);
        assertThat(passengerHomeButton.getVisibility()).isEqualTo(View.GONE);
    }

    @Test
    public void testPassengerHomeOnSecondarySystemUI_isVisible() {
        doReturn(true).when(() ->
                CarSystemUIUserUtil.isSecondaryMUMDSystemUI());
        mTestableResources.addOverride(R.bool.config_enableBottomSystemBar, true);
        mCarSystemBarController.init();

        CarSystemBarViewController bottomBar = mCarSystemBarController.getBarViewController(BOTTOM,
                /* isSetUp= */ true);
        View driverHomeButton = bottomBar.getView().findViewById(R.id.home);
        View passengerHomeButton = bottomBar.getView().findViewById(R.id.passenger_home);

        assertThat(driverHomeButton.getVisibility()).isEqualTo(View.GONE);
        assertThat(passengerHomeButton.getVisibility()).isEqualTo(View.VISIBLE);
    }

    private void clearSystemBarStates() {
        if (mCarSystemBarController != null) {
            mCarSystemBarController.setSystemBarStates(/* state= */ 0, /* state2= */ 0);
        }
        setLockTaskModeLocked(false);
    }

    private void setLockTaskModeLocked(boolean locked) {
        when(mActivityManager.getLockTaskModeState()).thenReturn(locked
                ? ActivityManager.LOCK_TASK_MODE_LOCKED
                : ActivityManager.LOCK_TASK_MODE_NONE);
        mCarSystemBarController.setSystemBarStates(/* state= */ 0, /* state2= */ 0);
    }

    private void enableSystemBarWithNotificationButton() {
        if (Flags.dockFeature()) {
            mTestableResources.addOverride(R.bool.config_enableTopSystemBar, true);
        } else {
            mTestableResources.addOverride(R.bool.config_enableBottomSystemBar, true);
        }
    }
}
