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

package com.android.systemui.car.userpicker;

import android.annotation.NonNull;
import android.content.Context;
import android.graphics.Color;
import android.util.Log;
import android.util.Slog;
import android.view.View;

import com.android.systemui.R;

import com.google.android.material.snackbar.Snackbar;

import javax.inject.Inject;

@UserPickerScope
final class SnackbarManager {
    private static final String TAG = SnackbarManager.class.getSimpleName();
    private static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);

    private int mDisplayId;
    private View mAnchorView;
    private int mSnackbarBackgroundTint;

    @Inject
    SnackbarManager() {}

    void setRootView(View anchorView) {
        Context context = anchorView.getContext();
        mAnchorView = anchorView;
        mDisplayId = context.getDisplayId();
        mSnackbarBackgroundTint = context.getColor(R.color.user_picker_snack_bar_background_color);
    }

    void showSnackbar(@NonNull String message) {
        if (DEBUG) {
            Slog.d(TAG, "showSnackBar: displayId=" + mDisplayId);
        }
        Snackbar snackbar = Snackbar.make(mAnchorView, message, Snackbar.LENGTH_SHORT);
        snackbar.setBackgroundTint(mSnackbarBackgroundTint);
        snackbar.setTextColor(Color.WHITE);
        snackbar.show();
    }
}
