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
import android.os.Build;

import androidx.annotation.NonNull;

import com.android.car.scalableui.panel.Panel;

/**
 * Abstract base class for implementing a {@link Panel}.
 *
 * <p>Provides common functionality and state management for different types of panels
 */
public abstract class BasePanel implements Panel {
    protected static final boolean DEBUG = Build.isDebuggable();

    private final Context mContext;
    private int mLayer = -1;

    private int mRole = 0;
    private Rect mBounds = null;
    private boolean mIsVisible;
    private String mId;
    private float mAlpha;
    private int mDisplayId;
    private int mCornerRadius;

    public BasePanel(@NonNull Context context, String id) {
        mContext = context;
        mId = id;
    }

    public Context getContext() {
        return mContext;
    }

    public int getRole() {
        return mRole;
    }

    public String getId() {
        return mId;
    }

    public int getDisplayId() {
        return mDisplayId;
    }

    @Override
    @NonNull
    public String getPanelId() {
        return mId;
    }

    @Override
    public int getLayer() {
        return mLayer;
    }

    @Override
    public void setLayer(int layer) {
        this.mLayer = layer;
    }

    @Override
    public int getX1() {
        return mBounds.left;
    }

    @Override
    public int getX2() {
        return mBounds.right;
    }

    @Override
    public int getY1() {
        return mBounds.top;
    }

    @Override
    public int getY2() {
        return mBounds.bottom;
    }

    @Override
    public void setX1(int x) {
        setBounds(new Rect(x, getY1(), getX2(), getY2()));
    }

    @Override
    public void setX2(int x) {
        setBounds(new Rect(getX1(), getY1(), x, getY2()));
    }

    @Override
    public void setY1(int y) {
        setBounds(new Rect(getX1(), y, getX2(), getY2()));
    }

    @Override
    public void setY2(int y) {
        setBounds(new Rect(getX1(), getY1(), getX2(), y));
    }

    @Override
    public boolean isVisible() {
        return mIsVisible;
    }

    @Override
    public void setVisibility(boolean isVisible) {
        mIsVisible = isVisible;
    }

    @Override
    public float getAlpha() {
        return mAlpha;
    }

    @Override
    public void setAlpha(float alpha) {
        mAlpha = alpha;
    }

    @Override
    public void setCornerRadius(int radius) {
        mCornerRadius = radius;
    }

    @Override
    public int getCornerRadius() {
        return mCornerRadius;
    }

    @Override
    public void setDisplayId(int displayId) {
        mDisplayId = displayId;
    }

    @Override
    public Rect getBounds() {
        return mBounds;
    }

    @Override
    public void setBounds(Rect bounds) {
        mBounds = bounds;
    }

    @Override
    public void setRole(int role) {
        mRole = role;
    }
}
