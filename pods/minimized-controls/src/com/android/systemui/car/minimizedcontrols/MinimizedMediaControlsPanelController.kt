/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.systemui.car.minimizedcontrols

import android.view.View
import com.android.car.scalableui.model.PanelControllerMetadata
import com.android.car.scalableui.panel.DecorPanelController
import com.android.systemui.car.wm.scalableui.panel.controller.DecorPanelViewMap
import com.android.systemui.car.wm.scalableui.view.DecorPanelControllerBase
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import javax.inject.Provider

/**
 * Controller for [MinimizedMediaControlsView].
 */
class MinimizedMediaControlsPanelController @AssistedInject constructor(
    @Assisted panelId: String,
    @Assisted metadata: PanelControllerMetadata,
    @DecorPanelViewMap decorPanelViewMap: Map<Class<*>, @JvmSuppressWildcards Provider<View>>
) : DecorPanelControllerBase(panelId, metadata, decorPanelViewMap) {

    private var view: MinimizedMediaControlsView? = null

    override fun getView(): View? {
        val currentView = super.getView()
        if (currentView !== view) {
            view = currentView as? MinimizedMediaControlsView
        }
        return currentView
    }

    @AssistedFactory
    interface Factory : DecorPanelController.Factory<MinimizedMediaControlsPanelController> {
        override fun create(panelId: String, metadata: PanelControllerMetadata):
                MinimizedMediaControlsPanelController
    }
}
