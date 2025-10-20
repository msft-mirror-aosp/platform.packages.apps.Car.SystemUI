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
import android.util.SparseArray;
import android.view.Display;
import android.view.DisplayInfo;

import com.android.systemui.car.flags.Flag;
import com.android.systemui.car.flags.FlagManager;
import com.android.wm.shell.common.DisplayController;
import com.android.wm.shell.common.DisplayController.OnDisplaysChangedListener;
import com.android.wm.shell.dagger.WMSingleton;
import com.android.wm.shell.sysui.ShellInit;

import javax.annotation.concurrent.GuardedBy;

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
    private final Object mLock = new Object();
    @GuardedBy("mLock")
    private final SparseArray<Configuration> mConfigurationMap;
    private final AutoTaskStackHelper mAutoTaskStackHelper;
    private final FlagManager mFlagManager;
    private final DisplayController mDisplayController;

    public ScalableUIWMInitializer(Context context, ShellInit shellInit,
            ActionConfigReader actionConfigReader, PanelConfigReader panelConfigReader,
            PanelAutoTaskStackTransitionHandlerDelegate delegate,
            ScalableUIDumpsys scalableUIDumpsys, DisplayController displayController,
            AutoTaskStackHelper autoTaskStackHelper, FlagManager flagManager) {
        shellInit.addInitCallback(this::onInit, this);
        mActionConfigReader = actionConfigReader;
        mPanelConfigReader = panelConfigReader;
        mPanelAutoTaskStackTransitionHandlerDelegate = delegate;
        mScalableUIDumpsys = scalableUIDumpsys;
        mAutoTaskStackHelper = autoTaskStackHelper;
        mFlagManager = flagManager;
        mDisplayController = displayController;
        synchronized (mLock) {
            mConfigurationMap = new SparseArray<>();
            mConfigurationMap.put(context.getDisplayId(),
                    new Configuration(context.getResources().getConfiguration()));
        }
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
        synchronized (mLock) {
            if (!mFlagManager.isEnabled(Flag.ScalableUiHandleConfigurationChange)) {
                return;
            }

            if (!isSupportedDisplay(displayId)) {
                debugLog("Skip reload for display " + displayId);
                return;
            }
            Configuration prevConfig = mConfigurationMap.get(displayId, new Configuration());
            debugLog(displayId + ", onDisplayChange was" + prevConfig);
            debugLog(displayId + " onDisplayChange change to" + newConfig);

            int delta = prevConfig.updateFrom(newConfig);
            mConfigurationMap.put(displayId, prevConfig);
            if (shouldReload(delta, displayId)) {
                // The DisplayController#onDisplayConfigurationChanged callback can occur
                // before the application context's resources are updated. Therefore, a new
                // context must be created from the provided provided to ensure that
                // the correct resources are loaded.
                mAutoTaskStackHelper.reloadTaskConfigs();
                mPanelConfigReader.reloadConfig(newConfig);
                mActionConfigReader.init();
                mScalableUIDumpsys.init();
            }
        }
    }

    private boolean shouldReload(int diff, int displayId) {
        debugLog("onDisplayChange delta" + diff);
        synchronized (mLock) {
            if ((diff & ActivityInfo.CONFIG_ORIENTATION) != 0) {
                debugLog("Orientation has changed!" + mConfigurationMap.get(displayId).orientation);
                return true;
            }
            if ((diff & ActivityInfo.CONFIG_ASSETS_PATHS) != 0) {
                debugLog("Asset paths have changed!" + mConfigurationMap.get(displayId).assetsSeq);
                return true;
            }
            return false;
        }
    }

    /**
     * Check if the display is supported for scalableUI
     */
    private boolean isSupportedDisplay(int displayId) {
        Display display = mDisplayController.getDisplay(displayId);
        DisplayInfo displayInfo = new DisplayInfo();
        display.getDisplayInfo(displayInfo);
        return displayInfo.type != Display.TYPE_VIRTUAL;
    }

    private void debugLog(String logMsg) {
        if (DEBUG) {
            Log.d(TAG, logMsg);
        }
    }
}