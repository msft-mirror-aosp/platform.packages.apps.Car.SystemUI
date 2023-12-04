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

package com.android.systemui.car.hvac;

/**
 *  Utility class for HVAC-related use cases.
 */
public final class HvacUtils {
    /**
     * @see #shouldAllowControl(boolean, boolean, boolean, boolean)
     */
    public static boolean shouldAllowControl(boolean disableViewIfPowerOff, boolean powerOn) {
        return shouldAllowControl(disableViewIfPowerOff, powerOn, /* disableViewIfAutoOn= */false,
                /* autoOn= */false);
    }

    /**
     * @see #shouldAllowControl(boolean, boolean, boolean, boolean)
     */
    public static boolean shouldAllowControl(boolean disableViewIfPowerOff, boolean powerOn,
            boolean autoOn) {
        return shouldAllowControl(disableViewIfPowerOff, powerOn, /* disableViewIfAutoOn= */true,
                autoOn);
    }

    /**
     * Returns whether the view can be controlled.
     *
     * @param disableViewIfPowerOff whether the view can be controlled when hvac power is off
     * @param powerOn is hvac power on
     * @param disableViewIfAutoOn whether the view can be controlled when hvac auto mode is on
     * @param autoOn is auto mode on
     * @return is the view controllable
     */
    public static boolean shouldAllowControl(boolean disableViewIfPowerOff, boolean powerOn,
            boolean disableViewIfAutoOn, boolean autoOn) {
        return (!disableViewIfPowerOff || powerOn) && (!disableViewIfAutoOn || !autoOn);
    }
}
