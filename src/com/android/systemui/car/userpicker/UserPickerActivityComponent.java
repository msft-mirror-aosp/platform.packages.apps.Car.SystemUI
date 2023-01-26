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

import dagger.Subcomponent;

/**
 * Injects dependencies for {@link UserPickerActivity}, and has {@link UserPickerScope}.
 * There are {@link UserPickerActivity}, {@link UserPickerController}, {@link DialogManager},
 * and {@link SnackbarManager} in {@link UserPickerScope}.
 */
@UserPickerScope
@Subcomponent
public interface UserPickerActivityComponent {
    /**
     * Builder for a {@link UserPickerActivityComponent}
     */
    @Subcomponent.Builder
    interface Builder {
        UserPickerActivityComponent build();
    }

    /**
     * Injects dependencies for {@link UserPickerActivity}
     *
     * @param activity {@link UserPickerActivity} to be injected by
     * {@link UserPickerActivityComponent}.
     */
    void inject(UserPickerActivity activity);
}
