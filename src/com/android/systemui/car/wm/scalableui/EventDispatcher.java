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

import com.android.car.scalableui.manager.Event;
import com.android.car.scalableui.manager.PanelTransaction;
import com.android.car.scalableui.manager.StateManager;
import com.android.wm.shell.dagger.WMSingleton;

import javax.inject.Inject;

/**
 * Class is responsible for dispatching events to the {@link StateManager} and then potentially
 * executing the resulting transaction.
 */
@WMSingleton
public class EventDispatcher {

    private final TaskPanelTransitionCoordinator mTaskPanelTransitionCoordinator;

    @Inject
    public EventDispatcher(TaskPanelTransitionCoordinator taskPanelTransitionCoordinator) {
        mTaskPanelTransitionCoordinator = taskPanelTransitionCoordinator;
    }

    /**
     * See {@link #getTransaction(Event)}
     */
    public static PanelTransaction getTransaction(String event) {
        return getTransaction(new Event(event));
    }

    /**
     * Retrieve a panel transaction describing the provided event parameter.
     */
    public static PanelTransaction getTransaction(Event event) {
        return StateManager.handleEvent(event);
    }

    /**
     * See {@link #executeTransaction(Event)}
     */
    public void executeTransaction(String event) {
        executeTransaction(new Event(event));
    }

    /**
     * Retrieve a panel transaction for a given event and then immediately execute this
     * transaction.
     */
    public void executeTransaction(Event event) {
        mTaskPanelTransitionCoordinator.startTransition(getTransaction(event));
    }
}
