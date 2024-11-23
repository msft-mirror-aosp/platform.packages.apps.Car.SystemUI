/*
 * Copyright (C) 2021 The Android Open Source Project
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

import static android.provider.Settings.Global.DEVELOPMENT_SETTINGS_ENABLED;

import android.annotation.ArrayRes;
import android.annotation.LayoutRes;
import android.content.Context;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.view.View;
import android.view.ViewGroup;

import com.android.settingslib.development.DevelopmentSettingsEnabler;
import com.android.systemui.R;
import com.android.systemui.broadcast.BroadcastDispatcher;
import com.android.systemui.car.CarServiceProvider;
import com.android.systemui.car.qc.SystemUIQCViewController;
import com.android.systemui.car.statusicon.StatusIconController;
import com.android.systemui.car.statusicon.StatusIconGroupContainerController;
import com.android.systemui.car.systembar.BuildInfoUtil;
import com.android.systemui.dagger.qualifiers.Main;
import com.android.systemui.settings.UserTracker;
import com.android.systemui.statusbar.policy.ConfigurationController;
import com.android.systemui.util.settings.GlobalSettings;

import java.util.Map;

import javax.inject.Inject;
import javax.inject.Provider;

/**
 * A controller for Quick Controls Entry Points
 */
public class QuickControlsEntryPointsController extends StatusIconGroupContainerController {
    private final Context mContext;
    private final GlobalSettings mGlobalSettings;
    private final Uri mDevelopEnabled;
    private final ContentObserver mDeveloperSettingsObserver;
    private View mDebugPanelView;

    @Inject
    QuickControlsEntryPointsController(
            Context context,
            UserTracker userTracker,
            @Main Resources resources,
            CarServiceProvider carServiceProvider,
            BroadcastDispatcher broadcastDispatcher,
            ConfigurationController configurationController,
            Provider<SystemUIQCViewController> qcViewControllerProvider,
            Map<Class<?>, Provider<StatusIconController>> iconControllerCreators,
            QCPanelReadOnlyIconsController qcPanelReadOnlyIconsController,
            @Main Handler mainHandler, GlobalSettings globalSettings) {
        super(context, userTracker, carServiceProvider, resources, broadcastDispatcher,
                configurationController, qcViewControllerProvider, iconControllerCreators,
                qcPanelReadOnlyIconsController);
        mContext = context;
        mGlobalSettings = globalSettings;
        mDevelopEnabled = globalSettings.getUriFor(DEVELOPMENT_SETTINGS_ENABLED);
        mDeveloperSettingsObserver = new ContentObserver(mainHandler) {
            @Override
            public void onChange(boolean selfChange, Uri uri) {
                super.onChange(selfChange, uri);
                updateStatus();
            }
        };
    }

    @Override
    @ArrayRes
    protected int getStatusIconControllersStringArray() {
        return R.array.config_quickControlsEntryPointIconControllers;
    }

    @Override
    @LayoutRes
    public int getButtonViewLayout(boolean isVertical) {
        return isVertical ? R.layout.car_qc_entry_points_button_vertical
                : R.layout.car_qc_entry_points_button_horizontal;
    }

    @Override
    public void addIconViews(ViewGroup containerViewGroup, boolean shouldAttachPanel) {
        super.addIconViews(containerViewGroup, shouldAttachPanel);
        setupDebugButton();
    }

    @Override
    public void resetCache() {
        super.resetCache();
        mGlobalSettings.unregisterContentObserver(mDeveloperSettingsObserver);
    }

    private void setupDebugButton() {
        mDebugPanelView = getViewFromClassName(
                mContext.getString(R.string.debug_status_icon_class_name));
        mGlobalSettings.registerContentObserver(mDevelopEnabled, mDeveloperSettingsObserver);
        updateStatus();
    }

    private void updateStatus() {
        if (mDebugPanelView != null) {
            mDebugPanelView.setVisibility(shouldDebugButtonBeVisible() ? View.VISIBLE : View.GONE);
        }
    }

    private boolean shouldDebugButtonBeVisible() {
        return BuildInfoUtil.isDevTesting(mContext)
                && DevelopmentSettingsEnabler.isDevelopmentSettingsEnabled(mContext);
    }
}
