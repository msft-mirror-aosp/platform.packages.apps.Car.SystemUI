/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.systemui.car.systemdialogs;

import static android.app.admin.DevicePolicyManager.DEVICE_OWNER_TYPE_FINANCED;
import static android.view.WindowInsets.Type.statusBars;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.provider.Settings;
import android.view.Window;
import android.view.WindowManager;

import com.android.systemui.R;
import com.android.systemui.dagger.SysUISingleton;
import com.android.systemui.plugins.ActivityStarter;
import com.android.systemui.statusbar.policy.SecurityController;

import javax.inject.Inject;

/**
 * A controller that can create and control the visibility of various system dialogs.
 */
@SysUISingleton
public class SystemDialogsViewController {
    private final Context mContext;
    private final SecurityController mSecurityController;

    private ActivityStarter mActivityStarter;

    private final AlertDialog.OnClickListener mOnDeviceMonitoringConfirmed = (dialog, which) -> {
        if (which == DialogInterface.BUTTON_POSITIVE) {
            Intent intent = new Intent(Settings.ACTION_ENTERPRISE_PRIVACY_SETTINGS);
            dialog.dismiss();
            mActivityStarter.postStartActivityDismissingKeyguard(intent, /* delay= */ 0);
        }
    };

    @Inject
    public SystemDialogsViewController(
            Context context,
            ActivityStarter activityStarter,
            SecurityController securityController) {
        mContext = context;
        mActivityStarter = activityStarter;
        mSecurityController = securityController;
    }

    protected void showDeviceMonitoringDialog() {
        AlertDialog dialog = new AlertDialog.Builder(mContext,
                com.android.internal.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(createDeviceMonitoringTitle())
                .setMessage(createDeviceMonitoringMessage())
                .setPositiveButton(R.string.ok, mOnDeviceMonitoringConfirmed)
                .create();

        applyCarSysUIDialogFlags(dialog);
        dialog.show();
    }

    private CharSequence createDeviceMonitoringTitle() {
        CharSequence deviceOwnerOrganization = mSecurityController.getDeviceOwnerOrganizationName();

        if (deviceOwnerOrganization != null && isFinancedDevice()) {
            return mContext.getString(R.string.monitoring_title_financed_device,
                    deviceOwnerOrganization);
        } else {
            return mContext.getString(R.string.monitoring_title_device_owned);
        }
    }

    private CharSequence createDeviceMonitoringMessage() {
        CharSequence deviceOwnerOrganization = mSecurityController.getDeviceOwnerOrganizationName();

        if (deviceOwnerOrganization != null) {
            if (isFinancedDevice()) {
                return mContext.getString(R.string.monitoring_financed_description_named_management,
                        deviceOwnerOrganization, deviceOwnerOrganization);
            } else {
                return mContext.getString(
                        R.string.monitoring_description_named_management, deviceOwnerOrganization);
            }
        }
        return mContext.getString(R.string.monitoring_description_management);
    }

    private boolean isFinancedDevice() {
        return mSecurityController.isDeviceManaged()
                && mSecurityController.getDeviceOwnerType(
                mSecurityController.getDeviceOwnerComponentOnAnyUser())
                == DEVICE_OWNER_TYPE_FINANCED;
    }

    private void applyCarSysUIDialogFlags(AlertDialog dialog) {
        Window window = dialog.getWindow();
        window.setType(WindowManager.LayoutParams.TYPE_KEYGUARD_DIALOG);
        window.addFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM
                | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
        window.getAttributes().setFitInsetsTypes(
                window.getAttributes().getFitInsetsTypes() & ~statusBars());
    }
}
