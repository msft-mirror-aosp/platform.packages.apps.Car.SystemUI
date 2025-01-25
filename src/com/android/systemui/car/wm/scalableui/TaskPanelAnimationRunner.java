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
package com.android.systemui.car.wm.scalableui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ValueAnimator;
import android.os.Build;
import android.util.Log;
import android.view.SurfaceControl;

import androidx.annotation.Nullable;

import com.android.car.internal.dep.Trace;
import com.android.systemui.car.wm.scalableui.panel.TaskPanel;
import com.android.systemui.car.wm.scalableui.panel.TaskPanelPool;
import com.android.wm.shell.dagger.WMSingleton;
import com.android.wm.shell.transition.Transitions;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

/**
 * Manages the state of UI panels. This class is responsible for loading panel definitions,
 * handling events that trigger state transitions, and applying visual updates to panels
 * based on their current state.
 */
@WMSingleton
public class TaskPanelAnimationRunner {
    private static final String TAG = TaskPanelAnimationRunner.class.getSimpleName();
    private static final boolean DEBUG = Build.IS_DEBUGGABLE;

    private AnimatorSet mAnimatorSet = null;

    @Inject
    public TaskPanelAnimationRunner() {}

    /**
     * Plays the animation in the pending list
     */
    public void playPendingAnimations(
            Map<String, Animator> pendingAnimators,
            @Nullable Transitions.TransitionFinishCallback finishCallback) {
        if (DEBUG) {
            Log.d(TAG, "playPendingAnimations: " + pendingAnimators.size());
        }
        Trace.beginSection(TAG + "#playPendingAnimations");
        stopRunningAnimations();

        mAnimatorSet = new AnimatorSet();

        List<Animator> animationToRun = new ArrayList<>();
        for (Map.Entry<String, Animator> entry : pendingAnimators.entrySet()) {
            Animator animator = entry.getValue();
            String id = entry.getKey();
            TaskPanel taskPanel = TaskPanelPool.getTaskPanel(tp -> tp.getId().equals(id));
            ValueAnimator surfaceAnimator = createSurfaceAnimator(animator.getTotalDuration(),
                    taskPanel);
            animationToRun.add(animator);
            animationToRun.add(surfaceAnimator);
        }
        mAnimatorSet.playTogether(animationToRun);
        mAnimatorSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                Trace.beginSection(TAG + "#onAnimationStart");
                super.onAnimationStart(animation);
                Trace.endSection();
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                Trace.beginSection(TAG + "#onAnimationEnd");
                super.onAnimationEnd(animation);
                if (DEBUG) {
                    Log.d(TAG, "Animation set finished " + finishCallback);
                }
                if (finishCallback != null) {
                    finishCallback.onTransitionFinished(/* wct= */ null);
                }
                Trace.endSection();
            }
        });
        mAnimatorSet.start();
        Trace.endSection();
    }

    /**
     * Ends any running animations associated with this instance.
     */
    void stopRunningAnimations() {
        if (mAnimatorSet != null && mAnimatorSet.isRunning()) {
            if (DEBUG) {
                Log.d(TAG, "stopRunningAnimations: has running animatorSet "
                        + mAnimatorSet.getCurrentPlayTime());
            }
            mAnimatorSet.end();
        }
    }

    private ValueAnimator createSurfaceAnimator(long duration, TaskPanel taskPanel) {
        Trace.beginSection(TAG + "#createSurfaceAnimator");
        ValueAnimator surfaceAnimator = ValueAnimator.ofFloat(0, 1f);
        surfaceAnimator.setDuration(duration);
        surfaceAnimator.addUpdateListener(animation -> {
            updatePanelSurface(taskPanel);
        });
        Trace.endSection();
        return surfaceAnimator;
    }

    private void updatePanelSurface(TaskPanel taskPanel) {
        Trace.beginSection(TAG + "#updatePanelSurface");

        SurfaceControl.Transaction tx = new SurfaceControl.Transaction();
        SurfaceControl sc = taskPanel.getLeash();
        if (sc == null) {
            Log.e(TAG, "leash is null for " + taskPanel);
            return;
        }

        if (DEBUG) {
            Log.d(TAG, "With transaction id " + tx.getId() + " with panel" + taskPanel.getId()
                    + ", bounds="
                    + taskPanel.getBounds());
        }

        tx.setVisibility(sc, taskPanel.isVisible());
        tx.setAlpha(sc, taskPanel.getAlpha());
        tx.setLayer(sc, taskPanel.getLayer());
        tx.setPosition(sc, taskPanel.getBounds().left, taskPanel.getBounds().top);
        tx.setWindowCrop(sc, taskPanel.getBounds().width(), taskPanel.getBounds().height());
        tx.apply();
        Trace.endSection();
    }
}
