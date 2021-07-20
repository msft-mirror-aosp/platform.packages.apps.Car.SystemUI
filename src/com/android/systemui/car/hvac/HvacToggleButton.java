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

package com.android.systemui.car.hvac;

import static android.car.VehiclePropertyIds.HVAC_AUTO_ON;
import static android.car.VehiclePropertyIds.HVAC_POWER_ON;

import android.car.hardware.CarPropertyValue;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.Log;
import android.widget.ImageButton;

import com.android.systemui.R;

public class HvacToggleButton extends ImageButton implements HvacView {
    private static int INVALID_ID = -1;

    private int mPropertyId;
    private int mAreaId;
    private boolean mIsOn;
    private boolean mPowerOn;
    private boolean mAutoOn;
    private boolean mTurnOffIfPowerOff;
    private boolean mTurnOffIfAutoOn;
    private Drawable mOnDrawable;
    private Drawable mOffDrawable;
    private float mOnAlpha;
    private float mOffAlpha;
    private HvacPropertySetter mHvacPropertySetter;

    public HvacToggleButton(Context context) {
        super(context);
    }

    public HvacToggleButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        parseAttributes(attrs);
    }

    public HvacToggleButton(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        parseAttributes(attrs);
    }

    @Override
    public void onFinishInflate() {
        super.onFinishInflate();
        setOnClickListener(v -> {
            if (!shouldAllowControl()) return;

            if (mHvacPropertySetter != null) {
                mHvacPropertySetter.setHvacProperty(mPropertyId, mAreaId, /* value= */ !mIsOn);
            }
        });
        updateButtonView();
    }

    @Override
    public void setHvacPropertySetter(HvacPropertySetter hvacPropertySetter) {
        mHvacPropertySetter = hvacPropertySetter;
    }

    @Override
    public void onPropertyChanged(CarPropertyValue value) {
        if (value.getPropertyId() == HVAC_POWER_ON) {
            mPowerOn = (Boolean) value.getValue();
        }

        if (value.getPropertyId() == HVAC_AUTO_ON) {
            mAutoOn = (Boolean) value.getValue();
        }

        if (value.getPropertyId() == getHvacPropertyToView()) {
            mIsOn = (Boolean) value.getValue();
        }

        updateButtonView();
    }

    @Override
    public @HvacController.HvacProperty Integer getHvacPropertyToView() {
        return mPropertyId;
    }

    @Override
    public @HvacController.AreaId Integer getAreaId() {
        return mAreaId;
    }

    @Override
    public void onHvacTemperatureUnitChanged(boolean usesFahrenheit) {
        // no-op.
    }

    @Override
    public void onLocaleListChanged() {
        // no-op.
    }

    private void parseAttributes(AttributeSet attrs) {
        TypedArray typedArray = mContext.obtainStyledAttributes(attrs, R.styleable.HvacView);
        mPropertyId = typedArray.getInt(R.styleable.HvacView_hvacPropertyId, INVALID_ID);
        mAreaId = typedArray.getInt(R.styleable.HvacView_hvacAreaId, INVALID_ID);
        mOnDrawable = typedArray.getDrawable(R.styleable.HvacView_hvacToggleOnButtonDrawable);
        mOffDrawable = typedArray.getDrawable(R.styleable.HvacView_hvacToggleOffButtonDrawable);
        mTurnOffIfPowerOff =
                typedArray.getBoolean(R.styleable.HvacView_hvacTurnOffIfPowerOff, true);
        mTurnOffIfAutoOn = typedArray.getBoolean(R.styleable.HvacView_hvacTurnOffIfAutoOn, false);
        mOnAlpha = mContext.getResources().getFloat(R.dimen.hvac_turned_on_alpha);
        mOffAlpha = mContext.getResources().getFloat(R.dimen.hvac_turned_off_alpha);
    }

    private void updateButtonView() {
        setAlpha(shouldAllowControl() ? mOnAlpha : mOffAlpha);
        setImageDrawable(mIsOn ? mOnDrawable : mOffDrawable);
    }

    private boolean shouldAllowControl() {
        if (mTurnOffIfPowerOff && !mPowerOn) {
            return false;
        }

        if (mTurnOffIfAutoOn && mAutoOn) {
            return false;
        }

        return true;
    }
}
