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

import com.android.systemui.car.systembar.SystemBarConfigs.TYPE_NAVIGATION_BAR
import com.android.systemui.car.systembar.SystemBarConfigs.TYPE_STATUS_BAR
import com.android.systemui.car.wm.scalableui.configuration.SystemUiConfigurationProvider
import com.android.systemui.car.wm.scalableui.panel.panelupdates.PanelUpdateConsumer
import com.android.wm.shell.dagger.WMSingleton
import dagger.Lazy
import java.util.Optional
import javax.inject.Inject

/**
 * Provides access to all [SystemUiWindow] objects.
 */
@WMSingleton
class SystemUiWindowProvider @Inject constructor(
    private val consumer: Optional<PanelUpdateConsumer>,
    private val windowFactory: SystemBarWindow.Factory,
    private val configurationProvider: SystemUiConfigurationProvider,
    private val hunWindow: Lazy<Optional<HunWindow>>
) {
    val navBarWindows: List<SystemUiWindow> by lazy { getBarWindows(TYPE_NAVIGATION_BAR) }
    val statusBarWindows: List<SystemUiWindow> by lazy { getBarWindows(TYPE_STATUS_BAR) }
    val systemBarWindows: List<SystemUiWindow> by lazy { statusBarWindows + navBarWindows }

    private fun getBarWindows(type: Int): List<SystemUiWindow> {
        if (consumer.isEmpty) {
            return emptyList()
        }

        val configs = if (type == TYPE_STATUS_BAR) {
            configurationProvider.statusBarConfigs
        } else {
            configurationProvider.navBarConfigs
        }

        return configs.map { config ->
            windowFactory.create(consumer.get(), config)
        }.toList()
    }

    /**
     * @return [HunWindow]
     */
    fun getHunWindow(): Optional<HunWindow> {
        return hunWindow.get()
    }
}
