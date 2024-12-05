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

import android.content.Context;
import android.view.InsetsFrameProvider;
import android.view.ViewGroup;
import android.view.WindowManager;

import androidx.annotation.Nullable;

import com.android.systemui.car.systembar.CarSystemBarController.SystemBarSide;

import java.util.List;

/**
 *  Interface for classes that provide system bar configurations.
 */
public interface SystemBarConfigs {

    /**
     * Invalidate cached resources and fetch from resources config file.
     *
     * <p>
     * This method should be called when the system bar configurations need to be refreshed,
     * such as when an RRO (Runtime Resource Overlay) is applied.
     * </p>
     */
    void resetSystemBarConfigs();

    /**
     * When creating system bars or overlay windows, use a WindowContext
     * for that particular window type to ensure proper display metrics.
     */
    Context getWindowContextBySide(@SystemBarSide int side);

    /**
     * @return The system bar view for the given side. {@code null} if side is unknown.
     */
    @Nullable
    ViewGroup getSystemBarLayoutBySide(@SystemBarSide int side, boolean isSetUp);

    /**
     * @return the systembar window for the given side. {@code null} if side is unknown.
     */
    @Nullable
    ViewGroup getWindowLayoutBySide(@SystemBarSide int side);

    /**
     * @return The {@link WindowManager.LayoutParams}, or {@code null} if the side is unknown
     * or the system bar is not enabled.
     */
    WindowManager.LayoutParams getLayoutParamsBySide(@SystemBarSide int side);

    /**
     * @return {@code true} if the system bar is enabled, {@code false} otherwise.
     */
    boolean getEnabledStatusBySide(@SystemBarSide int side);

    /**
     * @return {@code true} if the system bar should be hidden, {@code false} otherwise.
     */
    boolean getHideForKeyboardBySide(@SystemBarSide int side);

    /**
     * Applies padding to the given system bar view.
     *
     * @param view The system bar view
     */
    void insetSystemBar(@SystemBarSide int side, ViewGroup view);

    /**
     * @return A list of system bar sides sorted by their Z order.
     */
    List<@SystemBarSide Integer> getSystemBarSidesByZOrder();

    /**
     * @return one of the following values, or {@code -1} if the side is unknown
     * STATUS_BAR = 0
     * NAVIGATION_BAR = 1
     * STATUS_BAR_EXTRA = 2
     * NAVIGATION_BAR_EXTRA = 3
     */
    int getSystemBarInsetTypeBySide(@SystemBarSide int side);

    /**
     * @param index must be one of the following values
     * STATUS_BAR = 0
     * NAVIGATION_BAR = 1
     * STATUS_BAR_EXTRA = 2
     * NAVIGATION_BAR_EXTRA = 3
     * see {@link #getSystemBarInsetTypeBySide(int)}
     *
     * @return The {@link InsetsFrameProvider}, or {@code null} if the side is unknown
     */
    InsetsFrameProvider getInsetsFrameProvider(int index);

    /**
     * @return whether the left toolbar is used for display compat.
     */
    boolean isLeftDisplayCompatToolbarEnabled();

    /**
     * @return whether the right toolbar is used for display compat.
     */
    boolean isRightDisplayCompatToolbarEnabled();
}
