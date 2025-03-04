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
import android.graphics.Rect;
import android.os.Build;
import android.os.UserHandle;
import android.os.UserManager;
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
import com.android.systemui.car.users.CarSystemUIUserUtil;
import com.android.systemui.car.wm.scalableui.AutoTaskStackHelper;
import com.android.systemui.car.wm.scalableui.EventDispatcher;
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
public class TaskPanel implements Panel {
    private static final String TAG = TaskPanel.class.getSimpleName();
    private static final String ROLE_TYPE_STRING = "string";
    private static final String ROLE_TYPE_ARRAY = "array";
    private static final boolean DEBUG = Build.isDebuggable();

    private final AutoTaskStackController mAutoTaskStackController;
    private final CarServiceProvider mCarServiceProvider;
    private final Set<ComponentName> mPersistedActivities;
    private final Context mContext;
    private final AutoTaskStackHelper mAutoTaskStackHelper;
    private int mLayer = -1;
    private int mRole = 0;
    private CarActivityManager mCarActivityManager;
    private int mRootTaskId = -1;
    private Rect mBounds = null;
    private boolean mIsVisible;
    private String mId;
    private SurfaceControl mLeash;
    private float mAlpha;
    private int mDisplayId;
    private boolean mIsLaunchRoot;
    private RootTaskStack mRootTaskStack;

    @AssistedInject
    public TaskPanel(AutoTaskStackController autoTaskStackController, @NonNull Context context,
            CarServiceProvider carServiceProvider,
            AutoTaskStackHelper autoTaskStackHelper,
            EventDispatcher dispatcher,
            @Assisted String id) {
        mAutoTaskStackController = autoTaskStackController;
        mCarServiceProvider = carServiceProvider;
        mContext = context;
        mAutoTaskStackHelper = autoTaskStackHelper;
        mId = id;
        mPersistedActivities = new ArraySet<>();
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

        mAutoTaskStackController.createRootTaskStack(mDisplayId,
                new RootTaskStackListener() {
                    @Override
                    public void onRootTaskStackCreated(@NonNull RootTaskStack rootTaskStack) {
                        if (DEBUG) {
                            Log.d(TAG, mId + ", onRootTaskStackCreated " + rootTaskStack);
                        }
                        mRootTaskStack = rootTaskStack;
                        mRootTaskId = mRootTaskStack.getRootTaskInfo().taskId;
                        trySetPersistentActivity();
                        if (mIsLaunchRoot) {
                            mAutoTaskStackController.setDefaultRootTaskStackOnDisplay(mDisplayId,
                                    mRootTaskId);
                        }

                        if (isUserUnlocked(mContext)) {
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
                mContext.createContextAsUser(UserHandle.CURRENT, 0), 0, defaultIntent,
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
        ComponentName componentName = mAutoTaskStackHelper.getDefaultIntent(mId);
        if (componentName == null) {
            return null;
        }
        Intent defaultIntent = new Intent(Intent.ACTION_MAIN);
        defaultIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        defaultIntent.setComponent(componentName);
        return defaultIntent;
    }

    @Override
    public void setDisplayId(int displayId) {
        mDisplayId = displayId;
    }

    @Override
    public Rect getBounds() {
        return mBounds;
    }

    @Override
    public void setBounds(Rect bounds) {
        mBounds = bounds;
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

    public String getId() {
        return mId;
    }

    public void setId(String id) {
        mId = id;
    }

    @Override
    public int getLayer() {
        return mLayer;
    }

    @Override
    public void setLayer(int layer) {
        this.mLayer = layer;
    }

    @Override
    public int getX1() {
        return mBounds.left;
    }

    @Override
    public int getX2() {
        return mBounds.right;
    }

    @Override
    public int getY1() {
        return mBounds.top;
    }

    @Override
    public int getY2() {
        return mBounds.bottom;
    }

    @Override
    public void setX1(int x) {
        setBounds(new Rect(x, getY1(), getX2(), getY2()));
    }

    @Override
    public void setX2(int x) {
        setBounds(new Rect(getX1(), getY1(), x, getY2()));
    }

    @Override
    public void setY1(int y) {
        setBounds(new Rect(getX1(), y, getX2(), getY2()));
    }

    @Override
    public void setY2(int y) {
        setBounds(new Rect(getX1(), getY1(), getX2(), y));
    }

    @Override
    public boolean isVisible() {
        return mIsVisible;
    }

    @Override
    public void setVisibility(boolean isVisible) {
        mIsVisible = isVisible;
    }

    @Override
    public float getAlpha() {
        return mAlpha;
    }

    @Override
    public void setAlpha(float alpha) {
        mAlpha = alpha;
    }

    @Override
    public void setRole(int role) {
        if (this.mRole == role) return;
        this.mRole = role;
        String roleTypeName = mContext.getResources().getResourceTypeName(mRole);
        switch (roleTypeName) {
            case ROLE_TYPE_STRING:
                String roleString = mContext.getResources().getString(mRole);
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
                String[] componentNameStrings = mContext.getResources().getStringArray(mRole);
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
                        "mCarActivityManager or mRootTaskStack is null, [" + mId + ","
                                + mCarActivityManager + ", " + mRootTaskStack + "]");
            }
            return;
        }

        if (mRole == 0) {
            if (DEBUG) {
                Log.d(TAG, "mRole is 0, [" + mId + "]");
            }
            return;
        }

        if (mIsLaunchRoot) {
            if (DEBUG) {
                Log.d(TAG, "mIsLaunchRoot is true, [" + mId + "]");
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
                + "mId='" + mId + '\''
                + ", mIsLaunchRoot=" + mIsLaunchRoot
                + ", mDisplayId=" + mDisplayId
                + ", mAlpha=" + mAlpha
                + ", mIsVisible=" + mIsVisible
                + ", mBounds=" + mBounds
                + ", mRootTaskId=" + mRootTaskId
                + ", mContext=" + mContext
                + ", mRole=" + mRole
                + ", mLayer=" + mLayer
                + ", mLeash=" + mLeash
                + ", mRootTaskStack=" + mRootTaskStack
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

    private static boolean isUserUnlocked(@NonNull Context context) {
        int userId = CarSystemUIUserUtil.isSecondaryMUMDSystemUI()
                ? context.getUserId()
                : ActivityManager.getCurrentUser();

        UserManager userManager = context.getSystemService(UserManager.class);
        return userManager != null && userManager.isUserUnlocked(userId);
    }
}
