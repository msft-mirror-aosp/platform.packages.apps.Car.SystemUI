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
import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.util.Log;

import androidx.annotation.GuardedBy;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.android.car.scalableui.model.PanelControllerMetadata;
import com.android.car.scalableui.panel.TaskPanelController;
import com.android.car.scalableui.panel.TaskPanelHandler;
import com.android.systemui.car.wm.scalableui.panel.PanelUtils;

import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;
import dagger.assisted.AssistedInject;

import java.net.URISyntaxException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A base controller for managing {@link com.android.systemui.car.wm.scalableui.panel.TaskPanel}.
 *
 * <p>This class provides a foundational implementation for {@link TaskPanelController},
 * handling common tasks such as initializing based on {@link PanelControllerMetadata},
 * managing persistent activities, setting a default component, and responding to
 * application installation and uninstallation events. Subclasses can extend this
 * class to implement specific panel behaviors.
 */
public class BaseTaskPanelController implements TaskPanelController {
    private static final String TAG = BaseTaskPanelController.class.getSimpleName();
    protected static final boolean DEBUG = Build.isDebuggable();
    private static final String PACKAGE_DATA_SCHEME = "package";
    protected final Context mContext;
    @NonNull
    private final Object mLock = new Object();
    @NonNull
    private final PanelControllerMetadata mPanelControllerMetadata;
    @NonNull
    private final Set<ComponentName> mPersistentActivities;
    @NonNull
    private final PanelUtils mPanelUtils;
    @Nullable
    private ComponentName mDefaultComponent;
    @Nullable
    private Intent mUpdateFilter;
    @GuardedBy("mLock")
    @Nullable
    private TaskPanelHandler mTaskPanelHandler;

    /**
     * Constructs a new {@code BaseTaskPanelController}.
     *
     * @param context                 The application context.
     * @param panelControllerMetadata The metadata associated with this panel controller,
     *                                containing configuration information.
     */
    @AssistedInject
    public BaseTaskPanelController(@NonNull Context context,
            @NonNull @Assisted PanelControllerMetadata panelControllerMetadata,
            @NonNull PanelUtils panelUtils) {
        mContext = context;
        mPanelControllerMetadata = panelControllerMetadata;
        mPersistentActivities = new HashSet<>();
        mPanelUtils = panelUtils;
        init(panelControllerMetadata);
    }

    /**
     * Creates an instance of BaseTaskPanelController using the provided PanelControllerMetadata.
     */
    @AssistedFactory
    public interface Factory extends TaskPanelController.Factory<BaseTaskPanelController> {
        BaseTaskPanelController create(PanelControllerMetadata metadata);
    }

    private void init(PanelControllerMetadata metadata) {
        mDefaultComponent = parseDefaultComponent(metadata);
        mUpdateFilter = parseUpdateFilter(metadata);
        if (mUpdateFilter != null) {
            registerApplicationInstallUninstallReceiver();
        }
        updatePersistentActivities();
        logIfDebuggable("Panel Controller init: " + this);
    }

    private Intent parseUpdateFilter(@NonNull PanelControllerMetadata metadata) {
        String intentString = metadata.getStringConfiguration(
                PanelControllerMetadata.UPDATABLE_INTENT_FILTER);
        return getIntentFromString(intentString);
    }

    private ComponentName parseDefaultComponent(@NonNull PanelControllerMetadata metadata) {
        String defaultIntentString =
                metadata.getStringConfiguration(
                        PanelControllerMetadata.DEFAULT_COMPONENT);
        return defaultIntentString == null ? null : ComponentName.unflattenFromString(
                defaultIntentString);
    }

    private void registerApplicationInstallUninstallReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addDataScheme(PACKAGE_DATA_SCHEME);
        mContext.registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                updatePersistentActivities();
            }
        }, filter, Context.RECEIVER_EXPORTED);
    }

    @SuppressLint("MissingPermission")
    @VisibleForTesting
    void updatePersistentActivities() {
        mPersistentActivities.clear();
        if (mUpdateFilter != null) {
            List<ResolveInfo> result = mContext.getPackageManager().queryIntentActivitiesAsUser(
                    mUpdateFilter, PackageManager.MATCH_ALL, ActivityManager.getCurrentUser());
            for (ResolveInfo info : result) {
                if (info == null || info.activityInfo == null
                        || info.activityInfo.getComponentName() == null) {
                    continue;
                }
                if (mPersistentActivities.add(info.activityInfo.getComponentName())) {
                    logIfDebuggable("adding the following component to show on fullscreen: "
                            + info.activityInfo.getComponentName());
                }
            }
        }
        mPersistentActivities.addAll(
                mPanelUtils.parsePersistentActivitiesFromPackages(mPanelControllerMetadata,
                        PanelControllerMetadata.PERSISTENT_PACKAGE));
        mPersistentActivities.addAll(parsePersistentActivities(mPanelControllerMetadata,
                PanelControllerMetadata.PERSISTENT_ACTIVITY));
        synchronized (mLock) {
            if (mTaskPanelHandler != null) {
                mTaskPanelHandler.onApplicationChanged();
            }
        }
    }

    protected void logIfDebuggable(String s) {
        if (DEBUG) {
            Log.d(TAG, s);
        }
    }

    @Nullable
    private Intent getIntentFromString(@Nullable String string) {
        if (string == null) {
            return null;
        }
        try {
            return Intent.parseUri(string, Intent.URI_ANDROID_APP_SCHEME);
        } catch (URISyntaxException e) {
            Log.e(TAG, "Fail to parse intent string" + string + ", e=" + e);
            return null;
        }
    }

    private Set<ComponentName> parsePersistentActivities(
            @NonNull PanelControllerMetadata panelControllerMetadata, @NonNull String configName) {
        Set<ComponentName> set = new HashSet<>();
        if (!mPanelControllerMetadata.hasConfiguration(configName)) {
            return set;
        }
        List<String> list = panelControllerMetadata.getListConfiguration(configName);
        if (list == null) {
            String value = panelControllerMetadata.getStringConfiguration(configName);
            if (value != null) {
                set.add(ComponentName.unflattenFromString(value));
            }
        } else {
            for (String item : list) {
                ComponentName componentName = ComponentName.unflattenFromString(item);
                if (componentName == null) {
                    continue;
                }
                set.add(componentName);
            }
        }
        return set;
    }

    @Override
    public Intent getDefaultComponent() {
        Intent intent = new Intent();
        intent.setComponent(mDefaultComponent);
        logIfDebuggable("getDefaultComponent =  " + intent);
        return intent;
    }

    @Override
    @NonNull
    public Set<ComponentName> getPersistentActivities() {
        return mPersistentActivities;
    }

    @Override
    public void registerTaskPanelHandler(TaskPanelHandler taskPanelHandler) {
        synchronized (mLock) {
            mTaskPanelHandler = taskPanelHandler;
        }
    }

    @Override
    public boolean handles(ComponentName componentName) {
        return mPersistentActivities.contains(componentName);
    }

    @Override
    public String toString() {
        String persistentActivities = mPersistentActivities.stream().map(
                ComponentName::toString).collect(Collectors.joining(","));
        return "PanelController{"
                + "mPanelControllerMetadata=" + mPanelControllerMetadata
                + ", mPersistentActivities=" + persistentActivities
                + ", mDefaultComponent=" + getDefaultComponent()
                + ", mUpdateFilter=" + mUpdateFilter
                + '}';
    }
}
