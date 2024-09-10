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

package com.android.systemui.car.userswitcher;

import static android.car.settings.CarSettings.Global.ENABLE_USER_SWITCH_DEVELOPER_MESSAGE;

import static com.android.systemui.Flags.refactorGetCurrentUser;
import static com.android.systemui.car.Flags.userSwitchKeyguardShownTimeout;

import android.annotation.UserIdInt;
import android.app.ActivityManager;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.RemoteException;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.Settings;
import android.util.Log;
import android.view.IWindowManager;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.internal.annotations.VisibleForTesting;
import com.android.systemui.R;
import com.android.systemui.car.window.OverlayViewController;
import com.android.systemui.car.window.OverlayViewGlobalStateController;
import com.android.systemui.dagger.SysUISingleton;
import com.android.systemui.dagger.qualifiers.Main;
import com.android.systemui.util.concurrency.DelayableExecutor;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javax.inject.Inject;

/**
 * Handles showing and hiding UserSwitchTransitionView that is mounted to SystemUiOverlayWindow.
 */
@SysUISingleton
public class UserSwitchTransitionViewController extends OverlayViewController {
    private static final String TAG = "UserSwitchTransition";
    private static final String ENABLE_DEVELOPER_MESSAGE_TRUE = "true";
    private static final boolean DEBUG = Build.IS_DEBUGGABLE;
    // Amount of time to wait for keyguard to show before restarting SysUI (in seconds)
    private static final int KEYGUARD_SHOW_TIMEOUT = 20;

    private final Context mContext;
    private final Resources mResources;
    private final DelayableExecutor mMainExecutor;
    private final ActivityManager mActivityManager;
    private final UserManager mUserManager;
    private final IWindowManager mWindowManagerService;
    private final KeyguardManager mKeyguardManager;
    private final UserIconProvider mUserIconProvider = new UserIconProvider();
    private final int mWindowShownTimeoutMs;
    private final Runnable mWindowShownTimeoutCallback = () -> {
        if (DEBUG) {
            Log.w(TAG, "Window was not hidden within " + getWindowShownTimeoutMs() + " ms, so it"
                    + "was hidden by mWindowShownTimeoutCallback.");
        }

        handleHide();
    };

    // Whether the user switch transition is currently showing - only modified on main executor
    private boolean mTransitionViewShowing;
    // State when waiting for the keyguard to be shown as part of the user switch
    // Only modified on main executor
    private boolean mPendingKeyguardShow;
    // State when the a hide was attempted but ignored because the keyguard has not been shown yet
    // - once the keyguard is shown, the view should be hidden.
    // Only modified on main executor
    private boolean mPendingHideForKeyguardShown;

    private int mNewUserId = UserHandle.USER_NULL;
    private int mPreviousUserId = UserHandle.USER_NULL;
    private Runnable mCancelRunnable;

    @Inject
    public UserSwitchTransitionViewController(
            Context context,
            @Main Resources resources,
            @Main DelayableExecutor delayableExecutor,
            ActivityManager activityManager,
            UserManager userManager,
            IWindowManager windowManagerService,
            OverlayViewGlobalStateController overlayViewGlobalStateController) {

        super(R.id.user_switching_dialog_stub, overlayViewGlobalStateController);

        mContext = context;
        mResources = resources;
        mMainExecutor = delayableExecutor;
        mActivityManager = activityManager;
        mUserManager = userManager;
        mWindowManagerService = windowManagerService;
        mKeyguardManager = context.getSystemService(KeyguardManager.class);
        mWindowShownTimeoutMs = mResources.getInteger(
                R.integer.config_userSwitchTransitionViewShownTimeoutMs);
    }

    @Override
    protected int getInsetTypesToFit() {
        return 0;
    }

    @Override
    protected void showInternal() {
        populateDialog(mPreviousUserId, mNewUserId);
        super.showInternal();
    }

    /**
     * Makes the user switch transition view appear and draws the content inside of it if a user
     * that is different from the previous user is provided and if the dialog is not already
     * showing.
     */
    void handleShow(@UserIdInt int newUserId) {
        mMainExecutor.execute(() -> {
            if (mPreviousUserId == newUserId || mTransitionViewShowing) return;
            mTransitionViewShowing = true;
            try {
                mWindowManagerService.setSwitchingUser(true);
                if (!refactorGetCurrentUser()) {
                    mWindowManagerService.lockNow(null);
                }
            } catch (RemoteException e) {
                Log.e(TAG, "unable to notify window manager service regarding user switch");
            }

            mNewUserId = newUserId;
            start();
            // In case the window is still showing after WINDOW_SHOWN_TIMEOUT_MS, then hide the
            // window and log a warning message.
            mCancelRunnable = mMainExecutor.executeDelayed(mWindowShownTimeoutCallback,
                    mWindowShownTimeoutMs);

            if (refactorGetCurrentUser() && mKeyguardManager.isDeviceSecure(newUserId)) {
                // Setup keyguard timeout but don't lock the device just yet.
                // The device cannot be locked until we receive a user switching event - otherwise
                // the KeyguardViewMediator will not have the new userId.
                setupKeyguardShownTimeout();
            }
        });
    }

    void handleSwitching(int newUserId) {
        if (!refactorGetCurrentUser()) {
            return;
        }
        if (!mKeyguardManager.isDeviceSecure(newUserId)) {
            return;
        }
        mMainExecutor.execute(() -> {
            try {
                if (DEBUG) {
                    Log.d(TAG, "Notifying WM to lock device");
                }
                mWindowManagerService.lockNow(null);
            } catch (RemoteException e) {
                throw new RuntimeException("Error notifying WM of lock state", e);
            }
        });
    }

    void handleHide() {
        if (!mTransitionViewShowing) return;
        if (mPendingKeyguardShow) {
            if (DEBUG) {
                Log.d(TAG, "Delaying hide while waiting for keyguard to show");
            }
            // Prevent hiding transition view until device is locked - otherwise the home screen
            // may temporarily be exposed.
            mPendingHideForKeyguardShown = true;
            return;
        }
        mMainExecutor.execute(() -> {
            // next time a new user is selected, this current new user will be the previous user.
            mPreviousUserId = mNewUserId;
            mTransitionViewShowing = false;
            stop();
            if (mCancelRunnable != null) {
                mCancelRunnable.run();
            }
        });
    }

    @VisibleForTesting
    int getWindowShownTimeoutMs() {
        return mWindowShownTimeoutMs;
    }

    private void populateDialog(@UserIdInt int previousUserId, @UserIdInt int newUserId) {
        drawUserIcon(newUserId);
        populateLoadingText(previousUserId, newUserId);
    }

    private void drawUserIcon(int newUserId) {
        Drawable userIcon = mUserIconProvider.getDrawableWithBadge(mContext,
                mUserManager.getUserInfo(newUserId));
        ((ImageView) getLayout().findViewById(R.id.user_loading_avatar))
                .setImageDrawable(userIcon);
    }

    private void populateLoadingText(@UserIdInt int previousUserId, @UserIdInt int newUserId) {
        TextView msgView = getLayout().findViewById(R.id.user_loading);

        boolean showInfo = ENABLE_DEVELOPER_MESSAGE_TRUE.equals(
                Settings.Global.getString(mContext.getContentResolver(),
                        ENABLE_USER_SWITCH_DEVELOPER_MESSAGE));

        if (showInfo && mPreviousUserId != UserHandle.USER_NULL) {
            msgView.setText(
                    mResources.getString(R.string.car_loading_profile_developer_message,
                            previousUserId, newUserId));
        } else {
            // Show the switchingFromUserMessage if it was set.
            String switchingFromUserMessage = mActivityManager.getSwitchingFromUserMessage();
            msgView.setText(switchingFromUserMessage != null ? switchingFromUserMessage
                    : mResources.getString(R.string.car_loading_profile));
        }
    }

    /**
     * Wait for keyguard to be shown before hiding this blocking view.
     * This method does the following (in-order):
     * - Checks if the keyguard is already locked (and if so, do nothing else).
     * - Register a KeyguardLockedStateListener to be notified when the keyguard is locked.
     * - Start a 20 second timeout for keyguard to be shown. If it is not shown within this
     *   timeframe, SysUI/WM is in a bad state - crash SysUI and allow it to recover on restart.
     */
    @VisibleForTesting
    void setupKeyguardShownTimeout() {
        if (!userSwitchKeyguardShownTimeout()) {
            return;
        }
        if (mPendingKeyguardShow) {
            Log.w(TAG, "Attempted to setup timeout while pending keyguard show");
            return;
        }
        try {
            if (mWindowManagerService.isKeyguardLocked()) {
                return;
            }
        } catch (RemoteException e) {
            throw new RuntimeException("Unable to get current lock state from WM", e);
        }
        if (DEBUG) {
            Log.d(TAG, "Setting up keyguard show timeout");
        }
        mPendingKeyguardShow = true;
        // The executor's cancel runnable for the keyguard timeout (not the timeout itself)
        AtomicReference<Runnable> cancelKeyguardTimeout = new AtomicReference<>();
        KeyguardManager.KeyguardLockedStateListener keyguardLockedStateListener =
                new KeyguardManager.KeyguardLockedStateListener() {
                    @Override
                    public void onKeyguardLockedStateChanged(boolean isKeyguardLocked) {
                        if (DEBUG) {
                            Log.d(TAG, "Keyguard state change keyguardLocked=" + isKeyguardLocked);
                        }
                        if (isKeyguardLocked) {
                            mPendingKeyguardShow = false;
                            Runnable cancelTimeoutRunnable = cancelKeyguardTimeout.getAndSet(null);
                            if (cancelTimeoutRunnable != null) {
                                cancelTimeoutRunnable.run();
                            }
                            mKeyguardManager.removeKeyguardLockedStateListener(this);
                            if (mPendingHideForKeyguardShown) {
                                mPendingHideForKeyguardShown = false;
                                handleHide();
                            }
                        }
                    }
                };
        mKeyguardManager.addKeyguardLockedStateListener(mMainExecutor, keyguardLockedStateListener);

        Runnable keyguardTimeoutRunnable = () -> {
            mKeyguardManager.removeKeyguardLockedStateListener(keyguardLockedStateListener);
            // Keyguard did not show up in the expected timeframe - this indicates something is very
            // wrong. Crash SystemUI and allow it to recover on re-initialization.
            throw new RuntimeException(
                    String.format("Keyguard was not shown in %d seconds", KEYGUARD_SHOW_TIMEOUT));
        };
        cancelKeyguardTimeout.set(mMainExecutor.executeDelayed(keyguardTimeoutRunnable,
                KEYGUARD_SHOW_TIMEOUT, TimeUnit.SECONDS));
    }
}
