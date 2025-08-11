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
import android.graphics.Rect;
import android.util.Log;
import android.view.SurfaceControl;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.android.car.scalableui.manager.StateManager;
import com.android.car.scalableui.model.PanelState;
import com.android.car.scalableui.model.Variant;
import com.android.car.scalableui.panel.DecorPanelController;
import com.android.car.scalableui.panel.Panel;
import com.android.systemui.car.wm.scalableui.panel.controller.PanelControllerInitializer;
import com.android.wm.shell.automotive.AutoDecor;
import com.android.wm.shell.automotive.AutoDecorManager;
import com.android.wm.shell.automotive.AutoSurfaceTransaction;
import com.android.wm.shell.automotive.AutoSurfaceTransactionFactory;
import com.android.wm.shell.common.ShellExecutor;
import com.android.wm.shell.shared.annotations.ExternalMainThread;

import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;
import dagger.assisted.AssistedInject;

/**
 * A {@link AutoDecor} based implementation of a {@link Panel}.
 */
public final class DecorPanel extends BasePanel {
    private static final String TAG = DecorPanel.class.getSimpleName();

    private final AutoDecorManager mAutoDecorManager;
    private final PanelUtils mPanelUtils;
    private final PanelControllerInitializer mPanelControllerInitializer;
    private final ShellExecutor mMainExecutor;
    private final AutoSurfaceTransactionFactory mAutoSurfaceTransactionFactory;
    @VisibleForTesting
    AutoDecor mAutoDecor;

    @Nullable
    private View mDecorView;
    @Nullable
    DecorPanelController mDecorPanelController;

    @AssistedInject
    public DecorPanel(
            @NonNull Context context,
            AutoDecorManager autoDecorManager,
            PanelUtils panelUtils,
            PanelControllerInitializer panelControllerInitializer,
            @ExternalMainThread ShellExecutor mainExecutor,
            AutoSurfaceTransactionFactory autoSurfaceTransactionFactory,
            @Assisted String id
    ) {
        super(context, id);
        mAutoDecorManager = autoDecorManager;
        mPanelUtils = panelUtils;
        mPanelControllerInitializer = panelControllerInitializer;
        mMainExecutor = mainExecutor;
        mAutoSurfaceTransactionFactory = autoSurfaceTransactionFactory;
    }

    @NonNull
    @Override
    public Rect getSafeBounds() {
        // no-op
        return new Rect();
    }

    @Override
    public void setSafeBounds(@NonNull Rect safeBounds) {
        // no-op
    }

    @VisibleForTesting
    @Nullable
    View inflateDecorView() {
        View view = getRole().getView(getContext());
        return view != null ? view : initFromController();
    }

    @Nullable
    private View initFromController() {
        mDecorPanelController = mPanelControllerInitializer.createDecorPanelController(
                getPanelControllerMetadata());
        return mDecorPanelController == null ? null : mDecorPanelController.getView();
    }

    @Override
    public void init() {
        super.init();
        if (mPanelUtils.isUserUnlocked()) {
            reset();
        }
    }

    @Override
    public void reset() {
        super.reset();
        // Only modify the view and window on the main thread to prevent thread-based exceptions
        mMainExecutor.execute(() -> {
            // Remove existing autoDecor that holds the view.
            if (mAutoDecor != null) {
                mAutoDecorManager.removeAutoDecor(mAutoDecor);
            }
            // Reinflate and reattach the view.
            mDecorView = inflateDecorView();
            if (mDecorView == null) {
                Log.e(TAG, "DecorView is null, fail to create AutoDecor, " + getPanelId());
                return;
            }
            mAutoDecor = mAutoDecorManager.createAutoDecor(mDecorView, getLayer(), getBounds(),
                    getPanelId());
            mAutoDecorManager.attachAutoDecorToDisplay(mAutoDecor, getDisplayId());

            AutoSurfaceTransaction autoSurfaceTransaction = mAutoSurfaceTransactionFactory
                    .createTransaction(RESET_TRANSACTION + getPanelId());

            PanelState panelState = StateManager.getPanelState(getPanelId());
            Variant currentVariant = panelState == null ? null : panelState.getCurrentVariant();

            update(autoSurfaceTransaction, currentVariant, /* updateChildren= */ true);
            autoSurfaceTransaction.apply();
        });
    }

    @Override
    public void refreshTheme() {
        if (mDecorPanelController != null) {
            mDecorPanelController.refreshTheme();
        }
        reset();
    }

    @Nullable
    public AutoDecor getAutoDecor() {
        return mAutoDecor;
    }

    @VisibleForTesting
    void setDecorView(View view) {
        mDecorView = view;
    }

    @Override
    public void setAlpha(float alpha) {
        super.setAlpha(alpha);
        if (mDecorView != null) {
            mDecorView.setAlpha(alpha);
        }
    }

    @Override
    public void update(@NonNull SurfaceControl.Transaction tx, @Nullable Variant variant) {
        Log.e(TAG, "Cannot update DecorPanel without AutoSurfaceTransaction");
    }

    @Override
    protected void updateInternal(
            @Nullable AutoSurfaceTransaction autoSurfaceTransaction,
            @Nullable SurfaceControl.Transaction tx,
            @Nullable Variant variant,
            boolean updateChildren) {
        if (getAutoDecor() == null) {
            Log.e(TAG, "AutoDecor is null for " + getPanelId());
            return;
        }
        if (autoSurfaceTransaction == null) {
            Log.e(TAG, "AutoSurfaceTransaction cannot be null for DecorPanel updates");
            return;
        }
        logIfDebuggable("updateDecorPanelSurface:" + this);
        Rect bounds = variant == null ? getBounds() : variant.getBounds();
        autoSurfaceTransaction.setBounds(getAutoDecor(), bounds);
        autoSurfaceTransaction.setVisibility(getAutoDecor(),
                variant == null ? isVisible() : variant.isVisible());
        autoSurfaceTransaction.setZOrder(getAutoDecor(),
                variant == null ? getLayer() : variant.getLayer());
        autoSurfaceTransaction.setCornerRadius(getAutoDecor(),
                variant == null ? getCornerRadius() : variant.getCornerRadius());
        autoSurfaceTransaction.setCrop(getAutoDecor(),
                new Rect(0, 0, bounds.width(), bounds.height()));
        //TODO(b/404959846): replace with autoSurfaceTransaction api if available.
        if (mDecorView != null) {
            mDecorView.setAlpha(variant == null ? getAlpha() : variant.getAlpha());
            mDecorView.post(() -> {
                int vis;
                if (variant == null) {
                    vis = isVisible() ? View.VISIBLE : View.GONE;
                } else {
                    vis = variant.isVisible() ? View.VISIBLE : View.GONE;
                }
                mDecorView.setVisibility(vis);
            });
        }
    }

    @AssistedFactory
    public interface Factory {
        /** Create instance of {@link DecorPanel} with specified id */
        DecorPanel create(String id);
    }

    @Override
    public String toString() {
        return "DecorPanel{"
                + "mId='" + getPanelId() + '\''
                + ", mBounds=" + getBounds()
                + ", mLayer=" + getLayer()
                + ", mRole=" + getRole()
                + ", mIsVisible=" + isVisible()
                + ", mAlpha=" + getAlpha()
                + ", mDisplayId=" + getDisplayId()
                + ", mCornerRadius=" + getCornerRadius()
                + ", mDecorView=" + mDecorView + '}';
    }
}
