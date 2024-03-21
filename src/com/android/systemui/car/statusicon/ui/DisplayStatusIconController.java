/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.systemui.car.statusicon.ui;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;

import com.android.systemui.R;
import com.android.systemui.car.statusicon.StatusIconView;
import com.android.systemui.car.statusicon.StatusIconViewController;
import com.android.systemui.car.systembar.element.CarSystemBarElementStateController;
import com.android.systemui.car.systembar.element.CarSystemBarElementStatusBarDisableController;
import com.android.systemui.dagger.qualifiers.Main;

import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;
import dagger.assisted.AssistedInject;

/**
 * A controller for Display status icon.
 */
public class DisplayStatusIconController extends StatusIconViewController {

    private final Drawable mDisplayBrightnessDrawable;
    private final String mDisplayBrightnessContentDescription;

    @AssistedInject
    DisplayStatusIconController(
            @Assisted StatusIconView view,
            CarSystemBarElementStatusBarDisableController disableController,
            CarSystemBarElementStateController stateController,
            Context context, @Main Resources resources) {
        super(view, disableController, stateController);
        mDisplayBrightnessDrawable = resources.getDrawable(R.drawable.car_ic_brightness,
                context.getTheme());
        mDisplayBrightnessContentDescription = resources.getString(
                R.string.status_icon_display_status);
        updateStatus();
    }

    @AssistedFactory
    public interface Factory extends
            StatusIconViewController.Factory<DisplayStatusIconController> {
    }

    @Override
    protected void updateStatus() {
        setIconDrawableToDisplay(mDisplayBrightnessDrawable);
        setIconContentDescription(mDisplayBrightnessContentDescription);
        onStatusUpdated();
    }
}
