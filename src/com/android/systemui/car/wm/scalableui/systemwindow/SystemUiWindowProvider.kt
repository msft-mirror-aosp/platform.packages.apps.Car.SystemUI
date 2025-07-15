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
import com.android.wm.shell.dagger.WMSingleton
import dagger.Lazy
import java.util.Optional
import javax.inject.Inject
import javax.inject.Named

/**
 * Provides access to all [SystemUiWindow] objects.
 */
@WMSingleton
class SystemUiWindowProvider @Inject constructor(
    @Named(SystemBarTagXmlParser.SYSTEM_BAR_PANEL_LEFT_ID)
    leftSystemBarWindow: Lazy<Optional<SystemBarWindow>>,
    @Named(SystemBarTagXmlParser.SYSTEM_BAR_PANEL_TOP_ID)
    topSystemBarWindow: Lazy<Optional<SystemBarWindow>>,
    @Named(SystemBarTagXmlParser.SYSTEM_BAR_PANEL_RIGHT_ID)
    rightSystemBarWindow: Lazy<Optional<SystemBarWindow>>,
    @Named(SystemBarTagXmlParser.SYSTEM_BAR_PANEL_BOTTOM_ID)
    bottomSystemBarWindow: Lazy<Optional<SystemBarWindow>>
) {
    private val mSystemBarWindowMap: MutableMap<Int, Lazy<Optional<SystemBarWindow>>> =
        HashMap()

    init {
        mSystemBarWindowMap[CarSystemBarController.LEFT] = leftSystemBarWindow
        mSystemBarWindowMap[CarSystemBarController.TOP] = topSystemBarWindow
        mSystemBarWindowMap[CarSystemBarController.RIGHT] = rightSystemBarWindow
        mSystemBarWindowMap[CarSystemBarController.BOTTOM] = bottomSystemBarWindow
    }

    /**
     * @return [SystemBarWindow] according to [CarSystemBarController.SystemBarSide]
     */
    fun getSystemBarWindow(
        side: @SystemBarSide Int
    ): Optional<SystemBarWindow> {
        return mSystemBarWindowMap[side]?.get() ?: Optional.empty()
    }
}
