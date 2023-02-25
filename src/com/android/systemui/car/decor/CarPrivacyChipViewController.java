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
import android.util.Log;
import android.view.InsetsState;
import android.view.View;
import android.view.WindowInsets.Type.InsetsType;
import android.view.WindowInsetsController;

import androidx.annotation.UiThread;
import androidx.constraintlayout.motion.widget.MotionLayout;

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
    private static final String TAG = CarPrivacyChipViewController.class.getSimpleName();
    private boolean mAreaVisible;
    private boolean mHasAnimation;
    private final @InsetsType int mBarType;
    private long mDuration;
    private long mDotTransitionDelay;
    private DelayableExecutor mExecutor;

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
        mDotTransitionDelay = Long.valueOf(
                context.getResources().getInteger(R.integer.privacy_chip_pill_to_circle_delay));
    }

    @Override
    @UiThread
    public void updateDotView(ViewState state) {
        boolean shouldShow = state.shouldShowDot();
        View designatedCorner = state.getDesignatedCorner();
        if (mAreaVisible && shouldShow && designatedCorner != null) {
            showIndicator(state, mHasAnimation && mAreaVisible);
        } else {
            if (designatedCorner.getVisibility() == View.VISIBLE) {
                hideIndicator(state, mHasAnimation && mAreaVisible);
            }
        }
    }

    @UiThread
    private void showIndicator(ViewState viewState, boolean animate) {
        Log.d(TAG, "Show the immersive indicator");
        View container = viewState.getDesignatedCorner();
        container.setVisibility(View.VISIBLE);

        MotionLayout cameraView = container.findViewById(R.id.immersive_cam_indicator_container);
        MotionLayout micView = container.findViewById(R.id.immersive_mic_indicator_container);

        String contentDescription = viewState.getContentDescription();
        if (contentDescription.contains(PrivacyType.TYPE_CAMERA.getLogName())) {
            showIcon(cameraView, R.id.immersive_camera_transition_show,
                    R.id.immersive_camera_transition_collapse, animate);
        } else {
            hideIcon(cameraView, R.id.immersive_camera_transition_hide, animate);
        }

        if (contentDescription.contains(PrivacyType.TYPE_MICROPHONE.getLogName())) {
            showIcon(micView, R.id.immersive_mic_transition_show,
                    R.id.immersive_mic_transition_collapse, animate);
        } else {
            hideIcon(micView, R.id.immersive_mic_transition_hide, animate);
        }

        if (getShowingListener() != null) {
            getShowingListener().onPrivacyDotShown(container);
        }
    }

    @UiThread
    private void hideIndicator(ViewState viewState, boolean animate) {
        Log.d(TAG, "Hide the immersive indicators");
        View container = viewState.getDesignatedCorner();

        MotionLayout cameraView = container.findViewById(R.id.immersive_cam_indicator_container);
        MotionLayout micView = container.findViewById(R.id.immersive_mic_indicator_container);
        hideIcon(cameraView, R.id.immersive_camera_transition_hide, animate);
        hideIcon(micView, R.id.immersive_mic_transition_hide, animate);

        if (getShowingListener() != null) {
            getShowingListener().onPrivacyDotHidden(container);
        }
    }

    @UiThread
    private void showIcon(MotionLayout view, int showTransitionId,
            int collapseTransistionId, boolean animated) {
        if (animated) {
            view.setTransition(showTransitionId);
            view.transitionToEnd();
            view.addTransitionListener(new MotionLayout.TransitionListener() {
                @Override
                public void onTransitionStarted(MotionLayout motionLayout, int i, int i1) {
                    // Do nothing.
                }

                @Override
                public void onTransitionChange(MotionLayout motionLayout, int i, int i1, float v) {
                    // Do nothing.
                }

                @Override
                public void onTransitionCompleted(MotionLayout motionLayout, int i) {
                    mExecutor.executeDelayed(new Runnable() {
                        @Override
                        public void run() {
                            view.setTransition(collapseTransistionId);
                            view.transitionToEnd();
                        }
                    }, mDotTransitionDelay);
                    view.removeTransitionListener(this);
                }

                @Override
                public void onTransitionTrigger(MotionLayout motionLayout, int i, boolean b,
                        float v) {
                    // Do nothing.
                }
            });
        }
        view.setVisibility(View.VISIBLE);
    }

    @UiThread
    private void hideIcon(MotionLayout view, int transitionId, boolean animated) {
        if (view.getVisibility() == View.VISIBLE) {
            if (animated) {
                view.setTransition(transitionId);
                view.transitionToEnd();
            } else {
                view.setVisibility(View.GONE);
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
            @InsetsType int requestedVisibleTypes,
            String packageName,
            LetterboxDetails[] letterboxDetails) {
        boolean newAreaVisibility = (mBarType & requestedVisibleTypes) == 0;
        if (newAreaVisibility != mAreaVisible) {
            mAreaVisible = newAreaVisibility;
            mExecutor = getUiExecutor();
            // Null check to avoid crashing caused by debug.disable_screen_decorations=true
            if (mExecutor != null) {
                mExecutor.execute(() -> updateDotView(getCurrentViewState()));
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
