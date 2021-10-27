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
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.widget.PopupWindow;

import com.android.car.ui.FocusParkingView;
import com.android.car.ui.utils.ViewUtils;
import com.android.systemui.R;
import com.android.systemui.broadcast.BroadcastDispatcher;
import com.android.systemui.car.CarServiceProvider;
import com.android.systemui.car.qc.SystemUIQCView;
import com.android.systemui.statusbar.policy.ConfigurationController;

import java.util.ArrayList;

/**
 * A controller for a panel view associated with a status icon.
 */
public class StatusIconPanelController {
    private static final int DEFAULT_POPUP_WINDOW_ANCHOR_GRAVITY = Gravity.TOP | Gravity.START;

    private final Context mContext;
    private final ArrayList<SystemUIQCView> mQCViews = new ArrayList<>();

    private PopupWindow mPanel;
    private ViewGroup mPanelContent;
    private OnQcViewsFoundListener mOnQcViewsFoundListener;
    private float mDimValue = -1.0f;
    private boolean mUserSwitchEventRegistered;

    private final CarUserManager.UserLifecycleListener mUserLifecycleListener = event -> {
        if (event.getEventType() == CarUserManager.USER_LIFECYCLE_EVENT_TYPE_SWITCHING) {
            reset();
        }
    };

    private final ConfigurationController.ConfigurationListener mConfigurationListener =
            new ConfigurationController.ConfigurationListener() {
                @Override
                public void onLayoutDirectionChanged(boolean isLayoutRtl) {
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

    private final ViewTreeObserver.OnGlobalFocusChangeListener mFocusChangeListener =
            (oldFocus, newFocus) -> {
                if (mPanel != null && oldFocus != null && newFocus instanceof FocusParkingView) {
                    // When nudging out of the panel, RotaryService will focus on the
                    // FocusParkingView to clear the focus highlight. When this occurs, dismiss the
                    // panel.
                    mPanel.dismiss();
                }
            };

    public StatusIconPanelController(
            Context context,
            CarServiceProvider carServiceProvider,
            BroadcastDispatcher broadcastDispatcher,
            ConfigurationController configurationController) {
        mContext = context;

        broadcastDispatcher.registerReceiver(mBroadcastReceiver,
                new IntentFilter(Intent.ACTION_CLOSE_SYSTEM_DIALOGS), /* executor= */ null,
                UserHandle.ALL);
        configurationController.addCallback(mConfigurationListener);

        carServiceProvider.addListener(car -> {
            CarUserManager carUserManager = (CarUserManager) car.getCarManager(
                    Car.CAR_USER_SERVICE);
            if (!mUserSwitchEventRegistered) {
                carUserManager.addListener(Runnable::run, mUserLifecycleListener);
                mUserSwitchEventRegistered = true;
            }
        });
    }

    /**
     * @return list of {@link SystemUIQCView} in this controller
     */
    public ArrayList<SystemUIQCView> getQCViews() {
        return mQCViews;
    }

    public void setOnQcViewsFoundListener(OnQcViewsFoundListener onQcViewsFoundListener) {
        mOnQcViewsFoundListener = onQcViewsFoundListener;
    }

    /**
     * A listener that can be used to attach controllers quick control panels using
     * {@link SystemUIQCView#getLocalQCProvider()}
     */
    public interface OnQcViewsFoundListener {
        /**
         * This method is call up when {@link SystemUIQCView}s are found
         */
        void qcViewsFound(ArrayList<SystemUIQCView> qcViews);
    }

    /**
     * Attaches a panel to a root view that toggles the panel visibility when clicked.
     */
    public void attachPanel(View view, @LayoutRes int layoutRes, @DimenRes int widthRes) {
        attachPanel(view, layoutRes, widthRes, /* xOffset= */ 0,  /* yOffset= */0);
    }

    /**
     * Attaches a panel to a root view that toggles the panel visibility when clicked.
     */
    public void attachPanel(View view, @LayoutRes int layoutRes, @DimenRes int widthRes,
            int xOffset, int yOffset) {
        attachPanel(view, layoutRes, widthRes, xOffset, yOffset,
                DEFAULT_POPUP_WINDOW_ANCHOR_GRAVITY);
    }

    /**
     * Attaches a panel to a root view that toggles the panel visibility when clicked.
     */
    public void attachPanel(View view, @LayoutRes int layoutRes, @DimenRes int widthRes,
            int xOffset, int yOffset, int gravity) {
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
            registerFocusListener(true);

            mPanel.showAsDropDown(view, xOffset, yOffset, gravity);

            dimBehind(mPanel);
        });
    }

    private PopupWindow createPanel(@LayoutRes int layoutRes, @DimenRes int widthRes) {
        int panelWidth = mContext.getResources().getDimensionPixelSize(widthRes);
        mPanelContent = (ViewGroup) LayoutInflater.from(mContext).inflate(layoutRes, /* root= */
                null);
        mPanelContent.setLayoutDirection(View.LAYOUT_DIRECTION_LOCALE);
        findQcViews(mPanelContent);
        if (mOnQcViewsFoundListener != null) {
            mOnQcViewsFoundListener.qcViewsFound(mQCViews);
        }
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
                registerFocusListener(false);
                mQCViews.forEach(qcView -> qcView.listen(false));
            }
        });
        addFocusParkingView();

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

    /**
     * Add a FocusParkingView to the panel content to prevent rotary controller rotation wrapping
     * around in the panel - this only should be called once per panel.
     */
    private void addFocusParkingView() {
        if (mPanelContent != null) {
            FocusParkingView fpv = new FocusParkingView(mContext);
            mPanelContent.addView(fpv);
        }
    }

    private void registerFocusListener(boolean register) {
        if (mPanelContent == null) {
            return;
        }
        if (register) {
            mPanelContent.getViewTreeObserver().addOnGlobalFocusChangeListener(
                    mFocusChangeListener);
        } else {
            mPanelContent.getViewTreeObserver().removeOnGlobalFocusChangeListener(
                    mFocusChangeListener);
        }
    }

    private void reset() {
        if (mPanel == null) return;

        mPanel.dismiss();
        mPanel = null;
        mPanelContent = null;
        mOnQcViewsFoundListener = null;
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
