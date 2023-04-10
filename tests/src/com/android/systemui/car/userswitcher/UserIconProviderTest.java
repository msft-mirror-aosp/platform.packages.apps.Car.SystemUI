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

package com.android.systemui.car.userswitcher;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.mockitoSession;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.spyOn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import android.content.pm.UserInfo;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.UserManager;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;

import androidx.test.filters.SmallTest;

import com.android.internal.util.UserIcons;
import com.android.systemui.SysuiTestCase;
import com.android.systemui.car.CarSystemUiTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

@CarSystemUiTest
@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
@SmallTest
public class UserIconProviderTest extends SysuiTestCase {
    private final UserInfo mUserInfo =
                new UserInfo(/* id= */ 0, /* name= */ "User", /* flags= */ 0);
    private final UserInfo mGuestUserInfo =
                new UserInfo(/* id= */ 1, /* name= */ "Guest User", /* iconPath= */ null,
                /* flags= */ 0, UserManager.USER_TYPE_FULL_GUEST);

    private UserIconProvider mUserIconProvider;
    private MockitoSession mMockingSession;
    private Resources mResources;

    @Mock
    private UserManager mUserManager;
    @Mock
    private Drawable mDrawable;
    @Mock
    private Bitmap mBitmap;

    @Before
    public void setUp() {
        mMockingSession = mockitoSession()
                .initMocks(this)
                .spyStatic(UserManager.class)
                .spyStatic(UserIcons.class)
                .strictness(Strictness.WARN)
                .startMocking();

        doReturn(mUserManager).when(()-> UserManager.get(mContext));
        when(mUserManager.getUserInfo(mUserInfo.id)).thenReturn(mUserInfo);
        when(mUserManager.getUserInfo(mGuestUserInfo.id)).thenReturn(mGuestUserInfo);

        mUserIconProvider = new UserIconProvider();
        spyOn(mUserIconProvider);

        mResources = mContext.getResources();
        spyOn(mResources);
    }

    @After
    public void tearDown() {
        if (mMockingSession != null) {
            mMockingSession.finishMocking();
        }
    }

    @Test
    public void setRoundedUserIcon_existRoundedUserIcon() {
        mUserIconProvider.setRoundedUserIcon(mUserInfo, mContext);

        assertThat(mUserIconProvider.getRoundedUserIcon(mUserInfo, mContext)).isNotNull();
    }

    @Test
    public void assignDefaultIcon_withGuestUser_updateGuestUserIcon() {
        mUserIconProvider.assignDefaultIcon(mUserManager, mResources, mGuestUserInfo);

        verify(() -> UserIcons.getDefaultUserIcon(any(Resources.class), anyInt(), anyBoolean()));
        verify(() -> UserIcons.convertToBitmap(any(Drawable.class)));
        verify(mUserManager).setUserIcon(eq(mGuestUserInfo.id), any(Bitmap.class));
    }

    @Test
    public void assignDefaultIcon_withNotGuestUser_updateNotGuestUserIcon() {
        mUserIconProvider.assignDefaultIcon(mUserManager, mResources, mUserInfo);

        verify(() -> UserIcons.getDefaultUserIcon(any(Resources.class), anyInt(), anyBoolean()));
        verify(() -> UserIcons.convertToBitmap(any(Drawable.class)));
        verify(mUserManager).setUserIcon(eq(mUserInfo.id), any(Bitmap.class));
    }

    @Test
    public void getRoundedUserIcon_notExistUserIcon_assignDefaultIcon() {
        when(mUserManager.getUserIcon(mUserInfo.id)).thenReturn(null);

        mUserIconProvider.getRoundedUserIcon(mUserInfo, mContext);

        verify(mUserIconProvider).assignDefaultIcon(any(UserManager.class), any(Resources.class),
                        eq(mUserInfo));
    }

    @Test
    public void getRoundedUserIcon_existUserIcon_notAssignDefaultIcon() {
        when(mUserManager.getUserIcon(mUserInfo.id)).thenReturn(mBitmap);

        mUserIconProvider.getRoundedUserIcon(mUserInfo, mContext);

        verify(mUserIconProvider, never()).assignDefaultIcon(any(UserManager.class),
                        any(Resources.class), eq(mUserInfo));
    }
}
