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

package com.android.systemui.car.qc;

import static com.android.car.datasubscription.Flags.FLAG_DATA_SUBSCRIPTION_POP_UP;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.mockitoSession;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.os.UserHandle;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;
import android.view.View;
import android.widget.PopupWindow;

import androidx.test.filters.SmallTest;

import com.android.car.datasubscription.DataSubscription;
import com.android.car.datasubscription.DataSubscriptionStatus;
import com.android.car.ui.utils.CarUxRestrictionsUtil;
import com.android.systemui.SysuiTestCase;
import com.android.systemui.car.CarSystemUiTest;
import com.android.systemui.settings.UserTracker;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

@CarSystemUiTest
@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
@SmallTest
public class DataSubscriptionControllerTest extends SysuiTestCase {
    @Mock
    private UserTracker mUserTracker;
    @Mock
    private DataSubscription mDataSubscription;
    @Mock
    private PopupWindow mPopupWindow;
    @Mock
    private View mAnchorView;
    private MockitoSession mMockingSession;
    private DataSubscriptionController mController;
    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Before
    public void setUp() {
        mMockingSession = mockitoSession()
                .initMocks(this)
                .mockStatic(CarUxRestrictionsUtil.class)
                .strictness(Strictness.WARN)
                .startMocking();

        mContext = spy(mContext);
        when(mUserTracker.getUserHandle()).thenReturn(UserHandle.of(1000));
        mController = new DataSubscriptionController(mContext, mUserTracker);
        mController.setSubscription(mDataSubscription);
        mController.setPopupWindow(mPopupWindow);
    }

    @After
    public void tearDown() {
        if (mMockingSession != null) {
            mMockingSession.finishMocking();
        }
    }

    @RequiresFlagsEnabled(FLAG_DATA_SUBSCRIPTION_POP_UP)
    @Test
    public void setAnchorView_viewNotNull_popUpDisplay() {
        when(mPopupWindow.isShowing()).thenReturn(false);
        mController.setSubscriptionStatus(DataSubscriptionStatus.INACTIVE);

        mController.setAnchorView(mAnchorView);

        verify(mDataSubscription).addDataSubscriptionListener(any());
        verify(mAnchorView).post(any());
    }

    @RequiresFlagsEnabled(FLAG_DATA_SUBSCRIPTION_POP_UP)
    @Test
    public void setAnchorView_viewNull_popUpNotDisplay() {
        when(mPopupWindow.isShowing()).thenReturn(false);
        mController.setSubscriptionStatus(DataSubscriptionStatus.INACTIVE);

        mController.setAnchorView(null);

        verify(mDataSubscription).removeDataSubscriptionListener();
        verify(mAnchorView, never()).post(any());
    }

    @RequiresFlagsEnabled(FLAG_DATA_SUBSCRIPTION_POP_UP)
    @Test
    public void dataSubscriptionChange_statusInactive_popUpDisplay() {
        DataSubscription.DataSubscriptionChangeListener listener =
                mController.getDataSubscriptionChangeListener();

        listener.onChange(DataSubscriptionStatus.PAID);
        listener.onChange(DataSubscriptionStatus.INACTIVE);

        Assert.assertTrue(mController.getShouldDisplayProactiveMsg());

    }

    @RequiresFlagsEnabled(FLAG_DATA_SUBSCRIPTION_POP_UP)
    @Test
    public void dataSubscriptionChange_statusPaid_popUpNotDisplay() {
        DataSubscription.DataSubscriptionChangeListener listener =
                mController.getDataSubscriptionChangeListener();

        listener.onChange(DataSubscriptionStatus.INACTIVE);
        listener.onChange(DataSubscriptionStatus.PAID);

        Assert.assertFalse(mController.getShouldDisplayProactiveMsg());
    }
}
