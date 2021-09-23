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

package com.android.systemui.car.statusicon;

import static com.android.systemui.car.statusicon.StatusIconController.PANEL_CONTENT_LAYOUT_NONE;

import android.annotation.ArrayRes;
import android.annotation.LayoutRes;
import android.content.Context;
import android.content.res.Resources;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.android.systemui.R;
import com.android.systemui.broadcast.BroadcastDispatcher;
import com.android.systemui.car.CarServiceProvider;
import com.android.systemui.dagger.qualifiers.Main;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Map;

import javax.inject.Provider;

/**
 * A base controller for a view that contains a group of StatusIcons. It creates a button view for
 * each icon to display, instantiates {@link StatusIconController} instances associated with those
 * icons, and then registers those icons to those controllers.
 */
public abstract class StatusIconGroupContainerController {
    private final Context mContext;
    private final Resources mResources;
    private final CarServiceProvider mCarServiceProvider;
    private final BroadcastDispatcher mBroadcastDispatcher;
    private final Map<Class<?>, Provider<StatusIconController>> mIconControllerCreators;
    private final String mIconTag;
    private final String[] mStatusIconControllerNames;

    public StatusIconGroupContainerController(
            Context context,
            @Main Resources resources,
            CarServiceProvider carServiceProvider,
            BroadcastDispatcher broadcastDispatcher,
            Map<Class<?>, Provider<StatusIconController>> iconControllerCreators) {
        mContext = context;
        mResources = resources;
        mCarServiceProvider = carServiceProvider;
        mBroadcastDispatcher = broadcastDispatcher;
        mIconControllerCreators = iconControllerCreators;
        mIconTag = mResources.getString(R.string.qc_icon_tag);
        mStatusIconControllerNames = mResources.getStringArray(
                getStatusIconControllersStringArray());
    }

    private static <T> T resolve(String className, Map<Class<?>, Provider<T>> creators) {
        try {
            Class<?> clazz = Class.forName(className);
            Provider<T> provider = creators.get(clazz);
            return provider == null ? null : provider.get();
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    /**
     * Returns the layout res id to use as the button view that contains the StatusIcon.
     */
    @LayoutRes
    public int getButtonViewLayout() {
        return R.layout.default_status_icon;
    }

    /**
     * Adds Quick Control entry points to the provided container ViewGroup.
     */
    public void addIconViews(ViewGroup containerViewGroup) {
        LayoutInflater li = LayoutInflater.from(mContext);

        for (String clsName : mStatusIconControllerNames) {
            StatusIconController statusIconController = getStatusIconControllerByName(clsName);
            View entryPointView = li.inflate(getButtonViewLayout(),
                    containerViewGroup, /* attachToRoot= */ false);
            statusIconController.registerIconView(entryPointView.findViewWithTag(mIconTag));
            if (statusIconController.getPanelContentLayout() != PANEL_CONTENT_LAYOUT_NONE) {
                StatusIconPanelController panelController = new StatusIconPanelController(mContext,
                        mCarServiceProvider, mBroadcastDispatcher);
                panelController.attachPanel(entryPointView,
                        statusIconController.getPanelContentLayout(),
                        statusIconController.getPanelWidth());
            }
            containerViewGroup.addView(entryPointView);
        }
    }

    @ArrayRes
    protected abstract int getStatusIconControllersStringArray();

    private StatusIconController getStatusIconControllerByName(String className) {
        try {
            StatusIconController statusIconController = resolveStatusIconController(className);
            if (statusIconController == null) {
                Constructor constructor = Class.forName(className).getConstructor(Context.class);
                statusIconController = (StatusIconController) constructor.newInstance(this);
            }

            return statusIconController;
        } catch (ClassNotFoundException
                | NoSuchMethodException
                | IllegalAccessException
                | InstantiationException
                | InvocationTargetException ex) {
            throw new RuntimeException(ex);
        }
    }

    private StatusIconController resolveStatusIconController(String className) {
        return resolve(className, mIconControllerCreators);
    }
}
