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

package com.android.systemui.car.userpicker;

import static android.app.ActivityManager.USER_OP_SUCCESS;
import static android.car.CarOccupantZoneManager.INVALID_USER_ID;
import static android.car.user.CarUserManager.USER_LIFECYCLE_EVENT_TYPE_CREATED;
import static android.car.user.CarUserManager.USER_LIFECYCLE_EVENT_TYPE_REMOVED;
import static android.car.user.CarUserManager.USER_LIFECYCLE_EVENT_TYPE_STARTING;
import static android.car.user.CarUserManager.USER_LIFECYCLE_EVENT_TYPE_STOPPED;
import static android.car.user.CarUserManager.USER_LIFECYCLE_EVENT_TYPE_STOPPING;
import static android.car.user.CarUserManager.USER_LIFECYCLE_EVENT_TYPE_SWITCHING;
import static android.car.user.CarUserManager.USER_LIFECYCLE_EVENT_TYPE_UNLOCKED;
import static android.car.user.CarUserManager.lifecycleEventTypeToString;
import static android.os.UserHandle.USER_ALL;
import static android.os.UserHandle.USER_SYSTEM;
import static android.os.UserManager.SWITCHABILITY_STATUS_OK;
import static android.os.UserManager.isHeadlessSystemUserMode;

import android.annotation.MainThread;
import android.annotation.Nullable;
import android.annotation.UserIdInt;
import android.app.ActivityManager;
import android.app.IActivityManager;
import android.car.CarOccupantZoneManager;
import android.car.user.CarUserManager;
import android.car.user.CarUserManager.UserLifecycleListener;
import android.car.user.UserCreationResult;
import android.car.user.UserLifecycleEventFilter;
import android.car.util.concurrent.AsyncFuture;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.UserInfo;
import android.os.Handler;
import android.os.Looper;
import android.os.RemoteException;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.ArraySet;
import android.util.Log;
import android.util.Slog;
import android.util.SparseArray;
import android.util.SparseIntArray;

import com.android.internal.annotations.GuardedBy;
import com.android.systemui.R;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

/**
 * Helper class for {@link UserManager}, this is meant to be used by builds that support
 * {@link UserManager#isVisibleBackgroundUsersEnabled() Multi-user model with Concurrent Multi
 * User Feature.}
 *
 * <p>This class handles user event such as creating, removing, unlocking, stopped, and so on.
 * Also, it provides methods for creating, stopping, starting users.
 */
@UserPickerScope
public final class UserEventManager {
    private static final String TAG = UserEventManager.class.getSimpleName();
    private static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);

    private static final long CREATE_USER_TIMEOUT_MS = 10_000;

    private final UserLifecycleEventFilter mFilter = new UserLifecycleEventFilter.Builder()
            .addEventType(USER_LIFECYCLE_EVENT_TYPE_SWITCHING)
            .addEventType(USER_LIFECYCLE_EVENT_TYPE_CREATED)
            .addEventType(USER_LIFECYCLE_EVENT_TYPE_REMOVED)
            .addEventType(USER_LIFECYCLE_EVENT_TYPE_UNLOCKED)
            .addEventType(USER_LIFECYCLE_EVENT_TYPE_STARTING)
            .addEventType(USER_LIFECYCLE_EVENT_TYPE_STOPPING)
            .addEventType(USER_LIFECYCLE_EVENT_TYPE_STOPPED).build();

    private final Context mContext;
    private final UserManager mUserManager;
    private final ActivityManager mActivityManager;
    private final CarServiceMediator mCarServiceMediator;

    /**
     * {@link UserPickerController} is per-display object. It adds listener to UserEventManager to
     * update user information, and UserEventManager will call listeners whenever user event occurs.
     * mUpdateListeners is used only on main thread.
     */
    private final SparseArray<OnUpdateUsersListener> mUpdateListeners;

    /**
     * Same user icon can be clicked from different displays, and then, different thread can be
     * started for user login with same user id. In this case, mUsersLoginStarted will block the
     * thread of the next clicked display.
     */
    @GuardedBy("mLock")
    private final SparseIntArray mUsersLoginStarted;

    /**
     * When changing the user to another one, the user will be stopped and new user is started.
     * In this situation, if we click the stopping user icon in user picker on another display,
     * that user can not login to secondary displays by the core logic for 'pending user start'.
     * (b/254526109) To avoid this, we track the stopping users to block the stopping user is
     * clicked until completely stopped.
     */
    @GuardedBy("mLock")
    private final Set<Integer> mStoppingUsers = new ArraySet<>();

    private final Object mLock = new Object();
    private final Handler mMainHandler;

    /**
     * We don't use the main thread for UX responsiveness when handling user events.
     */
    private final ExecutorService mUserLifecycleReceiver;

    private final UserLifecycleListener mUserLifecycleListener = event -> {
        int eventType = event.getEventType();
        int userId = event.getUserId();
        if (DEBUG) {
            Slog.d(TAG, "event=" + lifecycleEventTypeToString(eventType) + " userId=" + userId);
        }
        if (eventType == USER_LIFECYCLE_EVENT_TYPE_STOPPING) {
            addStoppingUserId(userId);
        } else if (eventType == USER_LIFECYCLE_EVENT_TYPE_STOPPED) {
            if (isStoppingUser(userId)) {
                removeStoppingUserId(userId);
            }
        }
        runUpdateUsersOnMainThread(userId, eventType);
    };

    private final BroadcastReceiver mUserUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            runUpdateUsersOnMainThread();
        }
    };

    @Inject
    UserEventManager(Context context, CarServiceMediator carServiceMediator) {
        mUpdateListeners = new SparseArray<>();
        mUsersLoginStarted = new SparseIntArray();
        mContext = context.getApplicationContext();
        mUserLifecycleReceiver = Executors.newSingleThreadExecutor();
        mMainHandler = new Handler(Looper.getMainLooper());
        mUserManager = mContext.getSystemService(UserManager.class);
        mActivityManager = mContext.getSystemService(ActivityManager.class);
        mCarServiceMediator = carServiceMediator;
        mCarServiceMediator.registerUserChangeEventsListener(mUserLifecycleReceiver, mFilter,
                mUserLifecycleListener);
        registerUserInfoChangedReceiver();
    }

    /**
     * This method is to prevent repeated clicks on the same user icon on different displays.
     * It is called before starting user, and check the user id is in mUsersLoginStarted or not.
     * If the user id exists on there, it returns false, and worker thread which is responsible for
     * user start can not start the user. Otherwise, the user id is mapped with the display id, it
     * returns true, and worker can start the user.
     *
     * @param displayId user want to login
     * @param userId to login to the display
     * @return true if user can log in to the display, otherwise false.
     */
    boolean setUserLoginStarted(int displayId, @UserIdInt int userId) {
        if (DEBUG) {
            Slog.d(TAG, "setUserLoginStarted: userId=" + userId + " displayId=" + displayId);
        }
        synchronized (mLock) {
            for (int i = 0; i < mUsersLoginStarted.size(); i++) {
                int startedUser = mUsersLoginStarted.valueAt(i);
                if (startedUser == userId) {
                    Slog.w(TAG, "setUserLoginStarted: already started on display "
                            + mUsersLoginStarted.keyAt(i));
                    return false;
                }
            }
            mUsersLoginStarted.put(displayId, userId);
            return true;
        }
    }

    /**
     * This method is to release user id from mUsersLoginStarted. When the started user is unlocked
     * state, it is called, or it can be called if it succeeded in preoccupying the display by
     * adding the user id to the map, but failed to start the user in the subsequent process.
     */
    void resetUserLoginStarted(int displayId) {
        if (DEBUG) {
            Slog.d(TAG, "resetUserLoginStarted: displayId=" + displayId);
        }
        synchronized (mLock) {
            mUsersLoginStarted.put(displayId, INVALID_USER_ID);
        }
    }

    /**
     * Gets user id to be logging into the display.
     * It is used when the user is unlocked.
     *
     * @param displayId
     * @return user id to be logging into the display
     */
    int getUserLoginStarted(int displayId) {
        synchronized (mLock) {
            return mUsersLoginStarted.get(displayId, INVALID_USER_ID);
        }
    }

    // The user who is stopping can not start again on secondary displays now.<b/254526109>
    // So, we manage the list of stopping users, and block starting them again until they are
    // completely stopped. It is short term solution to solve the problem.
    private void addStoppingUserId(@UserIdInt int userId) {
        synchronized (mLock) {
            mStoppingUsers.add(userId);
        }
    }

    // removes from the blocked list when completely stopped
    private void removeStoppingUserId(Integer userId) {
        synchronized (mLock) {
            mStoppingUsers.remove(userId);
        }
    }

    // check whether the user is on stopping.
    boolean isStoppingUser(@UserIdInt int userId) {
        synchronized (mLock) {
            return mStoppingUsers.contains(userId);
        }
    }

    private void registerUserInfoChangedReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_USER_INFO_CHANGED);
        mContext.registerReceiverAsUser(mUserUpdateReceiver, UserHandle.ALL, filter, null, null);
    }

    void registerOnUpdateUsersListener(OnUpdateUsersListener listener, int displayId) {
        if (listener == null) {
            return;
        }
        mUpdateListeners.put(displayId, listener);
    }

    void unregisterOnUpdateUsersListener(int displayId) {
        mUpdateListeners.remove(displayId);
    }

    @MainThread
    private void updateUsers(@UserIdInt int userId, int userEvent) {
        for (int i = 0; i < mUpdateListeners.size(); i++) {
            OnUpdateUsersListener listener = mUpdateListeners.valueAt(i);
            if (listener != null) {
                listener.onUpdateUsers(userId, userEvent);
            }
        }
    }

    void runUpdateUsersOnMainThread() {
        runUpdateUsersOnMainThread(USER_ALL, 0);
    }

    void runUpdateUsersOnMainThread(@UserIdInt int userId, int userEvent) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mMainHandler.post(() -> updateUsers(userId, userEvent));
        } else {
            updateUsers(userId, userEvent);
        }
    }

    static int getMaxSupportedUsers() {
        int maxSupportedUsers = UserManager.getMaxSupportedUsers();
        if (isHeadlessSystemUserMode()) {
            maxSupportedUsers -= 1;
        }
        return maxSupportedUsers;
    }

    UserInfo getUserInfo(@UserIdInt int userId) {
        return mUserManager.getUserInfo(userId);
    }

    UserInfo getCurrentForegroundUserInfo() {
        return mUserManager.getUserInfo(ActivityManager.getCurrentUser());
    }

    /**
     * Gets alive users from user manager except guest users to create user records.
     * If it is headless system user mode, removes system user info from the list by
     * {@link UserManager#getAliveUsers}.
     *
     * @return the list of users that were created except guest users.
     */
    List<UserInfo> getAliveUsers() {
        List<UserInfo> aliveUsers = mUserManager.getAliveUsers();
        for (int i = aliveUsers.size() - 1; i >= 0; i--) {
            UserInfo userInfo = aliveUsers.get(i);
            if ((isHeadlessSystemUserMode() && userInfo.id == USER_SYSTEM)
                    || userInfo.isGuest()) {
                aliveUsers.remove(i);
            }
        }
        return aliveUsers;
    }

    boolean isUserLimitReached() {
        int countNonGuestUsers = getAliveUsers().size();
        int maxSupportedUsers = getMaxSupportedUsers();

        if (countNonGuestUsers > maxSupportedUsers) {
            Slog.e(TAG, "There are more users on the device than allowed.");
            return true;
        }
        return countNonGuestUsers == maxSupportedUsers;
    }

    boolean canForegroundUserAddUsers() {
        return !mUserManager.hasUserRestrictionForUser(UserManager.DISALLOW_ADD_USER,
                UserHandle.of(ActivityManager.getCurrentUser()));
    }

    boolean isForegroundUserNotSwitchable(UserHandle fgUserHandle) {
        return mUserManager.getUserSwitchability(fgUserHandle) != SWITCHABILITY_STATUS_OK;
    }

    @Nullable
    UserCreationResult createNewUser() {
        CarUserManager carUserManager = mCarServiceMediator.getCarUserManager();
        AsyncFuture<UserCreationResult> future = carUserManager.createUser(
                mContext.getString(R.string.car_new_user), 0);
        return getUserCreationResult(future);
    }

    @Nullable
    UserCreationResult createGuest() {
        CarUserManager carUserManager = mCarServiceMediator.getCarUserManager();
        AsyncFuture<UserCreationResult> future = carUserManager.createGuest(
                mContext.getString(R.string.car_guest));
        return getUserCreationResult(future);
    }

    @Nullable
    private UserCreationResult getUserCreationResult(AsyncFuture<UserCreationResult> future) {
        UserCreationResult result = null;
        try {
            result = future.get(CREATE_USER_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            if (result == null) {
                Slog.e(TAG, "Timed out creating guest after " + CREATE_USER_TIMEOUT_MS + "ms...");
                return null;
            }
        } catch (InterruptedException e) {
            Slog.w(TAG, "Interrupted waiting for future " + future, e);
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception e) {
            Slog.w(TAG, "Exception getting future " + future, e);
            return null;
        }

        return result;
    }

    boolean isUserRunningUnlocked(@UserIdInt int userId) {
        return mUserManager.isUserRunning(userId) && mUserManager.isUserUnlocked(userId);
    }

    boolean isUserRunning(@UserIdInt int userId) {
        return mUserManager.isUserRunning(userId);
    }

    boolean startUserForDisplay(@UserIdInt int prevCurrentUser, @UserIdInt int userId,
            int displayId, boolean isFgUserStart) {
        if (DEBUG) {
            Slog.d(TAG, "switchToUserForDisplay " + userId + " State :  Running "
                    + mUserManager.isUserRunning(userId) + " Unlocked "
                    + mUserManager.isUserUnlocked(userId) + " displayId=" + displayId
                    + " prevCurrentUser=" + prevCurrentUser + " isFgUserStart=" + isFgUserStart);
        }
        boolean isUserStarted = false;
        try {
            if (isFgUserStart) {
                // Old user will be stopped by {@link UserController} after user switching
                // completed. In the case of user switching, to avoid clicking stopping user, we can
                // block previous current user immediately here by adding to the list of stopping
                // users.
                addStoppingUserId(prevCurrentUser);
                isUserStarted = mActivityManager.switchUser(userId);
            } else {
                // TODO(b/257335554): will be changed to use CarUserManager.
                isUserStarted = mActivityManager
                        .startUserInBackgroundVisibleOnDisplay(userId, displayId);
            }
        } catch (Exception e) {
            Slog.e(TAG, "switchUser or startUserInBackgroundOnSecondaryDisplay failed.", e);
        } finally {
            if (!isUserStarted) {
                if (isFgUserStart) {
                    Slog.w(TAG, "could not switch user on display " + displayId);
                } else {
                    Slog.w(TAG, "could not start user in background on display " + displayId);
                }
            }
        }
        return isUserStarted;
    }

    boolean stopUserUnchecked(@UserIdInt int userId, int displayId) {
        if (DEBUG) {
            Slog.d(TAG, "stop user:" + userId);
        }
        boolean isStopping = false;

        // Unassign the user from the occupant zone before stop user.
        if (mCarServiceMediator.unassignOccupantZoneForDisplay(displayId)
                != CarOccupantZoneManager.USER_ASSIGNMENT_RESULT_OK) {
            Slog.e(TAG, "failed to unassign occupant zone for display " + displayId
                    + " when stopping user " + userId);
            return false;
        }

        try {
            // TODO(b/257335554): will be changed to use CarUserManager.
            IActivityManager am = ActivityManager.getService();
            isStopping = am.stopUserWithDelayedLocking(userId, /* force= */ false,
                    /* callback= */ null) == USER_OP_SUCCESS;
        } catch (RemoteException e) {
            isStopping = false;
        }
        if (!isStopping) {
            Slog.e(TAG, "Cannot stop user " + userId);
        }
        return isStopping;
    }

    /**
     * Interface for listeners that want to register for receiving updates to changes to the users
     * on the system including removing and adding users, and changing user info.
     */
    public interface OnUpdateUsersListener {
        /**
         * Method that will get called when users list has been changed.
         */
        void onUpdateUsers(@UserIdInt int userId, int userEvent);
    }
}
