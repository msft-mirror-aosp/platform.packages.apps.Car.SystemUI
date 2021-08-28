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

import static com.google.common.truth.Truth.assertThat;

import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;

import androidx.test.filters.SmallTest;

import com.android.systemui.R;
import com.android.systemui.SysuiTestCase;
import com.android.systemui.car.CarSystemUiTest;
import com.android.systemui.statusbar.policy.HotspotController;
import com.android.systemui.statusbar.policy.NetworkController;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@CarSystemUiTest
@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper(setAsMainLooper = true)
@SmallTest
public class SignalStatusIconControllerTest extends SysuiTestCase {

    private SignalStatusIconController mSignalStatusIconController;

    @Mock
    NetworkController mNetworkController;
    @Mock
    HotspotController mHotspotController;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        mSignalStatusIconController = new SignalStatusIconController(mContext, mNetworkController,
                mHotspotController);
    }

    @Test
    public void onUpdateStatus_wifiDisabled_hotspotDisabled_showsMobileDataIcon() {
        mSignalStatusIconController.setWifiIndicators(getWifiIndicator(/* enabled= */ false));
        mSignalStatusIconController.onHotspotChanged(/* enabled= */ false, /* numDevices= */  0);

        // onUpdateStatus is called by the events above.

        assertThat(mSignalStatusIconController.getIconDrawableToDisplay()).isEqualTo(
                mSignalStatusIconController.getMobileSignalIconDrawable());
    }

    @Test
    public void onUpdateStatus_wifiEnabled_hotspotDisabled_showsWifiIcon() {
        mSignalStatusIconController.setWifiIndicators(getWifiIndicator(/* enabled= */ true));
        mSignalStatusIconController.onHotspotChanged(/* enabled= */ false, /* numDevices= */  0);

        // onUpdateStatus is called by the events above.

        assertThat(mSignalStatusIconController.getIconDrawableToDisplay()).isEqualTo(
                mSignalStatusIconController.getWifiSignalIconDrawable());
    }

    @Test
    public void onUpdateStatus_wifiDisabled_hotspotEnabled_showsHotspotIcon() {
        mSignalStatusIconController.setWifiIndicators(
                getWifiIndicator(/* enabled= */ false));
        mSignalStatusIconController.onHotspotChanged(/* enabled= */ true, /* numDevices= */  0);

        // onUpdateStatus is called by the events above.

        assertThat(mSignalStatusIconController.getIconDrawableToDisplay()).isEqualTo(
                mSignalStatusIconController.getHotSpotIconDrawable());
    }

    @Test
    public void onUpdateStatus_wifiEnabled_hotspotEnabled_showsHotspotIcon() {
        mSignalStatusIconController.setWifiIndicators(
                getWifiIndicator(/* enabled= */ true));
        mSignalStatusIconController.onHotspotChanged(/* enabled= */ true, /* numDevices= */  0);

        // onUpdateStatus is called by the events above.

        assertThat(mSignalStatusIconController.getIconDrawableToDisplay()).isEqualTo(
                mSignalStatusIconController.getHotSpotIconDrawable());
    }

    private NetworkController.WifiIndicators getWifiIndicator(boolean enabled) {
        NetworkController.IconState iconState =
                new NetworkController.IconState(true, R.drawable.icon, "");

        return new NetworkController.WifiIndicators(enabled, iconState, null, false, false, "",
                false, "");
    }
}
