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
package com.android.systemui.car.wm.scalableui.systemwindow;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Rect;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.WindowManager;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.systemui.CarSysuiTestCase;
import com.android.systemui.car.CarSystemUiTest;
import com.android.systemui.car.wm.scalableui.EventDispatcher;
import com.android.systemui.car.wm.scalableui.configuration.SystemBarConfiguration;
import com.android.systemui.car.wm.scalableui.panel.panelupdates.PanelUpdateConsumer;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@CarSystemUiTest
@RunWith(AndroidJUnit4.class)
@SmallTest
public class SystemBarWindowTest extends CarSysuiTestCase {

    private static final String TEST_PANEL_ID = "test";

    @Rule
    public MockitoRule mRule = MockitoJUnit.rule();

    private SystemBarWindow mSystemBarWindow;

    @Mock
    private Context mContext;
    @Mock
    private Display mDisplay;
    @Mock
    private Resources mResources;
    @Mock
    private WindowManager mWindowManager;
    @Mock
    private EventDispatcher mEventDispatcher;
    @Mock
    private PanelUpdateConsumer mPanelUpdateConsumer;
    @Mock
    private SystemBarConfiguration mSystemBarConfiguration;

    @Before
    public void setUp() {
        when(mContext.getResources()).thenReturn(mResources);
        when(mResources.getDisplayMetrics()).thenReturn(new DisplayMetrics());
        when(mContext.getDisplay()).thenReturn(mDisplay);
        when(mContext.createDisplayContext(mDisplay)).thenReturn(mContext);
        when(mContext.getSystemService(WindowManager.class)).thenReturn(mWindowManager);
        when(mSystemBarConfiguration.getName()).thenReturn(TEST_PANEL_ID);
        when(mPanelUpdateConsumer.getBounds(TEST_PANEL_ID)).thenReturn(new Rect(0, 0, 100, 100));

        mSystemBarWindow = new SystemBarWindow(mContext, mEventDispatcher, mPanelUpdateConsumer,
                mSystemBarConfiguration);
    }

    @Test
    public void getLayoutParams_zOrderAboveHun_returnsNavBarPanelType() {
        when(mSystemBarConfiguration.getZOrder()).thenReturn(SystemBarWindow.HUN_Z_ORDER);

        WindowManager.LayoutParams params = mSystemBarWindow.getLayoutParams();

        assertThat(params.type).isEqualTo(WindowManager.LayoutParams.TYPE_NAVIGATION_BAR_PANEL);
    }

    @Test
    public void getLayoutParams_zOrderBelowHun_returnsStatusBarAdditionalType() {
        when(mSystemBarConfiguration.getZOrder()).thenReturn(SystemBarWindow.HUN_Z_ORDER - 1);

        WindowManager.LayoutParams params = mSystemBarWindow.getLayoutParams();

        assertThat(params.type).isEqualTo(WindowManager.LayoutParams.TYPE_STATUS_BAR_ADDITIONAL);
    }
}
