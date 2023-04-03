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

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Bundle;
import android.util.Log;
import android.util.Slog;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import com.android.systemui.Dumpable;
import com.android.systemui.R;
import com.android.systemui.car.CarServiceProvider;
import com.android.systemui.car.statusicon.ui.UserPickerReadOnlyIconsController;
import com.android.systemui.car.userpicker.UserPickerController.Callbacks;
import com.android.systemui.dump.DumpManager;
import com.android.systemui.settings.DisplayTracker;

import java.io.PrintWriter;
import java.util.List;

import javax.inject.Inject;

/**
 * Main activity for user picker.
 *
 * <p>This class uses the Trampoline pattern to ensure the activity is executed as user 0.
 * It has user picker controller object for the executed display, and cleans it up
 * when the activity is destroyed.
 */
public class UserPickerActivity extends Activity implements Dumpable {
    private static final String TAG = UserPickerActivity.class.getSimpleName();
    private static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);

    private UserPickerActivityComponent mUserPickerActivityComponent;

    @Inject
    UserPickerReadOnlyIconsController mUserPickerReadOnlyIconsController;
    @Inject
    DisplayTracker mDisplayTracker;
    @Inject
    DumpManager mDumpManager;

    @VisibleForTesting
    UserPickerController mController;
    @VisibleForTesting
    SnackbarManager mSnackbarManager;
    @VisibleForTesting
    DialogManager mDialogManager;
    @VisibleForTesting
    UserPickerAdapter mAdapter;
    @VisibleForTesting
    UserPickerView mUserPickerView;
    @VisibleForTesting
    View mRootView;
    @VisibleForTesting
    View mHeaderBarTextForLogout;
    @VisibleForTesting
    View mLogoutButton;
    @VisibleForTesting
    View mBackButton;

    @Inject
    UserPickerActivity(
            Context context, //application context
            DisplayTracker displayTracker,
            CarServiceProvider carServiceProvider,
            UserPickerSharedState userPickerSharedState
    ) {
        this();
        mUserPickerActivityComponent = DaggerUserPickerActivityComponent.builder()
                .context(context)
                .carServiceProvider(carServiceProvider)
                .displayTracker(displayTracker)
                .userPickerSharedState(userPickerSharedState)
                .build();
        //Component.inject(this) is not working because constructor and activity itself is
        //scoped to SystemUiScope but the deps below are scoped to UserPickerScope
        mDialogManager = mUserPickerActivityComponent.dialogManager();
        mSnackbarManager = mUserPickerActivityComponent.snackbarManager();
        mController = mUserPickerActivityComponent.userPickerController();
    }

    @VisibleForTesting
    UserPickerActivity() {
        super();
    }

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

        if (DEBUG) {
            Slog.d(TAG, "onCreate: userId=" + getUserId() + " displayId=" + getDisplayId());
        }

        super.onCreate(savedInstanceState);
        setShowWhenLocked(true);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        init();
    }

    @VisibleForTesting
    void init() {
        LayoutInflater inflater = LayoutInflater.from(this);
        mRootView = inflater.inflate(R.layout.user_picker, null);
        if (getWindow() != null) {
            setContentView(mRootView);
            initWindow();
        }

        initManagers(mRootView);
        initViews();
        initController();

        mController.onConfigurationChanged();
        String name = String.format("%s displayId=%d taskId=%d", TAG, getDisplayId(), getTaskId());
        mDumpManager.registerNormalDumpable(name, /* module= */ this);
    }

    private void initViews() {
        View powerBtn = mRootView.findViewById(R.id.power_button_icon_view);
        powerBtn.setOnClickListener(v -> mController.screenOffDisplay());
        mHeaderBarTextForLogout = mRootView.findViewById(R.id.message);

        mLogoutButton = mRootView.findViewById(R.id.logout_button_icon_view);
        mLogoutButton.setOnClickListener(v -> mController.logoutUser());

        mBackButton = mRootView.findViewById(R.id.back_button);
        mBackButton.setOnClickListener(v -> {
            finishAndRemoveTask();
        });

        mUserPickerView = (UserPickerView) mRootView.findViewById(R.id.user_picker);
        mAdapter = createUserPickerAdapter();
        mUserPickerView.setAdapter(mAdapter);

        ViewGroup statusIconContainer = mRootView
                .findViewById(R.id.user_picker_status_icon_container);
        if (statusIconContainer != null && mUserPickerReadOnlyIconsController != null) {
            mUserPickerReadOnlyIconsController.addIconViews(statusIconContainer,
                    /* shouldAttachPanel= */ false);
        }
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

    @VisibleForTesting
    UserPickerAdapter createUserPickerAdapter() {
        return new UserPickerAdapter(this);
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
        mDumpManager.unregisterDumpable(TAG + "#" + getDisplayId());

        super.onDestroy();
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        mAdapter.onConfigurationChanged();
        mController.onConfigurationChanged();
    }

    @VisibleForTesting
    void setupHeaderBar(HeaderState headerState) {
        int state = headerState.getState();
        switch (state) {
            case HEADER_STATE_LOGOUT:
                mHeaderBarTextForLogout.setVisibility(View.VISIBLE);
                mBackButton.setVisibility(View.GONE);
                mLogoutButton.setVisibility(View.GONE);
                break;
            case HEADER_STATE_CHANGE_USER:
                mHeaderBarTextForLogout.setVisibility(View.GONE);
                mBackButton.setVisibility(View.VISIBLE);
                if (getDisplayId() != mDisplayTracker.getDefaultDisplayId()) {
                    mLogoutButton.setVisibility(View.VISIBLE);
                }
                break;
        }
    }

    @Override
    public void dump(@NonNull PrintWriter pw, @NonNull String[] args) {
        if (mController != null) {
            mController.dump(pw);
        }
        if (mUserPickerView != null && mUserPickerView.getAdapter() != null) {
            ((UserPickerAdapter) mUserPickerView.getAdapter()).dump(pw);
        }
    }
}
