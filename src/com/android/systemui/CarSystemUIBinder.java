/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.systemui;

import com.android.keyguard.KeyguardBiometricLockoutLogger;
import com.android.systemui.accessibility.SystemActions;
import com.android.systemui.biometrics.AuthController;
import com.android.systemui.car.cluster.ClusterDisplayController;
import com.android.systemui.car.notification.CarNotificationModule;
import com.android.systemui.car.sideloaded.SideLoadedAppController;
import com.android.systemui.car.statusicon.ui.QuickControlsEntryPointsModule;
import com.android.systemui.car.systembar.CarSystemBar;
import com.android.systemui.car.toast.CarToastUI;
import com.android.systemui.car.voicerecognition.ConnectedDeviceVoiceRecognitionNotifier;
import com.android.systemui.car.volume.VolumeUI;
import com.android.systemui.car.window.OverlayWindowModule;
import com.android.systemui.car.window.SystemUIOverlayWindowManager;
import com.android.systemui.globalactions.GlobalActionsComponent;
import com.android.systemui.keyguard.KeyguardViewMediator;
import com.android.systemui.keyguard.dagger.KeyguardModule;
import com.android.systemui.power.PowerUI;
import com.android.systemui.recents.Recents;
import com.android.systemui.recents.RecentsModule;
import com.android.systemui.shortcut.ShortcutKeyDispatcher;
import com.android.systemui.statusbar.dagger.StatusBarModule;
import com.android.systemui.statusbar.notification.InstantAppNotifier;
import com.android.systemui.statusbar.notification.dagger.NotificationsModule;
import com.android.systemui.statusbar.phone.StatusBar;
import com.android.systemui.theme.ThemeOverlayController;
import com.android.systemui.util.leak.GarbageMonitor;
import com.android.systemui.wmshell.WMShell;

import dagger.Binds;
import dagger.Module;
import dagger.multibindings.ClassKey;
import dagger.multibindings.IntoMap;

/** Binder for car specific {@link CoreStartable} modules. */
@Module(includes = {RecentsModule.class, StatusBarModule.class, NotificationsModule.class,
        KeyguardModule.class, OverlayWindowModule.class, CarNotificationModule.class,
        QuickControlsEntryPointsModule.class})
public abstract class CarSystemUIBinder {
    /** Inject into AuthController. */
    @Binds
    @IntoMap
    @ClassKey(AuthController.class)
    public abstract CoreStartable bindAuthController(AuthController sysui);

    /** Inject Car Navigation Bar. */
    @Binds
    @IntoMap
    @ClassKey(CarSystemBar.class)
    public abstract CoreStartable bindCarSystemBar(CarSystemBar sysui);

    /** Inject into GarbageMonitor.Service. */
    @Binds
    @IntoMap
    @ClassKey(GarbageMonitor.Service.class)
    public abstract CoreStartable bindGarbageMonitorService(GarbageMonitor.Service sysui);

    /** Inject into GlobalActionsComponent. */
    @Binds
    @IntoMap
    @ClassKey(GlobalActionsComponent.class)
    public abstract CoreStartable bindGlobalActionsComponent(GlobalActionsComponent sysui);

    /** Inject into InstantAppNotifier. */
    @Binds
    @IntoMap
    @ClassKey(InstantAppNotifier.class)
    public abstract CoreStartable bindInstantAppNotifier(InstantAppNotifier sysui);

    /** Inject into KeyguardViewMediator. */
    @Binds
    @IntoMap
    @ClassKey(KeyguardViewMediator.class)
    public abstract CoreStartable bindKeyguardViewMediator(KeyguardViewMediator sysui);

    /** Inject into KeyguardBiometricLockoutLogger. */
    @Binds
    @IntoMap
    @ClassKey(KeyguardBiometricLockoutLogger.class)
    public abstract CoreStartable bindKeyguardBiometricLockoutLogger(
            KeyguardBiometricLockoutLogger sysui);

    /** Inject into LatencyTests. */
    @Binds
    @IntoMap
    @ClassKey(LatencyTester.class)
    public abstract CoreStartable bindLatencyTester(LatencyTester sysui);

    /** Inject into PowerUI. */
    @Binds
    @IntoMap
    @ClassKey(PowerUI.class)
    public abstract CoreStartable bindPowerUI(PowerUI sysui);

    /** Inject into Recents. */
    @Binds
    @IntoMap
    @ClassKey(Recents.class)
    public abstract CoreStartable bindRecents(Recents sysui);

    /** Inject into ScreenDecorations. */
    @Binds
    @IntoMap
    @ClassKey(ScreenDecorations.class)
    public abstract CoreStartable bindScreenDecorations(ScreenDecorations sysui);

    /** Inject into ShortcutKeyDispatcher. */
    @Binds
    @IntoMap
    @ClassKey(ShortcutKeyDispatcher.class)
    public abstract CoreStartable bindsShortcutKeyDispatcher(ShortcutKeyDispatcher sysui);

    /** Inject into SliceBroadcastRelayHandler. */
    @Binds
    @IntoMap
    @ClassKey(SliceBroadcastRelayHandler.class)
    public abstract CoreStartable bindSliceBroadcastRelayHandler(SliceBroadcastRelayHandler sysui);

    /** Inject into ThemeOverlayController. */
    @Binds
    @IntoMap
    @ClassKey(ThemeOverlayController.class)
    public abstract CoreStartable bindThemeOverlayController(ThemeOverlayController sysui);

    /** Inject into StatusBar. */
    @Binds
    @IntoMap
    @ClassKey(StatusBar.class)
    public abstract CoreStartable bindsStatusBar(StatusBar sysui);

    /** Inject into SystemActions. */
    @Binds
    @IntoMap
    @ClassKey(SystemActions.class)
    public abstract CoreStartable bindSystemActions(SystemActions sysui);

    /** Inject into VolumeUI. */
    @Binds
    @IntoMap
    @ClassKey(VolumeUI.class)
    public abstract CoreStartable bindVolumeUI(VolumeUI sysui);

    /** Inject into CarToastUI. */
    @Binds
    @IntoMap
    @ClassKey(CarToastUI.class)
    public abstract CoreStartable bindCarToastUI(CarToastUI service);

    /** Inject into ConnectedDeviceVoiceRecognitionNotifier. */
    @Binds
    @IntoMap
    @ClassKey(ConnectedDeviceVoiceRecognitionNotifier.class)
    public abstract CoreStartable bindConnectedDeviceVoiceRecognitionNotifier(
            ConnectedDeviceVoiceRecognitionNotifier sysui);

    /** Inject into SystemUIOverlayWindowManager. */
    @Binds
    @IntoMap
    @ClassKey(SystemUIOverlayWindowManager.class)
    public abstract CoreStartable bindSystemUIPrimaryWindowManager(
            SystemUIOverlayWindowManager sysui);

    /** Inject into SideLoadedAppController. */
    @Binds
    @IntoMap
    @ClassKey(SideLoadedAppController.class)
    public abstract CoreStartable bindSideLoadedAppController(SideLoadedAppController sysui);

    /** Inject into WMShell. */
    @Binds
    @IntoMap
    @ClassKey(WMShell.class)
    public abstract CoreStartable bindWMShell(WMShell sysui);

    /** Inject into ClusterDisplayController. */
    @Binds
    @IntoMap
    @ClassKey(ClusterDisplayController.class)
    public abstract CoreStartable bindClusterDisplayController(ClusterDisplayController sysui);
}
