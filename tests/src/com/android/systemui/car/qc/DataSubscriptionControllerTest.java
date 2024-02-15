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

import static com.android.dx.mockito.inline.extended.ExtendedMockito.mockitoSession;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.ActivityManager;
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
import org.junit.Test;
import org.junit.runner.RunWith;
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
        mController.setSubscriptionStatus(DataSubscriptionStatus.INACTIVE);

        mController.setAnchorView(mAnchorView);

        verify(mDataSubscription).addDataSubscriptionListener(any());
        verify(mAnchorView).post(any());
    }

    @Test
    public void setAnchorView_viewNull_popUpNotDisplay() {
        when(mPopupWindow.isShowing()).thenReturn(false);
        mController.setSubscriptionStatus(DataSubscriptionStatus.INACTIVE);
        mController.setIsDataSubscriptionListenerRegistered(true);

        mController.setAnchorView(null);

        verify(mDataSubscription).removeDataSubscriptionListener();
        verify(mAnchorView, never()).post(any());
    }

    @Test
    public void dataSubscriptionChange_statusInactive_popUpDisplay() {
        DataSubscription.DataSubscriptionChangeListener listener =
                mController.getDataSubscriptionChangeListener();

        listener.onChange(DataSubscriptionStatus.PAID);
        listener.onChange(DataSubscriptionStatus.INACTIVE);

        Assert.assertTrue(mController.getShouldDisplayProactiveMsg());
    }

    @Test
    public void dataSubscriptionChange_statusPaid_popUpNotDisplay() {
        DataSubscription.DataSubscriptionChangeListener listener =
                mController.getDataSubscriptionChangeListener();

        listener.onChange(DataSubscriptionStatus.INACTIVE);
        listener.onChange(DataSubscriptionStatus.PAID);

        Assert.assertFalse(mController.getShouldDisplayProactiveMsg());
    }

    @Test
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
}
