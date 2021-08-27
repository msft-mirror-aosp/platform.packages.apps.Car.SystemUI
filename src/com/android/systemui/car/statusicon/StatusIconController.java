/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.systemui.car.statusicon;

import android.graphics.drawable.Drawable;
import android.widget.ImageView;

import androidx.annotation.VisibleForTesting;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;

import java.util.HashMap;
import java.util.Map;

/**
 * Abstract class to extend to control views that display a certain status icon.
 */
public abstract class StatusIconController {
    private final StatusIconData mStatusIconData = new StatusIconData();
    private final MutableLiveData<StatusIconData> mStatusIconLiveData =
            new MutableLiveData<>(mStatusIconData);
    private final Map<ImageView, Observer<StatusIconData>> mObserverMap = new HashMap<>();

    /**
     * Registers an {@link ImageView} to contain the icon that this controller controls.
     */
    public final void registerIconView(ImageView view) {
        if (mObserverMap.containsKey(view)) return;

        Observer<StatusIconData> observer = getStatusIconViewObserver(view);
        mObserverMap.put(view, observer);
        mStatusIconLiveData.observeForever(observer);
    }

    /**
     * Unregisters the observer for an {@link ImageView}.
     */
    public final void unregisterIconView(ImageView view) {
        Observer<StatusIconData> observer = mObserverMap.remove(view);
        if (observer != null) {
            mStatusIconLiveData.removeObserver(observer);
        }
    }

    /**
     * Returns the {@link Drawable} set to be displayed as the icon.
     */
    public Drawable getIconDrawableToDisplay() {
        return mStatusIconData.getIconDrawable();
    }

    /**
     * Sets the icon drawable to display.
     */
    protected final void setIconDrawableToDisplay(Drawable drawable) {
        mStatusIconData.setIconDrawable(drawable);
    }

    /**
     * Provides observing views with the {@link StatusIconData} and causes them to update
     * themselves accordingly through {@link #updateIconView}.
     */
    protected void onStatusUpdated() {
        mStatusIconLiveData.setValue(mStatusIconData);
    }

    /**
     * Updates the icon view based on the current {@link StatusIconData}.
     */
    protected void updateIconView(ImageView view, StatusIconData data) {
        view.setImageDrawable(data.getIconDrawable());
    }

    /**
     * Determines the icon to display via {@link #setIconDrawableToDisplay} and notifies observing
     * views by calling {@link #onStatusUpdated} at the end.
     */
    protected abstract void updateStatus();

    @VisibleForTesting
    boolean isViewRegistered(ImageView view) {
        return mObserverMap.containsKey(view);
    }

    private Observer<StatusIconData> getStatusIconViewObserver(ImageView view) {
        return new Observer<StatusIconData>() {
            @Override
            public void onChanged(StatusIconData statusIconData) {
                updateIconView(view, statusIconData);
            }
        };
    }
}
