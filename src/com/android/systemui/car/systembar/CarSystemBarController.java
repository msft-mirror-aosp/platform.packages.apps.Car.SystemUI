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
import android.app.StatusBarManager.Disable2Flags;
import android.app.StatusBarManager.DisableFlags;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets.Type.InsetsType;
import android.view.WindowInsetsController;

import com.android.internal.statusbar.LetterboxDetails;
import com.android.internal.view.AppearanceRegion;
import com.android.systemui.car.hvac.HvacPanelController;
import com.android.systemui.car.hvac.HvacPanelOverlayViewController;
import com.android.systemui.car.notification.NotificationPanelViewController;
import com.android.systemui.car.notification.NotificationsShadeController;
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
     * See {@code CommandQueue.Callback#setImeWindowStatus}
     */
    void setImeWindowStatus(int displayId, int vis, int backDisposition,
                boolean showImeSwitcher);

    /**
     * See {@code CommandQueue.Callback#onSystemBarAttributesChanged}
     */
    void onSystemBarAttributesChanged(
                int displayId,
                @WindowInsetsController.Appearance int appearance,
                AppearanceRegion[] appearanceRegions,
                boolean navbarColorManagedByIme,
                @WindowInsetsController.Behavior int behavior,
                @InsetsType int requestedVisibleTypes,
                String packageName,
                LetterboxDetails[] letterboxDetails);

    /**
     * See {@code CommandQueue.Callback#showTransient}
     */
    void showTransient(int displayId, @InsetsType int types, boolean isGestureOnSystemBar);

    /**
     * See {@code CommandQueue.Callback#abortTransient}
     */
    void abortTransient(int displayId, @InsetsType int types);

    /**
     * See {@code CommandQueue.Callback#disable}
     */
    void disable(int displayId, @DisableFlags int state1, @Disable2Flags int state2,
                boolean animate);

    /**
     * See {@code CommandQueue.Callback#setSystemBarStates}
     */
    void setSystemBarStates(@DisableFlags int state, @DisableFlags int state2);

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
     * Shows all navigation buttons.
     */
    void showAllNavigationButtons(boolean isSetUp);

    /**
     * Shows all keugaurd buttons.
     */
    void showAllKeyguardButtons(boolean isSetUp);

    /**
     * Shows all occulusion buttons.
     */
    void showAllOcclusionButtons(boolean isSetUp);

    /**
     * Toggles all notification unseen indicator.
     */
    void toggleAllNotificationsUnseenIndicator(boolean isSetUp, boolean hasUnseen);

    /**
     * Registers a {@link HvacPanelController}
     */
    void registerHvacPanelController(HvacPanelController hvacPanelController);

    /**
     * Registers a {@link HvacPanelOverlayViewController}
     */
    void registerHvacPanelOverlayViewController(
            HvacPanelOverlayViewController hvacPanelOverlayViewController);

    /**
     * Registers a {@link NotificationsShadeController}
     */
    void registerNotificationController(
            NotificationsShadeController notificationsShadeController);

    /**
     * Registers a {@link NotificationPanelViewController}
     */
    void registerNotificationPanelViewController(
            NotificationPanelViewController notificationPanelViewController);
}
