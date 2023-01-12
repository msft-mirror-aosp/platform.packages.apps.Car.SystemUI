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
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.VisibleForTesting;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.systemui.R;

/**
 * Displays a GridLayout with icons for the users in the system to allow switching between users.
 * Also, shows and dissmisses dialogs using user picker dialog manager.
 */
@VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
public final class UserPickerView extends RecyclerView {

    @VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
    public UserPickerView(Context context, AttributeSet attrs) {
        super(context, attrs);

        addItemDecoration(new ItemSpacingDecoration(context.getResources().getDimensionPixelSize(
                R.dimen.car_user_switcher_vertical_spacing_between_users)));
        GridLayoutManager layoutManager = new GridLayoutManager(context,
                context.getResources().getInteger(R.integer.user_fullscreen_switcher_num_col));
        setLayoutManager(layoutManager);
    }

    /**
     * A {@link RecyclerView.ItemDecoration} that will add spacing between each item in the
     * RecyclerView that it is added to.
     */
    @VisibleForTesting
    static final class ItemSpacingDecoration extends RecyclerView.ItemDecoration {
        private final int mItemSpacing;

        @VisibleForTesting
        ItemSpacingDecoration(int itemSpacing) {
            mItemSpacing = itemSpacing;
        }

        @Override
        public void getItemOffsets(Rect outRect, View view, RecyclerView parent,
                RecyclerView.State state) {
            super.getItemOffsets(outRect, view, parent, state);
            int position = parent.getChildAdapterPosition(view);

            // Skip offset for last item except for {@link GridLayoutManager}.
            if (position == state.getItemCount() - 1
                    && !(parent.getLayoutManager() instanceof GridLayoutManager)) {
                return;
            }

            outRect.bottom = mItemSpacing;
        }
    }

    static final class UserPickerAdapterViewHolder extends RecyclerView.ViewHolder {
        public final ImageView mUserAvatarImageView;
        public final TextView mUserNameTextView;
        public final ImageView mUserBorderImageView;
        public final TextView mLoggedInTextView;
        public final View mView;
        public final FrameLayout mFrame;

        UserPickerAdapterViewHolder(View view) {
            super(view);
            mView = view;
            mUserAvatarImageView = (ImageView) view.findViewById(R.id.user_avatar);
            mUserNameTextView = (TextView) view.findViewById(R.id.user_name);
            mUserBorderImageView = (ImageView) view.findViewById(R.id.user_avatar_border);
            mLoggedInTextView = (TextView) view.findViewById(R.id.logged_in_info);
            mFrame = view.findViewById(R.id.current_user_frame);
        }
    }
}
