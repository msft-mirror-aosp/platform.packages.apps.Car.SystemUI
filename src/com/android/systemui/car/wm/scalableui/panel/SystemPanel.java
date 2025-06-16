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
import android.view.SurfaceControl;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.car.scalableui.model.Variant;
import com.android.car.scalableui.panel.Panel;
import com.android.car.scalableui.panel.PanelUpdatePublisher;
import com.android.wm.shell.automotive.AutoSurfaceTransaction;

import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;
import dagger.assisted.AssistedInject;

import java.util.Optional;

/**
 * A {@link Panel} whose view lives in SystemUI.
 */
public final class SystemPanel extends BasePanel {
    @AssistedInject
    public SystemPanel(@NonNull Context context, @Assisted String panelId,
            Optional<PanelUpdatePublisher> panelUpdatePublisherOptional) {
        super(context, panelId, panelUpdatePublisherOptional);
    }

    @Override
    public void refreshTheme() {
        // no-op
    }

    @Override
    public void update(@NonNull AutoSurfaceTransaction autoSurfaceTransaction,
            @Nullable SurfaceControl.Transaction tx, @Nullable Variant variant,
            boolean updateChildren) {
        if (getPanelUpdateObserver() == null) {
            return;
        }
        getPanelUpdateObserver().postVisibility(getPanelId(),
                variant == null ? isVisible() : variant.isVisible());
        getPanelUpdateObserver().postAlpha(getPanelId(),
                variant == null ? getAlpha() : variant.getAlpha());
        getPanelUpdateObserver().postCornerRadius(getPanelId(),
                variant == null ? getCornerRadius() : variant.getCornerRadius());
        getPanelUpdateObserver().postBounds(getPanelId(),
                variant == null ? getBounds() : variant.getBounds());
        getPanelUpdateObserver().postInsets(getPanelId(),
                variant == null ? getInsets() : variant.getInsets());
    }

    @Override
    public String toString() {
        return "SystemPanel{"
                + "mId='" + getPanelId() + '\''
                + ", mBounds=" + getBounds()
                + ", mIsVisible=" + isVisible()
                + ", mAlpha=" + getAlpha()
                + ", mInsets=" + getInsets()
                + ", mMetaData=" + getPanelControllerMetadata()
                + ", mCornerRadius=" + getCornerRadius() + '}';
    }

    @AssistedFactory
    public interface Factory {
        /** Create instance of {@link SystemPanel} with specified id */
        SystemPanel create(String id);
    }
}
