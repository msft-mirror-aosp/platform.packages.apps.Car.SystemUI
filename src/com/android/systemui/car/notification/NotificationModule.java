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

package com.android.systemui.car.notification;

import android.content.Context;

import com.android.systemui.R;

import dagger.Module;
import dagger.Provides;

import java.util.Arrays;
import java.util.List;

import javax.inject.Named;

/** Dagger module for Notifications. */
@Module
public class NotificationModule {
    /**
     * Dagger {@link Named} for a list of strings that listen to notification drag open listener.
     */
    public static final String DRAG_OPEN_NOTIFICATION_BAR_NAMES = "DragOpenNotificationBarNames";
    /**
     * Dagger {@link Named} for a list of strings that listen to notification drag close listener.
     */
    public static final String DRAG_CLOSE_NOTIFICATION_BAR_NAMES = "DragCloseNotificationBarNames";

    /** Provides the list of system bar names that the notification panel should register with. */
    @Provides
    @Named(DRAG_OPEN_NOTIFICATION_BAR_NAMES)
    public List<String> provideDragOpenNotificationBarNames(Context context) {
        return Arrays.asList(
                context.getResources().getStringArray(R.array.config_notificationDragOpenListener));
    }

    /** Provides the list of system bar names that the notification panel should register with. */
    @Provides
    @Named(DRAG_CLOSE_NOTIFICATION_BAR_NAMES)
    public List<String> provideDragCloseNotificationBarNames(Context context) {
        return Arrays.asList(context.getResources().getStringArray(
                R.array.config_notificationDragCloseListener));
    }
}
