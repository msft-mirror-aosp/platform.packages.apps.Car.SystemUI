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

import static com.android.systemui.car.systembar.CarSystemBarController.BOTTOM_BAR_NAME;
import static com.android.systemui.car.systembar.CarSystemBarController.LEFT_BAR_NAME;
import static com.android.systemui.car.systembar.CarSystemBarController.RIGHT_BAR_NAME;
import static com.android.systemui.car.systembar.CarSystemBarController.TOP_BAR_NAME;

import android.content.Context;
import android.os.Handler;
import android.view.WindowManager;

import com.android.internal.statusbar.IStatusBarService;
import com.android.systemui.CoreStartable;
import com.android.systemui.car.CarDeviceProvisionedController;
import com.android.systemui.car.dagger.CarSysUIDynamicOverride;
import com.android.systemui.car.flags.FlagManager;
import com.android.systemui.car.flexibleui.CarSystemBarElementController;
import com.android.systemui.car.flexibleui.FlexibleUiModule;
import com.android.systemui.car.hvac.HvacButtonController;
import com.android.systemui.car.hvac.TemperatureControlViewController;
import com.android.systemui.car.keyguard.KeyguardSystemBarPresenter;
import com.android.systemui.car.notification.NotificationButtonController;
import com.android.systemui.car.qc.datasubscription.DataSubscriptionModule;
import com.android.systemui.car.shared.R;
import com.android.systemui.car.systembar.aaosstudio.AaosStudioButtonModule;
import com.android.systemui.car.systembar.appgrid.AppGridButtonModule;
import com.android.systemui.car.systembar.assistant.AssistantButtonModule;
import com.android.systemui.car.systembar.controlcenter.ControlCenterButtonModule;
import com.android.systemui.car.systembar.debugpanel.DebugPanelModule;
import com.android.systemui.car.systembar.home.HomeButtonModule;
import com.android.systemui.car.systembar.notificationchip.PromotedNotificationChipModule;
import com.android.systemui.car.systembar.panel.PanelModule;
import com.android.systemui.car.systembar.passengerhome.PassengerHomeButtonModule;
import com.android.systemui.car.systembar.privacy.camera.PrivacyChipCameraModule;
import com.android.systemui.car.systembar.privacy.cast.PrivacyChipCastModule;
import com.android.systemui.car.systembar.privacy.mic.PrivacyChipMicModule;
import com.android.systemui.car.systembar.privacy.share.PrivacyChipShareModule;
import com.android.systemui.car.systembar.usernamepanel.UserNamePanelModule;
import com.android.systemui.car.systembar.volume.VolumeButtonModule;
import com.android.systemui.car.users.CarSystemUIUserUtil;
import com.android.systemui.car.wm.scalableui.panel.TaskPanelInfoRepository;
import com.android.systemui.car.wm.scalableui.systemwindow.SystemUiWindowProvider;
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
import dagger.multibindings.IntoMap;
import dagger.multibindings.IntoSet;
import dagger.multibindings.Multibinds;
import dagger.multibindings.StringKey;

import java.util.Map;
import java.util.Optional;

/**
 * Dagger injection module for {@link CarSystemBar}.
 *
 * This module includes the non-@Inject classes used as part of the {@link CarSystemBar}, allowing
 * extensions of SystemUI to override and provide their own implementations without replacing the
 * default system bar class.
 */
@Module(includes = {
        AaosStudioButtonModule.class,
        AppGridButtonModule.class,
        AssistantButtonModule.class,
        ControlCenterButtonModule.class,
        DebugPanelModule.class,
        DataSubscriptionModule.class,
        ExtensionPanelUpdatesCarSystemBarModule.class,
        FlexibleUiModule.class,
        HomeButtonModule.class,
        PanelModule.class,
        PassengerHomeButtonModule.class,
        PrivacyChipMicModule.class,
        PrivacyChipCameraModule.class,
        PrivacyChipCastModule.class,
        PrivacyChipShareModule.class,
        PromotedNotificationChipModule.class,
        SplitCarSystemBarModule.class,
        UserNamePanelModule.class,
        VolumeButtonModule.class})
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
            TaskPanelInfoRepository infoRepository,
            @CarSysUIDynamicOverride Optional<ButtonSelectionStateController> controller,
            FlagManager flagManager) {
        if (controller.isPresent()) {
            return controller.get();
        }
        return new ButtonSelectionStateController(context, infoRepository, flagManager);
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
            @Main Handler mainHandler,
            @CarSysUIDynamicOverride Optional<CarSystemBarController> carSystemBarController,
            Context context,
            UserTracker userTracker,
            CarSystemBarViewFactory carSystemBarViewFactory,
            ButtonSelectionStateController buttonSelectionStateController,
            ButtonRoleHolderController buttonRoleHolderController,
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
            SystemUiWindowProvider windowProvider) {

        if (carSystemBarController.isPresent()) {
            return carSystemBarController.get();
        }

        boolean isSecondaryMUMDSystemUI = (CarSystemUIUserUtil.isSecondaryMUMDSystemUI()
                || CarSystemUIUserUtil.isMUPANDSystemUI());
        boolean isSecondaryUserRROsEnabled = context.getResources()
                .getBoolean(R.bool.config_enableSecondaryUserRRO);

        if (isSecondaryMUMDSystemUI && isSecondaryUserRROsEnabled) {
            return new MDSystemBarsControllerImpl(mainHandler, context, userTracker,
                    carSystemBarViewFactory, systemBarConfigs, lightBarController,
                    darkIconDispatcher, windowManager, deviceProvisionedController, commandQueue,
                    autoHideController, buttonSelectionStateListener, mainExecutor, barService,
                    keyguardStateControllerLazy, iconPolicyLazy, configurationController,
                    restartTracker, displayTracker, windowProvider);
        } else {
            return new CarSystemBarControllerImpl(context, userTracker, carSystemBarViewFactory,
                    systemBarConfigs, lightBarController, darkIconDispatcher, windowManager,
                    deviceProvisionedController, commandQueue, autoHideController,
                    buttonSelectionStateListener, mainExecutor, barService,
                    keyguardStateControllerLazy, iconPolicyLazy, configurationController,
                    restartTracker, displayTracker, windowProvider, mainHandler);
        }
    }

    // CarSystemBarElements

    /** Empty set for CarSystemBarElements. */
    @Multibinds
    abstract Map<Class<?>, CarSystemBarElementController.Factory> bindEmptyElementFactoryMap();

    /** Injects DockViewControllerWrapper */
    @Binds
    @IntoMap
    @ClassKey(DockViewControllerWrapper.class)
    public abstract CarSystemBarElementController.Factory bindDockViewControllerWrapper(
            DockViewControllerWrapper.Factory factory);

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

    /** Injects CarSystemBarViewFactory */
    @SysUISingleton
    @Binds
    public abstract CarSystemBarViewFactory bindCarSystemBarViewFactory(
            CarSystemBarViewFactoryImpl impl);

    /** Injects CarSystemBarViewController for LEFT_BAR_NAME */
    @Binds
    @IntoMap
    @StringKey(LEFT_BAR_NAME)
    public abstract CarSystemBarViewControllerFactory<?> bindLeftCarSystemBarViewFactory(
            CarSystemBarViewControllerImpl.Factory factory);

    /** Injects CarSystemBarViewController for TOP_BAR_NAME */
    @Binds
    @IntoMap
    @StringKey(TOP_BAR_NAME)
    public abstract CarSystemBarViewControllerFactory<?> bindTopCarSystemBarViewFactory(
            CarSystemBarViewControllerImpl.Factory factory);

    /** Injects CarSystemBarViewController for RIGHT_BAR_NAME */
    @Binds
    @IntoMap
    @StringKey(RIGHT_BAR_NAME)
    public abstract CarSystemBarViewControllerFactory<?> bindRightCarSystemBarViewFactory(
            CarSystemBarViewControllerImpl.Factory factory);

    /** Injects CarSystemBarViewController for BOTTOM_BAR_NAME */
    @Binds
    @IntoMap
    @StringKey(BOTTOM_BAR_NAME)
    public abstract CarSystemBarViewControllerFactory<?> bindBottomCarSystemBarViewFactory(
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

    @Provides
    @IntoMap
    @StringKey(TOP_BAR_NAME)
    static CarSystemBarViewSupplier bindTopCarSystemBarViewSupplier() {
        return new CarSystemBarViewSupplierUsingLayout(R.layout.car_top_system_bar,
                R.layout.car_top_system_bar_unprovisioned);
    }

    @Provides
    @IntoMap
    @StringKey(TOP_BAR_NAME)
    static CarSystemBarWindowSupplier bindTopCarSystemBarWindowSupplier() {
        return new CarSystemBarWindowSupplierUsingLayout(
                com.android.systemui.res.R.layout.navigation_bar_window,
                R.id.car_top_bar_window);
    }

    @Provides
    @IntoMap
    @StringKey(LEFT_BAR_NAME)
    static CarSystemBarViewSupplier bindLeftCarSystemBarViewSupplier() {
        return new CarSystemBarViewSupplierUsingLayout(R.layout.car_left_system_bar,
                R.layout.car_left_system_bar_unprovisioned);
    }

    @Provides
    @IntoMap
    @StringKey(LEFT_BAR_NAME)
    static CarSystemBarWindowSupplier bindLeftCarSystemBarWindowSupplier() {
        return new CarSystemBarWindowSupplierUsingLayout(
                com.android.systemui.res.R.layout.navigation_bar_window,
                R.id.car_left_bar_window);
    }

    @Provides
    @IntoMap
    @StringKey(RIGHT_BAR_NAME)
    static CarSystemBarViewSupplier bindRightCarSystemBarViewSupplier() {
        return new CarSystemBarViewSupplierUsingLayout(R.layout.car_right_system_bar,
                R.layout.car_right_system_bar_unprovisioned);
    }

    @Provides
    @IntoMap
    @StringKey(RIGHT_BAR_NAME)
    static CarSystemBarWindowSupplier bindRightCarSystemBarWindowSupplier() {
        return new CarSystemBarWindowSupplierUsingLayout(
                com.android.systemui.res.R.layout.navigation_bar_window,
                R.id.car_right_bar_window);
    }

    @Provides
    @IntoMap
    @StringKey(BOTTOM_BAR_NAME)
    static CarSystemBarViewSupplier bindBottomCarSystemBarViewSupplier() {
        return new CarSystemBarViewSupplierUsingLayout(R.layout.car_bottom_system_bar,
                R.layout.car_bottom_system_bar_unprovisioned);
    }

    @Provides
    @IntoMap
    @StringKey(BOTTOM_BAR_NAME)
    static CarSystemBarWindowSupplier bindBottomCarSystemBarWindowSupplier() {
        return new CarSystemBarWindowSupplierUsingLayout(
                com.android.systemui.res.R.layout.navigation_bar_window,
                R.id.car_bottom_bar_window);
    }

    /** Injects SystemBarConfigs */
    @SysUISingleton
    @Binds
    public abstract SystemBarConfigs bindSystemBarConfigs(SystemBarConfigsImpl impl);
}
