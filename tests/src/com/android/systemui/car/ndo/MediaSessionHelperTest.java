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

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.UserHandle;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;

import androidx.test.filters.SmallTest;

import com.android.systemui.SysuiTestCase;
import com.android.systemui.car.CarSystemUiTest;
import com.android.systemui.lifecycle.InstantTaskExecutorRule;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.List;

@CarSystemUiTest
@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
@SmallTest
public class MediaSessionHelperTest extends SysuiTestCase {

    private MediaSessionHelper mMediaSessionHelper;
    private final UserHandle mUserHandle = UserHandle.CURRENT;
    private MediaController mActiveMediaController;
    private MediaController mInactiveMediaController;

    @Rule
    public final InstantTaskExecutorRule mTaskExecutorRule = new InstantTaskExecutorRule();

    @Mock
    private MediaSessionManager mMediaSessionManager;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(/* testClass= */ this);
        mContext = spy(mContext);
        when(mContext.getSystemService(MediaSessionManager.class)).thenReturn(mMediaSessionManager);

        mActiveMediaController = mock(MediaController.class);
        PlaybackState activePlaybackState = mock(PlaybackState.class);
        when(mActiveMediaController.getPlaybackState()).thenReturn(activePlaybackState);
        when(activePlaybackState.isActive()).thenReturn(true);

        mInactiveMediaController = mock(MediaController.class);
        PlaybackState inActivePlaybackState = mock(PlaybackState.class);
        when(mInactiveMediaController.getPlaybackState()).thenReturn(inActivePlaybackState);
        when(inActivePlaybackState.isActive()).thenReturn(false);

        List<MediaController> mediaControllers = new ArrayList<>();
        mediaControllers.add(mActiveMediaController);
        mediaControllers.add(mInactiveMediaController);
        when(mMediaSessionManager.getActiveSessionsForUser(isNull(), eq(mUserHandle)))
                .thenReturn(mediaControllers);

        mMediaSessionHelper = new MediaSessionHelper(mContext, mUserHandle);
    }

    @Test
    public void onCreate_setsInitialValue() {
        assertControllersSet();
    }

    @Test
    public void onActivePlaybackStateChanged_queriesNewMediaSessions() {
        PlaybackState playbackState = mock(PlaybackState.class);
        when(playbackState.isActive()).thenReturn(true);

        mMediaSessionHelper.onPlaybackStateChanged(playbackState);

        assertControllersSet();
    }

    @Test
    public void onInactivePlaybackStateChanged_doesNothing() {
        PlaybackState playbackState = mock(PlaybackState.class);
        when(playbackState.isActive()).thenReturn(false);

        mMediaSessionHelper.onPlaybackStateChanged(playbackState);

        // One time from initialization
        verify(mMediaSessionManager, times(1)).getActiveSessionsForUser(isNull(), eq(mUserHandle));
    }

    @Test
    public void onCleanup_removesListeners() {
        mMediaSessionHelper.mMediaControllersList.add(mock(MediaController.class));

        mMediaSessionHelper.cleanup();

        assertThat(mMediaSessionHelper.mMediaControllersList.isEmpty()).isTrue();
        verify(mMediaSessionManager).removeOnActiveSessionsChangedListener(any());
    }

    private void assertControllersSet() {
        assertThat(mMediaSessionHelper.getActiveMediaSessions().getValue().size())
                .isEqualTo(1);
        assertThat(mMediaSessionHelper.getActiveMediaSessions().getValue().getFirst())
                .isEqualTo(mActiveMediaController);
        assertThat(mMediaSessionHelper.mMediaControllersList.size()).isEqualTo(1);
        assertThat(mMediaSessionHelper.mMediaControllersList.getFirst())
                .isEqualTo(mInactiveMediaController);
    }
}
