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

import android.animation.Animator;
import android.annotation.NonNull;
import android.app.ActivityManager;
import android.window.WindowContainerTransaction;

import com.android.car.scalableui.manager.PanelTransaction;
import com.android.car.scalableui.model.Transition;
import com.android.car.scalableui.model.Variant;
import com.android.systemui.car.wm.scalableui.panel.TaskPanel;
import com.android.systemui.car.wm.scalableui.panel.TaskPanelPool;
import com.android.wm.shell.ShellTaskOrganizer;
import com.android.wm.shell.automotive.AutoTaskStackController;
import com.android.wm.shell.automotive.AutoTaskStackState;
import com.android.wm.shell.automotive.AutoTaskStackTransaction;
import com.android.wm.shell.dagger.WMSingleton;

import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;

@WMSingleton
public final class AutoTaskStackHelper {
    private final ShellTaskOrganizer mShellTaskOrganizer;
    private final AutoTaskStackController mAutoTaskStackController;
    private final HashMap<String, Animator> mPendingAnimators = new HashMap<>();

    @Inject
    public AutoTaskStackHelper(ShellTaskOrganizer shellTaskOrganizer,
            AutoTaskStackController autoTaskStackController) {
        mShellTaskOrganizer = shellTaskOrganizer;
        mAutoTaskStackController = autoTaskStackController;
    }

    /**
     * Sets a task as trimmable or not - by default this will be true for tasks.
     */
    public void setTaskTrimmable(@NonNull ActivityManager.RunningTaskInfo task, boolean trimmable) {
        WindowContainerTransaction wct = new WindowContainerTransaction();
        wct.setTaskTrimmableFromRecents(task.token, trimmable);
        mShellTaskOrganizer.applyTransaction(wct);
    }

    /**
     * Start a new transition for a given {@link PanelTransaction}
     */
    public void startTransition(PanelTransaction transaction) {
        mAutoTaskStackController.startTransition(getAutoTaskStackTransaction(transaction));
    }

    /**
     * Get a AutoTaskStackTransaction for a given PanelTransaction and set the appropriate
     * pending animators.
     */
    public AutoTaskStackTransaction getAutoTaskStackTransaction(
            PanelTransaction panelTransaction) {
        mPendingAnimators.clear();
        AutoTaskStackTransaction autoTaskStackTransaction = new AutoTaskStackTransaction();

        for (Map.Entry<String, Transition> entry :
                panelTransaction.getPanelTransactionStates()) {
            Transition transition = entry.getValue();
            Variant toVariant = transition.getToVariant();
            TaskPanel taskPanel = TaskPanelPool.getTaskPanel(
                    p -> p.getRootStack() != null && p.getId().equals(entry.getKey()));
            if (taskPanel == null) {
                continue;
            }
            AutoTaskStackState autoTaskStackState = new AutoTaskStackState(
                    toVariant.getBounds(),
                    toVariant.isVisible(),
                    toVariant.getLayer());
            autoTaskStackTransaction.setTaskStackState(taskPanel.getRootStack().getId(),
                    autoTaskStackState);
        }
        for (Map.Entry<String, Animator> entry : panelTransaction.getAnimators()) {
            //TODO(b/391726254): use a HashMap from IBinder to PanelTransaction.
            mPendingAnimators.put(entry.getKey(), entry.getValue());
        }

        return autoTaskStackTransaction;
    }

    /**
     * Retrieve the pending animators setup in {@link #getAutoTaskStackTransaction}.
     */
    public Map<String, Animator> getPendingAnimators() {
        return mPendingAnimators;
    }
}
