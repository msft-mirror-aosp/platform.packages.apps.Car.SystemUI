/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.systemui.car.systembar;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.ComponentName;
import android.content.Context;
import android.content.om.OverlayManager;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Handler;
import android.os.RemoteException;
import android.os.UserHandle;
import android.util.Log;
import android.view.Display;
import android.view.IDisplayWindowInsetsController;
import android.view.IWindowManager;
import android.view.InsetsSource;
import android.view.InsetsSourceControl;
import android.view.InsetsState;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.inputmethod.ImeTracker;

import androidx.annotation.BinderThread;
import androidx.annotation.MainThread;

import com.android.internal.statusbar.IStatusBarService;
import com.android.systemui.R;
import com.android.systemui.car.CarDeviceProvisionedController;
import com.android.systemui.car.displaycompat.ToolbarController;
import com.android.systemui.car.users.CarSystemUIUserUtil;
import com.android.systemui.dagger.qualifiers.Main;
import com.android.systemui.plugins.DarkIconDispatcher;
import com.android.systemui.settings.DisplayTracker;
import com.android.systemui.settings.UserTracker;
import com.android.systemui.statusbar.CommandQueue;
import com.android.systemui.statusbar.phone.AutoHideController;
import com.android.systemui.statusbar.phone.LightBarController;
import com.android.systemui.statusbar.phone.PhoneStatusBarPolicy;
import com.android.systemui.statusbar.policy.ConfigurationController;
import com.android.systemui.statusbar.policy.KeyguardStateController;
import com.android.systemui.util.concurrency.DelayableExecutor;

import dagger.Lazy;

import java.util.HashSet;
import java.util.Set;

/**
 * b/259604616, This controller is created as a workaround for NavBar issues in concurrent
 * {@link CarSystemBar}/SystemUI.
 * Problem: CarSystemBar relies on {@link IStatusBarService},
 * which can register only one process to listen for the {@link CommandQueue} events.
 * Solution: {@link MDSystemBarsControllerImpl} intercepts Insets change event by registering the
 * {@link BinderThread} with
 * {@link IWindowManager#setDisplayWindowInsetsController(int, IDisplayWindowInsetsController)} and
 * notifies its listener for both Primary and Secondary SystemUI
 * process.
 */
public class MDSystemBarsControllerImpl extends CarSystemBarControllerImpl {

    private static final String TAG = MDSystemBarsControllerImpl.class.getSimpleName();
    private static final boolean DEBUG = Build.IS_ENG || Build.IS_USERDEBUG;
    private Set<Listener> mListeners;
    private int mDisplayId = Display.INVALID_DISPLAY;
    private InsetsState mCurrentInsetsState;
    private final IWindowManager mIWindowManager;
    private final Handler mMainHandler;
    private final Context mContext;
    private final Listener mListener = new Listener() {
        @Override
        public void onKeyboardVisibilityChanged(boolean show) {
            MDSystemBarsControllerImpl.this.updateKeyboardVisibility(show);
        }
    };
    private final OverlayManager mOverlayManager;

    private boolean mInitialized = false;

    public MDSystemBarsControllerImpl(IWindowManager wmService,
            @Main Handler mainHandler,
            Context context,
            UserTracker userTracker,
            CarSystemBarViewFactory carSystemBarViewFactory,
            SystemBarConfigs systemBarConfigs,
            // TODO(b/156052638): Should not need to inject LightBarController
            LightBarController lightBarController,
            DarkIconDispatcher darkIconDispatcher,
            WindowManager windowManager,
            CarDeviceProvisionedController deviceProvisionedController,
            CommandQueue commandQueue,
            AutoHideController autoHideController,
            ButtonSelectionStateListener buttonSelectionStateListener,
            @Main DelayableExecutor mainExecutor,
            IStatusBarService barService,
            Lazy<KeyguardStateController> keyguardStateControllerLazy,
            Lazy<PhoneStatusBarPolicy> iconPolicyLazy,
            ConfigurationController configurationController,
            CarSystemBarRestartTracker restartTracker,
            DisplayTracker displayTracker,
            @Nullable ToolbarController toolbarController) {
        super(context,
                userTracker,
                carSystemBarViewFactory,
                systemBarConfigs,
                lightBarController,
                darkIconDispatcher,
                windowManager,
                deviceProvisionedController,
                commandQueue,
                autoHideController,
                buttonSelectionStateListener,
                mainExecutor,
                barService,
                keyguardStateControllerLazy,
                iconPolicyLazy,
                configurationController,
                restartTracker,
                displayTracker,
                toolbarController);
        mIWindowManager = wmService;
        mMainHandler = mainHandler;
        mContext = context;
        mOverlayManager = context.getSystemService(OverlayManager.class);
    }

    @Override
    public void init() {
        mInitialized = false;

        String rroPackageName = mContext.getString(
                R.string.config_secondaryUserSystemUIRROPackageName);
        if (DEBUG) {
            Log.d(TAG, "start(), toggle RRO package:" + rroPackageName);
        }
        // The RRO must be applied to the user that SystemUI is running as.
        // MUPAND SystemUI runs as the system user, not the actual user.
        UserHandle userHandle = CarSystemUIUserUtil.isMUPANDSystemUI() ? UserHandle.SYSTEM
                : mUserTracker.getUserHandle();
        try {
             // TODO(b/260206944): Can remove this after we have a fix for overlaid resources not
             // applied.
             //
             // Currently because of Bug:b/260206944, RROs are not applied to the secondary user.
             // This class acts as a Mediator, which toggles the Overlay state of the RRO package,
             // which in turn triggers onConfigurationChange. Only after this change start the
             // CarSystemBar with overlaid resources.
            mOverlayManager.setEnabled(rroPackageName, false, userHandle);
            mOverlayManager.setEnabled(rroPackageName, true, userHandle);
        } catch (IllegalArgumentException ex) {
            Log.w(TAG, "Failed to set overlay package: " + ex);
            mInitialized = true;
            super.init();
        }
    }

    @Override
    public void onConfigChanged(Configuration newConfig) {
        if (!mInitialized) {
            mInitialized = true;
            super.init();
        } else {
            super.onConfigChanged(newConfig);
        }
    }

    @Override
    protected void createSystemBar() {
        if (!CarSystemUIUserUtil.isSecondaryMUMDSystemUI()) {
            super.createSystemBar();
        } else {
            addListener(mListener);
            createNavBar();
        }
    }

    /**
     * Adds a listener for the display.
     * Adding a listener to a Display, replaces previous binder callback to this
     * displayId
     * {@link IWindowManager#setDisplayWindowInsetsController(int, IDisplayWindowInsetsController)}
     * A SystemUI process should only register to a single display with displayId
     * {@link Context#getDisplayId()}
     *
     * Note: {@link  Context#getDisplayId()} will return the {@link Context#DEVICE_ID_DEFAULT}, if
     * called in the constructor. As this component's constructor is called before the DisplayId
     * gets assigned to the context.
     *
     * @param listener SystemBar Inset events
     */
    @MainThread
    private void addListener(Listener listener) {
        if (mDisplayId != Display.INVALID_DISPLAY && mDisplayId != mContext.getDisplayId()) {
            Log.e(TAG, "Unexpected Display Id change");
            mListeners = null;
            mCurrentInsetsState = null;
            unregisterWindowInsetController(mDisplayId);
        }
        if (mListeners != null) {
            mListeners.add(listener);
            return;
        }
        mDisplayId = mContext.getDisplayId();
        mListeners = new HashSet<>();
        mListeners.add(listener);
        registerWindowInsetController(mDisplayId);
    }

    private void registerWindowInsetController(int displayId) {
        if (DEBUG) {
            Log.d(TAG, "Registering a WindowInsetController with Display: " + displayId);
        }
        try {
            mIWindowManager.setDisplayWindowInsetsController(displayId,
                    new DisplayWindowInsetsControllerImpl());
        } catch (RemoteException e) {
            Log.w(TAG, "Unable to set insets controller on display " + displayId);
        }
    }

    private void unregisterWindowInsetController(int displayId) {
        if (DEBUG) {
            Log.d(TAG, "Unregistering a WindowInsetController with Display: " + displayId);
        }
        try {
            mIWindowManager.setDisplayWindowInsetsController(displayId, null);
        } catch (RemoteException e) {
            Log.w(TAG, "Unable to remove insets controller on display " + displayId);
        }
    }

    @BinderThread
    private class DisplayWindowInsetsControllerImpl
            extends IDisplayWindowInsetsController.Stub {
        @Override
        public void topFocusedWindowChanged(ComponentName component,
                @WindowInsets.Type.InsetsType int requestedVisibleTypes) {
            //no-op
        }

        @Override
        public void insetsChanged(InsetsState insetsState) {
            if (insetsState == null || insetsState.equals(mCurrentInsetsState)) {
                return;
            }
            mCurrentInsetsState = insetsState;
            if (mListeners == null) {
                return;
            }
            boolean show = insetsState.isSourceOrDefaultVisible(InsetsSource.ID_IME,
                    WindowInsets.Type.ime());
            mMainHandler.post(() -> {
                for (Listener l : mListeners) {
                    l.onKeyboardVisibilityChanged(show);
                }
            });
        }

        @Override
        public void insetsControlChanged(InsetsState insetsState,
                InsetsSourceControl[] activeControls) {
            //no-op
        }

        @Override
        public void showInsets(@WindowInsets.Type.InsetsType int types, boolean fromIme,
                @Nullable ImeTracker.Token statsToken) {
            //no-op
        }

        @Override
        public void hideInsets(@WindowInsets.Type.InsetsType int types, boolean fromIme,
                @Nullable ImeTracker.Token statsToken) {
            //no-op
        }

        @Override
        public void setImeInputTargetRequestedVisibility(boolean visible,
                @NonNull ImeTracker.Token statsToken) {
            //no-op
        }
    }

    /**
     * Remove a listener for a display
     *
     * @param listener SystemBar Inset events Listener
     * @return if set contains such a listener, returns {@code true} otherwise false
     */
    public boolean removeListener(Listener listener) {
        if (mListeners == null) {
            return false;
        }
        return mListeners.remove(listener);
    }

    /**
     * Listener for SystemBar insets events
     */
    public interface Listener {
        /**
         * show/hide keyboard
         */
        void onKeyboardVisibilityChanged(boolean showing);
    }
}
