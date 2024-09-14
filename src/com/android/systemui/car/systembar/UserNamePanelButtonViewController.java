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

package com.android.systemui.car.systembar;

import android.app.ActivityOptions;
import android.car.app.CarActivityManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.android.car.ui.utils.CarUxRestrictionsUtil;
import com.android.systemui.R;
import com.android.systemui.car.CarDeviceProvisionedController;
import com.android.systemui.car.CarServiceProvider;
import com.android.systemui.car.statusicon.StatusIconPanelViewController;
import com.android.systemui.car.systembar.element.CarSystemBarElementController;
import com.android.systemui.car.systembar.element.CarSystemBarElementStateController;
import com.android.systemui.car.systembar.element.CarSystemBarElementStatusBarDisableController;
import com.android.systemui.car.users.CarSystemUIUserUtil;
import com.android.systemui.settings.UserTracker;

import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;
import dagger.assisted.AssistedInject;

import java.net.URISyntaxException;

import javax.inject.Provider;

public class UserNamePanelButtonViewController extends CarSystemBarPanelButtonViewController {
    private static final String TAG = UserNamePanelButtonViewController.class.getName();
    private final Context mContext;
    private final UserTracker mUserTracker;
    private final CarServiceProvider mCarServiceProvider;
    private final CarDeviceProvisionedController mCarDeviceProvisionedController;
    private final boolean mIsMUMDSystemUI;
    private CarActivityManager mCarActivityManager;

    private final CarServiceProvider.CarServiceOnConnectedListener mCarServiceOnConnectedListener =
            car -> {
                mCarActivityManager = car.getCarManager(CarActivityManager.class);
            };

    @AssistedInject
    protected UserNamePanelButtonViewController(@Assisted CarSystemBarPanelButtonView view,
            CarSystemBarElementStatusBarDisableController disableController,
            CarSystemBarElementStateController stateController,
            Provider<StatusIconPanelViewController.Builder> statusIconPanelBuilder,
            Context context, UserTracker userTracker, CarServiceProvider carServiceProvider,
            CarDeviceProvisionedController deviceProvisionedController) {
        super(view, disableController, stateController, statusIconPanelBuilder);
        mContext = context;
        mUserTracker = userTracker;
        mCarServiceProvider = carServiceProvider;
        mCarDeviceProvisionedController = deviceProvisionedController;
        mIsMUMDSystemUI = CarSystemUIUserUtil.isMUMDSystemUI();
    }

    @AssistedFactory
    public interface Factory extends
            CarSystemBarElementController.Factory<CarSystemBarPanelButtonView,
                    UserNamePanelButtonViewController> {
    }

    @Override
    protected void onInit() {
        if (mIsMUMDSystemUI) {
            // TODO(b/269490856): consider removal of UserPicker carve-outs
            mView.setOnClickListener(getMUMDUserPickerClickListener());
        } else {
            super.onInit();
        }
        if (!Build.IS_ENG && !Build.IS_USERDEBUG) {
            return;
        }
        String longIntentString = mContext.getString(R.string.user_profile_long_press_intent);
        if (!TextUtils.isEmpty(longIntentString)) {
            Intent intent;
            try {
                intent = Intent.parseUri(longIntentString, Intent.URI_INTENT_SCHEME);
            } catch (URISyntaxException e) {
                return;
            }
            Intent finalIntent = intent;
            mView.setOnLongClickListener(v -> {
                Intent broadcast = new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
                mContext.sendBroadcastAsUser(broadcast, mUserTracker.getUserHandle());
                try {
                    ActivityOptions options = ActivityOptions.makeBasic();
                    options.setLaunchDisplayId(mContext.getDisplayId());
                    mContext.startActivityAsUser(finalIntent, options.toBundle(),
                            mUserTracker.getUserHandle());
                } catch (Exception e) {
                    Log.e(TAG, "Failed to launch intent", e);
                }
                return true;
            });
        }
    }

    @Override
    protected void onViewAttached() {
        super.onViewAttached();
        if (mIsMUMDSystemUI) {
            mCarServiceProvider.addListener(mCarServiceOnConnectedListener);
        }
    }

    @Override
    protected void onViewDetached() {
        super.onViewDetached();
        if (mIsMUMDSystemUI) {
            mCarServiceProvider.removeListener(mCarServiceOnConnectedListener);
            mCarActivityManager = null;
        }
    }

    @Override
    protected boolean shouldRestoreState() {
        // TODO(b/269490856): consider removal of UserPicker carve-outs
        return !CarSystemUIUserUtil.isMUMDSystemUI();
    }

    private View.OnClickListener getMUMDUserPickerClickListener() {
        boolean disabledWhileDriving =
                mView.getDisabledWhileDriving() != null ? mView.getDisabledWhileDriving()
                        : false;
        boolean disabledWhileUnprovisioned = mView.getDisabledWhileUnprovisioned() != null
                ? mView.getDisabledWhileUnprovisioned() : false;
        CarUxRestrictionsUtil carUxRestrictionsUtil;
        if (disabledWhileDriving) {
            carUxRestrictionsUtil = CarUxRestrictionsUtil.getInstance(mContext);
        } else {
            carUxRestrictionsUtil = null;
        }
        return v -> {
            if (disabledWhileUnprovisioned && !isDeviceSetupForUser()) {
                return;
            }
            if (disabledWhileDriving && carUxRestrictionsUtil.getCurrentRestrictions()
                    .isRequiresDistractionOptimization()) {
                Toast.makeText(mContext, R.string.car_ui_restricted_while_driving,
                        Toast.LENGTH_LONG).show();
                return;
            }
            if (mCarActivityManager != null) {
                mCarActivityManager.startUserPickerOnDisplay(mContext.getDisplayId());
            }
        };
    }

    private boolean isDeviceSetupForUser() {
        return mCarDeviceProvisionedController.isCurrentUserSetup()
                && !mCarDeviceProvisionedController.isCurrentUserSetupInProgress();
    }
}
