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

package com.android.systemui.car.hvac.referenceui;

import static android.car.VehiclePropertyIds.HVAC_SEAT_TEMPERATURE;

import android.car.hardware.CarPropertyValue;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.Log;
import android.util.SparseArray;
import android.widget.ImageButton;

import androidx.annotation.ArrayRes;

import com.android.systemui.R;
import com.android.systemui.car.hvac.HvacController;
import com.android.systemui.car.hvac.HvacPropertySetter;
import com.android.systemui.car.hvac.HvacView;

public class SeatHeatLevelButton extends ImageButton implements HvacView {
    private static final String TAG = "SeatHeatLevelButton";
    private static final int DEFAULT_SEAT_HEAT_LEVEL_COUNT = 4;
    private static final int INVALID_ID = -1;

    private final SparseArray<Drawable> mIcons = new SparseArray<>();

    private int mAreaId;
    private HvacPropertySetter mHvacPropertySetter;
    private Drawable mCurrentIcon;
    private int mCurrentLevel;
    private int mTotalLevelCount;

    public SeatHeatLevelButton(Context context) {
        super(context);
    }

    public SeatHeatLevelButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        parseAttributes(attrs);
    }

    public SeatHeatLevelButton(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        parseAttributes(attrs);
    }

    @Override
    public void onFinishInflate() {
        super.onFinishInflate();
        setOnClickListener(v -> {
            if (mHvacPropertySetter != null) {
                mHvacPropertySetter.setHvacProperty(getHvacPropertyToView(), mAreaId,
                        (mCurrentLevel + 1) % mTotalLevelCount);
            }
        });
        setOnLongClickListener(v -> {
            if (mHvacPropertySetter != null) {
                mHvacPropertySetter.setHvacProperty(getHvacPropertyToView(), mAreaId,
                        mCurrentLevel == 0 ? mTotalLevelCount - 1 : 0);
                return true;
            }
            return false;
        });
        updateIcon();
    }

    @Override
    public void setHvacPropertySetter(HvacPropertySetter hvacPropertySetter) {
        mHvacPropertySetter = hvacPropertySetter;
    }

    @Override
    public void onPropertyChanged(CarPropertyValue value) {
        if (value.getPropertyId() == getHvacPropertyToView() && value.getAreaId() == getAreaId()) {
            mCurrentLevel = (int) value.getValue();
            mCurrentIcon = mIcons.get(mCurrentLevel);
            updateIcon();
        }
    }

    @Override
    public @HvacController.HvacProperty Integer getHvacPropertyToView() {
        return HVAC_SEAT_TEMPERATURE;
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

    private void updateIcon() {
        setImageDrawable(mCurrentIcon);
    }

    private void parseAttributes(AttributeSet attrs) {
        mIcons.clear();

        mTotalLevelCount = mContext.getResources().getInteger(R.integer.hvac_seat_heat_level_count);

        TypedArray typedArray = mContext.obtainStyledAttributes(attrs, R.styleable.HvacView);
        mAreaId = typedArray.getInt(R.styleable.HvacView_hvacAreaId, INVALID_ID);
        typedArray.recycle();

        typedArray = mContext.obtainStyledAttributes(attrs, R.styleable.SeatHeatLevelButton);
        @ArrayRes int drawableListRes = typedArray.getResourceId(
                R.styleable.SeatHeatLevelButton_seatHeaterIconDrawableList, INVALID_ID);
        if (drawableListRes == INVALID_ID) {
            if (mTotalLevelCount != DEFAULT_SEAT_HEAT_LEVEL_COUNT) {
                throw new IllegalArgumentException(
                        "R.styeable.SeatHeatLevelButton_seatHeaterIconDrawableList should be "
                                + "defined if R.integer.hvac_seat_heat_level_count is updated");
            }

            Log.d(TAG, "Using default seat heater icon drawables.");
            mIcons.set(0, mContext.getDrawable(R.drawable.ic_seat_heat_off));
            mIcons.set(1, mContext.getDrawable(R.drawable.ic_seat_heat_level_1));
            mIcons.set(2, mContext.getDrawable(R.drawable.ic_seat_heat_level_2));
            mIcons.set(3, mContext.getDrawable(R.drawable.ic_seat_heat_level_3));
            return;
        }

        TypedArray seatHeatIcons = mContext.getResources().obtainTypedArray(drawableListRes);
        if (seatHeatIcons.length() != mTotalLevelCount) {
            throw new IllegalArgumentException(
                    "R.styeable.SeatHeatLevelButton_seatHeaterIconDrawableList should have the "
                            + "same length as R.integer.hvac_seat_heat_level_count");
        }

        for (int i = 0; i < mTotalLevelCount; i++) {
            mIcons.set(i, seatHeatIcons.getDrawable(i));
        }
        seatHeatIcons.recycle();
        typedArray.recycle();
    }
}