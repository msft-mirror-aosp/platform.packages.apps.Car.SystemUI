/*
 * Copyright (C) 2024 The Android Open Source Project
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

import static com.android.systemui.car.systembar.CarSystemBarController.BOTTOM;
import static com.android.systemui.car.systembar.CarSystemBarController.LEFT;
import static com.android.systemui.car.systembar.CarSystemBarController.RIGHT;
import static com.android.systemui.car.systembar.CarSystemBarController.TOP;

import android.annotation.IdRes;
import android.content.Context;
import android.util.ArrayMap;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.LayoutRes;

import com.android.car.dockutil.Flags;
import com.android.systemui.R;
import com.android.systemui.car.systembar.CarSystemBarController.SystemBarSide;

import javax.inject.Inject;
import javax.inject.Provider;

/** A factory that creates and caches views for navigation bars. */
public class CarSystemBarViewFactoryImpl implements CarSystemBarViewFactory {

    private static final String TAG = CarSystemBarViewFactory.class.getSimpleName();
    private static final ArrayMap<Type, Integer> sLayoutMap = setupLayoutMapping();

    private static ArrayMap<Type, Integer> setupLayoutMapping() {
        ArrayMap<Type, Integer> map = new ArrayMap<>();
        map.put(Type.TOP, R.layout.car_top_system_bar);
        map.put(Type.TOP_WITH_DOCK, R.layout.car_top_system_bar_dock);
        map.put(Type.TOP_UNPROVISIONED, R.layout.car_top_system_bar_unprovisioned);
        map.put(Type.BOTTOM, R.layout.car_bottom_system_bar);
        map.put(Type.BOTTOM_WITH_DOCK, R.layout.car_bottom_system_bar_dock);
        map.put(Type.BOTTOM_UNPROVISIONED, R.layout.car_bottom_system_bar_unprovisioned);
        map.put(Type.LEFT, R.layout.car_left_system_bar);
        map.put(Type.LEFT_UNPROVISIONED, R.layout.car_left_system_bar_unprovisioned);
        map.put(Type.RIGHT, R.layout.car_right_system_bar);
        map.put(Type.RIGHT_UNPROVISIONED, R.layout.car_right_system_bar_unprovisioned);
        return map;
    }

    /** Type of navigation bar to be created. */
    private enum Type {
        TOP,
        TOP_WITH_DOCK,
        TOP_UNPROVISIONED,
        BOTTOM,
        BOTTOM_WITH_DOCK,
        BOTTOM_UNPROVISIONED,
        LEFT,
        LEFT_UNPROVISIONED,
        RIGHT,
        RIGHT_UNPROVISIONED
    }

    private final Context mContext;
    private final ArrayMap<Type, CarSystemBarViewController> mCachedViewControllerMap =
            new ArrayMap<>(Type.values().length);
    private final ArrayMap<Type, ViewGroup> mCachedContainerMap = new ArrayMap<>();
    private final Provider<CarSystemBarViewController.Factory>
            mCarSystemBarViewControllerFactoryProvider;

    @Inject
    public CarSystemBarViewFactoryImpl(
            Context context,
            Provider<CarSystemBarViewController.Factory>
                    carSystemBarViewControllerFactoryProvider) {
        mContext = context;
        mCarSystemBarViewControllerFactoryProvider = carSystemBarViewControllerFactoryProvider;
    }

    /** Gets the top window by side. */
    @Override
    public ViewGroup getWindow(@SystemBarSide int side) {
        switch (side) {
            case TOP:
                return getWindowCached(Type.TOP);
            case RIGHT:
                return getWindowCached(Type.RIGHT);
            case BOTTOM:
                return getWindowCached(Type.BOTTOM);
            case LEFT:
            default:
                return getWindowCached(Type.LEFT);
        }
    }

    /** Gets the bar by side. */
    @Override
    public CarSystemBarViewController getBar(@SystemBarSide int side, boolean isSetUp) {
        switch (side) {
            case TOP:
                if (Flags.dockFeature()) {
                    return getBar(isSetUp, Type.TOP_WITH_DOCK, Type.TOP_UNPROVISIONED);
                }
                return getBar(isSetUp, Type.TOP, Type.TOP_UNPROVISIONED);
            case RIGHT:
                return getBar(isSetUp, Type.RIGHT, Type.RIGHT_UNPROVISIONED);
            case BOTTOM:
                if (Flags.dockFeature()) {
                    return getBar(isSetUp, Type.BOTTOM_WITH_DOCK, Type.BOTTOM_UNPROVISIONED);
                }
                return  getBar(isSetUp, Type.BOTTOM, Type.BOTTOM_UNPROVISIONED);
            case LEFT:
            default:
                return getBar(isSetUp, Type.LEFT, Type.LEFT_UNPROVISIONED);
        }
    }

    private ViewGroup getWindowCached(Type type) {
        if (mCachedContainerMap.containsKey(type)) {
            return mCachedContainerMap.get(type);
        }

        ViewGroup window = (ViewGroup) View.inflate(mContext,
                R.layout.navigation_bar_window, /* root= */ null);
        window.setId(getWindowId(type));
        mCachedContainerMap.put(type, window);
        return mCachedContainerMap.get(type);
    }

    @IdRes
    private int getWindowId(Type type) {
        return switch (type) {
            case TOP -> R.id.car_top_bar_window;
            case BOTTOM -> R.id.car_bottom_bar_window;
            case LEFT -> R.id.car_left_bar_window;
            case RIGHT -> R.id.car_right_bar_window;
            default -> throw new IllegalArgumentException("unknown system bar window type " + type);
        };
    }

    private CarSystemBarViewController getBar(boolean isSetUp, Type provisioned,
            Type unprovisioned) {
        CarSystemBarViewController controller = getBarCached(isSetUp, provisioned, unprovisioned);

        if (controller == null) {
            String name = isSetUp ? provisioned.name() : unprovisioned.name();
            Log.e(TAG, "CarStatusBar failed inflate for " + name);
            throw new RuntimeException(
                    "Unable to build " + name + " nav bar due to missing layout");
        }
        return controller;
    }

    private CarSystemBarViewController getBarCached(boolean isSetUp, Type provisioned,
            Type unprovisioned) {
        Type type = isSetUp ? provisioned : unprovisioned;
        if (mCachedViewControllerMap.containsKey(type)) {
            return mCachedViewControllerMap.get(type);
        }

        Integer barLayoutInteger = sLayoutMap.get(type);
        if (barLayoutInteger == null) {
            return null;
        }
        @LayoutRes int barLayout = barLayoutInteger;
        CarSystemBarView view = (CarSystemBarView) View.inflate(mContext, barLayout,
                /* root= */ null);

        CarSystemBarViewController controller = mCarSystemBarViewControllerFactoryProvider.get()
                .create(view);
        controller.init();

        mCachedViewControllerMap.put(type, controller);
        return mCachedViewControllerMap.get(type);
    }

    /** Resets the cached system bar views. */
    @Override
    public void resetSystemBarViewCache() {
        mCachedViewControllerMap.clear();
    }

    /** Resets the cached system bar windows and system bar views. */
    @Override
    public void resetSystemBarWindowCache() {
        resetSystemBarViewCache();
        mCachedContainerMap.clear();
    }
}
