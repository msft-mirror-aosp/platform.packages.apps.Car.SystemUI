/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.systemui.car.systembar;

import static android.view.Display.DEFAULT_DISPLAY;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.ActivityManager;
import android.content.Context;
import android.graphics.PixelFormat;
import android.hardware.input.InputManager;
import android.os.Handler;
import android.os.Looper;
import android.os.RemoteException;
import android.os.SystemClock;
import android.util.Log;
import android.view.Gravity;
import android.view.InputChannel;
import android.view.InputEvent;
import android.view.InputEventReceiver;
import android.view.InputMonitor;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.WindowMetrics;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.RequiresPermission;

import com.android.systemui.R;

public class CarAppFloatingButtonManager {

    private static final String TAG = "CarAppFloatingButtonManager";
    private static final String TOUCH_INPUT_CHANNEL_NAME = "TouchMonitor";
    private static final int LONG_CLICK_DURATION_MS = 1000;
    private static final int ANIMATION_DURATION_MS = 1000;
    private static final int ANIMATION_DELAY_MS = 300;
    private static final int DRAG_DELAY_MS = 300;
    private static final long INPUT_IDLE_TIMEOUT = 10000;
    private static final long INACTIVITY_TIMEOUT_MS = 12000;

    private AnimatorSet initialAnimSet;
    private Context mContext;
    private WindowManager windowManager;
    private View appFloatingButtonView;
    private WindowManager.LayoutParams params;
    private ImageButton backButton;
    private InputEventReceiver inputEventReceiver;
    private InputChannel inputChannel;
    private InputManager inputManager;
    private InputMonitor inputMonitor;
    private TextView btnText;

    private boolean initialAnimationShown = false;
    private boolean isFadingIn = false;
    private boolean isInputMonitorActive = false;
    private boolean isSystemFade = false;
    private int screenHeight;
    private int tapThreshold;

    private Handler inputMonitorHandler;
    private Runnable inputMonitorRunnable;
    private Handler inactivityHandler;
    private Runnable inactivityRunnable;

    public CarAppFloatingButtonManager(Context context) {
        this.mContext = context;
        this.windowManager = context.getSystemService(WindowManager.class);
        initializeAppFloatingButton();
    }

    private boolean canLog() {
        return Log.isLoggable(TAG, Log.DEBUG);
    }

    /**
     * Initializes the app floating back button by inflating the layout, setting up button listeners
     * and configuring window parameters.
     */
    private void initializeAppFloatingButton() {
        LayoutInflater inflater = mContext.getSystemService(LayoutInflater.class);
        appFloatingButtonView = inflater.inflate(R.layout.car_app_fab_back_button, null);

        WindowMetrics windowMetrics = windowManager.getCurrentWindowMetrics();
        screenHeight = windowMetrics.getBounds().height();
        tapThreshold = (int) (screenHeight * 0.01);


        params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                        | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT
        );
        params.setTrustedOverlay();
        params.privateFlags |= WindowManager.LayoutParams.SYSTEM_FLAG_SHOW_FOR_ALL_USERS;
        params.gravity = Gravity.LEFT | Gravity.TOP;
        params.x = 0;
        params.y = screenHeight / 3;

        backButton = appFloatingButtonView.findViewById(R.id.car_fab_btn_back);
        btnText = appFloatingButtonView.findViewById(R.id.car_fab_btn_text);

        backButton.setOnTouchListener(new TouchListener());
        backButton.setOnClickListener(v -> {
            if (canLog()) {
                Log.d(TAG, "Back button clicked");
            }
            backButton.setActivated(true);
            backButton.postDelayed(() -> backButton.setActivated(false), ANIMATION_DELAY_MS);
            sendVirtualKeyPress(KeyEvent.KEYCODE_BACK);
        });

        inactivityHandler = new Handler();
        inactivityRunnable = new Runnable() {
            @Override
            public void run() {
                if(!isSystemFade) {
                    btnFadeOut(0.25f);
                }
            }
        };

        inputMonitorHandler = new Handler();
        inputMonitorRunnable = new Runnable() {
            @Override
            public void run() {
                btnFadeOut(0.0f);
                isSystemFade = true;
            }
        };
    }

    /**
     * Shows the app floating back button with animations if it's not already displayed.
     */
    public void showAppFloatingButton() {
        if (appFloatingButtonView != null) {
            if (!appFloatingButtonView.isAttachedToWindow()) {
                if (!initialAnimationShown) {
                    addFloatingButton();
                    applyInitialAnimations();
                } else {
                    addFloatingButton();
                    applyRippleEffect();
                }
            } else {
                windowManager.updateViewLayout(appFloatingButtonView, params);
            }
        }
    }

    private void addFloatingButton() {
        if (appFloatingButtonView.getParent() != null) {
            windowManager.removeView(appFloatingButtonView);
        }
        windowManager.addView(appFloatingButtonView, params);
        activateInputMonitor();
        inputMonitorHandler.removeCallbacks(inputMonitorRunnable);
        inputMonitorHandler.postDelayed(inputMonitorRunnable, INPUT_IDLE_TIMEOUT);
        inactivityHandler.removeCallbacks(inactivityRunnable);
        inactivityHandler.postDelayed(inactivityRunnable, INACTIVITY_TIMEOUT_MS);
    }

    /**
     * Removes the app floating back button from the window.
     */
    public void removeAppFloatingButton() {
        if (appFloatingButtonView != null && appFloatingButtonView.isAttachedToWindow()) {
            windowManager.removeView(appFloatingButtonView);
            deactivateInputMonitor();
        }
    }

    /**
     * Applies the initial animation to the control bar, followed by a ripple effect.
     */
    private void applyInitialAnimations() {
        backButton.setActivated(true);
        backButton.setClickable(false);
        btnText.setText(R.string.fab_drag_up);
        btnText.setTextColor(mContext.getResources().getColor(R.color.fab_white));
        btnText.setBackgroundColor(
            mContext.getResources().getColor(R.color.fab_default_background));
        btnText.setVisibility(View.VISIBLE);

        ValueAnimator moveUpAnimator = ValueAnimator.ofInt(params.y, params.y - 100);
        moveUpAnimator.setDuration(ANIMATION_DURATION_MS);
        moveUpAnimator.addUpdateListener(
            animation -> updateViewLayout((int) animation.getAnimatedValue()));

        ValueAnimator moveDownAnimator = ValueAnimator.ofInt(params.y - 100, params.y);
        moveDownAnimator.setDuration(ANIMATION_DURATION_MS);
        moveDownAnimator.addUpdateListener(
            animation -> updateViewLayout((int) animation.getAnimatedValue()));

        initialAnimSet = new AnimatorSet();
        initialAnimSet.playSequentially(moveUpAnimator, moveDownAnimator);
        initialAnimSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                animateText(mContext.getString(R.string.fab_drag_up),
                    mContext.getString(R.string.fab_drag_down));
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                backButton.setActivated(false);
                animateBackText();
                initialAnimationShown = true;
                backButton.setClickable(true);
            }
        });
        initialAnimSet.start();
    }

    private void applyRippleEffect() {
        btnFadeIn();
        backButton.setClickable(false);
        backButton.setPressed(true);
        backButton.postDelayed(() -> backButton.setPressed(false), ANIMATION_DELAY_MS);
        animateBackLongPressText();
    }

    private void animateText(String firstText, String secondText) {
        btnText.setText(firstText);
        btnText.animate().alpha(1f).setDuration(ANIMATION_DURATION_MS / 2)
                .withEndAction(() ->
                    btnText.animate().alpha(0f).setDuration(ANIMATION_DURATION_MS / 2)
                            .withEndAction(() -> {
                                btnText.setText(secondText);
                                btnText.animate().alpha(1f).setDuration(ANIMATION_DURATION_MS / 2);
                            }).start()
                ).start();
    }

    private void animateBackText() {
        btnText.setText(R.string.fab_back_button);
        btnText.setAlpha(1f);
        backButton.postDelayed(() -> {
            btnText.animate()
                   .alpha(0f)
                   .setDuration(ANIMATION_DURATION_MS)
                   .withEndAction(()  -> {
                       btnText.setVisibility(View.GONE);
                       applyRippleEffect();
                   })
                   .start();
        }, ANIMATION_DURATION_MS);
    }

    private void animateBackLongPressText() {
        btnText.setText(R.string.fab_tooltip);
        btnText.setVisibility(View.VISIBLE);
        btnText.setAlpha(1f);
        backButton.postDelayed(() -> {
            btnText.animate()
                   .alpha(0f)
                   .setDuration(ANIMATION_DURATION_MS)
                   .withEndAction(() -> btnText.setVisibility(View.GONE))
                   .start();
        }, ANIMATION_DURATION_MS);
    }

    // Fade in animation
    private void btnFadeIn() {
        // Suppress click during fade-in
        isFadingIn = true;

        // Fade in the back button to full opacity
        ObjectAnimator fadeIn = ObjectAnimator.ofFloat(backButton, "alpha", 0.25f, 1f);
        fadeIn.setDuration(ANIMATION_DURATION_MS);

        fadeIn.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                // Allow click events after fade-in completes
                isFadingIn = false;
            }
        });

        fadeIn.start();
    }

    // Fade out animation
    private void btnFadeOut(float level) {
        ObjectAnimator fadeOut = ObjectAnimator.ofFloat(backButton, "alpha", 1f, level);
        fadeOut.setDuration(ANIMATION_DURATION_MS);
        fadeOut.start();
    }

    /**
     * Updates the layout of the control bar with the new Y position.
     *
     * @param newY The new Y position.
     */
    private void updateViewLayout(int newY) {
        if (appFloatingButtonView.isAttachedToWindow()) {
            try {
                params.y = newY;
                windowManager.updateViewLayout(appFloatingButtonView, params);
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "Failed to update view layout. View not attached to window.", e);
            }
        } else {
            Log.w(TAG, "Skipped updateViewLayout. View not attached, clearning animations");
            if (initialAnimSet != null && initialAnimSet.isRunning()) {
                initialAnimSet.removeAllListeners();
                initialAnimSet.cancel();
                initialAnimSet = null;
            }
            initialAnimationShown = true;
            backButton.setClickable(true);
        }
    }

    // Activates InputMonitor only when needed
    private void activateInputMonitor() {
        if (!isInputMonitorActive) {
            inputManager = mContext.getSystemService(InputManager.class);
            inputMonitor = inputManager.monitorGestureInput(TOUCH_INPUT_CHANNEL_NAME,
                DEFAULT_DISPLAY);
            inputChannel = inputMonitor.getInputChannel();

            inputEventReceiver = new InputEventReceiver(inputChannel, Looper.getMainLooper()) {
                @Override
                public void onInputEvent(InputEvent event) {
                    if (event instanceof MotionEvent) {
                        MotionEvent motionEvent = (MotionEvent) event;
                        if (motionEvent.getAction() == MotionEvent.ACTION_DOWN) {
                            resetInputMonitor();
                        }
                    }
                    finishInputEvent(event, true);
                }
            };
            isInputMonitorActive = true;
        }
    }

    // Method to reset the input monitor timer
    private void resetInputMonitor() {
        inputMonitorHandler.removeCallbacks(inputMonitorRunnable);
        inputMonitorHandler.postDelayed(inputMonitorRunnable, INPUT_IDLE_TIMEOUT);
        if (backButton.getAlpha() == 0) {
            btnFadeIn();
            isSystemFade = false;
            inactivityHandler.postDelayed(inactivityRunnable, INACTIVITY_TIMEOUT_MS);
        }
    }

    // Deactivates InputMonitor to save resources
    private void deactivateInputMonitor() {
        inputMonitorHandler.removeCallbacks(inputMonitorRunnable);
        if (isInputMonitorActive) {
            if (inputEventReceiver != null) {
                inputEventReceiver.dispose();
                inputEventReceiver = null;
            }
            if (inputMonitor != null) {
                inputMonitor.dispose();
                inputMonitor = null;
            }
            isInputMonitorActive = false;
        }
    }

    // Inner class for handling touch events on the back button
    private class TouchListener implements View.OnTouchListener {
        private float initialTouchY;
        private float initialTouchX;
        private int initialY;
        private boolean isLongClick = false;
        private boolean isDragging = false;

        private Handler longClickHandler = new Handler();
        private Runnable longClickRunnable = () -> handleLongClick();
        private Handler dragHandler = new Handler();
        private Runnable dragRunnable = () -> isDragging = true;

        private final int dragThreshold = tapThreshold * 2;


        @Override
        public boolean onTouch(View v, MotionEvent event) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    handleDownEvent(event);
                    return true;

                case MotionEvent.ACTION_MOVE:
                    handleMoveEvent(event);
                    return true;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    handleActionUp(event);
                    return true;

                default:
                    return false;
            }
        }

        private void handleDownEvent(MotionEvent event) {
            if (backButton.getAlpha() < 1f) {
                btnFadeIn();
            }
            initialTouchY = event.getRawY();
            initialTouchX = event.getRawX();
            initialY = params.y;
            isLongClick = false;
            isDragging = false;
            backButton.setActivated(true);
            dragHandler.postDelayed(dragRunnable, DRAG_DELAY_MS);
            longClickHandler.postDelayed(longClickRunnable, LONG_CLICK_DURATION_MS);
            inactivityHandler.removeCallbacks(inactivityRunnable);
        }

        private void handleMoveEvent(MotionEvent event) {
            float deltaX = event.getRawX() - initialTouchX;
            float deltaY = event.getRawY() - initialTouchY;
            if (!isDragging &&
                    (Math.abs(deltaX) > tapThreshold || Math.abs(deltaY) > tapThreshold)) {
                longClickHandler.removeCallbacks(longClickRunnable);
                isDragging = true;
            }

            if (isDragging) {
                params.y = (int) (initialY + deltaY);
                windowManager.updateViewLayout(appFloatingButtonView, params);
            }
        }

        private void handleActionUp(MotionEvent event) {
            longClickHandler.removeCallbacks(longClickRunnable);
            dragHandler.removeCallbacks(dragRunnable);
            inactivityHandler.postDelayed(inactivityRunnable, INACTIVITY_TIMEOUT_MS);

            if (!isDragging && !isLongClick && !isFadingIn) {
                backButton.performClick();
            }

            backButton.setActivated(false);
        }

        private void handleLongClick() {
            isLongClick = true;
            if (canLog()) {
                Log.d(TAG, "Long click detected");
            }
            backButton.setPressed(true);
            backButton.postDelayed(() -> backButton.setPressed(false), 200);
            closeCurrentForegroundApp();
        }
    }

    /**
     * Sends a virtual key press event.
     *
     * @param keyCode The key code of the virtual key press.
     */
    private void sendVirtualKeyPress(int keyCode) {
        long downEventTime = SystemClock.uptimeMillis();
        long upEventTime = downEventTime + 1;
        final KeyEvent keydown = new KeyEvent(downEventTime, downEventTime, KeyEvent.ACTION_DOWN,
                keyCode, /* repeat= */ 0, /* metaState= */ 0,
                KeyCharacterMap.VIRTUAL_KEYBOARD, /* scancode= */ 0, KeyEvent.FLAG_FROM_SYSTEM);
        final KeyEvent keyup = new KeyEvent(upEventTime, upEventTime, KeyEvent.ACTION_UP,
                keyCode, /* repeat= */ 0, /* metaState= */ 0,
                KeyCharacterMap.VIRTUAL_KEYBOARD, /* scancode= */ 0, KeyEvent.FLAG_FROM_SYSTEM);

        InputManager inputManager = mContext.getSystemService(InputManager.class);
        inputManager.injectInputEvent(keydown, InputManager.INJECT_INPUT_EVENT_MODE_ASYNC);
        inputManager.injectInputEvent(keyup, InputManager.INJECT_INPUT_EVENT_MODE_ASYNC);
    }

    /**
     * Closes the current foreground app.
     *
     */
    @RequiresPermission(android.Manifest.permission.REMOVE_TASKS)
    private void closeCurrentForegroundApp() {
        if (canLog()) {
            Log.d(TAG, "closeCurrentForegroundApp");
        }
        ActivityManager activityManager = mContext.getSystemService(ActivityManager.class);
        if (activityManager != null) {
            ActivityManager.RunningTaskInfo topTask = activityManager.getRunningTasks(1).get(0);
            if (topTask != null) {
                int taskId = topTask.id;
                try {
                    ActivityManager.getService().removeTask(taskId);
                } catch (RemoteException e) {
                    e.rethrowFromSystemServer();
                }
            }
        }
    }
}
