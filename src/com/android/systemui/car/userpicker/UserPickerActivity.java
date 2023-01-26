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

import static com.android.systemui.car.userpicker.HeaderState.HEADER_STATE_CHANGE_USER;
import static com.android.systemui.car.userpicker.HeaderState.HEADER_STATE_LOGOUT;

import android.annotation.NonNull;
import android.app.Activity;
import android.content.res.Configuration;
import android.os.Bundle;
import android.util.Log;
import android.util.Slog;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;

import com.android.systemui.CarSystemUIApplication;
import com.android.systemui.CoreStartable;
import com.android.systemui.R;
import com.android.systemui.car.userpicker.UserPickerController.Callbacks;
import com.android.systemui.settings.DisplayTracker;

import java.util.List;

import javax.inject.Inject;

/**
 * Main activity for user picker.
 *
 * <p>This class uses the Trampoline pattern to ensure the activity is executed as user 0.
 * It has user picker controller object for the executed display, and cleans it up
 * when the activity is destroyed.
 */
@UserPickerScope
public final class UserPickerActivity extends Activity {
    private static final String TAG = UserPickerActivity.class.getSimpleName();
    private static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);

    private UserPickerActivityComponent mUserPickerActivityComponent;

    @Inject
    UserPickerController mController;
    @Inject
    SnackbarManager mSnackbarManager;
    @Inject
    DialogManager mDialogManager;
    @Inject
    DisplayTracker mDisplayTracker;

    private UserPickerAdapter mAdapter;
    private UserPickerView mUserPickerView;
    private View mRootView;
    private View mHeaderBarTextForLogout;
    private View mLogoutButton;
    private View mBackButton;

    private final Callbacks mCallbacks = new Callbacks() {
        @Override
        public void onUpdateUsers(List<UserRecord> users) {
            mAdapter.updateUsers(users);
            mAdapter.notifyDataSetChanged();
        }

        @Override
        public void onHeaderStateChanged(HeaderState headerState) {
            setupHeaderBar(headerState);
        }

        @Override
        public void onFinishRequested() {
            finishAndRemoveTask();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if (!ActivityHelper.startUserPickerAsUserSystem(this)) {
            super.onCreate(savedInstanceState);
            return;
        }

        // Dependency injection starts.
        injectDependencies();

        if (DEBUG) {
            Slog.d(TAG, "onCreate: userId=" + getUserId() + " displayId=" + getDisplayId());
        }

        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        LayoutInflater inflater = LayoutInflater.from(this);
        mRootView = inflater.inflate(R.layout.user_picker, null);
        setContentView(mRootView);

        initWindow();
        initManagers(mRootView);
        initViews();
        initController();

        mController.onConfigurationChanged();
    }

    private void injectDependencies() {
        CarSystemUIApplication app = (CarSystemUIApplication) getApplication();
        for (CoreStartable c : app.getServices()) {
            if (c instanceof UserPicker) {
                mUserPickerActivityComponent = ((UserPicker) c).inject(this);
                break;
            }
        }
    }

    private void initViews() {
        View powerBtn = mRootView.findViewById(R.id.power_button_icon_view);
        powerBtn.setOnClickListener(v -> mController.screenOffDisplay());
        mHeaderBarTextForLogout = mRootView.findViewById(R.id.message);

        mLogoutButton = mRootView.findViewById(R.id.logout_button_icon_view);
        mLogoutButton.setOnClickListener(v -> mController.logoutUser());

        mBackButton = mRootView.findViewById(R.id.back_button);
        mBackButton.setOnClickListener(v -> finishAndRemoveTask());

        mUserPickerView = (UserPickerView) mRootView.findViewById(R.id.user_picker);
        mAdapter = new UserPickerAdapter(this);
        mUserPickerView.setAdapter(mAdapter);
    }

    private void initWindow() {
        Window window = getWindow();
        WindowInsetsController insetsController = window.getInsetsController();
        if (insetsController != null) {
            insetsController.setAnimationsDisabled(true);
            insetsController.hide(WindowInsets.Type.statusBars()
                    | WindowInsets.Type.navigationBars());
        }
    }

    private void initManagers(View rootView) {
        mDialogManager.initContextFromView(rootView);
        mSnackbarManager.setRootView(rootView);
    }

    private void initController() {
        mController.init(mCallbacks, getDisplayId());
    }

    @Override
    protected void onDestroy() {
        if (DEBUG) {
            Slog.d(TAG, "onDestroy: displayId=" + getDisplayId());
        }
        if (mController != null) {
            mController.onDestroy();
        }
        if (mDialogManager != null) {
            mDialogManager.clearAllDialogs();
        }

        super.onDestroy();
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        mAdapter.onConfigurationChanged();
        mController.onConfigurationChanged();
    }

    private void setupHeaderBar(HeaderState headerState) {
        int state = headerState.getState();
        switch (state) {
            case HEADER_STATE_LOGOUT:
                mHeaderBarTextForLogout.setVisibility(View.VISIBLE);
                mBackButton.setVisibility(View.INVISIBLE);
                if (getDisplayId() != mDisplayTracker.getDefaultDisplayId()) {
                    mLogoutButton.setVisibility(View.INVISIBLE);
                }
                break;
            case HEADER_STATE_CHANGE_USER:
                mHeaderBarTextForLogout.setVisibility(View.INVISIBLE);
                mBackButton.setVisibility(View.VISIBLE);
                if (getDisplayId() != mDisplayTracker.getDefaultDisplayId()) {
                    mLogoutButton.setVisibility(View.VISIBLE);
                }
                break;
        }
    }
}
