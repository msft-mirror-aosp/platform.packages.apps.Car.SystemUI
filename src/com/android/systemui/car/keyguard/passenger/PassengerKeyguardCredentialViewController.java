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

import android.app.trust.TrustManager;
import android.car.Car;
import android.car.SyncResultCallback;
import android.car.user.CarUserManager;
import android.car.user.UserStopRequest;
import android.car.user.UserStopResponse;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.CallSuper;

import com.android.internal.widget.LockPatternChecker;
import com.android.internal.widget.LockPatternUtils;
import com.android.internal.widget.LockscreenCredential;
import com.android.systemui.R;
import com.android.systemui.car.CarServiceProvider;
import com.android.systemui.settings.UserTracker;
import com.android.systemui.util.ViewController;

/**
 * Base ViewController for PassengerKeyguard credential views.
 */
public abstract class PassengerKeyguardCredentialViewController extends ViewController<View> {
    private static final String TAG = PassengerKeyguardCredentialViewController.class.getName();
    private final LockPatternUtils mLockPatternUtils;
    private final UserTracker mUserTracker;
    private final TrustManager mTrustManager;
    private final Handler mMainHandler;
    private final CarServiceProvider mCarServiceProvider;

    private OnAuthSucceededCallback mCallback;
    private LockscreenCredential mEnteredPassword;
    private TextView mErrorMessageView;
    private Button mCancelButton;

    private CarUserManager mCarUserManager;
    private final CarServiceProvider.CarServiceOnConnectedListener mCarConnectedListener =
            new CarServiceProvider.CarServiceOnConnectedListener() {
                @Override
                public void onConnected(Car car) {
                    mCarUserManager = car.getCarManager(CarUserManager.class);
                }
            };

    protected PassengerKeyguardCredentialViewController(View view,
            LockPatternUtils lockPatternUtils, UserTracker userTracker, TrustManager trustManager,
            Handler mainHandler, CarServiceProvider carServiceProvider) {
        super(view);
        mLockPatternUtils = lockPatternUtils;
        mUserTracker = userTracker;
        mTrustManager = trustManager;
        mMainHandler = mainHandler;
        mCarServiceProvider = carServiceProvider;
    }

    @Override
    protected void onInit() {
        super.onInit();
        mErrorMessageView = mView.findViewById(R.id.message);
        mCancelButton = mView.findViewById(R.id.cancel_button);
        if (mCancelButton != null) {
            mCancelButton.setOnClickListener(v -> stopUser());
        }
    }

    @Override
    protected void onViewAttached() {
        mCarServiceProvider.addListener(mCarConnectedListener);
    }

    @Override
    protected void onViewDetached() {
        mCarServiceProvider.removeListener(mCarConnectedListener);
        mCarUserManager = null;
    }

    protected abstract LockscreenCredential getCurrentCredential();

    protected final void verifyCredential(Runnable onFailureUiRunnable) {
        mEnteredPassword = getCurrentCredential();
        if (mEnteredPassword.isNone()) {
            Log.e(TAG, "Expected to verify real credential but got none");
            return;
        }
        LockPatternChecker.verifyCredential(mLockPatternUtils, mEnteredPassword,
                mUserTracker.getUserId(), /* flags= */ 0,
                (response, throttleTimeoutMs) -> {
                    if (response.isMatched()) {
                        mTrustManager.reportEnabledTrustAgentsChanged(mUserTracker.getUserId());
                        if (mCallback != null) {
                            mCallback.onAuthSucceeded();
                        }
                    } else {
                        mMainHandler.post(onFailureUiRunnable);
                    }
                });
    }

    protected final void setErrorMessage(String message) {
        if (mErrorMessageView != null) {
            mErrorMessageView.setText(message);
        }
    }

    protected final void clearError() {
        if (mErrorMessageView != null && !TextUtils.isEmpty(mErrorMessageView.getText())) {
            mErrorMessageView.setText("");
        }
    }

    /**
     * Clear all credential data from memory. Subclasses should override and clear any necessary
     * fields and then call super.
     */
    @CallSuper
    public void clearAllCredentials() {
        if (mEnteredPassword != null) {
            mEnteredPassword.zeroize();
        }

        System.gc();
        System.runFinalization();
        System.gc();
    }

    private void stopUser() {
        if (mCarUserManager == null) {
            return;
        }

        SyncResultCallback<UserStopResponse> userStopCallback = new SyncResultCallback<>();
        mCarUserManager.stopUser(
                new UserStopRequest.Builder(mUserTracker.getUserHandle()).withDelayedLocking(
                        false).build(), getContext().getMainExecutor(), userStopCallback);
    }

    /**
     * Set callback to be called when authentication has succeeded.
     */
    public final void setAuthSucceededCallback(OnAuthSucceededCallback callback) {
        mCallback = callback;
    }

    public interface OnAuthSucceededCallback {
        /** Called when passenger keyguard authentication has succeeded. */
        void onAuthSucceeded();
    }
}
