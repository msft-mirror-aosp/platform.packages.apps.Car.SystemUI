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

package com.android.systemui.car.displaycompat;

import static android.car.Car.PERMISSION_CONTROL_CAR_APP_LAUNCH;

import android.Manifest;
import android.annotation.NonNull;
import android.annotation.RequiresPermission;
import android.annotation.UserIdInt;
import android.app.TaskInfo;
import android.car.content.pm.CarPackageManager;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.UserHandle;
import android.util.Log;

import androidx.annotation.Nullable;

/**
 * Utility class for display compatibility
 */
public class CarDisplayCompatUtils {
    private static final String TAG = "CarDisplayCompatUtils";
    private static final String
            META_DATA_BACK_SUPPORT = "android.car.displaycompat.requires_back_button";

    /**
     * @return the package name associated with the taskInfo
     */
    @Nullable
    public static String getPackageName(TaskInfo taskInfo) {
        if (taskInfo.topActivity != null) {
            return taskInfo.topActivity.getPackageName();
        }
        if (taskInfo.baseIntent.getComponent() != null) {
            return taskInfo.baseIntent.getComponent().getPackageName();
        }
        return null;
    }

    /**
     * @return {@code true} if the {@code packageName} requires display compatibility
     */
    @RequiresPermission(allOf = {PERMISSION_CONTROL_CAR_APP_LAUNCH,
            android.Manifest.permission.QUERY_ALL_PACKAGES})
    public static boolean requiresDisplayCompat(
            @NonNull String packageName, int userId,
            @Nullable CarPackageManager carPackageManager) {
        boolean result = false;
        if (carPackageManager != null) {
            try {
                result = carPackageManager.requiresDisplayCompat(packageName, userId);
            } catch (PackageManager.NameNotFoundException e) {
                Log.v(TAG, e.toString());
            }
        } else {
            Log.w(TAG, "CarPackageManager is not set");
        }
        return result;
    }

    /**
     * Determines whether the given package requires back affordance support by checking the
     * package's metadata for the presence of a specific key (META_DATA_BACK_SUPPORT).
     * If the metadata key exists and is set to true, the package requires back affordance.
     *
     * @param packageName The name of the package to check.
     * @param userId      The ID of the current user.
     * @return True if the package requires back affordance support; false otherwise.
     */
    @RequiresPermission(Manifest.permission.INTERACT_ACROSS_USERS)
    public static boolean requiresBackAffordance(@NonNull String packageName,
            @UserIdInt int userId, @Nullable PackageManager packageManager) {
        if (packageManager == null) {
            return false;
        }

        UserHandle userHandle = UserHandle.of(userId);
        ApplicationInfo applicationInfo;

        try {
            applicationInfo = packageManager.getApplicationInfoAsUser(packageName,
                    PackageManager.GET_META_DATA, userHandle);
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "Package not found: " + packageName, e);
            return false;
        }

        // Check for META_DATA_BACK_SUPPORT
        if (applicationInfo.metaData != null
                && applicationInfo.metaData.containsKey(META_DATA_BACK_SUPPORT)) {
            boolean requiresBackAffordance =
                    applicationInfo.metaData.getBoolean(META_DATA_BACK_SUPPORT);
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, String.format("Package %s has %s metadata: %b", packageName,
                        META_DATA_BACK_SUPPORT, requiresBackAffordance));
            }
            return requiresBackAffordance;
        }
        return false;
    }
}
