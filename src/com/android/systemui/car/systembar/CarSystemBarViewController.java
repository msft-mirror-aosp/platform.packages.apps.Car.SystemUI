/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.systemui.car.systembar;

import android.content.Context;

import com.android.car.ui.FocusParkingView;
import com.android.systemui.car.systembar.element.CarSystemBarElementInitializer;
import com.android.systemui.settings.UserTracker;
import com.android.systemui.util.ViewController;

import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;
import dagger.assisted.AssistedInject;

/**
 * A controller for initializing the CarSystemBarView instances.
 */
public class CarSystemBarViewController extends ViewController<CarSystemBarView> {

    private final Context mContext;
    private final UserTracker mUserTracker;
    private final CarSystemBarElementInitializer mCarSystemBarElementInitializer;

    @AssistedInject
    public CarSystemBarViewController(Context context,
            UserTracker userTracker,
            CarSystemBarElementInitializer elementInitializer,
            @Assisted CarSystemBarView systemBarView) {
        super(systemBarView);

        mContext = context;
        mUserTracker = userTracker;
        mCarSystemBarElementInitializer = elementInitializer;
    }

    @Override
    protected void onInit() {
        mView.setupSystemBarButtons(mUserTracker);
        mCarSystemBarElementInitializer.initializeCarSystemBarElements(mView);

        // Include a FocusParkingView at the beginning. The rotary controller "parks" the focus here
        // when the user navigates to another window. This is also used to prevent wrap-around.
        mView.addView(new FocusParkingView(mContext), 0);
    }

    @Override
    protected void onViewAttached() {
    }

    @Override
    protected void onViewDetached() {
    }

    @AssistedFactory
    public interface Factory {
        /** Create instance of CarSystemBarViewController for CarSystemBarView */
        CarSystemBarViewController create(CarSystemBarView view);
    }
}
