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

import static android.car.VehiclePropertyIds.HVAC_TEMPERATURE_SET;

import static com.android.systemui.car.hvac.HvacUtils.celsiusToFahrenheit;
import static com.android.systemui.car.hvac.HvacUtils.fahrenheitToCelsius;

import android.car.hardware.CarPropertyValue;
import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.core.content.ContextCompat;

import com.android.systemui.R;

/**
 *  An implementation of HvacView that displays the {@code HVAC_TEMPERATURE_SET} and two buttons to
 *  increase or decrease {@code HVAC_TEMPERATURE_SET}.
 */
public class TemperatureControlView extends LinearLayout implements HvacView {
    protected static final int BUTTON_REPEAT_INTERVAL_MS = 500;

    private final int mAreaId;
    private final int mAvailableTextColor;
    private final int mUnavailableTextColor;

    private boolean mAcOn;
    private boolean mTemperatureSetAvailable;
    private HvacPropertySetter mHvacPropertySetter;
    private TextView mTempTextView;
    private String mTempInDisplay;
    private View mIncreaseButton;
    private View mDecreaseButton;
    private float mMinTempC;
    private float mMaxTempC;
    private String mTemperatureFormat;
    private float mCurrentTempC;
    private boolean mDisplayInFahrenheit;

    public TemperatureControlView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        TypedArray typedArray = context.obtainStyledAttributes(attrs, R.styleable.TemperatureView);
        mAreaId = typedArray.getInt(R.styleable.TemperatureView_hvacAreaId, -1);
        mTemperatureFormat = getResources().getString(R.string.hvac_temperature_format);
        mMinTempC = getResources().getFloat(R.dimen.hvac_min_value_celsius);
        mMaxTempC = getResources().getFloat(R.dimen.hvac_max_value_celsius);
        mAvailableTextColor = ContextCompat.getColor(getContext(), R.color.system_bar_text_color);
        mUnavailableTextColor = ContextCompat.getColor(getContext(),
                R.color.system_bar_text_unavailable_color);
    }

    @Override
    public void onFinishInflate() {
        super.onFinishInflate();
        LayoutInflater.from(getContext()).inflate(R.layout.adjustable_temperature_view,
                /* root= */ this);
        mTempTextView = findViewById(R.id.hvac_temperature_text);
        mIncreaseButton = findViewById(R.id.hvac_increase_button);
        mDecreaseButton = findViewById(R.id.hvac_decrease_button);
        initButtons();
    }

    @Override
    public void onAcOnOffChanged(boolean acIsOn) {
        mAcOn = acIsOn;
        updateTemperatureView();
    }

    @Override
    public void onHvacTemperatureUnitChanged(boolean usesFahrenheit) {
        mDisplayInFahrenheit = usesFahrenheit;
        updateTemperatureView();
    }

    @Override
    public void onPropertyChanged(CarPropertyValue value) {
        if (value.getPropertyId() == HVAC_TEMPERATURE_SET) {
            mCurrentTempC = (Float) value.getValue();
            mTemperatureSetAvailable = value.getStatus() == CarPropertyValue.STATUS_AVAILABLE;
        }
        updateTemperatureView();
    }

    @Override
    public @HvacController.HvacProperty Integer getHvacPropertyToView() {
        return HVAC_TEMPERATURE_SET;
    }

    @Override
    public @HvacController.AreaId Integer getAreaId() {
        return mAreaId;
    }

    @Override
    public void setHvacPropertySetter(HvacPropertySetter hvacPropertySetter) {
        mHvacPropertySetter = hvacPropertySetter;
    }

    /**
     * Returns {@code true} if temperature should be available for change.
     */
    boolean isTemperatureAvailableForChange() {
        return mTemperatureSetAvailable && mHvacPropertySetter != null;
    }

    @VisibleForTesting
    String getTempInDisplay() {
        return mTempInDisplay;
    }

    @VisibleForTesting
    String getTempFormat() {
        return mTemperatureFormat;
    }

    private void initButtons() {
        mIncreaseButton.setOnClickListener((v) -> incrementTemperature(true));
        mDecreaseButton.setOnClickListener((v) -> incrementTemperature(false));

        setHoldToRepeatButton(mIncreaseButton);
        setHoldToRepeatButton(mDecreaseButton);
    }

    private void incrementTemperature(boolean increment) {
        float newTempC;
        if (mDisplayInFahrenheit) {
            float currentTempF = celsiusToFahrenheit(mCurrentTempC);
            float newTempF = increment ? currentTempF + 1 : currentTempF - 1;
            newTempC = fahrenheitToCelsius(newTempF);
        } else {
            newTempC = increment ? mCurrentTempC + 1 : mCurrentTempC - 1;
        }
        setTemperature(newTempC);
    }

    private void updateTemperatureView() {
        mTempInDisplay = String.format(mTemperatureFormat,
                mDisplayInFahrenheit ? celsiusToFahrenheit(mCurrentTempC) : mCurrentTempC);
        getContext().getMainExecutor().execute(() -> {
            mTempTextView.setText(mTempInDisplay);
            mTempTextView.setTextColor(mAcOn && mTemperatureSetAvailable ? mAvailableTextColor
                    : mUnavailableTextColor);
        });
    }

    private void setTemperature(float tempC) {
        tempC = Math.min(tempC, mMaxTempC);
        tempC = Math.max(tempC, mMinTempC);
        if (isTemperatureAvailableForChange()) {
            mHvacPropertySetter.setHvacProperty(HVAC_TEMPERATURE_SET, mAreaId, tempC);
        }
    }

    /**
     * Configures the {@code button} to be clicked repeatedly if clicked and held with
     * {@link #BUTTON_REPEAT_INTERVAL_MS}.
     */
    private void setHoldToRepeatButton(View button) {
        Runnable repeatClickRunnable = new Runnable() {
            @Override
            public void run() {
                button.performClick();
                mContext.getMainThreadHandler().postDelayed(this, BUTTON_REPEAT_INTERVAL_MS);
            }
        };

        button.setOnTouchListener((view, event) -> {
            int action = event.getAction();
            switch (action) {
                case MotionEvent.ACTION_DOWN:
                    repeatClickRunnable.run();
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    mContext.getMainThreadHandler().removeCallbacks(repeatClickRunnable);
            }

            // Return true so on click listener is not called superfluously.
            return true;
        });
    }
}
