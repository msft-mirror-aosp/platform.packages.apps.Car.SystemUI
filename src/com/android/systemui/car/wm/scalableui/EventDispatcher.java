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

import android.content.Context;

import com.android.car.scalableui.manager.ActionManager;
import com.android.car.scalableui.manager.StateManager;
import com.android.car.scalableui.model.Event;
import com.android.car.scalableui.model.PanelTransaction;
import com.android.systemui.R;
import com.android.wm.shell.dagger.WMSingleton;

import dagger.Lazy;

import javax.inject.Inject;

/**
 * Class is responsible for dispatching events to the {@link StateManager} and
 * {@link ActionManager} and then potentially executing the resulting transaction.
 */
@WMSingleton
public class EventDispatcher {

    private final Context mContext;
    private final PanelTransitionCoordinator mPanelTransitionCoordinator;

    @Inject
    public EventDispatcher(Context context,
            Lazy<PanelTransitionCoordinator> panelTransitionCoordinator) {
        mContext = context;
        if (isScalableUIEnabled()) {
            mPanelTransitionCoordinator = panelTransitionCoordinator.get();
        } else {
            mPanelTransitionCoordinator = null;
        }
    }

    /**
     * See {@link #getTransaction(Event)}
     */
    public static PanelTransaction getTransaction(String event) {
        return getTransaction(new Event.Builder(event).build());
    }

    /**
     * Retrieve a panel transaction describing the provided event parameter.
     */
    public static PanelTransaction getTransaction(Event event) {
        return StateManager.handleEvent(event);
    }

    /**
     * See {@link #executeEvent(Event)}
     */
    public void executeEvent(String event) {
        executeEvent(new Event.Builder(event).build());
    }

    /**
     * Executes the {@link Event} by getting a linked {@link PanelTransaction} and sending
     * an Action.
     */
    public void executeEvent(Event event) {
        if (!isScalableUIEnabled()) {
            throw new IllegalStateException("ScalableUI disabled - cannot execute transaction");
        }
        mPanelTransitionCoordinator.startTransition(getTransaction(event));
        ActionManager.handleEvent(mContext, event);
    }

    private boolean isScalableUIEnabled() {
        return mContext.getResources().getBoolean(R.bool.config_enableScalableUI);
    }

    /**
     * An interface representing an object that can produce {@link Event} and dispatch them.
     *
     * TODO(b/409615558): Create a broadcast receiver to receive event from other system components.
     */
    public interface EventProducer {
        /**
         * Sets the {@link EventDispatcher} that this producer should use to dispatch events.
         */
        void setEventDispatcher(EventDispatcher eventDispatcher);
    }
}
