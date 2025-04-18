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
import android.view.LayoutInflater;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.android.car.scalableui.panel.DecorPanelController;
import com.android.car.scalableui.panel.Panel;
import com.android.systemui.car.wm.scalableui.EventDispatcher;
import com.android.systemui.car.wm.scalableui.view.ViewController;
import com.android.wm.shell.automotive.AutoDecor;
import com.android.wm.shell.automotive.AutoDecorManager;
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
    @VisibleForTesting
    AutoDecor mAutoDecor;

    private View mDecorView;

    @AssistedInject
    public DecorPanel(@NonNull Context context,
            AutoDecorManager autoDecorManager,
            EventDispatcher eventDispatcher,
            PanelUtils panelUtils,
            @ExternalMainThread ShellExecutor mainExecutor,
            @Assisted String id
    ) {
        super(context, id);
        mAutoDecorManager = autoDecorManager;
        mPanelUtils = panelUtils;
        mMainExecutor = mainExecutor;
        mEventDispatcher = eventDispatcher;
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
    public void setRole(int role) {
        if (getRole() == role) return;
        super.setRole(role);
    }

    @VisibleForTesting
    @Nullable
    View inflateDecorView() {
        int role = getRole();
        String roleTypeName = getContext().getResources().getResourceTypeName(getRole());
        LayoutInflater inflater = LayoutInflater.from(getContext());

        View view = null;

        switch (roleTypeName) {
            case ROLE_TYPE_LAYOUT:
                view = inflater.inflate(role, null);
                break;
            case ROLE_TYPE_STRING:
                view = initFromController();
                break;
            default:
                Log.e(TAG, "Unsupported view type" + roleTypeName);
        }
        return view;
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
        if (mPanelUtils.isUserUnlocked()) {
            reset();
        }
    }

    @Override
    public void reset() {
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
