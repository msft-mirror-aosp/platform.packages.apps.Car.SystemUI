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

import static com.android.car.scalableui.panel.ReservedIdProvider.SYSTEM_PANEL_IDS;
import static com.android.systemui.car.Flags.scalableUiDesignCompose;

import android.app.ActivityManager;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.os.Build;
import android.util.Log;

import com.android.car.internal.dep.Trace;
import com.android.car.scalableui.designcompose.PanelStateDocLoader;
import com.android.car.scalableui.loader.xml.XmlModelLoader;
import com.android.car.scalableui.manager.StateManager;
import com.android.car.scalableui.model.PanelState;
import com.android.car.scalableui.panel.PanelPool;
import com.android.systemui.R;
import com.android.systemui.car.wm.scalableui.panel.BasePanel;
import com.android.systemui.car.wm.scalableui.panel.DecorPanel;
import com.android.systemui.car.wm.scalableui.panel.TaskPanel;
import com.android.wm.shell.dagger.WMSingleton;

import java.io.InputStream;
import java.util.List;

@WMSingleton
public class PanelConfigReader {
    private static final String TAG = PanelConfigReader.class.getSimpleName();
    private static final boolean DEBUG = Build.IS_DEBUGGABLE;
    private final Context mContext;
    private final TaskPanel.Factory mTaskPanelFactory;
    private final DecorPanel.Factory mDecorPanelFactory;
    private final BasePanel.Factory mBasePanelFactory;

    public PanelConfigReader(Context context, TaskPanel.Factory taskPanelFactory,
            DecorPanel.Factory decorPanelFactory, BasePanel.Factory basePanelFactory) {
        debugLog("PanelConfig initialized user: " + ActivityManager.getCurrentUser());
        mContext = context;
        mTaskPanelFactory = taskPanelFactory;
        mDecorPanelFactory = decorPanelFactory;
        mBasePanelFactory = basePanelFactory;
    }

    /**
     * Init the Panels.
     */
    public void init() {
        PanelPool.getInstance().clearPanels();
        PanelPool.getInstance().setDelegate(id -> {
            if (id.startsWith(PanelState.DECOR_PANEL_ID_PREFIX)) {
                return mDecorPanelFactory.create(id);
            } else if (SYSTEM_PANEL_IDS.contains(id)) {
                return mBasePanelFactory.create(id);
            } else {
                return mTaskPanelFactory.create(id);
            }
        });

        try {
            Trace.beginSection(TAG + "#init");
            StateManager.clearStates();

            if (scalableUiDesignCompose()) {
                loadFromDcf();
            } else {
                loadFromXml();
            }
        } finally {
            Trace.endSection();
        }
    }

    private void loadFromDcf() {
        try {
            InputStream dcfStream = mContext.getResources().openRawResource(R.raw.ScalableSystemUi);
            if (dcfStream == null) {
                Log.e(TAG, "Failed to open file ScalableSystemUi.dcf");
                // Throw a runtime exception to cause a crash
                throw new RuntimeException("Failed to open ScalableSystemUi.dcf");
            }

            debugLog("Loading panel states from DCF file");
            PanelStateDocLoader dcLoader = new PanelStateDocLoader(mContext);
            String docId = mContext.getResources().getString(R.string.config_scalableUiDcfFileId);

            List<PanelState> states = dcLoader.loadPanelStates(dcfStream, docId);
            debugLog("Loaded Panels: " + states.size());

            for (PanelState panelState : states) {
                debugLog("PanelConfig adding state: " + panelState.getId());
                StateManager.addState(panelState);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error opening or processing DCF file: " + e);
            // Throw a runtime exception to cause a crash
            throw new RuntimeException("Error opening or processing DCF file: ", e);
        }
    }

    private void loadFromXml() {
        debugLog("Loading panel states from XML");
        Resources res = mContext.getResources();
        try (TypedArray states = res.obtainTypedArray(R.array.window_states)) {
            for (int i = 0; i < states.length(); i++) {
                int xmlResId = states.getResourceId(i, 0);
                debugLog("PanelConfig adding state: " + xmlResId);
                XmlModelLoader loader = new XmlModelLoader(mContext);
                PanelState panelState = loader.createPanelState(xmlResId);
                StateManager.addState(panelState);
            }
        }
    }

    private void debugLog(String logMsg) {
        if (DEBUG) {
            Log.d(TAG, logMsg);
        }
    }
}
