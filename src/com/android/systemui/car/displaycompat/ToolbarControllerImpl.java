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
package com.android.systemui.car.displaycompat;

import static android.car.Car.PERMISSION_CONTROL_CAR_APP_LAUNCH;
import static android.view.Display.DEFAULT_DISPLAY;

import static com.android.systemui.car.displaycompat.CarDisplayCompatUtils.getPackageName;
import static com.android.systemui.car.displaycompat.CarDisplayCompatUtils.requiresBackAffordance;
import static com.android.systemui.car.displaycompat.CarDisplayCompatUtils.requiresDisplayCompat;

import android.Manifest;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.ActivityManager;
import android.app.ActivityTaskManager;
import android.app.ActivityTaskManager.RootTaskInfo;
import android.app.IActivityTaskManager;
import android.app.TaskStackListener;
import android.car.Car;
import android.car.content.pm.CarPackageManager;
import android.content.Context;
import android.os.Handler;
import android.os.RemoteException;
import android.os.UserHandle;
import android.util.Log;
import android.view.ViewGroup;

import androidx.annotation.MainThread;
import androidx.annotation.RequiresPermission;

import com.android.systemui.car.CarServiceProvider;
import com.android.systemui.car.systembar.CarAppFloatingButtonManager;
import com.android.systemui.car.systembar.CarSystemBarController;
import com.android.systemui.dagger.qualifiers.Main;
import com.android.systemui.dagger.qualifiers.UiBackground;

import javax.inject.Inject;

/**
 * Implementation of {@link ToolbarController} for showing/hiding the display compatibility toolbar.
 */
public class ToolbarControllerImpl implements ToolbarController {
    private static final String TAG = ToolbarControllerImpl.class.getSimpleName();
    private ViewGroup mToolbarParent;
    @NonNull
    private final Handler mMainHandler;
    @NonNull
    private CarServiceProvider mCarServiceProvider;
    @Nullable
    private CarPackageManager mCarPackageManager;
    @NonNull
    private ActivityManager mActivityManager;
    @NonNull
    private Context userContext;
    @NonNull
    @UiBackground
    private final CarServiceProvider.CarServiceOnConnectedListener mCarServiceLifecycleListener =
            car -> {
                mCarPackageManager = (CarPackageManager) car.getCarManager(Car.PACKAGE_SERVICE);
            };

    @Nullable
    private RootTaskInfo mCurrentTask;
    @NonNull
    private ActivityTaskManager mActivityTaskManager;
    @NonNull
    private IActivityTaskManager mActivityTaskMgrInterface;

    private CarAppFloatingButtonManager fbManager;

    @Inject
    public ToolbarControllerImpl(@NonNull @Main Handler mainHandler,
            CarServiceProvider carServiceProvider,
            ActivityManager activityManager,
            ActivityTaskManager activityTaskManager,
            IActivityTaskManager activityTaskMgrInterface) {
        mMainHandler = mainHandler;
        mCarServiceProvider = carServiceProvider;
        mActivityManager = activityManager;
        mActivityTaskManager = activityTaskManager;
        mActivityTaskMgrInterface = activityTaskMgrInterface;
    }

    /**
     * Needs to be called before calling any other method.
     */
    @Override
    public void init(@NonNull CarSystemBarController carSystemBarController) {
        mToolbarParent = carSystemBarController.getTopWindow();
        userContext = mToolbarParent.getContext().createContextAsUser(
                UserHandle.of(ActivityManager.getCurrentUser()), /* flags= */ 0);
        fbManager = new CarAppFloatingButtonManager(userContext);

        mMainHandler.post(() -> {
            mCarServiceProvider.addListener(mCarServiceLifecycleListener);
        });

        mActivityTaskManager.registerTaskStackListener(new TaskStackListener() {
            @Override
            public void onTaskFocusChanged(int taskId, boolean focused) {
                if (focused) {
                    try {
                        update(mActivityTaskMgrInterface.getFocusedRootTaskInfo());
                    } catch (RemoteException e) {
                        Log.e(TAG, "RemoteException while fetching RootTaskInfo " + taskId, e);
                    }
                }
            }
        });
    }

    @MainThread
    @Override
    public void show() {
        if (mToolbarParent == null) {
            Log.w(TAG, "init was not called");
            return;
        }
        if (fbManager != null) {
            fbManager.showAppFloatingButton();
        }
    }


    @MainThread
    @Override
    public void hide() {
        if (mToolbarParent == null) {
            Log.w(TAG, "init was not called");
            return;
        }
        if (fbManager != null) {
            fbManager.removeAppFloatingButton();
        }
    }

    /**
     * This method gets called several times from {@code TaskStackChangeListener#onTaskFocusChanged}
     */
    @RequiresPermission(allOf = {PERMISSION_CONTROL_CAR_APP_LAUNCH,
            android.Manifest.permission.QUERY_ALL_PACKAGES,
            Manifest.permission.INTERACT_ACROSS_USERS})
    @Override
    public void update(@NonNull RootTaskInfo taskInfo) {
        if (mToolbarParent == null) {
            Log.w(TAG, "init was not called");
            return;
        }
        if (taskInfo.displayId != DEFAULT_DISPLAY) {
            return;
        }

        String packageName = getPackageName(taskInfo);
        if (packageName != null && (requiresBackAffordance(packageName, taskInfo.userId,
                userContext.getPackageManager()) || requiresDisplayCompat(packageName,
                taskInfo.userId, mCarPackageManager))
                && taskInfo.displayId == DEFAULT_DISPLAY) {
            mCurrentTask = taskInfo;
            mMainHandler.post(() -> show());
            return;
        }
        mCurrentTask = null;
        mMainHandler.post(() -> hide());
    }
}
