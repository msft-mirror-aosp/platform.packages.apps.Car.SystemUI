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

package com.android.systemui.car.systembar;

import static android.hardware.SensorPrivacyManager.Sensors.CAMERA;
import static android.hardware.SensorPrivacyManager.Sources.QS_TILE;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.car.Car;
import android.car.user.CarUserManager;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.hardware.SensorPrivacyManager;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;

import androidx.test.filters.SmallTest;

import com.android.systemui.R;
import com.android.systemui.SysuiTestCase;
import com.android.systemui.broadcast.BroadcastDispatcher;
import com.android.systemui.car.CarDeviceProvisionedController;
import com.android.systemui.car.CarServiceProvider;
import com.android.systemui.car.CarSystemUiTest;
import com.android.systemui.car.privacy.CameraPrivacyChip;
import com.android.systemui.privacy.PrivacyItem;
import com.android.systemui.privacy.PrivacyItemController;
import com.android.systemui.privacy.PrivacyType;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collections;
import java.util.concurrent.Executor;

@CarSystemUiTest
@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
@SmallTest
public class CameraPrivacyChipViewControllerTest extends SysuiTestCase {

    private CameraPrivacyChipViewController mCameraPrivacyChipViewController;
    private FrameLayout mFrameLayout;
    private CameraPrivacyChip mCameraPrivacyChip;

    @Captor
    private ArgumentCaptor<Runnable> mRunnableArgumentCaptor;
    @Captor
    private ArgumentCaptor<PrivacyItemController.Callback> mPicCallbackArgumentCaptor;
    @Captor
    private ArgumentCaptor<SensorPrivacyManager.OnSensorPrivacyChangedListener>
            mOnSensorPrivacyChangedListenerArgumentCaptor;
    @Captor
    private ArgumentCaptor<BroadcastReceiver> mBroadcastReceiverArgumentCaptor;
    @Captor
    private ArgumentCaptor<CarUserManager.UserLifecycleListener>
            mUserLifecycleListenerArgumentCaptor;

    @Mock
    private PrivacyItemController mPrivacyItemController;
    @Mock
    private PrivacyItem mPrivacyItem;
    @Mock
    private Executor mExecutor;
    @Mock
    private BroadcastDispatcher mBroadcastDispatcher;
    @Mock
    private SensorPrivacyManager mSensorPrivacyManager;
    @Mock
    private CarDeviceProvisionedController mCarDeviceProvisionedController;
    @Mock
    private CarUserManager mCarUserManager;
    @Mock
    private Car mCar;
    @Mock
    private Runnable mQsTileNotifyUpdateRunnable;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(/* testClass= */ this);

        mFrameLayout = new FrameLayout(mContext);
        mCameraPrivacyChip = spy((CameraPrivacyChip) LayoutInflater.from(mContext)
                .inflate(R.layout.camera_privacy_chip, /* root= */ null));
        mFrameLayout.addView(mCameraPrivacyChip);
        mContext = spy(mContext);

        when(mContext.getMainExecutor()).thenReturn(mExecutor);
        when(mCar.isConnected()).thenReturn(true);
        when(mCar.getCarManager(Car.CAR_USER_SERVICE)).thenReturn(mCarUserManager);

        CarServiceProvider carServiceProvider = new CarServiceProvider(mContext, mCar);

        mCameraPrivacyChipViewController =
                new CameraPrivacyChipViewController(mContext, mPrivacyItemController,
                carServiceProvider, mBroadcastDispatcher, mSensorPrivacyManager,
                mCarDeviceProvisionedController);
        when(mCarDeviceProvisionedController.getCurrentUser()).thenReturn(0);
    }

    @Test
    public void addPrivacyChipView_privacyChipViewPresent_addCallbackCalled() {
        mCameraPrivacyChipViewController.addPrivacyChipView(mFrameLayout);

        verify(mPrivacyItemController).addCallback(any());
    }

    @Test
    public void addPrivacyChipView_privacyChipViewPresent_sensorStatusSet() {
        when(mSensorPrivacyManager.isSensorPrivacyEnabled(anyInt(), eq(CAMERA)))
                .thenReturn(false);
        mCameraPrivacyChipViewController.addPrivacyChipView(mFrameLayout);
        verify(mExecutor).execute(mRunnableArgumentCaptor.capture());

        mRunnableArgumentCaptor.getValue().run();

        verify(mCameraPrivacyChip).setSensorEnabled(eq(true));
    }

    @Test
    public void addPrivacyChipView_privacyChipViewNotPresent_addCallbackNotCalled() {
        mCameraPrivacyChipViewController.addPrivacyChipView(new View(getContext()));

        verify(mPrivacyItemController, never()).addCallback(any());
    }

    @Test
    public void onPrivacyItemsChanged_cameraIsPartOfPrivacyItems_animateInCalled() {
        when(mPrivacyItem.getPrivacyType()).thenReturn(PrivacyType.TYPE_CAMERA);
        mCameraPrivacyChipViewController.addPrivacyChipView(mFrameLayout);
        verify(mPrivacyItemController).addCallback(mPicCallbackArgumentCaptor.capture());
        mPicCallbackArgumentCaptor.getValue().onFlagAllChanged(true);
        mPicCallbackArgumentCaptor.getValue().onFlagMicCameraChanged(true);

        mPicCallbackArgumentCaptor.getValue()
                .onPrivacyItemsChanged(Collections.singletonList(mPrivacyItem));
        verify(mExecutor, times(2)).execute(mRunnableArgumentCaptor.capture());
        mRunnableArgumentCaptor.getAllValues().forEach(Runnable::run);

        verify(mCameraPrivacyChip).animateIn();
    }

    @Test
    public void onPrivacyItemsChanged_cameraIsPartOfPrivacyItemsTwice_animateInCalledOnce() {
        when(mPrivacyItem.getPrivacyType()).thenReturn(PrivacyType.TYPE_CAMERA);
        mCameraPrivacyChipViewController.addPrivacyChipView(mFrameLayout);
        verify(mPrivacyItemController).addCallback(mPicCallbackArgumentCaptor.capture());
        mPicCallbackArgumentCaptor.getValue().onFlagAllChanged(true);
        mPicCallbackArgumentCaptor.getValue().onFlagMicCameraChanged(true);

        mPicCallbackArgumentCaptor.getValue()
                .onPrivacyItemsChanged(Collections.singletonList(mPrivacyItem));
        mPicCallbackArgumentCaptor.getValue()
                .onPrivacyItemsChanged(Collections.singletonList(mPrivacyItem));
        verify(mExecutor, times(2)).execute(mRunnableArgumentCaptor.capture());
        mRunnableArgumentCaptor.getAllValues().forEach(Runnable::run);

        verify(mCameraPrivacyChip).animateIn();
    }

    @Test
    public void onPrivacyItemsChanged_cameraIsNotPartOfPrivacyItems_animateOutCalled() {
        when(mPrivacyItem.getPrivacyType()).thenReturn(PrivacyType.TYPE_CAMERA);
        mCameraPrivacyChipViewController.addPrivacyChipView(mFrameLayout);
        verify(mPrivacyItemController).addCallback(mPicCallbackArgumentCaptor.capture());
        mPicCallbackArgumentCaptor.getValue().onFlagAllChanged(true);
        mPicCallbackArgumentCaptor.getValue().onFlagMicCameraChanged(true);
        mPicCallbackArgumentCaptor.getValue()
                .onPrivacyItemsChanged(Collections.singletonList(mPrivacyItem));

        mPicCallbackArgumentCaptor.getValue().onPrivacyItemsChanged(Collections.emptyList());
        verify(mExecutor, times(3))
                .execute(mRunnableArgumentCaptor.capture());
        mRunnableArgumentCaptor.getAllValues().forEach(Runnable::run);

        verify(mCameraPrivacyChip).animateOut();
    }

    @Test
    public void onPrivacyItemsChanged_cameraIsNotPartOfPrivacyItemsTwice_animateOutCalledOnce() {
        when(mPrivacyItem.getPrivacyType()).thenReturn(PrivacyType.TYPE_CAMERA);
        mCameraPrivacyChipViewController.addPrivacyChipView(mFrameLayout);
        verify(mPrivacyItemController).addCallback(mPicCallbackArgumentCaptor.capture());
        mPicCallbackArgumentCaptor.getValue().onFlagAllChanged(true);
        mPicCallbackArgumentCaptor.getValue().onFlagMicCameraChanged(true);
        mPicCallbackArgumentCaptor.getValue()
                .onPrivacyItemsChanged(Collections.singletonList(mPrivacyItem));

        mPicCallbackArgumentCaptor.getValue().onPrivacyItemsChanged(Collections.emptyList());
        mPicCallbackArgumentCaptor.getValue().onPrivacyItemsChanged(Collections.emptyList());
        verify(mExecutor, times(3))
                .execute(mRunnableArgumentCaptor.capture());
        mRunnableArgumentCaptor.getAllValues().forEach(Runnable::run);

        verify(mCameraPrivacyChip).animateOut();
    }

    @Test
    public void onPrivacyItemsChanged_qsTileNotifyUpdateRunnableExecuted() {
        when(mPrivacyItem.getPrivacyType()).thenReturn(PrivacyType.TYPE_CAMERA);
        mCameraPrivacyChipViewController.setNotifyUpdateRunnable(mQsTileNotifyUpdateRunnable);
        mCameraPrivacyChipViewController.addPrivacyChipView(mFrameLayout);
        verify(mPrivacyItemController).addCallback(mPicCallbackArgumentCaptor.capture());
        mPicCallbackArgumentCaptor.getValue().onFlagAllChanged(true);
        mPicCallbackArgumentCaptor.getValue().onFlagMicCameraChanged(true);

        mPicCallbackArgumentCaptor.getValue().onPrivacyItemsChanged(Collections.emptyList());
        verify(mExecutor).execute(mRunnableArgumentCaptor.capture());
        mRunnableArgumentCaptor.getAllValues().forEach(Runnable::run);

        verify(mQsTileNotifyUpdateRunnable).run();
    }

    @Test
    public void onSensorPrivacyChanged_argTrue_setSensorEnabledWithFalseCalled() {
        mCameraPrivacyChipViewController.addPrivacyChipView(mFrameLayout);
        verify(mSensorPrivacyManager).addSensorPrivacyListener(eq(CAMERA),
                /* userId= */ eq(0), mOnSensorPrivacyChangedListenerArgumentCaptor.capture());
        reset(mCameraPrivacyChip);
        reset(mExecutor);
        mOnSensorPrivacyChangedListenerArgumentCaptor.getValue()
                .onSensorPrivacyChanged(CAMERA, /* enabled= */ true);
        verify(mExecutor).execute(mRunnableArgumentCaptor.capture());

        mRunnableArgumentCaptor.getAllValues().forEach(Runnable::run);

        verify(mCameraPrivacyChip).setSensorEnabled(eq(false));
    }

    @Test
    public void onSensorPrivacyChanged_argFalse_setSensorEnabledWithTrueCalled() {
        mCameraPrivacyChipViewController.addPrivacyChipView(mFrameLayout);
        verify(mSensorPrivacyManager).addSensorPrivacyListener(eq(CAMERA),
                /* userId= */ eq(0), mOnSensorPrivacyChangedListenerArgumentCaptor.capture());
        reset(mCameraPrivacyChip);
        reset(mExecutor);
        mOnSensorPrivacyChangedListenerArgumentCaptor.getValue()
                .onSensorPrivacyChanged(CAMERA, /* enabled= */ false);
        verify(mExecutor).execute(mRunnableArgumentCaptor.capture());

        mRunnableArgumentCaptor.getAllValues().forEach(Runnable::run);

        verify(mCameraPrivacyChip).setSensorEnabled(eq(true));
    }

    @Test
    public void onSensorPrivacyChanged_qsTileNotifyUpdateRunnableExecuted() {
        mCameraPrivacyChipViewController.setNotifyUpdateRunnable(mQsTileNotifyUpdateRunnable);
        mCameraPrivacyChipViewController.addPrivacyChipView(mFrameLayout);
        verify(mSensorPrivacyManager).addSensorPrivacyListener(eq(CAMERA),
                /* userId= */ eq(0), mOnSensorPrivacyChangedListenerArgumentCaptor.capture());
        reset(mCameraPrivacyChip);
        reset(mExecutor);
        mOnSensorPrivacyChangedListenerArgumentCaptor.getValue()
                .onSensorPrivacyChanged(CAMERA, /* enabled= */ true);
        verify(mExecutor).execute(mRunnableArgumentCaptor.capture());

        mRunnableArgumentCaptor.getAllValues().forEach(Runnable::run);

        verify(mQsTileNotifyUpdateRunnable).run();
    }

    @Test
    public void onUserUpdateReceive_setSensorEnabledCalled() {
        when(mSensorPrivacyManager.isSensorPrivacyEnabled(anyInt(), eq(CAMERA)))
                .thenReturn(true);
        mCameraPrivacyChipViewController.addPrivacyChipView(mFrameLayout);
        verify(mBroadcastDispatcher).registerReceiver(mBroadcastReceiverArgumentCaptor.capture(),
                any(), any(), any());
        reset(mExecutor);
        when(mCarDeviceProvisionedController.getCurrentUser()).thenReturn(1);
        mBroadcastReceiverArgumentCaptor.getValue().onReceive(mContext,
                new Intent(Intent.ACTION_USER_INFO_CHANGED));
        verify(mExecutor).execute(mRunnableArgumentCaptor.capture());

        mRunnableArgumentCaptor.getValue().run();

        verify(mCameraPrivacyChip).setSensorEnabled(eq(false));
    }

    @Test
    public void onUserChangeReceive_setSensorEnabledCalled() {
        when(mSensorPrivacyManager.isSensorPrivacyEnabled(anyInt(), eq(CAMERA)))
                .thenReturn(false);
        mCameraPrivacyChipViewController.addPrivacyChipView(mFrameLayout);
        verify(mCarUserManager).addListener(any(), any(),
                mUserLifecycleListenerArgumentCaptor.capture());
        CarUserManager.UserLifecycleEvent event = new CarUserManager.UserLifecycleEvent(
                CarUserManager.USER_LIFECYCLE_EVENT_TYPE_SWITCHING, /* from= */ 0,
                /* to= */ 1);
        CarUserManager.UserLifecycleListener userLifecycleListener =
                mUserLifecycleListenerArgumentCaptor.getValue();
        assertThat(userLifecycleListener).isNotNull();
        reset(mExecutor);
        userLifecycleListener.onEvent(event);
        verify(mExecutor).execute(mRunnableArgumentCaptor.capture());

        mRunnableArgumentCaptor.getValue().run();

        verify(mCameraPrivacyChip).setSensorEnabled(eq(true));
    }

    @Test
    public void isSensorEnabled_sensorPrivacyEnabled_returnFalse() {
        when(mSensorPrivacyManager.isSensorPrivacyEnabled(anyInt(), eq(CAMERA)))
                .thenReturn(true);

        assertThat(mCameraPrivacyChipViewController.isSensorEnabled()).isFalse();
    }

    @Test
    public void isSensorEnabled_sensorPrivacyDisabled_returnTrue() {
        when(mSensorPrivacyManager.isSensorPrivacyEnabled(anyInt(), eq(CAMERA)))
                .thenReturn(false);

        assertThat(mCameraPrivacyChipViewController.isSensorEnabled()).isTrue();
    }

    @Test
    public void toggleSensor_cameraTurnedOn_sensorPrivacyEnabled() {
        when(mSensorPrivacyManager.isSensorPrivacyEnabled(anyInt(), eq(CAMERA)))
                .thenReturn(false);

        mCameraPrivacyChipViewController.toggleSensor();

        verify(mSensorPrivacyManager)
                .setSensorPrivacy(eq(QS_TILE), eq(CAMERA), eq(true), anyInt());
    }

    @Test
    public void toggleSensor_cameraTurnedOff_sensorPrivacyDisabled() {
        when(mSensorPrivacyManager.isSensorPrivacyEnabled(anyInt(), eq(CAMERA)))
                .thenReturn(true);

        mCameraPrivacyChipViewController.toggleSensor();

        verify(mSensorPrivacyManager)
                .setSensorPrivacy(eq(QS_TILE), eq(CAMERA), eq(false), anyInt());
    }
}
