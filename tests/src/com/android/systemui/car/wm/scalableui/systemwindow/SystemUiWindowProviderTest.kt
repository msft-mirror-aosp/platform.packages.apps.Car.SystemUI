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

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.CarSysuiTestCase
import com.android.systemui.car.CarSystemUiTest
import com.android.systemui.car.wm.scalableui.configuration.SystemBarConfiguration
import com.android.systemui.car.wm.scalableui.configuration.SystemUiConfigurationProvider
import com.android.systemui.car.wm.scalableui.panel.panelupdates.PanelUpdateConsumer
import com.google.common.truth.Truth.assertThat
import dagger.Lazy
import java.util.Optional
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

@CarSystemUiTest
@RunWith(AndroidJUnit4::class)
@SmallTest
class SystemUiWindowProviderTest : CarSysuiTestCase() {

    private val mPanelUpdateConsumer = mock<PanelUpdateConsumer>()
    private val mMockConsumer = mock<Optional<PanelUpdateConsumer>> {
        on { isEmpty } doReturn false
        on { get() } doReturn mPanelUpdateConsumer
    }
    private val mMockHunWindow = mock<HunWindow>()
    private val mMockStatusBarConfiguration = mock<SystemBarConfiguration>()
    private val mMockNavBarConfiguration = mock<SystemBarConfiguration>()
    private val mMockStatusBarWindow = mock<SystemBarWindowImpl>()
    private val mMockNavBarWindow = mock<SystemBarWindowImpl>()
    private val mMockWindowFactory = mock<SystemBarWindowImpl.Factory> {
        on {
            create(
                mPanelUpdateConsumer,
                mMockStatusBarConfiguration
            )
        } doReturn mMockStatusBarWindow
        on { create(mPanelUpdateConsumer, mMockNavBarConfiguration) } doReturn mMockNavBarWindow
    }
    private val mMockConfigurationProvider = mock<SystemUiConfigurationProvider> {
        on { statusBarConfigs } doReturn listOf(mMockStatusBarConfiguration)
        on { navBarConfigs } doReturn listOf(mMockNavBarConfiguration)
    }

    private lateinit var mProvider: SystemUiWindowProvider

    @Before
    fun setUp() {
        mProvider = SystemUiWindowProvider(
            mMockConsumer,
            mMockWindowFactory,
            mMockConfigurationProvider,
            Lazy { Optional.of(mMockHunWindow) }
        )
    }

    @Test
    fun systemBarWindows_returnsAllSystemBarWindows() {
        val windows = mProvider.systemBarWindows
        assertThat(windows).containsExactly(mMockStatusBarWindow, mMockNavBarWindow)
    }

    @Test
    fun statusBarWindows_returnsOnlyStatusBarWindows() {
        val windows = mProvider.statusBarWindows
        assertThat(windows).containsExactly(mMockStatusBarWindow)
    }

    @Test
    fun navigationBarWindows_returnsOnlyNavBarWindows() {
        val windows = mProvider.navBarWindows
        assertThat(windows).containsExactly(mMockNavBarWindow)
    }

    @Test
    fun getHunWindow_returnsHunWindow() {
        val hunWindow = mProvider.getHunWindow()
        assertThat(hunWindow.get()).isEqualTo(mMockHunWindow)
    }
}
