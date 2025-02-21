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

import static android.car.Car.PERMISSION_CONTROL_CAR_APP_LAUNCH;
import static android.view.Display.DEFAULT_DISPLAY;

import static com.android.systemui.car.displaycompat.CarDisplayCompatUtils.getPackageName;
import static com.android.systemui.car.displaycompat.CarDisplayCompatUtils.requiresBackAffordance;
import static com.android.systemui.car.displaycompat.CarDisplayCompatUtils.requiresDisplayCompat;

import android.Manifest;
import android.annotation.Nullable;
import android.annotation.RequiresPermission;
import android.app.ActivityManager;
import android.car.content.pm.CarPackageManager;
import android.content.Context;
import android.os.UserHandle;

import androidx.annotation.NonNull;

import com.android.systemui.R;
import com.android.systemui.car.CarServiceProvider;
import com.android.wm.shell.ShellTaskOrganizer;
import com.android.wm.shell.common.DisplayController;
import com.android.wm.shell.common.DisplayInsetsController;
import com.android.wm.shell.common.ShellExecutor;
import com.android.wm.shell.common.SyncTransactionQueue;
import com.android.wm.shell.common.annotations.ShellBackgroundThread;
import com.android.wm.shell.common.annotations.ShellMainThread;
import com.android.wm.shell.windowdecor.CarWindowDecorViewModel;

/**
 * WindowDecoration for display compat applications
 */
public class AutoDisplayCompatWindowDecorViewModel extends CarWindowDecorViewModel {
    @NonNull
    private final Context mContext;

    @Nullable
    private CarPackageManager mCarPackageManager;

    public AutoDisplayCompatWindowDecorViewModel(
            @NonNull Context context,
            @ShellMainThread ShellExecutor mainExecutor,
            @ShellBackgroundThread ShellExecutor bgExecutor,
            ShellTaskOrganizer taskOrganizer,
            DisplayController displayController,
            DisplayInsetsController displayInsetsController,
            SyncTransactionQueue syncQueue,
            CarServiceProvider carServiceProvider) {
        super(context, mainExecutor, bgExecutor, taskOrganizer, displayController,
                displayInsetsController, syncQueue,
                /* isWindowDecorEnabled= */ context.getResources()
                        .getBoolean(R.bool.config_showDisplayCompatWindowDecoration));
        mContext = context;
        carServiceProvider.addListener(
                car -> mCarPackageManager = car.getCarManager(CarPackageManager.class));
    }

    @RequiresPermission(allOf = {PERMISSION_CONTROL_CAR_APP_LAUNCH,
            android.Manifest.permission.QUERY_ALL_PACKAGES,
            Manifest.permission.INTERACT_ACROSS_USERS})
    @Override
    protected boolean shouldShowWindowDecor(ActivityManager.RunningTaskInfo taskInfo) {
        String packageName = getPackageName(taskInfo);
        if (packageName == null) {
            return false;
        }
        Context userContext = mContext.createContextAsUser(
                UserHandle.of(taskInfo.userId), /* flags= */ 0);
        return (requiresBackAffordance(packageName, taskInfo.userId,
                userContext.getPackageManager())
                || requiresDisplayCompat(packageName, taskInfo.userId, mCarPackageManager))
                && taskInfo.displayId == DEFAULT_DISPLAY;
    }
}
