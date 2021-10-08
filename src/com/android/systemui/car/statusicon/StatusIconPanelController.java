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

package com.android.systemui.car.statusicon;

import static android.view.WindowManager.LayoutParams.TYPE_STATUS_BAR_SUB_PANEL;
import static android.widget.ListPopupWindow.WRAP_CONTENT;

import android.annotation.DimenRes;
import android.annotation.LayoutRes;
import android.car.Car;
import android.car.user.CarUserManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.UserHandle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.PopupWindow;

import com.android.car.ui.utils.ViewUtils;
import com.android.systemui.R;
import com.android.systemui.broadcast.BroadcastDispatcher;
import com.android.systemui.car.CarServiceProvider;
import com.android.systemui.car.qc.SystemUIQCView;

import java.util.ArrayList;

/**
 * A controller for a panel view associated with a status icon.
 */
public class StatusIconPanelController {
    private final Context mContext;
    private final ArrayList<SystemUIQCView> mQCViews = new ArrayList<>();

    private PopupWindow mPanel;
    private ViewGroup mPanelContent;
    private float mDimValue = -1.0f;
    private boolean mUserSwitchEventRegistered;

    private final CarUserManager.UserLifecycleListener mUserLifecycleListener = event -> {
        if (event.getEventType() == CarUserManager.USER_LIFECYCLE_EVENT_TYPE_SWITCHING) {
            reset();
        }
    };
    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (Intent.ACTION_CLOSE_SYSTEM_DIALOGS.equals(action) && mPanel != null
                    && mPanel.isShowing()) {
                mPanel.dismiss();
            }
        }
    };

    public StatusIconPanelController(
            Context context,
            CarServiceProvider carServiceProvider,
            BroadcastDispatcher broadcastDispatcher) {
        mContext = context;

        broadcastDispatcher.registerReceiver(mBroadcastReceiver,
                new IntentFilter(Intent.ACTION_CLOSE_SYSTEM_DIALOGS), /* executor= */ null,
                UserHandle.ALL);

        carServiceProvider.addListener(car -> {
            CarUserManager carUserManager = (CarUserManager) car.getCarManager(
                    Car.CAR_USER_SERVICE);
            if (!mUserSwitchEventRegistered) {
                carUserManager.addListener(Runnable::run, mUserLifecycleListener);
                mUserSwitchEventRegistered = true;
            }
        });
    }

    protected void attachPanel(View view, @LayoutRes int layoutRes, @DimenRes int widthRes) {
        view.setOnClickListener((v) -> {
            if (mPanel == null) {
                mPanel = createPanel(layoutRes, widthRes);
            }

            if (mPanel.isShowing()) {
                mPanel.dismiss();
                return;
            }

            mQCViews.forEach(qcView -> qcView.listen(true));

            // Clear the focus highlight in this window since a dialog window is about to show.
            // TODO(b/201700195): remove this workaround once the window focus issue is fixed.
            if (view.isFocused()) {
                ViewUtils.hideFocus(view.getRootView());
            }

            mPanel.showAsDropDown(view);

            dimBehind(mPanel);
        });
    }

    private PopupWindow createPanel(@LayoutRes int layoutRes, @DimenRes int widthRes) {
        int panelWidth = mContext.getResources().getDimensionPixelSize(widthRes);
        mPanelContent = (ViewGroup) LayoutInflater.from(mContext).inflate(layoutRes, /* root= */
                null);
        findQcViews(mPanelContent);
        PopupWindow panel = new PopupWindow(mPanelContent, panelWidth, WRAP_CONTENT);
        panel.setBackgroundDrawable(
                mContext.getResources().getDrawable(R.drawable.status_icon_panel_bg,
                        mContext.getTheme()));
        panel.setWindowLayoutType(TYPE_STATUS_BAR_SUB_PANEL);
        panel.setFocusable(true);
        panel.setOutsideTouchable(false);
        panel.setOnDismissListener(new PopupWindow.OnDismissListener() {
            @Override
            public void onDismiss() {
                mQCViews.forEach(qcView -> qcView.listen(false));
            }
        });

        return panel;
    }

    private void dimBehind(PopupWindow popupWindow) {
        View container = popupWindow.getContentView().getRootView();
        WindowManager wm = mContext.getSystemService(WindowManager.class);

        if (wm == null) return;

        if (mDimValue < 0) {
            mDimValue = mContext.getResources().getFloat(R.dimen.car_status_icon_panel_dim);
        }

        WindowManager.LayoutParams lp = (WindowManager.LayoutParams) container.getLayoutParams();
        lp.flags |= WindowManager.LayoutParams.FLAG_DIM_BEHIND;
        lp.dimAmount = mDimValue;
        wm.updateViewLayout(container, lp);
    }

    private void reset() {
        if (mPanel == null) return;

        mPanel.dismiss();
        mPanel = null;
        mPanelContent = null;
        mQCViews.forEach(v -> v.destroy());
        mQCViews.clear();
    }

    private void findQcViews(ViewGroup rootView) {
        for (int i = 0; i < rootView.getChildCount(); i++) {
            View v = rootView.getChildAt(i);
            if (v instanceof SystemUIQCView) {
                mQCViews.add((SystemUIQCView) v);
            } else if (v instanceof ViewGroup) {
                this.findQcViews((ViewGroup) v);
            }
        }
    }
}
