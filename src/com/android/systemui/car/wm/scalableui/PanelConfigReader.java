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

import android.app.ActivityManager;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.os.Build;
import android.util.Log;

import com.android.car.internal.dep.Trace;
import com.android.car.scalableui.manager.StateManager;
import com.android.car.scalableui.panel.PanelPool;
import com.android.systemui.R;
import com.android.systemui.car.wm.scalableui.panel.TaskPanel;
import com.android.wm.shell.dagger.WMSingleton;

import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;

@WMSingleton
public class PanelConfigReader {
    private static final String TAG = PanelConfigReader.class.getSimpleName();
    private static final boolean DEBUG = Build.IS_DEBUGGABLE;
    private final Context mContext;
    private final TaskPanel.Factory mTaskPanelFactory;

    public PanelConfigReader(Context context, TaskPanel.Factory taskPanelFactory) {
        if (DEBUG) {
            Log.d(TAG, "PanelConfig initialized user: " + ActivityManager.getCurrentUser());
        }
        mContext = context;
        mTaskPanelFactory = taskPanelFactory;
    }

    /**
     * Init the Panels.
     */
    public void init() {
        PanelPool.getInstance().clearPanels();
        PanelPool.getInstance().setDelegate(id -> mTaskPanelFactory.create(id));

        try {
            Trace.beginSection(TAG + "#init");
            Resources res = mContext.getResources();
            StateManager.clearStates();
            try (TypedArray states = res.obtainTypedArray(R.array.window_states)) {
                for (int i = 0; i < states.length(); i++) {
                    int xmlResId = states.getResourceId(i, 0);
                    if (DEBUG) {
                        Log.d(TAG, "PanelConfig adding state: " + xmlResId);
                    }
                    StateManager.addState(mContext, xmlResId);
                }
            }
        } catch (XmlPullParserException | IOException e) {
            throw new RuntimeException(e);
        } finally {
            Trace.endSection();
        }
    }
}
