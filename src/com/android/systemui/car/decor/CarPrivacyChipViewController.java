/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.systemui.car.decor;

import android.content.Context;
import android.view.InsetsState;
import android.view.View;
import android.view.WindowInsets.Type.InsetsType;
import android.view.WindowInsetsController;

import androidx.annotation.UiThread;

import com.android.internal.statusbar.LetterboxDetails;
import com.android.internal.view.AppearanceRegion;
import com.android.systemui.R;
import com.android.systemui.car.systembar.SystemBarConfigs;
import com.android.systemui.dagger.SysUISingleton;
import com.android.systemui.dagger.qualifiers.Main;
import com.android.systemui.plugins.statusbar.StatusBarStateController;
import com.android.systemui.privacy.PrivacyType;
import com.android.systemui.shade.ShadeExpansionStateManager;
import com.android.systemui.statusbar.CommandQueue;
import com.android.systemui.statusbar.events.PrivacyDotViewController;
import com.android.systemui.statusbar.events.SystemStatusAnimationScheduler;
import com.android.systemui.statusbar.events.ViewState;
import com.android.systemui.statusbar.phone.StatusBarContentInsetsProvider;
import com.android.systemui.statusbar.policy.ConfigurationController;
import com.android.systemui.util.concurrency.DelayableExecutor;

import org.jetbrains.annotations.NotNull;

import java.util.concurrent.Executor;

import javax.inject.Inject;

/**
 * Subclass of {@link PrivacyDotViewController}.
 */
@SysUISingleton
public class CarPrivacyChipViewController extends PrivacyDotViewController
        implements CommandQueue.Callbacks {
    private boolean mAreaVisible;
    private boolean mHasAnimation;
    private final @InsetsType int mBarType;
    private long mDuration;

    @Inject
    public CarPrivacyChipViewController(
            @NotNull @Main Executor mainExecutor,
            @NotNull Context context,
            @NotNull StatusBarStateController stateController,
            @NotNull ConfigurationController configurationController,
            @NotNull StatusBarContentInsetsProvider contentInsetsProvider,
            @NotNull SystemStatusAnimationScheduler animationScheduler,
            ShadeExpansionStateManager shadeExpansionStateManager,
            CommandQueue commandQueue) {
        super(mainExecutor, stateController, configurationController, contentInsetsProvider,
                animationScheduler, shadeExpansionStateManager);
        commandQueue.addCallback(this);
        mHasAnimation = context.getResources().getBoolean(
                R.bool.config_enableImmersivePrivacyChipAnimation);
        mBarType = InsetsState.toPublicType(SystemBarConfigs.BAR_TYPE_MAP[
                context.getResources().getInteger(R.integer.config_privacyIndicatorLocation)]);
        mDuration = Long.valueOf(
                context.getResources().getInteger(R.integer.privacy_indicator_animation_duration));
    }

    @Override
    @UiThread
    public void updateDotView(ViewState state) {
        boolean shouldShow = state.shouldShowDot();
        View designatedCorner = state.getDesignatedCorner();
        if (mAreaVisible && shouldShow && designatedCorner != null) {
            showIndicator(state, mHasAnimation);
        } else {
            if (designatedCorner.getVisibility() == View.VISIBLE) {
                hideIndicator(state, mHasAnimation);
            }
        }
    }

    @UiThread
    private void showIndicator(ViewState viewState, boolean animate) {
        View container = viewState.getDesignatedCorner();
        container.setVisibility(View.VISIBLE);
        container.setAlpha(1f);

        View cameraView = container.findViewById(R.id.immersive_privacy_camera);
        View micView = container.findViewById(R.id.immersive_privacy_microphone);

        String contentDescription = viewState.getContentDescription();
        if (contentDescription.contains(PrivacyType.TYPE_CAMERA.getLogName())) {
            updateViewVisibility(cameraView, View.VISIBLE, animate);
        } else {
            updateViewVisibility(cameraView, View.GONE, animate);
        }

        if (contentDescription.contains(PrivacyType.TYPE_MICROPHONE.getLogName())) {
            updateViewVisibility(micView, View.VISIBLE, animate);
        } else {
            updateViewVisibility(micView, View.GONE, animate);
        }

        if (getShowingListener() != null) {
            getShowingListener().onPrivacyDotShown(container);
        }
    }

    @UiThread
    private void hideIndicator(ViewState viewState, boolean animate) {
        View container = viewState.getDesignatedCorner();

        View cameraView = container.findViewById(R.id.immersive_privacy_camera);
        View micView = container.findViewById(R.id.immersive_privacy_microphone);
        updateViewVisibility(cameraView, View.GONE, animate);
        updateViewVisibility(micView, View.GONE, animate);

        container.setVisibility(View.INVISIBLE);
        if (getShowingListener() != null) {
            getShowingListener().onPrivacyDotHidden(container);
        }
    }

    @UiThread
    private void updateViewVisibility(View view, int visibility, boolean animate) {
        // TODO(b/248145978): Add animation for transition.
        view.setVisibility(visibility);
        view.setAlpha(visibility == View.VISIBLE ? 1f : 0f);
    }

    @Override
    public void onSystemBarAttributesChanged(
            int displayId,
            @WindowInsetsController.Appearance int appearance,
            AppearanceRegion[] appearanceRegions,
            boolean navbarColorManagedByIme,
            @WindowInsetsController.Behavior int behavior,
            @InsetsType int requestedVisibleTypes,
            String packageName,
            LetterboxDetails[] letterboxDetails) {
        boolean newAreaVisibility = (mBarType & requestedVisibleTypes) == 0;
        if (newAreaVisibility != mAreaVisible) {
            mAreaVisible = newAreaVisibility;
            DelayableExecutor executor = getUiExecutor();
            // Null check to avoid crashing caused by debug.disable_screen_decorations=true
            if (executor != null) {
                executor.execute(() -> updateDotView(getCurrentViewState()));
            }
        }
    }

    @Override
    @UiThread
    public void updateRotations(int rotation, int paddingTop) {
        // Do nothing.
    }

    @Override
    @UiThread
    public void setCornerSizes(ViewState state) {
        // Do nothing.
    }
}
