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

import static com.android.car.datasubscription.Flags.FLAG_DATA_SUBSCRIPTION_POP_UP;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;

import androidx.test.filters.SmallTest;

import com.android.car.datasubscription.DataSubscription;
import com.android.car.datasubscription.DataSubscriptionStatus;
import com.android.systemui.SysuiTestCase;
import com.android.systemui.car.CarSystemUiTest;
import com.android.systemui.car.systembar.element.CarSystemBarElementStateController;
import com.android.systemui.car.systembar.element.CarSystemBarElementStatusBarDisableController;
import com.android.systemui.car.systembar.element.layout.CarSystemBarImageView;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@CarSystemUiTest
@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
@SmallTest
public class DataSubscriptionUnseenIconControllerTest extends SysuiTestCase {
    private DataSubscriptionUnseenIconController mController;
    @Mock
    private CarSystemBarImageView mView;
    @Mock
    private CarSystemBarElementStatusBarDisableController mDisableController;
    @Mock
    private CarSystemBarElementStateController mStateController;
    @Mock
    private DataSubscription mDataSubscription;
    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mController = new DataSubscriptionUnseenIconController(mView,
                mDisableController, mStateController);
        mController.setSubscription(mDataSubscription);
    }

    @RequiresFlagsEnabled(FLAG_DATA_SUBSCRIPTION_POP_UP)
    @Test
    public void onViewAttached_registerListener() {
        when(mDataSubscription.getDataSubscriptionStatus()).thenReturn(
                DataSubscriptionStatus.INACTIVE);

        mController.onViewAttached();

        verify(mDataSubscription).addDataSubscriptionListener(any());
    }

    @RequiresFlagsEnabled(FLAG_DATA_SUBSCRIPTION_POP_UP)
    @Test
    public void onViewDetached_UnregisterListener() {
        when(mDataSubscription.getDataSubscriptionStatus()).thenReturn(
                DataSubscriptionStatus.INACTIVE);

        mController.onViewDetached();

        verify(mDataSubscription).removeDataSubscriptionListener();
    }

    @RequiresFlagsEnabled(FLAG_DATA_SUBSCRIPTION_POP_UP)
    @Test
    public void dataSubscriptionChange_statusInactive_viewVisible() {
        DataSubscription.DataSubscriptionChangeListener listener =
                mController.getDataSubscriptionChangeListener();

        listener.onChange(DataSubscriptionStatus.INACTIVE);

        Assert.assertEquals(DataSubscriptionStatus.INACTIVE,
                mController.getSubscriptionStatus());

    }

    @RequiresFlagsEnabled(FLAG_DATA_SUBSCRIPTION_POP_UP)
    @Test
    public void dataSubscriptionChange_statusPaid_viewGone() {
        DataSubscription.DataSubscriptionChangeListener listener =
                mController.getDataSubscriptionChangeListener();

        listener.onChange(DataSubscriptionStatus.PAID);

        verify(mView).post(any());
        Assert.assertEquals(DataSubscriptionStatus.PAID,
                mController.getSubscriptionStatus());
    }
}
