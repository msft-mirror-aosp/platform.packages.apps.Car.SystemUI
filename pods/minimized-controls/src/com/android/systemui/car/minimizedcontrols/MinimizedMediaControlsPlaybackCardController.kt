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
import android.graphics.drawable.Drawable
import android.view.ViewGroup
import com.android.car.media.common.MediaItemMetadata
import com.android.car.media.common.browse.MediaItemsRepository
import com.android.car.media.common.playback.PlaybackViewModel
import com.android.car.media.common.source.MediaSource
import com.android.car.media.common.ui.PlaybackCardController
import com.android.car.media.common.ui.PlaybackCardViewModel
import com.android.wm.shell.common.ShellExecutor

/**
 * Controller for the Playback Card View that proxies UI updates to the Shell Executor.
 * This ensures that updates originating from background threads (like Media callbacks)
 * are properly dispatched to the Shell Main thread where the UI resides.
 */
class MinimizedMediaControlsPlaybackCardController(
    builder: Builder,
    private val shellExecutor: ShellExecutor
) : PlaybackCardController(builder) {

    public override fun setupController() {
        super.setupController()
    }

    override fun updateMetadata(metadata: MediaItemMetadata?) {
        shellExecutor.execute {
            super.updateMetadata(metadata)
        }
    }

    override fun updateAlbumCoverWithDrawable(drawable: Drawable?) {
        shellExecutor.execute {
            super.updateAlbumCoverWithDrawable(drawable)
        }
    }

    override fun updateLogoWithDrawable(drawable: Drawable?) {
        shellExecutor.execute {
            super.updateLogoWithDrawable(drawable)
        }
    }

    override fun updateMediaSource(mediaSource: MediaSource?) {
        shellExecutor.execute {
            super.updateMediaSource(mediaSource)
        }
    }

    override fun updatePlaybackState(playbackState: PlaybackViewModel.PlaybackStateWrapper?) {
        shellExecutor.execute {
            super.updatePlaybackState(playbackState)
        }
    }

    /** Factory for creating [MinimizedMediaControlsPlaybackCardController]. */
    fun interface Factory {
        fun create(
            view: ViewGroup,
            playbackViewModel: PlaybackViewModel,
            playbackCardViewModel: PlaybackCardViewModel,
            mediaItemsRepository: MediaItemsRepository,
            context: Context
        ): MinimizedMediaControlsPlaybackCardController
    }
}
