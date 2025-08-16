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

import static com.android.car.scalableui.loader.xml.SystemBarTagXmlParser.SYSTEM_BAR_PANEL_BOTTOM_ID;
import static com.android.car.scalableui.loader.xml.SystemBarTagXmlParser.SYSTEM_BAR_PANEL_LEFT_ID;
import static com.android.car.scalableui.loader.xml.SystemBarTagXmlParser.SYSTEM_BAR_PANEL_RIGHT_ID;
import static com.android.car.scalableui.loader.xml.SystemBarTagXmlParser.SYSTEM_BAR_PANEL_TOP_ID;

import android.content.Context;
import android.os.Handler;
import android.view.IWindowManager;

import androidx.annotation.NonNull;

import com.android.car.scalableui.panel.PanelUpdatePublisher;
import com.android.systemui.car.CarServiceProvider;
import com.android.systemui.car.flags.Flag;
import com.android.systemui.car.flags.FlagManager;
import com.android.systemui.car.wm.AutoCaptionPerDisplayInitializer;
import com.android.systemui.car.wm.AutoDisplayCompatWindowDecorViewModel;
import com.android.systemui.car.wm.CarFullscreenTaskMonitorListener;
import com.android.systemui.car.wm.scalableui.ActionConfigReader;
import com.android.systemui.car.wm.scalableui.EventDispatcher;
import com.android.systemui.car.wm.scalableui.PanelAutoTaskStackTransitionHandlerDelegate;
import com.android.systemui.car.wm.scalableui.PanelConfigReader;
import com.android.systemui.car.wm.scalableui.ScalableUIDumpsys;
import com.android.systemui.car.wm.scalableui.ScalableUIWMInitializer;
import com.android.systemui.car.wm.scalableui.panel.BasePanel;
import com.android.systemui.car.wm.scalableui.panel.DecorPanel;
import com.android.systemui.car.wm.scalableui.panel.TaskPanel;
import com.android.systemui.car.wm.scalableui.panel.controller.PanelControllerModule;
import com.android.systemui.car.wm.scalableui.panel.panelupdates.PanelUpdateConsumer;
import com.android.systemui.car.wm.scalableui.panel.panelupdates.ScalableUIPanelUpdateImpl;
import com.android.systemui.car.wm.scalableui.systemwindow.HunWindow;
import com.android.systemui.car.wm.scalableui.systemwindow.SystemBarWindow;
import com.android.systemui.car.wm.scalableui.systemwindow.SystemBarWindow.SystemBarConfiguration;
import com.android.systemui.dagger.qualifiers.Main;
import com.android.systemui.wm.DisplaySystemBarsController;
import com.android.wm.shell.RootTaskDisplayAreaOrganizer;
import com.android.wm.shell.ShellTaskOrganizer;
import com.android.wm.shell.automotive.AutoCaptionController;
import com.android.wm.shell.automotive.AutoLayoutManager;
import com.android.wm.shell.automotive.AutoShellModule;
import com.android.wm.shell.automotive.AutoTaskRepository;
import com.android.wm.shell.common.DisplayController;
import com.android.wm.shell.common.DisplayInsetsController;
import com.android.wm.shell.common.ShellExecutor;
import com.android.wm.shell.common.SyncTransactionQueue;
import com.android.wm.shell.compatui.letterbox.DelegateLetterboxTransitionObserver;
import com.android.wm.shell.compatui.letterbox.LetterboxCommandHandler;
import com.android.wm.shell.compatui.letterbox.config.IgnoreLetterboxDependenciesHelper;
import com.android.wm.shell.compatui.letterbox.config.LetterboxDependenciesHelper;
import com.android.wm.shell.compatui.letterbox.lifecycle.LetterboxCleanupAdapter;
import com.android.wm.shell.compatui.letterbox.state.LetterboxTaskListenerAdapter;
import com.android.wm.shell.dagger.DynamicOverride;
import com.android.wm.shell.dagger.LetterboxModule;
import com.android.wm.shell.dagger.ShellCreateTriggerOverride;
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
import com.android.wm.shell.transition.Transitions;
import com.android.wm.shell.windowdecor.WindowDecorViewModel;
import com.android.wm.shell.windowdecor.common.viewhost.DefaultWindowDecorViewHostSupplier;
import com.android.wm.shell.windowdecor.common.viewhost.WindowDecorViewHost;
import com.android.wm.shell.windowdecor.common.viewhost.WindowDecorViewHostSupplier;

import dagger.BindsOptionalOf;
import dagger.Lazy;
import dagger.Module;
import dagger.Provides;

import kotlinx.coroutines.CoroutineScope;

import java.util.Optional;

import javax.inject.Named;

/** Provides dependencies from {@link com.android.wm.shell} for CarSystemUI. */
@Module(includes = {WMShellBaseModule.class, AutoShellModule.class, LetterboxModule.class,
        PanelControllerModule.class})
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

    @WMSingleton
    @Provides
    static Optional<AutoCaptionPerDisplayInitializer> provideAutoCaptionPerDisplayInitializer(
            Context context,
            ShellTaskOrganizer shellTaskOrganizer,
            AutoCaptionController autoCaptionController,
            DisplayController displayController,
            RootTaskDisplayAreaOrganizer rootTaskDisplayAreaOrganizer,
            AutoLayoutManager autoLayoutManager) {
        return Optional.of(
                new AutoCaptionPerDisplayInitializer(context, shellTaskOrganizer,
                        autoCaptionController, displayController, rootTaskDisplayAreaOrganizer,
                        autoLayoutManager));
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
            TaskViewTransitions taskViewTransitions,
            AutoTaskRepository taskRepository) {
        return new CarFullscreenTaskMonitorListener(context,
                carServiceProvider,
                shellInit,
                shellTaskOrganizer,
                syncQueue,
                recentTasksOptional,
                windowDecorViewModelOptional,
                taskViewTransitions,
                taskRepository);
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
            @ShellMainThread Handler handler,
            Transitions transitions,
            @ShellMainThread ShellExecutor mainExecutor,
            @ShellBackgroundThread ShellExecutor bgExecutor,
            ShellInit shellInit,
            ShellTaskOrganizer taskOrganizer,
            DisplayController displayController,
            DisplayInsetsController displayInsetsController,
            SyncTransactionQueue syncQueue,
            FocusTransitionObserver focusTransitionObserver,
            WindowDecorViewHostSupplier<WindowDecorViewHost> windowDecorViewHostSupplier,
            CarServiceProvider carServiceProvider
    ) {
        return new AutoDisplayCompatWindowDecorViewModel(
                context,
                handler,
                transitions,
                mainExecutor,
                bgExecutor,
                shellInit,
                taskOrganizer,
                displayController,
                displayInsetsController,
                syncQueue,
                focusTransitionObserver,
                windowDecorViewHostSupplier,
                carServiceProvider);
    }

    @WMSingleton
    @Provides
    static Optional<PanelConfigReader> providesPanelConfigReader(
            Context context,
            TaskPanel.Factory taskPanelFactory,
            DecorPanel.Factory decorPanelFactory,
            BasePanel.Factory basePanelFactory,
            FlagManager flagManager
    ) {
        if (flagManager.isEnabled(Flag.ScalableUIEnabled)) {
            return Optional.of(new PanelConfigReader(
                    context,
                    taskPanelFactory,
                    decorPanelFactory,
                    basePanelFactory,
                    flagManager));
        }
        return Optional.empty();
    }

    @WMSingleton
    @Provides
    static Optional<ActionConfigReader> providesActionConfigReader(Context context,
            FlagManager flagManager) {
        if (flagManager.isEnabled(Flag.ScalableUIEnabled)) {
            return Optional.of(new ActionConfigReader(context, flagManager));
        }
        return Optional.empty();
    }

    @WMSingleton
    @Provides
    static Optional<ScalableUIWMInitializer> provideScalableUIInitializer(ShellInit shellInit,
            Context context,
            Optional<ActionConfigReader> actionConfigReaderOptional,
            Optional<PanelConfigReader> panelConfigReaderOptional,
            Lazy<PanelAutoTaskStackTransitionHandlerDelegate> delegate,
            ScalableUIDumpsys scalableUIDumpsys,
            FlagManager flagManager) {
        if (flagManager.isEnabled(Flag.ScalableUIEnabled)
                && panelConfigReaderOptional.isPresent()) {
            return Optional.of(
                    new ScalableUIWMInitializer(shellInit, actionConfigReaderOptional.get(),
                            panelConfigReaderOptional.get(), delegate.get(), scalableUIDumpsys));
        }
        return Optional.empty();
    }

    @WMSingleton
    @Provides
    static FlagManager provideFlagManager(Context context) {
        return new FlagManager(context);
    }

    @WMSingleton
    @Provides
    static Optional<ScalableUIPanelUpdateImpl> provideScalableUIPanelUpdateImpl(
            FlagManager flagManager) {
        if (flagManager.isEnabled(Flag.ScalableUIEnabled) && flagManager.isEnabled(
                Flag.EnableExtPanelUpdates)) {
            return Optional.of(new ScalableUIPanelUpdateImpl());
        }
        return Optional.empty();
    }

    @WMSingleton
    @Provides
    static Optional<PanelUpdatePublisher> providePanelUpdatePublisher(
            Optional<ScalableUIPanelUpdateImpl> scalableUIPanelUpdateOptional) {
        if (scalableUIPanelUpdateOptional.isPresent()) {
            return Optional.of(scalableUIPanelUpdateOptional.get());
        }
        return Optional.empty();
    }

    @WMSingleton
    @Provides
    static Optional<PanelUpdateConsumer> providePanelUpdateConsumer(
            Optional<ScalableUIPanelUpdateImpl> scalableUIPanelUpdateOptional) {
        if (scalableUIPanelUpdateOptional.isPresent()) {
            return Optional.of(scalableUIPanelUpdateOptional.get());
        }
        return Optional.empty();
    }

    @WMSingleton
    @ShellCreateTriggerOverride
    @Provides
    static Object provideIndependentShellComponentsToCreate(
            @NonNull DelegateLetterboxTransitionObserver letterboxTransitionObserver,
            @NonNull LetterboxCommandHandler letterboxCommandHandler,
            @NonNull LetterboxTaskListenerAdapter letterboxTaskListenerAdapter,
            @NonNull LetterboxCleanupAdapter letterboxCleanupAdapter) {
        return new Object();
    }

    @WMSingleton
    @Provides
    static LetterboxDependenciesHelper provideLetterboxDependenciesHelper() {
        return new IgnoreLetterboxDependenciesHelper();
    }

    @WMSingleton
    @Provides
    @Named(SYSTEM_BAR_PANEL_LEFT_ID)
    static Optional<SystemBarWindow> provideLeftSystemBarWindow(Context context,
            Optional<PanelUpdateConsumer> consumer, EventDispatcher dispatcher) {
        if (consumer.isPresent()) {
            try {
                return Optional.of(
                        new SystemBarWindow(context, consumer, dispatcher,
                                SYSTEM_BAR_PANEL_LEFT_ID));
            } catch (IllegalStateException e) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    @WMSingleton
    @Provides
    @Named(SYSTEM_BAR_PANEL_TOP_ID)
    static Optional<SystemBarWindow> provideTopSystemBarWindow(Context context,
            Optional<PanelUpdateConsumer> consumer, EventDispatcher dispatcher) {
        if (consumer.isPresent()) {
            try {
                return Optional.of(
                        new SystemBarWindow(context, consumer, dispatcher,
                                SYSTEM_BAR_PANEL_TOP_ID));
            } catch (IllegalStateException e) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    @WMSingleton
    @Provides
    @Named(SYSTEM_BAR_PANEL_RIGHT_ID)
    static Optional<SystemBarWindow> provideRightSystemBarWindow(Context context,
            Optional<PanelUpdateConsumer> consumer, EventDispatcher dispatcher) {
        if (consumer.isPresent()) {
            try {
                return Optional.of(
                        new SystemBarWindow(context, consumer, dispatcher,
                                SYSTEM_BAR_PANEL_RIGHT_ID));
            } catch (IllegalStateException e) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    @WMSingleton
    @Provides
    @Named(SYSTEM_BAR_PANEL_BOTTOM_ID)
    static Optional<SystemBarWindow> provideBottomSystemBarWindow(Context context,
            Optional<PanelUpdateConsumer> consumer, EventDispatcher dispatcher) {
        if (consumer.isPresent()) {
            try {
                return Optional.of(
                        new SystemBarWindow(context, consumer, dispatcher,
                                SYSTEM_BAR_PANEL_BOTTOM_ID));
            } catch (IllegalStateException e) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    @WMSingleton
    @Provides
    @Named(SYSTEM_BAR_PANEL_LEFT_ID)
    static Optional<SystemBarConfiguration> provideLeftSystemBarConfiguration(
            Optional<PanelUpdateConsumer> consumer) {
        if (consumer.isPresent()) {
            try {
                return Optional.of(new SystemBarConfiguration(consumer, SYSTEM_BAR_PANEL_LEFT_ID));
            } catch (IllegalStateException e) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    @WMSingleton
    @Provides
    @Named(SYSTEM_BAR_PANEL_TOP_ID)
    static Optional<SystemBarConfiguration> provideTopSystemBarConfiguration(
            Optional<PanelUpdateConsumer> consumer) {
        if (consumer.isPresent()) {
            try {
                return Optional.of(new SystemBarConfiguration(consumer, SYSTEM_BAR_PANEL_TOP_ID));
            } catch (IllegalStateException e) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    @WMSingleton
    @Provides
    @Named(SYSTEM_BAR_PANEL_RIGHT_ID)
    static Optional<SystemBarConfiguration> provideRightSystemBarConfiguration(
            Optional<PanelUpdateConsumer> consumer) {
        if (consumer.isPresent()) {
            try {
                return Optional.of(new SystemBarConfiguration(consumer, SYSTEM_BAR_PANEL_RIGHT_ID));
            } catch (IllegalStateException e) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    @WMSingleton
    @Provides
    @Named(SYSTEM_BAR_PANEL_BOTTOM_ID)
    static Optional<SystemBarConfiguration> provideBottomSystemBarConfiguration(
            Optional<PanelUpdateConsumer> consumer) {
        if (consumer.isPresent()) {
            try {
                return Optional.of(
                        new SystemBarConfiguration(consumer, SYSTEM_BAR_PANEL_BOTTOM_ID));
            } catch (IllegalStateException e) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    @WMSingleton
    @Provides
    static Optional<HunWindow> provideHunWindow(Context context,
            Optional<PanelUpdateConsumer> consumer, EventDispatcher dispatcher) {
        if (consumer.isPresent()) {
            try {
                return Optional.of(
                        new HunWindow(context, consumer.get(), dispatcher));
            } catch (IllegalStateException e) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }
}
