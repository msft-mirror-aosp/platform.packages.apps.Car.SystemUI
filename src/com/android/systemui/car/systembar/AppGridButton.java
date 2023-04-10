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

package com.android.systemui.car.systembar;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.TypedArray;
import android.hardware.input.InputManager;
import android.util.AttributeSet;
import android.view.KeyEvent;

import com.android.systemui.R;
import com.android.systemui.shared.system.TaskStackChangeListener;
import com.android.systemui.shared.system.TaskStackChangeListeners;
import com.android.systemui.statusbar.AlphaOptimizedImageView;

/**
 * AppGridButton is used to display the app grid and toggle recents.
 * Long click functionality is updated to send KeyEvent instead of regular intents.
 * TaskStackChangeListener helps to toggle the local long clicked state which further helps
 * determine the appropriate icon and alpha to show.
 */
public class AppGridButton extends CarSystemBarButton {
    private final InputManager mInputManager;
    private final ComponentName mRecentsComponentName;
    private boolean mIsRecentsActive;

    public AppGridButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        mInputManager = context.getSystemService(InputManager.class);
        mRecentsComponentName = ComponentName.unflattenFromString(context.getString(
                com.android.internal.R.string.config_recentsComponentName));
        TaskStackChangeListeners.getInstance().registerTaskStackListener(
                new TaskStackChangeListener() {
                    @Override
                    public void onTaskMovedToFront(ActivityManager.RunningTaskInfo taskInfo) {
                        if (mRecentsComponentName == null) {
                            return;
                        }
                        if (mRecentsComponentName.getClassName().equals(
                                taskInfo.topActivity.getClassName())) {
                            mIsRecentsActive = true;
                            return;
                        }
                        if (mIsRecentsActive) {
                            mIsRecentsActive = false;
                        }
                    }
                });
    }

    @Override
    protected void setUpIntents(TypedArray typedArray) {
        super.setUpIntents(typedArray);
        setOnLongClickListener(v -> {
            if (mIsRecentsActive) {
                return false;
            }
            toggleRecents();
            return true;
        });

    }

    @Override
    protected OnClickListener getButtonClickListener(Intent toSend) {
        OnClickListener onClickListener = super.getButtonClickListener(toSend);
        return v -> {
            if (mIsRecentsActive) {
                toggleRecents();
                return;
            }
            onClickListener.onClick(v);
        };
    }

    @Override
    protected void updateImage(AlphaOptimizedImageView icon) {
        if (mIsRecentsActive) {
            icon.setImageResource(R.drawable.car_ic_recents);
            return;
        }
        super.updateImage(icon);
    }

    @Override
    protected void refreshIconAlpha(AlphaOptimizedImageView icon) {
        if (mIsRecentsActive) {
            icon.setAlpha(getSelectedAlpha());
            return;
        }
        super.refreshIconAlpha(icon);
    }

    private void toggleRecents() {
        mInputManager.injectInputEvent(
                new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_APP_SWITCH),
                InputManager.INJECT_INPUT_EVENT_MODE_ASYNC);
    }
}
