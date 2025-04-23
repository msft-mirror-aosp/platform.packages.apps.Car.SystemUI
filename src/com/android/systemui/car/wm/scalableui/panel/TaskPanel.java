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

import static com.android.systemui.car.wm.scalableui.systemevents.SystemEventConstants.PANEL_TOKEN_ID;
import static com.android.systemui.car.wm.scalableui.systemevents.SystemEventConstants.SYSTEM_TASK_PANEL_EMPTY_EVENT_ID;

import android.annotation.MainThread;
import android.app.ActivityManager;
import android.app.ActivityOptions;
import android.app.PendingIntent;
import android.car.app.CarActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.UserHandle;
import android.util.ArraySet;
import android.util.Log;
import android.view.SurfaceControl;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.android.car.internal.dep.Trace;
import com.android.car.scalableui.model.Blur;
import com.android.car.scalableui.model.Event;
import com.android.car.scalableui.model.Role;
import com.android.car.scalableui.panel.Panel;
import com.android.systemui.car.CarServiceProvider;
import com.android.systemui.car.wm.AutoCaptionBarViewFactoryImpl;
import com.android.systemui.car.wm.scalableui.AutoTaskStackHelper;
import com.android.systemui.car.wm.scalableui.EventDispatcher;
import com.android.wm.shell.ShellTaskOrganizer;
import com.android.wm.shell.automotive.AutoCaptionController;
import com.android.wm.shell.automotive.AutoDecor;
import com.android.wm.shell.automotive.AutoDecorManager;
import com.android.wm.shell.automotive.AutoTaskStackController;
import com.android.wm.shell.automotive.AutoTaskStackState;
import com.android.wm.shell.automotive.AutoTaskStackTransaction;
import com.android.wm.shell.automotive.RootTaskStack;
import com.android.wm.shell.automotive.RootTaskStackListener;

import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;
import dagger.assisted.AssistedInject;

import java.util.Arrays;
import java.util.Set;

/**
 * A {@link RootTaskStack} based implementation of a {@link Panel}.
 */
public final class TaskPanel extends BasePanel {
    private static final String TAG = TaskPanel.class.getSimpleName();

    private static final boolean DEBUG = Build.isDebuggable();

    private final AutoTaskStackController mAutoTaskStackController;
    private final CarServiceProvider mCarServiceProvider;
    private final Set<ComponentName> mPersistedActivities;
    private final AutoTaskStackHelper mAutoTaskStackHelper;
    private final AutoCaptionController mAutoCaptionController;
    private final AutoCaptionBarViewFactoryImpl mAutoCaptionBarViewFactoryImpl;
    private final TaskPanelInfoRepository mTaskPanelInfoRepository;
    private final EventDispatcher mEventDispatcher;

    private CarActivityManager mCarActivityManager;
    private int mRootTaskId = -1;
    private SurfaceControl mLeash;
    private Blur mBlur;
    private boolean mIsLaunchRoot;
    @NonNull
    private Rect mSafeBounds = new Rect();
    private RootTaskStack mRootTaskStack;
    private PanelUtils mPanelUtils;
    private OverlayPanelView mOverlayView;
    private final AutoDecorManager mAutoDecorManager;
    private AutoDecor mAutoDecor;
    @Nullable
    private String mTopTaskPackageName;
    private Context mContext;

    @AssistedInject
    public TaskPanel(AutoTaskStackController autoTaskStackController,
            @NonNull Context context,
            CarServiceProvider carServiceProvider,
            AutoTaskStackHelper autoTaskStackHelper,
            ShellTaskOrganizer shellTaskOrganizer,
            AutoCaptionController autoCaptionController,
            PanelUtils panelUtils,
            TaskPanelInfoRepository taskPanelInfoRepository,
            AutoDecorManager autoDecorManager,
            EventDispatcher dispatcher,
            @Assisted String id) {
        super(context, id);
        mAutoTaskStackController = autoTaskStackController;
        mCarServiceProvider = carServiceProvider;
        mAutoTaskStackHelper = autoTaskStackHelper;
        mTaskPanelInfoRepository = taskPanelInfoRepository;
        mEventDispatcher = dispatcher;
        mPersistedActivities = new ArraySet<>();
        mPanelUtils = panelUtils;
        mAutoCaptionController = autoCaptionController;
        mAutoCaptionBarViewFactoryImpl =
                new AutoCaptionBarViewFactoryImpl(context, shellTaskOrganizer);
        mAutoDecorManager = autoDecorManager;
        mContext = context;
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
                        setupToolbarAndSafeRegion();

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
                        mAutoCaptionController.removeSafeRegionAndCaptionRegion(rootTaskStack);
                        mRootTaskStack = null;
                        mRootTaskId = -1;
                    }

                    @Override
                    public void onTaskAppeared(ActivityManager.RunningTaskInfo taskInfo,
                            SurfaceControl leash) {

                        mTopTaskPackageName = mPanelUtils.getTaskPackageName(taskInfo);
                        if (mTopTaskPackageName == null) {
                            Log.e(TAG, "onTaskAppeared: Failed to get package name for task "
                                    + taskInfo.taskId);
                            return;
                        }

                        mAutoTaskStackHelper.setTaskUntrimmableIfNeeded(taskInfo);
                        mTaskPanelInfoRepository.onTaskAppearedOnPanel(getPanelId(), taskInfo);
                    }

                    @Override
                    public void onTaskInfoChanged(ActivityManager.RunningTaskInfo taskInfo) {
                        mTaskPanelInfoRepository.onTaskChangedOnPanel(getPanelId(), taskInfo);
                    }

                    @Override
                    public void onTaskVanished(ActivityManager.RunningTaskInfo taskInfo) {
                        mTaskPanelInfoRepository.onTaskVanishedOnPanel(getPanelId(), taskInfo);
                        if (mRootTaskStack != null
                                && mRootTaskStack.getRootTaskInfo().numActivities == 0) {
                            mEventDispatcher.executeTransaction(new Event.Builder(
                                    SYSTEM_TASK_PANEL_EMPTY_EVENT_ID).addToken(PANEL_TOKEN_ID,
                                    getPanelId()).build());
                        }
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

    @MainThread
    private void resetOverlay(Blur blur) {
        // Remove existing autoDecor that holds the view.
        if (mAutoDecor != null) {
            mAutoDecorManager.removeAutoDecor(mAutoDecor);
        }
        mOverlayView = getOverlayView(true);
        mAutoDecor = mAutoDecorManager.createAutoDecor(mOverlayView, getLayer(), getBounds(),
                getPanelId());
        mAutoDecorManager.attachAutoDecorToDisplay(mAutoDecor, getDisplayId());
        if (mBlur == null || blur.getBlurRadius() != mBlur.getBlurRadius()) {
            mOverlayView.setBlurIntensity(blur.getBlurRadius());
        }
        if (mBlur == null || blur.getBackgroundColor() != mBlur.getBackgroundColor()) {
            mOverlayView.setBlurColor(blur.getBackgroundColor());
        }
        if (mBlur == null || blur.getCornerRadius() != mBlur.getCornerRadius()) {
            mOverlayView.setCornerRadius(blur.getCornerRadius());
        }
        if (blur.isVailEnabled()) {
            if (mTopTaskPackageName == null) {
                Log.e(TAG, "vail can't be set for null package name");
            } else {
                try {
                    Drawable icon = mContext.getPackageManager().getApplicationIcon(
                            mTopTaskPackageName);
                    mOverlayView.setVail(icon);
                } catch (PackageManager.NameNotFoundException e) {
                    Log.e(TAG, "vail can't be set for package name ", e);
                }
            }
        }
    }

    /**
     * Returns the attached AutoDecor
     */
    public AutoDecor getAutoDecor() {
        return mAutoDecor;
    }

    private OverlayPanelView getOverlayView(boolean forceReset) {
        Blur blur = new Blur.Builder().build();
        if (mOverlayView == null || forceReset) {
            mOverlayView = new OverlayPanelView(getContext(), blur);
        }

        return mOverlayView;
    }

    private void resetOverlayView() {
        mOverlayView = null;
        if (mAutoDecor != null) {
            mAutoDecorManager.removeAutoDecor(mAutoDecor);
        }
    }

    @Override
    public void setBlur(Blur blur) {
        if (blur == null) {
            resetOverlayView();
            mBlur = null;
            return;
        }
        resetOverlay(blur);
        mOverlayView.refresh();
        mBlur = blur;
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

    @NonNull
    @Override
    public Rect getSafeBounds() {
        return mSafeBounds;
    }

    @Override
    public void setSafeBounds(@NonNull Rect safeBounds) {
        if (safeBounds.isEmpty()) {
            throw new IllegalArgumentException(
                    "Tried setting incorrect safe bounds: " + safeBounds + "on panel: "
                            + getPanelId());
        }
        mSafeBounds = safeBounds;
        setupToolbarAndSafeRegion();
    }

    @Override
    public void setRole(Role role) {
        if (getRole() == role) return;
        super.setRole(role);

        if (getRole().isDefault()) {
            mIsLaunchRoot = true;
            return;
        } else {
            ComponentName[] persistedActivities = getRole().getPersistedActivities();
            if (persistedActivities != null) {
                mPersistedActivities.clear();
                mPersistedActivities.addAll(Arrays.asList(persistedActivities));
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
                        "mCarActivityManager or mRootTaskStack is null, [" + getPanelId() + ","
                                + mCarActivityManager + ", " + mRootTaskStack + "]");
            }
            return;
        }

        if (getRole().getPersistedActivities() == null
                || getRole().getPersistedActivities().length == 0) {
            if (DEBUG) {
                Log.d(TAG, "Persistent Activities is empty, [" + getPanelId() + "]");
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

    private void setupToolbarAndSafeRegion() {
        if (mRootTaskStack == null) {
            logVerbose("Root TaskStack not set for panel: " + getPanelId());
            return;
        }
        if (mSafeBounds.isEmpty()) {
            // TODO(b/409067170): update AutoCaptionController API to be able to set these values
            //  independently
            logVerbose("Invalid Safe Bounds, not setting safe region for panel: " + getPanelId());
            return;
        }
        if (getBounds() == null || getBounds().isEmpty()) {
            logVerbose("Null or invalid panel bounds, not setting safe region for panel: "
                    + getPanelId());
            return;
        }
        if (mSafeBounds.equals(getBounds())) {
            logVerbose("SafeBounds equivalent to panel bounds, not setting safe region for panel: "
                    + getPanelId());
            return;
        }

        Rect toolbarBounds = calculateToolbarBounds(getBounds(), getSafeBounds());
        if (toolbarBounds.isEmpty()) {
            logVerbose("Toolbar with bounds: " + toolbarBounds + " cannot be added to panel: "
                    + getPanelId());
            return;
        }
        toolbarBounds.offset(-getBounds().left, -getBounds().top);

        logVerbose("Setting up toolbar and safe region with following values: "
                + "rootTaskStack = " + mRootTaskStack
                + ", safe bounds = " + mSafeBounds
                + ", toolbar bounds = " + toolbarBounds
                + ", panel bounds = " + getBounds());

        mAutoCaptionController.setSafeRegionAndCaptionRegion(mRootTaskStack, mSafeBounds,
                toolbarBounds, mAutoCaptionBarViewFactoryImpl);
    }

    @NonNull
    private Rect calculateToolbarBounds(@NonNull Rect panelBounds, @NonNull Rect safeBounds) {
        // TODO(b/409067170): remove this when AutoCaptionController API is able to handle safe
        //  region and toolbar separately
        if (panelBounds.top < safeBounds.top) {
            return new Rect(safeBounds.left, panelBounds.top, safeBounds.right, safeBounds.top);
        }
        if (panelBounds.bottom > safeBounds.bottom) {
            return new Rect(safeBounds.left, safeBounds.bottom, safeBounds.right,
                    panelBounds.bottom);
        }
        if (panelBounds.left < safeBounds.left) {
            return new Rect(panelBounds.left, safeBounds.top, safeBounds.left, safeBounds.bottom);
        }
        if (panelBounds.right > safeBounds.right) {
            return new Rect(safeBounds.right, safeBounds.top, panelBounds.right, safeBounds.bottom);
        }
        return new Rect();
    }

    private void logVerbose(String message) {
        if (DEBUG) {
            Log.v(TAG, message);
        }
    }

    @VisibleForTesting
    void setRootTaskStack(RootTaskStack rootTaskStack) {
        mRootTaskStack = rootTaskStack;
    }

    @Override
    public String toString() {
        return "TaskPanel{"
                + "mId='" + getPanelId() + '\''
                + ", mBounds=" + getBounds()
                + ", mAlpha=" + getAlpha()
                + ", mIsVisible=" + isVisible()
                + ", mRootTaskId=" + mRootTaskId
                + ", mRole=" + getRole()
                + ", mLayer=" + getLayer()
                + ", mLeash=" + mLeash
                + ", mRootTaskStack=" + mRootTaskStack
                + ", mCornerRadius=" + getCornerRadius()
                + ", mIsLaunchRoot=" + mIsLaunchRoot
                + ", mDisplayId=" + getDisplayId()
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
