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
package com.android.systemui.car.wm.scalableui.panel.controller;

import android.annotation.NonNull;
import android.content.Context;
import android.content.Intent;

import com.android.car.scalableui.model.PanelControllerMetadata;
import com.android.car.tos.TosHelper;
import com.android.systemui.R;
import com.android.systemui.car.wm.scalableui.panel.PanelUtils;

public final class MapsPanelController extends BaseTaskPanelController {
    private static final String TAG = MapsPanelController.class.getSimpleName();

    public MapsPanelController(@NonNull Context context,
            @NonNull PanelControllerMetadata panelControllerMetadata,
            @NonNull PanelUtils panelUtils) {
        super(context, panelControllerMetadata, panelUtils);
    }

    @Override
    public Intent getDefaultComponent() {
        Intent mapIntent = super.getDefaultComponent();
        Intent result = TosHelper.maybeReplaceWithTosMapIntent(mContext, mapIntent,
                R.string.config_tosMapIntent);
        logIfDebuggable(TAG + ", getDefaultComponent =  " + result);
        return result;
    }
}
