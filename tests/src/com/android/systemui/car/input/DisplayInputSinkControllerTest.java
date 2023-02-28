/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.systemui.car.input;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doAnswer;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.mockitoSession;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.spyOn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import android.car.settings.CarSettings;
import android.content.ContentResolver;
import android.database.ContentObserver;
import android.hardware.display.DisplayManager;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.Settings;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;
import android.view.Display;
import android.view.InputEvent;

import androidx.annotation.NonNull;
import androidx.test.filters.SmallTest;

import com.android.systemui.SysuiTestCase;
import com.android.systemui.car.CarSystemUiTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.List;

@CarSystemUiTest
@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
@SmallTest
public class DisplayInputSinkControllerTest extends SysuiTestCase {

    private static final String EMPTY_SETTING_VALUE = "";

    @Mock
    private DisplayManager mDisplayManager;
    @Mock
    private Display mDisplay;
    @Mock
    private DisplayInputSink.OnInputEventListener mCallback;

    private MockitoSession mMockingSession;
    private DisplayInputSinkController mDisplayInputSinkController;
    private Handler mHandler;
    private ContentResolver mContentResolver;
    private List<Display> mDisplays = new ArrayList<Display>();

    @Before
    public void setUp() {
        mMockingSession = mockitoSession()
                .initMocks(this)
                .spyStatic(UserHandle.class)
                .spyStatic(UserManager.class)
                .strictness(Strictness.WARN)
                .startMocking();
        spyOn(mContext);
        mContentResolver = mContext.getContentResolver();
        spyOn(mContentResolver);
        mHandler = new Handler(Looper.getMainLooper());
        doReturn(mDisplayManager).when(mContext).getSystemService(DisplayManager.class);
        mDisplayInputSinkController =
                new DisplayInputSinkController(mContext, mHandler);
        spyOn(mDisplayInputSinkController);
        writeDisplayInputLockSetting(mContentResolver, EMPTY_SETTING_VALUE);
        doAnswer(invocation -> mDisplays.toArray(new Display[0]))
                .when(mDisplayManager).getDisplays();
    }

    @After
    public void tearDown() {
        mMockingSession.finishMocking();
    }

    @Test
    public void start_nonSystemUser_controllerNotStarted() {
        doReturn(UserHandle.USER_NULL).when(() -> UserHandle.myUserId());
        doReturn(true).when(() -> UserManager.isHeadlessSystemUserMode());

        mDisplayInputSinkController.start();

        verify(mContentResolver, never())
                .registerContentObserver(any(Uri.class), anyBoolean(), any(ContentObserver.class));
        verify(mDisplayManager, never()).registerDisplayListener(
                any(DisplayManager.DisplayListener.class),
                any());
    }

    @Test
    public void start_systemUser_controllerStarted() {
        doReturn(UserHandle.USER_SYSTEM).when(() -> UserHandle.myUserId());
        doReturn(true).when(() -> UserManager.isHeadlessSystemUserMode());

        mDisplayInputSinkController.start();

        verify(mContentResolver, times(1))
                .registerContentObserver(any(Uri.class), anyBoolean(), any(ContentObserver.class));
        verify(mDisplayManager, times(1)).registerDisplayListener(
                any(DisplayManager.DisplayListener.class),
                any());
    }

    @Test
    public void startDisplayInputLock_withValidDisplay_createsDisplayInputSink() {
        int displayId = 99;
        createDisplay(displayId, "testUniqueId");

        mDisplayInputSinkController.startDisplayInputLock(displayId);

        assertThat(mDisplayInputSinkController.mDisplayInputSinks.size()).isEqualTo(1);
        assertThat(mDisplayInputSinkController.mDisplayInputSinks.get(displayId)).isNotNull();
    }

    @Test
    public void startDisplayInputLock_withInvalidDisplay_doesNotCreateDisplayInputSink() {
        int displayId = 99;
        doReturn(null).when(mDisplayManager).getDisplay(eq(displayId));

        mDisplayInputSinkController.startDisplayInputLock(displayId);

        assertThat(mDisplayInputSinkController.mDisplayInputSinks.size()).isEqualTo(0);
        assertThat(mDisplayInputSinkController.mDisplayInputSinks.get(displayId)).isNull();
    }

    @Test
    public void startDisplayInputLock_alreadyStarted_displayInputSinkRemainsSame() {
        int displayId = 99;
        doReturn(mDisplay).when(mDisplayManager).getDisplay(eq(displayId));
        addDisplayInputSink(displayId);

        mDisplayInputSinkController.startDisplayInputLock(displayId);

        assertThat(mDisplayInputSinkController.mDisplayInputSinks.size()).isEqualTo(1);
        assertThat(mDisplayInputSinkController.mDisplayInputSinks.get(displayId)).isNotNull();
    }

    @Test
    public void stopDisplayInputLockLocked_inputLockStarted_removesDisplayInputSink() {
        int displayId = 99;
        addDisplayInputSink(displayId);

        mDisplayInputSinkController.stopDisplayInputLock(displayId);

        assertThat(mDisplayInputSinkController.mDisplayInputSinks.size()).isEqualTo(0);
        assertThat(mDisplayInputSinkController.mDisplayInputSinks.get(displayId)).isNull();
    }

    @Test
    public void stopDisplayInputLockLocked_inputLockNotStarted_doesNotStopDisplayInputLock() {
        int displayId = 99;
        int inputLockedDisplayId = 100;
        addDisplayInputSink(inputLockedDisplayId);

        mDisplayInputSinkController.stopDisplayInputLock(displayId);

        assertThat(mDisplayInputSinkController.mDisplayInputSinks.size()).isEqualTo(1);
        assertThat(mDisplayInputSinkController.mDisplayInputSinks.get(displayId)).isNull();
        assertThat(mDisplayInputSinkController.mDisplayInputSinks.get(inputLockedDisplayId))
                .isNotNull();
    }

    @Test
    public void onDisplayAdded_withValidDisplay_callsStartDisplayInputLock() {
        int displayId = 99;
        String uniqueId = "testUniqueId";
        createDisplay(displayId, uniqueId);
        doReturn(UserHandle.USER_SYSTEM).when(() -> UserHandle.myUserId());
        mDisplayInputSinkController.start();
        mDisplayInputSinkController.mDisplayInputLockSetting.add(uniqueId);

        mDisplayInputSinkController.mDisplayListener.onDisplayAdded(displayId);

        verify(mDisplayInputSinkController).startDisplayInputLock(eq(displayId));
    }

    @Test
    public void onDisplayAdded_withNullDisplay_doesNotCallStartDisplayInputLock() {
        int displayId = 99;
        String uniqueId = "testUniqueId";
        doReturn(UserHandle.USER_SYSTEM).when(() -> UserHandle.myUserId());
        mDisplayInputSinkController.start();
        mDisplayInputSinkController.mDisplayInputLockSetting.add(uniqueId);
        doReturn(null).when(mDisplayManager).getDisplay(eq(displayId));
        doReturn(uniqueId).when(mDisplay).getUniqueId();

        mDisplayInputSinkController.mDisplayListener.onDisplayAdded(displayId);

        verify(mDisplayInputSinkController, never()).startDisplayInputLock(eq(displayId));
    }

    @Test
    public void onDisplayRemoved_inputLockStarted_callsStopDisplayInputLock() {
        int displayId = 99;
        doReturn(UserHandle.USER_SYSTEM).when(() -> UserHandle.myUserId());
        mDisplayInputSinkController.start();
        addDisplayInputSink(displayId);

        mDisplayInputSinkController.mDisplayListener.onDisplayRemoved(displayId);

        verify(mDisplayInputSinkController).stopDisplayInputLock(eq(displayId));
    }

    @Test
    public void refreshDisplayInputLock_withValidSettingValue_callsStartDisplayInputLock() {
        int displayId = 99;
        String displayUniqueId = "testUniqueId";
        createDisplay(displayId, displayUniqueId);
        writeDisplayInputLockSetting(mContentResolver, displayUniqueId);

        mDisplayInputSinkController.refreshDisplayInputLock();

        assertThat(mDisplayInputSinkController.mDisplayInputLockSetting.size()).isEqualTo(1);
        verify(mDisplayInputSinkController).startDisplayInputLock(eq(displayId));
    }

    @Test
    public void refreshDisplayInputLock_withInvalidSettingValue_doesNotCallStartDisplayInputLock() {
        int displayId = 99;
        String displayUniqueId = "validUniqueId";
        String settingUniqueId = "invalidUniqueId";
        createDisplay(displayId, displayUniqueId);
        writeDisplayInputLockSetting(mContentResolver, settingUniqueId);

        mDisplayInputSinkController.refreshDisplayInputLock();

        assertThat(mDisplayInputSinkController.mDisplayInputLockSetting.size()).isEqualTo(0);
        verify(mDisplayInputSinkController, never())
                .startDisplayInputLock(eq(displayId));
    }

    @Test
    public void refreshDisplayInputLock_duplicateEntriesInSettingValue_onlyOneEntryIsValid() {
        int displayId = 99;
        String displayUniqueId = "testUniqueId";
        String settingUniqueId = displayUniqueId + "," + displayUniqueId;
        createDisplay(displayId, displayUniqueId);
        writeDisplayInputLockSetting(mContentResolver, settingUniqueId);

        mDisplayInputSinkController.refreshDisplayInputLock();

        assertThat(mDisplayInputSinkController.mDisplayInputLockSetting.size()).isEqualTo(1);
        verify(mDisplayInputSinkController, times(1)).startDisplayInputLock(eq(displayId));
    }

    @Test
    public void refreshDisplayInputLock_inputLockAlreadyStarted_doesNotCallStartDisplayInputLock() {
        int inputLockedDisplayId = 99;
        String displayUniqueId = "testUniqueId";
        addDisplayInputSink(inputLockedDisplayId);
        createDisplay(inputLockedDisplayId, displayUniqueId);
        writeDisplayInputLockSetting(mContentResolver, displayUniqueId);

        mDisplayInputSinkController.refreshDisplayInputLock();

        assertThat(mDisplayInputSinkController.mDisplayInputLockSetting.size()).isEqualTo(1);
        verify(mDisplayInputSinkController, never())
                .startDisplayInputLock(eq(inputLockedDisplayId));
    }

    @Test
    public void refreshDisplayInputLock_settingValueReplaced_stopsExistingLockAndStartsNewLock() {
        int inputLockedDisplayId = 99;
        int displayId = 100;
        String displayUniqueId = "testUniqueId";
        addDisplayInputSink(inputLockedDisplayId);
        createDisplay(displayId, displayUniqueId);
        writeDisplayInputLockSetting(mContentResolver, displayUniqueId);

        mDisplayInputSinkController.refreshDisplayInputLock();

        verify(mDisplayInputSinkController).stopDisplayInputLock(eq(inputLockedDisplayId));
        verify(mDisplayInputSinkController).startDisplayInputLock(eq(displayId));
    }

    @Test
    public void refreshDisplayInputLock_multiEntriesInSettingValue_startsDisplayInputLockForEach() {
        int displayId1 = 99;
        int displayId2 = 100;
        String displayUniqueId1 = "testUniqueId1";
        String displayUniqueId2 = "testUniqueId2";
        String settingUniqueId = displayUniqueId1 + "," + displayUniqueId2;
        createDisplay(displayId1, displayUniqueId1);
        createDisplay(displayId2, displayUniqueId2);
        writeDisplayInputLockSetting(mContentResolver, settingUniqueId);

        mDisplayInputSinkController.refreshDisplayInputLock();

        verify(mDisplayInputSinkController).startDisplayInputLock(eq(displayId1));
        verify(mDisplayInputSinkController).startDisplayInputLock(eq(displayId2));
    }

    @Test
    public void refreshDisplayInputLock_settingValueRemoved_stopsDisplayInputLockForEach() {
        int displayId1 = 99;
        int displayId2 = 100;
        String displayUniqueId1 = "testUniqueId1";
        String displayUniqueId2 = "testUniqueId2";
        addDisplayInputSink(displayId1);
        addDisplayInputSink(displayId2);
        writeDisplayInputLockSetting(mContentResolver, EMPTY_SETTING_VALUE);
        createDisplay(displayId1, displayUniqueId1);
        createDisplay(displayId2, displayUniqueId2);

        mDisplayInputSinkController.refreshDisplayInputLock();

        verify(mDisplayInputSinkController).stopDisplayInputLock(eq(displayId1));
        verify(mDisplayInputSinkController).stopDisplayInputLock(eq(displayId2));
    }

    @Test
    public void onInputEvent_inputEventReceived_callbackOnInputEvent() {
        doReturn(mDisplay).when(mDisplayManager).getDisplay(anyInt());
        DisplayInputSink displayInputSinks =
                new DisplayInputSink(mDisplay, mCallback);

        displayInputSinks.mInputEventReceiver.onInputEvent(mock(InputEvent.class));

        verify(mCallback).onInputEvent(any(InputEvent.class));
    }

    private void writeDisplayInputLockSetting(@NonNull ContentResolver resolver,
            @NonNull String value) {
        Settings.Global.putString(resolver, CarSettings.Global.DISPLAY_INPUT_LOCK, value);
    }

    private Display createDisplay(int displayId, String uniqueId) {
        Display display = mock(Display.class);
        doReturn(display).when(mDisplayManager).getDisplay(displayId);
        doReturn(uniqueId).when(display).getUniqueId();
        doReturn(displayId).when(display).getDisplayId();
        mDisplays.add(display);
        doReturn(mock(DisplayInputLockInfoWindow.class))
                .when(mDisplayInputSinkController).createDisplayInputLockInfoWindow(display);
        return display;
    }

    private void addDisplayInputSink(int displayId) {
        Display display = mock(Display.class);
        doReturn(display).when(mDisplayManager).getDisplay(displayId);
        DisplayInputSink displayInputSinks = new DisplayInputSink(display, mCallback);
        mDisplayInputSinkController.mDisplayInputSinks.put(displayId, displayInputSinks);
        assertThat(mDisplayInputSinkController.mDisplayInputSinks.get(displayId)).isNotNull();
    }
}
