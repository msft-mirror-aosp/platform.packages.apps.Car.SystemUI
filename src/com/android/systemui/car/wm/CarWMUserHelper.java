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
package com.android.systemui.car.wm;

import android.annotation.UserIdInt;
import android.app.ActivityManager;
import android.car.CarOccupantZoneManager;
import android.util.ArraySet;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.systemui.car.CarServiceProvider;
import com.android.systemui.car.users.CarSystemUIUserUtil;
import com.android.wm.shell.dagger.WMSingleton;

import java.util.Set;

import javax.inject.Inject;

/**
 * Helper class for multi user related queries in the WM scope.
 */
@WMSingleton
public class CarWMUserHelper {
    private final CarServiceProvider mCarServiceProvider;
    private final CarServiceProvider.CarServiceOnConnectedListener mCarServiceLifecycleListener;
    private final boolean mIsMUMDSystem;
    @GuardedBy("mListeners")
    private final Set<OccupantZoneChangeListener> mListeners = new ArraySet<>();
    private CarOccupantZoneManager mCarOccupantZoneManager;
    private boolean mIsOccupantListenerRegistered = false;
    private final CarOccupantZoneManager.OccupantZoneConfigChangeListener
            mOccupantZoneConfigChangeListener = changeFlags -> {
                if ((changeFlags & (CarOccupantZoneManager.ZONE_CONFIG_CHANGE_FLAG_DISPLAY
                        | CarOccupantZoneManager.ZONE_CONFIG_CHANGE_FLAG_USER)) != 0) {
                    notifyOccupantZonesChanged();
                }
            };

    @Inject
    public CarWMUserHelper(CarServiceProvider carServiceProvider) {
        this(carServiceProvider, CarSystemUIUserUtil.isMUMDSystemUI());
    }

    @VisibleForTesting
    CarWMUserHelper(CarServiceProvider carServiceProvider, boolean isMUMDSystem) {
        mCarServiceProvider = carServiceProvider;
        mIsMUMDSystem = isMUMDSystem;
        mCarServiceLifecycleListener = car -> {
            mCarOccupantZoneManager = car.getCarManager(CarOccupantZoneManager.class);
            synchronized (mListeners) {
                if (!mListeners.isEmpty() && mCarOccupantZoneManager != null) {
                    mCarOccupantZoneManager.registerOccupantZoneConfigChangeListener(
                            mOccupantZoneConfigChangeListener);
                    mIsOccupantListenerRegistered = true;
                }
            }
        };
        if (mIsMUMDSystem) {
            mCarServiceProvider.addListener(mCarServiceLifecycleListener);
        }
    }

    /**
     * Get the userId currently assigned to a particular display. If this is a non-MUMD system,
     * always return the current foreground user.
     */
    @UserIdInt
    public int getUserIdForDisplay(int displayId) {
        if (!mIsMUMDSystem) {
            return ActivityManager.getCurrentUser();
        }
        if (mCarOccupantZoneManager == null) {
            return CarOccupantZoneManager.INVALID_USER_ID;
        }
        return mCarOccupantZoneManager.getUserForDisplayId(displayId);
    }

    /**
     * Add a listener for occupant zone changes relating to users and displays.
     */
    public void addOccupantZoneChangeListener(OccupantZoneChangeListener listener) {
        synchronized (mListeners) {
            if (mListeners.isEmpty() && mCarOccupantZoneManager != null
                    && !mIsOccupantListenerRegistered) {
                mCarOccupantZoneManager.registerOccupantZoneConfigChangeListener(
                        mOccupantZoneConfigChangeListener);
                mIsOccupantListenerRegistered = true;
            }
            mListeners.add(listener);
        }
    }

    /**
     * Remove occupant zone change listener from receiving updates.
     */
    public void removeOccupantZoneChangeListener(OccupantZoneChangeListener listener) {
        synchronized (mListeners) {
            mListeners.remove(listener);
            if (mListeners.isEmpty() && mCarOccupantZoneManager != null
                    && mIsOccupantListenerRegistered) {
                mCarOccupantZoneManager.unregisterOccupantZoneConfigChangeListener(
                        mOccupantZoneConfigChangeListener);
                mIsOccupantListenerRegistered = false;
            }
        }
    }

    private void notifyOccupantZonesChanged() {
        synchronized (mListeners) {
            mListeners.forEach(OccupantZoneChangeListener::onOccupantZonesChanged);
        }
    }

    public interface OccupantZoneChangeListener {
        /**
         * One or more occupant zone assignments has changed.
         */
        void onOccupantZonesChanged();
    }
}
