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

package com.android.systemui.wm;

import android.os.Handler;
import android.util.SparseArray;

import com.android.internal.statusbar.IStatusBarService;
import com.android.systemui.car.systembar.CarSystemBar;
import com.android.systemui.dagger.qualifiers.Main;
import com.android.systemui.statusbar.CommandQueue;
import com.android.wm.shell.common.DisplayImeController;
import com.android.wm.shell.sysui.ShellInit;

import java.util.HashSet;
import java.util.Set;

/**
 * b/259604616, This controller is created as a workaround for NavBar issues in concurrent
 * {@link CarSystemBar}/SystemUI.
 * Problem: CarSystemBar relies on {@link IStatusBarService},
 * which can register only one process to listen for the {@link CommandQueue} events.
 * Solution: {@link MDSystemBarsController} intercepts the Keyboard events from
 * {@link DisplayImeController} and notifies its listener for both Primary and Secondary SystemUI
 * process.
 */
public class MDSystemBarsController {

    private static final String TAG = MDSystemBarsController.class.getSimpleName();
    private SparseArray<Set<Listener>> mListeners;
    private final DisplayImeController mDisplayImeController;
    private final Handler mMainHandler;

    public MDSystemBarsController(DisplayImeController displayImeController,
            @Main Handler mainHandler,
            ShellInit shellInit) {
        this.mDisplayImeController = displayImeController;
        this.mMainHandler = mainHandler;
        shellInit.addInitCallback(this::init, this);
    }

    /**
     * Adds a listener for a display.
     *
     * @param listener  SystemBar Inset events
     * @param displayId id associated with the display
     */
    public void addListener(Listener listener, int displayId) {
        if (mListeners == null) {
            mListeners = new SparseArray<>();
        }
        Set<Listener> displayListeners = mListeners.get(displayId);
        if (displayListeners == null) {
            displayListeners = new HashSet<>();
            mListeners.put(displayId, displayListeners);
        }
        displayListeners.add(listener);
    }

    /**
     * Remove a listener for a display
     *
     * @param listener  SystemBar Inset events Listener
     * @param displayId id associated with the display
     * @return if set contains such a listener, returns {@code true} otherwise false
     */
    public boolean removeListener(Listener listener, int displayId) {
        if (mListeners == null) {
            return false;
        }
        Set<Listener> displayListeners = mListeners.get(displayId);
        if (displayListeners == null) {
            return false;
        }
        return displayListeners.remove(listener);
    }

    private void init() {
        mDisplayImeController.addPositionProcessor(new DisplayImeController.ImePositionProcessor() {
            @Override
            public void onImeVisibilityChanged(int displayId, boolean isShowing) {
                if (mListeners.get(displayId) != null) {
                    mMainHandler.post(() -> {
                        for (Listener l: mListeners.get(displayId)) {
                            l.onKeyboardVisibilityChanged(isShowing);
                        }
                    });
                }
            }
        });

    }

    /**
     * Listener for SystemBar insets events
     */
    public interface Listener {
        /**
         * show/hide keyboard
         */
        void onKeyboardVisibilityChanged(boolean showing);
    }
}
