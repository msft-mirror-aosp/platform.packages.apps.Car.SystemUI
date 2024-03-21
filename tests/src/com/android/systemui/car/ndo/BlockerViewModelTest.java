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

import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;

import androidx.test.filters.SmallTest;

import com.android.car.telephony.calling.InCallServiceManager;
import com.android.systemui.SysuiTestCase;
import com.android.systemui.car.CarSystemUiTest;
import com.android.systemui.car.telecom.InCallServiceImpl;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.beans.PropertyChangeEvent;

@CarSystemUiTest
@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
@SmallTest
public class BlockerViewModelTest extends SysuiTestCase {
    private BlockerViewModel mBlockerViewModel;
    private static final String PROPERTY_IN_CALL_SERVICE = "PROPERTY_IN_CALL_SERVICE";
    private static final String BLOCKED_ACTIVITY = "com.blocked.activity";

    private InCallServiceManager mInCallServiceManager;

    @Before
    public void setup() {
        mInCallServiceManager = new InCallServiceManager();
        mBlockerViewModel = new BlockerViewModel(mInCallServiceManager);
        mBlockerViewModel.initialize(BLOCKED_ACTIVITY);
    }

    @Test
    public void testPropertyChange_serviceConnected() {
        PropertyChangeEvent event = mock(PropertyChangeEvent.class);
        when(event.getPropertyName()).thenReturn(PROPERTY_IN_CALL_SERVICE);
        InCallServiceImpl mockInCallService = mock(InCallServiceImpl.class);
        mInCallServiceManager.setInCallService(mockInCallService);

        mBlockerViewModel.propertyChange(event);

        verify(mockInCallService, atLeastOnce()).addListener(any());
    }
}
