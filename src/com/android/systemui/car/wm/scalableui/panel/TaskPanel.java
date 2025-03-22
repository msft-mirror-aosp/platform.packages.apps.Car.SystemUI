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
package com.android.systemui.car.wm.scalableui.panel;

import android.app.ActivityManager;
import android.app.ActivityOptions;
import android.app.PendingIntent;
import android.car.app.CarActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.UserHandle;
import android.util.ArraySet;
import android.util.Log;
import android.view.SurfaceControl;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.android.car.internal.dep.Trace;
import com.android.car.scalableui.model.PanelState;
import com.android.car.scalableui.panel.Panel;
import com.android.systemui.car.CarServiceProvider;
import com.android.systemui.car.wm.scalableui.AutoTaskStackHelper;
import com.android.wm.shell.automotive.AutoTaskStackController;
import com.android.wm.shell.automotive.AutoTaskStackState;
import com.android.wm.shell.automotive.AutoTaskStackTransaction;
import com.android.wm.shell.automotive.RootTaskStack;
import com.android.wm.shell.automotive.RootTaskStackListener;

import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;
import dagger.assisted.AssistedInject;

import java.util.Set;

/**
 * A {@link RootTaskStack} based implementation of a {@link Panel}.
 */
public final class TaskPanel extends BasePanel {
    private static final String TAG = TaskPanel.class.getSimpleName();
    private static final String ROLE_TYPE_STRING = "string";
    private static final String ROLE_TYPE_ARRAY = "array";
    private static final boolean DEBUG = Build.isDebuggable();

    private final AutoTaskStackController mAutoTaskStackController;
    private final CarServiceProvider mCarServiceProvider;
    private final Set<ComponentName> mPersistedActivities;
    private final AutoTaskStackHelper mAutoTaskStackHelper;
    private CarActivityManager mCarActivityManager;
    private int mRootTaskId = -1;
    private SurfaceControl mLeash;
    private boolean mIsLaunchRoot;
    private RootTaskStack mRootTaskStack;
    private PanelUtils mPanelUtils;

    @AssistedInject
    public TaskPanel(AutoTaskStackController autoTaskStackController,
            @NonNull Context context,
            CarServiceProvider carServiceProvider,
            AutoTaskStackHelper autoTaskStackHelper,
            PanelUtils panelUtils,
            @Assisted String id) {
        super(context, id);
        mAutoTaskStackController = autoTaskStackController;
        mCarServiceProvider = carServiceProvider;
        mAutoTaskStackHelper = autoTaskStackHelper;
        mPersistedActivities = new ArraySet<>();
        mPanelUtils = panelUtils;
    }

    /**
     * Initializes the panel with the RootTask. This must be called after the state has been set.
     */
    @Override
    public void init() {
        mCarServiceProvider.addListener(
                car -> {
                    mCarActivityManager = car.getCarManager(CarActivityManager.class);
                    trySetPersistentActivity();
                });

        mAutoTaskStackController.createRootTaskStack(getDisplayId(), getPanelId(),
                new RootTaskStackListener() {
                    @Override
                    public void onRootTaskStackCreated(@NonNull RootTaskStack rootTaskStack) {
                        if (DEBUG) {
                            Log.d(TAG, getPanelId() + ", onRootTaskStackCreated " + rootTaskStack);
                        }
                        mRootTaskStack = rootTaskStack;
                        mRootTaskId = mRootTaskStack.getRootTaskInfo().taskId;
                        trySetPersistentActivity();
                        if (mIsLaunchRoot) {
                            mAutoTaskStackController.setDefaultRootTaskStackOnDisplay(
                                    getDisplayId(),
                                    mRootTaskId);
                        }

                        if (mPanelUtils.isUserUnlocked()) {
                            reset();
                        }
                    }

                    @Override
                    public void onRootTaskStackInfoChanged(@NonNull RootTaskStack rootTaskStack) {
                        mRootTaskStack = rootTaskStack;
                        mRootTaskId = mRootTaskStack.getRootTaskInfo().taskId;
                    }

                    @Override
                    public void onRootTaskStackDestroyed(@NonNull RootTaskStack rootTaskStack) {
                        mRootTaskStack = null;
                        mRootTaskId = -1;
                    }

                    @Override
                    public void onTaskAppeared(ActivityManager.RunningTaskInfo taskInfo,
                            SurfaceControl leash) {
                        mAutoTaskStackHelper.setTaskUntrimmableIfNeeded(taskInfo);
                    }

                    @Override
                    public void onTaskVanished(ActivityManager.RunningTaskInfo taskInfo) {
                        // no-op
                    }
                });
    }

    @Override
    public void reset() {
        if (getRootStack() == null) {
            Log.e(TAG, "Cannot reset when root stack is null for panel" + getPanelId());
            return;
        }
        AutoTaskStackTransaction autoTaskStackTransaction = new AutoTaskStackTransaction();
        AutoTaskStackState autoTaskStackState = new AutoTaskStackState(getBounds(), isVisible(),
                getLayer());
        autoTaskStackTransaction.setTaskStackState(getRootStack().getId(), autoTaskStackState);
        if (isVisible()) {
            setBaseIntent(autoTaskStackTransaction);
        }
        mAutoTaskStackController.startTransition(autoTaskStackTransaction);
    }

    private void setBaseIntent(AutoTaskStackTransaction autoTaskStackTransaction) {
        if (getDefaultIntent() == null || getRootStack().getRootTaskInfo() == null) {
            return;
        }
        Trace.beginSection(TAG + "#setBaseIntent");
        Intent defaultIntent = getDefaultIntent();
        ActivityOptions options = ActivityOptions.makeBasic();
        options.setPendingIntentBackgroundActivityStartMode(
                ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS);
        options.setLaunchRootTask(getRootStack().getRootTaskInfo().token);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                getContext().createContextAsUser(UserHandle.CURRENT, 0), 0, defaultIntent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        autoTaskStackTransaction.sendPendingIntent(pendingIntent, defaultIntent,
                options.toBundle());
        Trace.endSection();
    }

    @Nullable
    public RootTaskStack getRootStack() {
        return mRootTaskStack;
    }

    /**
     * Returns the task ID of the root task associated with this panel.
     */
    public int getRootTaskId() {
        if (mRootTaskStack == null) {
            return -1;
        }
        return mRootTaskStack.getRootTaskInfo().taskId;
    }

    /**
     * Returns the default intent associated with this {@link TaskPanel}.
     *
     * <p>The default intent will be send right after the TaskPanel is ready.
     */
    @Nullable
    public Intent getDefaultIntent() {
        ComponentName componentName = mAutoTaskStackHelper.getDefaultIntent(getPanelId());
        if (componentName == null) {
            return null;
        }
        Intent defaultIntent = new Intent(Intent.ACTION_MAIN);
        defaultIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        defaultIntent.setComponent(componentName);
        return defaultIntent;
    }

    public SurfaceControl getLeash() {
        return mLeash;
    }

    public void setLeash(SurfaceControl leash) {
        mLeash = leash;
    }

    /**
     * Return whether this panel is the launch root panel.
     */
    public boolean isLaunchRoot() {
        return mIsLaunchRoot;
    }

    @Override
    public void setRole(int role) {
        if (getRole() == role) return;
        super.setRole(role);
        String roleTypeName = getContext().getResources().getResourceTypeName(getRole());
        switch (roleTypeName) {
            case ROLE_TYPE_STRING:
                String roleString = getContext().getResources().getString(getRole());
                if (PanelState.DEFAULT_ROLE.equals(roleString)) {
                    mIsLaunchRoot = true;
                    return;
                }
                mPersistedActivities.clear();
                ComponentName componentName = ComponentName.unflattenFromString(roleString);
                mPersistedActivities.add(componentName);
                break;
            case ROLE_TYPE_ARRAY:
                mPersistedActivities.clear();
                String[] componentNameStrings = getContext().getResources().getStringArray(
                        getRole());
                mPersistedActivities.addAll(convertToComponentNames(componentNameStrings));
                break;
            default: {
                Log.e(TAG, "Role type is not supported " + roleTypeName);
            }
        }
    }

    private ArraySet<ComponentName> convertToComponentNames(String[] componentStrings) {
        ArraySet<ComponentName> componentNames = new ArraySet<>(componentStrings.length);
        for (int i = componentStrings.length - 1; i >= 0; i--) {
            componentNames.add(ComponentName.unflattenFromString(componentStrings[i]));
        }
        return componentNames;
    }

    private void trySetPersistentActivity() {
        if (mCarActivityManager == null || mRootTaskStack == null) {
            if (DEBUG) {
                Log.d(TAG,
                        "mCarActivityManager or mRootTaskStack is null, [" + getId() + ","
                                + mCarActivityManager + ", " + mRootTaskStack + "]");
            }
            return;
        }

        if (getRole() == 0) {
            if (DEBUG) {
                Log.d(TAG, "mRole is 0, [" + getPanelId() + "]");
            }
            return;
        }

        if (mIsLaunchRoot) {
            if (DEBUG) {
                Log.d(TAG, "mIsLaunchRoot is true, [" + getPanelId() + "]");
            }
            return;
        }

        mCarActivityManager.setPersistentActivitiesOnRootTask(
                mPersistedActivities.stream().toList(),
                mRootTaskStack.getRootTaskInfo().token.asBinder());
    }

    @VisibleForTesting
    void setRootTaskStack(RootTaskStack rootTaskStack) {
        mRootTaskStack = rootTaskStack;
    }

    @Override
    public String toString() {
        return "TaskPanel{"
                + "mId='" + getPanelId() + '\''
                + ", mIsLaunchRoot=" + mIsLaunchRoot
                + ", mDisplayId=" + getDisplayId()
                + ", mAlpha=" + getAlpha()
                + ", mIsVisible=" + isVisible()
                + ", mBounds=" + getBounds()
                + ", mRootTaskId=" + mRootTaskId
                + ", mContext=" + getContext()
                + ", mRole=" + getRole()
                + ", mLayer=" + getLayer()
                + ", mLeash=" + mLeash
                + ", mRootTaskStack=" + mRootTaskStack
                + ", mCornerRadius=" + getCornerRadius()
                + '}';
    }

    /**
     * Checks if the activity with given {@link ComponentName} should show in current panel.
     */
    public boolean handles(@Nullable ComponentName componentName) {
        return componentName != null && mPersistedActivities.contains(componentName);
    }

    @AssistedFactory
    public interface Factory {
        /** Create instance of TaskPanel with specified id */
        TaskPanel create(String id);
    }
}
