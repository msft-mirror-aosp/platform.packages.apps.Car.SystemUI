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
import com.android.car.scalableui.manager.StateManager
import com.android.car.scalableui.model.PanelState
import com.android.car.scalableui.model.Variant
import com.android.car.scalableui.panel.Panel
import com.android.car.scalableui.panel.PanelPool
import com.android.systemui.CarSysuiTestCase
import com.android.systemui.car.CarSystemUiTest
import com.android.systemui.car.wm.scalableui.configuration.SystemBarConfiguration
import com.android.systemui.car.wm.scalableui.configuration.SystemUiConfigurationProvider
import com.android.systemui.car.wm.scalableui.panel.panelupdates.PanelUpdateConsumer
import com.google.common.truth.Truth.assertThat
import dagger.Lazy
import java.util.Optional
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
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
    private val mMockStatusBarConfiguration = mock<SystemBarConfiguration> {
        on { name } doReturn "TestStatusBar"
    }
    private val mMockNavBarConfiguration = mock<SystemBarConfiguration> {
        on { name } doReturn "TestNavBar"
    }
    private val mMockStatusBarWindow = mock<SystemBarWindowImpl>()
    private val mMockNavBarWindow = mock<SystemBarWindowImpl>()
    private val mMockWindowFactory = mock<SystemBarWindowImpl.Factory> {
        on {
            create(
                mPanelUpdateConsumer,
                mMockStatusBarConfiguration,
                TEST_DISPLAY_ID
            )
        } doReturn mMockStatusBarWindow
        on {
            create(mPanelUpdateConsumer, mMockNavBarConfiguration, TEST_DISPLAY_ID)
        } doReturn mMockNavBarWindow
    }
    private val mMockConfigurationProvider = mock<SystemUiConfigurationProvider> {
        on { statusBarConfigs } doReturn listOf(mMockStatusBarConfiguration)
        on { navBarConfigs } doReturn listOf(mMockNavBarConfiguration)
    }

    private lateinit var mProvider: SystemUiWindowProvider

    private companion object {
        const val TEST_DISPLAY_ID = 0
        val mockVariant = mock<Variant>()
        val mockPanel = mock<Panel>()
        val mockDelegate = mock<PanelPool.PanelCreatorDelegate> {
            on { createPanel(any(), any()) } doReturn mockPanel
        }

        val statusBarPanelState = mock<PanelState> {
            on { getId() } doReturn "TestStatusBar"
            on { getDisplayId() } doReturn TEST_DISPLAY_ID
            on { getCurrentVariant() } doReturn mockVariant
        }

        val navBarPanelState = mock<PanelState> {
            on { getId() } doReturn "TestNavBar"
            on { getDisplayId() } doReturn TEST_DISPLAY_ID
            on { getCurrentVariant() } doReturn mockVariant
        }
    }

    @Before
    fun setUp() {
        PanelPool.getInstance().setDelegate(mockDelegate)
        StateManager.addState(statusBarPanelState)
        StateManager.addState(navBarPanelState)

        mProvider = SystemUiWindowProvider(
            mMockConsumer,
            mMockWindowFactory,
            mMockConfigurationProvider,
            Lazy { Optional.of(mMockHunWindow) }
        )
    }

    @After
    fun tearDown() {
        StateManager.clearStates()
        PanelPool.getInstance().clearPanels()
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
