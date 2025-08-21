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
package com.android.systemui.car.wm.scalableui.configuration

import com.android.car.scalableui.loader.xml.SystemBarTagXmlParser.TYPE_ATTRIBUTE
import com.android.car.scalableui.loader.xml.SystemBarTagXmlParser.TYPE_NAVIGATION
import com.android.car.scalableui.loader.xml.SystemBarTagXmlParser.TYPE_STATUS
import com.android.car.scalableui.panel.PanelPool
import com.android.wm.shell.dagger.WMSingleton
import javax.inject.Inject

/**
 * Provides access to all System UI Configuration objects.
 */
@WMSingleton
class SystemUiConfigurationProvider @Inject constructor(
    private val configurationFactory: SystemBarConfiguration.Factory
) {
    val navBarConfigs: List<SystemBarConfiguration> by lazy {
        getBarConfigs(TYPE_NAVIGATION)
    }
    val statusBarConfigs: List<SystemBarConfiguration> by lazy {
        getBarConfigs(TYPE_STATUS)
    }
    private val statusBarCount: Int by lazy {
        PanelPool.getInstance()
            .getPanels { panel ->
                TYPE_STATUS ==
                        panel.panelControllerMetadata?.configurations?.getString(TYPE_ATTRIBUTE)
            }.size
    }

    /**
     * @return status & navigation [SystemBarConfiguration]s
     */
    fun getSystemBarConfigs(): List<SystemBarConfiguration> {
        return statusBarConfigs + navBarConfigs
    }

    private fun getBarConfigs(type: String): List<SystemBarConfiguration> {
        val indexOffset = if (type == TYPE_STATUS) 0 else statusBarCount
        return PanelPool.getInstance()
            .getPanels { panel ->
                type == panel.panelControllerMetadata?.configurations?.getString(TYPE_ATTRIBUTE)
            }.mapIndexed { index, panel ->
                configurationFactory.create(
                    panel.panelId,
                    index,
                    indexOffset
                )
            }
    }
}
