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

import static android.view.WindowInsets.Type.navigationBars;
import static android.view.WindowInsets.Type.statusBars;
import static android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;

import static com.android.car.scalableui.loader.xml.SystemBarTagXmlParser.SYSTEM_BAR_PANEL_BOTTOM_ID;
import static com.android.car.scalableui.loader.xml.SystemBarTagXmlParser.SYSTEM_BAR_PANEL_LEFT_ID;
import static com.android.car.scalableui.loader.xml.SystemBarTagXmlParser.SYSTEM_BAR_PANEL_RIGHT_ID;
import static com.android.car.scalableui.loader.xml.SystemBarTagXmlParser.SYSTEM_BAR_PANEL_TOP_ID;
import static com.android.systemui.car.wm.scalableui.systemevents.SystemEventConstants.HIDE_EVENT_PREFIX;
import static com.android.systemui.car.wm.scalableui.systemevents.SystemEventConstants.SHOW_EVENT_PREFIX;
import static com.android.systemui.car.wm.scalableui.systemwindow.SystemBarWindow.HUN_Z_ORDER;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowManager;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.car.scalableui.loader.xml.SystemBarTagXmlParser;
import com.android.car.scalableui.model.PanelControllerMetadata;
import com.android.systemui.CarSysuiTestCase;
import com.android.systemui.car.CarSystemUiTest;
import com.android.systemui.car.systembar.CarSystemBarView;
import com.android.systemui.car.wm.scalableui.EventDispatcher;
import com.android.systemui.car.wm.scalableui.panel.panelupdates.PanelUpdateConsumer;
import com.android.systemui.tests.R;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Optional;

@CarSystemUiTest
@RunWith(AndroidJUnit4.class)
@SmallTest
public class SystemBarWindowTest extends CarSysuiTestCase {
    private static final float TEST_ALPHA = 0.2f;
    private static final boolean TEST_VISIBILITY = true;
    private static final int TEST_DISPLAY_HEIGHT = 1000;
    private static final int TEST_DISPLAY_WIDTH = 1000;
    private static final int TEST_GIRTH = 100;
    @Mock
    EventDispatcher mEventDispatcher;
    @Mock
    private Context mMockContext;
    @Mock
    private PanelUpdateConsumer mPanelUpdateConsumer;
    @Mock
    private PanelControllerMetadata mPanelControllerMetadata;
    @Mock
    private WindowManager mWindowManager;
    @Mock
    private Resources mResources;
    @Mock
    private SystemUiWindow.WindowUpdateCallback mWindowUpdateCallback;
    private SystemBarWindow mSystemBarWindow;
    private Bundle mBundle;
    private DisplayMetrics mDisplayMetrics;
    private ViewGroup mSystemBarView;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);

        mSystemBarView = (ViewGroup) LayoutInflater.from(mContext).inflate(
                R.layout.car_top_system_bar, /* root= */ null);
        mBundle = new Bundle();
        mDisplayMetrics = Resources.getSystem().getDisplayMetrics();
        mDisplayMetrics.widthPixels = TEST_DISPLAY_WIDTH;
        mDisplayMetrics.heightPixels = TEST_DISPLAY_HEIGHT;
        when(mPanelUpdateConsumer.getPanelControllerMetadata(anyString()))
                .thenReturn(mPanelControllerMetadata);
        when(mPanelControllerMetadata.getConfigurations()).thenReturn(mBundle);
        when(mMockContext.getSystemService(WindowManager.class)).thenReturn(mWindowManager);
        when(mMockContext.getResources()).thenReturn(mResources);
        when(mResources.getDisplayMetrics()).thenReturn(mDisplayMetrics);
        mSystemBarWindow = new SystemBarWindow(mMockContext, Optional.of(mPanelUpdateConsumer),
                mEventDispatcher, SYSTEM_BAR_PANEL_TOP_ID);
    }

    @Test
    public void constructor_panelUpdateConsumer_callbackRegistered() {
        verify(mPanelUpdateConsumer).registerCallback(eq(SYSTEM_BAR_PANEL_TOP_ID),
                any(PanelUpdateConsumer.PanelUpdateCallback.class));
    }

    @Test
    public void constructor_panelUpdateConsumer_onBoundsChange_updateViewLayoutCalled() {
        ArgumentCaptor<PanelUpdateConsumer.PanelUpdateCallback> callbackCaptor =
                ArgumentCaptor.forClass(PanelUpdateConsumer.PanelUpdateCallback.class);
        verify(mPanelUpdateConsumer).registerCallback(eq(SYSTEM_BAR_PANEL_TOP_ID),
                callbackCaptor.capture());
        mSystemBarWindow.setRootView(mSystemBarView);

        callbackCaptor.getValue().onBoundsChange(SYSTEM_BAR_PANEL_TOP_ID, new Rect());

        verify(mWindowManager).updateViewLayout(any(View.class),
                any(WindowManager.LayoutParams.class));
    }

    @Test
    public void constructor_panelUpdateConsumer_onAlphaChange_correctAlphaSet() {
        ArgumentCaptor<PanelUpdateConsumer.PanelUpdateCallback> callbackCaptor =
                ArgumentCaptor.forClass(PanelUpdateConsumer.PanelUpdateCallback.class);
        verify(mPanelUpdateConsumer).registerCallback(eq(SYSTEM_BAR_PANEL_TOP_ID),
                callbackCaptor.capture());
        mSystemBarWindow.setRootView(mSystemBarView);

        callbackCaptor.getValue().onAlphaChange(SYSTEM_BAR_PANEL_TOP_ID, TEST_ALPHA);

        assertThat(mSystemBarView.getAlpha()).isEqualTo(TEST_ALPHA);
    }

    @Test
    public void constructor_panelUpdateConsumer_onVisibilityChange_correctVisibilitySet() {
        ArgumentCaptor<PanelUpdateConsumer.PanelUpdateCallback> callbackCaptor =
                ArgumentCaptor.forClass(PanelUpdateConsumer.PanelUpdateCallback.class);
        verify(mPanelUpdateConsumer).registerCallback(eq(SYSTEM_BAR_PANEL_TOP_ID),
                callbackCaptor.capture());
        mSystemBarWindow.setRootView(mSystemBarView);

        callbackCaptor.getValue().onVisibilityChange(SYSTEM_BAR_PANEL_TOP_ID, TEST_VISIBILITY);

        assertThat(mSystemBarView.getVisibility()).isEqualTo(View.VISIBLE);
    }

    @Test
    public void setRootView_windowManagerCalled() {
        Rect bounds = new Rect();
        bounds.left = 0;
        bounds.right = TEST_DISPLAY_WIDTH;
        bounds.top = 0;
        bounds.bottom = TEST_GIRTH;
        mBundle.putInt(SystemBarTagXmlParser.BAR_Z_ORDER_ATTRIBUTE, HUN_Z_ORDER);
        mBundle.putInt(SystemBarTagXmlParser.TYPE_ATTRIBUTE, 0);
        when(mPanelUpdateConsumer.getBounds(eq(SYSTEM_BAR_PANEL_TOP_ID))).thenReturn(bounds);

        mSystemBarWindow.setRootView(mSystemBarView);

        verify(mWindowManager).addView(any(CarSystemBarView.class),
                any(WindowManager.LayoutParams.class));
    }

    @Test
    public void removeRootView_windowManagerCalled() {
        mSystemBarWindow.setRootView(mSystemBarView);

        mSystemBarWindow.removeRootView();

        verify(mWindowManager).removeView(any(View.class));
    }

    @Test
    public void isVisible_panelUpdateConsumerCalled() {
        mSystemBarWindow.isVisible();

        verify(mPanelUpdateConsumer).isVisible(eq(SYSTEM_BAR_PANEL_TOP_ID));
    }

    @Test
    public void hide_eventDispatcherCalled() {
        mSystemBarWindow.hide();

        verify(mEventDispatcher).executeEvent(eq(HIDE_EVENT_PREFIX + SYSTEM_BAR_PANEL_TOP_ID));
    }

    @Test
    public void show_eventDispatcherCalled() {
        mSystemBarWindow.show();

        verify(mEventDispatcher).executeEvent(eq(SHOW_EVENT_PREFIX + SYSTEM_BAR_PANEL_TOP_ID));
    }

    @Test
    public void getLayoutParams_panelUpdateConsumerCalled() {
        mSystemBarWindow.getLayoutParams();

        verify(mPanelUpdateConsumer).getBounds(eq(SYSTEM_BAR_PANEL_TOP_ID));
    }

    @Test
    public void getLayoutParams_topSystemBar_lpCorrect() {
        Rect bounds = new Rect();
        bounds.left = 0;
        bounds.right = TEST_DISPLAY_WIDTH;
        bounds.top = 0;
        bounds.bottom = TEST_GIRTH;
        mBundle.putInt(SystemBarTagXmlParser.BAR_Z_ORDER_ATTRIBUTE, HUN_Z_ORDER);
        mBundle.putInt(SystemBarTagXmlParser.TYPE_ATTRIBUTE, 0);
        when(mPanelUpdateConsumer.getBounds(eq(SYSTEM_BAR_PANEL_TOP_ID))).thenReturn(bounds);

        WindowManager.LayoutParams lp = mSystemBarWindow.getLayoutParams();

        assertThat(lp.type).isEqualTo(WindowManager.LayoutParams.TYPE_NAVIGATION_BAR_PANEL);
        assertThat(lp.flags).isEqualTo(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                | WindowManager.LayoutParams.FLAG_SPLIT_TOUCH);
        assertThat(lp.format).isEqualTo(PixelFormat.TRANSLUCENT);
        assertThat(lp.getFitInsetsTypes()).isEqualTo(0);
        assertThat(lp.getTitle()).isEqualTo("TopCarSystemBar");
        assertThat(lp.gravity).isEqualTo(Gravity.TOP);
        assertThat(lp.providedInsets.length).isEqualTo(2);
        assertThat(lp.providedInsets[0].getIndex()).isEqualTo(0);
        assertThat(lp.providedInsets[0].getType()).isEqualTo(statusBars());
        assertThat(lp.providedInsets[1].getIndex()).isEqualTo(0);
        assertThat(lp.providedInsets[1].getType())
                .isEqualTo(WindowInsets.Type.mandatorySystemGestures());
        assertThat(lp.windowAnimations).isEqualTo(0);
        assertThat(lp.layoutInDisplayCutoutMode).isEqualTo(LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS);
        assertThat(lp.privateFlags
                & WindowManager.LayoutParams.PRIVATE_FLAG_INTERCEPT_GLOBAL_DRAG_AND_DROP)
                .isEqualTo(WindowManager.LayoutParams.PRIVATE_FLAG_INTERCEPT_GLOBAL_DRAG_AND_DROP);
        assertThat(lp.height).isEqualTo(bounds.height());
        assertThat(lp.width).isEqualTo(bounds.width());
        assertThat(lp.horizontalMargin).isEqualTo(0);
        assertThat(lp.verticalMargin).isEqualTo(0);
    }

    @Test
    public void getLayoutParams_bottomSystemBar_lpCorrect() {
        mSystemBarWindow.setId(SYSTEM_BAR_PANEL_BOTTOM_ID);
        Rect bounds = new Rect();
        bounds.left = 0;
        bounds.right = TEST_DISPLAY_WIDTH;
        bounds.top = TEST_DISPLAY_HEIGHT - TEST_GIRTH;
        bounds.bottom = TEST_DISPLAY_HEIGHT;
        mBundle.putInt(SystemBarTagXmlParser.BAR_Z_ORDER_ATTRIBUTE, HUN_Z_ORDER);
        mBundle.putInt(SystemBarTagXmlParser.TYPE_ATTRIBUTE, 1);
        when(mPanelUpdateConsumer.getBounds(eq(SYSTEM_BAR_PANEL_BOTTOM_ID))).thenReturn(bounds);

        WindowManager.LayoutParams lp = mSystemBarWindow.getLayoutParams();

        assertThat(lp.type).isEqualTo(WindowManager.LayoutParams.TYPE_NAVIGATION_BAR_PANEL);
        assertThat(lp.flags).isEqualTo(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                | WindowManager.LayoutParams.FLAG_SPLIT_TOUCH);
        assertThat(lp.format).isEqualTo(PixelFormat.TRANSLUCENT);
        assertThat(lp.getFitInsetsTypes()).isEqualTo(0);
        assertThat(lp.getTitle()).isEqualTo("BottomCarSystemBar");
        assertThat(lp.gravity).isEqualTo(Gravity.BOTTOM);
        assertThat(lp.providedInsets.length).isEqualTo(2);
        assertThat(lp.providedInsets[0].getIndex()).isEqualTo(0);
        assertThat(lp.providedInsets[0].getType()).isEqualTo(navigationBars());
        assertThat(lp.providedInsets[1].getIndex()).isEqualTo(1);
        assertThat(lp.providedInsets[1].getType())
                .isEqualTo(WindowInsets.Type.mandatorySystemGestures());
        assertThat(lp.windowAnimations).isEqualTo(0);
        assertThat(lp.layoutInDisplayCutoutMode).isEqualTo(LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS);
        assertThat(lp.privateFlags
                & WindowManager.LayoutParams.PRIVATE_FLAG_INTERCEPT_GLOBAL_DRAG_AND_DROP)
                .isEqualTo(WindowManager.LayoutParams.PRIVATE_FLAG_INTERCEPT_GLOBAL_DRAG_AND_DROP);
        assertThat(lp.height).isEqualTo(bounds.height());
        assertThat(lp.width).isEqualTo(bounds.width());
        assertThat(lp.horizontalMargin).isEqualTo(0);
        assertThat(lp.verticalMargin).isEqualTo(0);
    }

    @Test
    public void getLayoutParams_leftSystemBar_lpCorrect() {
        mSystemBarWindow.setId(SYSTEM_BAR_PANEL_LEFT_ID);
        Rect bounds = new Rect();
        bounds.left = 0;
        bounds.right = TEST_GIRTH;
        bounds.top = 0;
        bounds.bottom = TEST_DISPLAY_HEIGHT;
        mBundle.putInt(SystemBarTagXmlParser.BAR_Z_ORDER_ATTRIBUTE, HUN_Z_ORDER - 1);
        mBundle.putInt(SystemBarTagXmlParser.TYPE_ATTRIBUTE, 2);
        when(mPanelUpdateConsumer.getBounds(eq(SYSTEM_BAR_PANEL_LEFT_ID))).thenReturn(bounds);

        WindowManager.LayoutParams lp = mSystemBarWindow.getLayoutParams();

        assertThat(lp.type).isEqualTo(WindowManager.LayoutParams.TYPE_STATUS_BAR_ADDITIONAL);
        assertThat(lp.flags).isEqualTo(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                | WindowManager.LayoutParams.FLAG_SPLIT_TOUCH);
        assertThat(lp.format).isEqualTo(PixelFormat.TRANSLUCENT);
        assertThat(lp.getFitInsetsTypes()).isEqualTo(0);
        assertThat(lp.getTitle()).isEqualTo("LeftCarSystemBar");
        assertThat(lp.gravity).isEqualTo(Gravity.LEFT);
        assertThat(lp.providedInsets.length).isEqualTo(2);
        assertThat(lp.providedInsets[0].getIndex()).isEqualTo(1);
        assertThat(lp.providedInsets[0].getType()).isEqualTo(statusBars());
        assertThat(lp.providedInsets[1].getIndex()).isEqualTo(2);
        assertThat(lp.providedInsets[1].getType())
                .isEqualTo(WindowInsets.Type.mandatorySystemGestures());
        assertThat(lp.windowAnimations).isEqualTo(0);
        assertThat(lp.layoutInDisplayCutoutMode).isEqualTo(LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS);
        assertThat(lp.privateFlags
                & WindowManager.LayoutParams.PRIVATE_FLAG_INTERCEPT_GLOBAL_DRAG_AND_DROP)
                .isEqualTo(WindowManager.LayoutParams.PRIVATE_FLAG_INTERCEPT_GLOBAL_DRAG_AND_DROP);
        assertThat(lp.height).isEqualTo(bounds.height());
        assertThat(lp.width).isEqualTo(bounds.width());
        assertThat(lp.horizontalMargin).isEqualTo(0);
        assertThat(lp.verticalMargin).isEqualTo(0);
    }

    @Test
    public void getLayoutParams_rightSystemBar_lpCorrect() {
        mSystemBarWindow.setId(SYSTEM_BAR_PANEL_RIGHT_ID);
        Rect bounds = new Rect();
        bounds.left = TEST_DISPLAY_WIDTH - TEST_GIRTH;
        bounds.right = TEST_DISPLAY_WIDTH;
        bounds.top = 0;
        bounds.bottom = TEST_DISPLAY_HEIGHT;
        mBundle.putInt(SystemBarTagXmlParser.BAR_Z_ORDER_ATTRIBUTE, HUN_Z_ORDER - 1);
        mBundle.putInt(SystemBarTagXmlParser.TYPE_ATTRIBUTE, 3);
        when(mPanelUpdateConsumer.getBounds(eq(SYSTEM_BAR_PANEL_RIGHT_ID))).thenReturn(bounds);

        WindowManager.LayoutParams lp = mSystemBarWindow.getLayoutParams();

        assertThat(lp.type).isEqualTo(WindowManager.LayoutParams.TYPE_STATUS_BAR_ADDITIONAL);
        assertThat(lp.flags).isEqualTo(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                | WindowManager.LayoutParams.FLAG_SPLIT_TOUCH);
        assertThat(lp.format).isEqualTo(PixelFormat.TRANSLUCENT);
        assertThat(lp.getFitInsetsTypes()).isEqualTo(0);
        assertThat(lp.getTitle()).isEqualTo("RightCarSystemBar");
        assertThat(lp.gravity).isEqualTo(Gravity.RIGHT);
        assertThat(lp.providedInsets.length).isEqualTo(2);
        assertThat(lp.providedInsets[0].getIndex()).isEqualTo(1);
        assertThat(lp.providedInsets[0].getType()).isEqualTo(navigationBars());
        assertThat(lp.providedInsets[1].getIndex()).isEqualTo(3);
        assertThat(lp.providedInsets[1].getType())
                .isEqualTo(WindowInsets.Type.mandatorySystemGestures());
        assertThat(lp.windowAnimations).isEqualTo(0);
        assertThat(lp.layoutInDisplayCutoutMode).isEqualTo(LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS);
        assertThat(lp.privateFlags
                & WindowManager.LayoutParams.PRIVATE_FLAG_INTERCEPT_GLOBAL_DRAG_AND_DROP)
                .isEqualTo(WindowManager.LayoutParams.PRIVATE_FLAG_INTERCEPT_GLOBAL_DRAG_AND_DROP);
        assertThat(lp.height).isEqualTo(bounds.height());
        assertThat(lp.width).isEqualTo(bounds.width());
        assertThat(lp.horizontalMargin).isEqualTo(0);
        assertThat(lp.verticalMargin).isEqualTo(0);
    }

    @Test
    public void getHeight_correctReturn() {
        Rect bounds = new Rect();
        bounds.left = 0;
        bounds.right = 0;
        bounds.top = 0;
        bounds.bottom = TEST_GIRTH;
        when(mPanelUpdateConsumer.getBounds(eq(SYSTEM_BAR_PANEL_TOP_ID))).thenReturn(bounds);

        int height = mSystemBarWindow.getHeight();

        assertThat(height).isEqualTo(TEST_GIRTH);
    }

    @Test
    public void getWidth_correctReturn() {
        Rect bounds = new Rect();
        bounds.left = 0;
        bounds.right = TEST_GIRTH;
        bounds.top = 0;
        bounds.bottom = 0;
        when(mPanelUpdateConsumer.getBounds(eq(SYSTEM_BAR_PANEL_TOP_ID))).thenReturn(bounds);

        int width = mSystemBarWindow.getWidth();

        assertThat(width).isEqualTo(TEST_GIRTH);
    }

    @Test
    public void getAlpha_panelUpdateConsumerCalled() {
        mSystemBarWindow.getAlpha();

        verify(mPanelUpdateConsumer).getAlpha(eq(SYSTEM_BAR_PANEL_TOP_ID));
    }

    @Test
    public void getInsets_panelUpdateConsumerCalled() {
        mSystemBarWindow.getInsets();

        verify(mPanelUpdateConsumer).getInsets(eq(SYSTEM_BAR_PANEL_TOP_ID));
    }

    @Test
    public void getCornerRadius_panelUpdateConsumerCalled() {
        mSystemBarWindow.getCornerRadius();

        verify(mPanelUpdateConsumer).getCornerRadius(eq(SYSTEM_BAR_PANEL_TOP_ID));
    }

    @Test
    public void addCallback_registerCallbackCalled() {
        mSystemBarWindow.addCallback(mWindowUpdateCallback);

        verify(mPanelUpdateConsumer).registerCallback(eq(SYSTEM_BAR_PANEL_TOP_ID),
                eq(mWindowUpdateCallback));
    }

    @Test
    public void removeCallback_unregisterCallbackCalled() {
        mSystemBarWindow.removeCallback(mWindowUpdateCallback);

        verify(mPanelUpdateConsumer).unregisterCallback(eq(mWindowUpdateCallback));
    }

    @Test
    public void systemBarConfiguration_getConfiguration_correctBundle() {
        mBundle.putInt(SystemBarTagXmlParser.BAR_Z_ORDER_ATTRIBUTE, HUN_Z_ORDER);
        mBundle.putInt(SystemBarTagXmlParser.TYPE_ATTRIBUTE, 0);
        when(mPanelControllerMetadata.getConfigurations()).thenReturn(mBundle);
        when(mPanelUpdateConsumer.getPanelControllerMetadata(SYSTEM_BAR_PANEL_TOP_ID))
                .thenReturn(mPanelControllerMetadata);
        SystemBarWindow.SystemBarConfiguration config = new SystemBarWindow.SystemBarConfiguration(
                Optional.of(mPanelUpdateConsumer), SYSTEM_BAR_PANEL_TOP_ID);

        assertThat(config.getConfiguration()).isEqualTo(mBundle);
    }

    @Test
    public void systemBarConfiguration_getZOrder_correctZOrder() {
        mBundle.putInt(SystemBarTagXmlParser.BAR_Z_ORDER_ATTRIBUTE, HUN_Z_ORDER);
        mBundle.putInt(SystemBarTagXmlParser.TYPE_ATTRIBUTE, 0);
        when(mPanelControllerMetadata.getConfigurations()).thenReturn(mBundle);
        when(mPanelUpdateConsumer.getPanelControllerMetadata(SYSTEM_BAR_PANEL_TOP_ID))
                .thenReturn(mPanelControllerMetadata);
        SystemBarWindow.SystemBarConfiguration config = new SystemBarWindow.SystemBarConfiguration(
                Optional.of(mPanelUpdateConsumer), SYSTEM_BAR_PANEL_TOP_ID);

        assertThat(config.getZOrder()).isEqualTo(HUN_Z_ORDER);
    }

    @Test
    public void systemBarConfiguration_isAboveHun_isTrue() {
        mBundle.putInt(SystemBarTagXmlParser.BAR_Z_ORDER_ATTRIBUTE, HUN_Z_ORDER);
        mBundle.putInt(SystemBarTagXmlParser.TYPE_ATTRIBUTE, 0);
        when(mPanelControllerMetadata.getConfigurations()).thenReturn(mBundle);
        when(mPanelUpdateConsumer.getPanelControllerMetadata(SYSTEM_BAR_PANEL_TOP_ID))
                .thenReturn(mPanelControllerMetadata);
        SystemBarWindow.SystemBarConfiguration config = new SystemBarWindow.SystemBarConfiguration(
                Optional.of(mPanelUpdateConsumer), SYSTEM_BAR_PANEL_TOP_ID);

        assertThat(config.isAboveHun()).isTrue();
    }

    @Test
    public void systemBarConfiguration_getType_correctType() {
        mBundle.putInt(SystemBarTagXmlParser.BAR_Z_ORDER_ATTRIBUTE, HUN_Z_ORDER);
        mBundle.putInt(SystemBarTagXmlParser.TYPE_ATTRIBUTE, 0);
        when(mPanelControllerMetadata.getConfigurations()).thenReturn(mBundle);
        when(mPanelUpdateConsumer.getPanelControllerMetadata(SYSTEM_BAR_PANEL_TOP_ID))
                .thenReturn(mPanelControllerMetadata);
        SystemBarWindow.SystemBarConfiguration config = new SystemBarWindow.SystemBarConfiguration(
                Optional.of(mPanelUpdateConsumer), SYSTEM_BAR_PANEL_TOP_ID);

        assertThat(config.getType()).isEqualTo(0);
    }
}
