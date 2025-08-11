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
package com.android.systemui.car.wm.scalableui.systemwindow;

import static android.view.WindowInsets.Type.mandatorySystemGestures;
import static android.view.WindowInsets.Type.navigationBars;
import static android.view.WindowInsets.Type.statusBars;
import static android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;

import static com.android.systemui.car.systembar.SystemBarConfigs.TYPE_STATUS_BAR;

import android.content.Context;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.os.Binder;
import android.util.DisplayMetrics;
import android.view.InsetsFrameProvider;
import android.view.View;
import android.view.WindowManager;

import androidx.annotation.NonNull;

import com.android.systemui.car.wm.scalableui.EventDispatcher;
import com.android.systemui.car.wm.scalableui.configuration.SystemBarConfiguration;
import com.android.systemui.car.wm.scalableui.panel.panelupdates.PanelUpdateConsumer;

import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;
import dagger.assisted.AssistedInject;

/**
 * An implementation of {@link SystemUiWindow} specifically for system bars.
 */
public class SystemBarWindow extends SystemUiWindowBase {
    /**
     * Pre-defined HUN z order
     */
    public static final int HUN_Z_ORDER = 10;
    private static final Binder INSETS_OWNER = new Binder();
    private final DisplayMetrics mDisplayMetrics;
    private final SystemBarConfiguration mConfiguration;

    @AssistedInject
    public SystemBarWindow(Context context, EventDispatcher dispatcher,
            @Assisted PanelUpdateConsumer consumer, @Assisted SystemBarConfiguration config) {
        super(context, consumer, dispatcher, config.getName());
        mDisplayMetrics = context.getResources().getDisplayMetrics();
        mConfiguration = config;

        consumer.registerCallback(mConfiguration.getName(),
                new PanelUpdateConsumer.PanelUpdateCallback() {
                    @Override
                    public void onAlphaChange(@NonNull String panelId, float alpha) {
                        if (get_rootView() == null) {
                            return;
                        }
                        get_rootView().setAlpha(alpha);
                    }

                    @Override
                    public void onVisibilityChange(@NonNull String panelId, boolean isVisible) {
                        if (get_rootView() == null) {
                            return;
                        }
                        get_rootView().setVisibility(isVisible ? View.VISIBLE : View.GONE);
                    }
                });
    }

    private static int mapZOrderToBarType(int zOrder) {
        return zOrder >= HUN_Z_ORDER ? WindowManager.LayoutParams.TYPE_NAVIGATION_BAR_PANEL
                : WindowManager.LayoutParams.TYPE_STATUS_BAR_ADDITIONAL;
    }

    @Override
    public WindowManager.LayoutParams getLayoutParams() {
        Rect bounds = getPanelUpdateConsumer().getBounds(getId());
        if (bounds == null) {
            return null;
        }
        return getLayoutParamsFromBounds(bounds);
    }

    private WindowManager.LayoutParams getLayoutParamsFromBounds(Rect bounds) {
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                mapZOrderToBarType(mConfiguration.getZOrder()),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                        | WindowManager.LayoutParams.FLAG_SPLIT_TOUCH, PixelFormat.TRANSLUCENT);
        SystemUiWindow.updateLayoutParams(lp, bounds, mDisplayMetrics);
        lp.setTitle(mConfiguration.getName());
        lp.providedInsets = new InsetsFrameProvider[]{new InsetsFrameProvider(INSETS_OWNER,
                mConfiguration.getIndex(),
                TYPE_STATUS_BAR == mConfiguration.getType() ? statusBars() : navigationBars()),
                new InsetsFrameProvider(INSETS_OWNER, getMandatorySystemGesturesIndex(),
                        mandatorySystemGestures())};
        lp.setFitInsetsTypes(0);
        lp.windowAnimations = 0;
        lp.layoutInDisplayCutoutMode = LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        lp.privateFlags = lp.privateFlags
                | WindowManager.LayoutParams.PRIVATE_FLAG_INTERCEPT_GLOBAL_DRAG_AND_DROP;
        return lp;
    }

    /**
     * Calculates the index for the {@link InsetsFrameProvider} for
     * {@link WindowInsets.Type#mandatorySystemGestures}.
     *
     * <p>For status bars, this index is the same as the bar's own index. For navigation bars, an
     * offset is added to prevent index collisions with status bars, ensuring each gesture provider
     * has a unique index.
     *
     * @return The unique index for the mandatory system gestures provider.
     */
    private int getMandatorySystemGesturesIndex() {
        if (TYPE_STATUS_BAR == mConfiguration.getType()) {
            return mConfiguration.getIndex();
        }
        return mConfiguration.getMandatorySystemGestureIndexOffset() + mConfiguration.getIndex();
    }

    @AssistedFactory
    public interface Factory {
        /**
         * Create instance of {@link SystemBarWindow} with specified {@link SystemBarConfiguration}
         * and a {@link PanelUpdateConsumer}
         */
        SystemBarWindow create(PanelUpdateConsumer consumer, SystemBarConfiguration config);
    }
}