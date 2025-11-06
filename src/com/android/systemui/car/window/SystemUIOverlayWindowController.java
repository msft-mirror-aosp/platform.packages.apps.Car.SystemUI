/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.systemui.car.window;

import static android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE;
import static android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;

import android.content.Context;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.os.Binder;
import android.util.Log;
import android.view.DisplayCutout;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;

import androidx.annotation.Nullable;

import com.android.systemui.R;
import com.android.systemui.dagger.SysUISingleton;
import com.android.systemui.statusbar.policy.ConfigurationController;

import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Provider;

/**
 * Controls the expansion state of the primary window which will contain all of the fullscreen sysui
 * behavior. This window still has a collapsed state in order to watch for swipe events to expand
 * this window for the notification panel.
 */
@SysUISingleton
public class SystemUIOverlayWindowController {
    private static final String TAG = "SystemUIOverlayWindowController";

    /**
     * Touch listener to get touches on the view.
     */
    public interface OnTouchListener {

        /**
         * Called when a touch happens on the view.
         */
        void onTouch(View v, MotionEvent event);
    }

    private final Context mContext;
    private final WindowManager mWindowManager;
    private final Map<String, Provider<OverlayViewController>> mOverlayControllerProviders;
    private final Map<String, ViewGroup> mContainerCache = new HashMap<>();

    private ViewGroup mBaseLayout;
    private WindowManager.LayoutParams mLp;
    private WindowManager.LayoutParams mLpChanged;
    private boolean mVisible = false;
    private boolean mFocusable = false;
    private boolean mUsingStableInsets = false;

    private boolean mInternalIsAttached = false;
    private boolean mIsAttaching = false;

    @Inject
    public SystemUIOverlayWindowController(
            Context context,
            ConfigurationController configurationController,
            Map<String, Provider<OverlayViewController>> overlayControllerProviders) {
        mContext = context.createWindowContext(WindowManager.LayoutParams.TYPE_NOTIFICATION_SHADE,
                /* options= */ null);
        mWindowManager = mContext.getSystemService(WindowManager.class);
        mOverlayControllerProviders = overlayControllerProviders;

        mLpChanged = new WindowManager.LayoutParams();

        inflateBaseLayout();
    }

    private void inflateBaseLayout() {
        mBaseLayout = (ViewGroup) LayoutInflater.from(mContext)
                .inflate(R.layout.sysui_overlay_window, /* root= */ null, false);
        mBaseLayout.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View v) {
                mIsAttaching = false;
                mInternalIsAttached = true;
            }

            @Override
            public void onViewDetachedFromWindow(View v) {
                mIsAttaching = false;
                mInternalIsAttached = false;
            }
        });
        // Pre-populate the cache
        for (int i = 0; i < mBaseLayout.getChildCount(); i++) {
            View child = mBaseLayout.getChildAt(i);
            if (child instanceof ViewGroup && child.getTag() != null) {
                mContainerCache.put((String) child.getTag(), (ViewGroup) child);
            }
        }
        attach();
        setWindowVisible(false);
    }

    /**
     * Register to {@link OnTouchListener}
     */
    public void registerOutsideTouchListener(OnTouchListener listener) {
        mBaseLayout.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                listener.onTouch(v, event);
                return false;
            }
        });
    }

    /** Returns the base view of the primary window. */
    public ViewGroup getBaseLayout() {
        return mBaseLayout;
    }

    /** Returns {@code true} if the window is being attached. */
    private boolean isAttaching() {
        return mIsAttaching;
    }

    /** Returns {@code true} if the window is already attached. */
    private boolean isAttached() {
        // For some reason the mBaseLayout is dettached but we never receive a call to
        // onViewDetachedFromWindow. But mBaseLayout.isAttachedToWindow() has the correct value.
        boolean isReallyAttached = (mBaseLayout != null && mBaseLayout.isAttachedToWindow());

        if (mInternalIsAttached && !isReallyAttached) {
            mInternalIsAttached = false;
            mIsAttaching = false;
            return false;
        }

        if (mIsAttaching) {
            // If we are in the process of attaching, return the actual state.
            return false;
        }

        return mInternalIsAttached;
    }

    /** Attaches the window to the window manager. */
    private void attach() {
        if (isAttached() || isAttaching()) {
            return;
        }
        // Now that the status bar window encompasses the sliding panel and its
        // translucent backdrop, the entire thing is made TRANSLUCENT and is
        // hardware-accelerated.
        mLp = new WindowManager.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_NOTIFICATION_SHADE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_TOUCHABLE_WHEN_WAKING
                        | WindowManager.LayoutParams.FLAG_SPLIT_TOUCH
                        | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                        | WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS
                        | WindowManager.LayoutParams.FLAG_DIM_BEHIND,
                PixelFormat.TRANSLUCENT);
        mLp.token = new Binder();
        mLp.gravity = Gravity.TOP;
        mLp.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE;
        mLp.dimAmount = 0f;
        mLp.setTitle("SystemUIOverlayWindow");
        mLp.packageName = mContext.getPackageName();
        mLp.layoutInDisplayCutoutMode = LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        mLp.insetsFlags.behavior = BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE;

        mWindowManager.addView(mBaseLayout, mLp);
        mLpChanged.copyFrom(mLp);
        mIsAttaching = true;
    }

    /** Sets the types of insets to fit. Note: This should be rarely used. */
    public void setFitInsetsTypes(@WindowInsets.Type.InsetsType int types) {
        mLpChanged.setFitInsetsTypes(types);
        mLpChanged.setFitInsetsIgnoringVisibility(mUsingStableInsets);
        updateWindow();
    }

    /** Sets the sides of system bar insets to fit. Note: This should be rarely used. */
    public void setFitInsetsSides(@WindowInsets.Side.InsetsSide int sides) {
        mLpChanged.setFitInsetsSides(sides);
        mLpChanged.setFitInsetsIgnoringVisibility(mUsingStableInsets);
        updateWindow();
    }

    /** Sets the window to the visible state. */
    public void setWindowVisible(boolean visible) {
        mVisible = visible;
        if (visible) {
            mBaseLayout.setVisibility(View.VISIBLE);
        } else {
            mBaseLayout.setVisibility(View.INVISIBLE);
        }
        updateWindow();
    }

    /** Sets the window to be focusable. */
    public void setWindowFocusable(boolean focusable) {
        mFocusable = focusable;
        if (focusable) {
            mLpChanged.flags &= ~WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        } else {
            mLpChanged.flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        }
        updateWindow();
    }

    /** Sets the dim behind the window */
    public void setDimBehind(float dimAmount) {
        mLpChanged.dimAmount = dimAmount;
        updateWindow();
    }

    /** Sets the window to enable IME. */
    public void setWindowNeedsInput(boolean needsInput) {
        if (needsInput) {
            mLpChanged.flags &= ~WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM;
        } else {
            mLpChanged.flags |= WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM;
        }
        updateWindow();
    }

    /** Returns {@code true} if the window is visible */
    public boolean isWindowVisible() {
        return mVisible;
    }

    public boolean isWindowFocusable() {
        return mFocusable;
    }

    protected void setUsingStableInsets(boolean useStableInsets) {
        mUsingStableInsets = useStableInsets;
    }

    /**
     * Returns the placeholder ViewGroup for the given overlay type String.
     */
    @Nullable
    public ViewGroup getContainerForType(String type) {
        ViewGroup container = mContainerCache.get(type);
        if (container == null) {
            Log.e(TAG, "Could not find container ViewGroup with tag: " + type);
        }
        return container;
    }

    private void updateWindow() {
        if (!isAttached() && !isAttaching()) {
            Log.d(TAG, "Window not attached, attaching in updateWindow");
            attach();
        }

        if (mLp != null && mLp.copyFrom(mLpChanged) != 0) {
            if (isAttached()) {
                handleDisplayCutout();
                mLp.insetsFlags.behavior = BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE;
                mWindowManager.updateViewLayout(mBaseLayout, mLp);
            }
        }
    }

    /**
     * Certain device configurations may contain a curvature at the top-left or top-right of the
     * screen. In those cases, overlay window should still render under the cutout in order to cover
     * the entire screen, but the content of HVAC and Notification Center should be centered around
     * the app area.
     *
     * TODO (b/326495987): Find an alternative approach to update all component together rather than
     * only measuring and updating HVAC and notifications.
     */
    private void handleDisplayCutout() {
        DisplayCutout cutout =
                mWindowManager.getCurrentWindowMetrics().getWindowInsets().getDisplayCutout();
        if (cutout == null) return;

        Rect safeInsets = new Rect(
                cutout.getSafeInsetLeft(),
                cutout.getSafeInsetTop(),
                cutout.getSafeInsetRight(),
                cutout.getSafeInsetBottom()
        );

        for (Provider<OverlayViewController> controllerProvider :
                mOverlayControllerProviders.values()) {
            OverlayViewController controller = controllerProvider.get();
            if (controller.isInflated()) {
                controller.adjustForDisplayCutout(safeInsets);
            }
        }
    }

    void showInsets(int types) {
        WindowInsetsController wic = getBaseLayout().getWindowInsetsController();
        if (wic != null) {
            wic.show(types);
        } else {
            Log.e(TAG, "Cannot showInsets, WindowInsetsController is null");
        }
    }

    void hideInsets(int types) {
        WindowInsetsController wic = getBaseLayout().getWindowInsetsController();
        if (wic != null) {
            wic.hide(types);
        } else {
            Log.e(TAG, "Cannot hideInsets, WindowInsetsController is null");
        }
    }
}
