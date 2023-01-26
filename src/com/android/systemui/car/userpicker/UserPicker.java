/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.systemui.car.userpicker;

import android.os.UserHandle;
import android.os.UserManager;
import android.util.Slog;

import androidx.annotation.NonNull;

import com.android.systemui.CoreStartable;
import com.android.systemui.dagger.SysUISingleton;

import java.io.PrintWriter;

import javax.inject.Inject;

/**
 * {@link CoreStartable} for user picker.
 * It has a member of {@link UserPickerComponent}, and creates {@link UserPickerActivityComponent}.
 * Builder using it, and also provides {@link UserPickerActivity} with injection method.
 *
 */
@SysUISingleton
public class UserPicker implements CoreStartable {
    private static final String TAG = UserPicker.class.getSimpleName();

    private UserPickerComponent mUserPickerComponent;
    private boolean mEnabled;

    @Inject
    public UserPicker(UserPickerComponent.Builder userPickerComponentBuilder) {
        mUserPickerComponent = userPickerComponentBuilder.build();
    }

    @Override
    public void start() {
        if (UserHandle.myUserId() != UserHandle.USER_SYSTEM
                && UserManager.isHeadlessSystemUserMode()) {
            Slog.i(TAG, "Disable UserPicker for non system user "
                    + UserHandle.myUserId());
            return;
        }

        mUserPickerComponent.getUserEventManager();
        mEnabled = true;
    }

    // TODO<b/254526109>: implements in phase 2
    @Override
    public void dump(@NonNull PrintWriter pw, @NonNull String[] args) {
        if (!mEnabled) {
            return;
        }
    }

    /**
     * Injects dependencies for {@link UserPickerActivity} and returns
     * {@link UserPickerActivityComponent} to keep the user picker activity scope.
     *
     * @param userPickerActivity dependencies to be injected
     * @return UserPickerActivityComponent to keep the user picker activity scope in
     * UserPickerActivity.
     */
    public UserPickerActivityComponent inject(UserPickerActivity userPickerActivity) {
        if (!mEnabled) {
            return null;
        }

        UserPickerActivityComponent activityComponent = mUserPickerComponent
                .getUserPickerActivityComponentBuilder().build();
        activityComponent.inject(userPickerActivity);
        return activityComponent;
    }
}
