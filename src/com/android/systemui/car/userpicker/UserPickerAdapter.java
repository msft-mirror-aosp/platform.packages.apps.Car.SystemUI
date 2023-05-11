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

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.recyclerview.widget.RecyclerView.Adapter;

import com.android.car.internal.user.UserHelper;
import com.android.systemui.R;
import com.android.systemui.car.userpicker.UserPickerView.UserPickerAdapterViewHolder;

import java.io.PrintWriter;
import java.util.List;

final class UserPickerAdapter extends Adapter<UserPickerAdapterViewHolder> {
    @VisibleForTesting
    static final int USER_PICKER_GUEST_COLOR = R.color.userpicker_guest_color;

    private final Context mContext;
    private final int mDisplayId;
    private final float mDisabledAlpha;

    private List<UserRecord> mUsers;
    private String mLoggedInText;
    private String mPrefixOtherSeatLoggedInInfo;
    private String mStoppingUserText;

    UserPickerAdapter(Context context) {
        mContext = context;
        mDisplayId = mContext.getDisplayId();
        mDisabledAlpha = mContext.getResources().getFloat(R.fraction.user_picker_disabled_alpha);

        updateTexts();
    }

    void updateUsers(List<UserRecord> users) {
        mUsers = users;
    }

    private void setUserLoggedInInfo(UserPickerAdapterViewHolder holder, UserRecord userRecord) {
        if (!userRecord.mIsStopping && !userRecord.mIsLoggedIn) {
            holder.mUserBorderImageView.setVisibility(View.INVISIBLE);
            holder.mLoggedInTextView.setText("");
            updateAlpha(holder, /* disabled= */ false);
            return;
        }

        int color = userRecord.mIsStartGuestSession ? mContext.getColor(USER_PICKER_GUEST_COLOR)
                : UserHelper.getUserNameIconColor(mContext, userRecord.mInfo.getUserHandle());
        if (userRecord.mIsStopping) {
            holder.mUserBorderImageView.setVisibility(View.INVISIBLE);
            holder.mLoggedInTextView.setTextColor(color);
            holder.mLoggedInTextView.setText(mStoppingUserText);
            updateAlpha(holder, /* disabled= */ true);
        } else if (userRecord.mIsLoggedIn) {
            if (userRecord.mLoggedInDisplay == mDisplayId) {
                holder.mUserBorderImageView.setColorFilter(color);
                holder.mUserBorderImageView.setVisibility(View.VISIBLE);
                holder.mLoggedInTextView.setTextColor(color);
                holder.mLoggedInTextView.setText(mLoggedInText);
                updateAlpha(holder, /* disabled= */ false);
            } else {
                holder.mUserBorderImageView.setVisibility(View.INVISIBLE);
                holder.mLoggedInTextView.setTextColor(color);
                holder.mLoggedInTextView.setText(String.format(mPrefixOtherSeatLoggedInInfo,
                        userRecord.mSeatLocationName));
                updateAlpha(holder, /* disabled= */ true);
            }
        }
    }

    private void updateAlpha(UserPickerAdapterViewHolder holder, boolean disabled) {
        float alpha = disabled ? mDisabledAlpha : 1.0f;
        holder.mUserAvatarImageView.setAlpha(alpha);
        holder.mUserNameTextView.setAlpha(alpha);
        holder.mLoggedInTextView.setAlpha(alpha);
    }

    @Override
    public UserPickerAdapterViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(mContext)
                .inflate(R.layout.user_picker_user_pod, parent, false);
        view.setAlpha(1f);
        view.bringToFront();
        return new UserPickerAdapterViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull UserPickerAdapterViewHolder holder, int position) {
        UserRecord userRecord = mUsers.get(position);
        holder.mUserAvatarImageView.setImageDrawable(userRecord.mIcon);
        holder.mFrame.setBackgroundResource(0);
        holder.mUserNameTextView.setText(userRecord.mName);
        setUserLoggedInInfo(holder, userRecord);
        holder.mView.setOnClickListener(userRecord.mOnClickListener);
    }

    @Override
    public int getItemCount() {
        return mUsers != null ? mUsers.size() : 0;
    }

    void onConfigurationChanged() {
        updateTexts();
    }

    private void updateTexts() {
        mLoggedInText = mContext.getString(R.string.logged_in_text);
        mPrefixOtherSeatLoggedInInfo = mContext
                .getString(R.string.prefix_logged_in_info_for_other_seat);
        mStoppingUserText = mContext.getString(R.string.stopping_user_text);
    }

    void dump(@NonNull PrintWriter pw) {
        pw.println("  UserRecords : ");
        for (int i = 0; i < mUsers.size(); i++) {
            UserRecord userRecord = mUsers.get(i);
            pw.println("    " + userRecord.toString());
        }
    }
}
