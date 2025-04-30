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
package com.android.systemui.car.wm.scalableui.systemevents;

import static android.car.user.CarUserManager.USER_LIFECYCLE_EVENT_TYPE_UNLOCKED;
import static android.content.res.Configuration.ORIENTATION_LANDSCAPE;
import static android.content.res.Configuration.ORIENTATION_PORTRAIT;

import static com.android.systemui.car.wm.scalableui.systemevents.SystemEventConstants.SYSTEM_ENTER_SUW_EVENT_ID;
import static com.android.systemui.car.wm.scalableui.systemevents.SystemEventConstants.SYSTEM_EXIST_SUW_EVENT_ID;

import android.car.user.CarUserManager;
import android.content.Context;
import android.content.res.TypedArray;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;

import com.android.car.scalableui.loader.xml.XmlModelLoader;
import com.android.car.scalableui.manager.StateManager;
import com.android.car.scalableui.model.PanelState;
import com.android.systemui.CoreStartable;
import com.android.systemui.R;
import com.android.systemui.car.CarDeviceProvisionedController;
import com.android.systemui.car.CarDeviceProvisionedListener;
import com.android.systemui.car.CarServiceProvider;
import com.android.systemui.car.wm.scalableui.EventDispatcher;
import com.android.systemui.dagger.SysUISingleton;
import com.android.systemui.dagger.qualifiers.Background;
import com.android.systemui.settings.UserTracker;
import com.android.systemui.statusbar.policy.ConfigurationController;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

import javax.inject.Inject;

/**
 * A system event handler that listens for user lifecycle events and device provisioning state
 * changes.
 *
 * <p>This class dispatches events to the {@link StateManager} when a user is unlocked or when
 * the device
 * is being set up.
 */
@SysUISingleton
public class SystemEventHandler implements CoreStartable,
        ConfigurationController.ConfigurationListener {
    private static final String TAG = SystemEventHandler.class.getSimpleName();
    private static final boolean DEBUG = Build.IS_DEBUGGABLE;

    private final Context mContext;
    private final CarServiceProvider mCarServiceProvider;
    private final UserTracker mUserTracker;
    private final Executor mBackgroundExecutor;
    private final CarDeviceProvisionedController mCarDeviceProvisionedController;
    private final EventDispatcher mEventDispatcher;

    private CarUserManager mCarUserManager;
    private boolean mIsUserSetupInProgress;

    private int mCurrentOrientation;

    private final CarUserManager.UserLifecycleListener mUserLifecycleListener =
            new CarUserManager.UserLifecycleListener() {
                @Override
                public void onEvent(@NonNull CarUserManager.UserLifecycleEvent event) {
                    if (DEBUG) {
                        Log.d(TAG, "on User event = " + event + ", mIsUserSetupInProgress="
                                + mIsUserSetupInProgress);
                    }
                    if (mIsUserSetupInProgress) {
                        mEventDispatcher.executeTransaction(SYSTEM_ENTER_SUW_EVENT_ID);
                        return;
                    }
                    if (event.getUserHandle().isSystem()) {
                        return;
                    }

                    if (event.getEventType() == USER_LIFECYCLE_EVENT_TYPE_UNLOCKED) {
                        if (event.getUserId() == mUserTracker.getUserId()) {
                            StateManager.handlePanelReset();
                        }
                    }
                }
            };

    private final CarDeviceProvisionedListener mCarDeviceProvisionedListener =
            new CarDeviceProvisionedListener() {
                @Override
                public void onUserSetupInProgressChanged() {
                    updateUserSetupState();
                }
            };

    @Inject
    public SystemEventHandler(
            Context context,
            @Background Executor bgExecutor,
            CarServiceProvider carServiceProvider,
            UserTracker userTracker,
            CarDeviceProvisionedController carDeviceProvisionedController,
            EventDispatcher dispatcher
    ) {
        mContext = context;
        mBackgroundExecutor = bgExecutor;
        mCarServiceProvider = carServiceProvider;
        mUserTracker = userTracker;
        mCarDeviceProvisionedController = carDeviceProvisionedController;
        mEventDispatcher = dispatcher;
        mIsUserSetupInProgress = mCarDeviceProvisionedController.isCurrentUserSetupInProgress();
        mCurrentOrientation = mContext.getResources().getConfiguration().orientation;
    }

    private void updateUserSetupState() {
        boolean isUserSetupInProgress =
                mCarDeviceProvisionedController.isCurrentUserSetupInProgress();
        if (isUserSetupInProgress != mIsUserSetupInProgress) {
            mIsUserSetupInProgress = isUserSetupInProgress;
            if (mIsUserSetupInProgress) {
                mEventDispatcher.executeTransaction(SYSTEM_ENTER_SUW_EVENT_ID);
            } else {
                mEventDispatcher.executeTransaction(SYSTEM_EXIST_SUW_EVENT_ID);
            }
        }
    }

    @Override
    public void start() {
        if (isScalableUIEnabled()) {
            registerUserEventListener();
            registerProvisionedStateListener();
        }
    }

    @Override
    public void onOrientationChanged(int orientation) {
        if (mCurrentOrientation != orientation && (ORIENTATION_LANDSCAPE == orientation
                || ORIENTATION_PORTRAIT == orientation)) {
            mCurrentOrientation = orientation;
            TypedArray states = mContext.getResources().obtainTypedArray(R.array.window_states);
            List<PanelState> panelStateList = new ArrayList<>();
            for (int i = 0; i < states.length(); i++) {
                int xmlResId = states.getResourceId(i, 0);
                XmlModelLoader loader = new XmlModelLoader(mContext);
                PanelState panelState = loader.createPanelState(xmlResId);
                panelStateList.add(panelState);
            }
            StateManager.reloadPanelState(panelStateList);
        }
    }


    private void registerProvisionedStateListener() {
        mCarDeviceProvisionedController.addCallback(mCarDeviceProvisionedListener);
    }

    private void registerUserEventListener() {
        mCarServiceProvider.addListener(car -> {
            mCarUserManager = car.getCarManager(CarUserManager.class);
            if (mCarUserManager != null) {
                mCarUserManager.addListener(mBackgroundExecutor, mUserLifecycleListener);
            }
        });
    }

    private boolean isScalableUIEnabled() {
        return mContext.getResources().getBoolean(R.bool.config_enableScalableUI);
    }
}
