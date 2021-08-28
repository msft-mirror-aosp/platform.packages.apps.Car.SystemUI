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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.android.systemui.R;
import com.android.systemui.car.statusicon.StatusIconController;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Provider;

/**
 * A controller for Quick Control Entry Points. It creates a button view for each icon to display,
 * instantiates {@link StatusIconController}s associated with those icons, and then registers those
 * icons to those controllers.
 */
public class QuickControlsEntryPointsController {
    private final Context mContext;
    private final Map<Class<?>, Provider<StatusIconController>> mIconControllerCreators;
    private final String mIconTag;

    private String[] mStatusIconControllerNames;

    @Inject
    QuickControlsEntryPointsController(
            Context context,
            Map<Class<?>, Provider<StatusIconController>> iconControllerCreators) {
        mContext = context;
        mIconControllerCreators = iconControllerCreators;
        mIconTag = mContext.getResources().getString(R.string.qc_icon_tag);
    }

    /**
     * Adds Quick Control entry points to the provided container ViewGroup.
     */
    public void addQuickControlEntryPoints(ViewGroup containerViewGroup) {
        if (mStatusIconControllerNames == null) {
            mStatusIconControllerNames = mContext.getResources().getStringArray(
                    R.array.config_quickControlsEntryPointIconControllers);
        }

        LayoutInflater li = LayoutInflater.from(mContext);

        for (String clsName : mStatusIconControllerNames) {
            StatusIconController statusIconController = getStatusIconControllerByName(clsName);
            View entryPointView = li.inflate(R.layout.car_qc_entry_points_button,
                    containerViewGroup, /* attachToRoot= */ false);
            statusIconController.registerIconView(entryPointView.findViewWithTag(mIconTag));
            containerViewGroup.addView(entryPointView);
        }
    }

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

    private static <T> T resolve(String className, Map<Class<?>, Provider<T>> creators) {
        try {
            Class<?> clazz = Class.forName(className);
            Provider<T> provider = creators.get(clazz);
            return provider == null ? null : provider.get();
        } catch (ClassNotFoundException e) {
            return null;
        }
    }
}
