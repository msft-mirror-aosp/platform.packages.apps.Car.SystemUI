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

import android.annotation.UserIdInt;
import android.content.Context;
import android.content.pm.UserInfo;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.UserHandle;
import android.os.UserManager;

import androidx.core.graphics.drawable.RoundedBitmapDrawable;

import com.android.car.admin.ui.UserAvatarView;
import com.android.internal.util.UserIcons;
import com.android.systemui.R;

/**
 * Simple class for providing icons for users.
 */
public class UserIconProvider {

    private static final int[] USER_NAME_ICON_COLORS = {
        R.color.user_name_icon_1,
        R.color.user_name_icon_2,
        R.color.user_name_icon_3,
        R.color.user_name_icon_4,
        R.color.user_name_icon_5,
        R.color.user_name_icon_6,
        R.color.user_name_icon_7,
        R.color.user_name_icon_8
    };

    private static final int[] USER_BACKGROUND_ICON_COLORS = {
        R.color.user_background_icon_1,
        R.color.user_background_icon_2,
        R.color.user_background_icon_3,
        R.color.user_background_icon_4,
        R.color.user_background_icon_5,
        R.color.user_background_icon_6,
        R.color.user_background_icon_7,
        R.color.user_background_icon_8
    };

    /**
     * Sets a rounded icon with the first letter of the given user name.
     * This method will update UserManager to use that icon.
     *
     * @param userInfo User for which the icon is requested.
     * @param context Context to use for resources
     */
    public void setRoundedUserIcon(UserInfo userInfo, Context context) {
        UserManager userManager = UserManager.get(context);
        Resources res = context.getResources();
        assignDefaultIcon(userManager, res, userInfo);
    }

    /**
     * Gets a scaled rounded icon for the given user.  If a user does not have an icon saved, this
     * method will default to a generic icon and update UserManager to use that icon.
     *
     * @param userInfo User for which the icon is requested.
     * @param context Context to use for resources
     * @return {@link RoundedBitmapDrawable} representing the icon for the user.
     */
    public Drawable getRoundedUserIcon(UserInfo userInfo, Context context) {
        UserManager userManager = UserManager.get(context);
        Resources res = context.getResources();
        Bitmap icon = userManager.getUserIcon(userInfo.id);

        if (icon == null) {
            icon = assignDefaultIcon(userManager, res, userInfo);
        }

        return new BitmapDrawable(res, icon);
    }

    /**
     * Gets a user icon with badge if the user profile is managed.
     *
     * @param context to use for the avatar view
     * @param userInfo User for which the icon is requested and badge is set
     * @return {@link Drawable} with badge
     */
    public Drawable getDrawableWithBadge(Context context, UserInfo userInfo) {
        return addBadge(context, getRoundedUserIcon(userInfo, context), userInfo.id);
    }

    /**
     * Gets an icon with badge if the device is managed.
     *
     * @param context context
     * @param drawable icon without badge
     * @return {@link Drawable} with badge
     */
    public Drawable getDrawableWithBadge(Context context, Drawable drawable) {
        return addBadge(context, drawable, UserHandle.USER_NULL);
    }

    private static Drawable addBadge(Context context, Drawable drawable, @UserIdInt int userId) {
        int iconSize = drawable.getIntrinsicWidth();
        UserAvatarView userAvatarView = new UserAvatarView(context);
        float badgeToIconSizeRatio =
                context.getResources().getDimension(R.dimen.car_user_switcher_managed_badge_size)
                        / context.getResources().getDimension(
                        R.dimen.car_user_switcher_image_avatar_size);
        userAvatarView.setBadgeDiameter(iconSize * badgeToIconSizeRatio);
        float badgePadding = context.getResources().getDimension(
                R.dimen.car_user_switcher_managed_badge_margin);
        userAvatarView.setBadgeMargin(badgePadding);
        if (userId != UserHandle.USER_NULL) {
            // When the userId is valid, add badge if the user is managed.
            userAvatarView.setDrawableWithBadge(drawable, userId);
        } else {
            // When the userId is not valid, add badge if the device is managed.
            userAvatarView.setDrawableWithBadge(drawable);
        }
        Drawable badgedIcon = userAvatarView.getUserIconDrawable();
        badgedIcon.setBounds(0, 0, iconSize, iconSize);
        return badgedIcon;
    }

    /** Returns a scaled, rounded, default icon for the Guest user */
    public Drawable getRoundedGuestDefaultIcon(Resources resources) {
        Bitmap icon = getGuestUserDefaultIcon(resources);
        return new BitmapDrawable(resources, icon);
    }

    /**
     * Returns a {@link Drawable} for the given {@code icon} scaled to the appropriate size.
     */
    private static BitmapDrawable scaleUserIcon(Resources res, Bitmap icon) {
        int desiredSize = res.getDimensionPixelSize(R.dimen.car_primary_icon_size);
        Bitmap scaledIcon =
                Bitmap.createScaledBitmap(icon, desiredSize, desiredSize, /*filter=*/ true);
        return new BitmapDrawable(res, scaledIcon);
    }

    /**
     * Assigns a default icon to a user according to the user's id. Handles Guest icon and non-guest
     * user icons.
     *
     * @param userManager {@link UserManager} to set user icon
     * @param resources {@link Resources} to grab icons from
     * @param userInfo User whose the first letter of user name is set to default icon.
     * @return Bitmap of the user icon.
     */
    public Bitmap assignDefaultIcon(
            UserManager userManager, Resources resources, UserInfo userInfo) {
        Bitmap bitmap = userInfo.isGuest()
                ? getGuestUserDefaultIcon(resources)
                : getUserDefaultIcon(resources, userInfo);
        userManager.setUserIcon(userInfo.id, bitmap);
        return bitmap;
    }

    /**
     * Gets a bitmap representing the first letter of user name.
     *
     * @param resources The resources to pull from
     * @param userInfo User whose the first letter of user name is set to default icon.
     * @return Default user icon
     */
    private Bitmap getUserDefaultIcon(Resources resources, UserInfo userInfo) {
        return UserIcons.convertToBitmap(
                UserIcons.getDefaultUserIcon(resources, userInfo.id, /* light= */ false));
//        TODO(b/268396237): Update user icons everywhere
//        Drawable icon = resources.getDrawable(
//                R.drawable.car_user_icon_circle_background, /*Theme=*/ null).mutate();
//        icon.setBounds(/*left=*/ 0, /*top=*/ 0,
//                icon.getIntrinsicWidth(), icon.getIntrinsicHeight());
//        // Set color for the background of the user icon.
//        int backgroundColor = resources.getColor(
//                USER_BACKGROUND_ICON_COLORS[userInfo.id % USER_BACKGROUND_ICON_COLORS.length]);
//        icon.setColorFilter(new BlendModeColorFilter(backgroundColor, BlendMode.SRC_IN));
//
//        Bitmap userIconBitmap = UserIcons.convertToBitmap(icon);
//
//        // Set the first letter of user name as user icon.
//        String firstLetter = userInfo.name.substring(/*beginIndex=*/ 0, /*endIndex=*/ 1);
//        Paint paint = new Paint();
//        paint.setStyle(Paint.Style.FILL);
//        paint.setColor(resources.getColor(
//                USER_NAME_ICON_COLORS[userInfo.id % USER_NAME_ICON_COLORS.length]));
//        paint.setTextSize(resources.getDimension(R.dimen.user_icon_text_size));
//        paint.setTextAlign(Paint.Align.LEFT);
//
//        // Draw text in center of the canvas.
//        Canvas canvas = new Canvas(userIconBitmap);
//        Rect textBounds = new Rect();
//        paint.getTextBounds(firstLetter, /*start=*/0, /*end=*/1, textBounds);
//        float x = canvas.getWidth() * 0.5f - textBounds.exactCenterX();
//        float y = canvas.getHeight() * 0.5f - textBounds.exactCenterY();
//        canvas.drawText(firstLetter, x, y, paint);
//
//        return userIconBitmap;
    }

    private Bitmap getGuestUserDefaultIcon(Resources resources) {
        return UserIcons.convertToBitmap(UserIcons.getDefaultUserIcon(
                resources, /*userId=*/ UserHandle.USER_NULL, /*light=*/ false));
    }
}
