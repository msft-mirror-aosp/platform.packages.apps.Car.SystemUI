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

package com.android.systemui.car.wm;

import static com.android.systemui.car.wm.CarFullscreenTaskMonitorListener.DBG;
import static com.android.systemui.car.wm.CarFullscreenTaskMonitorListener.TAG;

import android.app.ActivityManager;
import android.car.Car;
import android.car.app.CarActivityManager;
import android.content.Context;
import android.hardware.display.DisplayManager;
import android.util.Slog;
import android.view.Display;
import android.view.SurfaceControl;

import com.android.systemui.car.CarServiceProvider;
import com.android.wm.shell.ShellTaskOrganizer;
import com.android.wm.shell.automotive.TaskRepository;
import com.android.wm.shell.taskview.TaskViewTransitions;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * This class reports the task events to CarService using {@link CarActivityManager}.
 */
final class CarServiceTaskReporter {
    private final DisplayManager mDisplayManager;
    private final AtomicReference<CarActivityManager> mCarActivityManagerRef =
            new AtomicReference<>();
    private final boolean mShouldConnectToCarActivityService;
    private final TaskViewTransitions mTaskViewTransitions;
    private final ShellTaskOrganizer mShellTaskOrganizer;
    // TODO(b/395767437): Add task listener for fullscreen and multi window mode in task repository
    private final TaskRepository mTaskRepository;

    CarServiceTaskReporter(Context context, CarServiceProvider carServiceProvider,
            TaskViewTransitions taskViewTransitions,
            ShellTaskOrganizer shellTaskOrganizer,
            TaskRepository taskRepository) {
        mDisplayManager = context.getSystemService(DisplayManager.class);
        mTaskViewTransitions = taskViewTransitions;
        // Rely on whether or not CarSystemUIProxy should be registered to account for these
        // cases:
        // 1. Legacy system where System UI + launcher both register a TaskOrganizer.
        //    CarFullScreenTaskMonitorListener will not forward the task lifecycle to the car
        //    service, as launcher has its own FullScreenTaskMonitorListener.
        // 2. MUMD system where only System UI registers a TaskOrganizer but the user associated
        //    with the current display is not a system user. CarSystemUIProxy will be registered
        //    for system user alone and hence CarFullScreenTaskMonitorListener should be
        //    registered only then.
        mShouldConnectToCarActivityService = CarSystemUIProxyImpl.shouldRegisterCarSystemUIProxy(
                context);
        mShellTaskOrganizer = shellTaskOrganizer;
        mTaskRepository = taskRepository;

        if (mShouldConnectToCarActivityService) {
            carServiceProvider.addListener(this::onCarConnected);
        }
    }

    public void reportTaskAppeared(ActivityManager.RunningTaskInfo taskInfo, SurfaceControl leash) {
        if (!mShouldConnectToCarActivityService) {
            if (DBG) {
                Slog.w(TAG, "onTaskAppeared() handled in SystemUI as conditions not met for "
                        + "connecting to car service.");
            }
            return;
        }

        if (mTaskViewTransitions.isTaskViewTask(taskInfo)) {
            if (DBG) {
                Slog.w(TAG, "not reporting onTaskAppeared for taskview task = " + taskInfo.taskId);
            }
            return;
        }
        CarActivityManager carAM = mCarActivityManagerRef.get();
        if (carAM != null) {
            if (carAM.isUsingAutoTaskStackWindowing()) {
                mTaskRepository.onTaskAppeared(taskInfo, leash);
            } else {
                carAM.onTaskAppeared(taskInfo, leash);
            }
        } else {
            Slog.w(TAG, "CarActivityManager is null, skip onTaskAppeared: taskInfo=" + taskInfo);
        }
    }

    public void reportTaskInfoChanged(ActivityManager.RunningTaskInfo taskInfo) {
        if (!mShouldConnectToCarActivityService) {
            if (DBG) {
                Slog.w(TAG, "onTaskInfoChanged() handled in SystemUI as conditions not met for "
                        + "connecting to car service.");
            }
            return;
        }

        if (mTaskViewTransitions.isTaskViewTask(taskInfo)) {
            if (DBG) {
                Slog.w(TAG,
                        "not reporting onTaskInfoChanged for taskview task = " + taskInfo.taskId);
            }
            return;
        }

        CarActivityManager carAM = mCarActivityManagerRef.get();
        if (carAM != null) {
            if (carAM.isUsingAutoTaskStackWindowing()) {
                mTaskRepository.onTaskChanged(taskInfo);
            } else {
                carAM.onTaskInfoChanged(taskInfo);
            }
        } else {
            Slog.w(TAG, "CarActivityManager is null, skip onTaskInfoChanged: taskInfo=" + taskInfo);
        }
    }

    public void reportTaskVanished(ActivityManager.RunningTaskInfo taskInfo) {
        if (!mShouldConnectToCarActivityService) {
            if (DBG) {
                Slog.w(TAG, "onTaskVanished() handled in SystemUI as conditions not met for "
                        + "connecting to car service.");
            }
            return;
        }

        if (mTaskViewTransitions.isTaskViewTask(taskInfo)) {
            if (DBG) {
                Slog.w(TAG, "not reporting onTaskVanished for taskview task = " + taskInfo.taskId);
            }
            return;
        }

        CarActivityManager carAM = mCarActivityManagerRef.get();
        if (carAM != null) {
            if (carAM.isUsingAutoTaskStackWindowing()) {
                mTaskRepository.onTaskVanished(taskInfo);
            } else {
                carAM.onTaskVanished(taskInfo);
            }
        } else {
            Slog.w(TAG, "CarActivityManager is null, skip onTaskVanished: taskInfo=" + taskInfo);
        }
    }

    private void onCarConnected(Car car) {
        mCarActivityManagerRef.set(car.getCarManager(CarActivityManager.class));
        // The tasks that have already appeared need to be reported to the CarActivityManager.
        // The code uses null as the leash because there is no way to get the leash at the moment.
        // And the leash is only required for mirroring cases. Those tasks will anyway appear
        // after the car service is connected and hence will go via the {@link #onTaskAppeared}
        // flow.
        List<ActivityManager.RunningTaskInfo> runningTasks = getRunningNonTaskViewTasks();
        CarActivityManager carAM = mCarActivityManagerRef.get();
        for (ActivityManager.RunningTaskInfo runningTaskInfo : runningTasks) {
            Slog.d(TAG, "Sending onTaskAppeared for an already existing task: "
                    + runningTaskInfo.taskId);
            if (carAM.isUsingAutoTaskStackWindowing()) {
                mTaskRepository.onTaskAppeared(runningTaskInfo, /* leash = */ null);
            } else {
                carAM.onTaskAppeared(runningTaskInfo, /* leash = */ null);
            }
        }
    }

    private List<ActivityManager.RunningTaskInfo> getRunningNonTaskViewTasks() {
        Display[] displays = mDisplayManager.getDisplays();
        List<ActivityManager.RunningTaskInfo> tasksToReturn = new ArrayList<>();
        for (int i = 0; i < displays.length; i++) {
            List<ActivityManager.RunningTaskInfo> taskInfos = mShellTaskOrganizer.getRunningTasks(
                    displays[i].getDisplayId());
            for (ActivityManager.RunningTaskInfo taskInfo : taskInfos) {
                if (!mTaskViewTransitions.isTaskViewTask(taskInfo)) {
                    tasksToReturn.add(taskInfo);
                }
            }
        }
        return tasksToReturn;
    }
}
