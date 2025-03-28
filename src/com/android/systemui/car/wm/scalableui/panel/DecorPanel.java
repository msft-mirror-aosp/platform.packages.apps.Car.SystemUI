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
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.car.scalableui.panel.Panel;
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

    private static final String ROLE_TYPE_LAYOUT = "layout";
    private static final String TAG = DecorPanel.class.getSimpleName();

    private final AutoDecorManager mAutoDecorManager;
    private final PanelUtils mPanelUtils;
    private final ShellExecutor mMainExecutor;
    private AutoDecor mAutoDecor;

    private View mDecorView;

    @AssistedInject
    public DecorPanel(@NonNull Context context,
            AutoDecorManager autoDecorManager,
            PanelUtils panelUtils,
            @ExternalMainThread ShellExecutor mainExecutor,
            @Assisted String id) {
        super(context, id);
        mAutoDecorManager = autoDecorManager;
        mPanelUtils = panelUtils;
        mMainExecutor = mainExecutor;
    }

    @Override
    public void setRole(int role) {
        if (getRole() == role) return;
        super.setRole(role);
    }

    @Nullable
    private View inflateDecorView() {
        int role = getRole();
        String roleTypeName = getContext().getResources().getResourceTypeName(getRole());
        LayoutInflater inflater = LayoutInflater.from(getContext());

        switch (roleTypeName) {
            case ROLE_TYPE_LAYOUT:
                return inflater.inflate(role, null);
            default:
                Log.e(TAG, "Unsupported view type" + roleTypeName);
        }
        return null;
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
            if (mDecorView == null) return;
            mAutoDecor = mAutoDecorManager.createAutoDecor(mDecorView, getLayer(), getBounds(),
                    getPanelId());
            mAutoDecorManager.attachAutoDecorToDisplay(mAutoDecor, getDisplayId());
        });
    }

    @Nullable
    public AutoDecor getAutoDecor() {
        return mAutoDecor;
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
                + ", mLayer=" + getLayer()
                + ", mRole=" + getRole()
                + ", mBounds=" + getBounds()
                + ", mIsVisible=" + isVisible()
                + ", mAlpha=" + getAlpha()
                + ", mDisplayId=" + getDisplayId()
                + ", mCornerRadius=" + getCornerRadius()
                + ", mDecorView=" + mDecorView + '}';
    }
}
