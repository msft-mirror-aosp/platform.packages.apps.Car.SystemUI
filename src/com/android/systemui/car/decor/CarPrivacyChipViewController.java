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
import android.view.InsetsVisibilities;
import android.view.View;
import android.view.WindowInsetsController;

import androidx.annotation.UiThread;

import com.android.internal.statusbar.LetterboxDetails;
import com.android.internal.view.AppearanceRegion;
import com.android.systemui.R;
import com.android.systemui.car.systembar.SystemBarConfigs;
import com.android.systemui.dagger.SysUISingleton;
import com.android.systemui.dagger.qualifiers.Main;
import com.android.systemui.plugins.statusbar.StatusBarStateController;
import com.android.systemui.shade.ShadeExpansionStateManager;
import com.android.systemui.statusbar.CommandQueue;
import com.android.systemui.statusbar.events.PrivacyDotViewController;
import com.android.systemui.statusbar.events.SystemStatusAnimationScheduler;
import com.android.systemui.statusbar.events.ViewState;
import com.android.systemui.statusbar.phone.StatusBarContentInsetsProvider;
import com.android.systemui.statusbar.policy.ConfigurationController;

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
    private int mBarType;

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
        mBarType = SystemBarConfigs.BAR_TYPE_MAP[
                context.getResources().getInteger(R.integer.config_privacyIndicatorLocation)];
    }

    @Override
    @UiThread
    public void updateDotView(ViewState state) {
        // TODO(b/248145978): Add animation for transition.
        boolean shouldShow = state.shouldShowDot();
        View designatedCorner = state.getDesignatedCorner();
        if (mAreaVisible && shouldShow && designatedCorner != null) {
            showDotView(designatedCorner, mHasAnimation);
        } else {
            if (designatedCorner.getVisibility() == View.VISIBLE) {
                hideDotView(designatedCorner, mHasAnimation);
            }
        }
    }

    @Override
    public void onSystemBarAttributesChanged(
            int displayId,
            @WindowInsetsController.Appearance int appearance,
            AppearanceRegion[] appearanceRegions,
            boolean navbarColorManagedByIme,
            @WindowInsetsController.Behavior int behavior,
            InsetsVisibilities requestedVisibilities,
            String packageName,
            LetterboxDetails[] letterboxDetails) {
        boolean newAreaVisibility = requestedVisibilities != null
                ? !requestedVisibilities.getVisibility(mBarType)
                : false;
        if (newAreaVisibility != mAreaVisible) {
            mAreaVisible = newAreaVisibility;
            getUiExecutor().execute(() -> updateDotView(getCurrentViewState()));
        }
    }
}
