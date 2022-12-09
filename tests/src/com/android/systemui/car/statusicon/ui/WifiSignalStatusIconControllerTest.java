/*
 * Copyright (C) 2023 The Android Open Source Project
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

import android.content.res.Resources;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;

import androidx.test.filters.SmallTest;

import com.android.systemui.R;
import com.android.systemui.SysuiTestCase;
import com.android.systemui.car.CarSystemUiTest;
import com.android.systemui.statusbar.connectivity.IconState;
import com.android.systemui.statusbar.connectivity.NetworkController;
import com.android.systemui.statusbar.connectivity.WifiIndicators;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@CarSystemUiTest
@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper(setAsMainLooper = true)
@SmallTest
public class WifiSignalStatusIconControllerTest extends SysuiTestCase {
    @Mock
    Resources mResources;
    @Mock
    NetworkController mNetworkController;

    private WifiSignalStatusIconController mWifiSignalStatusIconController;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        mWifiSignalStatusIconController = new WifiSignalStatusIconController(mContext, mResources,
                mNetworkController);
    }

    @Test
    public void onUpdateStatus_showsWifiIcon() {
        mWifiSignalStatusIconController.setWifiIndicators(getWifiIndicator());

        assertThat(mWifiSignalStatusIconController.getIconDrawableToDisplay()).isEqualTo(
                mWifiSignalStatusIconController.getWifiSignalIconDrawable());
    }

    private WifiIndicators getWifiIndicator() {
        IconState iconState = new IconState(/* visible= */ true, R.drawable.icon,
                /* contentDescription= */ "");
        return new WifiIndicators(/* enabled= */ true, iconState, /* qsIcon= */ null,
                /* activityIn= */ false, /* activityOut= */ false, /* qsDescription=" */ "",
                /* isTransient= */ false, /* statusLabel= */ "");
    }
}
