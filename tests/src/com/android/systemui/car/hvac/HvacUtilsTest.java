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

import static com.google.common.truth.Truth.assertThat;

import android.test.suitebuilder.annotation.SmallTest;

import com.android.systemui.SysuiTestCase;
import com.android.systemui.car.CarSystemUiTest;

import org.junit.Test;

@CarSystemUiTest
@SmallTest
public class HvacUtilsTest extends SysuiTestCase {
    @Test
    public void shouldAllowControl_powerNotNeeded_powerOff_autoNotNeeded_autoOff() {
        assertThat(HvacUtils.shouldAllowControl(/* disableViewIfPowerOff= */ false,
                /* powerOn=*/ false, /* disableViewIfAutoOn= */ false,
                /* autoOn= */ false)).isTrue();
    }

    @Test
    public void shouldAllowControl_powerNotNeeded_powerOff_autoNotNeeded_autoOn() {
        assertThat(HvacUtils.shouldAllowControl(/* disableViewIfPowerOff= */ false,
                /* powerOn=*/ false, /* disableViewIfAutoOn= */ false,
                /* autoOn= */ true)).isTrue();
    }

    @Test
    public void shouldAllowControl_powerNotNeeded_powerOff_autoNeeded_autoOn() {
        assertThat(HvacUtils.shouldAllowControl(/* disableViewIfPowerOff= */ false,
                /* powerOn=*/ false, /* disableViewIfAutoOn= */ true,
                /* autoOn= */ true)).isFalse();
    }

    @Test
    public void shouldAllowControl_powerNotNeeded_powerOff_autoNeeded_autoOff() {
        assertThat(HvacUtils.shouldAllowControl(/* disableViewIfPowerOff= */ false,
                /* powerOn=*/ false, /* disableViewIfAutoOn= */ true,
                /* autoOn= */ false)).isTrue();
    }

    @Test
    public void shouldAllowControl_powerNotNeeded_powerOn_autoNeeded_autoOn() {
        assertThat(HvacUtils.shouldAllowControl(/* disableViewIfPowerOff= */ false,
                /* powerOn=*/ true, /* disableViewIfAutoOn= */ true,
                /* autoOn= */ true)).isFalse();
    }

    @Test
    public void shouldAllowControl_powerNotNeeded_powerOn_autoNeeded_autoOff() {
        assertThat(HvacUtils.shouldAllowControl(/* disableViewIfPowerOff= */ false,
                /* powerOn=*/ true, /* disableViewIfAutoOn= */ true,
                /* autoOn= */ false)).isTrue();
    }

    @Test
    public void shouldAllowControl_powerNotNeeded_powerOn_autoNotNeeded_autoOn() {
        assertThat(HvacUtils.shouldAllowControl(/* disableViewIfPowerOff= */ false,
                /* powerOn=*/ true, /* disableViewIfAutoOn= */ false,
                /* autoOn= */ true)).isTrue();
    }

    @Test
    public void shouldAllowControl_powerNotNeeded_powerOn_autoNotNeeded_autoOff() {
        assertThat(HvacUtils.shouldAllowControl(/* disableViewIfPowerOff= */ false,
                /* powerOn=*/ true, /* disableViewIfAutoOn= */ false,
                /* autoOn= */ false)).isTrue();
    }

    @Test
    public void shouldAllowControl_powerNeeded_powerOn_autoNeeded_autoOn() {
        assertThat(HvacUtils.shouldAllowControl(/* disableViewIfPowerOff= */ true,
                /* powerOn=*/ true, /* disableViewIfAutoOn= */ true,
                /* autoOn= */ true)).isFalse();
    }

    @Test
    public void shouldAllowControl_powerNeeded_powerOn_autoNeeded_autoOff() {
        assertThat(HvacUtils.shouldAllowControl(/* disableViewIfPowerOff= */ true,
                /* powerOn=*/ true, /* disableViewIfAutoOn= */ true,
                /* autoOn= */ false)).isTrue();
    }

    @Test
    public void shouldAllowControl_powerNeeded_powerOn_autoNotNeeded_autoOn() {
        assertThat(HvacUtils.shouldAllowControl(/* disableViewIfPowerOff= */ true,
                /* powerOn=*/ true, /* disableViewIfAutoOn= */ false,
                /* autoOn= */ true)).isTrue();
    }

    @Test
    public void shouldAllowControl_powerNeeded_powerOn_autoNotNeeded_autoOff() {
        assertThat(HvacUtils.shouldAllowControl(/* disableViewIfPowerOff= */ true,
                /* powerOn=*/ true, /* disableViewIfAutoOn= */ false,
                /* autoOn= */ false)).isTrue();
    }

    @Test
    public void shouldAllowControl_powerNeeded_powerOff_autoNotNeeded_autoOff() {
        assertThat(HvacUtils.shouldAllowControl(/* disableViewIfPowerOff= */ true,
                /* powerOn=*/ false, /* disableViewIfAutoOn= */ false,
                /* autoOn= */ false)).isFalse();
    }

    @Test
    public void shouldAllowControl_powerNeeded_powerOff_autoNotNeeded_autoOn() {
        assertThat(HvacUtils.shouldAllowControl(/* disableViewIfPowerOff= */ true,
                /* powerOn=*/ false, /* disableViewIfAutoOn= */ false,
                /* autoOn= */ true)).isFalse();
    }

    @Test
    public void shouldAllowControl_powerNeeded_powerOff_autoNeeded_autoOn() {
        assertThat(HvacUtils.shouldAllowControl(/* disableViewIfPowerOff= */ true,
                /* powerOn=*/ false, /* disableViewIfAutoOn= */ true,
                /* autoOn= */ true)).isFalse();
    }

    @Test
    public void shouldAllowControl_powerNeeded_powerOff_autoNeeded_autoOff() {
        assertThat(HvacUtils.shouldAllowControl(/* disableViewIfPowerOff= */ true,
                /* powerOn=*/ false, /* disableViewIfAutoOn= */ true,
                /* autoOn= */ false)).isFalse();
    }
}
