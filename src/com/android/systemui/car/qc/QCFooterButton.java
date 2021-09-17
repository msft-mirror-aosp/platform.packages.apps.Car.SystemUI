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

package com.android.systemui.car.qc;

import android.app.ActivityOptions;
import android.content.Context;
import android.content.Intent;
import android.content.res.TypedArray;
import android.os.UserHandle;
import android.util.AttributeSet;
import android.util.Log;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.systemui.R;

import java.net.URISyntaxException;

/**
 * Footer button for quick control panels.
 *
 * Allows for an intent action to be specified via the {@link R.styleable.QCFooterButton_intent}
 * attribute.
 */
public class QCFooterButton extends Button {
    private static final String TAG = "QCFooterButton";

    public QCFooterButton(Context context) {
        super(context);
        init(context, null);
    }

    public QCFooterButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public QCFooterButton(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    public QCFooterButton(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init(context, attrs);
    }

    private void init(@NonNull Context context, @Nullable AttributeSet attrs) {
        if (attrs == null) {
            return;
        }
        TypedArray typedArray = context.obtainStyledAttributes(attrs,
                R.styleable.QCFooterButton);
        String intentString = typedArray.getString(R.styleable.QCFooterButton_intent);
        if (intentString != null) {
            Intent intent = null;
            try {
                intent = Intent.parseUri(intentString, Intent.URI_INTENT_SCHEME);
            } catch (URISyntaxException e) {
                throw new RuntimeException("Failed to attach intent", e);
            }
            Intent finalIntent = intent;
            setOnClickListener(v -> {
                mContext.sendBroadcastAsUser(new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS),
                        UserHandle.CURRENT);
                try {
                    ActivityOptions options = ActivityOptions.makeBasic();
                    options.setLaunchDisplayId(mContext.getDisplayId());
                    mContext.startActivityAsUser(finalIntent, options.toBundle(),
                            UserHandle.CURRENT);
                } catch (Exception e) {
                    Log.e(TAG, "Failed to launch intent", e);
                }
            });
        }
    }
}
