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
package com.android.systemui.car.wm.activity;

import static com.android.systemui.car.Flags.configAppBlockingActivities;

import android.app.ActivityManager;
import android.car.Car;
import android.car.CarOccupantZoneManager;
import android.car.app.CarActivityManager;
import android.car.content.pm.CarPackageManager;
import android.car.drivingstate.CarUxRestrictions;
import android.car.drivingstate.CarUxRestrictionsManager;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Insets;
import android.graphics.Rect;
import android.hardware.display.DisplayManager;
import android.opengl.GLSurfaceView;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.UserHandle;
import android.text.TextUtils;
import android.util.Log;
import android.util.Slog;
import android.view.DisplayInfo;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.TextView;

import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;

import com.android.systemui.R;
import com.android.systemui.car.ndo.BlockerViewModel;
import com.android.systemui.car.ndo.NdoViewModelFactory;
import com.android.systemui.car.wm.activity.blurredbackground.BlurredSurfaceRenderer;

import java.util.List;
import java.util.concurrent.Executor;

import javax.inject.Inject;

/**
 * Default activity that will be launched when the current foreground activity is not allowed.
 * Additional information on blocked Activity should be passed as intent extras.
 */
public class ActivityBlockingActivity extends FragmentActivity {
    private static final int ACTIVITY_MONITORING_DELAY_MS = 1000;
    private static final String TAG = "BlockingActivity";
    private static final int EGL_CONTEXT_VERSION = 2;
    private static final int EGL_CONFIG_SIZE = 8;
    private static final int INVALID_TASK_ID = -1;
    private final Object mLock = new Object();

    private GLSurfaceView mGLSurfaceView;
    private BlurredSurfaceRenderer mSurfaceRenderer;
    private boolean mIsGLSurfaceSetup = false;

    private Car mCar;
    private CarUxRestrictionsManager mUxRManager;
    private CarPackageManager mCarPackageManager;
    private CarActivityManager mCarActivityManager;
    private CarOccupantZoneManager mCarOccupantZoneManager;

    private Button mExitButton;
    private Button mToggleDebug;

    private int mBlockedTaskId;
    private final Handler mHandler = new Handler();
    private String mBlockedActivityName;
    private final NdoViewModelFactory mViewModelFactory;

    private final View.OnClickListener mOnExitButtonClickedListener =
            v -> {
                if (isExitOptionCloseApplication()) {
                    handleCloseApplication();
                } else {
                    handleRestartingTask();
                }
            };

    private final ViewTreeObserver.OnGlobalLayoutListener mOnGlobalLayoutListener =
            new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    mToggleDebug.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                    updateButtonWidths();
                }
            };

    private final CarPackageManager.BlockingUiCommandListener mBlockingUiCommandListener = () -> {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Slog.d(TAG, "Finishing ABA due to task stack change");
        }
        finish();
    };

    @Inject
    public ActivityBlockingActivity(NdoViewModelFactory viewModelFactory) {
        mViewModelFactory = viewModelFactory;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_blocking);
        mExitButton = findViewById(R.id.exit_button);
        // Listen to the CarUxRestrictions so this blocking activity can be dismissed when the
        // restrictions are lifted.
        // This Activity should be launched only after car service is initialized. Currently this
        // Activity is only launched from CPMS. So this is safe to do.
        mCar = Car.createCar(this, /* handler= */ null, Car.CAR_WAIT_TIMEOUT_WAIT_FOREVER,
                (car, ready) -> {
                    if (!ready) {
                        return;
                    }
                    mCarPackageManager = (CarPackageManager) car.getCarManager(
                            Car.PACKAGE_SERVICE);
                    mCarActivityManager = (CarActivityManager) car.getCarManager(
                            Car.CAR_ACTIVITY_SERVICE);
                    mUxRManager = (CarUxRestrictionsManager) car.getCarManager(
                            Car.CAR_UX_RESTRICTION_SERVICE);
                    mCarOccupantZoneManager = car.getCarManager(CarOccupantZoneManager.class);
                    // This activity would have been launched only in a restricted state.
                    // But ensuring when the service connection is established, that we are still
                    // in a restricted state.
                    handleUxRChange(mUxRManager.getCurrentCarUxRestrictions());
                    mUxRManager.registerListener(ActivityBlockingActivity.this::handleUxRChange);
                    Executor executor = new Handler(Looper.getMainLooper())::post;
                    mCarPackageManager.registerBlockingUiCommandListener(getDisplayId(), executor,
                            mBlockingUiCommandListener);
                });

        setupGLSurface();

        if (!configAppBlockingActivities()) {
            Slog.d(TAG, "Ignoring app blocking activity feature");
        } else if (getResources().getBoolean(R.bool.config_enableAppBlockingActivities)) {
            mBlockedActivityName = getIntent().getStringExtra(
                    CarPackageManager.BLOCKING_INTENT_EXTRA_BLOCKED_ACTIVITY_NAME);
            BlockerViewModel blockerViewModel = new ViewModelProvider(this, mViewModelFactory)
                    .get(BlockerViewModel.class);
            int userOnDisplay = getUserForCurrentDisplay();
            if (userOnDisplay == CarOccupantZoneManager.INVALID_USER_ID) {
                Slog.w(TAG, "Can't find user on display " + getDisplayId()
                        + " defaulting to current user");
                userOnDisplay = UserHandle.USER_CURRENT;
            }
            blockerViewModel.initialize(mBlockedActivityName, UserHandle.of(userOnDisplay));
            blockerViewModel.getBlockingTypeLiveData().observe(this, blockingType -> {
                switch (blockingType) {
                    case DIALER -> startBlockingActivity(
                            getString(R.string.config_dialerBlockingActivity));
                    case MEDIA -> startBlockingActivity(
                            getString(R.string.config_mediaBlockingActivity));
                    case NONE -> { /* no-op */ }
                }
            });
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (mIsGLSurfaceSetup) {
            mGLSurfaceView.onResume();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Display info about the current blocked activity, and optionally show an exit button
        // to restart the blocked task (stack of activities) if its root activity is DO.
        mBlockedTaskId = getIntent().getIntExtra(
                CarPackageManager.BLOCKING_INTENT_EXTRA_BLOCKED_TASK_ID,
                INVALID_TASK_ID);

        // blockedActivity is expected to be always passed in as the topmost activity of task.
        String blockedActivity = getIntent().getStringExtra(
                CarPackageManager.BLOCKING_INTENT_EXTRA_BLOCKED_ACTIVITY_NAME);
        if (!TextUtils.isEmpty(blockedActivity)) {
            if (isTopActivityBehindAbaDistractionOptimized()) {
                Slog.w(TAG, "Top activity is already DO, so finishing");
                finish();
                return;
            }

            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Slog.d(TAG, "Blocking activity " + blockedActivity);
            }
        }

        displayExitButton();

        // Show more debug info for non-user build.
        if (Build.IS_ENG || Build.IS_USERDEBUG) {
            displayDebugInfo();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();

        if (mIsGLSurfaceSetup) {
            // We queue this event so that it runs on the Rendering thread
            mGLSurfaceView.queueEvent(() -> mSurfaceRenderer.onPause());

            mGLSurfaceView.onPause();
        }

        // Finish when blocking activity goes invisible to avoid it accidentally re-surfaces with
        // stale string regarding blocked activity.
        finish();
    }

    private void setupGLSurface() {
        DisplayManager displayManager = (DisplayManager) getApplicationContext().getSystemService(
                Context.DISPLAY_SERVICE);
        DisplayInfo displayInfo = new DisplayInfo();

        int displayId = getDisplayId();
        displayManager.getDisplay(displayId).getDisplayInfo(displayInfo);

        Rect windowRect = getAppWindowRect();

        mSurfaceRenderer = new BlurredSurfaceRenderer(this, windowRect, getDisplayId());

        mGLSurfaceView = findViewById(R.id.blurred_surface_view);
        mGLSurfaceView.setEGLContextClientVersion(EGL_CONTEXT_VERSION);

        mGLSurfaceView.setEGLConfigChooser(EGL_CONFIG_SIZE, EGL_CONFIG_SIZE, EGL_CONFIG_SIZE,
                EGL_CONFIG_SIZE, EGL_CONFIG_SIZE, EGL_CONFIG_SIZE);

        mGLSurfaceView.setRenderer(mSurfaceRenderer);

        // We only want to render the screen once
        mGLSurfaceView.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);

        mIsGLSurfaceSetup = true;
    }

    /**
     * Computes a Rect that represents the portion of the screen that contains the activity that is
     * being blocked.
     *
     * @return Rect that represents the application window
     */
    private Rect getAppWindowRect() {
        Insets systemBarInsets = getWindowManager()
                .getCurrentWindowMetrics()
                .getWindowInsets()
                .getInsets(WindowInsets.Type.systemBars());

        Rect displayBounds = getWindowManager().getCurrentWindowMetrics().getBounds();

        int leftX = systemBarInsets.left;
        int rightX = displayBounds.width() - systemBarInsets.right;
        int topY = systemBarInsets.top;
        int bottomY = displayBounds.height() - systemBarInsets.bottom;

        return new Rect(leftX, topY, rightX, bottomY);
    }

    private void displayExitButton() {
        String exitButtonText = getExitButtonText();

        mExitButton.setText(exitButtonText);
        mExitButton.setOnClickListener(mOnExitButtonClickedListener);
    }

    // If the root activity is DO, the user will have the option to go back to that activity,
    // otherwise, the user will have the option to close the blocked application
    private boolean isExitOptionCloseApplication() {
        boolean isRootDO = getIntent().getBooleanExtra(
                CarPackageManager.BLOCKING_INTENT_EXTRA_IS_ROOT_ACTIVITY_DO, false);
        return mBlockedTaskId == INVALID_TASK_ID || !isRootDO;
    }

    private String getExitButtonText() {
        return isExitOptionCloseApplication() ? getString(R.string.exit_button_close_application)
                : getString(R.string.exit_button_go_back);
    }

    /**
     * It is possible that the stack info has changed between when the intent to launch this
     * activity was initiated and when this activity is started. Check whether the activity behind
     * the ABA is distraction optimized.
     *
     * @return {@code true} if the activity is distraction optimized, {@code false} if the top task
     * behind the ABA is null or the top task's top activity is null or if the top activity is
     * non-distraction optimized.
     */
    private boolean isTopActivityBehindAbaDistractionOptimized() {
        List<ActivityManager.RunningTaskInfo> taskInfosTopToBottom;
        taskInfosTopToBottom = mCarActivityManager.getVisibleTasks();
        ActivityManager.RunningTaskInfo topStackBehindAba = null;

        // Iterate in bottom to top manner
        for (int i = taskInfosTopToBottom.size() - 1; i >= 0; i--) {
            ActivityManager.RunningTaskInfo taskInfo = taskInfosTopToBottom.get(i);
            if (taskInfo.displayId != getDisplayId()) {
                // ignore stacks on other displays
                continue;
            }

            if (getComponentName().equals(taskInfo.topActivity)) {
                // quit when stack with the blocking activity is encountered because the last seen
                // task will be the topStackBehindAba.
                break;
            }

            topStackBehindAba = taskInfo;
        }

        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Slog.d(TAG, String.format("Top stack behind ABA is: %s", topStackBehindAba));
        }

        if (topStackBehindAba != null && topStackBehindAba.topActivity != null) {
            boolean isDo = mCarPackageManager.isActivityDistractionOptimized(
                    topStackBehindAba.topActivity.getPackageName(),
                    topStackBehindAba.topActivity.getClassName());
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Slog.d(TAG,
                        String.format("Top activity (%s) is DO: %s", topStackBehindAba.topActivity,
                                isDo));
            }
            return isDo;
        }

        // unknown top stack / activity, default to considering it non-DO
        return false;
    }

    private void displayDebugInfo() {
        String blockedActivity = getIntent().getStringExtra(
                CarPackageManager.BLOCKING_INTENT_EXTRA_BLOCKED_ACTIVITY_NAME);
        String rootActivity = getIntent().getStringExtra(
                CarPackageManager.BLOCKING_INTENT_EXTRA_ROOT_ACTIVITY_NAME);

        TextView debugInfo = findViewById(R.id.debug_info);
        debugInfo.setText(getDebugInfo(blockedActivity, rootActivity));

        // We still want to ensure driving safety for non-user build;
        // toggle visibility of debug info with this button.
        mToggleDebug = findViewById(R.id.toggle_debug_info);
        mToggleDebug.setVisibility(View.VISIBLE);
        mToggleDebug.setOnClickListener(v -> {
            boolean isDebugVisible = debugInfo.getVisibility() == View.VISIBLE;
            debugInfo.setVisibility(isDebugVisible ? View.GONE : View.VISIBLE);
        });

        mToggleDebug.getViewTreeObserver().addOnGlobalLayoutListener(mOnGlobalLayoutListener);
    }

    // When the Debug button is visible, we set both of the visible buttons to have the width
    // of whichever button is wider
    private void updateButtonWidths() {
        Button debugButton = findViewById(R.id.toggle_debug_info);

        int exitButtonWidth = mExitButton.getWidth();
        int debugButtonWidth = debugButton.getWidth();

        if (exitButtonWidth > debugButtonWidth) {
            debugButton.setWidth(exitButtonWidth);
        } else {
            mExitButton.setWidth(debugButtonWidth);
        }
    }

    private String getDebugInfo(String blockedActivity, String rootActivity) {
        StringBuilder debug = new StringBuilder();

        ComponentName blocked = ComponentName.unflattenFromString(blockedActivity);
        debug.append("Blocked activity is ")
                .append(blocked.getShortClassName())
                .append("\nBlocked activity package is ")
                .append(blocked.getPackageName());

        if (rootActivity != null) {
            ComponentName root = ComponentName.unflattenFromString(rootActivity);
            // Optionally show root activity info if it differs from the blocked activity.
            if (!root.equals(blocked)) {
                debug.append("\n\nRoot activity is ").append(root.getShortClassName());
            }
            if (!root.getPackageName().equals(blocked.getPackageName())) {
                debug.append("\nRoot activity package is ").append(root.getPackageName());
            }
        }
        return debug.toString();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mCar.disconnect();
        mUxRManager.unregisterListener();
        mCarPackageManager.unregisterBlockingUiCommandListener(mBlockingUiCommandListener);
        if (mToggleDebug != null) {
            mToggleDebug.getViewTreeObserver().removeOnGlobalLayoutListener(
                    mOnGlobalLayoutListener);
        }
        mHandler.removeCallbacksAndMessages(null);
        mCar.disconnect();
    }

    // If no distraction optimization is required in the new restrictions, then dismiss the
    // blocking activity (self).
    private void handleUxRChange(CarUxRestrictions restrictions) {
        if (restrictions == null) {
            return;
        }
        if (!restrictions.isRequiresDistractionOptimization()) {
            finish();
        }
    }

    private void handleCloseApplication() {
        if (isFinishing()) {
            return;
        }

        int userOnDisplay = getUserForCurrentDisplay();
        if (userOnDisplay == CarOccupantZoneManager.INVALID_USER_ID) {
            Slog.e(TAG, "can not find user on display " + getDisplay()
                    + " to start Home");
            finish();
        }

        Intent startMain = new Intent(Intent.ACTION_MAIN);

        int driverDisplayId = mCarOccupantZoneManager.getDisplayIdForDriver(
                CarOccupantZoneManager.DISPLAY_TYPE_MAIN);
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Slog.d(TAG, String.format("display id: %d, driver display id: %d",
                    getDisplayId(), driverDisplayId));
        }
        startMain.addCategory(Intent.CATEGORY_HOME);
        startMain.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivityAsUser(startMain, UserHandle.of(userOnDisplay));
        finish();
    }

    private void handleRestartingTask() {
        // Lock on self to avoid restarting the same task twice.
        synchronized (mLock) {
            if (isFinishing()) {
                return;
            }

            if (Log.isLoggable(TAG, Log.INFO)) {
                Slog.i(TAG, "Restarting task " + mBlockedTaskId);
            }
            mCarPackageManager.restartTask(mBlockedTaskId);
            finish();
        }
    }

    private void startBlockingActivity(String blockingActivity) {
        int userOnDisplay = getUserForCurrentDisplay();
        if (userOnDisplay == CarOccupantZoneManager.INVALID_USER_ID) {
            Slog.w(TAG, "Can't find user on display " + getDisplayId()
                    + " defaulting to USER_CURRENT");
            userOnDisplay = UserHandle.USER_CURRENT;
        }

        ComponentName componentName = ComponentName.unflattenFromString(blockingActivity);
        Intent intent = new Intent();
        intent.setComponent(componentName);
        intent.putExtra(Intent.EXTRA_COMPONENT_NAME, mBlockedActivityName);
        try {
            startActivityAsUser(intent, UserHandle.of(userOnDisplay));
        } catch (ActivityNotFoundException ex) {
            Slog.e(TAG, "Unable to resolve blocking activity " + blockingActivity, ex);
        } catch (RuntimeException ex) {
            Slog.w(TAG, "Failed to launch blocking activity " + blockingActivity, ex);
        }
    }

    private int getUserForCurrentDisplay() {
        int displayId = getDisplayId();
        return mCarOccupantZoneManager.getUserForDisplayId(displayId);
    }
}