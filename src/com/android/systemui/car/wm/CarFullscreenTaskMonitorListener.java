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

package com.android.systemui.car.wm;

import android.app.ActivityManager;
import android.content.Context;
import android.util.Log;
import android.view.SurfaceControl;

import com.android.systemui.car.CarServiceProvider;
import com.android.wm.shell.ShellTaskOrganizer;
import com.android.wm.shell.automotive.TaskRepository;
import com.android.wm.shell.common.SyncTransactionQueue;
import com.android.wm.shell.fullscreen.FullscreenTaskListener;
import com.android.wm.shell.recents.RecentTasksController;
import com.android.wm.shell.sysui.ShellInit;
import com.android.wm.shell.taskview.TaskViewTransitions;
import com.android.wm.shell.windowdecor.WindowDecorViewModel;

import java.util.Optional;

/**
 * The Car version of {@link FullscreenTaskListener}, which reports Task lifecycle to CarService
 * only when the {@link CarSystemUIProxyImpl} should be registered.
 *
 * Please note that this reports FULLSCREEN + MULTI_WINDOW tasks to the CarActivityService but
 * excludes the tasks that are associated with a taskview.
 *
 * <p>When {@link CarSystemUIProxyImpl#shouldRegisterCarSystemUIProxy(Context)} returns true, the
 * task organizer is registered by the system ui alone and hence SystemUI is responsible to act as
 * a task monitor for the car service.
 *
 * <p>On legacy system where a task organizer is registered by system ui and car launcher both,
 * this listener will not forward task lifecycle to car service as this would end up sending
 * multiple task events to the car service.
 */
public class CarFullscreenTaskMonitorListener extends FullscreenTaskListener {
    static final String TAG = "CarFullscrTaskMonitor";
    static final boolean DBG = Log.isLoggable(TAG, Log.DEBUG);
    private final ShellTaskOrganizer mShellTaskOrganizer;
    private final CarServiceTaskReporter mCarServiceTaskReporter;

    private final ShellTaskOrganizer.TaskListener mMultiWindowTaskListener =
            new ShellTaskOrganizer.TaskListener() {
                @Override
                public void onTaskAppeared(ActivityManager.RunningTaskInfo taskInfo,
                        SurfaceControl leash) {
                    mCarServiceTaskReporter.reportTaskAppeared(taskInfo, leash);
                }

                @Override
                public void onTaskInfoChanged(ActivityManager.RunningTaskInfo taskInfo) {
                    mCarServiceTaskReporter.reportTaskInfoChanged(taskInfo);
                }

                @Override
                public void onTaskVanished(ActivityManager.RunningTaskInfo taskInfo) {
                    mCarServiceTaskReporter.reportTaskVanished(taskInfo);
                }
            };

    public CarFullscreenTaskMonitorListener(
            Context context,
            CarServiceProvider carServiceProvider,
            ShellInit shellInit,
            ShellTaskOrganizer shellTaskOrganizer,
            SyncTransactionQueue syncQueue,
            Optional<RecentTasksController> recentTasksOptional,
            Optional<WindowDecorViewModel> windowDecorViewModelOptional,
            TaskViewTransitions taskViewTransitions,
            TaskRepository taskRepository) {
        super(shellInit, shellTaskOrganizer, syncQueue, recentTasksOptional,
                windowDecorViewModelOptional);
        mShellTaskOrganizer = shellTaskOrganizer;
        mCarServiceTaskReporter = new CarServiceTaskReporter(context, carServiceProvider,
                taskViewTransitions,
                shellTaskOrganizer,
                taskRepository);

        shellInit.addInitCallback(
                () -> mShellTaskOrganizer.addListenerForType(mMultiWindowTaskListener,
                        ShellTaskOrganizer.TASK_LISTENER_TYPE_MULTI_WINDOW),
                this);
    }

    @Override
    public void onTaskAppeared(ActivityManager.RunningTaskInfo taskInfo,
            SurfaceControl leash) {
        super.onTaskAppeared(taskInfo, leash);
        mCarServiceTaskReporter.reportTaskAppeared(taskInfo, leash);
    }

    @Override
    public void onTaskInfoChanged(ActivityManager.RunningTaskInfo taskInfo) {
        super.onTaskInfoChanged(taskInfo);
        mCarServiceTaskReporter.reportTaskInfoChanged(taskInfo);
    }

    @Override
    public void onTaskVanished(ActivityManager.RunningTaskInfo taskInfo) {
        super.onTaskVanished(taskInfo);
        mCarServiceTaskReporter.reportTaskVanished(taskInfo);
    }
}
