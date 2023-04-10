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
package com.android.systemui.car.userpicker;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertNotNull;

import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;

import com.android.systemui.R;
import com.android.systemui.car.CarSystemUiTest;
import com.android.systemui.dump.DumpManager;
import com.android.systemui.settings.DisplayTracker;
import com.android.systemui.statusbar.policy.Clock;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@CarSystemUiTest
@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
public class UserPickerBottomBarTest extends UserPickerTestCase {
    private static final String TAG = UserPickerBottomBarTest.class.getSimpleName();

    private UserPickerTestActivity mUserPickerActivity;

    @Mock
    private UserPickerController mMockUserPickerController;
    @Mock
    private UserPickerAdapter mMockUserPickerAdapter;
    @Mock
    private DialogManager mMockDialogManager;
    @Mock
    private SnackbarManager mMockSnackbarManager;
    @Mock
    private DisplayTracker mMockDisplayTracker;
    @Mock
    private DumpManager mMockDumpManager;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        mUserPickerActivity = new UserPickerTestActivity(mContext,
                mMockUserPickerController, mMockUserPickerAdapter, mMockDialogManager,
                mMockSnackbarManager, mMockDisplayTracker, mMockDumpManager);
        mUserPickerActivity.init();
    }

    @Test
    public void checkBottomBarHeight_validDimension() {
        doReturn(FRONT_PASSENGER_DISPLAY_ID).when(mContext).getDisplayId();

        LinearLayout bottombar = (LinearLayout) mUserPickerActivity.mRootView
                .findViewById(R.id.user_picker_bottom_bar);
        float height = bottombar.getLayoutParams().height;
        float target_height = mContext.getResources()
                .getDimension(R.dimen.car_bottom_system_bar_height);

        assertThat(height).isEqualTo(target_height);
    }

    @Test
    public void checkClockVisibility_isClockVisible() {
        doReturn(FRONT_PASSENGER_DISPLAY_ID).when(mContext).getDisplayId();

        Clock clock = (Clock) mUserPickerActivity.mRootView
                .findViewById(R.id.user_picker_bottom_bar_clock);
        assertThat(clock.getVisibility()).isEqualTo(View.VISIBLE);
    }

    @Test
    public void checkSignalStatusIcon_canInflateSignalStatusIconLayout() {
        ImageView v = (ImageView) mInflater.inflate(R.layout.user_picker_status_icon_layout, null);
        assertNotNull(v);
    }
}
