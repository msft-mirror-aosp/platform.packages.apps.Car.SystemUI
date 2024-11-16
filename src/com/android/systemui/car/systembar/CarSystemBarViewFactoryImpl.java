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

import android.content.Context;
import android.util.Log;
import android.util.SparseArray;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.LayoutRes;

import com.android.systemui.car.systembar.CarSystemBarController.SystemBarSide;

import java.util.Map;

import javax.inject.Inject;

/** A factory that creates and caches views for navigation bars. */
public class CarSystemBarViewFactoryImpl implements CarSystemBarViewFactory {

    private static final String TAG = CarSystemBarViewFactory.class.getSimpleName();

    private final Context mContext;
    // Map<@LayoutRes Integer, CarSystemBarViewController>
    private final SparseArray<CarSystemBarViewController> mCachedViewControllerMap =
            new SparseArray<>();
    // Map<@SystemBarSide Integer, ViewGroup>
    private final SparseArray<ViewGroup> mCachedWindowMap = new SparseArray<>();
    private final Map<@SystemBarSide Integer,
            CarSystemBarViewControllerFactory> mFactoriesMap;
    private final SystemBarConfigs mSystemBarConfigs;

    @Inject
    public CarSystemBarViewFactoryImpl(
            Context context,
            Map<@SystemBarSide Integer,
                    CarSystemBarViewControllerFactory> factoriesMap,
            SystemBarConfigs systemBarConfigs) {
        mContext = context;
        mFactoriesMap = factoriesMap;
        mSystemBarConfigs = systemBarConfigs;
    }

    /** Gets the top window by side. */
    @Override
    public ViewGroup getSystemBarWindow(@SystemBarSide int side) {
        return getWindowCached(side);
    }

    /** Gets the bar by side. */
    @Override
    public CarSystemBarViewController getSystemBarViewController(@SystemBarSide int side,
            boolean isSetUp) {
        CarSystemBarViewController controller = getBarCached(side, isSetUp);

        if (controller == null) {
            Log.e(TAG, "system bar failed inflate for side " + side + " setup " + isSetUp);
            throw new RuntimeException(
                    "Unable to inflate system bar for side " + side + " setup " + isSetUp
                    + " due to missing layout");
        }
        return controller;
    }

    private ViewGroup getWindowCached(@SystemBarSide int side) {
        if (mCachedWindowMap.get(side) != null) {
            return mCachedWindowMap.get(side);
        }

        ViewGroup window = (ViewGroup) View.inflate(mContext,
                mSystemBarConfigs.getWindowLayoutBySide(side), /* root= */ null);
        window.setId(mSystemBarConfigs.getWindowIdBySide(side));
        mCachedWindowMap.put(side, window);
        return window;
    }

    private CarSystemBarViewController getBarCached(@SystemBarSide int side, boolean isSetUp) {
        @LayoutRes int barLayout = mSystemBarConfigs.getSystemBarLayoutBySide(side, isSetUp);
        if (barLayout == 0) {
            return null;
        }
        if (mCachedViewControllerMap.get(barLayout) != null) {
            return mCachedViewControllerMap.get(barLayout);
        }

        ViewGroup view = (ViewGroup) View.inflate(mContext, barLayout, /* root= */ null);

        CarSystemBarViewController controller = mFactoriesMap.get(side).create(side, view);
        controller.init();

        mCachedViewControllerMap.put(barLayout, controller);
        return controller;
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
        mCachedWindowMap.clear();
    }
}
