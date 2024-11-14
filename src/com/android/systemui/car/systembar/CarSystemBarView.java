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

package com.android.systemui.car.systembar;

import android.annotation.IntDef;
import android.annotation.Nullable;
import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;

import com.android.systemui.R;
import com.android.systemui.car.notification.NotificationPanelViewController;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;
import java.util.Set;

/**
 * A custom system bar for the automotive use case.
 * <p>
 * The system bar in the automotive use case is more like a list of shortcuts, rendered
 * in a linear layout.
 */
public class CarSystemBarView extends LinearLayout {

    @IntDef(value = {BUTTON_TYPE_NAVIGATION, BUTTON_TYPE_KEYGUARD, BUTTON_TYPE_OCCLUSION})
    @Target({ElementType.TYPE_PARAMETER, ElementType.TYPE_USE})
    public @interface ButtonsType {
    }

    private static final String TAG = CarSystemBarView.class.getSimpleName();
    private static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);

    public static final int BUTTON_TYPE_NAVIGATION = 0;
    public static final int BUTTON_TYPE_KEYGUARD = 1;
    public static final int BUTTON_TYPE_OCCLUSION = 2;

    private final boolean mConsumeTouchWhenPanelOpen;
    private final boolean mButtonsDraggable;
    private View mNavButtons;
    private View mLockScreenButtons;
    private View mOcclusionButtons;
    // used to wire in open/close gestures for overlay panels
    private Set<OnTouchListener> mStatusBarWindowTouchListeners;
    private NotificationPanelViewController mNotificationPanelViewController;

    public CarSystemBarView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mConsumeTouchWhenPanelOpen = getResources().getBoolean(
                R.bool.config_consumeSystemBarTouchWhenNotificationPanelOpen);
        mButtonsDraggable = getResources().getBoolean(R.bool.config_systemBarButtonsDraggable);
    }

    @Override
    public void onFinishInflate() {
        mNavButtons = findViewById(R.id.nav_buttons);
        mLockScreenButtons = findViewById(R.id.lock_screen_nav_buttons);
        mOcclusionButtons = findViewById(R.id.occlusion_buttons);
        // Needs to be clickable so that it will receive ACTION_MOVE events.
        setClickable(true);
        // Needs to not be focusable so rotary won't highlight the entire nav bar.
        setFocusable(false);
    }

    // Used to forward touch events even if the touch was initiated from a child component
    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (mStatusBarWindowTouchListeners != null && !mStatusBarWindowTouchListeners.isEmpty()) {
            if (!mButtonsDraggable) {
                return false;
            }
            boolean shouldConsumeEvent = mNotificationPanelViewController == null ? false
                    : mNotificationPanelViewController.isPanelExpanded();

            // Forward touch events to the status bar window so it can drag
            // windows if required (ex. Notification shade)
            triggerAllTouchListeners(this, ev);

            if (mConsumeTouchWhenPanelOpen && shouldConsumeEvent) {
                return true;
            }
        }
        return super.onInterceptTouchEvent(ev);
    }

    /**
     * Sets the touch listeners that will be called from onInterceptTouchEvent and onTouchEvent
     *
     * @param statusBarWindowTouchListeners List of listeners to call from touch and intercept touch
     */
    public void setStatusBarWindowTouchListeners(
            Set<OnTouchListener> statusBarWindowTouchListeners) {
        mStatusBarWindowTouchListeners = statusBarWindowTouchListeners;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        triggerAllTouchListeners(this, event);
        return super.onTouchEvent(event);
    }

    /**
     * Shows buttons of the specified {@link ButtonsType}.
     *
     * NOTE: Only one type of buttons can be shown at a time, so showing buttons of one type will
     * hide all buttons of other types.
     *
     * @param buttonsType
     */
    public void showButtonsOfType(@ButtonsType int buttonsType) {
        switch(buttonsType) {
            case BUTTON_TYPE_NAVIGATION:
                setNavigationButtonsVisibility(View.VISIBLE);
                setKeyguardButtonsVisibility(View.GONE);
                setOcclusionButtonsVisibility(View.GONE);
                break;
            case BUTTON_TYPE_KEYGUARD:
                setNavigationButtonsVisibility(View.GONE);
                setKeyguardButtonsVisibility(View.VISIBLE);
                setOcclusionButtonsVisibility(View.GONE);
                break;
            case BUTTON_TYPE_OCCLUSION:
                setNavigationButtonsVisibility(View.GONE);
                setKeyguardButtonsVisibility(View.GONE);
                setOcclusionButtonsVisibility(View.VISIBLE);
                break;
        }
    }

    /**
     * Sets the system bar view's disabled state and runnable when disabled.
     */
    public void setDisabledSystemBarButton(int viewId, boolean disabled, Runnable runnable,
                @Nullable String buttonName) {
        CarSystemBarButton button = findViewById(viewId);
        if (button != null) {
            if (DEBUG) {
                Log.d(TAG, "setDisabledSystemBarButton for: " + buttonName + " to: " + disabled);
            }
            button.setDisabled(disabled, runnable);
        }
    }

    /**
     * Sets the NotificationPanelViewController and adds button listeners
     */
    public void registerNotificationPanelViewController(
            NotificationPanelViewController controller) {
        mNotificationPanelViewController = controller;
    }

    private void setNavigationButtonsVisibility(@View.Visibility int visibility) {
        if (mNavButtons != null) {
            mNavButtons.setVisibility(visibility);
        }
    }

    private void setKeyguardButtonsVisibility(@View.Visibility int visibility) {
        if (mLockScreenButtons != null) {
            mLockScreenButtons.setVisibility(visibility);
        }
    }

    private void setOcclusionButtonsVisibility(@View.Visibility int visibility) {
        if (mOcclusionButtons != null) {
            mOcclusionButtons.setVisibility(visibility);
        }
    }

    private void triggerAllTouchListeners(View view, MotionEvent event) {
        if (mStatusBarWindowTouchListeners == null) {
            return;
        }
        for (OnTouchListener listener : mStatusBarWindowTouchListeners) {
            listener.onTouch(view, event);
        }
    }
}
