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
package com.android.systemui.car.wm.scalableui.panel.panelupdates;

import android.graphics.Insets;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.car.scalableui.model.PanelControllerMetadata;
import com.android.car.scalableui.panel.PanelUpdatePublisher;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementation of {@link PanelUpdatePublisher} and {@link PanelUpdateConsumer}
 * that manages ScalableUI Panel updates.
 *
 * <p>This class allows Non-ScalableUI components to register for updates on specific panel
 * (panel_id)
 * and enables ScalableUI components to publish these updates. It ensures that UI updates
 * are dispatched on the main thread. It also provides an option to replay the last known
 * state to newly registered listeners.
 */
public class ScalableUIPanelUpdateImpl implements PanelUpdatePublisher, PanelUpdateConsumer {

    /**
     * Stores a set of {@link PanelUpdateCallback} listeners for each panel ID.
     * Uses a {@link LinkedHashSet} to maintain insertion order and uniqueness of callbacks.
     */
    private final Map<String, LinkedHashSet<PanelUpdateCallback>> mPanelUpdateListenersPerPanelMap =
            new HashMap<>();
    /**
     * Caches the last known {@link PanelState} (e.g., bounds) for each panel ID.
     * Uses a {@link ConcurrentHashMap} for thread-safe access and modification.
     */
    private final Map<String, PanelState> mLastKnownPanelState = new ConcurrentHashMap<>();

    /**
     * Handler associated with the main application thread (UI thread).
     * Used to ensure that {@link PanelUpdateCallback} methods are invoked on the main thread.
     */
    private final Handler mMainThreadHandler = new Handler(Looper.getMainLooper());

    @Override
    public void registerCallback(@NonNull String panelId, @NonNull PanelUpdateCallback callback) {
        LinkedHashSet<PanelUpdateCallback> callbacks =
                mPanelUpdateListenersPerPanelMap.getOrDefault(panelId, new LinkedHashSet<>());
        callbacks.add(callback);
        mPanelUpdateListenersPerPanelMap.put(panelId, callbacks);

        if (!mLastKnownPanelState.containsKey(panelId)) {
            return;
        }

        Rect bounds = getBounds(panelId);
        if (bounds != null) {
            callback.onBoundsChange(panelId, bounds);
        }

        Float alpha = getAlpha(panelId);
        if (alpha != null) {
            callback.onAlphaChange(panelId, alpha);
        }

        Integer radius = getCornerRadius(panelId);
        if (radius != null) {
            callback.onCornerRadiusChange(panelId, radius);
        }

        Boolean visibility = isVisible(panelId);
        if (visibility != null) {
            callback.onVisibilityChange(panelId, visibility);
        }

        Insets insets = getInsets(panelId);
        if (insets != null) {
            callback.onInsetsChange(panelId, insets);
        }

        Drawable scrim = getScrim(panelId);
        callback.onScrimChange(panelId, scrim);

        int gravity = getGravity(panelId);
        callback.onGravityChange(panelId, gravity);
    }

    @Override
    public void unregisterCallback(@NonNull PanelUpdateCallback callback) {
        for (Map.Entry<String, LinkedHashSet<PanelUpdateCallback>> entry :
                mPanelUpdateListenersPerPanelMap.entrySet()) {
            unregisterCallback(entry.getKey(), callback);
        }
    }

    @Override
    public void unregisterCallback(@NonNull String panelId, @NonNull PanelUpdateCallback callback) {
        LinkedHashSet<PanelUpdateCallback> callbacksSet = mPanelUpdateListenersPerPanelMap.get(
                panelId);
        boolean removed = callbacksSet.remove(callback);
        if (removed && callbacksSet.isEmpty()) {
            mPanelUpdateListenersPerPanelMap.remove(panelId); // Clean up map if set becomes empty
        }
    }

    @Override
    public void postBounds(String panelId, Rect bounds) {
        PanelState panelState = mLastKnownPanelState.getOrDefault(panelId, new PanelState());
        panelState.setBounds(bounds);
        mLastKnownPanelState.put(panelId, panelState);
        LinkedHashSet<PanelUpdateCallback> callbacks = mPanelUpdateListenersPerPanelMap.get(
                panelId);
        if (callbacks == null) {
            return;
        }
        mMainThreadHandler.post(() -> callbacks.forEach(
                boundsUpdateCallback -> boundsUpdateCallback.onBoundsChange(panelId, bounds)));
    }

    @Override
    public void postAlpha(String panelId, float alpha) {
        PanelState panelState = mLastKnownPanelState.getOrDefault(panelId, new PanelState());
        panelState.setAlpha(alpha);
        mLastKnownPanelState.put(panelId, panelState);
        LinkedHashSet<PanelUpdateCallback> callbacks = mPanelUpdateListenersPerPanelMap.get(
                panelId);
        if (callbacks == null) {
            return;
        }
        mMainThreadHandler.post(() -> callbacks.forEach(
                boundsUpdateCallback -> boundsUpdateCallback.onAlphaChange(panelId, alpha)));
    }

    @Override
    public void postCornerRadius(String panelId, int radius) {
        PanelState panelState = mLastKnownPanelState.getOrDefault(panelId, new PanelState());
        panelState.setRadius(radius);
        mLastKnownPanelState.put(panelId, panelState);
        LinkedHashSet<PanelUpdateCallback> callbacks = mPanelUpdateListenersPerPanelMap.get(
                panelId);
        if (callbacks == null) {
            return;
        }
        mMainThreadHandler.post(() -> callbacks.forEach(
                boundsUpdateCallback -> boundsUpdateCallback.onCornerRadiusChange(panelId,
                        radius)));
    }

    @Override
    public void postVisibility(String panelId, boolean isVisible) {
        PanelState panelState = mLastKnownPanelState.getOrDefault(panelId, new PanelState());
        panelState.setVisible(isVisible);
        mLastKnownPanelState.put(panelId, panelState);
        LinkedHashSet<PanelUpdateCallback> callbacks = mPanelUpdateListenersPerPanelMap.get(
                panelId);
        if (callbacks == null) {
            return;
        }
        mMainThreadHandler.post(() -> callbacks.forEach(
                boundsUpdateCallback -> boundsUpdateCallback.onVisibilityChange(panelId,
                        isVisible)));
    }

    @Override
    public void postInsets(String panelId, Insets insets) {
        PanelState panelState = mLastKnownPanelState.getOrDefault(panelId, new PanelState());
        panelState.setInsets(insets);
        mLastKnownPanelState.put(panelId, panelState);
        LinkedHashSet<PanelUpdateCallback> callbacks = mPanelUpdateListenersPerPanelMap.get(
                panelId);
        if (callbacks == null) {
            return;
        }
        mMainThreadHandler.post(() -> callbacks.forEach(
                boundsUpdateCallback -> boundsUpdateCallback.onInsetsChange(panelId, insets)));
    }

    @Override
    public void postControllerMetadata(String panelId, PanelControllerMetadata metadata) {
        PanelState panelState = mLastKnownPanelState.getOrDefault(panelId, new PanelState());
        panelState.setMetadata(metadata);
        mLastKnownPanelState.put(panelId, panelState);
    }

    @Override
    public void postScrim(String panelId, @Nullable Drawable scrim) {
        PanelState panelState = mLastKnownPanelState.getOrDefault(panelId, new PanelState());
        panelState.setScrim(scrim);
        mLastKnownPanelState.put(panelId, panelState);
        LinkedHashSet<PanelUpdateCallback> callbacks = mPanelUpdateListenersPerPanelMap.get(
                panelId);
        if (callbacks == null) {
            return;
        }
        mMainThreadHandler.post(() -> callbacks.forEach(
                updateCallback -> updateCallback.onScrimChange(panelId, scrim)));
    }

    @Override
    public void postGravity(String panelId, int gravity) {
        PanelState panelState = mLastKnownPanelState.getOrDefault(panelId, new PanelState());
        panelState.setGravity(gravity);
        mLastKnownPanelState.put(panelId, panelState);
        LinkedHashSet<PanelUpdateCallback> callbacks = mPanelUpdateListenersPerPanelMap.get(
                panelId);
        if (callbacks == null) {
            return;
        }
        mMainThreadHandler.post(() -> callbacks.forEach(
                updateCallback -> updateCallback.onGravityChange(panelId, gravity)));
    }

    @Override
    @Nullable
    public Rect getBounds(String panelId) {
        if (mLastKnownPanelState.get(panelId) != null) {
            return Rect.copyOrNull(mLastKnownPanelState.get(panelId).getBounds());
        }
        return null;
    }

    @Override
    public Float getAlpha(String panelId) {
        if (mLastKnownPanelState.get(panelId) != null) {
            return mLastKnownPanelState.get(panelId).getAlpha();
        }
        return null;
    }

    @Override
    public Integer getCornerRadius(String panelId) {
        if (mLastKnownPanelState.get(panelId) != null) {
            return mLastKnownPanelState.get(panelId).getRadius();
        }
        return null;
    }

    @Override
    public Boolean isVisible(String panelId) {
        if (mLastKnownPanelState.get(panelId) != null) {
            return mLastKnownPanelState.get(panelId).isVisible();
        }
        return null;
    }

    @Nullable
    @Override
    public Insets getInsets(String panelId) {
        if (mLastKnownPanelState.get(panelId) != null) {
            return mLastKnownPanelState.get(panelId).getInsets();
        }
        return null;
    }

    @Nullable
    @Override
    public PanelControllerMetadata getPanelControllerMetadata(String panelId) {
        if (mLastKnownPanelState.get(panelId) != null) {
            return mLastKnownPanelState.get(panelId).getMetadata();
        }
        return null;
    }

    @Nullable
    @Override
    public Drawable getScrim(String panelId) {
        if (mLastKnownPanelState.get(panelId) != null) {
            return mLastKnownPanelState.get(panelId).getScrim();
        }
        return null;
    }

    @Override
    public int getGravity(String panelId) {
        if (mLastKnownPanelState.get(panelId) != null) {
            return mLastKnownPanelState.get(panelId).getGravity();
        }
        return Gravity.NO_GRAVITY;
    }

    /**
     * Internal data class to hold the last known state of a panel.
     */
    private static class PanelState {
        @Nullable
        private Rect mBounds;
        @Nullable
        private Float mAlpha;
        @Nullable
        private Integer mRadius;
        @Nullable
        private Boolean mIsVisible;
        @Nullable
        private Insets mInsets;
        @Nullable
        private PanelControllerMetadata mMetadata;
        @Nullable
        private Drawable mScrim;
        private int mGravity = Gravity.NO_GRAVITY;

        @Nullable
        Float getAlpha() {
            return mAlpha;
        }

        void setAlpha(@Nullable Float alpha) {
            mAlpha = alpha;
        }

        @Nullable
        Integer getRadius() {
            return mRadius;
        }

        void setRadius(@Nullable Integer radius) {
            mRadius = radius;
        }

        @Nullable
        Boolean isVisible() {
            return mIsVisible;
        }

        void setVisible(@Nullable Boolean visible) {
            mIsVisible = visible;
        }

        @Nullable
        Insets getInsets() {
            return mInsets;
        }

        void setInsets(@Nullable Insets insets) {
            mInsets = insets;
        }

        @Nullable
        PanelControllerMetadata getMetadata() {
            return mMetadata;
        }

        void setMetadata(@Nullable PanelControllerMetadata metadata) {
            mMetadata = metadata;
        }

        @Nullable
        Rect getBounds() {
            return mBounds;
        }

        void setBounds(@Nullable Rect bounds) {
            mBounds = bounds;
        }

        @Nullable
        Drawable getScrim() {
            return mScrim;
        }

        void setScrim(@Nullable Drawable scrim) {
            mScrim = scrim;
        }

        int getGravity() {
            return mGravity;
        }

        void setGravity(int gravity) {
            mGravity = gravity;
        }
    }

}
