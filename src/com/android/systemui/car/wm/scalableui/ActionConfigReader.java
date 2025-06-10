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

import static com.android.systemui.car.Flags.scalableUiActions;
import static com.android.systemui.car.Flags.scalableUiDesignCompose;

import android.app.ActivityManager;
import android.content.Context;
import android.content.res.Resources;
import android.os.Build;
import android.util.Log;

import com.android.car.scalableui.loader.xml.XmlModelLoader;
import com.android.car.scalableui.manager.ActionManager;
import com.android.car.scalableui.model.Action;
import com.android.systemui.R;
import com.android.wm.shell.dagger.WMSingleton;

import java.util.List;

@WMSingleton
public class ActionConfigReader {
    private static final String TAG = ActionConfigReader.class.getSimpleName();
    private static final boolean DEBUG = Build.IS_DEBUGGABLE;
    private final Context mContext;

    public ActionConfigReader(Context context) {
        debugLog("ActionConfig initialized user: " + ActivityManager.getCurrentUser());
        mContext = context;
    }

    /**
     * Init the Actions.
     */
    public void init() {
        if (!scalableUiActions()) {
            return;
        }
        if (scalableUiDesignCompose()) {
            loadFromDcf();
        } else {
            loadFromXml();
        }
    }

    private void loadFromDcf() {
        // TODO(424263939): Load actions from DCF.
        Log.w(TAG, "Loading actions from DCF is not supported yet!");
    }

    private void loadFromXml() {
        debugLog("Loading actions from XML");
        Resources res = mContext.getResources();
        XmlModelLoader loader = new XmlModelLoader(mContext);
        List<Action> actions = loader.createActions(R.xml.scalable_ui_actions);
        ActionManager.setActions(actions);
    }

    private void debugLog(String logMsg) {
        if (DEBUG) {
            Log.d(TAG, logMsg);
        }
    }
}
