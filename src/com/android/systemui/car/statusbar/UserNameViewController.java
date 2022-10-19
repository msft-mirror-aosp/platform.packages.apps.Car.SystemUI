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

package com.android.systemui.car.statusbar;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.UserInfo;
import android.os.UserHandle;
import android.os.UserManager;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.MainThread;

import com.android.systemui.R;
import com.android.systemui.broadcast.BroadcastDispatcher;
import com.android.systemui.dagger.SysUISingleton;
import com.android.systemui.settings.UserTracker;

import javax.inject.Inject;

/**
 * Controls a TextView with the current driver's username
 */
@SysUISingleton
public class UserNameViewController {
    private static final String TAG = "UserNameViewController";

    private Context mContext;
    private UserTracker mUserTracker;
    private UserManager mUserManager;
    private BroadcastDispatcher mBroadcastDispatcher;
    private TextView mUserNameView;

    private final BroadcastReceiver mUserUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateUser(mUserTracker.getUserId());
        }
    };

    private boolean mUserLifecycleListenerRegistered = false;

    private final UserTracker.Callback mUserChangedCallback =
            new UserTracker.Callback() {
                @Override
                public void onUserChanged(int newUser, Context userContext) {
                    updateUser(newUser);
                }
            };

    @Inject
    public UserNameViewController(Context context, UserTracker userTracker,
            UserManager userManager, BroadcastDispatcher broadcastDispatcher) {
        mContext = context;
        mUserTracker = userTracker;
        mUserManager = userManager;
        mBroadcastDispatcher = broadcastDispatcher;
    }

    /**
     * Find the {@link TextView} for the driver's user name from a view and if found set it with the
     * current driver's user name.
     */
     @MainThread
    public void addUserNameView(View v) {
        TextView userNameView = v.findViewById(R.id.user_name_text);
        if (userNameView != null) {
            if (mUserNameView == null) {
                registerForUserChangeEvents();
            }
            mUserNameView = userNameView;
            updateUser(mUserTracker.getUserId());
        }
    }

    /**
     * Clean up the controller and unregister receiver.
     */
    public void removeAll() {
        mUserNameView = null;
        if (mUserLifecycleListenerRegistered) {
            mBroadcastDispatcher.unregisterReceiver(mUserUpdateReceiver);
            mUserTracker.removeCallback(mUserChangedCallback);
            mUserLifecycleListenerRegistered = false;
        }
    }

    private void registerForUserChangeEvents() {
        // Register for user switching
        if (!mUserLifecycleListenerRegistered) {
            mUserTracker.addCallback(mUserChangedCallback, mContext.getMainExecutor());
            mUserLifecycleListenerRegistered = true;
        }
        // Also register for user info changing
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_USER_INFO_CHANGED);
        mBroadcastDispatcher.registerReceiver(mUserUpdateReceiver, filter, /* executor= */ null,
                UserHandle.ALL);
    }

    private void updateUser(int userId) {
        if (mUserNameView != null) {
            UserInfo currentUserInfo = mUserManager.getUserInfo(userId);
            mUserNameView.setText(currentUserInfo.name);
        }
    }
}
