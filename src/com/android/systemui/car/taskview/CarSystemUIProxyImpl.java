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

package com.android.systemui.car.taskview;

import static android.app.WindowConfiguration.WINDOWING_MODE_MULTI_WINDOW;

import android.app.ActivityTaskManager;
import android.app.TaskInfo;
import android.car.Car;
import android.car.app.CarActivityManager;
import android.car.app.CarSystemUIProxy;
import android.car.app.CarTaskViewClient;
import android.car.app.CarTaskViewHost;
import android.content.Context;
import android.os.Process;
import android.util.Slog;
import android.window.TaskAppearedInfo;

import com.android.systemui.R;
import com.android.systemui.car.CarServiceProvider;
import com.android.wm.shell.ShellTaskOrganizer;
import com.android.wm.shell.TaskViewTransitions;
import com.android.wm.shell.common.SyncTransactionQueue;
import com.android.wm.shell.dagger.WMSingleton;
import com.android.wm.shell.fullscreen.FullscreenTaskListener;

import java.util.List;

import javax.inject.Inject;

/**
 * This class provides a concrete implementation for {@link CarSystemUIProxy}. It hosts all the
 * system ui interaction that is required by other apps.
 */
@WMSingleton
public final class CarSystemUIProxyImpl
        implements CarSystemUIProxy, CarServiceProvider.CarServiceOnConnectedListener {
    private static final String TAG = CarSystemUIProxyImpl.class.getSimpleName();

    private final Context mContext;
    private final SyncTransactionQueue mSyncQueue;
    private final ShellTaskOrganizer mTaskOrganizer;
    private final TaskViewTransitions mTaskViewTransitions;

    @Inject
    CarSystemUIProxyImpl(
            Context context,
            CarServiceProvider carServiceProvider,
            SyncTransactionQueue syncTransactionQueue,
            ShellTaskOrganizer taskOrganizer,
            TaskViewTransitions taskViewTransitions) {
        mContext = context;
        mTaskOrganizer = taskOrganizer;
        mSyncQueue = syncTransactionQueue;
        mTaskViewTransitions = taskViewTransitions;

        if (!Process.myUserHandle().isSystem()) {
            Slog.e(TAG, "Non system user, quitting.");
            return;
        }
        if (!context.getResources().getBoolean(R.bool.config_registerCarSystemUIProxy)) {
            Slog.d(TAG, "registerCarSystemUIProxy disabled, quitting.");
            return;
        }
        carServiceProvider.addListener(this);
    }

    @Override
    public CarTaskViewHost createCarTaskView(CarTaskViewClient carTaskViewClient) {
        RemoteCarTaskViewServerImpl remoteCarTaskViewServerImpl =
                new RemoteCarTaskViewServerImpl(
                        mContext,
                        mTaskOrganizer,
                        mSyncQueue,
                        carTaskViewClient,
                        mTaskViewTransitions);
        return remoteCarTaskViewServerImpl.getHostImpl();
    }

    @Override
    public void onConnected(Car car) {
        cleanUpExistingTaskViewTasks(mTaskOrganizer.registerOrganizer());

        CarActivityManager carActivityManager = car.getCarManager(CarActivityManager.class);
        FullscreenTaskListener fullscreenTaskListener = new CarFullscreenTaskMonitorListener(
                carActivityManager, mSyncQueue);

        carActivityManager.registerTaskMonitor();
        carActivityManager.registerCarSystemUIProxy(this);
    }

    private static void cleanUpExistingTaskViewTasks(List<TaskAppearedInfo> taskAppearedInfos) {
        ActivityTaskManager atm = ActivityTaskManager.getInstance();
        for (TaskAppearedInfo taskAppearedInfo : taskAppearedInfos) {
            TaskInfo taskInfo = taskAppearedInfo.getTaskInfo();
            // In Auto, only TaskView tasks have WINDOWING_MODE_MULTI_WINDOW as of now.
            if (taskInfo.getWindowingMode() == WINDOWING_MODE_MULTI_WINDOW) {
                Slog.d(TAG, "Found a dangling task, removing: " + taskInfo.taskId);
                atm.removeTask(taskInfo.taskId);
            }
        }
    }
}