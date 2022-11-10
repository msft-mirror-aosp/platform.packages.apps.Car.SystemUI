/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.systemui.car.users;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Handler;
import android.os.Process;
import android.os.UserHandle;
import android.os.UserManager;

import com.android.systemui.CoreStartable;
import com.android.systemui.dagger.SysUISingleton;
import com.android.systemui.dagger.qualifiers.Background;
import com.android.systemui.dump.DumpManager;
import com.android.systemui.settings.UserContentResolverProvider;
import com.android.systemui.settings.UserContextProvider;
import com.android.systemui.settings.UserFileManager;
import com.android.systemui.settings.UserFileManagerImpl;
import com.android.systemui.settings.UserTracker;

import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import dagger.multibindings.ClassKey;
import dagger.multibindings.IntoMap;

/**
 * Car-specific dagger Module for classes found within the com.android.systemui.settings package.
 */
@Module
public abstract class CarMultiUserUtilsModule {
    @Binds
    @SysUISingleton
    abstract UserContextProvider bindUserContextProvider(UserTracker tracker);

    @Binds
    @SysUISingleton
    abstract UserContentResolverProvider bindUserContentResolverProvider(
            UserTracker tracker);

    @SysUISingleton
    @Provides
    static UserTracker provideUserTracker(
            Context context,
            UserManager userManager,
            DumpManager dumpManager,
            @Background Handler handler
    ) {
        UserHandle processUser = Process.myUserHandle();
        boolean isSecondaryUserSystemUI =
                userManager.isUsersOnSecondaryDisplaysSupported()
                        && !processUser.isSystem()
                        && processUser.getIdentifier() != ActivityManager.getCurrentUser();
        int startingUser = isSecondaryUserSystemUI
                ? processUser.getIdentifier()
                : ActivityManager.getCurrentUser();
        CarUserTrackerImpl tracker = new CarUserTrackerImpl(context, userManager, dumpManager,
                handler, isSecondaryUserSystemUI);
        tracker.initialize(startingUser);
        return tracker;
    }

    @Binds
    @IntoMap
    @ClassKey(UserFileManagerImpl.class)
    abstract CoreStartable bindUserFileManagerCoreStartable(UserFileManagerImpl sysui);

    @Binds
    abstract UserFileManager bindUserFileManager(UserFileManagerImpl impl);
}