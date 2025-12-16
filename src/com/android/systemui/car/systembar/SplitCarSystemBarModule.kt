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

package com.android.systemui.car.systembar

import com.android.systemui.R
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoMap
import dagger.multibindings.StringKey

/** Dagger module for the scalable car system bar. */
@Module
abstract class SplitCarSystemBarModule {

    @Binds
    @IntoMap
    @StringKey("top_bar_left_panel")
    abstract fun bindTopBarLeftViewControllerFactory(
        factory: CarSystemBarViewControllerImpl.Factory
    ): CarSystemBarViewControllerFactory<*>

    @Binds
    @IntoMap
    @StringKey("top_bar_right_panel")
    abstract fun bindTopBarRightViewControllerFactory(
        factory: CarSystemBarViewControllerImpl.Factory
    ): CarSystemBarViewControllerFactory<*>

    @Binds
    @IntoMap
    @StringKey("bottom_bar_left_panel")
    abstract fun bindBottomBarLeftViewControllerFactory(
        factory: CarSystemBarViewControllerImpl.Factory
    ): CarSystemBarViewControllerFactory<*>

    @Binds
    @IntoMap
    @StringKey("bottom_bar_center_panel")
    abstract fun bindBottomBarCenterViewControllerFactory(
        factory: CarSystemBarViewControllerImpl.Factory
    ): CarSystemBarViewControllerFactory<*>

    @Binds
    @IntoMap
    @StringKey("bottom_bar_right_panel")
    abstract fun bindBottomBarRightViewControllerFactory(
        factory: CarSystemBarViewControllerImpl.Factory
    ): CarSystemBarViewControllerFactory<*>

    companion object {
        @Provides
        @IntoMap
        @StringKey("top_bar_left_panel")
        fun provideTopBarLeftViewSupplier(): CarSystemBarViewSupplier {
            return CarSystemBarViewSupplierUsingLayout(
                R.layout.car_top_left_system_bar,
                R.layout.car_top_left_system_bar_unprovisioned
            )
        }

        @Provides
        @IntoMap
        @StringKey("top_bar_left_panel")
        fun provideTopBarLeftWindowSupplier(): CarSystemBarWindowSupplier {
            return CarSystemBarWindowSupplierUsingLayout(
                R.layout.navigation_bar_window,
                R.id.car_top_bar_left_window
            )
        }

        @Provides
        @IntoMap
        @StringKey("top_bar_right_panel")
        fun provideTopBarRightViewSupplier(): CarSystemBarViewSupplier {
            return CarSystemBarViewSupplierUsingLayout(
                R.layout.car_top_right_system_bar,
                R.layout.car_top_right_system_bar_unprovisioned
            )
        }

        @Provides
        @IntoMap
        @StringKey("top_bar_right_panel")
        fun provideTopBarRightWindowSupplier(): CarSystemBarWindowSupplier {
            return CarSystemBarWindowSupplierUsingLayout(
                R.layout.navigation_bar_window,
                R.id.car_top_bar_right_window
            )
        }

        @Provides
        @IntoMap
        @StringKey("bottom_bar_left_panel")
        fun provideBottomBarLeftViewSupplier(): CarSystemBarViewSupplier {
            return CarSystemBarViewSupplierUsingLayout(
                R.layout.car_bottom_left_system_bar,
                R.layout.car_bottom_left_system_bar_unprovisioned
            )
        }

        @Provides
        @IntoMap
        @StringKey("bottom_bar_left_panel")
        fun provideBottomBarLeftWindowSupplier(): CarSystemBarWindowSupplier {
            return CarSystemBarWindowSupplierUsingLayout(
                R.layout.navigation_bar_window,
                R.id.car_bottom_bar_left_window
            )
        }

        @Provides
        @IntoMap
        @StringKey("bottom_bar_center_panel")
        fun provideBottomBarCenterViewSupplier(): CarSystemBarViewSupplier {
            return CarSystemBarViewSupplierUsingLayout(
                R.layout.car_bottom_center_system_bar,
                R.layout.car_bottom_center_system_bar_unprovisioned
            )
        }

        @Provides
        @IntoMap
        @StringKey("bottom_bar_center_panel")
        fun provideBottomBarCenterWindowSupplier(): CarSystemBarWindowSupplier {
            return CarSystemBarWindowSupplierUsingLayout(
                R.layout.navigation_bar_window,
                R.id.car_bottom_bar_center_window
            )
        }

        @Provides
        @IntoMap
        @StringKey("bottom_bar_right_panel")
        fun provideBottomBarRightViewSupplier(): CarSystemBarViewSupplier {
            return CarSystemBarViewSupplierUsingLayout(
                R.layout.car_bottom_right_system_bar,
                R.layout.car_bottom_right_system_bar_unprovisioned
            )
        }

        @Provides
        @IntoMap
        @StringKey("bottom_bar_right_panel")
        fun provideBottomBarRightWindowSupplier(): CarSystemBarWindowSupplier {
            return CarSystemBarWindowSupplierUsingLayout(
                R.layout.navigation_bar_window,
                R.id.car_bottom_bar_right_window
            )
        }
    }
}
