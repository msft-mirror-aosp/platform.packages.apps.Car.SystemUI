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

import static android.hardware.SensorPrivacyManager.Sensors.MICROPHONE;

import android.content.Context;
import android.hardware.SensorPrivacyManager;

import androidx.annotation.IdRes;

import com.android.systemui.R;
import com.android.systemui.car.CarDeviceProvisionedController;
import com.android.systemui.car.privacy.PrivacyChip;
import com.android.systemui.car.statusicon.StatusIconPanelViewController;
import com.android.systemui.car.systembar.element.CarSystemBarElementController;
import com.android.systemui.car.systembar.element.CarSystemBarElementStateController;
import com.android.systemui.car.systembar.element.CarSystemBarElementStatusBarDisableController;
import com.android.systemui.privacy.PrivacyItemController;
import com.android.systemui.privacy.PrivacyType;
import com.android.systemui.settings.UserTracker;

import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;
import dagger.assisted.AssistedInject;

import javax.inject.Provider;

/** Controls a Mic Privacy Chip view in system icons. */
public class MicPrivacyChipViewController extends PrivacyChipViewController {

    @AssistedInject
    public MicPrivacyChipViewController(@Assisted PrivacyChip view,
            CarSystemBarElementStatusBarDisableController disableController,
            CarSystemBarElementStateController stateController,
            Context context,
            PrivacyItemController privacyItemController,
            SensorPrivacyManager sensorPrivacyManager,
            UserTracker userTracker,
            CarDeviceProvisionedController carDeviceProvisionedController,
            Provider<StatusIconPanelViewController.Builder> panelControllerBuilderProvider) {
        super(view, disableController, stateController, context, privacyItemController,
                sensorPrivacyManager, userTracker, carDeviceProvisionedController,
                panelControllerBuilderProvider);
    }

    @AssistedFactory
    public interface Factory extends
            CarSystemBarElementController.Factory<PrivacyChip,
                    MicPrivacyChipViewController> {
    }

    @Override
    protected @SensorPrivacyManager.Sensors.Sensor int getChipSensor() {
        return MICROPHONE;
    }

    @Override
    protected PrivacyType getChipPrivacyType() {
        return PrivacyType.TYPE_MICROPHONE;
    }

    @Override
    protected @IdRes int getChipResourceId() {
        return R.id.mic_privacy_chip;
    }

    @Override
    protected int getPanelLayoutRes() {
        return R.layout.qc_mic_panel;
    }
}
