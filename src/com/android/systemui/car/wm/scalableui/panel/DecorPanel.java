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

import com.android.car.scalableui.model.Role;
import com.android.car.scalableui.model.Variant;
import com.android.car.scalableui.panel.DecorPanelController;
import com.android.car.scalableui.panel.Panel;
import com.android.systemui.car.wm.scalableui.EventDispatcher;
import com.android.systemui.car.wm.scalableui.view.ViewController;
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
    private final ShellExecutor mMainExecutor;
    private final EventDispatcher mEventDispatcher;
    private final AutoSurfaceTransactionFactory mAutoSurfaceTransactionFactory;
    @VisibleForTesting
    AutoDecor mAutoDecor;

    @Nullable
    private View mDecorView;

    @AssistedInject
    public DecorPanel(@NonNull Context context,
            AutoDecorManager autoDecorManager,
            EventDispatcher eventDispatcher,
            PanelUtils panelUtils,
            @ExternalMainThread ShellExecutor mainExecutor,
            AutoSurfaceTransactionFactory autoSurfaceTransactionFactory,
            @Assisted String id
    ) {
        super(context, id);
        mAutoDecorManager = autoDecorManager;
        mPanelUtils = panelUtils;
        mMainExecutor = mainExecutor;
        mEventDispatcher = eventDispatcher;
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

    @Override
    public void setRole(Role role) {
        if (getRole() == role) return;
        super.setRole(role);
    }

    @VisibleForTesting
    @Nullable
    View inflateDecorView() {
        View view = getRole().getView(getContext());
        return view != null ? view : initFromController();
    }

    private View initFromController() {
        DecorPanelController decorPanelController = ViewController.createDecorPanelController(
                getContext(), getPanelControllerMetadata());
        if (decorPanelController instanceof EventDispatcher.EventProducer eventProducer) {
            eventProducer.setEventDispatcher(mEventDispatcher);
        }
        return decorPanelController == null ? null : decorPanelController.getView();
    }

    @Override
    public void init() {
        super.init();
        if (mPanelUtils.isUserUnlocked()) {
            reset();
        }
    }

    @Override
    public void setVisibility(boolean isVisible) {
        if (mDecorView == null) {
            return;
        }
        mMainExecutor.execute(() -> {
            boolean currentVisibility = isVisible();
            if (currentVisibility != isVisible && mDecorView != null) {
                if (isVisible) {
                    mDecorView.setVisibility(View.VISIBLE);
                } else {
                    mDecorView.setVisibility(View.GONE);
                }
            }
            super.setVisibility(isVisible);
        });
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
            update(autoSurfaceTransaction, /* tx= */ null, /* variant= */ null);
            autoSurfaceTransaction.apply();
        });
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
    public void update(
            @NonNull AutoSurfaceTransaction autoSurfaceTransaction,
            @Nullable SurfaceControl.Transaction tx,
            @Nullable Variant variant) {
        if (getAutoDecor() == null) {
            Log.e(TAG, "AutoDecor is null for " + getPanelId());
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
