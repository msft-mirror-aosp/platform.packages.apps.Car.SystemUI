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

package com.android.systemui.car.userswitcher;

import android.car.user.CarUserManager;
import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import com.android.systemui.car.CarServiceProvider;
import com.android.systemui.car.users.CarSystemUIUserUtil;
import com.android.systemui.car.window.OverlayViewMediator;
import com.android.systemui.settings.UserTracker;

import javax.inject.Inject;

/**
 * Registers listeners that subscribe to events that show or hide CarUserSwitchingDialog that is
 * mounted to SystemUiOverlayWindow.
 */
public class UserSwitchTransitionViewMediator implements OverlayViewMediator,
        CarUserManager.UserSwitchUiCallback {
    private static final String TAG = "UserSwitchTransitionViewMediator";

    private final Context mContext;
    private final CarServiceProvider mCarServiceProvider;
    private final UserTracker mUserTracker;
    private final UserSwitchTransitionViewController mUserSwitchTransitionViewController;

    @VisibleForTesting
    final UserTracker.Callback mUserChangedCallback = new UserTracker.Callback() {
        @Override
        public void onBeforeUserSwitching(int newUser) {
            mUserSwitchTransitionViewController.handleShow(newUser);
        }

        @Override
        public void onUserChanging(int newUser, @NonNull Context userContext) {
            mUserSwitchTransitionViewController.handleSwitching(newUser);
        }

        @Override
        public void onUserChanged(int newUser, @NonNull Context userContext) {
            mUserSwitchTransitionViewController.handleHide();
        }
    };

    @Inject
    public UserSwitchTransitionViewMediator(
            Context context,
            CarServiceProvider carServiceProvider,
            UserTracker userTracker,
            UserSwitchTransitionViewController userSwitchTransitionViewController) {
        mContext = context;
        mCarServiceProvider = carServiceProvider;
        mUserTracker = userTracker;
        mUserSwitchTransitionViewController = userSwitchTransitionViewController;
    }

    @Override
    public void registerListeners() {
        if (!CarSystemUIUserUtil.isSecondaryMUMDSystemUI()) {
            // TODO(b/335664913): allow for callback from non-system user (and per user).
            mCarServiceProvider.addListener(car -> {
                CarUserManager carUserManager = car.getCarManager(CarUserManager.class);

                if (carUserManager != null) {
                    carUserManager.setUserSwitchUiCallback(this);
                } else {
                    Log.e(TAG, "registerListeners: CarUserManager could not be obtained.");
                }
            });
        }

        mUserTracker.addCallback(mUserChangedCallback, mContext.getMainExecutor());
    }

    @Override
    public void setUpOverlayContentViewControllers() {
        // no-op.
    }

    @Override
    public void showUserSwitchDialog(int userId) {
        mUserSwitchTransitionViewController.handleShow(userId);
    }
}
