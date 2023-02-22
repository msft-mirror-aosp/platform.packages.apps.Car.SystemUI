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

package com.android.systemui.car.taskview;

import android.app.ActivityManager;
import android.app.ActivityOptions;
import android.app.PendingIntent;
import android.car.app.CarTaskViewClient;
import android.car.app.CarTaskViewHost;
import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Bundle;
import android.view.SurfaceControl;
import android.window.WindowContainerTransaction;

import com.android.wm.shell.ShellTaskOrganizer;
import com.android.wm.shell.TaskViewBase;
import com.android.wm.shell.TaskViewTaskController;
import com.android.wm.shell.TaskViewTransitions;
import com.android.wm.shell.common.SyncTransactionQueue;

/** Server side implementation for the {@code RemoteCarTaskView}. */
public class RemoteCarTaskViewServerImpl implements TaskViewBase {
    private static final String TAG = RemoteCarTaskViewServerImpl.class.getSimpleName();

    private final SyncTransactionQueue mSyncQueue;
    private final CarTaskViewClient mCarTaskViewClient;
    private final TaskViewTaskController mTaskViewTaskController;

    private final CarTaskViewHost mHostImpl = new CarTaskViewHost() {
        @Override
        public void release() {
            mTaskViewTaskController.release();
        }

        @Override
        public void notifySurfaceCreated(SurfaceControl control) {
            mTaskViewTaskController.surfaceCreated(control);
        }

        @Override
        public void setWindowBounds(Rect bounds) {
            mTaskViewTaskController.setWindowBounds(bounds);
        }

        @Override
        public void notifySurfaceDestroyed() {
            mTaskViewTaskController.surfaceDestroyed();
        }

        @Override
        public void startActivity(
                PendingIntent pendingIntent,
                Intent fillInIntent,
                Bundle options,
                Rect launchBounds) {
            mTaskViewTaskController.startActivity(
                    pendingIntent,
                    fillInIntent,
                    ActivityOptions.fromBundle(options),
                    launchBounds);
        }

        @Override
        public void showEmbeddedTask() {
            ActivityManager.RunningTaskInfo taskInfo =
                    mTaskViewTaskController.getTaskInfo();
            if (taskInfo == null) {
                return;
            }
            WindowContainerTransaction wct = new WindowContainerTransaction();
            // Clears the hidden flag to make it TopFocusedRootTask: b/228092608
            wct.setHidden(taskInfo.token, /* hidden= */ false);
            // Moves the embedded task to the top to make it resumed: b/225388469
            wct.reorder(taskInfo.token, /* onTop= */ true);
            mSyncQueue.queue(wct);
        }
    };

    public RemoteCarTaskViewServerImpl(
            Context context,
            ShellTaskOrganizer organizer,
            SyncTransactionQueue syncQueue,
            CarTaskViewClient carTaskViewClient,
            TaskViewTransitions taskViewTransitions) {
        mSyncQueue = syncQueue;
        mCarTaskViewClient = carTaskViewClient;

        mTaskViewTaskController =
                new TaskViewTaskController(context, organizer, taskViewTransitions, syncQueue);
        mTaskViewTaskController.setTaskViewBase(this);
    }

    public CarTaskViewHost getHostImpl() {
        return mHostImpl;
    }

    @Override
    public Rect getCurrentBoundsOnScreen() {
        return mCarTaskViewClient.getCurrentBoundsOnScreen();
    }

    @Override
    public void setResizeBgColor(SurfaceControl.Transaction transaction, int color) {
        mCarTaskViewClient.setResizeBackgroundColor(transaction, color);
    }

    @Override
    public void onTaskAppeared(ActivityManager.RunningTaskInfo taskInfo, SurfaceControl leash) {
        mCarTaskViewClient.onTaskAppeared(taskInfo, leash);
    }

    @Override
    public void onTaskInfoChanged(ActivityManager.RunningTaskInfo taskInfo) {
        mCarTaskViewClient.onTaskInfoChanged(taskInfo);
    }

    @Override
    public void onTaskVanished(ActivityManager.RunningTaskInfo taskInfo) {
        mCarTaskViewClient.onTaskVanished(taskInfo);
    }
}