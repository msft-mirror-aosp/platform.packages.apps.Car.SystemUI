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

package com.android.systemui.car.wm.scalableui;

import android.annotation.NonNull;
import android.app.ActivityManager;
import android.window.WindowContainerTransaction;

import com.android.wm.shell.ShellTaskOrganizer;
import com.android.wm.shell.dagger.WMSingleton;

import javax.inject.Inject;

@WMSingleton
public final class AutoTaskStackHelper {
    private final ShellTaskOrganizer mShellTaskOrganizer;

    @Inject
    public AutoTaskStackHelper(ShellTaskOrganizer shellTaskOrganizer) {
        mShellTaskOrganizer = shellTaskOrganizer;
    }

    /**
     * Sets a task as trimmable or not - by default this will be true for tasks.
     */
    public void setTaskTrimmable(@NonNull ActivityManager.RunningTaskInfo task, boolean trimmable) {
        WindowContainerTransaction wct = new WindowContainerTransaction();
        wct.setTaskTrimmableFromRecents(task.token, trimmable);
        mShellTaskOrganizer.applyTransaction(wct);
    }
}
