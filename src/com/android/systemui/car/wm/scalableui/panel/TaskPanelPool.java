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

import androidx.annotation.Nullable;

import com.android.car.scalableui.panel.PanelPool;

import java.util.function.Predicate;

/**
 * This utility class provides helper methods for {@link TaskPanel}.
 */
public class TaskPanelPool {
    private static final String TAG = TaskPanelPool.class.getSimpleName();

    private TaskPanelPool() {}

    /**
     * Checks if any panel in the pool handles the given root task ID.
     *
     * @param rootTaskId The root task ID to check.
     * @return True if a panel with the given root task ID exists in the pool, false otherwise.
     */
    public static boolean handles(int rootTaskId) {
        return getTaskPanel(panel -> panel.getRootTaskId() == rootTaskId) != null;
    }

    /**
     * Retrieves a {@link TaskPanel} that satisfies the given {@link Predicate}.
     *
     * @param predicate The predicate to test against potential {@link TaskPanel} instances.
     * @return The matching {@link TaskPanel}, or null if none is found.
     */
    @Nullable
    public static TaskPanel getTaskPanel(Predicate<TaskPanel> predicate) {
        return (TaskPanel) PanelPool.getInstance().getPanel(
                p -> (p instanceof TaskPanel tp) && predicate.test(tp));
    }
}
