/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.systemui.car.privacy;

import android.content.Context;
import android.util.AttributeSet;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/** Car optimized OngoingActivity Privacy Chip View. */
public abstract class OngoingActivityPrivacyChip extends PrivacyChip {

    public OngoingActivityPrivacyChip(@NonNull Context context) {
        this(context, /* attrs= */ null);
    }

    public OngoingActivityPrivacyChip(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, /* defStyleAttrs= */ 0);
    }

    public OngoingActivityPrivacyChip(@NonNull Context context,
            @Nullable AttributeSet attrs, int defStyleAttrs) {
        super(context, attrs, defStyleAttrs);
    }

    @Override
    protected int getNoSensorUsageDelay() {
        // The chip should be gone right after the ongoing activity becomes inactive.
        return 0;
    }

    @Override
    protected @DrawableRes int getLightMutedIconResourceId() {
        return getLightIconResourceId();
    }

    @Override
    protected @DrawableRes int getDarkMutedIconResourceId() {
        return getDarkIconResourceId();
    }

    @Override
    protected String getSensorName() {
        return getActivityName();
    }

    @Override
    protected String getSensorNameWithFirstLetterCapitalized() {
        return getActivityNameWithFirstLetterCapitalized();
    }

    protected abstract String getActivityName();

    protected abstract String getActivityNameWithFirstLetterCapitalized();
}
