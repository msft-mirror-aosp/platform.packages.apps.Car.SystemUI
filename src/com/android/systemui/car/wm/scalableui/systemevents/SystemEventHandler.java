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
package com.android.systemui.car.wm.scalableui.systemevents;

import static android.car.user.CarUserManager.USER_LIFECYCLE_EVENT_TYPE_SWITCHING;
import static android.car.user.CarUserManager.USER_LIFECYCLE_EVENT_TYPE_UNLOCKED;
import static android.content.res.Configuration.ORIENTATION_LANDSCAPE;
import static android.content.res.Configuration.ORIENTATION_PORTRAIT;

import static com.android.systemui.car.wm.scalableui.systemevents.SystemEventConstants.SYSTEM_BEFORE_USER_SWITCH_EVENT_ID;
import static com.android.systemui.car.wm.scalableui.systemevents.SystemEventConstants.SYSTEM_ENTER_SUW_EVENT_ID;
import static com.android.systemui.car.wm.scalableui.systemevents.SystemEventConstants.SYSTEM_EXIT_SUW_EVENT_ID;
import static com.android.systemui.car.wm.scalableui.systemevents.SystemEventConstants.SYSTEM_KEYGUARD_HIDDEN_EVENT_ID;
import static com.android.systemui.car.wm.scalableui.systemevents.SystemEventConstants.SYSTEM_KEYGUARD_SHOWN_EVENT_ID;
import static com.android.systemui.car.wm.scalableui.systemevents.SystemEventConstants.SYSTEM_USER_AUTHENTICATED_EVENT_ID;
import static com.android.systemui.car.wm.scalableui.systemevents.SystemEventConstants.SYSTEM_USER_SWITCH_COMPLETE_EVENT_ID;
import static com.android.systemui.car.wm.scalableui.systemevents.SystemEventConstants.SYSTEM_USER_SWITCH_ON_AUTHENTICATED_TOKEN_ID;

import android.app.KeyguardManager;
import android.car.user.CarUserManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.TypedArray;
import android.os.Build;
import android.os.UserManager;
import android.util.Log;
import android.view.Display;

import androidx.annotation.NonNull;

import com.android.car.scalableui.loader.xml.XmlModelLoader;
import com.android.car.scalableui.manager.ActionManager;
import com.android.car.scalableui.manager.StateManager;
import com.android.car.scalableui.model.Action;
import com.android.car.scalableui.model.Event;
import com.android.car.scalableui.model.PanelState;
import com.android.car.scalableui.panel.Panel;
import com.android.car.scalableui.panel.PanelPool;
import com.android.systemui.CoreStartable;
import com.android.systemui.R;
import com.android.systemui.car.CarDeviceProvisionedController;
import com.android.systemui.car.CarDeviceProvisionedListener;
import com.android.systemui.car.CarServiceProvider;
import com.android.systemui.car.display.DisplayStateHelper;
import com.android.systemui.car.flags.Flag;
import com.android.systemui.car.flags.FlagManager;
import com.android.systemui.car.wm.scalableui.EventDispatcher;
import com.android.systemui.dagger.SysUISingleton;
import com.android.systemui.dagger.qualifiers.Background;
import com.android.systemui.settings.DisplayTracker;
import com.android.systemui.settings.UserTracker;
import com.android.systemui.statusbar.policy.ConfigurationController;
import com.android.systemui.statusbar.policy.KeyguardStateController;

import dagger.Lazy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

import javax.inject.Inject;

/**
 * A system event handler that listens for user lifecycle events and device provisioning state
 * changes.
 *
 * <p>This class dispatches events to the {@link StateManager} when a user is unlocked or when
 * the device
 * is being set up.
 */
@SysUISingleton
public class SystemEventHandler implements CoreStartable,
        ConfigurationController.ConfigurationListener {
    private static final String TAG = SystemEventHandler.class.getSimpleName();
    private static final boolean DEBUG = Build.IS_DEBUGGABLE;

    private final Context mContext;
    private final UserManager mUserManager;
    private final CarServiceProvider mCarServiceProvider;
    private final UserTracker mUserTracker;
    private final DisplayTracker mDisplayTracker;
    private final Lazy<DisplayStateHelper> mDisplayStateHelper;
    private final KeyguardStateController mKeyguardStateController;
    private final Executor mBackgroundExecutor;
    private final CarDeviceProvisionedController mCarDeviceProvisionedController;
    private final EventDispatcher mEventDispatcher;
    private final FlagManager mFlagManager;

    private CarUserManager mCarUserManager;
    private boolean mIsUserSetupInProgress;
    // Flag to track if reset has already been called for this user
    private boolean mResetCalledForUser = false;
    private boolean mIsUserSwitching = true;
    private boolean mIsKeyguardShowing;
    private int mCurrentOrientation;

    private final CarUserManager.UserLifecycleListener mUserLifecycleListener =
            new CarUserManager.UserLifecycleListener() {
                @Override
                public void onEvent(@NonNull CarUserManager.UserLifecycleEvent event) {
                    if (DEBUG) {
                        Log.d(TAG, "on User event = " + event);
                    }
                    if (event.getUserHandle().isSystem()) {
                        Log.i(TAG, "Ignore system event");
                        return;
                    }

                    if (event.getUserId() != mUserTracker.getUserId()) {
                        Log.i(TAG, "Not current user" + event.getUserId());
                        return;
                    }

                    if (event.getEventType() == USER_LIFECYCLE_EVENT_TYPE_SWITCHING) {
                        // reset flag when switching
                        mResetCalledForUser = false;
                        mIsUserSwitching = true;
                    } else if (event.getEventType() == USER_LIFECYCLE_EVENT_TYPE_UNLOCKED) {
                        if (shouldResetPanels()) {
                            Log.d(TAG, "Resetting panels during user unlock");
                            // TODO(b/432217693): remove once visibility barrier is not home
                            Intent homeIntent = new Intent(Intent.ACTION_MAIN);
                            homeIntent.addCategory(Intent.CATEGORY_HOME);
                            homeIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            mContext.startActivityAsUser(homeIntent,
                                    mUserTracker.getUserHandle());

                            StateManager.handlePanelReset();
                            mResetCalledForUser = true;
                        } else {
                            Log.d(TAG, "Received user unlock while user is not setup");
                        }

                        if (!mIsKeyguardShowing) {
                            sendUserAuthEvent();
                        }
                    } else {
                        Log.i(TAG, "Ignore system event" + event.getEventType());
                    }
                }
            };

    private final CarDeviceProvisionedListener mCarDeviceProvisionedListener =
            new CarDeviceProvisionedListener() {
                @Override
                public void onUserSetupChanged() {
                    if (mUserTracker.getUserHandle().isSystem()) {
                        // don't handle headless system user
                        return;
                    }
                    if (mUserManager.isUserUnlocked(mUserTracker.getUserId())
                            && shouldResetPanels()) {
                        Log.d(TAG, "Resetting panels during user setup state change");
                        StateManager.handlePanelReset();
                        mResetCalledForUser = true;
                    }
                }

                @Override
                public void onUserSetupInProgressChanged() {
                    updateUserSetupState();
                }

                @Override
                public void onDeviceProvisionedChanged() {
                    updateUserSetupState();
                }

                @Override
                public void onUserSwitched() {
                    updateUserSetupState();
                }
            };

    private final UserTracker.Callback mUserTrackerCallback = new UserTracker.Callback() {
        @Override
        public void onBeforeUserSwitching(int newUser) {
            sendEvent(new Event.Builder(SYSTEM_BEFORE_USER_SWITCH_EVENT_ID));
        }

        @Override
        public void onUserChanged(int newUser, @NonNull Context userContext) {
            sendEvent(new Event.Builder(SYSTEM_USER_SWITCH_COMPLETE_EVENT_ID));
        }
    };

    private final DisplayStateHelper.Listener mDisplayStateListener =
            new DisplayStateHelper.Listener() {
                @Override
                public void onDisplayPowerStateChanged(int displayId, boolean isOn) {
                    if (isOn && mUserManager.isUserUnlocked(mUserTracker.getUserId())
                            && !mIsKeyguardShowing) {
                        sendUserAuthEvent();
                    }
                }
            };

    @Inject
    public SystemEventHandler(
            Context context,
            UserManager userManager,
            @Background Executor bgExecutor,
            CarServiceProvider carServiceProvider,
            UserTracker userTracker,
            DisplayTracker displayTracker,
            Lazy<DisplayStateHelper> displayStateHelper,
            KeyguardStateController keyguardStateController,
            KeyguardManager keyguardManager,
            CarDeviceProvisionedController carDeviceProvisionedController,
            EventDispatcher dispatcher,
            FlagManager flagManager
    ) {
        mContext = context;
        mUserManager = userManager;
        mBackgroundExecutor = bgExecutor;
        mCarServiceProvider = carServiceProvider;
        mUserTracker = userTracker;
        mDisplayTracker = displayTracker;
        mDisplayStateHelper = displayStateHelper;
        mKeyguardStateController = keyguardStateController;
        mCarDeviceProvisionedController = carDeviceProvisionedController;
        mEventDispatcher = dispatcher;
        mFlagManager = flagManager;
        mCurrentOrientation = mContext.getResources().getConfiguration().orientation;
    }

    private void updateUserSetupState() {
        boolean isUserSetupInProgress = !mCarDeviceProvisionedController.isCurrentUserFullySetup();
        if (isUserSetupInProgress != mIsUserSetupInProgress) {
            mIsUserSetupInProgress = isUserSetupInProgress;
            notifySuwStateEvent();
        }
    }

    private void notifySuwStateEvent() {
        String eventId =
                mIsUserSetupInProgress ? SYSTEM_ENTER_SUW_EVENT_ID : SYSTEM_EXIT_SUW_EVENT_ID;
        sendEvent(new Event.Builder(eventId));
    }

    @Override
    public void start() {
        if (mFlagManager.isEnabled(Flag.ScalableUIEnabled)) {
            registerUserEventListener();
            registerProvisionedStateListener();
            mUserTracker.addCallback(mUserTrackerCallback, mBackgroundExecutor);
            mDisplayStateHelper.get().addListener(mDisplayStateListener);
            registerKeyguardStateListener();
        }
    }

    @Override
    public void onUiModeChanged() {
        PanelPool.getInstance().forEach(Panel::refreshTheme);
    }

    @Override
    public void onOrientationChanged(int orientation) {
        if (mCurrentOrientation != orientation && (ORIENTATION_LANDSCAPE == orientation
                || ORIENTATION_PORTRAIT == orientation)) {
            mCurrentOrientation = orientation;
            XmlModelLoader loader = new XmlModelLoader(mContext);

            TypedArray states = mContext.getResources().obtainTypedArray(R.array.window_states);
            List<PanelState> panelStateList = new ArrayList<>();
            for (int i = 0; i < states.length(); i++) {
                int xmlResId = states.getResourceId(i, 0);
                PanelState panelState = loader.createPanelState(xmlResId);
                panelStateList.add(panelState);
            }
            StateManager.reloadPanelState(panelStateList);

            List<Action> actions = loader.createActions(R.xml.scalable_ui_actions);
            ActionManager.setActions(actions);
        }
    }

    private void registerProvisionedStateListener() {
        mIsUserSetupInProgress = !mCarDeviceProvisionedController.isCurrentUserFullySetup();
        notifySuwStateEvent();
        mCarDeviceProvisionedController.addCallback(mCarDeviceProvisionedListener);
    }

    private void registerUserEventListener() {
        mCarServiceProvider.addListener(car -> {
            mCarUserManager = car.getCarManager(CarUserManager.class);
            if (mCarUserManager != null) {
                mCarUserManager.addListener(mBackgroundExecutor, mUserLifecycleListener);
            }
        });
    }

    private void registerKeyguardStateListener() {
        mIsKeyguardShowing = isKeyguardShowing();
        mKeyguardStateController.addCallback(new KeyguardStateController.Callback() {
            @Override
            public void onKeyguardShowingChanged() {
                keyguardShowingChanged(isKeyguardShowing());
            }
        });
    }

    private void keyguardShowingChanged(boolean showing) {
        if (mIsKeyguardShowing == showing) {
            return;
        }
        mIsKeyguardShowing = showing;
        if (mIsKeyguardShowing) {
            sendEvent(new Event.Builder(SYSTEM_KEYGUARD_SHOWN_EVENT_ID));
        } else {
            sendEvent(new Event.Builder(SYSTEM_KEYGUARD_HIDDEN_EVENT_ID));
            if (mUserManager.isUserUnlocked(mUserTracker.getUserId())) {
                sendUserAuthEvent();
            }
        }
    }

    private boolean isKeyguardShowing() {
        return mKeyguardStateController.isShowing();
    }

    private boolean shouldResetPanels() {
        return mCarDeviceProvisionedController.isUserSetup(mUserTracker.getUserId())
                && !mResetCalledForUser;
    }

    private void sendEvent(Event.Builder builder) {
        mEventDispatcher.executeEvent(builder.addApplicableDisplays(
                Arrays.stream(mDisplayTracker.getAllDisplays())
                        .map(Display::getDisplayId)
                        .collect(Collectors.toList())).build());
    }

    private void sendUserAuthEvent() {
        sendEvent(new Event.Builder(
                SYSTEM_USER_AUTHENTICATED_EVENT_ID)
                .addToken(SYSTEM_USER_SWITCH_ON_AUTHENTICATED_TOKEN_ID,
                        Boolean.toString(mIsUserSwitching)));
        mIsUserSwitching = false;
    }
}
