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

import android.view.View;

import androidx.annotation.VisibleForTesting;

import com.android.car.datasubscription.DataSubscription;
import com.android.car.datasubscription.DataSubscriptionStatus;
import com.android.car.datasubscription.Flags;
import com.android.systemui.car.systembar.element.CarSystemBarElementController;
import com.android.systemui.car.systembar.element.CarSystemBarElementStateController;
import com.android.systemui.car.systembar.element.CarSystemBarElementStatusBarDisableController;
import com.android.systemui.car.systembar.element.layout.CarSystemBarImageView;

import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;
import dagger.assisted.AssistedInject;

/**
 * Controller to display the unseen icon for signal status icon
 */
public class DataSubscriptionUnseenIconController extends
        CarSystemBarElementController<CarSystemBarImageView> {
    private DataSubscription mSubscription;
    private int mSubscriptionStatus;
    private final DataSubscription.DataSubscriptionChangeListener mDataSubscriptionChangeListener =
            value -> {
                mSubscriptionStatus = value;
                updateShouldDisplayUnseenIcon();
            };

    @AssistedInject
    DataSubscriptionUnseenIconController(@Assisted CarSystemBarImageView view,
            CarSystemBarElementStatusBarDisableController disableController,
            CarSystemBarElementStateController stateController) {
        super(view, disableController, stateController);
        mSubscription = new DataSubscription(mView.getContext());
    }

    @AssistedFactory
    public interface Factory extends
            CarSystemBarElementController.Factory<CarSystemBarImageView,
                    DataSubscriptionUnseenIconController> {
    }

    @Override
    protected void onViewAttached() {
        super.onViewAttached();
        if (Flags.dataSubscriptionPopUp()) {
            mSubscriptionStatus = mSubscription.getDataSubscriptionStatus();
            if (mSubscriptionStatus != DataSubscriptionStatus.PAID) {
                mView.setVisibility(View.VISIBLE);
            }
            mSubscription.addDataSubscriptionListener(mDataSubscriptionChangeListener);
        }
    }

    @Override
    protected void onViewDetached() {
        super.onViewDetached();
        if (Flags.dataSubscriptionPopUp()) {
            mSubscription.removeDataSubscriptionListener();
        }
    }


    /**
     * update unseen icon's visibility based on data subscription status
     */
    public void updateShouldDisplayUnseenIcon() {
        mView.post(() -> {
            if (mSubscriptionStatus != DataSubscriptionStatus.PAID) {
                mView.setVisibility(View.VISIBLE);
            } else {
                mView.setVisibility(View.GONE);
            }
        });
    }


    @VisibleForTesting
    void setSubscription(DataSubscription subscription) {
        mSubscription = subscription;
    }

    @VisibleForTesting
    DataSubscription.DataSubscriptionChangeListener getDataSubscriptionChangeListener() {
        return mDataSubscriptionChangeListener;
    }

    @VisibleForTesting
    @DataSubscriptionStatus int getSubscriptionStatus() {
        return mSubscriptionStatus;
    }
}
