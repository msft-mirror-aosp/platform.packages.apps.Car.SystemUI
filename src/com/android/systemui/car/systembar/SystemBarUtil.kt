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

package com.android.systemui.car.systembar

import android.app.ActivityOptions
import android.car.settings.CarSettings.Secure.KEY_UNACCEPTED_TOS_DISABLED_APPS
import android.content.Context
import android.content.Intent
import android.os.UserHandle
import android.provider.Settings
import android.text.TextUtils
import android.util.ArraySet
import android.util.Log
import android.view.WindowInsets.Type.navigationBars
import android.view.WindowInsets.Type.statusBars
import com.android.systemui.R
import com.android.systemui.settings.UserTracker
import com.android.systemui.wm.BarControlPolicy
import java.net.URISyntaxException

object SystemBarUtil {
    private const val TAG = "SystemBarUtil"
    private const val TOS_DISABLED_APPS_SEPARATOR = ","
    const val SYSTEM_BAR_PERSISTENCY_CONFIG_NON_IMMERSIVE = 0
    const val SYSTEM_BAR_PERSISTENCY_CONFIG_IMMERSIVE = 1
    const val SYSTEM_BAR_PERSISTENCY_CONFIG_IMMERSIVE_WITH_NAV = 2
    const val SYSTEM_BAR_PERSISTENCY_CONFIG_BARPOLICY = 3
    const val VISIBLE_BAR_VISIBILITIES_TYPES_INDEX: Int = 0
    const val INVISIBLE_BAR_VISIBILITIES_TYPES_INDEX: Int = 1

    /**
     * Returns a set of packages that are disabled by tos
     *
     * @param context The application context
     * @param uid A user id for a particular user
     *
     * @return Set of packages disabled by tos
     */
    fun getTosDisabledPackages(context: Context, uid: Int?): Set<String> {
        if (uid == null) {
            return ArraySet()
        }
        val settingsValue = Settings.Secure.getStringForUser(
            context.contentResolver,
            KEY_UNACCEPTED_TOS_DISABLED_APPS,
            uid
        )
        return if (TextUtils.isEmpty(settingsValue)) {
            ArraySet()
        } else {
            settingsValue.split(TOS_DISABLED_APPS_SEPARATOR).toSet()
        }
    }

    /**
     * Gets the intent for launching the TOS acceptance flow
     *
     * @param context The app context
     * @param id The desired resource identifier
     *
     * @return TOS intent, or null
     */
    fun getIntentForTosAcceptanceFlow(context: Context, id: Int): Intent? {
        val tosIntentName = context.resources.getString(id)
        return try {
            Intent.parseUri(tosIntentName, Intent.URI_ANDROID_APP_SCHEME)
        } catch (e: URISyntaxException) {
            Log.e(TAG, "Invalid intent URI in user_tos_activity_intent", e)
            null
        }
    }

    /**
     * Helper method that launches an activity with an intent for a particular user.
     *
     * @param context The app context
     * @param intent The description of the activity to start.
     * @param userId The UserHandle of the user to start this activity for.
     */
    fun launchApp(context: Context, intent: Intent, userId: UserHandle) {
        val options = ActivityOptions.makeBasic()
        options.launchDisplayId = context.displayId
        context.startActivityAsUser(intent, options.toBundle(), userId)
    }

    /**
     * Launch the TOS acceptance flow
     *
     * @param context The app context
     * @param userTracker user tracker object
     */
    fun showTosAcceptanceFlow(context: Context, userTracker: UserTracker?) {
        val tosIntent = getIntentForTosAcceptanceFlow(context, R.string.user_tos_activity_intent)
        val userHandle = userTracker?.userHandle
        if (tosIntent == null) {
            Log.w(TAG, "Unable to launch TOS flow from Assistant because intent is null")
            return
        }
        if (userHandle == null) {
            Log.w(TAG, "Unable to launch TOS flow from Assistant because userid is null")
            return
        }
        launchApp(context, tosIntent, userHandle)
    }

    /**
     * Helper function that returns {@code true} if the navigation bar is persistent on the display.
     */
    fun isNavBarPersistent(context: Context): Boolean {
        val behavior = context.resources.getInteger(R.integer.config_systemBarPersistency)
        val remoteInsetsControllerControlsSystemBars =
            context.resources.getBoolean(
                android.R.bool.config_remoteInsetsControllerControlsSystemBars
            )
        val navBarVisibleOnBarControlPolicy =
            (behavior == SYSTEM_BAR_PERSISTENCY_CONFIG_BARPOLICY) &&
                    isBarVisibleOnBarControlPolicy(context, navigationBars())

        return remoteInsetsControllerControlsSystemBars &&
                (behavior == SYSTEM_BAR_PERSISTENCY_CONFIG_NON_IMMERSIVE ||
                        behavior == SYSTEM_BAR_PERSISTENCY_CONFIG_IMMERSIVE_WITH_NAV ||
                        navBarVisibleOnBarControlPolicy)
    }

    /**
     * Helper function that returns {@code true} if the status bar is persistent on the display.
     */
    fun isStatusBarPersistent(context: Context): Boolean {
        val behavior = context.resources.getInteger(R.integer.config_systemBarPersistency)
        val remoteInsetsControllerControlsSystemBars =
            context.resources.getBoolean(
                android.R.bool.config_remoteInsetsControllerControlsSystemBars
            )
        val statusBarVisibleOnBarControlPolicy =
            (behavior == SYSTEM_BAR_PERSISTENCY_CONFIG_BARPOLICY) &&
                    isBarVisibleOnBarControlPolicy(context, statusBars())

        return remoteInsetsControllerControlsSystemBars &&
                (behavior == SYSTEM_BAR_PERSISTENCY_CONFIG_NON_IMMERSIVE ||
                        statusBarVisibleOnBarControlPolicy)
    }

    private fun isBarVisibleOnBarControlPolicy(context: Context, type: Int): Boolean {
        val showTypes =
            BarControlPolicy.getBarVisibilities(
                context.packageName
            )[VISIBLE_BAR_VISIBILITIES_TYPES_INDEX]
        return (showTypes and type) != 0
    }
}
