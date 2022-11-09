/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.systemui.car.keyguard;

import android.app.trust.TrustManager;
import android.content.Context;
import android.os.PowerManager;

import com.android.internal.jank.InteractionJankMonitor;
import com.android.internal.widget.LockPatternUtils;
import com.android.keyguard.KeyguardDisplayManager;
import com.android.keyguard.KeyguardUpdateMonitor;
import com.android.keyguard.KeyguardViewController;
import com.android.keyguard.mediator.ScreenOnCoordinator;
import com.android.systemui.animation.ActivityLaunchAnimator;
import com.android.systemui.broadcast.BroadcastDispatcher;
import com.android.systemui.car.users.CarSystemUIUserUtil;
import com.android.systemui.classifier.FalsingCollector;
import com.android.systemui.dreams.DreamOverlayStateController;
import com.android.systemui.dump.DumpManager;
import com.android.systemui.keyguard.DismissCallbackRegistry;
import com.android.systemui.keyguard.KeyguardUnlockAnimationController;
import com.android.systemui.keyguard.KeyguardViewMediator;
import com.android.systemui.navigationbar.NavigationModeController;
import com.android.systemui.shade.ShadeController;
import com.android.systemui.statusbar.NotificationShadeDepthController;
import com.android.systemui.statusbar.NotificationShadeWindowController;
import com.android.systemui.statusbar.SysuiStatusBarStateController;
import com.android.systemui.statusbar.phone.DozeParameters;
import com.android.systemui.statusbar.phone.ScreenOffAnimationController;
import com.android.systemui.statusbar.policy.KeyguardStateController;
import com.android.systemui.statusbar.policy.UserSwitcherController;
import com.android.systemui.util.DeviceConfigProxy;

import java.util.concurrent.Executor;

import dagger.Lazy;

/**
 * Car customizations on top of {@link KeyguardViewMediator}. Please refer to that class for
 * more details on specific functionalities.
 */
public class CarKeyguardViewMediator extends KeyguardViewMediator {
    private final Context mContext;

    /**
     * Injected constructor. See {@link CarKeyguardModule}.
     */
    public CarKeyguardViewMediator(Context context,
            FalsingCollector falsingCollector,
            LockPatternUtils lockPatternUtils,
            BroadcastDispatcher broadcastDispatcher,
            Lazy<KeyguardViewController> statusBarKeyguardViewManagerLazy,
            DismissCallbackRegistry dismissCallbackRegistry,
            KeyguardUpdateMonitor keyguardUpdateMonitor,
            DumpManager dumpManager,
            Executor uiBgExecutor, PowerManager powerManager,
            TrustManager trustManager,
            UserSwitcherController userSwitcherController,
            DeviceConfigProxy deviceConfig,
            NavigationModeController navigationModeController,
            KeyguardDisplayManager keyguardDisplayManager,
            DozeParameters dozeParameters,
            SysuiStatusBarStateController statusBarStateController,
            KeyguardStateController keyguardStateController,
            Lazy<KeyguardUnlockAnimationController> keyguardUnlockAnimationControllerLazy,
            ScreenOffAnimationController screenOffAnimationController,
            Lazy<NotificationShadeDepthController> notificationShadeDepthController,
            ScreenOnCoordinator screenOnCoordinator,
            InteractionJankMonitor interactionJankMonitor,
            DreamOverlayStateController dreamOverlayStateController,
            Lazy<ShadeController> mShadeControllerLazy,
            Lazy<NotificationShadeWindowController> notificationShadeWindowControllerLazy,
            Lazy<ActivityLaunchAnimator> activityLaunchAnimator) {
        super(context, falsingCollector, lockPatternUtils, broadcastDispatcher,
                statusBarKeyguardViewManagerLazy, dismissCallbackRegistry, keyguardUpdateMonitor,
                dumpManager, uiBgExecutor, powerManager, trustManager, userSwitcherController,
                deviceConfig, navigationModeController, keyguardDisplayManager, dozeParameters,
                statusBarStateController, keyguardStateController,
                keyguardUnlockAnimationControllerLazy, screenOffAnimationController,
                notificationShadeDepthController, screenOnCoordinator, interactionJankMonitor,
                dreamOverlayStateController,
                mShadeControllerLazy,
                notificationShadeWindowControllerLazy,
                activityLaunchAnimator);
        mContext = context;
    }

    @Override
    public void start() {
        if (CarSystemUIUserUtil.isSecondaryMUMDSystemUI(mContext)) {
            // Currently keyguard is not functional for the secondary users in a MUMD configuration
            // TODO_MD: make keyguard functional for secondary users
            return;
        }
        super.start();
    }
}
