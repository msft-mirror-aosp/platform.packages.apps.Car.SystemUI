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
package com.android.systemui.car.wm.scalableui.view;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;

import com.android.car.scalableui.model.PanelControllerMetadata;
import com.android.car.scalableui.panel.Panel;
import com.android.car.scalableui.panel.PanelPool;
import com.android.internal.graphics.drawable.BackgroundBlurDrawable;
import com.android.systemui.R;
import com.android.systemui.car.wm.scalableui.panel.TaskPanel;

/**
 * A Controller for the {@link GripBar}
 * <p>
 * Configuration for the GripBar is read from a themed attribute, which is
 * expected to be an array resource. The array should contain values defining
 * the behavior of the GripBar. The following indices in the configuration
 * array are used:
 * </p>
 * <ul>
 * <li>Index 0: View provider name (String)</li>
 * <li>Index 1: View class name (String)</li>
 * <li>Index 2: Drag event ID (String)</li>
 * <li>Index 3: Orientation (0 for vertical, 1 for horizontal) (Integer)</li>
 * <li>Index 4: Snap threshold (Dimension)</li>
 * <li>Index 5: Resource ID of the breakpoint definition array (Integer)</li>
 * </ul>
 */
public class PanelOverlayController extends DecorPanelControllerBase {
    private static final String TAG = PanelOverlayController.class.getSimpleName();
    private PanelOverlay mPanelOverlay;
    private String mOverlayPanelId;

    public PanelOverlayController(Context context, PanelControllerMetadata metadata) {
        super(context, metadata);
        init(metadata);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    @NonNull
    public View getView() {
        View view = super.getView();
        if (view instanceof PanelOverlay panelOverlay) {
            mPanelOverlay = panelOverlay;
        } else {
            throw new RuntimeException("PanelOverlayController mush have a PanelOverlay view");
        }
        mPanelOverlay.setOnVisibilityChangeListener((visibility) -> {
            if (visibility == View.GONE) {
                return;
            }
            setBlur();
            PanelPool pool = PanelPool.getInstance();
            Panel panel = pool.getPanel(mOverlayPanelId);
            if (panel instanceof TaskPanel) {
                String packageName = ((TaskPanel) panel).getTopTaskPackageName();
                setVail(packageName);
            }
        });
        return mPanelOverlay;
    }

    private void init(PanelControllerMetadata metadata) {
        mOverlayPanelId = metadata.getStringConfiguration(PanelControllerMetadata.OVERLAY_PANEL_ID);
    }

    private void setVail(String packageName) {
        if (packageName == null) {
            Log.i(TAG, "can't set vail as package name is null.");
            return;
        }
        Drawable icon;
        try {
            icon = mContext.getPackageManager().getApplicationIcon(
                    packageName);
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "vail can't be set for package name ", e);
            icon = mContext.getDrawable(R.drawable.car_ic_apps);
        }
        if (icon != null) {
            ImageView iconImageView = new ImageView(getContext());
            iconImageView.setImageDrawable(icon);
            int width = getContext().getResources().getDimensionPixelSize(
                    R.dimen.overlay_panel_view_vail_width);
            int height = getContext().getResources().getDimensionPixelSize(
                    R.dimen.overlay_panel_view_vail_height);
            addCenteredIconWithConstraintSet(iconImageView, width, height);
        }
    }

    private void setBlur() {
        if (mPanelOverlay.getBackground() != null) {
            return;
        }
        BackgroundBlurDrawable drawable =
                mPanelOverlay.getViewRootImpl().createBackgroundBlurDrawable();
        drawable.setColor(
                mContext.getResources().getColor(R.color.overlay_panel_bg_color));
        drawable.setCornerRadius(
                mContext.getResources().getInteger(R.integer.overlay_panel_blur_corner_radius));
        drawable.setBlurRadius(
                mContext.getResources().getInteger(R.integer.overlay_panel_blur_radius));
        mPanelOverlay.setBackground(drawable);
    }

    private void addCenteredIconWithConstraintSet(ImageView iconImageView, int width, int height) {
        if (iconImageView.getId() == View.NO_ID) {
            iconImageView.setId(View.generateViewId());
        }

        ConstraintLayout.LayoutParams initialParams = new ConstraintLayout.LayoutParams(width,
                height);
        iconImageView.setLayoutParams(initialParams);

        mPanelOverlay.post(() -> {
            mPanelOverlay.addView(iconImageView);

            ConstraintSet constraintSet = new ConstraintSet();
            constraintSet.clone(mPanelOverlay);

            // Center Horizontally
            constraintSet.connect(iconImageView.getId(), ConstraintSet.START,
                    ConstraintSet.PARENT_ID, ConstraintSet.START);
            constraintSet.connect(iconImageView.getId(), ConstraintSet.END, ConstraintSet.PARENT_ID,
                    ConstraintSet.END);

            // Center Vertically
            constraintSet.connect(iconImageView.getId(), ConstraintSet.TOP, ConstraintSet.PARENT_ID,
                    ConstraintSet.TOP);
            constraintSet.connect(iconImageView.getId(), ConstraintSet.BOTTOM,
                    ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM);

            // Apply the constraints
            constraintSet.applyTo(mPanelOverlay);
        });
    }
}
