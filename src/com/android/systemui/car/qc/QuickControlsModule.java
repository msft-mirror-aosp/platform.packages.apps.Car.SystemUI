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

package com.android.systemui.car.qc;

import com.android.systemui.car.flexibleui.CarSystemBarElementController;
import com.android.systemui.car.qc.footer.logout.QCLogoutModule;
import com.android.systemui.car.qc.profileswitcher.QCProfileSwitcherModule;
import com.android.systemui.car.systembar.privacy.camera.CameraQcPanelModule;
import com.android.systemui.car.systembar.privacy.mic.MicQcPanelModule;

import dagger.Binds;
import dagger.Module;
import dagger.multibindings.ClassKey;
import dagger.multibindings.IntoMap;

/**
 * Dagger injection module for {@link SystemUIQCViewController}
 */
@Module(includes = {CameraQcPanelModule.class,
        MicQcPanelModule.class,
        QCLogoutModule.class,
        QCProfileSwitcherModule.class})
public abstract class QuickControlsModule {

    /** Injects SystemUIQCViewController. */
    @Binds
    @IntoMap
    @ClassKey(SystemUIQCViewController.class)
    public abstract CarSystemBarElementController.Factory bindQCViewControllerFactory(
            SystemUIQCViewController.Factory factory);

    /** Injects QCFooterButtonController. */
    @Binds
    @IntoMap
    @ClassKey(QCFooterButtonController.class)
    public abstract CarSystemBarElementController.Factory bindQCFooterButtonControllerFactory(
            QCFooterButtonController.Factory factory);

    /** Injects QCFooterViewController. */
    @Binds
    @IntoMap
    @ClassKey(QCFooterViewController.class)
    public abstract CarSystemBarElementController.Factory bindQCFooterViewControllerFactory(
            QCFooterViewController.Factory factory);

    /** Injects QCScreenOffButtonController. */
    @Binds
    @IntoMap
    @ClassKey(QCScreenOffButtonController.class)
    public abstract CarSystemBarElementController.Factory bindQCScreenOffButtonControllerFactory(
            QCScreenOffButtonController.Factory factory);

    /** Injects QCUserPickerButtonController. */
    @Binds
    @IntoMap
    @ClassKey(QCUserPickerButtonController.class)
    public abstract CarSystemBarElementController.Factory bindQCUserPickerButtonControllerFactory(
            QCUserPickerButtonController.Factory factory);
}
