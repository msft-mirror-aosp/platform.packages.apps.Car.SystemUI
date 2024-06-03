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

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import android.content.Context;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;
import android.view.View;

import androidx.test.filters.SmallTest;

import com.android.car.datasubscription.DataSubscription;
import com.android.car.datasubscription.DataSubscriptionStatus;
import com.android.systemui.SysuiTestCase;
import com.android.systemui.car.CarSystemUiTest;

import org.junit.Assert;
import org.junit.Before;
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
    private View mView;

    @Mock
    private DataSubscription mDataSubscription;
    @Mock
    private Context mContext;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mController = new DataSubscriptionUnseenIconController(mContext);
        mController.setSubscription(mDataSubscription);
    }

    @Test
    public void setUnseenIcon_IconNull_NotRegisterListener() {
        DataSubscription.DataSubscriptionChangeListener listener =
                mController.getDataSubscriptionChangeListener();

        mController.setUnseenIcon(null);

        verify(mDataSubscription, never()).addDataSubscriptionListener(listener);
    }

    @Test
    public void setUnseenIcon_IconNotNull_RegisterListener() {
        DataSubscription.DataSubscriptionChangeListener listener =
                mController.getDataSubscriptionChangeListener();

        mController.setUnseenIcon(mView);

        verify(mDataSubscription).addDataSubscriptionListener(listener);
    }

    @Test
    public void dataSubscriptionChange_statusInactive_viewVisible() {
        DataSubscription.DataSubscriptionChangeListener listener =
                mController.getDataSubscriptionChangeListener();

        mController.setUnseenIcon(mView);
        listener.onChange(DataSubscriptionStatus.INACTIVE);

        Assert.assertEquals(DataSubscriptionStatus.INACTIVE,
                mController.getSubscriptionStatus());

    }

    @Test
    public void dataSubscriptionChange_statusPaid_viewGone() {
        DataSubscription.DataSubscriptionChangeListener listener =
                mController.getDataSubscriptionChangeListener();

        mController.setUnseenIcon(mView);
        listener.onChange(DataSubscriptionStatus.PAID);

        Assert.assertEquals(DataSubscriptionStatus.PAID,
                mController.getSubscriptionStatus());
    }
}
