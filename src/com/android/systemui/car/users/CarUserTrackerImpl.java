/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.systemui.car.users;

import android.content.Context;
import android.os.Handler;
import android.os.UserManager;

import com.android.systemui.dump.DumpManager;
import com.android.systemui.settings.UserTrackerImpl;

/**
 * Custom user tracking class extended from {@link UserTrackerImpl} which defines custom behavior
 * when CarSystemUI is running as a secondary user on a multi-display device.
 *
 */
public class CarUserTrackerImpl extends UserTrackerImpl {
    private final boolean mIsSecondaryUserSystemUI;

    public CarUserTrackerImpl(Context context, UserManager userManager, DumpManager dumpManager,
            Handler backgroundHandler, boolean isSecondaryUserSystemUI) {
        super(context, userManager, dumpManager, backgroundHandler);
        mIsSecondaryUserSystemUI = isSecondaryUserSystemUI;
    }

    @Override
    protected void handleSwitchUser(int user) {
        if (mIsSecondaryUserSystemUI) {
            // Secondary user SystemUI instances are not running on foreground users, so they should
            // not be impacted by foreground user switches.
            return;
        }
        super.handleSwitchUser(user);
    }

    @Override
    protected void handleProfilesChanged() {
        if (mIsSecondaryUserSystemUI) {
            // Profile changes are only sent for the primary user, so they should be ignored by
            // secondary users.
            return;
        }
        super.handleProfilesChanged();
    }
}
