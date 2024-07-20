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

package com.android.systemui.car.systembar;

import android.content.Context;
import android.view.View;

import androidx.annotation.VisibleForTesting;

import com.android.car.datasubscription.DataSubscription;

import javax.inject.Inject;

/**
 * Controller to display the unseen icon for signal status icon
 */

public class DataSubscriptionUnseenIconController
        implements DataSubscription.DataSubscriptionChangeListener{
    private DataSubscription mSubscription;
    private View mView;
    private boolean mIsListenerRegistered;

    @Inject
    DataSubscriptionUnseenIconController(Context context) {
        mSubscription = new DataSubscription(context);
    }


    @Override
    public void onChange(int value) {
        updateShouldDisplayUnseenIcon();
    }

    /**
     * update unseen icon's visibility based on data subscription status
     */
    public void updateShouldDisplayUnseenIcon() {
        mView.post(() -> {
            if (mSubscription.isDataSubscriptionInactive()) {
                mView.setVisibility(View.VISIBLE);
            } else {
                mView.setVisibility(View.GONE);
            }
        });
    }

    /**
     * Set unseen icon on a view
     */
    public void setUnseenIcon(View view) {
        mView = view;
        if (mView != null && !mIsListenerRegistered) {
            mSubscription.addDataSubscriptionListener(this);
            mIsListenerRegistered = true;
            updateShouldDisplayUnseenIcon();
        }
    }

    /**
     * Unregister active listeners
     */
    public void unregisterListener() {
        if (mIsListenerRegistered) {
            mSubscription.removeDataSubscriptionListener();
            mIsListenerRegistered = false;
        }
    }


    @VisibleForTesting
    void setSubscription(DataSubscription subscription) {
        mSubscription = subscription;
    }

    @VisibleForTesting
    void setIsListenerRegistered(boolean value) {
        mIsListenerRegistered = value;
    }
}
