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

package com.android.systemui.car.systembar.element;

import android.app.StatusBarManager;
import android.view.Display;
import android.view.View;

import androidx.annotation.CallSuper;

import com.android.systemui.util.ViewController;

public abstract class CarSystemBarElementController<V extends View & CarSystemBarElement>
        extends ViewController<V> {
    private final CarSystemBarElementStatusBarDisableController mElementDisableController;
    private boolean mIsDisabledBySystemBarState;
    private boolean mDisableListenerRegistered = false;
    private final CarSystemBarElementStatusBarDisableController.Listener mDisableListener =
            disabled -> {
                mIsDisabledBySystemBarState = disabled;
                updateVisibility();
            };

    protected CarSystemBarElementController(V view,
            CarSystemBarElementStatusBarDisableController disableController) {
        super(view);
        mElementDisableController = disableController;
    }

    /**
     * Generic factory for CarSystemBarElementController - implementation classes should override
     * with types (without overriding the create method).
     * @param <V> view type that also implements CarSystemBarElement
     * @param <T> controller type that extends CarSystemBarElementController
     */
    public interface Factory<V extends View & CarSystemBarElement,
            T extends CarSystemBarElementController<V>> {
        /** Create instance of CarSystemBarElementController for CarSystemBarElement view */
        T create(V view);
    }

    @Override
    @CallSuper
    protected void onViewAttached() {
        if (shouldRegisterDisableListener()) {
            int displayId = mView.getDisplay() != null ? mView.getDisplay().getDisplayId()
                    : Display.INVALID_DISPLAY;
            if (displayId == Display.INVALID_DISPLAY) return;
            mElementDisableController.addListener(mDisableListener,
                    displayId, mView.getSystemBarDisableFlags(),
                    mView.getSystemBarDisable2Flags(), mView.disableForLockTaskModeLocked());
            mDisableListenerRegistered = true;
        }
    }

    @Override
    @CallSuper
    protected void onViewDetached() {
        if (mDisableListenerRegistered) {
            mElementDisableController.removeListener(mDisableListener);
        }
    }

    /**
     * Updates the visibility of the view based on the system bar states and the state of
     * {@link #shouldBeVisible}.
     */
    protected final void updateVisibility() {
        boolean visible = shouldBeVisible() && !mIsDisabledBySystemBarState;
        mView.setVisibility(visible ? View.VISIBLE : View.INVISIBLE);
    }

    /**
     * Return true if the element should currently be visible - this may be overruled by the
     * system bar disable state flags.
     */
    protected boolean shouldBeVisible() {
        return true;
    }

    private boolean shouldRegisterDisableListener() {
        if (mDisableListenerRegistered) return false;
        return mView.getSystemBarDisableFlags() != StatusBarManager.DISABLE_NONE
                || mView.getSystemBarDisable2Flags() != StatusBarManager.DISABLE2_NONE
                || mView.disableForLockTaskModeLocked();
    }
}
