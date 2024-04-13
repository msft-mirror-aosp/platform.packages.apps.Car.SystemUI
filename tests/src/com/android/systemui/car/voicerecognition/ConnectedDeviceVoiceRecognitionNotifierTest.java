/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.systemui.car.voicerecognition;

import static com.android.systemui.car.voicerecognition.ConnectedDeviceVoiceRecognitionNotifier.INVALID_VALUE;
import static com.android.systemui.car.voicerecognition.ConnectedDeviceVoiceRecognitionNotifier.VOICE_RECOGNITION_STARTED;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.testing.AndroidTestingRunner;

import androidx.test.filters.SmallTest;

import com.android.systemui.SysuiTestCase;
import com.android.systemui.car.CarSystemUiTest;
import com.android.systemui.util.concurrency.DelayableExecutor;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@CarSystemUiTest
@RunWith(AndroidTestingRunner.class)
@SmallTest
// TODO(b/162866441): Refactor to use the Executor pattern instead.
public class ConnectedDeviceVoiceRecognitionNotifierTest extends SysuiTestCase {

    // TODO(b/218911666): {@link BluetoothHeadsetClient.ACTION_AG_EVENT} is a hidden API.
    private static final String HEADSET_CLIENT_ACTION_AG_EVENT =
            "android.bluetooth.headsetclient.profile.action.AG_EVENT";
    // TODO(b/218911666): {@link BluetoothHeadsetClient.EXTRA_VOICE_RECOGNITION} is a hidden API.
    private static final String HEADSET_CLIENT_EXTRA_VOICE_RECOGNITION =
            "android.bluetooth.headsetclient.extra.VOICE_RECOGNITION";
    // TODO(b/218911666): {@link BluetoothHeadsetClient.ACTION_AUDIO_STATE_CHANGED} is a hidden API.
    private static final String HEADSET_CLIENT_ACTION_AUDIO_STATE_CHANGED =
            "android.bluetooth.headsetclient.profile.action.AUDIO_STATE_CHANGED";

    private static final String BLUETOOTH_REMOTE_ADDRESS = "00:11:22:33:44:55";

    private ConnectedDeviceVoiceRecognitionNotifier mVoiceRecognitionNotifier;
    private BluetoothDevice mBluetoothDevice;

    @Mock
    private Context mMockContext;
    @Mock
    private DelayableExecutor mExecutor;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mBluetoothDevice = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(
                BLUETOOTH_REMOTE_ADDRESS);
        mVoiceRecognitionNotifier = new ConnectedDeviceVoiceRecognitionNotifier(
                mMockContext, mExecutor);
    }

    @Test
    public void onBootComplete_registersReceiver() {
        mVoiceRecognitionNotifier.onBootCompleted();

        ArgumentCaptor<IntentFilter> captor = ArgumentCaptor.forClass(IntentFilter.class);
        verify(mMockContext).registerReceiverAsUser(any(), any(), captor.capture(), any(), any());

        assertThat(captor.getValue().hasAction(HEADSET_CLIENT_ACTION_AG_EVENT)).isTrue();
    }

    @Test
    public void testReceiveIntent_started_showToast() {
        BroadcastReceiver receiver = onBootCompleteGetBroadcastReceiver();
        Intent intent = new Intent(HEADSET_CLIENT_ACTION_AG_EVENT);
        intent.putExtra(HEADSET_CLIENT_EXTRA_VOICE_RECOGNITION, VOICE_RECOGNITION_STARTED);
        intent.putExtra(BluetoothDevice.EXTRA_DEVICE, mBluetoothDevice);

        receiver.onReceive(mMockContext, intent);

        ArgumentCaptor<Runnable> argumentCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(mExecutor).execute(argumentCaptor.capture());
        assertThat(argumentCaptor.getValue()).isNotNull();
        assertThat(argumentCaptor.getValue()).isNotEqualTo(this);
    }

    @Test
    public void testReceiveIntent_invalidExtra_noToast() {
        BroadcastReceiver receiver = onBootCompleteGetBroadcastReceiver();
        Intent intent = new Intent(HEADSET_CLIENT_ACTION_AG_EVENT);
        intent.putExtra(HEADSET_CLIENT_EXTRA_VOICE_RECOGNITION, INVALID_VALUE);
        intent.putExtra(BluetoothDevice.EXTRA_DEVICE, mBluetoothDevice);

        receiver.onReceive(mMockContext, intent);

        verify(mExecutor, never()).execute(any());
    }

    @Test
    public void testReceiveIntent_noExtra_noToast() {
        BroadcastReceiver receiver = onBootCompleteGetBroadcastReceiver();
        Intent intent = new Intent(HEADSET_CLIENT_ACTION_AG_EVENT);
        intent.putExtra(BluetoothDevice.EXTRA_DEVICE, mBluetoothDevice);

        receiver.onReceive(mMockContext, intent);

        verify(mExecutor, never()).execute(any());
    }

    @Test
    public void testReceiveIntent_invalidIntent_noToast() {
        BroadcastReceiver receiver = onBootCompleteGetBroadcastReceiver();
        Intent intent = new Intent(HEADSET_CLIENT_ACTION_AUDIO_STATE_CHANGED);
        intent.putExtra(BluetoothDevice.EXTRA_DEVICE, mBluetoothDevice);

        receiver.onReceive(mMockContext, intent);

        verify(mExecutor, never()).execute(any());
    }

    private BroadcastReceiver onBootCompleteGetBroadcastReceiver() {
        mVoiceRecognitionNotifier.onBootCompleted();

        ArgumentCaptor<BroadcastReceiver> captor = ArgumentCaptor.forClass(BroadcastReceiver.class);
        verify(mMockContext).registerReceiverAsUser(captor.capture(), any(), any(), any(), any());
        return captor.getValue();
    }
}
