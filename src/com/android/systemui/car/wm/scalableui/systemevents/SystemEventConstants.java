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
package com.android.systemui.car.wm.scalableui.systemevents;

public class SystemEventConstants {
    /** Event IDs */
    public static final String EMPTY_EVENT_ID = "empty_event";
    public static final String SYSTEM_HOME_EVENT_ID = "_System_OnHomeEvent";
    public static final String SYSTEM_TASK_OPEN_EVENT_ID = "_System_TaskOpenEvent";
    public static final String SYSTEM_TASK_CLOSE_EVENT_ID = "_System_TaskCloseEvent";
    public static final String SYSTEM_TASK_PANEL_EMPTY_EVENT_ID = "_System_TaskPanelEmptyEvent";
    public static final String SYSTEM_ENTER_SUW_EVENT_ID = "_System_EnterSuwEvent";
    public static final String SYSTEM_EXIST_SUW_EVENT_ID = "_System_ExitSuwEvent";

    /** Token IDs */
    public static final String PANEL_TOKEN_ID = "panelId";
    public static final String COMPONENT_TOKEN_ID = "component";
}
