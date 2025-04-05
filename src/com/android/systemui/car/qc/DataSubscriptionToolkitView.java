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

package com.android.systemui.car.qc;

import static android.widget.PopupWindow.INPUT_METHOD_NOT_NEEDED;

import static com.android.car.datasubscription.DataSubscription.DATA_SUBSCRIPTION_ACTION;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import com.android.car.datasubscription.DataSubscriptionController;
import com.android.car.datasubscription.DataSubscriptionViewActionListener;
import com.android.car.datasubscription.NetworkTaskEventListener;
import com.android.systemui.R;
import com.android.systemui.settings.UserTracker;

import javax.inject.Inject;

/**
 * Create a toolkit view for data subscription controller
 */
public class DataSubscriptionToolkitView implements NetworkTaskEventListener {
    private final DataSubscriptionStatsLogHelper mDataSubscriptionStatsLogHelper;
    private final Context mContext;
    @NonNull
    private PopupWindow mPopupWindow;
    private Button mExplorationButton;
    private final View mPopupView;
    private final Intent mIntent;
    private final int mPopUpTimeOut;
    private final UserTracker mUserTracker;
    private View mAnchorView;
    private CharSequence mTopLabel;
    private boolean mIsProactiveMsg;
    private DataSubscriptionViewActionListener mListener;
    private TextView mPopUpPrompt;

    @Inject
    public DataSubscriptionToolkitView(
            Context context,
            UserTracker userTracker,
            DataSubscriptionStatsLogHelper dataSubscriptionStatsLogHelper) {
        mContext = context;
        mUserTracker = userTracker;
        mDataSubscriptionStatsLogHelper = dataSubscriptionStatsLogHelper;
        mListener = new DataSubscriptionController(mContext);
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
                mPopupWindow.dismiss();
                mListener.onMsgDismissed();
                mDataSubscriptionStatsLogHelper.logSessionFinished();
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
        mPopUpPrompt = mPopupView.findViewById(R.id.popup_text_view);

    }

    @Override
    public boolean onDataSubscriptionStatusChanged(boolean isUxrRequired,
                                         boolean shouldDisplayProactiveMsg) {
        if (isUxrRequired && mPopupWindow.isShowing()) {
            mPopupWindow.dismiss();
            mDataSubscriptionStatsLogHelper.logSessionFinished();
            return false;
        }
        if (shouldDisplayProactiveMsg && !mPopupWindow.isShowing()) {
            mIsProactiveMsg = true;
            showPopUpWindow();
            return true;
        }
        return false;
    }
    @Override
    public boolean onAppForeground(boolean isUxrRequired, boolean shouldDisplayReactiveMsg,
                                  CharSequence appLabel) {
        if (isUxrRequired && mPopupWindow.isShowing()) {
            mPopupWindow.dismiss();
            mDataSubscriptionStatsLogHelper.logSessionFinished();
            return false;
        }
        if (isUxrRequired) {
            mExplorationButton.setVisibility(View.GONE);
        } else {
            mExplorationButton.setVisibility(View.VISIBLE);
        }
        mTopLabel = appLabel;

        if (shouldDisplayReactiveMsg && !mPopupWindow.isShowing()) {
            mIsProactiveMsg = false;
            showPopUpWindow();
            return true;
        }
        return false;
    }

    @Override
    public boolean onUxrChange(boolean isUxrRequired) {
        if (mIsProactiveMsg && mPopupWindow.isShowing() && isUxrRequired) {
            mPopupWindow.dismiss();
            mDataSubscriptionStatsLogHelper.logSessionFinished();
            return false;
        }

        if (!mIsProactiveMsg && mPopupWindow.isShowing()) {
            if (isUxrRequired) {
                mExplorationButton.setVisibility(View.GONE);
            } else {
                mExplorationButton.setVisibility(View.VISIBLE);
            }
            mPopupWindow.update();
            return true;
        }
        return false;
    }

    @VisibleForTesting
    void showPopUpWindow() {
        if (mPopUpPrompt != null) {
            if (mIsProactiveMsg) {
                mPopUpPrompt.setText(R.string.data_subscription_proactive_msg_prompt);
                mDataSubscriptionStatsLogHelper.logSessionStarted(
                        DataSubscriptionStatsLogHelper.DataSubscriptionMessageType
                                .PROACTIVE);
            } else {
                mPopUpPrompt.setText(getReactiveMsg());
                mDataSubscriptionStatsLogHelper.logSessionStarted(
                        DataSubscriptionStatsLogHelper.DataSubscriptionMessageType
                                .REACTIVE);
            }
            int xOffsetInPx = mContext.getResources().getDimensionPixelSize(
                    R.dimen.data_subscription_pop_up_horizontal_offset);
            int yOffsetInPx = mContext.getResources().getDimensionPixelSize(
                    R.dimen.data_subscription_pop_up_vertical_offset);
            mAnchorView.post(() -> {
                mPopupWindow.showAsDropDown(mAnchorView, -xOffsetInPx, yOffsetInPx);
                mAnchorView.getHandler().postDelayed(() -> {
                    if (mPopupWindow.isShowing()) {
                        // after the proactive msg dismisses, it won't get displayed again
                        // hence the msg from now on will just be reactive
                        mIsProactiveMsg = false;
                        mPopupWindow.dismiss();
                        mListener.onMsgDismissed();
                        mDataSubscriptionStatsLogHelper.logSessionFinished();
                    }
                }, mPopUpTimeOut);
            });
        }
    }

    private CharSequence getReactiveMsg() {
        return mContext.getString(
                R.string.data_subscription_reactive_msg_prompt, mTopLabel.isEmpty()
                        ? mContext.getResources().getString(
                        R.string.data_subscription_reactive_generic_app_label) :
                        mTopLabel);

    }

    /** Set the anchor view. If null, unregisters active data subscription listeners */
    public void setAnchorView(View view) {
        mAnchorView = view;
        if (view != null) {
            mListener.setNetworkTaskEventListener(this);
            mListener.registerListeners();
        } else {
            if (mListener != null) {
                mListener.setNetworkTaskEventListener(null);
                mListener.unregisterListeners();
            }
        }
    }

    @VisibleForTesting
    void setPopupWindow(PopupWindow popupWindow) {
        mPopupWindow = popupWindow;
    }

    @VisibleForTesting
    void setIsProactiveMsg(boolean isProactiveMsg) {
        mIsProactiveMsg = isProactiveMsg;
    }

    @VisibleForTesting
    Button getExplorationButton() {
        return mExplorationButton;
    }

    @VisibleForTesting
    TextView getPopUpPrompt() {
        return mPopUpPrompt;
    }

    @VisibleForTesting
    void setDataSubscriptionViewActionListener(DataSubscriptionViewActionListener listener) {
        mListener = listener;
    }
}
