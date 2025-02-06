/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.systemui.car.qc;

import static android.Manifest.permission.ACCESS_NETWORK_STATE;
import static android.Manifest.permission.INTERNET;
import static android.widget.PopupWindow.INPUT_METHOD_NOT_NEEDED;

import android.annotation.Nullable;
import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.app.ActivityTaskManager;
import android.app.TaskStackListener;
import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import com.android.car.datasubscription.DataSubscription;
import com.android.car.datasubscription.DataSubscriptionStatus;
import com.android.car.ui.utils.CarUxRestrictionsUtil;
import com.android.systemui.R;
import com.android.systemui.car.qc.DataSubscriptionStatsLogHelper.DataSubscriptionMessageType;
import com.android.systemui.dagger.SysUISingleton;
import com.android.systemui.dagger.qualifiers.Background;
import com.android.systemui.dagger.qualifiers.Main;
import com.android.systemui.settings.UserTracker;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

/**
 * Controller to display the data subscription pop-up
 */
@SysUISingleton
public class DataSubscriptionController implements DataSubscription.DataSubscriptionChangeListener {
    private static final boolean DEBUG = Build.IS_DEBUGGABLE;
    private static final String TAG = DataSubscriptionController.class.toString();
    private static final String DATA_SUBSCRIPTION_ACTION =
            "android.intent.action.DATA_SUBSCRIPTION";
    private static final String DATA_SUBSCRIPTION_SHARED_PREFERENCE_PATH =
            "com.android.car.systemui.car.qc.DataSubscriptionController";
    // Timeout for network callback in ms
    private static final int CALLBACK_TIMEOUT_MS = 1000;
    private final Context mContext;
    private DataSubscription mSubscription;
    private final UserTracker mUserTracker;
    private PopupWindow mPopupWindow;
    private final View mPopupView;
    private Button mExplorationButton;
    private final Intent mIntent;
    private ConnectivityManager mConnectivityManager;
    private DataSubscriptionNetworkCallback mNetworkCallback;
    private final Handler mMainHandler;
    private final Executor mBackGroundExecutor;
    private Set<String> mActivitiesBlocklist;
    private Set<String> mPackagesBlocklist;
    private CountDownLatch mLatch;
    private boolean mIsNetworkCallbackRegistered;
    private final DataSubscriptionStatsLogHelper mDataSubscriptionStatsLogHelper;
    private final TaskStackListener mTaskStackListener = new TaskStackListener() {
        @SuppressLint("MissingPermission")
        @Override
        public void onTaskMovedToFront(ActivityManager.RunningTaskInfo taskInfo) {
            if (mIsNetworkCallbackRegistered && mConnectivityManager != null) {
                mConnectivityManager.unregisterNetworkCallback(mNetworkCallback);
                mIsNetworkCallbackRegistered = false;
            }

            if (taskInfo.topActivity == null || mConnectivityManager == null) {
                return;
            }
            mTopPackage = taskInfo.topActivity.getPackageName();
            if (mPackagesBlocklist.contains(mTopPackage)) {
                return;
            }

            mTopActivity = taskInfo.topActivity.flattenToString();
            if (mActivitiesBlocklist.contains(mTopActivity)) {
                return;
            }

            PackageInfo info;
            int userId = mUserTracker.getUserId();
            try {
                info = mContext.getPackageManager().getPackageInfoAsUser(mTopPackage,
                        PackageManager.GET_PERMISSIONS, userId);
                if (info != null) {
                    String[] permissions = info.requestedPermissions;
                    boolean appReqInternet = Arrays.asList(permissions).contains(
                            ACCESS_NETWORK_STATE)
                            && Arrays.asList(permissions).contains(INTERNET);
                    if (!appReqInternet) {
                        mActivitiesBlocklist.add(mTopActivity);
                        return;
                    }
                }

                ApplicationInfo appInfo = mContext.getPackageManager().getApplicationInfoAsUser(
                        mTopPackage, 0, mUserTracker.getUserId());
                mTopLabel = appInfo.loadLabel(mContext.getPackageManager());
                int uid = appInfo.uid;
                mConnectivityManager.registerDefaultNetworkCallbackForUid(uid, mNetworkCallback,
                        mMainHandler);
                mIsNetworkCallbackRegistered = true;
                // since we don't have the option of using the synchronous call of getting the
                // default network by UID, we need to set a timeout period to make sure the network
                // from the callback is updated correctly before deciding to display the message
                //TODO: b/336869328 use the synchronous call to update network status
                mLatch = new CountDownLatch(CALLBACK_TIMEOUT_MS);
                mBackGroundExecutor.execute(() -> {
                    try {
                        mLatch.await(CALLBACK_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                    } catch (InterruptedException e) {
                        Log.e(TAG, "error updating network callback" + e);
                    } finally {
                        if (mNetworkCallback.mNetwork == null) {
                            mNetworkCapabilities = null;
                            updateShouldDisplayReactiveMsg();
                        }
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, mTopPackage + " not found : " + e);
            }
        }
    };

    private final CarUxRestrictionsUtil.OnUxRestrictionsChangedListener
            mUxRestrictionsChangedListener =
            new CarUxRestrictionsUtil.OnUxRestrictionsChangedListener() {
                @Override
                public void onRestrictionsChanged(@NonNull CarUxRestrictions carUxRestrictions) {
                    mIsDistractionOptimizationRequired =
                            carUxRestrictions.isRequiresDistractionOptimization();
                    if (mIsProactiveMsg) {
                        if (mIsDistractionOptimizationRequired
                                && mPopupWindow != null
                                && mPopupWindow.isShowing()) {
                            mPopupWindow.dismiss();
                            mDataSubscriptionStatsLogHelper.logSessionFinished();
                        }
                    } else {
                        if (mIsDistractionOptimizationRequired && mPopupWindow != null) {
                            mExplorationButton.setVisibility(View.GONE);

                        } else {
                            mExplorationButton.setVisibility(View.VISIBLE);
                        }
                        mPopupWindow.update();
                    }
                }
            };

    // Determines whether a proactive message was already displayed
    private boolean mWasProactiveMsgDisplayed;
    // Determines whether the current message being displayed is proactive or reactive
    private boolean mIsProactiveMsg;
    private boolean mIsDistractionOptimizationRequired;
    private View mAnchorView;
    private boolean mShouldDisplayProactiveMsg;

    private final int mPopUpTimeOut;
    private boolean mShouldDisplayReactiveMsg;
    private String mTopActivity;
    private String mTopPackage;
    private CharSequence mTopLabel;
    private NetworkCapabilities mNetworkCapabilities;
    private boolean mIsUxRestrictionsListenerRegistered;
    private SharedPreferences mSharedPreferences;
    private SharedPreferences.Editor mEditor;
    private int mCurrentInterval;
    private int mCurrentCycle;
    private int mCurrentActiveDays;

    @VisibleForTesting
    static final String KEY_PREV_POPUP_DATE =
            "com.android.car.systemui.car.qc.PREV_DATE";
    @VisibleForTesting
    static final String KEY_PREV_POPUP_CYCLE =
            "com.android.car.systemui.car.qc.PREV_CYCLE";
    @VisibleForTesting
    static final String KEY_PREV_POPUP_ACTIVE_DAYS =
            "com.android.car.systemui.car.qc.PREV_ACTIVE_DAYS";
    @VisibleForTesting
    static final String KEY_PREV_POPUP_STATUS =
            "com.android.car.systemui.car.qc.PREV_STATUS";

    @SuppressLint("MissingPermission")
    @Inject
    public DataSubscriptionController(Context context,
            UserTracker userTracker,
            @Main Handler mainHandler,
            @Background Executor backgroundExecutor,
            DataSubscriptionStatsLogHelper dataSubscriptionStatsLogHelper) {
        mContext = context;
        mSubscription = new DataSubscription(context);
        mUserTracker = userTracker;
        mMainHandler = mainHandler;
        mBackGroundExecutor = backgroundExecutor;
        mDataSubscriptionStatsLogHelper = dataSubscriptionStatsLogHelper;
        mIntent = new Intent(DATA_SUBSCRIPTION_ACTION);
        mIntent.setPackage(mContext.getString(
                R.string.connectivity_flow_app));
        mIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        LayoutInflater inflater = LayoutInflater.from(mContext);
        mPopupView = inflater.inflate(R.layout.data_subscription_popup_window, null);
        mPopUpTimeOut = mContext.getResources().getInteger(
                R.integer.data_subscription_pop_up_timeout);
        int width = LinearLayout.LayoutParams.WRAP_CONTENT;
        int height = LinearLayout.LayoutParams.WRAP_CONTENT;
        boolean focusable = true;
        mPopupWindow = new PopupWindow(mPopupView, width, height, focusable);
        mPopupWindow.setTouchModal(false);
        mPopupWindow.setOutsideTouchable(true);
        mPopupWindow.setInputMethodMode(INPUT_METHOD_NOT_NEEDED);
        mPopupView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (mPopupWindow != null) {
                    mPopupWindow.dismiss();
                    if (!mWasProactiveMsgDisplayed) {
                        mWasProactiveMsgDisplayed = true;
                    }
                    mDataSubscriptionStatsLogHelper.logSessionFinished();
                }
                return true;
            }
        });

        mExplorationButton = mPopupView.findViewById(
                R.id.data_subscription_explore_options_button);
        mExplorationButton.setOnClickListener(v -> {
            mPopupWindow.dismiss();
            mContext.startActivityAsUser(mIntent, mUserTracker.getUserHandle());
            mDataSubscriptionStatsLogHelper.logButtonClicked();
        });
        mConnectivityManager = mContext.getSystemService(ConnectivityManager.class);
        mNetworkCallback = new DataSubscriptionNetworkCallback();
        mActivitiesBlocklist = new HashSet<>();
        mPackagesBlocklist = new HashSet<>();

        Resources res = mContext.getResources();
        String[] blockActivities = res.getStringArray(
                R.array.config_dataSubscriptionBlockedActivitiesList);
        mActivitiesBlocklist.addAll(List.of(blockActivities));
        String[] blockComponents = res.getStringArray(
                R.array.config_dataSubscriptionBlockedPackagesList);
        mPackagesBlocklist.addAll(List.of(blockComponents));
        try {
            ActivityTaskManager.getService().registerTaskStackListener(mTaskStackListener);
        } catch (Exception e) {
            Log.e(TAG, "error while registering TaskStackListener " + e);
        }
        mSharedPreferences = mContext.getSharedPreferences(
                DATA_SUBSCRIPTION_SHARED_PREFERENCE_PATH, Context.MODE_PRIVATE);
        mEditor = mSharedPreferences.edit();
    }

    void updateShouldDisplayProactiveMsg() {
        if (mIsDistractionOptimizationRequired) {
            if (mPopupWindow != null && mPopupWindow.isShowing()) {
                mPopupWindow.dismiss();
                mDataSubscriptionStatsLogHelper.logSessionFinished();
            }
        } else {
            // Determines whether a proactive message should be displayed
            mShouldDisplayProactiveMsg = !mWasProactiveMsgDisplayed
                    && mSubscription.isDataSubscriptionInactive()
                    && isValidTimeInterval()
                    && isValidCycle()
                    && isValidActiveDays();
            if (mShouldDisplayProactiveMsg && mPopupWindow != null
                    && !mPopupWindow.isShowing()) {
                mIsProactiveMsg = true;
                showPopUpWindow();
                writeLatestPopupDate();
                writeLatestPopupCycle();
                writeLatestPopupActiveDays();
            }
        }
    }

    private void updateShouldDisplayReactiveMsg() {
        if (mIsDistractionOptimizationRequired) {
            mExplorationButton.setVisibility(View.GONE);

        } else {
            mExplorationButton.setVisibility(View.VISIBLE);
        }
        if (!mPopupWindow.isShowing()) {
            mShouldDisplayReactiveMsg = ((mNetworkCapabilities == null
                    || (!isSuspendedNetwork() && !isValidNetwork()))
                    && mSubscription.isDataSubscriptionInactive());
            if (mShouldDisplayReactiveMsg) {
                mIsProactiveMsg = false;
                showPopUpWindow();
                mActivitiesBlocklist.add(mTopActivity);
            } else {
                if (mPopupWindow != null && mPopupWindow.isShowing()) {
                    mPopupWindow.dismiss();
                    mDataSubscriptionStatsLogHelper.logSessionFinished();
                }
            }
        }
    }

    @VisibleForTesting
    void showPopUpWindow() {
        if (mAnchorView != null) {
            mAnchorView.post(new Runnable() {
                @Override
                public void run() {
                    TextView popUpPrompt = mPopupView.findViewById(R.id.popup_text_view);
                    if (popUpPrompt != null) {
                        if (mIsProactiveMsg) {
                            popUpPrompt.setText(R.string.data_subscription_proactive_msg_prompt);
                            mDataSubscriptionStatsLogHelper.logSessionStarted(
                                    DataSubscriptionMessageType.PROACTIVE);
                        } else {
                            popUpPrompt.setText(getReactiveMsg());
                            mDataSubscriptionStatsLogHelper.logSessionStarted(
                                    DataSubscriptionMessageType.REACTIVE);
                        }
                    }
                    int xOffsetInPx = mContext.getResources().getDimensionPixelSize(
                            R.dimen.data_subscription_pop_up_horizontal_offset);
                    int yOffsetInPx = mContext.getResources().getDimensionPixelSize(
                            R.dimen.data_subscription_pop_up_vertical_offset);
                    mPopupWindow.showAsDropDown(mAnchorView, -xOffsetInPx, yOffsetInPx);
                    mAnchorView.getHandler().postDelayed(new Runnable() {

                        public void run() {
                            if (mPopupWindow.isShowing()) {
                                mPopupWindow.dismiss();
                                mWasProactiveMsgDisplayed = true;
                                // after the proactive msg dismisses, it won't get displayed again
                                // hence the msg from now on will just be reactive
                                mIsProactiveMsg = false;
                                mDataSubscriptionStatsLogHelper.logSessionFinished();
                            }
                        }
                    }, mPopUpTimeOut);
                }
            });
        }
    }

    /** Set the anchor view. If null, unregisters active data subscription listeners */
    public void setAnchorView(@Nullable View view) {
        mAnchorView = view;
        if (mAnchorView != null) {
            mSubscription.addDataSubscriptionListener(this);
            updateCurrentStatus();
            updateCurrentInterval();
            updateCurrentCycle();
            updateCurrentActiveDays();
            updateShouldDisplayProactiveMsg();
            if (!mIsUxRestrictionsListenerRegistered) {
                CarUxRestrictionsUtil.getInstance(mContext).register(
                        mUxRestrictionsChangedListener);
                mIsUxRestrictionsListenerRegistered = true;
            }
        } else {
            mSubscription.removeDataSubscriptionListener();
            if (mIsUxRestrictionsListenerRegistered) {
                CarUxRestrictionsUtil.getInstance(mContext).unregister(
                        mUxRestrictionsChangedListener);
                mIsUxRestrictionsListenerRegistered = false;
            }
        }
    }

    boolean isValidNetwork() {
        return mNetworkCapabilities.hasCapability(
                NetworkCapabilities.NET_CAPABILITY_VALIDATED);
    }

    boolean isSuspendedNetwork() {
        return !mNetworkCapabilities.hasCapability(
                NetworkCapabilities.NET_CAPABILITY_NOT_SUSPENDED);
    }

    private CharSequence getReactiveMsg() {
        return mContext.getString(
                R.string.data_subscription_reactive_msg_prompt, mTopLabel.isEmpty()
                ? mContext.getResources().getString(
                        R.string.data_subscription_reactive_generic_app_label) :
                        mTopLabel);

    }

    @Override
    public void onChange(int value) {
        updateCurrentStatus();
        updateShouldDisplayProactiveMsg();
    }

    public class DataSubscriptionNetworkCallback extends ConnectivityManager.NetworkCallback {
        Network mNetwork;

        @Override
        public void onAvailable(@NonNull Network network) {
            if (DEBUG) {
                Log.d(TAG, "onAvailable " + network);
            }
            mNetwork = network;
            mLatch.countDown();
        }

        @Override
        public void onCapabilitiesChanged(@NonNull Network network,
                @NonNull NetworkCapabilities networkCapabilities) {
            if (DEBUG) {
                Log.d(TAG, "onCapabilitiesChanged " + network);
            }
            mNetwork = network;
            mNetworkCapabilities = networkCapabilities;
            updateShouldDisplayReactiveMsg();
        }
    }

    private boolean isValidTimeInterval() {
        return mCurrentInterval >= mContext.getResources().getInteger(
                R.integer.data_subscription_pop_up_frequency);
    }

    private boolean isValidCycle() {
        if (mCurrentCycle == 1) {
            return true;
        }
        return mCurrentCycle <= mContext.getResources().getInteger(
                R.integer.data_subscription_pop_up_startup_cycle_limit);
    }

    private boolean isValidActiveDays() {
        if (mCurrentActiveDays == 1) {
            return true;
        }
        return mCurrentActiveDays <= mContext.getResources().getInteger(
                R.integer.data_subscription_pop_up_active_days_limit);
    }

    private void updateCurrentStatus() {
        int prevStatus = mSharedPreferences.getInt(KEY_PREV_POPUP_STATUS, 0);
        int currentStatus = mSubscription.getDataSubscriptionStatus();
        if (prevStatus == DataSubscriptionStatus.INACTIVE && prevStatus != currentStatus) {
            mEditor.clear();
            mEditor.apply();
        }
        mEditor.putInt(KEY_PREV_POPUP_STATUS, currentStatus);
        mEditor.apply();
    }

    private void updateCurrentInterval() {
        mCurrentInterval = mContext.getResources().getInteger(
                R.integer.data_subscription_pop_up_frequency);
        String prevDate = mSharedPreferences.getString(KEY_PREV_POPUP_DATE, /* defValue=*/ "");
        if (!TextUtils.isEmpty(prevDate)) {
            mCurrentInterval = (int) ChronoUnit.DAYS.between(LocalDate.parse(prevDate),
                    LocalDate.now(ZoneId.systemDefault()));
        }
    }

    private void updateCurrentCycle() {
        mCurrentCycle = mSharedPreferences.getInt(
                KEY_PREV_POPUP_CYCLE, /* defValue=*/ 0);
    }

    private void updateCurrentActiveDays() {
        mCurrentActiveDays = mSharedPreferences.getInt(
                KEY_PREV_POPUP_ACTIVE_DAYS, /* defValue=*/ 0);
    }

    private void writeLatestPopupDate() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        LocalDate newDate = LocalDate.now(ZoneId.systemDefault());
        String formattedNewDate = newDate.format(formatter);
        mEditor.putString(KEY_PREV_POPUP_DATE, formattedNewDate);
        mEditor.apply();
    }

    private void writeLatestPopupCycle() {
        mEditor.putInt(KEY_PREV_POPUP_CYCLE, mSharedPreferences.getInt(
                KEY_PREV_POPUP_CYCLE, /* defValue=*/ 1) + 1);
        mEditor.apply();
    }

    private void writeLatestPopupActiveDays() {
        mEditor.putInt(KEY_PREV_POPUP_ACTIVE_DAYS, mSharedPreferences.getInt(
                KEY_PREV_POPUP_ACTIVE_DAYS, /* defValue=*/ 1) + 1);
        mEditor.apply();
    }

    @VisibleForTesting
    void setPopupWindow(PopupWindow popupWindow) {
        mPopupWindow = popupWindow;
    }

    @VisibleForTesting
    void setSubscription(DataSubscription dataSubscription) {
        mSubscription = dataSubscription;
    }

    @VisibleForTesting
    boolean getShouldDisplayProactiveMsg() {
        return mShouldDisplayProactiveMsg;
    }

    @VisibleForTesting
    void setPackagesBlocklist(Set<String> list) {
        mPackagesBlocklist = list;
    }

    @VisibleForTesting
    void setActivitiesBlocklist(Set<String> list) {
        mActivitiesBlocklist = list;
    }

    @VisibleForTesting
    void setConnectivityManager(ConnectivityManager connectivityManager) {
        mConnectivityManager = connectivityManager;
    }

    @VisibleForTesting
    TaskStackListener getTaskStackListener() {
        return mTaskStackListener;
    }

    @VisibleForTesting
    boolean getShouldDisplayReactiveMsg() {
        return mShouldDisplayReactiveMsg;
    }

    @VisibleForTesting
    void setNetworkCallback(DataSubscriptionNetworkCallback callback) {
        mNetworkCallback = callback;
    }

    @VisibleForTesting
    void setIsCallbackRegistered(boolean value) {
        mIsNetworkCallbackRegistered = value;
    }

    @VisibleForTesting
    void setIsProactiveMsg(boolean value) {
        mIsProactiveMsg = value;
    }

    @VisibleForTesting
    void setExplorationButton(Button button) {
        mExplorationButton = button;
    }

    @VisibleForTesting
    void setIsUxRestrictionsListenerRegistered(boolean value) {
        mIsUxRestrictionsListenerRegistered = value;
    }

    @VisibleForTesting
    void setSharedPreference(SharedPreferences sharedPreference) {
        mSharedPreferences = sharedPreference;
    }

    @VisibleForTesting
    void setCurrentInterval(int currentInterval) {
        mCurrentInterval = currentInterval;
    }

    @VisibleForTesting
    void setCurrentCycle(int cycle) {
        mCurrentCycle = cycle;
    }

    @VisibleForTesting
    void setCurrentActiveDays(int activeDays) {
        mCurrentActiveDays = activeDays;
    }

    @VisibleForTesting
    void setWasProactiveMsgDisplayed(boolean value) {
        mWasProactiveMsgDisplayed = value;
    }
}
