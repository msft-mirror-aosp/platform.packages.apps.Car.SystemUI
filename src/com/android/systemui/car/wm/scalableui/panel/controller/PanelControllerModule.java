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

package com.android.systemui.car.wm.scalableui.panel.controller;

import android.content.Context;
import android.view.View;

import com.android.car.scalableui.panel.DecorPanelController;
import com.android.car.scalableui.panel.TaskPanelController;
import com.android.systemui.car.wm.scalableui.view.GripBar;
import com.android.systemui.car.wm.scalableui.view.GripBarViewController;
import com.android.systemui.car.wm.scalableui.view.PanelOverlay;
import com.android.systemui.car.wm.scalableui.view.PanelOverlayController;

import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import dagger.multibindings.ClassKey;
import dagger.multibindings.IntoMap;

/**
 * Module to inject instance related to panel controllers.
 */
@Module
public abstract class PanelControllerModule {
    /** Binds MapsPanelController.Factory. */
    @Binds
    @IntoMap
    @ClassKey(MapsPanelController.class)
    public abstract TaskPanelController.Factory bindMapsPanelControllerFactory(
            MapsPanelController.Factory factory);

    /** Binds GripBarViewController.Factory. */
    @Binds
    @IntoMap
    @ClassKey(GripBarViewController.class)
    public abstract DecorPanelController.Factory bindGripBarControllerFactory(
            GripBarViewController.Factory factory);

    /** Binds PanelOverlayController.Factory. */
    @Binds
    @IntoMap
    @ClassKey(PanelOverlayController.class)
    public abstract DecorPanelController.Factory bindPanelOverlayControllerFactory(
            PanelOverlayController.Factory factory);

    /** Binds {@link GripBar} as a decor panel view. */
    @Provides
    @IntoMap
    @ClassKey(GripBar.class)
    @DecorPanelViewMap
    static View bindGripBarView(Context context) {
        return new GripBar(context);
    }

    /** Binds {@link PanelOverlay} as a decor panel view. */
    @Provides
    @IntoMap
    @ClassKey(PanelOverlay.class)
    @DecorPanelViewMap
    static View bindPanelOverlayView(Context context) {
        return new PanelOverlay(context);
    }
}
