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
package com.android.systemui.car.wm.scalableui.systemwindow

import com.android.car.scalableui.loader.xml.SystemBarTagXmlParser
import com.android.systemui.car.systembar.CarSystemBarController
import com.android.systemui.car.systembar.CarSystemBarController.SystemBarSide
import com.android.systemui.car.wm.scalableui.systemwindow.SystemBarWindow.SystemBarConfiguration
import com.android.wm.shell.dagger.WMSingleton
import dagger.Lazy
import java.util.Optional
import javax.inject.Inject
import javax.inject.Named

/**
 * Provides access to all System UI Configuration objects.
 */
@WMSingleton
class SystemUiConfigurationProvider @Inject constructor(
    @Named(SystemBarTagXmlParser.SYSTEM_BAR_PANEL_LEFT_ID)
    leftSystemBarConfig: Lazy<Optional<SystemBarConfiguration>>,
    @Named(SystemBarTagXmlParser.SYSTEM_BAR_PANEL_TOP_ID)
    topSystemBarConfig: Lazy<Optional<SystemBarConfiguration>>,
    @Named(SystemBarTagXmlParser.SYSTEM_BAR_PANEL_RIGHT_ID)
    rightSystemBarConfig: Lazy<Optional<SystemBarConfiguration>>,
    @Named(SystemBarTagXmlParser.SYSTEM_BAR_PANEL_BOTTOM_ID)
    bottomSystemBarConfig: Lazy<Optional<SystemBarConfiguration>>
) {
    private val mSystemBarConfigMap: MutableMap<Int, Lazy<Optional<SystemBarConfiguration>>> =
        HashMap()

    init {
        mSystemBarConfigMap[CarSystemBarController.LEFT] = leftSystemBarConfig
        mSystemBarConfigMap[CarSystemBarController.TOP] = topSystemBarConfig
        mSystemBarConfigMap[CarSystemBarController.RIGHT] = rightSystemBarConfig
        mSystemBarConfigMap[CarSystemBarController.BOTTOM] = bottomSystemBarConfig
    }

    /**
     * @return [SystemBarWindow.SystemBarConfiguration] according to
     * [CarSystemBarController.SystemBarSide]
     */
    fun getSystemBarConfig(
        side: @SystemBarSide Int
    ): Optional<SystemBarConfiguration> {
        return mSystemBarConfigMap[side]?.get() ?: Optional.empty()
    }
}
