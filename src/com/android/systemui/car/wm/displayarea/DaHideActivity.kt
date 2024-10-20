/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.systemui.car.wm.displayarea

import android.app.Activity
import android.os.Bundle
import android.view.WindowManager
import android.window.OnBackInvokedCallback

/**
 * This activity is meant to be used as a signal that a display area is hidden. Whenever this
 * activity is at the top, the underlying display area should be considered hidden.
 */
class DaHideActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val callbackPriority = 1000
        window.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
        // Avoid finishing the activity on back pressed to in-turn avoid unwanted transitions.
        onBackInvokedDispatcher.registerOnBackInvokedCallback(
            callbackPriority,
            object : OnBackInvokedCallback {
                override fun onBackInvoked() {
                }
            }
        )
    }
}
