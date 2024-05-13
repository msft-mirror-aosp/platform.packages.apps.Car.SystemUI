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

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import androidx.annotation.VisibleForTesting;

import com.android.car.datasubscription.DataSubscription;
import com.android.car.datasubscription.DataSubscriptionStatus;
import com.android.systemui.R;
import com.android.systemui.settings.UserTracker;

import javax.inject.Inject;

/**
 * Controller to display the data subscription pop-up
 */
public class DataSubscriptionController {
    private static final String TAG = DataSubscriptionController.class.toString();
    private final Context mContext;
    private DataSubscription mSubscription;
    private final UserTracker mUserTracker;
    private PopupWindow mPopupWindow;
    private final View mPopupView;
    private final Button mExplorationButton;
    private final Intent mIntent;
    private int mSubscriptionStatus;
    // Determines whether a proactive message was already displayed
    private boolean mWasProactiveMsgDisplayed;
    // Determines whether the current message being displayed is proactive or reactive
    private boolean mIsProactiveMsg;
    private View mAnchorView;
    private boolean mShouldDisplayProactiveMsg;

    private final DataSubscription.DataSubscriptionChangeListener mDataSubscriptionChangeListener =
            value -> {
                mSubscriptionStatus = value;
                updateShouldDisplayProactiveMsg();
            };
    private int mPopUpTimeOut;
    private boolean mIsDataSubscriptionListenerRegistered;

    @SuppressLint("MissingPermission")
    @Inject
    public DataSubscriptionController(Context context,
            UserTracker userTracker) {
        mContext = context;
        mSubscription = new DataSubscription(context);
        mSubscriptionStatus = mSubscription.getDataSubscriptionStatus();
        mUserTracker = userTracker;
        mIntent = new Intent(mContext.getString(
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
        mPopupView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (mPopupWindow != null) {
                    mPopupWindow.dismiss();
                    if (!mWasProactiveMsgDisplayed) {
                        mWasProactiveMsgDisplayed = true;
                    }
                }
                return true;
            }
        });

        mExplorationButton = mPopupView.findViewById(
                R.id.data_subscription_explore_options_button);
        mExplorationButton.setOnClickListener(v -> {
            mContext.startActivityAsUser(mIntent, mUserTracker.getUserHandle());
            mPopupWindow.dismiss();
        });
    }

    private void updateShouldDisplayProactiveMsg() {
        // Determines whether a proactive message should be displayed
        mShouldDisplayProactiveMsg = !mWasProactiveMsgDisplayed
                && mSubscriptionStatus != DataSubscriptionStatus.PAID;
        if (mShouldDisplayProactiveMsg && mPopupWindow != null
                && !mPopupWindow.isShowing()) {
            mIsProactiveMsg = true;
            showPopUpWindow();
        }
    }

    @VisibleForTesting
    void showPopUpWindow() {
        if (mAnchorView != null) {
            TextView popUpPrompt = mPopupView.findViewById(R.id.popup_text_view);
            if (popUpPrompt != null) {
                if (mIsProactiveMsg) {
                    popUpPrompt.setText(R.string.data_subscription_proactive_msg_prompt);
                }
            }
            mAnchorView.post(new Runnable() {
                @Override
                public void run() {
                    int xOffsetInPx = mContext.getResources().getDimensionPixelSize(
                            R.dimen.car_quick_controls_entry_points_button_width);
                    int yOffsetInPx = mContext.getResources().getDimensionPixelSize(
                            R.dimen.car_quick_controls_panel_margin);
                    mPopupWindow.showAsDropDown(mAnchorView, -xOffsetInPx / 2, yOffsetInPx);
                    mAnchorView.getHandler().postDelayed(new Runnable() {

                        public void run() {
                            mPopupWindow.dismiss();
                        }
                    }, mPopUpTimeOut);
                    mWasProactiveMsgDisplayed = true;
                }
            });
        }
    }

    /** Set the anchor view. If null, unregisters active data subscription listeners */
    public void setAnchorView(View view) {
        mAnchorView = view;
        if (mAnchorView == null && mIsDataSubscriptionListenerRegistered) {
            mSubscription.removeDataSubscriptionListener();
            mIsDataSubscriptionListenerRegistered = false;
            return;
        }
        if (!mIsDataSubscriptionListenerRegistered) {
            mSubscription.addDataSubscriptionListener(mDataSubscriptionChangeListener);
            mIsDataSubscriptionListenerRegistered = true;
        }
        updateShouldDisplayProactiveMsg();
    }

    @VisibleForTesting()
    void setSubscription(DataSubscription dataSubscription) {
        mSubscription = dataSubscription;
    }

    @VisibleForTesting
    void setPopupWindow(PopupWindow popupWindow) {
        mPopupWindow = popupWindow;
    }

    @VisibleForTesting
    void setSubscriptionStatus(int status) {
        mSubscriptionStatus = status;
    }

    @VisibleForTesting
    DataSubscription.DataSubscriptionChangeListener getDataSubscriptionChangeListener() {
        return mDataSubscriptionChangeListener;
    }

    @VisibleForTesting
    boolean getShouldDisplayProactiveMsg() {
        return mShouldDisplayProactiveMsg;
    }
}
