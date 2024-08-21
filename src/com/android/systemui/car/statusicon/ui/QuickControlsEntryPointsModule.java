/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.systemui.car.statusicon.ui;

import com.android.systemui.car.statusicon.StatusIconViewController;
import com.android.systemui.car.systembar.element.CarSystemBarElementController;

import dagger.Binds;
import dagger.Module;
import dagger.multibindings.ClassKey;
import dagger.multibindings.IntoMap;

/**
 * Dagger injection module for {@link StatusIconViewController}
 */
@Module
public abstract class QuickControlsEntryPointsModule {

    /** Injects BluetoothStatusIconController. */
    @Binds
    @IntoMap
    @ClassKey(BluetoothStatusIconController.class)
    public abstract CarSystemBarElementController.Factory bindBluetoothStatusIconController(
            BluetoothStatusIconController.Factory bluetoothStatusIconController);

    /** Injects SignalStatusIconController. */
    @Binds
    @IntoMap
    @ClassKey(SignalStatusIconController.class)
    public abstract CarSystemBarElementController.Factory bindSignalStatusIconController(
            SignalStatusIconController.Factory signalStatusIconController);

    /** Injects LocationStatusIconController. */
    @Binds
    @IntoMap
    @ClassKey(LocationStatusIconController.class)
    public abstract CarSystemBarElementController.Factory bindLocationStatusIconController(
            LocationStatusIconController.Factory locationStatusIconController);

    /** Injects PhoneCallStatusIconController. */
    @Binds
    @IntoMap
    @ClassKey(PhoneCallStatusIconController.class)
    public abstract CarSystemBarElementController.Factory bindPhoneCallStatusIconController(
            PhoneCallStatusIconController.Factory phoneCallStatusIconController);

    /** Injects ThemeSwitchStatusIconController. */
    @Binds
    @IntoMap
    @ClassKey(DriveModeStatusIconController.class)
    public abstract CarSystemBarElementController.Factory bindDriveModeStatusIconController(
            DriveModeStatusIconController.Factory driveModeStatusIconController);

    /** Injects MediaVolumeStatusIconController. */
    @Binds
    @IntoMap
    @ClassKey(MediaVolumeStatusIconController.class)
    public abstract CarSystemBarElementController.Factory bindMediaVolumeStatusIconController(
            MediaVolumeStatusIconController.Factory mediaVolumeStatusIconController);
}