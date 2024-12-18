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

package com.android.systemui.car.wm.taskview;

import android.app.ActivityManager;
import android.app.WindowConfiguration;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.IBinder;
import android.util.Log;
import android.util.Slog;
import android.view.SurfaceControl;
import android.window.TransitionInfo;
import android.window.TransitionRequestInfo;
import android.window.WindowContainerTransaction;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.systemui.car.wm.CarSystemUIProxyImpl;
import com.android.wm.shell.dagger.WMSingleton;
import com.android.wm.shell.shared.TransitionUtil;
import com.android.wm.shell.transition.Transitions;

import dagger.Lazy;

import javax.inject.Inject;

/**
 * This class handles the extra transitions work pertaining to shell transitions. This class only
 * works when shell transitions are enabled.
 */
@WMSingleton
public final class RemoteCarTaskViewTransitions implements Transitions.TransitionHandler {
    // TODO(b/359584498): Add unit tests for this class.
    private static final String TAG = "CarTaskViewTransit";

    private final Transitions mTransitions;
    private final Context mContext;
    private final Lazy<CarSystemUIProxyImpl> mCarSystemUIProxy;

    @Inject
    public RemoteCarTaskViewTransitions(Transitions transitions,
            Lazy<CarSystemUIProxyImpl> carSystemUIProxy,
            Context context) {
        mTransitions = transitions;
        mContext = context;
        mCarSystemUIProxy = carSystemUIProxy;

        if (Transitions.ENABLE_SHELL_TRANSITIONS) {
            mTransitions.addHandler(this);
        } else {
            Slog.e(TAG,
                    "Not initializing RemoteCarTaskViewTransitions, as shell transitions are "
                            + "disabled");
        }
    }

    @Nullable
    @Override
    public WindowContainerTransaction handleRequest(@NonNull IBinder transition,
            @NonNull TransitionRequestInfo request) {
        if (request.getTriggerTask() == null) {
            return null;
        }

        WindowContainerTransaction wct = null;
        // TODO(b/333923667): Plumb some API and get the host activity from CarSystemUiProxyImpl
        //  on a per taskview basis and remove the ACTIVITY_TYPE_HOME check.
        if (isHome(request.getTriggerTask())
                && TransitionUtil.isOpeningType(request.getType())) {
            wct = reorderEmbeddedTasksToTop(request.getTriggerTask().displayId);
        }

        // TODO(b/333923667): Think of moving this to CarUiPortraitSystemUI instead.
        if (mContext.getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_CAR_SPLITSCREEN_MULTITASKING)) {
            if (isHome(request.getTriggerTask())
                    && TransitionUtil.isOpeningType(request.getType())
                    && isInFullScreenMode(request.getTriggerTask())) {
                Slog.e(TAG, "A non-home task (" + request.getTriggerTask().taskId + ") "
                        + "shouldn't appear in fullscreen mode on automotive split-screen.");
                // TODO(b/333923667): Need a recovery. Either reparent this task to the relevant
                //  root task + bring home to top or just terminate this task.
            }
        }
        return wct;
    }

    private static boolean isHome(ActivityManager.RunningTaskInfo taskInfo) {
        return taskInfo.getActivityType() == WindowConfiguration.ACTIVITY_TYPE_HOME;
    }

    private static boolean isInFullScreenMode(ActivityManager.RunningTaskInfo taskInfo) {
        return taskInfo.getWindowingMode() == WindowConfiguration.WINDOWING_MODE_FULLSCREEN;
    }

    private WindowContainerTransaction reorderEmbeddedTasksToTop(int endDisplayId) {
        WindowContainerTransaction wct = new WindowContainerTransaction();
        for (int i = mCarSystemUIProxy.get().getAllTaskViews().size() - 1; i >= 0; i--) {
            // TODO(b/359586295): Handle restarting of tasks if required.
            ActivityManager.RunningTaskInfo task =
                    mCarSystemUIProxy.get().getAllTaskViews().valueAt(i).getTaskInfo();
            if (task == null) continue;
            if (task.displayId != endDisplayId) continue;
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Slog.d(TAG, "Adding transition work to bring the embedded " + task.topActivity
                        + " to top");
            }
            wct.reorder(task.token, true);
        }
        return wct;
    }

    @Override
    public boolean startAnimation(@NonNull IBinder transition, @NonNull TransitionInfo info,
            @NonNull SurfaceControl.Transaction startTransaction,
            @NonNull SurfaceControl.Transaction finishTransaction,
            @NonNull Transitions.TransitionFinishCallback finishCallback) {
        // TODO(b/369186876): Implement reordering of task view task with the host task
        return false;
    }
}
