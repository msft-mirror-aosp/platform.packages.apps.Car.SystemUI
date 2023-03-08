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

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.car.settings.CarSettings;
import android.content.Context;
import android.database.ContentObserver;
import android.hardware.display.DisplayManager;
import android.net.Uri;
import android.os.Handler;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.Settings;
import android.util.ArraySet;
import android.util.IntArray;
import android.util.Log;
import android.util.Slog;
import android.util.SparseArray;
import android.view.Display;

import androidx.annotation.MainThread;
import androidx.annotation.VisibleForTesting;

import com.android.systemui.CoreStartable;
import com.android.systemui.R;
import com.android.systemui.dagger.SysUISingleton;
import com.android.systemui.dagger.qualifiers.Main;

import java.io.PrintWriter;

import javax.inject.Inject;

/**
 * Controls display input lock. It observes for when the setting is changed and starts/stops
 * display input lock accordingly.
 */
@SysUISingleton
public final class DisplayInputSinkController implements CoreStartable {
    private static final String TAG = "DisplayInputLock";
    static final boolean DBG = Log.isLoggable(TAG, Log.DEBUG);

    private static final Uri DISPLAY_INPUT_LOCK_URI =
            Settings.Global.getUriFor(CarSettings.Global.DISPLAY_INPUT_LOCK);

    private final Context mContext;
    private final Handler mHandler;
    private final DisplayManager mDisplayManager;
    private final ContentObserver mSettingsObserver;

    // Array of input locks that are currently on going. (key: displayId)
    @VisibleForTesting
    final SparseArray<DisplayInputSink> mDisplayInputSinks = new SparseArray<>();
    // Array of display unique ids from the display input lock setting.
    @VisibleForTesting
    final ArraySet<String> mDisplayInputLockSetting = new ArraySet<>();

    @VisibleForTesting
    final DisplayManager.DisplayListener mDisplayListener =
            new DisplayManager.DisplayListener() {
        @Override
        @MainThread
        public void onDisplayAdded(int displayId) {
            Display display = mDisplayManager.getDisplay(displayId);
            if (display == null) {
                return;
            }

            if (mDisplayInputLockSetting.contains(display.getUniqueId())) {
                startDisplayInputLock(displayId);
            }
        }

        @Override
        @MainThread
        public void onDisplayRemoved(int displayId) {
            if (isDisplayInputLockStarted(displayId)) {
                stopDisplayInputLock(displayId);
            }
        }

        @Override
        @MainThread
        public void onDisplayChanged(int displayId) {}
    };

    @Inject
    public DisplayInputSinkController(Context context, @Main Handler handler) {
        mContext = context;
        mHandler = handler;
        mDisplayManager = mContext.getSystemService(DisplayManager.class);
        mSettingsObserver = new ContentObserver(mHandler) {
            @Override
            @MainThread
            public void onChange(boolean selfChange, Uri uri) {
                refreshDisplayInputLock();
            }
        };
    }

    @Override
    public void start() {
        if (UserHandle.myUserId() != UserHandle.USER_SYSTEM
                && UserManager.isHeadlessSystemUserMode()) {
            Slog.i(TAG, "Disable DisplayInputSinkController for non system user "
                    + UserHandle.myUserId());
            return;
        }

        mContext.getContentResolver().registerContentObserver(DISPLAY_INPUT_LOCK_URI,
                /* notifyForDescendants= */ false, mSettingsObserver);
        mDisplayManager.registerDisplayListener(mDisplayListener, mHandler);
        refreshDisplayInputLock();
    }

    // Start/stop display input locks from the current global setting.
    @VisibleForTesting
    void refreshDisplayInputLock() {
        String settingValue = getDisplayInputLockSettingValue();
        parseDisplayInputLockSettingValue(CarSettings.Global.DISPLAY_INPUT_LOCK, settingValue);
        if (DBG) {
            Slog.d(TAG, "refreshDisplayInputLock: settingValue=" + settingValue);
        }

        // Get display ids from display unique ids.
        IntArray inputLockedDisplayIds = new IntArray(mDisplayInputLockSetting.size());
        Display[] displays = mDisplayManager.getDisplays();
        mDisplayInputLockSetting.forEach(uniqueId -> {
            inputLockedDisplayIds.add(findDisplayIdByUniqueId(uniqueId, displays));
        });

        // Stop ongoing input locks according to the global setting.
        // Iterate in reverse order as input sinks will be removed when stopping input locks.
        for (int i = mDisplayInputSinks.size() - 1; i >= 0; --i) {
            int displayId = mDisplayInputSinks.keyAt(i);
            int index = inputLockedDisplayIds.indexOf(displayId);
            if (index == -1) {
                // Input lock is disabled.
                stopDisplayInputLock(displayId);
            } else {
                // Same state, just remove value.
                inputLockedDisplayIds.remove(index);
            }
        }

        // Start a new input lock from the global setting.
        int lockSettingCount = inputLockedDisplayIds.size();
        for (int i = 0; i < lockSettingCount; i++) {
            startDisplayInputLock(inputLockedDisplayIds.get(i));
        }
    }

    private String getDisplayInputLockSettingValue() {
        return Settings.Global.getString(mContext.getContentResolver(),
                CarSettings.Global.DISPLAY_INPUT_LOCK);
    }

    private void parseDisplayInputLockSettingValue(@NonNull String settingKey,
            @Nullable String value) {
        mDisplayInputLockSetting.clear();
        if (value == null || value.isEmpty()) {
            return;
        }

        String[] entries = value.split(",");
        int numEntries = entries.length;
        mDisplayInputLockSetting.ensureCapacity(numEntries);
        Display[] displays = mDisplayManager.getDisplays();
        for (int i = 0; i < numEntries; i++) {
            String uniqueId = entries[i];
            if (findDisplayIdByUniqueId(uniqueId, displays) == Display.INVALID_DISPLAY) {
                Slog.w(TAG, "Invalid display id: " + uniqueId);
                continue;
            }
            mDisplayInputLockSetting.add(uniqueId);
        }
    }

    private int findDisplayIdByUniqueId(@NonNull String displayUniqueId,
            @NonNull Display[] displays) {
        for (int i = 0; i < displays.length; i++) {
            Display display = displays[i];
            if (displayUniqueId.equals(display.getUniqueId())) {
                return display.getDisplayId();
            }
        }
        return Display.INVALID_DISPLAY;
    }

    private boolean isDisplayInputLockStarted(int displayId) {
        return mDisplayInputSinks.get(displayId) != null;
    }

    @VisibleForTesting
    void startDisplayInputLock(int displayId) {
        Display display = mDisplayManager.getDisplay(displayId);
        if (display == null) {
            Slog.w(TAG, "Unable to start display input lock: no valid display " + displayId);
            return;
        }
        if (isDisplayInputLockStarted(displayId)) {
            // Already started input lock for the given display.
            if (DBG) {
                Slog.d(TAG, "Input lock is already started for display " + displayId);
            }
            return;
        }
        Slog.i(TAG, "Start input lock for display " + displayId);
        DisplayInputLockInfoWindow lockInfoWindow = createDisplayInputLockInfoWindow(display);
        DisplayInputSink.OnInputEventListener callback = (event) -> {
            if (DBG) {
                Slog.d(TAG, "Received input events while input is locked for display "
                        + event.getDisplayId());
            }
            lockInfoWindow.setText(mContext, R.string.display_input_lock_text);
            lockInfoWindow.setDismissDelay(mContext,
                    R.integer.config_displayInputLockIconDismissDelay);
            lockInfoWindow.show();
        };
        mDisplayInputSinks.put(displayId, new DisplayInputSink(display, callback));
        // Now that the display input lock is started, let's inform the user of it.
        lockInfoWindow.show();
    }

    @VisibleForTesting
    DisplayInputLockInfoWindow createDisplayInputLockInfoWindow(Display display) {
        return new DisplayInputLockInfoWindow(mContext, display,
                        R.string.display_input_lock_initial_text,
                        R.integer.config_displayInputLockInitialDismissDelay);
    }

    @VisibleForTesting
    void stopDisplayInputLock(int displayId) {
        if (!isDisplayInputLockStarted(displayId)) {
            if (DBG) {
                Slog.d(TAG, "There is no input lock started for display " + displayId);
            }
            return;
        }

        Slog.i(TAG, "Stop input lock for display " + displayId);

        DisplayInputSink inputSink = mDisplayInputSinks.get(displayId);
        inputSink.remove();

        mDisplayInputSinks.remove(displayId);
    }

    @Override
    public void dump(@NonNull PrintWriter pw, @NonNull String[] args) {
        pw.println("DisplayInputSinks:");

        int size = mDisplayInputSinks.size();
        for (int i = 0; i < size; i++) {
            DisplayInputSink inputSink = mDisplayInputSinks.valueAt(i);
            pw.printf("  %d: %s\n", i, inputSink.toString());
        }
    }
}
