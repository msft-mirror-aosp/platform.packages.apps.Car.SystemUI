/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.systemui.car.activity.window;

import static android.view.Display.DEFAULT_DISPLAY;
import static android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;

import android.annotation.NonNull;
import android.app.ActivityManager;
import android.app.ActivityTaskManager;
import android.app.TaskStackListener;
import android.car.app.CarActivityManager;
import android.car.app.CarTaskViewController;
import android.car.app.CarTaskViewControllerCallback;
import android.car.app.CarTaskViewControllerHostLifecycle;
import android.car.app.RemoteCarDefaultRootTaskView;
import android.car.app.RemoteCarDefaultRootTaskViewCallback;
import android.car.app.RemoteCarDefaultRootTaskViewConfig;
import android.car.content.pm.CarPackageManager;
import android.content.Context;
import android.graphics.PixelFormat;
import android.hardware.input.InputManager;
import android.os.Binder;
import android.os.Handler;
import android.os.RemoteException;
import android.os.SystemClock;
import android.util.Log;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowManager;

import androidx.annotation.MainThread;

import com.android.systemui.R;
import com.android.systemui.car.CarServiceProvider;
import com.android.systemui.dagger.SysUISingleton;
import com.android.systemui.dagger.qualifiers.Main;
import com.android.systemui.dagger.qualifiers.UiBackground;

import javax.inject.Inject;

/**
 * Handles adding the {@link RemoteCarDefaultRootTaskView}
 */
@SysUISingleton
public class ActivityWindowController {
    public static final String TAG = ActivityWindowController.class.getSimpleName();

    @NonNull
    private final Handler mMainHandler;
    @NonNull
    private final Context mContext;
    @NonNull
    private final WindowManager mWindowManager;
    @NonNull
    private final CarServiceProvider mCarServiceProvider;
    @NonNull
    private final InputManager mInputManager;

    @NonNull
    private ViewGroup mLayout;
    @NonNull
    private WindowManager.LayoutParams mWmLayoutParams;

    @NonNull
    private CarTaskViewController mCarTaskViewController;
    @NonNull
    private CarTaskViewControllerHostLifecycle mCarTaskViewControllerHostLifecycle;
    @NonNull
    private String mCurrentTaskPackageName = "";
    private int mCurrentTaskDisplayId = DEFAULT_DISPLAY;

    @NonNull
    private CarPackageManager mCarPackageManager;
    @NonNull
    private CarActivityManager mCarActivityManager;

    @NonNull
    @UiBackground
    private final CarServiceProvider.CarServiceOnConnectedListener mCarServiceLifecycleListener =
            car -> {
                mCarPackageManager = car.getCarManager(CarPackageManager.class);
                mCarActivityManager = car.getCarManager(CarActivityManager.class);

                inflate();
                setupRemoteCarTaskView();
            };

    @Inject
    public ActivityWindowController(Context context, WindowManager windowManager,
            CarServiceProvider carServiceProvider, @Main Handler mainHandler,
            CarTaskViewControllerHostLifecycle carTaskViewControllerHostLifecycle) {
        mContext = context;
        mWindowManager = windowManager;
        mCarServiceProvider = carServiceProvider;
        mInputManager = context.getSystemService(InputManager.class);
        mMainHandler = mainHandler;
        mCarTaskViewControllerHostLifecycle = carTaskViewControllerHostLifecycle;
    }

    /**
     * called for initialization
     */
    @MainThread
    public void init() {
        mCarServiceProvider.addListener(mCarServiceLifecycleListener);
    }

    /**
     * Show Toolbar
     */
    @MainThread
    public void show() {
        showToolbar();
    }

    /**
     * Hide Toolbar
     */
    @MainThread
    public void hide() {
        hideToolbar();
    }

    @MainThread
    protected void inflate() {
        mLayout = (ViewGroup) LayoutInflater.from(mContext)
                .inflate(R.layout.car_activity_window, /* root= */ null, /* attachToRoot= */ false);

        mWmLayoutParams = new WindowManager.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                    | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT);
        mWmLayoutParams.setTrustedOverlay();
        mWmLayoutParams.setFitInsetsTypes(0);
        mWmLayoutParams.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE;
        mWmLayoutParams.token = new Binder();
        mWmLayoutParams.setTitle("ActivityWindow!");
        mWmLayoutParams.packageName = mContext.getPackageName();
        mWmLayoutParams.layoutInDisplayCutoutMode = LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        mWmLayoutParams.privateFlags |= WindowManager.LayoutParams.SYSTEM_FLAG_SHOW_FOR_ALL_USERS;

        mWindowManager.addView(mLayout, mWmLayoutParams);
    }

    private void setupRemoteCarTaskView() {
        mCarActivityManager.getCarTaskViewController(
                mContext,
                mCarTaskViewControllerHostLifecycle,
                mContext.getMainExecutor(),
                new CarTaskViewControllerCallback() {
                    @Override
                    public void onConnected(
                            CarTaskViewController carTaskViewController) {
                        mCarTaskViewController = carTaskViewController;
                        taskViewControllerReady();
                    }

                    @Override
                    public void onDisconnected(
                            CarTaskViewController carTaskViewController) {
                    }
                });
    }

    private void taskViewControllerReady() {
        mCarTaskViewController.createRemoteCarDefaultRootTaskView(
                new RemoteCarDefaultRootTaskViewConfig.Builder()
                        .setDisplayId(mContext.getDisplayId())
                        .embedHomeTask(true)
                        .embedRecentsTask(true)
                        .build(),
                mContext.getMainExecutor(),
                new RemoteCarDefaultRootTaskViewCallback() {
                    @Override
                    public void onTaskViewCreated(@NonNull RemoteCarDefaultRootTaskView taskView) {
                        Log.d(TAG, "Root Task View is created");
                        taskView.setZOrderMediaOverlay(true);

                        mLayout.setOnApplyWindowInsetsListener(
                                new View.OnApplyWindowInsetsListener() {
                                @Override
                                public WindowInsets onApplyWindowInsets(View view,
                                        WindowInsets insets) {
                                    mLayout.setPadding(
                                            insets.getSystemWindowInsetLeft(),
                                            insets.getSystemWindowInsetTop(),
                                            insets.getSystemWindowInsetRight(),
                                            insets.getSystemWindowInsetBottom());
                                    return insets.replaceSystemWindowInsets(
                                        /* left */ 0, /* top */ 0, /* right */ 0, /* bottom */ 0);
                                }
                            });

                        mLayout.findViewById(R.id.back_btn).setOnClickListener(v -> {
                            fakeBack();
                        });

                        try {
                            ActivityTaskManager.getService()
                                    .registerTaskStackListener(mTaskStackListener);
                        } catch (RemoteException e) {
                            Log.e(TAG, "Couldn't register TaskStackListener.");
                            mMainHandler.post(() -> hideToolbar());
                        }

                        ViewGroup layout = (ViewGroup) mLayout.findViewById(R.id.activity_area);
                        layout.addView(taskView);
                    }

                    @Override
                    public void onTaskViewInitialized() {
                        Log.d(TAG, "Root Task View is ready");
                    }
                }
        );
    }

    private void fakeBack() {
        sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
    }

    private void sendKeyDownUpSync(int keyCode) {
        sendKeySync(new KeyEvent(KeyEvent.ACTION_DOWN, keyCode));
        sendKeySync(new KeyEvent(KeyEvent.ACTION_UP, keyCode));
    }

    private void sendKeySync(KeyEvent event) {
        long downTime = event.getDownTime();
        long eventTime = event.getEventTime();
        int source = event.getSource();
        if (source == InputDevice.SOURCE_UNKNOWN) {
            source = InputDevice.SOURCE_KEYBOARD;
        }
        if (eventTime == 0) {
            eventTime = SystemClock.uptimeMillis();
        }
        if (downTime == 0) {
            downTime = eventTime;
        }
        KeyEvent newEvent = new KeyEvent(event);
        newEvent.setDisplayId(mCurrentTaskDisplayId);
        newEvent.setTime(downTime, eventTime);
        newEvent.setSource(source);
        newEvent.setFlags(event.getFlags() | KeyEvent.FLAG_FROM_SYSTEM);
        mInputManager.injectInputEvent(newEvent,
                InputManager.INJECT_INPUT_EVENT_MODE_ASYNC);
    }

    private final TaskStackListener mTaskStackListener = new TaskStackListener() {

        @Override
        public void onTaskMovedToFront(ActivityManager.RunningTaskInfo taskInfo)
                throws RemoteException {
            String packageName = getPackageName(taskInfo);
            mCurrentTaskDisplayId = taskInfo.displayId;
            if (requiresDisplayCompat(packageName)) {
                mMainHandler.post(() -> showToolbar());
                return;
            }
            mMainHandler.post(() -> hideToolbar());
        }

        @Override
        public void onTaskMovedToBack(ActivityManager.RunningTaskInfo taskInfo) {
        }

        private String getPackageName(ActivityManager.RunningTaskInfo taskInfo) {
            if (taskInfo.topActivity != null) {
                mCurrentTaskPackageName = taskInfo.topActivity.getPackageName();
            } else {
                mCurrentTaskPackageName = taskInfo.baseIntent.getComponent().getPackageName();
            }
            return mCurrentTaskPackageName;
        }
    };

    @MainThread
    private void showToolbar() {
        mLayout.findViewById(R.id.action_bar).setVisibility(View.VISIBLE);
    }

    @MainThread
    private void hideToolbar() {
        mLayout.findViewById(R.id.action_bar).setVisibility(View.GONE);
    }

    private boolean requiresDisplayCompat(String packageName) {
        try {
            return mCarPackageManager.requiresDisplayCompat(packageName);
        } catch (Exception e) {
            return false;
        }
    }
}
