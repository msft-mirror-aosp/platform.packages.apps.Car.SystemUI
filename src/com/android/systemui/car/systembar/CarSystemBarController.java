/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.systemui.car.systembar;

import android.annotation.IntDef;
import android.view.View;
import android.view.ViewGroup;

import com.android.systemui.car.hvac.HvacPanelController;
import com.android.systemui.car.hvac.HvacPanelOverlayViewController;
import com.android.systemui.statusbar.policy.ConfigurationController;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * An interface for controlling system bars.
 */
public interface CarSystemBarController extends ConfigurationController.ConfigurationListener {

    int LEFT = 0;
    int TOP = 1;
    int RIGHT = 2;
    int BOTTOM = 3;

    @IntDef(value = {LEFT, TOP, RIGHT, BOTTOM})
    @Target({ElementType.TYPE_PARAMETER, ElementType.TYPE_USE})
    @Retention(RetentionPolicy.SOURCE)
    @interface SystemBarSide {
    }

    /**
     * initializes the system bars.
     */
    void init();

    /**
     * Changes window visibility of the given system bar side.
     */
    boolean setBarVisibility(@SystemBarSide int side, @View.Visibility int visibility);

    /**
     * Returns the window of the given system bar side.
     */
    ViewGroup getBarWindow(@SystemBarSide int side);

    /**
     * Returns the view of the given system bar side.
     */
    CarSystemBarView getBarView(@SystemBarSide int side, boolean isSetUp);

    /**
     * Registers a touch listener callbar for the given system bar side.
     */
    void registerBarTouchListener(@SystemBarSide int side, View.OnTouchListener listener);

    /**
     * Registers a {@link HvacPanelController}
     */
    void registerHvacPanelController(HvacPanelController hvacPanelController);

    /**
     * Registers a {@link HvacPanelOverlayViewController}
     */
    void registerHvacPanelOverlayViewController(
            HvacPanelOverlayViewController hvacPanelOverlayViewController);
}
