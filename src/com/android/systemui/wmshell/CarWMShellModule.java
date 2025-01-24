/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.systemui.wmshell;

import static com.android.systemui.car.Flags.scalableUi;

import android.content.Context;
import android.os.Handler;
import android.view.IWindowManager;

import androidx.annotation.NonNull;

import com.android.systemui.R;
import com.android.systemui.car.CarServiceProvider;
import com.android.systemui.car.wm.AutoDisplayCompatWindowDecorViewModel;
import com.android.systemui.car.wm.CarFullscreenTaskMonitorListener;
import com.android.systemui.car.wm.scalableui.PanelAutoTaskStackTransitionHandlerDelegate;
import com.android.systemui.car.wm.scalableui.PanelConfigReader;
import com.android.systemui.car.wm.scalableui.ScalableUIWMInitializer;
import com.android.systemui.car.wm.scalableui.panel.TaskPanel;
import com.android.systemui.dagger.qualifiers.Main;
import com.android.systemui.wm.DisplaySystemBarsController;
import com.android.wm.shell.ShellTaskOrganizer;
import com.android.wm.shell.automotive.AutoShellModule;
import com.android.wm.shell.common.DisplayController;
import com.android.wm.shell.common.DisplayInsetsController;
import com.android.wm.shell.common.ShellExecutor;
import com.android.wm.shell.common.SyncTransactionQueue;
import com.android.wm.shell.dagger.DynamicOverride;
import com.android.wm.shell.dagger.WMShellBaseModule;
import com.android.wm.shell.dagger.WMSingleton;
import com.android.wm.shell.fullscreen.FullscreenTaskListener;
import com.android.wm.shell.pip.Pip;
import com.android.wm.shell.recents.RecentTasksController;
import com.android.wm.shell.shared.annotations.ShellBackgroundThread;
import com.android.wm.shell.shared.annotations.ShellMainThread;
import com.android.wm.shell.sysui.ShellInit;
import com.android.wm.shell.taskview.TaskViewTransitions;
import com.android.wm.shell.transition.FocusTransitionObserver;
import com.android.wm.shell.windowdecor.WindowDecorViewModel;
import com.android.wm.shell.windowdecor.common.viewhost.DefaultWindowDecorViewHostSupplier;
import com.android.wm.shell.windowdecor.common.viewhost.WindowDecorViewHost;
import com.android.wm.shell.windowdecor.common.viewhost.WindowDecorViewHostSupplier;

import dagger.BindsOptionalOf;
import dagger.Module;
import dagger.Provides;

import kotlinx.coroutines.CoroutineScope;

import java.util.Optional;

/** Provides dependencies from {@link com.android.wm.shell} for CarSystemUI. */
@Module(includes = {WMShellBaseModule.class, AutoShellModule.class})
public abstract class CarWMShellModule {

    @WMSingleton
    @Provides
    static DisplaySystemBarsController provideDisplaySystemBarsController(Context context,
            IWindowManager wmService, DisplayController displayController,
            DisplayInsetsController displayInsetsController,
            @Main Handler mainHandler) {
        return new DisplaySystemBarsController(context, wmService, displayController,
                displayInsetsController, mainHandler);
    }

    @BindsOptionalOf
    abstract Pip optionalPip();

    @WMSingleton
    @Provides
    @DynamicOverride
    static FullscreenTaskListener provideFullScreenTaskListener(Context context,
            CarServiceProvider carServiceProvider,
            ShellInit shellInit,
            ShellTaskOrganizer shellTaskOrganizer,
            SyncTransactionQueue syncQueue,
            Optional<RecentTasksController> recentTasksOptional,
            Optional<WindowDecorViewModel> windowDecorViewModelOptional,
            TaskViewTransitions taskViewTransitions) {
        return new CarFullscreenTaskMonitorListener(context,
                carServiceProvider,
                shellInit,
                shellTaskOrganizer,
                syncQueue,
                recentTasksOptional,
                windowDecorViewModelOptional,
                taskViewTransitions);
    }

    @WMSingleton
    @Provides
    static WindowDecorViewHostSupplier<WindowDecorViewHost> provideWindowDecorViewHostSupplier(
            @ShellMainThread @NonNull CoroutineScope mainScope) {
        return new DefaultWindowDecorViewHostSupplier(mainScope);
    }

    @WMSingleton
    @Provides
    static WindowDecorViewModel provideWindowDecorViewModel(
            Context context,
            @ShellMainThread ShellExecutor mainExecutor,
            @ShellBackgroundThread ShellExecutor bgExecutor,
            ShellInit shellInit,
            ShellTaskOrganizer taskOrganizer,
            DisplayController displayController,
            SyncTransactionQueue syncQueue,
            FocusTransitionObserver focusTransitionObserver,
            WindowDecorViewHostSupplier<WindowDecorViewHost> windowDecorViewHostSupplier,
            CarServiceProvider carServiceProvider
    ) {
        return new AutoDisplayCompatWindowDecorViewModel(
                context,
                mainExecutor,
                bgExecutor,
                shellInit,
                taskOrganizer,
                displayController,
                syncQueue,
                focusTransitionObserver,
                windowDecorViewHostSupplier,
                carServiceProvider);
    }

    @WMSingleton
    @Provides
    static Optional<PanelConfigReader> providesPanelConfigReader(
            Context context,
            TaskPanel.Factory taskPanelFactory
    ) {
        if (isScalableUIEnabled(context)) {
            return Optional.of(new PanelConfigReader(
                    context,
                    taskPanelFactory));
        }
        return Optional.empty();
    }

    @WMSingleton
    @Provides
    static Optional<ScalableUIWMInitializer> provideScalableUIInitializer(ShellInit shellInit,
            Context context,
            Optional<PanelConfigReader> panelConfigReaderOptional,
            PanelAutoTaskStackTransitionHandlerDelegate delegate) {
        if (isScalableUIEnabled(context) && panelConfigReaderOptional.isPresent()) {
            return Optional.of(
                    new ScalableUIWMInitializer(shellInit, panelConfigReaderOptional.get(),
                            delegate));
        }
        return Optional.empty();
    }

    private static boolean isScalableUIEnabled(Context context) {
        return scalableUi() && context.getResources().getBoolean(R.bool.config_enableScalableUI);
    }
}
