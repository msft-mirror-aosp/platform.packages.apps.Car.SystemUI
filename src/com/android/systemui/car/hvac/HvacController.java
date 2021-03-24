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

import static android.car.VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL;
import static android.car.VehiclePropertyIds.HVAC_ACTUAL_FAN_SPEED_RPM;
import static android.car.VehiclePropertyIds.HVAC_AC_ON;
import static android.car.VehiclePropertyIds.HVAC_AUTO_ON;
import static android.car.VehiclePropertyIds.HVAC_AUTO_RECIRC_ON;
import static android.car.VehiclePropertyIds.HVAC_DEFROSTER;
import static android.car.VehiclePropertyIds.HVAC_DUAL_ON;
import static android.car.VehiclePropertyIds.HVAC_ELECTRIC_DEFROSTER_ON;
import static android.car.VehiclePropertyIds.HVAC_FAN_DIRECTION;
import static android.car.VehiclePropertyIds.HVAC_FAN_DIRECTION_AVAILABLE;
import static android.car.VehiclePropertyIds.HVAC_FAN_SPEED;
import static android.car.VehiclePropertyIds.HVAC_MAX_AC_ON;
import static android.car.VehiclePropertyIds.HVAC_MAX_DEFROST_ON;
import static android.car.VehiclePropertyIds.HVAC_POWER_ON;
import static android.car.VehiclePropertyIds.HVAC_RECIRC_ON;
import static android.car.VehiclePropertyIds.HVAC_SEAT_TEMPERATURE;
import static android.car.VehiclePropertyIds.HVAC_SEAT_VENTILATION;
import static android.car.VehiclePropertyIds.HVAC_SIDE_MIRROR_HEAT;
import static android.car.VehiclePropertyIds.HVAC_STEERING_WHEEL_HEAT;
import static android.car.VehiclePropertyIds.HVAC_TEMPERATURE_CURRENT;
import static android.car.VehiclePropertyIds.HVAC_TEMPERATURE_DISPLAY_UNITS;
import static android.car.VehiclePropertyIds.HVAC_TEMPERATURE_SET;

import android.annotation.IntDef;
import android.car.Car;
import android.car.VehicleUnit;
import android.car.hardware.CarPropertyValue;
import android.car.hardware.property.CarPropertyManager;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.VisibleForTesting;

import com.android.systemui.car.CarServiceProvider;
import com.android.systemui.dagger.qualifiers.UiBackground;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

import javax.inject.Inject;

/**
 * Connects to {@link CarPropertyManager} to subscribe to HVAC property change events and propagate
 * them to subscribing {@link HvacView}s by property ID and area ID.
 *
 * Grants {@link HvacView}s access to {@link HvacPropertySetter} with API's to write new values
 * for HVAC properties.
 */
public class HvacController implements HvacPropertySetter {
    private static final String TAG = HvacController.class.getSimpleName();
    private static final Boolean DEBUG = false;
    private static final Integer[] HVAC_PROPERTIES =
            {HVAC_FAN_SPEED, HVAC_FAN_DIRECTION, HVAC_TEMPERATURE_CURRENT, HVAC_TEMPERATURE_SET,
                    HVAC_DEFROSTER, HVAC_AC_ON, HVAC_MAX_AC_ON, HVAC_MAX_DEFROST_ON, HVAC_RECIRC_ON,
                    HVAC_DUAL_ON, HVAC_AUTO_ON, HVAC_SEAT_TEMPERATURE, HVAC_SIDE_MIRROR_HEAT,
                    HVAC_STEERING_WHEEL_HEAT, HVAC_TEMPERATURE_DISPLAY_UNITS,
                    HVAC_ACTUAL_FAN_SPEED_RPM, HVAC_POWER_ON, HVAC_FAN_DIRECTION_AVAILABLE,
                    HVAC_AUTO_RECIRC_ON, HVAC_SEAT_VENTILATION, HVAC_ELECTRIC_DEFROSTER_ON};

    @IntDef(value = {HVAC_FAN_SPEED, HVAC_FAN_DIRECTION, HVAC_TEMPERATURE_CURRENT,
            HVAC_TEMPERATURE_SET, HVAC_DEFROSTER, HVAC_AC_ON, HVAC_MAX_AC_ON, HVAC_MAX_DEFROST_ON,
            HVAC_RECIRC_ON, HVAC_DUAL_ON, HVAC_AUTO_ON, HVAC_SEAT_TEMPERATURE,
            HVAC_SIDE_MIRROR_HEAT, HVAC_STEERING_WHEEL_HEAT, HVAC_TEMPERATURE_DISPLAY_UNITS,
            HVAC_ACTUAL_FAN_SPEED_RPM, HVAC_POWER_ON, HVAC_FAN_DIRECTION_AVAILABLE,
            HVAC_AUTO_RECIRC_ON, HVAC_SEAT_VENTILATION, HVAC_ELECTRIC_DEFROSTER_ON})
    @Target({ElementType.TYPE_PARAMETER, ElementType.TYPE_USE})
    @interface HvacProperty {
    }

    @Target({ElementType.TYPE_PARAMETER, ElementType.TYPE_USE})
    @interface AreaId {
    }

    private final Executor mExecutor;
    private final ArrayList<View> mViewsToInit = new ArrayList<>();
    private final Map<@HvacProperty Integer, Map<@AreaId Integer, List<HvacView>>>
            mHvacPropertyViewMap = new HashMap<>();
    private final CarPropertyManager.CarPropertyEventCallback mPropertyEventCallback =
            new CarPropertyManager.CarPropertyEventCallback() {
                @Override
                public void onChangeEvent(CarPropertyValue value) {
                    mExecutor.execute(() -> handleHvacPropertyChange(value.getPropertyId(), value));
                }

                @Override
                public void onErrorEvent(int propId, int zone) {
                    Log.d(TAG, "Could not handle " + propId + " change event in zone " + zone);
                }
            };
    @VisibleForTesting
    final CarServiceProvider.CarServiceOnConnectedListener mCarServiceLifecycleListener =
            car -> {
                try {
                    mIsConnectedToCar = true;
                    mCarPropertyManager = (CarPropertyManager) car.getCarManager(
                            Car.PROPERTY_SERVICE);
                    registerHvacPropertyEventListeners();
                    mViewsToInit.forEach(this::registerHvacView);
                    mViewsToInit.clear();
                } catch (Exception e) {
                    Log.e(TAG, "Failed to connect to HVAC", e);
                    mIsConnectedToCar = false;
                }
            };


    private CarPropertyManager mCarPropertyManager;
    private boolean mIsConnectedToCar;

    @Inject
    public HvacController(CarServiceProvider carServiceProvider, @UiBackground Executor executor) {
        mExecutor = executor;
        if (!mIsConnectedToCar) {
            carServiceProvider.addListener(mCarServiceLifecycleListener);
        }
    }

    @Override
    public void setHvacProperty(@HvacProperty Integer propertyId, int areaId, int val) {
        mExecutor.execute(() -> {
            try {
                mCarPropertyManager.setIntProperty(propertyId, areaId, val);
            } catch (RuntimeException e) {
                Log.w(TAG, "Error while setting HVAC property: ", e);
            }
        });
    }

    @Override
    public void setHvacProperty(@HvacProperty Integer propertyId, int areaId, float val) {
        mExecutor.execute(() -> {
            try {
                mCarPropertyManager.setFloatProperty(propertyId, areaId, val);
            } catch (RuntimeException e) {
                Log.w(TAG, "Error while setting HVAC property: ", e);
            }
        });
    }

    @Override
    public void setHvacProperty(@HvacProperty Integer propertyId, int areaId, boolean val) {
        mExecutor.execute(() -> {
            try {
                mCarPropertyManager.setBooleanProperty(propertyId, areaId, val);
            } catch (RuntimeException e) {
                Log.w(TAG, "Error while setting HVAC property: ", e);
            }
        });
    }

    /**
     * Recursively find all HvacPropertyBaseViews to register to this HvacController.
     */
    public void registerHvacView(View hvacView) {
        if (!mIsConnectedToCar) {
            mViewsToInit.add(hvacView);
            return;
        }

        if (hvacView instanceof HvacView) {
            HvacView hvacPropView = (HvacView) hvacView;
            @HvacProperty Integer propId = hvacPropView.getHvacPropertyToView();
            @AreaId Integer areaId = hvacPropView.getAreaId();
            hvacPropView.setHvacPropertySetter(this);

            addHvacViewToMap(propId, areaId, hvacPropView);
            // init the view with the initial value.
            if (mCarPropertyManager != null) {
                CarPropertyValue initValue = mCarPropertyManager.getProperty(propId, areaId);
                boolean hvacOn = mCarPropertyManager.getBooleanProperty(HVAC_AC_ON,
                        mCarPropertyManager.getAreaId(HVAC_AC_ON, areaId));
                boolean usesFahrenheit = mCarPropertyManager.getIntProperty(
                        HVAC_TEMPERATURE_DISPLAY_UNITS,
                        mCarPropertyManager.getAreaId(HVAC_TEMPERATURE_DISPLAY_UNITS,
                                areaId)) == VehicleUnit.FAHRENHEIT;
                hvacPropView.onPropertyChanged(initValue);
                hvacPropView.onHvacOnOffChanged(hvacOn);
                hvacPropView.onHvacTemperatureUnitChanged(usesFahrenheit);
            }
        }

        if (hvacView instanceof ViewGroup) {
            ViewGroup hvacContainerView = (ViewGroup) hvacView;
            for (int i = 0; i < hvacContainerView.getChildCount(); i++) {
                registerHvacView(hvacContainerView.getChildAt(i));
            }
        }
    }

    public void unregisterView(View view) {
        if (view instanceof HvacView) {
            HvacView hvacView = (HvacView) view;
            @HvacProperty Integer propId = hvacView.getHvacPropertyToView();
            @AreaId Integer areaId = hvacView.getAreaId();

            removeHvacViewFromMap(propId, areaId, hvacView);
        }

        if (view instanceof ViewGroup) {
            ViewGroup hvacContainerView = (ViewGroup) view;
            for (int i = 0; i < hvacContainerView.getChildCount(); i++) {
                unregisterView(hvacContainerView.getChildAt(i));
            }
        }
    }

    @VisibleForTesting
    void handleHvacPropertyChange(@HvacProperty int propertyId, CarPropertyValue value) {
        List<HvacView> viewsToNotify = null;

        if (value.getPropertyId() == HVAC_AC_ON) {
            mHvacPropertyViewMap.forEach((propId, areaIds) -> {
                areaIds.forEach((areaId, views) -> {
                    views.forEach(v -> v.onHvacOnOffChanged((boolean) value.getValue()));
                });
            });
        }

        if (value.getPropertyId() == HVAC_TEMPERATURE_DISPLAY_UNITS) {
            mHvacPropertyViewMap.forEach((propId, areaIds) -> {
                areaIds.forEach((areaId, views) -> {
                    views.forEach(v -> v.onHvacTemperatureUnitChanged(
                            (Integer) value.getValue() == VehicleUnit.FAHRENHEIT));
                });
            });
        }

        if (mHvacPropertyViewMap.containsKey(propertyId)) {
            if (value.getAreaId() == VEHICLE_AREA_TYPE_GLOBAL) {
                mHvacPropertyViewMap.get(propertyId).forEach((areaId, views) -> {
                    views.forEach(v -> v.onPropertyChanged(value));
                });
            } else {
                viewsToNotify = mHvacPropertyViewMap.get(propertyId).get(value.getAreaId());
                if (viewsToNotify != null) {
                    viewsToNotify.forEach(v -> v.onPropertyChanged(value));
                }
            }
        }
    }

    @VisibleForTesting
    Map<@HvacProperty Integer, Map<@AreaId Integer, List<HvacView>>> getHvacPropertyViewMap() {
        return mHvacPropertyViewMap;
    }

    private void registerHvacPropertyEventListeners() {
        for (int i = 0; i < HVAC_PROPERTIES.length; i++) {
            @HvacProperty Integer propertyId = HVAC_PROPERTIES[i];
            mCarPropertyManager.registerCallback(mPropertyEventCallback, propertyId,
                    CarPropertyManager.SENSOR_RATE_ONCHANGE);
        }
    }

    private void addHvacViewToMap(@HvacProperty int propId, @AreaId int areaId,
            HvacView v) {
        mHvacPropertyViewMap.computeIfAbsent(propId, k -> new HashMap<>());
        mHvacPropertyViewMap.get(propId).computeIfAbsent(areaId, k -> new ArrayList<>());
        mHvacPropertyViewMap.get(propId).get(areaId).add(v);
    }

    private void removeHvacViewFromMap(@HvacProperty int propId, @AreaId int areaId, HvacView v) {
        if (mHvacPropertyViewMap.get(propId) != null) {
            List<HvacView> registeredViews = mHvacPropertyViewMap.get(propId).get(areaId);
            if (registeredViews != null) {
                registeredViews.remove(v);
                if (registeredViews.isEmpty()) {
                    mHvacPropertyViewMap.get(propId).remove(areaId);
                    if (mHvacPropertyViewMap.get(propId).keySet().size() == 0) {
                        mHvacPropertyViewMap.remove(propId);
                    }
                }
            }
        }
    }
}