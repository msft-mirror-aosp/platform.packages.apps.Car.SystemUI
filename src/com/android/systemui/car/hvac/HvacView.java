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


import android.car.hardware.CarPropertyConfig;
import android.car.hardware.CarPropertyValue;

/**
 *  Views implementing this interface can subscribe to HVAC Property by property ID and area ID and
 *  gains access to {@link HvacPropertySetter} with API's to write new values for HVAC Properties.
 */
public interface HvacView {
    /**
     * Grants HvacView access to {@link HvacPropertySetter}.
     */
    void setHvacPropertySetter(HvacPropertySetter hvacPropertySetter);

    /**
     * Called when the on/off state of the HVAC system is changed.
     */
    void onHvacOnOffChanged(boolean hvacIsOn);

    /**
     * Called when the temperature display unit (celsius or fahrenheit) is changed.
     */
    void onHvacTemperatureUnitChanged(boolean usesFahrenheit);

    /**
     * Called when the subscribed CarPropertyValue is changed.
     */
    void onPropertyChanged(CarPropertyValue value);

    /**
     *  Returns the HvacProperty ID to subscribe to.
     * @return the property ID to subscribe to.
     */
    @HvacController.HvacProperty Integer getHvacPropertyToView();

    /**
     * Returns the area ID to subscribe to.
     *
     * NOTE: Which area id's are supported by a certain property depends on the VHAL implementation.
     * For example, DefaultConfig.h file found in vhal_v2_0 documents the area id's associated with
     * each property. Calling {@link CarPropertyConfig#getAreaIds()} also returns an array of all
     * supported area id's for the given property.
     *
     * @return the area ID to subscribe to.
     */
    @HvacController.AreaId Integer getAreaId();
}