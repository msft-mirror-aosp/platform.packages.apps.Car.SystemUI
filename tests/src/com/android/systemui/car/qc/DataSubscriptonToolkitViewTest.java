/*
 * Copyright (C) 2025 The Android Open Source Project
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

import static com.google.common.truth.Truth.assertThat;

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

import com.android.car.datasubscription.DataSubscriptionViewActionListener;
import com.android.systemui.SysuiTestCase;
import com.android.systemui.car.CarSystemUiTest;
import com.android.systemui.settings.UserTracker;

import junit.framework.Assert;

import org.junit.After;
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
public class DataSubscriptonToolkitViewTest extends SysuiTestCase {
    @Mock
    private PopupWindow mPopupWindow;
    @Mock
    private View mAnchorView;
    @Mock
    private UserTracker mUserTracker;
    @Mock
    private DataSubscriptionViewActionListener mDataSubscriptionViewActionListener;

    private DataSubscriptionToolkitView mDataSubscriptionToolkitView;

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        when(mUserTracker.getUserHandle()).thenReturn(UserHandle.of(1000));
        mDataSubscriptionToolkitView = new DataSubscriptionToolkitView(mContext, mUserTracker);
        mDataSubscriptionToolkitView.setDataSubscriptionViewActionListener(
                mDataSubscriptionViewActionListener);
        mDataSubscriptionToolkitView.setPopupWindow(mPopupWindow);
    }

    @After()
    public void tearDown() {
        mDataSubscriptionToolkitView.getPopUpPrompt().setText("");
    }

    @RequiresFlagsEnabled(FLAG_DATA_SUBSCRIPTION_POP_UP)
    @Test
    public void setAnchorView_viewNull_notRegisterListeners() {
        mDataSubscriptionToolkitView.setAnchorView(null);

        verify(mDataSubscriptionViewActionListener).unregisterListeners();
    }

    @RequiresFlagsEnabled(FLAG_DATA_SUBSCRIPTION_POP_UP)
    @Test
    public void setAnchorView_viewNotNull_registerListeners() {
        mDataSubscriptionToolkitView.setAnchorView(mAnchorView);

        verify(mDataSubscriptionViewActionListener).registerListeners();
    }

    @RequiresFlagsEnabled(FLAG_DATA_SUBSCRIPTION_POP_UP)
    @Test
    public void onDataSubscriptionStatusChanged_shouldNotDisplayProactiveMsg_popUpNotDisplay() {
        when(mPopupWindow.isShowing()).thenReturn(true);

        mDataSubscriptionToolkitView.onDataSubscriptionStatusChanged(false, false);

        assertThat(mDataSubscriptionToolkitView.getPopUpPrompt().getText().isEmpty())
                .isTrue();
    }

    @RequiresFlagsEnabled(FLAG_DATA_SUBSCRIPTION_POP_UP)
    @Test
    public void onDataSubscriptionStatusChanged_shouldDisplayProactiveMsg_popUpDisplay() {
        when(mPopupWindow.isShowing()).thenReturn(true);

        mDataSubscriptionToolkitView.onDataSubscriptionStatusChanged(false, true);

        Assert.assertNotNull(mDataSubscriptionToolkitView.getPopUpPrompt().getText());
    }

    @RequiresFlagsEnabled(FLAG_DATA_SUBSCRIPTION_POP_UP)
    @Test
    public void onAppForeground_shouldNotDisplayReactiveMsg_popUpNotDisplay() {
        when(mPopupWindow.isShowing()).thenReturn(true);

        mDataSubscriptionToolkitView.onAppForeground(false, true,
                "Test App Label");

        assertThat(mDataSubscriptionToolkitView.getPopUpPrompt().getText().isEmpty())
                .isTrue();
    }

    @RequiresFlagsEnabled(FLAG_DATA_SUBSCRIPTION_POP_UP)
    @Test
    public void onAppForeground_shouldDisplayReactiveMsg_popUpDisplay() {
        when(mPopupWindow.isShowing()).thenReturn(true);

        mDataSubscriptionToolkitView.onAppForeground(false, true,
                "Test App Label");

        Assert.assertNotNull(mDataSubscriptionToolkitView.getPopUpPrompt().getText());
    }

    @RequiresFlagsEnabled(FLAG_DATA_SUBSCRIPTION_POP_UP)
    @Test
    public void onUxrChange_UxrRequired_proactivePopUpDimissed() {
        mDataSubscriptionToolkitView.setIsProactiveMsg(true);
        when(mPopupWindow.isShowing()).thenReturn(true);

        mDataSubscriptionToolkitView.onUxrChange(true);

        verify(mPopupWindow).dismiss();
    }

    @RequiresFlagsEnabled(FLAG_DATA_SUBSCRIPTION_POP_UP)
    @Test
    public void onUxrChange_UxrRequired_reactivePopUpButtonGone() {
        mDataSubscriptionToolkitView.setIsProactiveMsg(false);
        when(mPopupWindow.isShowing()).thenReturn(true);

        mDataSubscriptionToolkitView.onUxrChange(true);

        assertThat(mDataSubscriptionToolkitView.getExplorationButton().getVisibility())
                .isEqualTo(View.GONE);

    }
}
