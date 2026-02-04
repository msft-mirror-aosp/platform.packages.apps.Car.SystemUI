/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.systemui.car.systembar.notificationchip;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.car.oem.tokens.Token;
import com.android.systemui.car.systembar.privacy.base.PrivacyChip;

/**
 * A {@link PrivacyChip} that is used to display promoted notification information.
 */
public class PromotedNotificationChipView extends PrivacyChip {

    private Drawable mDefaultLightIcon;
    private Drawable mDefaultDarkIcon;

    private Drawable mCurrentLightIcon;
    private Drawable mCurrentDarkIcon;

    public PromotedNotificationChipView(@NonNull Context context) {
        super(context);
        init(context);
    }

    public PromotedNotificationChipView(@NonNull Context context,
            @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public PromotedNotificationChipView(@NonNull Context context, @Nullable AttributeSet attrs,
            int defStyleAttrs) {
        super(context, attrs, defStyleAttrs);
        init(context);
    }

    private void init(Context context) {
        mDefaultLightIcon = context.getDrawable(R.drawable.ic_promoted_notif_default_light);
        mDefaultDarkIcon = context.getDrawable(R.drawable.ic_promoted_notif_default_dark);
    }

    @Override
    protected int getNoSensorUsageDelay() {
        // The chip should be gone right after the notification becomes inactive.
        return 0;
    }

    @Override
    protected Drawable getLightIconDrawable() {
        if (mCurrentLightIcon != null) {
            return mCurrentLightIcon;
        }
        return mDefaultLightIcon;
    }

    @Override
    protected Drawable getDarkIconDrawable() {
        if (mCurrentDarkIcon != null) {
            return mCurrentDarkIcon;
        }
        return mDefaultDarkIcon;
    }

    @Override
    protected Drawable getLightMutedIconDrawable() {
        return getLightIconDrawable();
    }

    @Override
    protected Drawable getDarkMutedIconDrawable() {
        return getDarkIconDrawable();
    }

    @Override
    protected String getSensorName() {
        return "Promoted Notifications"; // TODO: replace
    }

    @Override
    protected String getSensorNameWithFirstLetterCapitalized() {
        return getSensorName(); // TODO: replace
    }

    @Override
    public Class<?> getElementControllerClass() {
        Class<?> superClass = super.getElementControllerClass();
        if (superClass != null) {
            return superClass;
        }
        return PromotedNotificationChipViewController.class;
    }

    /**
     * Update the notification icon with a new drawable. If the provided drawable is null, the
     * default icon will be used.
     */
    public void updateNotificationIcon(@Nullable Drawable drawable) {
        if (drawable == null || drawable.getConstantState() == null) {
            mCurrentLightIcon = null;
            mCurrentDarkIcon = null;
            updateIcons();
            return;
        }
        Context oemContext = Token.createOemStyledContext(getContext());
        mCurrentLightIcon = drawable.getConstantState().newDrawable(
                getContext().getResources()).mutate();
        mCurrentLightIcon.setTint(oemContext.getColor(
                com.android.systemui.car.shared.R.color.privacy_chip_light_icon_color));
        mCurrentDarkIcon = drawable.getConstantState().newDrawable(
                getContext().getResources()).mutate();
        mCurrentDarkIcon.setTint(oemContext.getColor(
                com.android.systemui.car.shared.R.color.privacy_chip_dark_icon_color));
        updateIcons();
    }
}
