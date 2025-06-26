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

package com.android.systemui;

import static android.car.CarOccupantZoneManager.DISPLAY_TYPE_MAIN;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.car.Car;
import android.car.CarOccupantZoneManager;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.UserHandle;
import android.view.Display;
import android.view.WindowManager;

import com.android.car.oem.tokens.Token;
import com.android.systemui.application.impl.SystemUIApplicationImpl;
import com.android.systemui.car.users.CarSystemUIUserUtil;

/**
 * Application class for CarSystemUI.
 */
public class CarSystemUIApplication extends SystemUIApplicationImpl {

    private boolean mIsVisibleBackgroundUserSysUI;

    @Override
    public void onCreate() {
        mIsVisibleBackgroundUserSysUI = CarSystemUIUserUtil.isSecondaryMUMDSystemUI();
        super.onCreate();
        if (mIsVisibleBackgroundUserSysUI) {
            Car car = Car.createCar(this);
            if (car == null) {
                return;
            }
            CarOccupantZoneManager manager = (CarOccupantZoneManager) car.getCarManager(
                    Car.CAR_OCCUPANT_ZONE_SERVICE);
            if (manager != null) {
                CarOccupantZoneManager.OccupantZoneInfo info = manager.getMyOccupantZone();
                if (info != null) {
                    Display display = manager.getDisplayForOccupant(info, DISPLAY_TYPE_MAIN);
                    if (display != null) {
                        updateDisplay(display.getDisplayId());
                        startSystemUserServicesIfNeeded();
                    }
                }
            }
            car.disconnect();
        }
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        Token.applyOemTokenStyle(this);
        super.onConfigurationChanged(newConfig);
    }

    @Override
    protected boolean shouldStartSystemUserServices() {
        if (mIsVisibleBackgroundUserSysUI) {
            // visible background user SystemUI instances should start the same services as the
            // normal system user SystemUI instance.
            return true;
        }
        return super.shouldStartSystemUserServices();
    }

    @Override
    protected boolean shouldStartSecondaryUserServices() {
        if (mIsVisibleBackgroundUserSysUI) {
            // visible background user SystemUI instances should start the same services as the
            // normal system user SystemUI instance - this already includes the secondary user
            // services.
            return false;
        }
        return super.shouldStartSecondaryUserServices();
    }

    @Override
    public void attachBaseContext(Context base) {
        Token.applyOemTokenStyle(base);
        base.getTheme().applyStyle(R.style.CarSystemUIThemeOverlay, true);
        super.attachBaseContext(base);
    }

    @Override
    @NonNull
    public Context createContextAsUser(UserHandle user, @CreatePackageOptions int flags) {
        Context context = super.createContextAsUser(user, flags);
        context.getTheme().setTo(getTheme());
        context.getTheme().rebase();
        return context;
    }

    @Override
    @NonNull
    public Context createWindowContext(@WindowManager.LayoutParams.WindowType int type,
            @Nullable Bundle options) {
        Context context = super.createWindowContext(type, options);
        context.getTheme().setTo(getTheme());
        context.getTheme().rebase();
        return context;
    }

    @Override
    @NonNull
    public Context createWindowContext(@NonNull Display display, int type,
            @Nullable Bundle options) {
        Context context = super.createWindowContext(display, type, options);
        context.getTheme().setTo(getTheme());
        context.getTheme().rebase();
        return context;
    }

    @Override
    public Context createConfigurationContext(Configuration overrideConfiguration) {
        Context context = super.createConfigurationContext(overrideConfiguration);
        context.getTheme().setTo(getTheme());
        context.getTheme().rebase();
        return context;
    }
}
