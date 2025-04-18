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
package com.android.systemui.car.wm.scalableui.view;

import android.content.Context;
import android.util.Log;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.car.scalableui.model.PanelControllerMetadata;
import com.android.car.scalableui.panel.DecorPanelController;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

public abstract class ViewController implements DecorPanelController {
    private static final String TAG = ViewController.class.getSimpleName();
    private static final boolean DEBUG = true;
    protected final Context mContext;
    protected final String mViewName;
    protected final PanelControllerMetadata mMetadata;
    protected View mView;

    protected ViewController(Context context, PanelControllerMetadata metadata) {
        mContext = context;
        mMetadata = metadata;
        mViewName = metadata.getConfiguration(PanelControllerMetadata.VIEW_TAG);
        if (mViewName == null) {
            throw new RuntimeException("ViewName must be set " + metadata);
        }
    }

    private static View initView(Context context, String viewClassName) {
        if (viewClassName == null) {
            Log.e(TAG, "viewClassName is null");
            return null;
        }
        try {
            Class<?> clazz = Class.forName(viewClassName);
            if (View.class.isAssignableFrom(clazz)) {
                Constructor<?> constructor = clazz.getConstructor(Context.class);
                return (View) constructor.newInstance(context);
            }
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            // Handle the case where the class is not found
            Log.e(TAG, "Class or method not found not found: " + viewClassName, e);
        } catch (InvocationTargetException e) {
            Log.e(TAG, "InvocationTargetException: " + viewClassName, e);
        } catch (InstantiationException e) {
            Log.e(TAG, "InstantiationException: " + viewClassName, e);
        } catch (IllegalAccessException e) {
            Log.e(TAG, "IllegalAccessException: " + viewClassName, e);
        }
        return null;
    }

    /**
     * Initialized a {@link DecorPanelController}.
     */
    @Nullable
    public static DecorPanelController createDecorPanelController(@NonNull Context context,
            @Nullable PanelControllerMetadata metadata) {
        if (metadata == null) {
            logIfDebuggable("Metadata is null");
            return null;
        }
        String controllerName = metadata.getControllerName();

        logIfDebuggable("Init view provider with class name" + controllerName);
        try {
            Class<?> clazz = Class.forName(controllerName);
            if (ViewController.class.isAssignableFrom(clazz)) {
                Constructor<?> constructor = clazz.getConstructor(Context.class,
                        PanelControllerMetadata.class);
                return (ViewController) constructor.newInstance(context, metadata);
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

    protected Context getContext() {
        return mContext;
    }

    /**
     * Retrieves the {@link View} provided by this {@link ViewController}.
     */
    @Nullable
    public View getView() {
        mView = mView == null ? initView(mContext, mViewName) : mView;
        return mView;
    }

    protected static void logIfDebuggable(String msg) {
        if (DEBUG) {
            Log.d(TAG, msg);
        }
    }
}
