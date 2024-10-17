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

package com.android.systemui.car.keyguard.passenger;

import com.android.systemui.R;
import com.android.systemui.car.window.OverlayViewController;
import com.android.systemui.car.window.OverlayViewGlobalStateController;
import com.android.systemui.dagger.SysUISingleton;

import javax.inject.Inject;

/**
 * Controller for the instantiation and visibility of the passenger keyguard overlay.
 */
@SysUISingleton
public class PassengerKeyguardOverlayViewController extends OverlayViewController {
    private final PassengerKeyguardCredentialViewControllerFactory mCredentialViewFactory;

    private PassengerKeyguardCredentialViewController mCredentialViewController;

    @Inject
    public PassengerKeyguardOverlayViewController(
            OverlayViewGlobalStateController overlayViewGlobalStateController,
            PassengerKeyguardCredentialViewControllerFactory credentialViewFactory) {
        super(R.id.passenger_keyguard_stub, overlayViewGlobalStateController);
        mCredentialViewFactory = credentialViewFactory;
    }

    @Override
    protected void onFinishInflate() {
        mCredentialViewController = mCredentialViewFactory.create(
                getLayout().requireViewById(R.id.passenger_keyguard_frame));
        mCredentialViewController.setAuthSucceededCallback(this::stop);
    }

    @Override
    protected void hideInternal() {
        super.hideInternal();
        if (mCredentialViewController != null) {
            mCredentialViewController.clearAllCredentials();
        }
    }
}
