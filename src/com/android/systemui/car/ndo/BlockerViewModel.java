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

import android.util.Slog;

import androidx.lifecycle.ViewModel;

import com.android.car.telephony.calling.InCallServiceManager;
import com.android.systemui.car.telecom.InCallServiceImpl;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import javax.inject.Inject;

/**
 * ViewModel for blocking activities. Provides livedatas that listen to media and call state.
 */
public class BlockerViewModel extends ViewModel implements PropertyChangeListener {
    private static final String TAG = "SysUi.BlockerViewModel";
    private static final String PROPERTY_IN_CALL_SERVICE = "PROPERTY_IN_CALL_SERVICE";

    private InCallLiveData mInCallLiveData;
    private final InCallServiceManager mServiceManager;

    @Inject
    public BlockerViewModel(InCallServiceManager serviceManager) {
        mServiceManager = serviceManager;
    }

    /** Initialize data sources **/
    public void initialize(String blockedActivity) {
        mInCallLiveData = new InCallLiveData(mServiceManager, blockedActivity);

        // Listens to the call manager for when the inCallService is started after ABA.
        mServiceManager.addObserver(this);
        if (mServiceManager.getInCallService() != null) {
            onInCallServiceConnected();
        }
    }

    /**
     * Returns the livedata that provides the first call of the currently blocked calling activity.
     */
    public InCallLiveData getInCallLiveData() {
        return mInCallLiveData;
    }

    /**
     * {@link InCallServiceManager} will call this method to let it know the InCallService has been
     * added.
     */
    @Override
    public void propertyChange(PropertyChangeEvent propertyChangeEvent) {
        if (PROPERTY_IN_CALL_SERVICE.equals(propertyChangeEvent.getPropertyName())
                && mServiceManager.getInCallService() != null) {
            onInCallServiceConnected();
        }
    }

    @Override
    public void onCleared() {
        InCallServiceImpl inCallService = (InCallServiceImpl) mServiceManager.getInCallService();
        if (inCallService != null) {
            inCallService.removeListener(mInCallLiveData);
        }
        mServiceManager.removeObserver(this);
    }

    private void onInCallServiceConnected() {
        Slog.d(TAG, "inCallService Connected");
        InCallServiceImpl inCallService = (InCallServiceImpl) mServiceManager.getInCallService();
        inCallService.addListener(mInCallLiveData);
    }
}
