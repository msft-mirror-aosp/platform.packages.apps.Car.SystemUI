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

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doNothing;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;
import static com.android.systemui.car.userpicker.HeaderState.HEADER_STATE_CHANGE_USER;
import static com.android.systemui.car.userpicker.HeaderState.HEADER_STATE_LOGOUT;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.when;

import android.content.res.Configuration;
import android.test.suitebuilder.annotation.SmallTest;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;
import android.view.View;

import com.android.systemui.R;
import com.android.systemui.car.CarSystemUiTest;
import com.android.systemui.car.userpicker.UserPickerController.Callbacks;
import com.android.systemui.dump.DumpManager;
import com.android.systemui.settings.DisplayTracker;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Locale;

@CarSystemUiTest
@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
@SmallTest
public class UserPickerActivityHeaderTest extends UserPickerTestCase {
    private UserPickerTestActivity mUserPickerActivity;
    private HeaderState mHeaderstate;

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
    private Callbacks mMockCallbacks;
    @Mock
    private DumpManager mMockDumpManager;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        when(mMockDisplayTracker.getDefaultDisplayId()).thenReturn(MAIN_DISPLAY_ID);

        mHeaderstate = new HeaderState(mMockCallbacks);

        mUserPickerActivity = new UserPickerTestActivity(mContext,
                mMockUserPickerController, mMockUserPickerAdapter, mMockDialogManager,
                mMockSnackbarManager, mMockDisplayTracker, mMockDumpManager);
    }

    @Test
    public void pressBackButton_changeUserState_finishActivity() {
        init(/* isPassenger= */ true);
        mHeaderstate.setState(HEADER_STATE_CHANGE_USER);
        mUserPickerActivity.setupHeaderBar(mHeaderstate);

        mUserPickerActivity.mBackButton.performClick();

        assertThat(mUserPickerActivity.mBackButton.getVisibility()).isEqualTo(View.VISIBLE);
        assertThat(mUserPickerActivity.mCalledFinished).isTrue();
    }

    @Test
    public void checkButtonsVisibility_logoutState_invisibleButtons() {
        init(/* isPassenger= */ true);
        mHeaderstate.setState(HEADER_STATE_LOGOUT);
        mUserPickerActivity.setupHeaderBar(mHeaderstate);

        assertThat(mUserPickerActivity.mBackButton.getVisibility()).isEqualTo(View.GONE);
        assertThat(mUserPickerActivity.mLogoutButton.getVisibility()).isEqualTo(View.GONE);
    }

    @Test
    public void checkTextView_changeUserState_invisibleTextView() {
        init(/* isPassenger= */ true);
        mHeaderstate.setState(HEADER_STATE_CHANGE_USER);
        mUserPickerActivity.setupHeaderBar(mHeaderstate);

        assertThat(mUserPickerActivity.mHeaderBarTextForLogout.getVisibility())
                .isEqualTo(View.GONE);
    }

    @Test
    public void checkTextView_logoutState_visibleTextView() {
        init(/* isPassenger= */ true);
        mHeaderstate.setState(HEADER_STATE_LOGOUT);
        mUserPickerActivity.setupHeaderBar(mHeaderstate);

        assertThat(mUserPickerActivity.mHeaderBarTextForLogout.getVisibility())
                .isEqualTo(View.VISIBLE);
    }

    @Test
    public void logoutUser_pressLogoutButton_stopUser() {
        init(/* isPassenger= */ true);
        mHeaderstate.setState(HEADER_STATE_CHANGE_USER);
        mUserPickerActivity.setupHeaderBar(mHeaderstate);

        assertThat(mUserPickerActivity.mLogoutButton.getVisibility()).isEqualTo(View.VISIBLE);
        mUserPickerActivity.mLogoutButton.performClick();

        verify(mMockUserPickerController).logoutUser();
    }

    @Test
    public void pressPowerButton_screenOff() {
        doNothing().when(mMockUserPickerController).screenOffDisplay();
        init(/* isPassenger= */ true);
        mHeaderstate.setState(HEADER_STATE_CHANGE_USER);
        mUserPickerActivity.setupHeaderBar(mHeaderstate);

        View powerBtn = mUserPickerActivity.mRootView.findViewById(R.id.power_button_icon_view);
        powerBtn.performClick();

        assertThat(powerBtn.getVisibility()).isEqualTo(View.VISIBLE);
        verify(mMockUserPickerController).screenOffDisplay();
    }

    @Test
    public void checkLogoutButton_inDriverSeat_invisibleLogoutButton() {
        init(/* isPassenger= */ false);
        mHeaderstate.setState(HEADER_STATE_CHANGE_USER);
        mUserPickerActivity.setupHeaderBar(mHeaderstate);

        assertThat(mUserPickerActivity.mBackButton.getVisibility()).isEqualTo(View.VISIBLE);
        assertThat(mUserPickerActivity.mLogoutButton.getVisibility()).isEqualTo(View.GONE);
    }

    @Test
    public void onConfigurationChanged_changeConfiguration_callSetAdapter() {
        init(/* isPassenger= */ true);
        mHeaderstate.setState(HEADER_STATE_CHANGE_USER);
        mUserPickerActivity.setupHeaderBar(mHeaderstate);
        // initial settings
        Configuration origConfiguration = mUserPickerActivity.getResources().getConfiguration();
        Configuration newConfiguration = origConfiguration;
        newConfiguration.orientation = Configuration.ORIENTATION_LANDSCAPE;
        newConfiguration.screenLayout = Configuration.SCREENLAYOUT_LAYOUTDIR_RTL;
        newConfiguration.setLocale(new Locale("en"));
        // orientation change
        newConfiguration.orientation = Configuration.ORIENTATION_PORTRAIT;
        mUserPickerActivity.onConfigurationChanged(newConfiguration);
        verify(mMockUserPickerAdapter).onConfigurationChanged();
        clearInvocations(mMockUserPickerAdapter);
        // screen layout change
        newConfiguration.screenLayout = Configuration.SCREENLAYOUT_LAYOUTDIR_LTR;
        mUserPickerActivity.onConfigurationChanged(newConfiguration);
        verify(mMockUserPickerAdapter).onConfigurationChanged();
        clearInvocations(mMockUserPickerAdapter);
        // locale change
        newConfiguration.setLocale(new Locale("kr"));
        mUserPickerActivity.onConfigurationChanged(newConfiguration);
        verify(mMockUserPickerAdapter).onConfigurationChanged();
        clearInvocations(mMockUserPickerAdapter);
        // reset configuration
        mUserPickerActivity.onConfigurationChanged(origConfiguration);
    }

    private void init(boolean isPassenger) {
        int displayId = isPassenger ? FRONT_PASSENGER_DISPLAY_ID : MAIN_DISPLAY_ID;
        doReturn(displayId).when(mContext).getDisplayId();
        mUserPickerActivity.init();
    }
}
