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

package com.android.systemui.car.systembar;

import static android.provider.Settings.Global.DEVELOPMENT_SETTINGS_ENABLED;

import android.database.ContentObserver;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;

import com.android.settingslib.development.DevelopmentSettingsEnabler;
import com.android.systemui.car.statusicon.StatusIconPanelViewController;
import com.android.systemui.car.systembar.element.CarSystemBarElementController;
import com.android.systemui.car.systembar.element.CarSystemBarElementStateController;
import com.android.systemui.car.systembar.element.CarSystemBarElementStatusBarDisableController;
import com.android.systemui.dagger.qualifiers.Main;
import com.android.systemui.util.settings.GlobalSettings;

import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;
import dagger.assisted.AssistedInject;

import javax.inject.Provider;

/**
 * A controller for the debug panel button.
 */
public class DebugPanelButtonViewController extends CarSystemBarPanelButtonViewController {
    private static final boolean DEBUG = Build.IS_ENG || Build.IS_USERDEBUG;
    private final GlobalSettings mGlobalSettings;
    private final Uri mDevelopEnabled;
    private final ContentObserver mDeveloperSettingsObserver;

    @AssistedInject
    protected DebugPanelButtonViewController(@Assisted CarSystemBarPanelButtonView view,
            CarSystemBarElementStatusBarDisableController disableController,
            CarSystemBarElementStateController stateController,
            Provider<StatusIconPanelViewController.Builder> statusIconPanelBuilder,
            @Main Handler mainHandler, GlobalSettings globalSettings) {
        super(view, disableController, stateController, statusIconPanelBuilder);
        mGlobalSettings = globalSettings;
        mDevelopEnabled = globalSettings.getUriFor(DEVELOPMENT_SETTINGS_ENABLED);
        mDeveloperSettingsObserver = new ContentObserver(mainHandler) {
            @Override
            public void onChange(boolean selfChange, Uri uri) {
                super.onChange(selfChange, uri);
                updateVisibility();
            }
        };
    }

    @AssistedFactory
    public interface Factory extends
            CarSystemBarElementController.Factory<CarSystemBarPanelButtonView,
                    DebugPanelButtonViewController> {
    }

    @Override
    protected void onViewAttached() {
        super.onViewAttached();
        mGlobalSettings.registerContentObserverAsync(mDevelopEnabled, mDeveloperSettingsObserver);
        updateVisibility();
    }

    @Override
    protected void onViewDetached() {
        super.onViewDetached();
        mGlobalSettings.unregisterContentObserverAsync(mDeveloperSettingsObserver);
    }

    @Override
    protected boolean shouldBeVisible() {
        return DEBUG && DevelopmentSettingsEnabler.isDevelopmentSettingsEnabled(getContext());
    }
}
