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

package com.android.systemui.car.wm.scalableui;

import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.os.Build;
import android.util.Log;

import com.android.systemui.car.flags.Flag;
import com.android.systemui.car.flags.FlagManager;
import com.android.wm.shell.common.DisplayController;
import com.android.wm.shell.common.DisplayController.OnDisplaysChangedListener;
import com.android.wm.shell.dagger.WMSingleton;
import com.android.wm.shell.sysui.ShellInit;

/**
 * Class to include ScalableUI constructs that need to be initialized on startup.
 */
@WMSingleton
public class ScalableUIWMInitializer implements OnDisplaysChangedListener {
    private static final String TAG = ScalableUIWMInitializer.class.getSimpleName();
    private static final boolean DEBUG = Build.IS_DEBUGGABLE;
    private final ActionConfigReader mActionConfigReader;
    private final PanelConfigReader mPanelConfigReader;
    private final PanelAutoTaskStackTransitionHandlerDelegate
            mPanelAutoTaskStackTransitionHandlerDelegate;
    private final ScalableUIDumpsys mScalableUIDumpsys;
    private final Configuration mConfiguration;
    private final AutoTaskStackHelper mAutoTaskStackHelper;
    private final FlagManager mFlagManager;
    private final DisplayController mDisplayController;

    public ScalableUIWMInitializer(
            Context context,
            ShellInit shellInit,
            ActionConfigReader actionConfigReader,
            PanelConfigReader panelConfigReader,
            PanelAutoTaskStackTransitionHandlerDelegate delegate,
            ScalableUIDumpsys scalableUIDumpsys,
            DisplayController displayController,
            AutoTaskStackHelper autoTaskStackHelper,
            FlagManager flagManager
    ) {
        shellInit.addInitCallback(this::onInit, this);
        mActionConfigReader = actionConfigReader;
        mPanelConfigReader = panelConfigReader;
        mPanelAutoTaskStackTransitionHandlerDelegate = delegate;
        mScalableUIDumpsys = scalableUIDumpsys;
        mConfiguration = new Configuration(context.getResources().getConfiguration());
        mAutoTaskStackHelper = autoTaskStackHelper;
        mFlagManager = flagManager;
        mDisplayController = displayController;
    }

    private void onInit() {
        mPanelAutoTaskStackTransitionHandlerDelegate.init();
        mPanelConfigReader.init();
        mActionConfigReader.init();
        mScalableUIDumpsys.init();
        mDisplayController.addDisplayWindowListener(this);
    }

    @Override
    public void onDisplayConfigurationChanged(int displayId, Configuration newConfig) {
        OnDisplaysChangedListener.super.onDisplayConfigurationChanged(displayId, newConfig);
        debugLog("onDisplayChange was" + mConfiguration);
        debugLog("onDisplayChange change to" + newConfig);
        if (!mFlagManager.isEnabled(Flag.ScalableUiHandleConfigurationChange)) {
            return;
        }
        if (shouldReload(mConfiguration.updateFrom(newConfig))) {
            mAutoTaskStackHelper.reloadTaskConfigs();
            mPanelConfigReader.reloadConfig();
            mActionConfigReader.init();
            mScalableUIDumpsys.init();
        }
    }

    private boolean shouldReload(int diff) {
        debugLog("onDisplayChange delta" + diff);
        if ((diff & ActivityInfo.CONFIG_ORIENTATION) != 0) {
            debugLog("Orientation has changed!" + mConfiguration.orientation);
            return true;
        }

        if ((diff & ActivityInfo.CONFIG_ASSETS_PATHS) != 0) {
            debugLog("Asset paths have changed!" + mConfiguration.assetsSeq);
            return true;
        }

        return false;
    }

    private void debugLog(String logMsg) {
        if (DEBUG) {
            Log.d(TAG, logMsg);
        }
    }
}
