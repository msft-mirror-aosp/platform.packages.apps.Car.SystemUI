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
package com.android.systemui.car.wm.scalableui.panel;

import android.app.ActivityManager;
import android.content.Context;
import android.os.UserManager;

import androidx.annotation.Nullable;

import com.android.car.scalableui.panel.PanelPool;
import com.android.systemui.car.users.CarSystemUIUserUtil;
import com.android.wm.shell.dagger.WMSingleton;

import java.util.function.Predicate;

import javax.inject.Inject;

/**
 * This utility class provides helper methods for {@link TaskPanel}.
 */
@WMSingleton
public class PanelUtils {
    private static final String TAG = PanelUtils.class.getSimpleName();
    private final Context mContext;
    private final UserManager mUserManager;

    @Inject
    public PanelUtils(Context context) {
        mContext = context;
        mUserManager = mContext.getSystemService(UserManager.class);
    }

    /**
     * Checks if any panel in the pool handles the given root task ID.
     *
     * @param rootTaskId The root task ID to check.
     * @return True if a panel with the given root task ID exists in the pool, false otherwise.
     */
    public boolean handles(int rootTaskId) {
        return getTaskPanel(panel -> panel.getRootTaskId() == rootTaskId) != null;
    }

    /**
     * Retrieves a {@link TaskPanel} that satisfies the given {@link Predicate}.
     *
     * @param predicate The predicate to test against potential {@link TaskPanel} instances.
     * @return The matching {@link TaskPanel}, or null if none is found.
     */
    @Nullable
    public TaskPanel getTaskPanel(Predicate<TaskPanel> predicate) {
        return (TaskPanel) PanelPool.getInstance().getPanel(
                p -> (p instanceof TaskPanel tp) && predicate.test(tp));
    }

    /**
     * Checks if the user is unlocked.
     */
    public boolean isUserUnlocked() {
        int userId = CarSystemUIUserUtil.isSecondaryMUMDSystemUI()
                ? mContext.getUserId()
                : ActivityManager.getCurrentUser();

        return mUserManager != null && mUserManager.isUserUnlocked(userId);
    }
}
