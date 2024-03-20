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
package com.android.systemui.car.telecom;

import android.telecom.Call;
import android.telecom.InCallService;
import android.util.Log;

import com.android.car.telephony.calling.InCallServiceManager;

import java.util.ArrayList;

import javax.inject.Inject;

/**
 * Implementation of {@link InCallService}, an {@link android.telecom} service which must be
 * implemented by an app that wishes to provide functionality for managing phone calls. This service
 * is bound by android telecom.
 */
public class InCallServiceImpl extends InCallService {
    private static final String TAG = "SysUI.InCallServiceImpl";
    private static final boolean DEBUG = false;

    private final InCallServiceManager mServiceManager;
    private final ArrayList<InCallListener> mInCallListeners = new ArrayList<>();

    @Inject
    public InCallServiceImpl(InCallServiceManager serviceManager) {
        mServiceManager = serviceManager;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        if (DEBUG) Log.d(TAG, "onCreate service");
        mServiceManager.setInCallService(this);
    }

    @Override
    public void onDestroy() {
        if (DEBUG) Log.d(TAG, "onDestroy service");
        mServiceManager.setInCallService(null);
        super.onDestroy();
    }

    @Override
    public void onCallAdded(Call call) {
        if (DEBUG) Log.d(TAG, "onCallAdded: " + call);
        for (InCallListener listener : mInCallListeners) {
            listener.onCallAdded(call);
        }
    }

    @Override
    public void onCallRemoved(Call call) {
        if (DEBUG) Log.d(TAG, "onCallRemoved: " + call);
        for (InCallListener listener : mInCallListeners) {
            listener.onCallRemoved(call);
        }
    }

    /**
     * Adds a listener for {@link InCallService} events
     */
    public void addListener(InCallListener listener) {
        mInCallListeners.add(listener);
    }

    /**
     * Removes a listener for {@link InCallService} events
     */
    public void removeListener(InCallListener listener) {
        if (!mInCallListeners.isEmpty()) mInCallListeners.remove(listener);
    }

    /**
     * Listens for {@link #onCallAdded(Call)} and {@link #onCallRemoved(Call)} events
     */
    public interface InCallListener {
        /**
         * Called when a {@link Call} has been added to this in-call session, generally indicating
         * that the call has been received.
         */
        void onCallAdded(Call call);

        /**
         * Called when a {@link Call} has been removed from this in-call session, generally
         * indicating that the call has ended.
         */
        void onCallRemoved(Call call);
    }
}
