/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.systemui.car.statusicon.ui;

import android.content.Context;
import android.content.res.Resources;

import com.android.systemui.R;
import com.android.systemui.car.statusicon.StatusIconController;
import com.android.systemui.car.statusicon.StatusIconGroupContainerController;
import com.android.systemui.dagger.qualifiers.Main;

import java.util.Map;

import javax.inject.Inject;
import javax.inject.Provider;

/**
 *  A Controller for a group of read-only status icons in quick controls panel.
 */
public class QCPanelReadOnlyIconsController extends StatusIconGroupContainerController {

    @Inject
    public QCPanelReadOnlyIconsController(Context context,
            @Main Resources resources,
            Map<Class<?>, Provider<StatusIconController>> iconControllerCreators) {
        // set panelControllerBuilderProvider to null to avoid dependency cycle (icons within the
        // panel should not need to build another panel)
        super(context, resources, iconControllerCreators, null);
    }

    @Override
    protected int getStatusIconControllersStringArray() {
        return R.array.config_qcPanelReadOnlyIconControllers;
    }
}
