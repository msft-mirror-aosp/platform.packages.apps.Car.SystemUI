/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.systemui.car.minimizedcontrols

import android.content.Context
import android.content.ContextWrapper
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.os.Handler
import android.os.UserHandle
import android.util.Log
import android.view.View
import androidx.annotation.VisibleForTesting
import com.android.car.media.common.source.MediaModels
import com.android.car.media.common.source.MediaSessionHelper
import com.android.car.scalableui.model.PanelControllerMetadata
import com.android.car.scalableui.panel.DecorPanelController
import com.android.systemui.car.wm.scalableui.panel.controller.DecorPanelViewMap
import com.android.systemui.car.wm.scalableui.view.DecorPanelControllerBase
import com.android.systemui.dagger.qualifiers.Main
import com.android.wm.shell.sysui.ShellController
import com.android.wm.shell.sysui.UserChangeListener
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import java.util.concurrent.Executor
import javax.inject.Provider

/**
 * Controller for [MinimizedMediaControlsView].
 */
class MinimizedMediaControlsPanelController @AssistedInject constructor(
    @Assisted panelId: String,
    @Assisted metadata: PanelControllerMetadata,
    @DecorPanelViewMap decorPanelViewMap: Map<Class<*>, @JvmSuppressWildcards Provider<View>>,
    private val shellController: ShellController,
    @param:Main private val mainHandler: Handler,
    @param:Main private val mainExecutor: Executor
) : DecorPanelControllerBase(panelId, metadata, decorPanelViewMap) {

    private var viewModel: MinimizedMediaControlsViewModel? = null

    private var view: MinimizedMediaControlsView? = null
    private var currentUserId: Int = UserHandle.USER_NULL

    private val userChangeListener = object : UserChangeListener {
        override fun onUserChanged(newUserId: Int, userContext: Context) {
            Log.d(TAG, "onUserChanged: newUserId=$newUserId")
            reinitMedia(newUserId)
        }
    }

    override fun getView(): View {
        val currentView = checkNotNull(super.getView()) {
            "View could not be loaded for MinimizedMediaControlsPanelController"
        }
        if (currentView !== view) {
            view = currentView as? MinimizedMediaControlsView
        }
        initMedia()
        return currentView
    }

    private fun initMedia() {
        if (viewModel != null) return // Already initialized

        // Register listener
        shellController.addUserChangeListener(userChangeListener)

        val userId = shellController.currentUserId
        Log.d(TAG, "initMedia: userId=$userId")
        reinitMedia(userId)
    }

    @VisibleForTesting
    var viewModelFactory: ((Context, Int) -> MinimizedMediaControlsViewModel) = { ctx, userId ->
        val models = MediaModels(
             ctx,
             /* notificationProvider = */
             null,
             object : MediaSessionHelper.SessionProvider {
                 override fun getActiveSessions(
                     manager: MediaSessionManager
                 ): List<MediaController> {
                     // The default getActiveSessions uses the calling process ID.
                     // We need to explicitly call getActiveSessionsForUser with the target userId to get the correct sessions.
                     return manager.getActiveSessionsForUser(
                         /* notificationListener= */
                         null,
                         UserHandle.of(userId)
                     )
                 }

                 override fun registerActiveSessionsListener(
                     manager: MediaSessionManager,
                     executor: Executor,
                     listener: MediaSessionManager.OnActiveSessionsChangedListener
                 ) {
                     // Similarly, addOnActiveSessionsChangedListener uses the calling process  ID.
                     // We must use the user-aware variant to listen for the target user's session changes.
                     manager.addOnActiveSessionsChangedListener(
                         /* notificationListener= */
                         null,
                         UserHandle.of(userId),
                         mainExecutor,
                         listener
                     )
                 }
             }
        )
        MinimizedMediaControlsViewModel(models)
    }

    private fun reinitMedia(userId: Int) {
        if (currentUserId == userId && viewModel != null) {
            Log.d(TAG, "reinitMedia: Skipping re-initialization for same user $userId")
            return
        }
        currentUserId = userId

        val appCtx = view?.context?.applicationContext ?: return
        Log.d(TAG, "reinitMedia: userId=$userId")

        val userContext = if (userId > 0) {
            UserHandle.of(userId).let { userHandle ->
                try {
                    appCtx.createContextAsUser(userHandle, 0)
                } catch (e: Exception) {
                    Log.w(TAG, "Error creating user context for $userId", e)
                    appCtx
                }
            }
        } else {
            appCtx
        }

        // Wrap context to ensure getApplicationContext() returns the user-aware context (this wrapper)
        // instead of the raw Application context (which is User 0).
        // This is required for shared libraries (car-media-common) that rely on getApplicationContext(),
        // preventing them from falling back to the system user context.
        // This also ensures that any SharedPreferences accessed via this context are user-isolated
        // (stored in /data/user/<userId>/...), so explicitly including userId in preference keys/filenames is not needed.
        val wrappedContext = object : ContextWrapper(userContext) {
            override fun getApplicationContext(): Context {
                return this
            }
        }

        mainHandler.post {
            viewModel?.cleanUp()

            // Create NEW ViewModel instance
            viewModel = viewModelFactory(wrappedContext, userId)
        }
    }

    override fun destroy() {
        Log.d(TAG, "destroy")
        super.destroy()
        shellController.removeUserChangeListener(userChangeListener)
        mainHandler.post {
            viewModel?.cleanUp()
            viewModel = null
        }
    }

    companion object {
        private const val TAG = "MinimizedMediaControlsPanelController"
    }

    @AssistedFactory
    interface Factory : DecorPanelController.Factory<MinimizedMediaControlsPanelController> {
        override fun create(panelId: String, metadata: PanelControllerMetadata):
                MinimizedMediaControlsPanelController
    }
}
