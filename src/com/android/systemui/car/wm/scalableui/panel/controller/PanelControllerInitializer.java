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
package com.android.systemui.car.wm.scalableui.panel.controller;

import android.annotation.NonNull;
import android.content.Context;
import android.os.Build;
import android.util.Log;

import androidx.annotation.Nullable;

import com.android.car.scalableui.model.PanelControllerMetadata;
import com.android.car.scalableui.panel.TaskPanelController;
import com.android.systemui.car.wm.scalableui.panel.PanelUtils;
import com.android.wm.shell.dagger.WMSingleton;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import javax.inject.Inject;

/**
 * Initializes {@link TaskPanelController} instances based on provided metadata.
 *
 * <p>This class is responsible for dynamically creating instances of {@link TaskPanelController}
 * by using the controller class name specified in the {@link PanelControllerMetadata}. It handles
 * potential exceptions that may occur during class loading and instantiation.
 *
 * TODO(411549493): Move DecorPanelController here.
 */
@WMSingleton
public class PanelControllerInitializer {
    private static final boolean DEBUG = Build.isDebuggable();
    private static final String TAG = PanelControllerInitializer.class.getSimpleName();
    private final Context mContext;
    private final PanelUtils mPanelUtils;

    @Inject
    public PanelControllerInitializer(@NonNull Context context,
            @NonNull PanelUtils panelUtils) {
        mContext = context;
        mPanelUtils = panelUtils;
    }

    /**
     * Creates a {@link TaskPanelController} instance based on the provided metadata.
     *
     * <p>This method attempts to load the class specified by
     * {@link PanelControllerMetadata#getControllerName()},
     * ensures it is a subclass of {@link TaskPanelController}, and then instantiates it using a
     * constructor that accepts a {@link Context} and a {@link PanelControllerMetadata}.
     *
     * @param metadata The metadata containing information about the panel controller to create.
     *                 If {@code null}, this method will return {@code null}.
     * @return A new instance of {@link TaskPanelController} if successful, otherwise {@code null}.
     */
    public TaskPanelController createTaskPanelController(
            @Nullable PanelControllerMetadata metadata) {
        if (metadata == null) {
            logIfDebuggable("Metadata is null");
            return null;
        }
        String controllerName = metadata.getControllerName();

        logIfDebuggable("Init TaskPanelController with class name" + controllerName);
        try {
            Class<?> clazz = Class.forName(controllerName);
            if (TaskPanelController.class.isAssignableFrom(clazz)) {
                Constructor<?> constructor = clazz.getConstructor(Context.class,
                        PanelControllerMetadata.class, PanelUtils.class);
                //TODO(b/411549493): move to factory pattern.
                return (TaskPanelController) constructor.newInstance(mContext, metadata,
                        mPanelUtils);
            }
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            // Handle the case where the class is not found
            Log.e(TAG, "Class not found: " + controllerName, e);
        } catch (InvocationTargetException e) {
            Log.e(TAG, "InvocationTargetException: " + controllerName, e);
        } catch (InstantiationException e) {
            Log.e(TAG, "InstantiationException: " + controllerName, e);
        } catch (IllegalAccessException e) {
            Log.e(TAG, "IllegalAccessException: " + controllerName, e);
        }
        return null;
    }

    private static void logIfDebuggable(String msg) {
        if (DEBUG) {
            Log.d(TAG, msg);
        }
    }
}
