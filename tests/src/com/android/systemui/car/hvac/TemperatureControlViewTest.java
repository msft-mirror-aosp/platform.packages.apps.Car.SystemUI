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

package com.android.systemui.car.hvac;

import static android.car.VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL;
import static android.car.VehiclePropertyIds.HVAC_TEMPERATURE_DISPLAY_UNITS;
import static android.car.VehiclePropertyIds.HVAC_TEMPERATURE_SET;
import static android.car.VehicleUnit.CELSIUS;
import static android.car.VehicleUnit.FAHRENHEIT;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.when;

import android.car.hardware.CarPropertyValue;
import android.test.suitebuilder.annotation.SmallTest;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;
import android.view.LayoutInflater;

import com.android.systemui.SysuiTestCase;
import com.android.systemui.car.CarSystemUiTest;
import com.android.systemui.tests.R;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@CarSystemUiTest
@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
@SmallTest
public class TemperatureControlViewTest extends SysuiTestCase {
    private static final int AREA_ID = 99;

    private TemperatureControlView mTemperatureControlView;
    @Mock
    private CarPropertyValue mCarPropertyValue;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mTemperatureControlView = (TemperatureControlView) LayoutInflater.from(
                getContext()).inflate(R.layout.temperature_control_view, /* root= */
                null).findViewById(R.id.test_hvac);
    }

    @Test
    public void onPropertyChanged_inCelsius_displaysSetTemperatureInCelsius()
            throws InterruptedException {
        when(mCarPropertyValue.getAreaId()).thenReturn(AREA_ID);
        when(mCarPropertyValue.getPropertyId()).thenReturn(HVAC_TEMPERATURE_SET);
        when(mCarPropertyValue.getValue()).thenReturn(1f);

        mTemperatureControlView.onPropertyChanged(mCarPropertyValue);
        mTemperatureControlView.onHvacTemperatureUnitChanged(/* usesFahrenheit= */ false);

        assertThat(mTemperatureControlView.getTempInDisplay()).isEqualTo(
                String.format(mTemperatureControlView.getTempFormat(), 1f));
    }

    @Test
    public void onPropertyChanged_inFahrenheit_displaysSetTemperatureInFahrenheit() {
        when(mCarPropertyValue.getAreaId()).thenReturn(AREA_ID);
        when(mCarPropertyValue.getPropertyId()).thenReturn(HVAC_TEMPERATURE_SET);
        when(mCarPropertyValue.getValue()).thenReturn(1f);

        mTemperatureControlView.onPropertyChanged(mCarPropertyValue);
        mTemperatureControlView.onHvacTemperatureUnitChanged(/* usesFahrenheit= */ true);

        assertThat(mTemperatureControlView.getTempInDisplay()).isEqualTo(
                String.format(mTemperatureControlView.getTempFormat(),
                        HvacUtils.convertToFahrenheit(1f)));
    }
}
