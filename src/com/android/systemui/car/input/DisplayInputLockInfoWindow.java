/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.systemui.car.input;

import static android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
import static android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
import static android.view.WindowManager.LayoutParams.SYSTEM_FLAG_SHOW_FOR_ALL_USERS;
import static android.view.WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY;
import static android.view.WindowManager.LayoutParams.WRAP_CONTENT;

import android.annotation.NonNull;
import android.content.Context;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.transition.Fade;
import android.transition.Transition;
import android.transition.TransitionListenerAdapter;
import android.transition.TransitionManager;
import android.transition.TransitionSet;
import android.view.Display;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;

import androidx.annotation.MainThread;

import com.android.systemui.R;
import com.android.systemui.animation.Interpolators;

/**
 * A simple implementation to show a lock icon on the screen when user touches it,
 * indicating display input lock is currently enabled.
 */
public final class DisplayInputLockInfoWindow {
    private final Handler mHandler;
    private final WindowManager mWindowManager;
    private final WindowManager.LayoutParams mLp;
    private final ViewGroup mLockInfoViewGroup;
    private final View mLockIconView;
    private final long mDismissAnimDurationMs;
    private final long mAutoDismissDelayMs;

    private boolean mIsShown;  // Only accessed from MainThread.
    private boolean mIsTransitioningToHide;  // Only accessed from MainThread.

    private final Runnable mHideInputLockInfoRunnable = () -> {
        hideInputLockInfoWindow();
    };

    private final Runnable mShowInputLockInfoRunnable = () -> {
        showInputLockInfoWindow();
    };

    /**
     * Construct a new {@link DisplayInputLockInfoWindow}.
     *
     * <p> Must be called from the main thread.
     *
     * @param context The context used to access resources and create a {@link WindowContext}.
     * @param display The display to add the input lock info window on.
     */
    @MainThread
    public DisplayInputLockInfoWindow(@NonNull Context context, @NonNull Display display) {
        mHandler = context.getMainThreadHandler();
        // Construct an instance of WindowManager to add the input lock info window of
        // TYPE_SYSTEM_OVERLAY to the Display `display`.
        mWindowManager = context.createWindowContext(display, TYPE_SYSTEM_OVERLAY,
                /* options= */ null).getSystemService(WindowManager.class);
        mLockInfoViewGroup = (ViewGroup) LayoutInflater.from(context)
                .inflate(R.layout.display_input_locked, /* root= */ null);
        mLockIconView = mLockInfoViewGroup.findViewById(R.id.display_input_lock_icon);
        mDismissAnimDurationMs = context.getResources().getInteger(
                R.integer.config_displayInputLockIconAnimDuration);
        mAutoDismissDelayMs = context.getResources().getInteger(
                R.integer.config_displayInputLockIconDismissDelay);
        mLp = new WindowManager.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, TYPE_SYSTEM_OVERLAY,
                FLAG_NOT_FOCUSABLE | FLAG_NOT_TOUCHABLE, PixelFormat.RGBA_8888);

        mLp.gravity = Gravity.CENTER;
        mLp.privateFlags |= SYSTEM_FLAG_SHOW_FOR_ALL_USERS;
        mLp.setTitle("DisplayInputLockInfo#" + display.getDisplayId());
    }

    /**
     * Show input lock info window.
     *
     * <p> Can be called from any thread.
     */
    public void show() {
        mHandler.removeCallbacks(mShowInputLockInfoRunnable);
        mHandler.post(mShowInputLockInfoRunnable);
        hideDelayed(mAutoDismissDelayMs);
    }

    /**
     * Hide input lock info window.
     *
     * <p> Can be called from any thread.
     */
    public void hide() {
        mHandler.removeCallbacks(mHideInputLockInfoRunnable);
        mHandler.post(mHideInputLockInfoRunnable);
    }

    private void hideDelayed(long delayMillis) {
        mHandler.removeCallbacks(mHideInputLockInfoRunnable);
        mHandler.postDelayed(mHideInputLockInfoRunnable, delayMillis);
    }

    @MainThread
    private void showInputLockInfoWindow() {
        if (mIsShown) {
            return;
        }
        mIsShown = true;
        mLockIconView.setVisibility(View.VISIBLE);
        mWindowManager.addView(mLockInfoViewGroup, mLp);
    }

    @MainThread
    private void hideInputLockInfoWindow() {
        if (!mIsShown || mIsTransitioningToHide) {
            return;
        }

        if (shouldAnimateLockIconView()) {
            mIsTransitioningToHide = true;
            TransitionSet transition = new TransitionSet();
            transition.addTransition(new Fade(Fade.OUT));
            transition.setDuration(mDismissAnimDurationMs);
            transition.setInterpolator(Interpolators.DECELERATE_QUINT);
            transition.addListener(new TransitionListenerAdapter() {
                @Override
                @MainThread
                public void onTransitionEnd(Transition transition) {
                    transition.removeListener(this);
                    hideImmediately();
                }
            });
            TransitionManager.beginDelayedTransition(mLockInfoViewGroup, transition);
            mLockIconView.setVisibility(View.INVISIBLE);
        } else {
            hideImmediately();
        }
    }

    @MainThread
    private void hideImmediately() {
        mWindowManager.removeView(mLockInfoViewGroup);
        mIsShown = false;
        mIsTransitioningToHide = false;
    }

    @MainThread
    private boolean shouldAnimateLockIconView() {
        // The lock icon view is animated only if the container view {@code mLockInfoViewGroup} has
        // already been laid out. If it hasn't been laid out, it hasn't been drawn to screen yet.
        return mLockInfoViewGroup.isLaidOut();
    }
}
