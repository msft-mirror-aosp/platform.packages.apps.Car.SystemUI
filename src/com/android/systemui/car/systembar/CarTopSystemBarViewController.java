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

import static com.android.systemui.car.systembar.CarSystemBarController.TOP;

import android.annotation.LayoutRes;
import android.content.Context;
import android.view.Gravity;
import android.view.View;

import androidx.annotation.Nullable;

import com.android.systemui.R;
import com.android.systemui.car.CarDeviceProvisionedController;
import com.android.systemui.car.statusicon.StatusIconPanelViewController;
import com.android.systemui.car.systembar.CarSystemBarController.SystemBarSide;
import com.android.systemui.car.systembar.element.CarSystemBarElementInitializer;
import com.android.systemui.settings.UserTracker;

import dagger.Lazy;
import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;
import dagger.assisted.AssistedInject;

import javax.inject.Provider;

/**
 * A controller for initializing the TOP CarSystemBarView.
 * TODO(b/373710798): remove privacy chip related code when they are migrated to flexible ui.
 */
public class CarTopSystemBarViewController extends CarSystemBarViewController {

    private final CarDeviceProvisionedController mCarDeviceProvisionedController;
    private final Provider<StatusIconPanelViewController.Builder> mPanelControllerBuilderProvider;

    private int mPrivacyChipXOffset;
    private StatusIconPanelViewController mMicPanelController;
    private StatusIconPanelViewController mCameraPanelController;

    @AssistedInject
    public CarTopSystemBarViewController(Context context,
            UserTracker userTracker,
            CarSystemBarElementInitializer elementInitializer,
            SystemBarConfigs systemBarConfigs,
            ButtonRoleHolderController buttonRoleHolderController,
            ButtonSelectionStateController buttonSelectionStateController,
            Lazy<CameraPrivacyChipViewController> cameraPrivacyChipViewControllerLazy,
            Lazy<MicPrivacyChipViewController> micPrivacyChipViewControllerLazy,
            CarDeviceProvisionedController deviceProvisionedController,
            Provider<StatusIconPanelViewController.Builder> panelControllerBuilderProvider,
            @Assisted CarSystemBarView systemBarView) {
        super(context,
                userTracker,
                elementInitializer,
                systemBarConfigs,
                buttonRoleHolderController,
                buttonSelectionStateController,
                cameraPrivacyChipViewControllerLazy,
                micPrivacyChipViewControllerLazy,
                TOP,
                systemBarView);
        mCarDeviceProvisionedController = deviceProvisionedController;
        mPanelControllerBuilderProvider = panelControllerBuilderProvider;

        mPrivacyChipXOffset = -context.getResources()
                .getDimensionPixelOffset(R.dimen.privacy_chip_horizontal_padding);
    }

    @Override
    protected void onInit() {
        super.onInit();

        if (isDeviceSetupForUser()) {
            // We do not want the privacy chips or the profile picker to be clickable in
            // unprovisioned mode.
            mMicPanelController = setupSensorQcPanel(mMicPanelController, R.id.mic_privacy_chip,
                    R.layout.qc_mic_panel);
            mCameraPanelController = setupSensorQcPanel(mCameraPanelController,
                    R.id.camera_privacy_chip, R.layout.qc_camera_panel);
        }
    }

    private StatusIconPanelViewController setupSensorQcPanel(
            @Nullable StatusIconPanelViewController panelController, int chipId,
            @LayoutRes int panelLayoutRes) {
        if (panelController == null) {
            View privacyChip = mView.findViewById(chipId);
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

    private boolean isDeviceSetupForUser() {
        return mCarDeviceProvisionedController.isCurrentUserSetup()
                && !mCarDeviceProvisionedController.isCurrentUserSetupInProgress();
    }

    @AssistedFactory
    public interface Factory extends CarSystemBarViewController.Factory {
        @Override
        default CarSystemBarViewController create(@SystemBarSide int side, CarSystemBarView view) {
            if (side == TOP) {
                return create(view);
            }
            throw new UnsupportedOperationException("Side not supported");
        }

        /** Create instance of CarTopSystemBarViewController for CarSystemBarView */
        CarTopSystemBarViewController create(CarSystemBarView view);
    }
}
