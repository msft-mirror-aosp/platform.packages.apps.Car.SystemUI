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
package com.android.systemui.car.ndo;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doNothing;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.ComponentName;
import android.net.Uri;
import android.telecom.Call;
import android.telecom.GatewayInfo;
import android.telecom.PhoneAccountHandle;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;

import androidx.test.filters.SmallTest;

import com.android.car.telephony.calling.InCallServiceManager;
import com.android.systemui.SysuiTestCase;
import com.android.systemui.car.CarSystemUiTest;
import com.android.systemui.car.telecom.InCallServiceImpl;
import com.android.systemui.lifecycle.InstantTaskExecutorRule;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

@CarSystemUiTest
@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
@SmallTest
public class InCallLiveDataTest extends SysuiTestCase {
    @Rule
    public TestRule rule = new InstantTaskExecutorRule();

    private InCallLiveData mInCallLiveData;
    private static final String PACKAGE_NAME = "com.package.name";
    private static final String CLASS_NAME = "com.package.name.class";
    private static final String NUMBER = "1234567890";

    @Mock
    private InCallServiceImpl mMockInCallService;
    private InCallServiceManager mInCallServiceManager;
    @Mock
    private Call mMockCall;
    private Call.Details mMockDetails;
    @Captor
    private ArgumentCaptor<Call.Callback> mCallbackCaptor;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);

        when(mMockInCallService.getCalls()).thenReturn(List.of(mMockCall));
        mMockDetails = createMockCallDetails(NUMBER, Call.STATE_HOLDING);
        when(mMockCall.getDetails()).thenReturn(mMockDetails);
        doNothing().when(mMockCall).registerCallback(mCallbackCaptor.capture());

        mInCallServiceManager = new InCallServiceManager();
        mInCallServiceManager.setInCallService(mMockInCallService);
        mInCallLiveData = new InCallLiveData(mInCallServiceManager, PACKAGE_NAME);
    }

    @Test
    public void testOnCallAdded() {
        mInCallLiveData.onCallAdded(mMockCall);

        verify(mMockCall).registerCallback(any());
        assertThat(mInCallLiveData.getValue()).isEqualTo(mMockCall);
    }

    @Test
    public void testOnCallRemoved() {
        when(mMockInCallService.getCalls()).thenReturn(List.of());

        mInCallLiveData.onCallRemoved(mMockCall);
        verify(mMockCall).unregisterCallback(any());
        assertThat(mInCallLiveData.getValue()).isNull();
    }

    @Test
    public void testOnStateChanged() {
        when(mMockDetails.getState()).thenReturn(Call.STATE_RINGING);
        mInCallLiveData.onCallAdded(mMockCall);

        verify(mMockCall).registerCallback(any());
        assertThat(mInCallLiveData.getValue()).isNull();

        when(mMockDetails.getState()).thenReturn(Call.STATE_ACTIVE);
        mCallbackCaptor.getValue().onStateChanged(mMockCall, Call.STATE_ACTIVE);
        assertThat(mInCallLiveData.getValue()).isEqualTo(mMockCall);
    }

    private static Call.Details createMockCallDetails(String number, int state) {
        Call.Details callDetails = mock(Call.Details.class);
        Uri uri = Uri.fromParts("tel", number, null);
        GatewayInfo gatewayInfo = new GatewayInfo("", uri, uri);
        PhoneAccountHandle handle = mock(PhoneAccountHandle.class);
        ComponentName componentName = new ComponentName(PACKAGE_NAME, CLASS_NAME);
        when(handle.getComponentName()).thenReturn(componentName);
        when(callDetails.getHandle()).thenReturn(uri);
        when(callDetails.getGatewayInfo()).thenReturn(gatewayInfo);
        when(callDetails.getAccountHandle()).thenReturn(handle);
        when(callDetails.hasProperty(Call.Details.PROPERTY_SELF_MANAGED)).thenReturn(true);
        when(callDetails.getState()).thenReturn(state);
        return callDetails;
    }
}
