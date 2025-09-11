/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.systemui.car.wm.scalableui.systemwindow

import android.content.Context
import android.content.res.Resources
import android.hardware.display.DisplayManager
import android.testing.TestableContext
import android.testing.TestableLooper.RunWithLooper
import android.view.Display
import android.view.DisplayAdjustments
import android.view.View
import android.view.WindowManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.CarSysuiTestCase
import com.android.systemui.car.CarSystemUiTest
import com.android.systemui.car.wm.scalableui.EventDispatcher
import com.android.systemui.car.wm.scalableui.panel.panelupdates.PanelUpdateConsumer
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

@CarSystemUiTest
@RunWith(AndroidJUnit4::class)
@RunWithLooper
@SmallTest
class SystemUiWindowBaseTest : CarSysuiTestCase() {

    private val display = mock<Display> {
        on { displayAdjustments } doReturn DisplayAdjustments()
    }
    private val resources = mock<Resources>()
    private val windowManager = mock<WindowManager>()
    private val panelUpdateConsumer = mock<PanelUpdateConsumer>()
    private val eventDispatcher = mock<EventDispatcher>()
    private val view = mock<View>()
    private val displayManager = mock<DisplayManager> {
        on { getDisplay(TEST_DISPLAY_ID) } doReturn display
    }

    private lateinit var systemUiWindowBase: SystemUiWindowBase
    private lateinit var testableContext: TestableContext

    private val layoutParams = WindowManager.LayoutParams()

    @Before
    fun setUp() {
        testableContext = object : TestableContext(mContext) {
            override fun getBasePackageName(): String {
                return "test.pkg"
            }
            override fun createDisplayContext(display: Display): Context {
                return this
            }
            override fun getDisplay(): Display {
                return this@SystemUiWindowBaseTest.display
            }
        }
        testableContext.addMockSystemService(WindowManager::class.java, windowManager)
        testableContext.addMockSystemService(DisplayManager::class.java, displayManager)

        systemUiWindowBase = object : SystemUiWindowBase(
            testableContext,
            displayManager,
            panelUpdateConsumer,
            eventDispatcher,
            TEST_ID,
            TEST_DISPLAY_ID
        ) {
            override fun getLayoutParams(): WindowManager.LayoutParams {
                return this@SystemUiWindowBaseTest.layoutParams
            }
        }
    }

    @Test
    fun setRootView_addsViewToWindowManager() {
        systemUiWindowBase.setRootView(view, layoutParams)

        verify(windowManager).addView(view, layoutParams)
    }

    @Test
    fun removeRootView_removesViewFromWindowManager() {
        systemUiWindowBase.setRootView(view, layoutParams)
        systemUiWindowBase.removeRootView()

        verify(windowManager).removeView(view)
    }

    @Test
    fun removeRootViewImmediate_removesViewImmediateFromWindowManager() {
        systemUiWindowBase.setRootView(view, layoutParams)
        systemUiWindowBase.removeRootViewImmediate()

        verify(windowManager).removeViewImmediate(view)
    }

    companion object {
        private const val TEST_ID = "test_id"
        private const val TEST_DISPLAY_ID = 1
    }
}
