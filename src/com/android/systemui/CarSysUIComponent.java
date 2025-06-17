/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.systemui;

import static com.android.car.scalableui.loader.xml.SystemBarTagXmlParser.SYSTEM_BAR_PANEL_BOTTOM_ID;
import static com.android.car.scalableui.loader.xml.SystemBarTagXmlParser.SYSTEM_BAR_PANEL_LEFT_ID;
import static com.android.car.scalableui.loader.xml.SystemBarTagXmlParser.SYSTEM_BAR_PANEL_RIGHT_ID;
import static com.android.car.scalableui.loader.xml.SystemBarTagXmlParser.SYSTEM_BAR_PANEL_TOP_ID;

import com.android.systemui.car.wm.scalableui.EventDispatcher;
import com.android.systemui.car.wm.scalableui.ScalableUIWMInitializer;
import com.android.systemui.car.wm.scalableui.panel.TaskPanelInfoRepository;
import com.android.systemui.car.wm.scalableui.systemwindow.SystemBarWindow;
import com.android.systemui.car.wm.scalableui.systemwindow.SystemBarWindow.SystemBarConfiguration;
import com.android.systemui.dagger.DependencyProvider;
import com.android.systemui.dagger.SysUIComponent;
import com.android.systemui.dagger.SysUISingleton;
import com.android.systemui.dagger.SystemUIModule;
import com.android.systemui.scene.ShadelessSceneContainerFrameworkModule;
import com.android.wm.shell.RootTaskDisplayAreaOrganizer;

import dagger.BindsInstance;
import dagger.Subcomponent;

import java.util.Optional;

import javax.inject.Named;

/**
 * Dagger Subcomponent for Core SysUI.
 */
@SysUISingleton
@Subcomponent(modules = {
        CarComponentBinder.class,
        DependencyProvider.class,
        SystemUIModule.class,
        CarSystemUICoreStartableModule.class,
        CarSystemUIModule.class,
        CarSystemUIBinder.class,
        ShadelessSceneContainerFrameworkModule.class})
public interface CarSysUIComponent extends SysUIComponent {

    /**
     * Builder for a CarSysUIComponent.
     */
    @Subcomponent.Builder
    interface Builder extends SysUIComponent.Builder {
        @BindsInstance
        Builder setRootTaskDisplayAreaOrganizer(Optional<RootTaskDisplayAreaOrganizer> r);

        /**
         * Sets an optional {@link ScalableUIWMInitializer} for the builder.
         */
        @BindsInstance
        Builder setScalableUIWMInitializer(Optional<ScalableUIWMInitializer> initializer);

        /**
         * Sets the ScalableUI {@link TaskPanelInfoRepository} for the builder.
         */
        @BindsInstance
        Builder setTaskPanelInfoRepository(TaskPanelInfoRepository repository);

        /**
         * Sets the ScalableUI {@link EventDispatcher} for the builder.
         */
        @BindsInstance
        Builder setScalableUIEventDispatcher(EventDispatcher dispatcher);

        /**
         * Sets the {@link SystemBarWindow} for the left side
         */
        @BindsInstance
        Builder setLeftSystemBarWindow(
                @Named(SYSTEM_BAR_PANEL_LEFT_ID) Optional<SystemBarWindow> systemBarWindow);

        /**
         * Sets the {@link SystemBarWindow} for the top side
         */
        @BindsInstance
        Builder setTopSystemBarWindow(
                @Named(SYSTEM_BAR_PANEL_TOP_ID) Optional<SystemBarWindow> systemBarWindow);

        /**
         * Sets the {@link SystemBarWindow} for the right side
         */
        @BindsInstance
        Builder setRightSystemBarWindow(
                @Named(SYSTEM_BAR_PANEL_RIGHT_ID) Optional<SystemBarWindow> systemBarWindow);

        /**
         * Sets the {@link SystemBarWindow} for the bottom side
         */
        @BindsInstance
        Builder setBottomSystemBarWindow(
                @Named(SYSTEM_BAR_PANEL_BOTTOM_ID) Optional<SystemBarWindow> systemBarWindow);

        /**
         * Sets the {@link SystemBarConfiguration} for the left side
         */
        @BindsInstance
        Builder setLeftSystemBarConfiguration(
                @Named(SYSTEM_BAR_PANEL_LEFT_ID) Optional<SystemBarConfiguration> configuration);

        /**
         * Sets the {@link SystemBarConfiguration} for the top side
         */
        @BindsInstance
        Builder setTopSystemBarConfiguration(
                @Named(SYSTEM_BAR_PANEL_TOP_ID) Optional<SystemBarConfiguration> configuration);

        /**
         * Sets the {@link SystemBarConfiguration} for the right side
         */
        @BindsInstance
        Builder setRightSystemBarConfiguration(
                @Named(SYSTEM_BAR_PANEL_RIGHT_ID) Optional<SystemBarConfiguration> configuration);

        /**
         * Sets the {@link SystemBarConfiguration} for the bottom side
         */
        @BindsInstance
        Builder setBottomSystemBarConfiguration(
                @Named(SYSTEM_BAR_PANEL_BOTTOM_ID) Optional<SystemBarConfiguration> configuration);

        CarSysUIComponent build();
    }
}
