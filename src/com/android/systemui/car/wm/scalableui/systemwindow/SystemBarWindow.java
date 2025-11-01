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

import static android.view.WindowInsets.Type.navigationBars;
import static android.view.WindowInsets.Type.statusBars;
import static android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;

import android.content.Context;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.os.Binder;
import android.os.Bundle;
import android.util.ArrayMap;
import android.view.Gravity;
import android.view.InsetsFrameProvider;
import android.view.WindowInsets;
import android.view.WindowManager;

import com.android.car.scalableui.loader.xml.SystemBarTagXmlParser;
import com.android.car.scalableui.model.PanelControllerMetadata;
import com.android.internal.annotations.VisibleForTesting;
import com.android.systemui.car.wm.scalableui.EventDispatcher;
import com.android.systemui.car.wm.scalableui.panel.panelupdates.PanelUpdateConsumer;

import java.util.Map;
import java.util.Optional;

import javax.inject.Inject;

/**
 * An implementation of {@link SystemUiWindow} specifically for system bars.
 */
public class SystemBarWindow extends SystemUiWindowBase {
    private static final Binder INSETS_OWNER = new Binder();
    private static final Map<String, InsetsFrameProvider> BAR_GESTURE_MAP = new ArrayMap<>();
    private static final Map<String, Integer> BAR_GRAVITY_MAP = new ArrayMap<>();
    private static final Map<String, String> BAR_TITLE_MAP = new ArrayMap<>();
    /*
            NOTE: The elements' order in the map below must be preserved as-is since the correct
            corresponding values are obtained by the index.
         */
    private static final InsetsFrameProvider[] BAR_PROVIDER_MAP = {
            new InsetsFrameProvider(INSETS_OWNER, 0 /* index */, statusBars()),
            new InsetsFrameProvider(INSETS_OWNER, 0 /* index */, navigationBars()),
            new InsetsFrameProvider(INSETS_OWNER, 1 /* index */, statusBars()),
            new InsetsFrameProvider(INSETS_OWNER, 1 /* index */, navigationBars()),
    };
    @VisibleForTesting
    static final int HUN_Z_ORDER = 10;
    private final SystemBarConfiguration mConfiguration;

    @Inject
    public SystemBarWindow(Context context, Optional<PanelUpdateConsumer> consumer,
            EventDispatcher dispatcher, String id) {
        super(context, consumer.get(), dispatcher, id);
        mConfiguration = new SystemBarConfiguration(consumer, getId());
        populateMaps();
    }

    private static void populateMaps() {
        BAR_GRAVITY_MAP.put(SystemBarTagXmlParser.SYSTEM_BAR_PANEL_TOP_ID, Gravity.TOP);
        BAR_GRAVITY_MAP.put(SystemBarTagXmlParser.SYSTEM_BAR_PANEL_BOTTOM_ID, Gravity.BOTTOM);
        BAR_GRAVITY_MAP.put(SystemBarTagXmlParser.SYSTEM_BAR_PANEL_LEFT_ID, Gravity.LEFT);
        BAR_GRAVITY_MAP.put(SystemBarTagXmlParser.SYSTEM_BAR_PANEL_RIGHT_ID, Gravity.RIGHT);

        BAR_TITLE_MAP.put(SystemBarTagXmlParser.SYSTEM_BAR_PANEL_TOP_ID, "TopCarSystemBar");
        BAR_TITLE_MAP.put(SystemBarTagXmlParser.SYSTEM_BAR_PANEL_BOTTOM_ID, "BottomCarSystemBar");
        BAR_TITLE_MAP.put(SystemBarTagXmlParser.SYSTEM_BAR_PANEL_LEFT_ID, "LeftCarSystemBar");
        BAR_TITLE_MAP.put(SystemBarTagXmlParser.SYSTEM_BAR_PANEL_RIGHT_ID, "RightCarSystemBar");

        BAR_GESTURE_MAP.put(SystemBarTagXmlParser.SYSTEM_BAR_PANEL_TOP_ID,
                new InsetsFrameProvider(INSETS_OWNER, 0 /* index */,
                        WindowInsets.Type.mandatorySystemGestures()));
        BAR_GESTURE_MAP.put(SystemBarTagXmlParser.SYSTEM_BAR_PANEL_BOTTOM_ID,
                new InsetsFrameProvider(INSETS_OWNER, 1 /* index */,
                        WindowInsets.Type.mandatorySystemGestures()));
        BAR_GESTURE_MAP.put(SystemBarTagXmlParser.SYSTEM_BAR_PANEL_LEFT_ID,
                new InsetsFrameProvider(INSETS_OWNER, 2 /* index */,
                        WindowInsets.Type.mandatorySystemGestures()));
        BAR_GESTURE_MAP.put(SystemBarTagXmlParser.SYSTEM_BAR_PANEL_RIGHT_ID,
                new InsetsFrameProvider(INSETS_OWNER, 3 /* index */,
                        WindowInsets.Type.mandatorySystemGestures()));
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
        SystemUiWindow.updateLayoutParams(lp, bounds, getDisplayMetrics());
        lp.setTitle(BAR_TITLE_MAP.get(getId()));
        lp.providedInsets = new InsetsFrameProvider[]{BAR_PROVIDER_MAP[mConfiguration.getType()],
                BAR_GESTURE_MAP.get(getId())};
        lp.setFitInsetsTypes(0);
        lp.windowAnimations = 0;
        lp.gravity = BAR_GRAVITY_MAP.get(getId());
        lp.layoutInDisplayCutoutMode = LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        lp.privateFlags = lp.privateFlags
                | WindowManager.LayoutParams.PRIVATE_FLAG_INTERCEPT_GLOBAL_DRAG_AND_DROP;
        return lp;
    }

    public static class SystemBarConfiguration {
        private final Bundle mMetadata;

        @Inject
        public SystemBarConfiguration(Optional<PanelUpdateConsumer> consumer, String id) {
            this(getMetadata(consumer, id));
        }

        private SystemBarConfiguration(Bundle metadata) {
            mMetadata = metadata;
        }

        private static Bundle getMetadata(Optional<PanelUpdateConsumer> consumer, String id) {
            if (consumer.isEmpty()) {
                throw new IllegalStateException("PanelUpdateConsumer must be present");
            }
            PanelUpdateConsumer panelUpdateConsumer = consumer.get();
            PanelControllerMetadata metadata = panelUpdateConsumer.getPanelControllerMetadata(id);
            if (metadata == null) {
                throw new IllegalStateException("PanelControllerMetadata must be present");
            }
            return metadata.getConfigurations();
        }

        /**
         * @return a {@link Bundle} that contains all of the configuration values
         */
        public Bundle getConfiguration() {
            return mMetadata;
        }

        /**
         * @return the relative Z-order of the SystemBar
         */
        public int getZOrder() {
            return mMetadata.getInt(SystemBarTagXmlParser.BAR_Z_ORDER_ATTRIBUTE);
        }

        /**
         * @return {@code true} if SystemBar should be displayed above HUN
         */
        public boolean isAboveHun() {
            return HUN_Z_ORDER >= getZOrder();
        }

        /**
         * @return type of SystemBar
         *
         * STATUS_BAR = 0
         * NAVIGATION_BAR = 1
         * STATUS_BAR_EXTRA = 2
         * NAVIGATION_BAR_EXTRA = 3
         */
        public int getType() {
            return mMetadata.getInt(SystemBarTagXmlParser.TYPE_ATTRIBUTE);
        }
    }
}
