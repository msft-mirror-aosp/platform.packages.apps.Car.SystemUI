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

package com.android.systemui.car.ndo;

import android.content.Context;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.UserHandle;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * Class that handles listening to and returning active media sessions.
 */
public class MediaSessionHelper extends MediaController.Callback {

    private final MutableLiveData<List<MediaController>> mLiveData = new MutableLiveData<>();
    private final MediaSessionManager mMediaSessionManager;
    private final UserHandle mUserHandle;
    @VisibleForTesting
    final List<MediaController> mMediaControllersList = new ArrayList<>();
    private final Executor mExecutor;

    private final MediaSessionManager.OnActiveSessionsChangedListener mChangedListener =
            this::onMediaSessionChange;

    public MediaSessionHelper(Context context, UserHandle userHandle) {
        mMediaSessionManager = context.getSystemService(MediaSessionManager.class);
        mUserHandle = userHandle;
        mExecutor = context.getMainExecutor();
        init();
    }

    private void init() {
        // Set initial data
        onMediaSessionChange(mMediaSessionManager
                .getActiveSessionsForUser(/* notificationListener= */ null, mUserHandle));

        mMediaSessionManager.addOnActiveSessionsChangedListener(/* notificationListener= */ null,
                mUserHandle, mExecutor, mChangedListener);
    }

    /** Returns MediaControllers of current active media sessions */
    public LiveData<List<MediaController>> getActiveMediaSessions() {
        return mLiveData;
    }

    /** Performs cleanup when MediaSessionHelper should no longer be used. */
    public void cleanup() {
        mMediaSessionManager.removeOnActiveSessionsChangedListener(mChangedListener);
        unregisterPlaybackChanges();
    }

    @Override
    public void onPlaybackStateChanged(@Nullable PlaybackState state) {
        if (isPausedOrActive(state)) {
            onMediaSessionChange(mMediaSessionManager
                    .getActiveSessionsForUser(/* notificationListener= */ null, mUserHandle));
        }
    }

    private void onMediaSessionChange(List<MediaController> mediaControllers) {
        unregisterPlaybackChanges();
        if (mediaControllers == null || mediaControllers.isEmpty()) {
            return;
        }

        List<MediaController> activeMediaControllers = new ArrayList<>();

        for (MediaController mediaController : mediaControllers) {
            if (isPausedOrActive(mediaController.getPlaybackState())) {
                activeMediaControllers.add(mediaController);
            } else {
                // Since playback state changes don't trigger an active media session change, we
                // need to listen to the other media sessions in case another one becomes active.
                registerForPlaybackChanges(mediaController);
            }
        }
        mLiveData.setValue(activeMediaControllers);
    }

    /** Returns whether the MediaController is paused active */
    private boolean isPausedOrActive(PlaybackState playbackState) {
        if (playbackState == null) {
            return false;
        }
        return playbackState.isActive() || playbackState.getState() == PlaybackState.STATE_PAUSED;
    }

    private void registerForPlaybackChanges(MediaController controller) {
        if (mMediaControllersList.contains(controller)) {
            return;
        }

        controller.registerCallback(this);
        mMediaControllersList.add(controller);
    }

    private void unregisterPlaybackChanges() {
        for (MediaController mediaController : mMediaControllersList) {
            if (mediaController != null) {
                mediaController.unregisterCallback(this);
            }
        }
        mMediaControllersList.clear();
    }
}
