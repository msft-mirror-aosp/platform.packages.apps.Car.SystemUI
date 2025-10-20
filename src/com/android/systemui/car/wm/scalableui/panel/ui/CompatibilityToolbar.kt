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
package com.android.systemui.car.wm.scalableui.panel.ui

import android.content.Context
import android.util.AttributeSet
import android.widget.Button
import android.widget.LinearLayout
import com.android.systemui.R

/**
 * A view that displays a toolbar with controls for a TaskPanel.
 *
 * This view inflates the `R.layout.tasktoolbar` layout and provides callbacks for actions like
 * navigating back, toggling fullscreen, changing the aspect ratio, and closing the task.
 */
class CompatibilityToolbar
@JvmOverloads
constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0
) : LinearLayout(context, attrs, defStyleAttr, defStyleRes) {
    val backButton: Button?
    val fullscreenButton: Button?
    val aspectRatioButton: Button?
    val closeButton: Button?

    /** A listener to be invoked when the back button is clicked. */
    var onBackButtonClick: (() -> Unit)? = null

    /** A listener to be invoked when the fullscreen button is clicked. */
    var onFullscreenButtonClick: (() -> Unit)? = null

    /** A listener to be invoked when the aspect ratio button is clicked. */
    var onAspectRatioButtonClick: (() -> Unit)? = null

    /** A listener to be invoked when the close button is clicked. */
    var onCloseButtonClick: (() -> Unit)? = null

    init {
        inflate(context, R.layout.tasktoolbar, this)

        backButton = findViewById<Button>(R.id.back_button)?.apply {
            setOnClickListener { onBackButtonClick?.invoke() }
        }
        fullscreenButton = findViewById<Button>(R.id.fullscreen_button)?.apply {
            if (context.resources.getBoolean(R.bool.show_toolbar_fullscreen_button)) {
                setOnClickListener { onFullscreenButtonClick?.invoke() }
            } else {
                visibility = GONE
            }
        }
        aspectRatioButton = findViewById<Button>(R.id.aspect_ratio)?.apply {
            setOnClickListener { onAspectRatioButtonClick?.invoke() }
        }
        closeButton = findViewById<Button>(R.id.close_window)?.apply {
            setOnClickListener { onCloseButtonClick?.invoke() }
        }
        CompatibilityToolbarUiState.logIfDebuggable("Toolbar init")
    }
}
