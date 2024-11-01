/*
 * Copyright (C) 2022 The Android Open Source Project
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

import static com.android.systemui.car.systembar.CarSystemBarController.LEFT;
import static com.android.systemui.car.systembar.CarSystemBarController.TOP;
import static com.android.systemui.car.systembar.CarSystemBarController.RIGHT;
import static com.android.systemui.car.systembar.CarSystemBarController.BOTTOM;

import android.annotation.Nullable;
import android.content.Context;
import android.os.Handler;
import android.view.IWindowManager;
import android.view.WindowManager;

import com.android.internal.statusbar.IStatusBarService;
import com.android.systemui.CoreStartable;
import com.android.systemui.R;
import com.android.systemui.car.CarDeviceProvisionedController;
import com.android.systemui.car.dagger.CarSysUIDynamicOverride;
import com.android.systemui.car.displaycompat.ToolbarController;
import com.android.systemui.car.hvac.HvacButtonController;
import com.android.systemui.car.hvac.TemperatureControlViewController;
import com.android.systemui.car.keyguard.KeyguardSystemBarPresenter;
import com.android.systemui.car.notification.NotificationButtonController;
import com.android.systemui.car.statusicon.StatusIconPanelViewController;
import com.android.systemui.car.systembar.element.CarSystemBarElementController;
import com.android.systemui.car.users.CarSystemUIUserUtil;
import com.android.systemui.dagger.SysUISingleton;
import com.android.systemui.dagger.qualifiers.Main;
import com.android.systemui.plugins.DarkIconDispatcher;
import com.android.systemui.settings.DisplayTracker;
import com.android.systemui.settings.UserTracker;
import com.android.systemui.statusbar.CommandQueue;
import com.android.systemui.statusbar.phone.AutoHideController;
import com.android.systemui.statusbar.phone.LightBarController;
import com.android.systemui.statusbar.phone.PhoneStatusBarPolicy;
import com.android.systemui.statusbar.policy.ConfigurationController;
import com.android.systemui.statusbar.policy.ConfigurationController.ConfigurationListener;
import com.android.systemui.statusbar.policy.KeyguardStateController;
import com.android.systemui.util.concurrency.DelayableExecutor;

import dagger.Binds;
import dagger.BindsOptionalOf;
import dagger.Lazy;
import dagger.Module;
import dagger.Provides;
import dagger.multibindings.ClassKey;
import dagger.multibindings.IntKey;
import dagger.multibindings.IntoMap;
import dagger.multibindings.IntoSet;
import dagger.multibindings.Multibinds;

import java.util.Map;
import java.util.Optional;

import javax.inject.Provider;

/**
 * Dagger injection module for {@link CarSystemBar}.
 *
 * This module includes the non-@Inject classes used as part of the {@link CarSystemBar}, allowing
 * extensions of SystemUI to override and provide their own implementations without replacing the
 * default system bar class.
 */
@Module
public abstract class CarSystemBarModule {

    @Provides
    @IntoMap
    @ClassKey(CarSystemBar.class)
    static CoreStartable bindCarSystemBarStartable(CarSystemBar systemBarService) {
        return systemBarService;
    }

    @Provides
    @IntoSet
    static ConfigurationListener provideCarSystemBarConfigListener(
            CarSystemBarController carSystemBarController) {
        return carSystemBarController;
    }

    @BindsOptionalOf
    @CarSysUIDynamicOverride
    abstract ButtonSelectionStateListener optionalButtonSelectionStateListener();

    @SysUISingleton
    @Provides
    static ButtonSelectionStateListener provideButtonSelectionStateListener(@CarSysUIDynamicOverride
            Optional<ButtonSelectionStateListener> overrideButtonSelectionStateListener,
            ButtonSelectionStateController controller) {
        if (overrideButtonSelectionStateListener.isPresent()) {
            return overrideButtonSelectionStateListener.get();
        }
        return new ButtonSelectionStateListener(controller);
    }

    @BindsOptionalOf
    @CarSysUIDynamicOverride
    abstract ButtonSelectionStateController optionalButtonSelectionStateController();

    @SysUISingleton
    @Provides
    static ButtonSelectionStateController provideButtonSelectionStateController(Context context,
            @CarSysUIDynamicOverride Optional<ButtonSelectionStateController> controller) {
        if (controller.isPresent()) {
            return controller.get();
        }
        return new ButtonSelectionStateController(context);
    }

    @BindsOptionalOf
    @CarSysUIDynamicOverride
    abstract CarSystemBarController optionalCarSystemBarController();

    /**
     * Allows for the replacement of {@link CarSystemBarController} class with a custom subclass.
     * Note that this is not ideal and should be used as a last resort since there are no guarantees
     * that there will not be changes upstream that break the dependencies here (creating additional
     * maintenance burden).
     */
    @SysUISingleton
    @Provides
    static CarSystemBarController provideCarSystemBarController(
            IWindowManager iWindowManager,
            @Main Handler mainHandler,
            @CarSysUIDynamicOverride Optional<CarSystemBarController> carSystemBarController,
            Context context,
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
            ConfigurationController configurationController,
            CarSystemBarRestartTracker restartTracker,
            DisplayTracker displayTracker,
            @Nullable ToolbarController toolbarController) {

        if (carSystemBarController.isPresent()) {
            return carSystemBarController.get();
        }

        boolean isSecondaryMUMDSystemUI = (CarSystemUIUserUtil.isSecondaryMUMDSystemUI()
                || CarSystemUIUserUtil.isMUPANDSystemUI());
        boolean isSecondaryUserRROsEnabled = context.getResources()
                .getBoolean(R.bool.config_enableSecondaryUserRRO);

        if (isSecondaryMUMDSystemUI && isSecondaryUserRROsEnabled) {
            return new MDSystemBarsControllerImpl(iWindowManager, mainHandler, context, userTracker,
                    carSystemBarViewFactory, systemBarConfigs, lightBarController,
                    darkIconDispatcher, windowManager, deviceProvisionedController, commandQueue,
                    autoHideController, buttonSelectionStateListener, mainExecutor, barService,
                    keyguardStateControllerLazy, iconPolicyLazy, configurationController,
                    restartTracker, displayTracker, toolbarController);
        } else {
            return new CarSystemBarControllerImpl(context, userTracker, carSystemBarViewFactory,
                    systemBarConfigs, lightBarController, darkIconDispatcher, windowManager,
                    deviceProvisionedController, commandQueue, autoHideController,
                    buttonSelectionStateListener, mainExecutor, barService,
                    keyguardStateControllerLazy, iconPolicyLazy, configurationController,
                    restartTracker, displayTracker, toolbarController);
        }
    }

    // CarSystemBarElements

    /** Empty set for CarSystemBarElements. */
    @Multibinds
    abstract Map<Class<?>, CarSystemBarElementController.Factory> bindEmptyElementFactoryMap();

    /** Injects CarSystemBarPanelButtonViewController */
    @Binds
    @IntoMap
    @ClassKey(CarSystemBarPanelButtonViewController.class)
    public abstract CarSystemBarElementController.Factory bindSystemBarPanelButtonController(
            CarSystemBarPanelButtonViewController.Factory factory);

    /** Injects DockViewControllerWrapper */
    @Binds
    @IntoMap
    @ClassKey(DockViewControllerWrapper.class)
    public abstract CarSystemBarElementController.Factory bindDockViewControllerWrapper(
            DockViewControllerWrapper.Factory factory);

    /** Injects DataSubscriptionUnseenIconController */
    @Binds
    @IntoMap
    @ClassKey(DataSubscriptionUnseenIconController.class)
    public abstract CarSystemBarElementController.Factory bindDataSubscriptionUnseenIconController(
            DataSubscriptionUnseenIconController.Factory factory);

    /** Injects UserNamePanelButtonViewController */
    @Binds
    @IntoMap
    @ClassKey(UserNamePanelButtonViewController.class)
    public abstract CarSystemBarElementController.Factory bindUserNamePanelButtonViewController(
            UserNamePanelButtonViewController.Factory factory);

    /** Injects UserNameTextViewController */
    @Binds
    @IntoMap
    @ClassKey(UserNameTextViewController.class)
    public abstract CarSystemBarElementController.Factory bindUserNameTextViewController(
            UserNameTextViewController.Factory factory);

    /** Injects UserNameImageViewController */
    @Binds
    @IntoMap
    @ClassKey(UserNameImageViewController.class)
    public abstract CarSystemBarElementController.Factory bindUserNameImageViewController(
            UserNameImageViewController.Factory factory);

    /** Injects KeyguardSystemBarPresenter */
    @SysUISingleton
    @Provides
    static Optional<KeyguardSystemBarPresenter> provideKeyguardSystemBarPresenter(
             CarSystemBarController controller) {
        if (controller instanceof KeyguardSystemBarPresenter) {
            return Optional.of((KeyguardSystemBarPresenter) controller);
        } else {
            return Optional.empty();
        }
    }

    /** Injects DebugPanelButtonViewController */
    @Binds
    @IntoMap
    @ClassKey(DebugPanelButtonViewController.class)
    public abstract CarSystemBarElementController.Factory bindDebugPanelButtonViewController(
            DebugPanelButtonViewController.Factory factory);

    /** Injects CarSystemBarViewFactory */
    @SysUISingleton
    @Binds
    public abstract CarSystemBarViewFactory bindCarSystemBarViewFactory(
            CarSystemBarViewFactoryImpl impl);

    /** Injects CarSystemBarViewController for @SystemBarSide LEFT */
    @Binds
    @IntoMap
    @IntKey(LEFT)
    public abstract CarSystemBarViewControllerFactory bindLeftCarSystemBarViewFactory(
            CarSystemBarViewControllerImpl.Factory factory);

    /** Injects CarSystemBarViewController for @SystemBarSide TOP */
    @Binds
    @IntoMap
    @IntKey(TOP)
    public abstract CarSystemBarViewControllerFactory bindTopCarSystemBarViewFactory(
            CarTopSystemBarViewController.Factory factory);

    /** Injects CarSystemBarViewController for @SystemBarSide RIGHT */
    @Binds
    @IntoMap
    @IntKey(RIGHT)
    public abstract CarSystemBarViewControllerFactory bindRightCarSystemBarViewFactory(
            CarSystemBarViewControllerImpl.Factory factory);

    /** Injects CarSystemBarViewController for @SystemBarSide BOTTOM */
    @Binds
    @IntoMap
    @IntKey(BOTTOM)
    public abstract CarSystemBarViewControllerFactory bindBottomCarSystemBarViewFactory(
            CarSystemBarViewControllerImpl.Factory factory);

    /** Injects CarSystemBarButtonController */
    @Binds
    @IntoMap
    @ClassKey(CarSystemBarButtonController.class)
    public abstract CarSystemBarElementController.Factory bindCarSystemBarButtonControllerFactory(
            CarSystemBarButtonController.Factory factory);

    /** Injects NotificationButtonController */
    @Binds
    @IntoMap
    @ClassKey(NotificationButtonController.class)
    public abstract CarSystemBarElementController.Factory bindNotificationButtonControllerFactory(
            NotificationButtonController.Factory factory);

    /** Injects HvacButtonController */
    @Binds
    @IntoMap
    @ClassKey(HvacButtonController.class)
    public abstract CarSystemBarElementController.Factory bindHvacButtonControllerFactory(
            HvacButtonController.Factory factory);

    /** Injects TemperatureControlViewController */
    @Binds
    @IntoMap
    @ClassKey(TemperatureControlViewController.class)
    public abstract CarSystemBarElementController.Factory
            bindTemperatureControlViewControllerFactory(
                    TemperatureControlViewController.Factory factory);

    /** Injects HomeButtonController */
    @Binds
    @IntoMap
    @ClassKey(HomeButtonController.class)
    public abstract CarSystemBarElementController.Factory bindHomeButtonControllerFactory(
            HomeButtonController.Factory factory);

    /** Injects PassengerHomeButtonController */
    @Binds
    @IntoMap
    @ClassKey(PassengerHomeButtonController.class)
    public abstract CarSystemBarElementController.Factory bindPassengerHomeButtonControllerFactory(
            PassengerHomeButtonController.Factory factory);
}
