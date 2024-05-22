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

import static android.Manifest.permission.ACCESS_NETWORK_STATE;
import static android.Manifest.permission.INTERNET;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.mockitoSession;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.ActivityManager;
import android.car.drivingstate.CarUxRestrictions;
import android.content.ComponentName;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.os.Handler;
import android.os.RemoteException;
import android.os.UserHandle;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;
import android.view.View;
import android.widget.Button;
import android.widget.PopupWindow;

import androidx.test.filters.SmallTest;

import com.android.car.datasubscription.DataSubscription;
import com.android.car.ui.utils.CarUxRestrictionsUtil;
import com.android.systemui.SysuiTestCase;
import com.android.systemui.car.CarSystemUiTest;
import com.android.systemui.settings.UserTracker;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

import java.util.HashSet;
import java.util.concurrent.Executor;

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
    @Mock
    private ConnectivityManager mConnectivityManager;
    @Mock
    private PackageManager mPackageManager;
    @Mock
    private DataSubscriptionController.DataSubscriptionNetworkCallback mNetworkCallback;
    @Mock
    private Handler mHandler;
    @Mock
    private Executor mExecutor;
    @Mock
    private CarUxRestrictionsUtil mCarUxRestrictionsUtil;
    private MockitoSession mMockingSession;
    private ActivityManager.RunningTaskInfo mRunningTaskInfoMock;
    private DataSubscriptionController mController;

    @Before
    public void setUp() {
        mMockingSession = mockitoSession()
                .initMocks(this)
                .mockStatic(CarUxRestrictionsUtil.class)
                .strictness(Strictness.WARN)
                .startMocking();

        mContext = spy(mContext);
        when(mUserTracker.getUserHandle()).thenReturn(UserHandle.of(1000));
        mController = new DataSubscriptionController(mContext, mUserTracker, mHandler, mExecutor);
        mController.setSubscription(mDataSubscription);
        mController.setPopupWindow(mPopupWindow);
        mController.setConnectivityManager(mConnectivityManager);
        mRunningTaskInfoMock = new ActivityManager.RunningTaskInfo();
        mRunningTaskInfoMock.topActivity = new ComponentName("testPkgName", "testClassName");
        mRunningTaskInfoMock.taskId = 1;
        doReturn(mCarUxRestrictionsUtil).when(() -> CarUxRestrictionsUtil.getInstance(any()));
    }

    @After
    public void tearDown() {
        if (mMockingSession != null) {
            mMockingSession.finishMocking();
        }
    }

    @Test
    public void setAnchorView_viewNotNull_popUpDisplay() {
        when(mPopupWindow.isShowing()).thenReturn(false);
        when(mDataSubscription.isDataSubscriptionInactive()).thenReturn(true);

        mController.setAnchorView(mAnchorView);

        verify(mDataSubscription).addDataSubscriptionListener(any());
        verify(mCarUxRestrictionsUtil).register(any());
        verify(mAnchorView).post(any());
    }

    @Test
    public void setAnchorView_viewNull_popUpNotDisplay() {
        when(mPopupWindow.isShowing()).thenReturn(false);
        when(mDataSubscription.isDataSubscriptionInactive()).thenReturn(true);
        mController.setIsUxRestrictionsListenerRegistered(true);
        mController.setIsDataSubscriptionListenerRegistered(true);

        mController.setAnchorView(null);

        verify(mDataSubscription).removeDataSubscriptionListener();
        verify(mCarUxRestrictionsUtil).unregister(any());
        verify(mAnchorView, never()).post(any());
    }

    public void onTaskMovedToFront_TopPackageBlocked_popUpNotDisplay() throws RemoteException {
        HashSet<String> packagesBlocklist = new HashSet<>();
        packagesBlocklist.add(mRunningTaskInfoMock.topActivity.getPackageName());
        mController.setPackagesBlocklist(packagesBlocklist);

        mController.getTaskStackListener().onTaskMovedToFront(mRunningTaskInfoMock);

        Assert.assertFalse(mController.getShouldDisplayReactiveMsg());
    }

    @Test
    public void onTaskMovedToFront_TopActivityBlocked_popUpNotDisplay() throws RemoteException {
        HashSet<String> activitiesBlocklist = new HashSet<>();
        activitiesBlocklist.add(mRunningTaskInfoMock.topActivity.flattenToString());
        mController.setActivitiesBlocklist(activitiesBlocklist);

        mController.getTaskStackListener().onTaskMovedToFront(mRunningTaskInfoMock);

        Assert.assertFalse(mController.getShouldDisplayReactiveMsg());
    }

    @Test
    public void onTaskMovedToFront_AppNotRequireInternet_popUpNotDisplay()
            throws RemoteException, PackageManager.NameNotFoundException {
        PackageInfo packageInfo = new PackageInfo();
        when(mUserTracker.getUserId()).thenReturn(1000);
        when(mContext.getPackageManager()).thenReturn(mPackageManager);
        when(mPackageManager.getPackageInfoAsUser(
                anyString(), anyInt(), anyInt())).thenReturn(packageInfo);

        mController.getTaskStackListener().onTaskMovedToFront(mRunningTaskInfoMock);

        Assert.assertFalse(mController.getShouldDisplayReactiveMsg());
    }

    @Test
    public void onTaskMovedToFront_AppRequiresInternetAndNotBlocked_registerCallback()
            throws RemoteException, PackageManager.NameNotFoundException {
        PackageInfo packageInfo = new PackageInfo();
        ApplicationInfo appInfo = new ApplicationInfo();
        appInfo.uid = 1000;
        packageInfo.requestedPermissions = new String[] {ACCESS_NETWORK_STATE, INTERNET};
        when(mUserTracker.getUserId()).thenReturn(1000);
        when(mContext.getPackageManager()).thenReturn(mPackageManager);
        when(mPackageManager.getPackageInfoAsUser(
                anyString(), anyInt(), anyInt())).thenReturn(packageInfo);
        when(mPackageManager.getApplicationInfoAsUser(
                anyString(), anyInt(), anyInt())).thenReturn(appInfo);

        mController.getTaskStackListener().onTaskMovedToFront(mRunningTaskInfoMock);

        verify(mConnectivityManager).registerDefaultNetworkCallbackForUid(anyInt(), any(), any());
    }

    @Test
    public void onTaskMovedToFront_invalidNetCap_popUpDisplay()
            throws RemoteException, PackageManager.NameNotFoundException {
        PackageInfo packageInfo = new PackageInfo();
        ApplicationInfo appInfo = new ApplicationInfo();
        appInfo.uid = 1000;
        packageInfo.requestedPermissions = new String[] {ACCESS_NETWORK_STATE, INTERNET};
        mController.setNetworkCallback(mNetworkCallback);

        when(mUserTracker.getUserId()).thenReturn(1000);
        when(mContext.getPackageManager()).thenReturn(mPackageManager);
        when(mPackageManager.getPackageInfoAsUser(
                anyString(), anyInt(), anyInt())).thenReturn(packageInfo);
        when(mPackageManager.getApplicationInfoAsUser(
                anyString(), anyInt(), anyInt())).thenReturn(appInfo);
        when(mNetworkCallback.isNetworkAvailable()).thenReturn(true);
        mController.getTaskStackListener().onTaskMovedToFront(mRunningTaskInfoMock);

        Assert.assertFalse(mController.getShouldDisplayReactiveMsg());
    }

    @Test
    public void onTaskMovedToFront_callbackRegistered_unregisterAndRegisterCallback()
            throws RemoteException, PackageManager.NameNotFoundException {
        PackageInfo packageInfo = new PackageInfo();
        ApplicationInfo appInfo = new ApplicationInfo();
        appInfo.uid = 1000;
        packageInfo.requestedPermissions = new String[] {ACCESS_NETWORK_STATE, INTERNET};
        mController.setIsCallbackRegistered(true);
        when(mUserTracker.getUserId()).thenReturn(1000);
        when(mContext.getPackageManager()).thenReturn(mPackageManager);
        when(mPackageManager.getPackageInfoAsUser(
                anyString(), anyInt(), anyInt())).thenReturn(packageInfo);
        when(mPackageManager.getApplicationInfoAsUser(
                anyString(), anyInt(), anyInt())).thenReturn(appInfo);

        mController.getTaskStackListener().onTaskMovedToFront(mRunningTaskInfoMock);

        verify(mConnectivityManager).unregisterNetworkCallback(
                (ConnectivityManager.NetworkCallback) any());
        verify(mConnectivityManager).registerDefaultNetworkCallbackForUid(anyInt(), any(), any());
    }

    @Test
    public void onRestrictionsChanged_optimizationRequired_proactiveMsgDismissed() {
        doReturn(mCarUxRestrictionsUtil).when(() -> CarUxRestrictionsUtil.getInstance(any()));

        when(mPopupWindow.isShowing()).thenReturn(true);
        ArgumentCaptor<CarUxRestrictionsUtil.OnUxRestrictionsChangedListener> captor =
                ArgumentCaptor.forClass(
                        CarUxRestrictionsUtil.OnUxRestrictionsChangedListener.class);
        mController.setAnchorView(mAnchorView);
        verify(mCarUxRestrictionsUtil).register(captor.capture());
        CarUxRestrictionsUtil.OnUxRestrictionsChangedListener listener = captor.getValue();
        CarUxRestrictions carUxRestrictions = mock(CarUxRestrictions.class);
        when(carUxRestrictions.isRequiresDistractionOptimization()).thenReturn(true);
        mController.setIsProactiveMsg(true);

        listener.onRestrictionsChanged(carUxRestrictions);

        verify(mPopupWindow).dismiss();
    }

    @Test
    public void onRestrictionsChanged_optimizationRequired_buttonDismissedInReactiveMsg() {
        doReturn(mCarUxRestrictionsUtil).when(() -> CarUxRestrictionsUtil.getInstance(any()));

        when(mPopupWindow.isShowing()).thenReturn(true);
        ArgumentCaptor<CarUxRestrictionsUtil.OnUxRestrictionsChangedListener> captor =
                ArgumentCaptor.forClass(
                        CarUxRestrictionsUtil.OnUxRestrictionsChangedListener.class);
        mController.setAnchorView(mAnchorView);
        verify(mCarUxRestrictionsUtil).register(captor.capture());
        CarUxRestrictionsUtil.OnUxRestrictionsChangedListener listener = captor.getValue();
        CarUxRestrictions carUxRestrictions = mock(CarUxRestrictions.class);
        when(carUxRestrictions.isRequiresDistractionOptimization()).thenReturn(true);

        Button button = mock(Button.class);
        mController.setExplorationButton(button);
        mController.setIsProactiveMsg(false);

        listener.onRestrictionsChanged(carUxRestrictions);

        verify(button).setVisibility(anyInt());
    }
}
