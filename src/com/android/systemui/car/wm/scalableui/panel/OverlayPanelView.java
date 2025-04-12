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
package com.android.systemui.car.wm.scalableui.panel;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;

import com.android.car.scalableui.model.Blur;
import com.android.internal.graphics.drawable.BackgroundBlurDrawable;
import com.android.systemui.R;

/**
 * This view is used within the overlay panel in scalable UI. Main functionality it provides is
 * dimming, blur / frost effects.
 */
public final class OverlayPanelView extends LinearLayout {

    private int mBlurColor;
    private float mCornerRadius;
    private int mBlurIntensity;

    public OverlayPanelView(Context context, int blurColor, float cornerRadius, int blurIntensity) {
        super(context);
        mBlurColor = blurColor;
        mCornerRadius = cornerRadius;
        mBlurIntensity = blurIntensity;
    }

    public OverlayPanelView(Context context, Blur blur) {
        super(context);
        mBlurColor = blur.getBackgroundColor();
        mCornerRadius = blur.getCornerRadius();
        mBlurIntensity = blur.getBlurRadius();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        refresh();
    }

    /**
     * Sets the vail for the topmost task onto the view center.
     */
    public void setVail(Drawable appIcon) {
        setGravity(Gravity.CENTER);
        if (appIcon != null) {
            ImageView iconImageView = new ImageView(getContext());
            iconImageView.setImageDrawable(appIcon);
            int width = getContext().getResources().getDimensionPixelSize(
                    R.dimen.overlay_panel_view_vail_width);
            int height = getContext().getResources().getDimensionPixelSize(
                    R.dimen.overlay_panel_view_vail_height);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(width, height);
            addView(iconImageView, params);
        }
    }

    /**
     * Refreshes the view with new properties.
     */
    public void refresh() {
        post(() -> {
            BackgroundBlurDrawable drawable = getViewRootImpl().createBackgroundBlurDrawable();
            drawable.setColor(mBlurColor);
            drawable.setCornerRadius(mCornerRadius);
            drawable.setBlurRadius(mBlurIntensity);
            setBackground(drawable);
        });
    }

    /**
     * Gets the background color
     */
    public int getBlurColor() {
        return mBlurColor;
    }

    /**
     * Gets the corner radius
     */
    public float getCornerRadius() {
        return mCornerRadius;
    }

    /**
     * Gets the blur radius / intensity
     */
    public int getBlurIntensity() {
        return mBlurIntensity;
    }

    /**
     * Sets the background color
     */
    public void setBlurColor(int blurColor) {
        mBlurColor = blurColor;
    }

    /**
     * sets the corner radius
     */
    public void setCornerRadius(float cornerRadius) {
        mCornerRadius = cornerRadius;
    }

    /**
     * Sets the blur radius / intensity
     */
    public void setBlurIntensity(int blurIntensity) {
        mBlurIntensity = blurIntensity;
    }
}
