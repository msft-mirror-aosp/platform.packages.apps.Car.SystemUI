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

import static android.car.VehiclePropertyIds.HVAC_AUTO_ON;
import static android.car.VehiclePropertyIds.HVAC_POWER_ON;
import static android.car.VehiclePropertyIds.HVAC_TEMPERATURE_SET;
import static android.view.MotionEvent.ACTION_DOWN;
import static android.view.MotionEvent.ACTION_UP;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.car.hardware.CarPropertyValue;
import android.os.SystemClock;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;

import androidx.test.filters.SmallTest;

import com.android.systemui.SysuiTestCase;
import com.android.systemui.car.CarSystemUiTest;
import com.android.systemui.tests.R;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@CarSystemUiTest
@SmallTest
@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
public class TemperatureControlViewTest extends SysuiTestCase {
    private static final int GLOBAL_AREA_ID = 117;
    private static final int AREA_ID = 99;
    private static final int PROPERTY_ID = HVAC_TEMPERATURE_SET;

    private TemperatureControlView mTemperatureControlView;
    @Mock
    private HvacPropertySetter mHvacPropertySetter;
    @Mock
    private CarPropertyValue mCarPropertyValue;
    @Mock
    private CarPropertyValue mHvacPowerProperty;
    @Mock
    private MotionEvent mDownMotionEvent;
    @Mock
    private MotionEvent mUpMotionEvent;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mTemperatureControlView = LayoutInflater.from(getContext()).inflate(
                R.layout.temperature_control_view, /* root= */ null).findViewById(R.id.test_hvac);
        mTemperatureControlView.setHvacPropertySetter(mHvacPropertySetter);
        mTemperatureControlView.onFinishInflate();

        when(mCarPropertyValue.getAreaId()).thenReturn(AREA_ID);
        when(mCarPropertyValue.getPropertyId()).thenReturn(HVAC_TEMPERATURE_SET);
        when(mCarPropertyValue.getStatus()).thenReturn(CarPropertyValue.STATUS_AVAILABLE);
        when(mCarPropertyValue.getValue()).thenReturn(20f);

        when(mDownMotionEvent.getAction()).thenReturn(MotionEvent.ACTION_DOWN);
        when(mUpMotionEvent.getAction()).thenReturn(ACTION_UP);
    }

    @Test
    public void onPropertyChanged_inCelsius_displaysSetTemperatureInCelsius()
            throws InterruptedException {
        setPowerPropertyValue(true);
        mTemperatureControlView.onPropertyChanged(mHvacPowerProperty);
        mTemperatureControlView.onPropertyChanged(mCarPropertyValue);
        mTemperatureControlView.onHvacTemperatureUnitChanged(/* usesFahrenheit= */ false);

        assertThat(mTemperatureControlView.getTempInDisplay()).isEqualTo(
                String.format(mTemperatureControlView.getTempFormat(), 20f));
    }

    @Test
    public void onPropertyChanged_inFahrenheit_displaysSetTemperatureInFahrenheit() {
        setPowerPropertyValue(true);
        mTemperatureControlView.onPropertyChanged(mHvacPowerProperty);
        mTemperatureControlView.onPropertyChanged(mCarPropertyValue);
        mTemperatureControlView.onHvacTemperatureUnitChanged(/* usesFahrenheit= */ true);

        assertThat(mTemperatureControlView.getTempInDisplay()).isEqualTo(
                String.format(mTemperatureControlView.getTempFormat(),
                        celsiusToFahrenheit(20f)));
    }

    @Test
    public void onTouchIncreaseButtonThreeTimes_inCelsius_increasesThreeDegreesCelsius() {
        setPowerPropertyValue(true);
        mTemperatureControlView.onPropertyChanged(mHvacPowerProperty);
        View increaseButton = mTemperatureControlView.findViewById(R.id.hvac_increase_button);
        int intervalTimes = 3;
        mTemperatureControlView.onPropertyChanged(mCarPropertyValue);
        mTemperatureControlView.onHvacTemperatureUnitChanged(/* usesFahrenheit= */ false);

        // Subtract 10ms so the click will be performed exactly intervalTimes.
        // The first click occurs when the button is pressed, and so after interval * n
        // milliseconds, n + 1 clicks happen in total.
        touchViewForDurationMs(increaseButton,
                TemperatureControlView.BUTTON_REPEAT_INTERVAL_MS * intervalTimes - 10);

        verify(mHvacPropertySetter, times(intervalTimes)).setHvacProperty(HVAC_TEMPERATURE_SET,
                AREA_ID, (float) mCarPropertyValue.getValue() + 1f);
    }

    @Test
    public void onTouchDecreaseButtonThreeTimes_inCelsius_decreasesThreeDegreesCelsius() {
        setPowerPropertyValue(true);
        mTemperatureControlView.onPropertyChanged(mHvacPowerProperty);
        View decreaseButton = mTemperatureControlView.findViewById(R.id.hvac_decrease_button);
        int intervalTimes = 3;
        mTemperatureControlView.onPropertyChanged(mCarPropertyValue);
        mTemperatureControlView.onHvacTemperatureUnitChanged(/* usesFahrenheit= */ false);

        // Subtract 10ms so the click will be performed exactly intervalTimes.
        // The first click occurs when the button is pressed, and so after interval * n
        // milliseconds, n + 1 clicks happen in total.
        touchViewForDurationMs(decreaseButton,
                TemperatureControlView.BUTTON_REPEAT_INTERVAL_MS * intervalTimes - 10);

        verify(mHvacPropertySetter, times(intervalTimes)).setHvacProperty(HVAC_TEMPERATURE_SET,
                AREA_ID, (float) mCarPropertyValue.getValue() - 1f);
    }

    @Test
    public void onTouchIncreaseButtonThreeTimes_inFahrenheit_increasesThreeDegreesFahrenheit() {
        setPowerPropertyValue(true);
        mTemperatureControlView.onPropertyChanged(mHvacPowerProperty);
        View increaseButton = mTemperatureControlView.findViewById(R.id.hvac_increase_button);
        int intervalTimes = 3;
        mTemperatureControlView.onPropertyChanged(mCarPropertyValue);
        mTemperatureControlView.onHvacTemperatureUnitChanged(/* usesFahrenheit= */ true);

        // Subtract 10ms so the click will be performed exactly intervalTimes.
        // The first click occurs when the button is pressed, and so after interval * n
        // milliseconds, n + 1 clicks happen in total.
        touchViewForDurationMs(increaseButton,
                TemperatureControlView.BUTTON_REPEAT_INTERVAL_MS * intervalTimes - 10);

        verify(mHvacPropertySetter, times(intervalTimes)).setHvacProperty(HVAC_TEMPERATURE_SET,
                AREA_ID,
                fahrenheitToCelsius(celsiusToFahrenheit((float) mCarPropertyValue.getValue()) + 1));
    }

    @Test
    public void onTouchDecreaseButtonThreeTimes_inFahrenheit_decreasesThreeDegreesFahrenheit() {
        setPowerPropertyValue(true);
        mTemperatureControlView.onPropertyChanged(mHvacPowerProperty);
        View decreaseButton = mTemperatureControlView.findViewById(R.id.hvac_decrease_button);
        int intervalTimes = 3;
        mTemperatureControlView.onPropertyChanged(mCarPropertyValue);
        mTemperatureControlView.onHvacTemperatureUnitChanged(/* usesFahrenheit= */ true);

        // Subtract 10ms so the click will be performed exactly intervalTimes.
        // The first click occurs when the button is pressed, and so after interval * n
        // milliseconds, n + 1 clicks happen in total.
        touchViewForDurationMs(decreaseButton,
                TemperatureControlView.BUTTON_REPEAT_INTERVAL_MS * intervalTimes - 10);

        verify(mHvacPropertySetter, times(intervalTimes)).setHvacProperty(HVAC_TEMPERATURE_SET,
                AREA_ID,
                fahrenheitToCelsius(celsiusToFahrenheit((float) mCarPropertyValue.getValue()) - 1));
    }

    @Test
    public void onTouchIncreaseButtonThreeTimes_powerOff_doesNotSetNewValues() {
        setPowerPropertyValue(false);
        mTemperatureControlView.onPropertyChanged(mHvacPowerProperty);
        View increaseButton = mTemperatureControlView.findViewById(R.id.hvac_increase_button);
        int intervalTimes = 3;

        // Subtract 10ms so the click will be performed exactly intervalTimes.
        // The first click occurs when the button is pressed, and so after interval * n
        // milliseconds, n + 1 clicks happen in total.
        touchViewForDurationMs(increaseButton,
                TemperatureControlView.BUTTON_REPEAT_INTERVAL_MS * intervalTimes - 10);

        verify(mHvacPropertySetter, never()).setHvacProperty(anyInt(), anyInt(), anyFloat());
    }

    @Test
    public void onTouchDecreaseButtonThreeTimes_powerOff_doesNotSetNewValues() {
        setPowerPropertyValue(false);
        mTemperatureControlView.onPropertyChanged(mHvacPowerProperty);
        View decreaseButton = mTemperatureControlView.findViewById(R.id.hvac_decrease_button);
        int intervalTimes = 3;

        // Subtract 10ms so the click will be performed exactly intervalTimes.
        // The first click occurs when the button is pressed, and so after interval * n
        // milliseconds, n + 1 clicks happen in total.
        touchViewForDurationMs(decreaseButton,
                TemperatureControlView.BUTTON_REPEAT_INTERVAL_MS * intervalTimes - 10);

        verify(mHvacPropertySetter, never()).setHvacProperty(anyInt(), anyInt(), anyFloat());
    }

    private void touchViewForDurationMs(View view, long duration) {
        view.dispatchTouchEvent(createMotionEvent(ACTION_DOWN));
        try {
            Thread.sleep(duration);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        view.dispatchTouchEvent(createMotionEvent(ACTION_UP));
        waitForIdleSync();
    }

    private MotionEvent createMotionEvent(int action) {
        long eventTime = SystemClock.uptimeMillis();
        return MotionEvent.obtain(0, eventTime, action, 0, 0, 0);
    }

    // This test suite does not use HvacUtils to not assume HvacUtils is implemented correctly.
    private static float celsiusToFahrenheit(float tempC) {
        return (tempC * 9f / 5f) + 32;
    }
    private static float fahrenheitToCelsius(float tempF) {
        return (tempF - 32) * 5f / 9f;
    }

    private void setPowerPropertyValue(boolean value) {
        when(mHvacPowerProperty.getAreaId()).thenReturn(GLOBAL_AREA_ID);
        when(mHvacPowerProperty.getPropertyId()).thenReturn(HVAC_POWER_ON);
        when(mHvacPowerProperty.getValue()).thenReturn(value);
    }
}
