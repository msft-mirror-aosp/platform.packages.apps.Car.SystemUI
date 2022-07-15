/*
 * Copyright (C) 2018 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.systemui;

import android.os.UserHandle;

import com.android.systemui.dagger.GlobalRootComponent;
import com.android.systemui.dagger.SysUIComponent;
import com.android.systemui.dagger.WMComponent;
import com.android.systemui.wmshell.CarWMComponent;

import java.util.Optional;

/**
 * Class factory to provide car specific SystemUI components.
 */
public class CarSystemUIFactory extends SystemUIFactory {
    @Override
    protected GlobalRootComponent.Builder getGlobalRootComponentBuilder() {
        return DaggerCarGlobalRootComponent.builder();
    }

    @Override
    protected SysUIComponent.Builder prepareSysUIComponentBuilder(
            SysUIComponent.Builder sysUIBuilder, WMComponent wm) {
        CarWMComponent carWm = (CarWMComponent) wm;
        boolean isSystemUser = UserHandle.myUserId() == UserHandle.USER_SYSTEM;
        return ((CarSysUIComponent.Builder) sysUIBuilder).setRootTaskDisplayAreaOrganizer(
                isSystemUser ? Optional.of(carWm.getRootTaskDisplayAreaOrganizer())
                        : Optional.empty());
    }
}
