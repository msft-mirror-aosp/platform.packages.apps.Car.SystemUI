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
import android.graphics.Insets
import android.graphics.Rect
import android.view.Display
import android.view.View
import android.view.WindowManager
import com.android.systemui.car.wm.scalableui.EventDispatcher
import com.android.systemui.car.wm.scalableui.panel.panelupdates.PanelUpdateConsumer
import com.android.systemui.car.wm.scalableui.systemevents.SystemEventConstants.HIDE_EVENT_PREFIX
import com.android.systemui.car.wm.scalableui.systemevents.SystemEventConstants.SHOW_EVENT_PREFIX

/**
 * A base class for [SystemUiWindow] implementations, providing common functionality.
 */
abstract class SystemUiWindowBase(
    context: Context,
    protected val panelUpdateConsumer: PanelUpdateConsumer,
    protected val eventDispatcher: EventDispatcher,
    protected var id: String
) : SystemUiWindow {
    protected val display: Display = context.display
    protected val displayContext: Context = context.createDisplayContext(display)
    protected val windowManager: WindowManager = displayContext.getSystemService(
        WindowManager::class.java
    )
    protected val displayMetrics = displayContext.resources.displayMetrics
    protected var _rootView: View? = null

    init {
        panelUpdateConsumer.registerCallback(id, object : PanelUpdateConsumer.PanelUpdateCallback {
            override fun onBoundsChange(panelId: String, bounds: Rect) {
                _rootView?.let {
                    windowManager.updateViewLayout(it, getLayoutParams())
                }
            }
        })
    }

    override fun getBounds(): Rect? {
        return panelUpdateConsumer.getBounds(id)
    }

    override fun setRootView(view: View, layoutParams: WindowManager.LayoutParams?) {
        if (_rootView != null) {
            removeRootView()
        }
        this._rootView = view
        windowManager.addView(this._rootView, layoutParams)
    }

    override fun removeRootView() {
        _rootView?.let {
            windowManager.removeView(it)
            _rootView = null
        }
    }

    override fun removeRootViewImmediate() {
        _rootView?.let {
            windowManager.removeViewImmediate(it)
            _rootView = null
        }
    }

    override fun isVisible(): Boolean {
        return panelUpdateConsumer.isVisible(id) ?: false
    }

    override fun hide() {
        eventDispatcher.executeEvent(HIDE_EVENT_PREFIX + id)
    }

    override fun show() {
        eventDispatcher.executeEvent(SHOW_EVENT_PREFIX + id)
    }

    override fun getHeight(): Int {
        return panelUpdateConsumer.getBounds(id)?.height() ?: 0
    }

    override fun getWidth(): Int {
        return panelUpdateConsumer.getBounds(id)?.width() ?: 0
    }

    override fun getAlpha(): Float {
        return panelUpdateConsumer.getAlpha(id) ?: 1f
    }

    override fun getInsets(): Insets? {
        return panelUpdateConsumer.getInsets(id)
    }

    override fun getCornerRadius(): Int {
        return panelUpdateConsumer.getCornerRadius(id) ?: 0
    }

    override fun addCallback(callback: SystemUiWindow.WindowUpdateCallback) {
        panelUpdateConsumer.registerCallback(id, callback)
    }

    override fun removeCallback(callback: SystemUiWindow.WindowUpdateCallback) {
        panelUpdateConsumer.unregisterCallback(id, callback)
    }
}
