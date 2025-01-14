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
import static android.view.View.GONE;
import static android.view.View.VISIBLE;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.UserIdInt;
import android.app.ActivityManager;
import android.app.ActivityTaskManager.RootTaskInfo;
import android.app.ActivityTaskManager;
import android.app.IActivityTaskManager;
import android.app.TaskStackListener;
import android.car.Car;
import android.car.content.pm.CarPackageManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.drawable.Drawable;
import android.hardware.input.InputManager;
import android.os.Handler;
import android.os.RemoteException;
import android.os.SystemClock;
import android.os.UserHandle;
import android.util.Log;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;

import androidx.annotation.MainThread;
import androidx.annotation.RequiresPermission;

import com.android.systemui.R;
import com.android.systemui.car.CarServiceProvider;
import com.android.systemui.car.systembar.CarAppFloatingButtonManager;
import com.android.systemui.car.systembar.CarSystemBarController;
import com.android.systemui.dagger.qualifiers.Main;
import com.android.systemui.dagger.qualifiers.UiBackground;
import com.android.systemui.shared.system.ActivityManagerWrapper;

import javax.inject.Inject;
import java.util.List;

/**
 * Implementation of {@link ToolbarController} for showing/hiding the display compatibility toolbar.
 */
public class ToolbarControllerImpl implements ToolbarController {
    private static final String TAG = ToolbarControllerImpl.class.getSimpleName();
    private static final String META_DATA_BACK_SUPPORT
                                    = "android.car.displaycompat.requires_back_button";
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
            android.Manifest.permission.QUERY_ALL_PACKAGES})
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
        if (requiresBackAffordance(packageName, taskInfo.userId)
            || requiresDisplayCompat(packageName, taskInfo.userId)) {
            mCurrentTask = taskInfo;
            mMainHandler.post(() -> show());
            return;
        }
        mCurrentTask = null;
        mMainHandler.post(() -> hide());
    }

    private boolean canLog() {
        return Log.isLoggable(TAG, Log.DEBUG);
    }

    /**
     * Determines whether the given package requires back affordance support by checking the
     * package's metadata for the presence of a specific key (META_DATA_BACK_SUPPORT).
     * If the metadata key exists and is set to true, the package requires back affordance.
     *
     * @param packageName The name of the package to check.
     * @param userId The ID of the current user.
     * @return True if the package requires back affordance support; false otherwise.
     */
    private boolean requiresBackAffordance(@NonNull String packageName, @UserIdInt int userId) {

        UserHandle userHandle = UserHandle.of(userId);
        PackageManager mPackageManager = userContext.getPackageManager();
        ApplicationInfo applicationInfo = null;

        try {
            applicationInfo = mPackageManager.getApplicationInfoAsUser(packageName,
                PackageManager.GET_META_DATA, userHandle);
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "Package not found: " + packageName, e);
            return false;
        }

        // Check for META_DATA_BACK_SUPPORT
        if (applicationInfo.metaData != null &&
            applicationInfo.metaData.containsKey(META_DATA_BACK_SUPPORT)) {
            boolean requiresBackAffordance =
                applicationInfo.metaData.getBoolean(META_DATA_BACK_SUPPORT);
            if (canLog()) {
                Log.d(TAG, String.format("Package %s has %s metadata: %b", packageName,
                    META_DATA_BACK_SUPPORT, requiresBackAffordance));
            }
            return requiresBackAffordance;
        }
        return false;
    }

    private String getPackageName(RootTaskInfo taskInfo) {
        if (taskInfo.topActivity != null) {
            return taskInfo.topActivity.getPackageName();
        }
        return taskInfo.baseIntent.getComponent().getPackageName();
    }

    @RequiresPermission(allOf = {PERMISSION_CONTROL_CAR_APP_LAUNCH,
            android.Manifest.permission.QUERY_ALL_PACKAGES})
    private boolean requiresDisplayCompat(@NonNull String packageName, @UserIdInt int userId) {
        boolean result = false;
        if (mCarPackageManager != null) {
            try {
                result = mCarPackageManager.requiresDisplayCompat(packageName, userId);
            } catch (NameNotFoundException e) {
            }
        } else {
            Log.w(TAG, "CarPackageManager is not set.");
        }
        return result;
    }
}
