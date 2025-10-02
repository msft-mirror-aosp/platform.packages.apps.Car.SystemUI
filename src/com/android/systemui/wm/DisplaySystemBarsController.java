/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.systemui.wm;

import static android.car.CarOccupantZoneManager.INVALID_USER_ID;
import static android.content.Intent.ACTION_OVERLAY_CHANGED;
import static android.view.WindowInsets.Type.navigationBars;
import static android.view.WindowInsets.Type.statusBars;
import static android.view.WindowInsets.Type.systemBars;

import static com.android.systemui.car.Flags.packageLevelSystemBarVisibility;
import static com.android.systemui.car.systembar.SystemBarUtil.INVISIBLE_BAR_VISIBILITIES_TYPES_INDEX;
import static com.android.systemui.car.systembar.SystemBarUtil.SYSTEM_BAR_PERSISTENCY_CONFIG_BARPOLICY;
import static com.android.systemui.car.systembar.SystemBarUtil.SYSTEM_BAR_PERSISTENCY_CONFIG_IMMERSIVE;
import static com.android.systemui.car.systembar.SystemBarUtil.SYSTEM_BAR_PERSISTENCY_CONFIG_IMMERSIVE_WITH_NAV;
import static com.android.systemui.car.systembar.SystemBarUtil.SYSTEM_BAR_PERSISTENCY_CONFIG_NON_IMMERSIVE;
import static com.android.systemui.car.systembar.SystemBarUtil.SYSTEM_BAR_SUW_PERSISTENCY_CONFIG_DISABLED;
import static com.android.systemui.car.systembar.SystemBarUtil.SYSTEM_BAR_SUW_PERSISTENCY_CONFIG_IMMERSIVE;
import static com.android.systemui.car.systembar.SystemBarUtil.SYSTEM_BAR_SUW_PERSISTENCY_CONFIG_IMMERSIVE_WITH_NAV;
import static com.android.systemui.car.systembar.SystemBarUtil.SYSTEM_BAR_SUW_PERSISTENCY_CONFIG_IMMERSIVE_WITH_STATUS;
import static com.android.systemui.car.systembar.SystemBarUtil.VISIBLE_BAR_VISIBILITIES_TYPES_INDEX;
import static com.android.systemui.car.users.CarSystemUIUserUtil.isSecondaryMUMDSystemUI;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.car.settings.CarSettings;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.PatternMatcher;
import android.os.RemoteException;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.Slog;
import android.util.SparseArray;
import android.view.IDisplayWindowInsetsController;
import android.view.IWindowManager;
import android.view.InsetsController;
import android.view.InsetsSourceControl;
import android.view.InsetsState;
import android.view.WindowInsets;
import android.view.WindowInsets.Type.InsetsType;
import android.view.inputmethod.ImeTracker;
import android.view.inputmethod.InputMethodManager;

import androidx.annotation.VisibleForTesting;

import com.android.systemui.R;
import com.android.systemui.car.wm.CarWMUserHelper;
import com.android.systemui.dagger.qualifiers.Main;
import com.android.wm.shell.common.DisplayController;
import com.android.wm.shell.common.DisplayInsetsController;
import com.android.wm.shell.sysui.ShellController;
import com.android.wm.shell.sysui.UserChangeListener;

import java.util.Arrays;
import java.util.Objects;

import javax.annotation.concurrent.GuardedBy;

/**
 * Controller that maps between displays and {@link IDisplayWindowInsetsController} in order to
 * give system bar control to SystemUI.
 * {@link R.bool#config_remoteInsetsControllerControlsSystemBars} determines whether this controller
 * takes control or not.
 */
public class DisplaySystemBarsController implements DisplayController.OnDisplaysChangedListener {

    private static final String TAG = DisplaySystemBarsController.class.getSimpleName();
    private static final int STATE_NON_IMMERSIVE = systemBars();
    private static final int STATE_IMMERSIVE_WITH_NAV_BAR = navigationBars();
    private static final int STATE_IMMERSIVE_WITH_STATUS_BAR = statusBars();
    private static final int STATE_IMMERSIVE = 0;
    private static final boolean DEBUG = Build.IS_DEBUGGABLE;

    protected final Context mContext;
    protected final IWindowManager mWmService;
    protected final DisplayInsetsController mDisplayInsetsController;
    protected final Handler mHandler;
    protected final CarWMUserHelper mUserHelper;
    protected final ContentObserver mSuwSettingsObserver;

    private final int[] mDefaultVisibilities =
            new int[]{WindowInsets.Type.systemBars(), 0};
    private final int[] mImmersiveWithNavBarVisibilities = new int[]{
            WindowInsets.Type.navigationBars() | WindowInsets.Type.captionBar()
                    | WindowInsets.Type.systemOverlays(),
            WindowInsets.Type.statusBars()
    };
    private final int[] mImmersiveWithStatusBarVisibilities = new int[]{
            WindowInsets.Type.statusBars() | WindowInsets.Type.captionBar()
                    | WindowInsets.Type.systemOverlays(),
            WindowInsets.Type.navigationBars()
    };
    private final int[] mImmersiveVisibilities =
            new int[]{0, WindowInsets.Type.systemBars()};
    private static final String OVERLAY_FILTER_DATA_SCHEME = "package";

    private final Object mPerDisplaySparseArrayLock = new Object();

    private int mBehavior;
    private int mSuwBehavior;
    private BroadcastReceiver mOverlayChangeBroadcastReceiver;
    private CarWMUserHelper.OccupantZoneChangeListener mOccupantChangeListener;
    private final ShellController mShellController;
    private final BarControlPolicy mBarControlPolicy;

    @GuardedBy("mPerDisplaySparseArrayLock")
    @VisibleForTesting
    SparseArray<PerDisplay> mPerDisplaySparseArray;

    public DisplaySystemBarsController(
            Context context,
            IWindowManager wmService,
            DisplayController displayController,
            DisplayInsetsController displayInsetsController,
            @Main Handler mainHandler,
            CarWMUserHelper carWMUserHelper,
            ShellController shellController) {
        mContext = context;
        mWmService = wmService;
        mDisplayInsetsController = displayInsetsController;
        mHandler = mainHandler;
        mUserHelper = carWMUserHelper;
        mBehavior = mContext.getResources().getInteger(
                R.integer.config_systemBarPersistency);
        mSuwBehavior = mContext.getResources().getInteger(
                R.integer.config_systemBarSuwBehavior);
        mShellController = shellController;
        mBarControlPolicy = new BarControlPolicy();

        mSuwSettingsObserver = new ContentObserver(mHandler) {
            @Override
            public void onChange(boolean selfChange, @Nullable Uri uri, int flags) {
                onUserSetupInProgressChangedPerDisplay();
            }
        };

        mShellController.addUserChangeListener(new UserChangeListener() {
            @Override
            public void onUserChanged(int newUserId, @NonNull Context userContext) {
                onUserSetupInProgressChangedPerDisplay();
            }
        });

        if (!isSecondaryMUMDSystemUI()) {
            // This WM controller should only be initialized once for the primary SystemUI, as it
            // will affect insets on all displays.
            // TODO(b/262773276): support per-user remote inset controllers
            displayController.addDisplayWindowListener(this);
            mContext.getContentResolver().registerContentObserver(Settings.Secure.getUriFor(
                            CarSettings.Secure.KEY_SETUP_WIZARD_IN_PROGRESS),
                    /* notifyForDescendants= */ true, mSuwSettingsObserver, UserHandle.USER_ALL);
            registerOverlayChangeBroadcastReceiver();
            registerOccupantZoneChangeListener();
        }
    }

    private void onUserSetupInProgressChangedPerDisplay() {
        synchronized (mPerDisplaySparseArrayLock) {
            if (mPerDisplaySparseArray == null) {
                return;
            }
            for (int i = 0; i < mPerDisplaySparseArray.size(); i++) {
                mPerDisplaySparseArray.valueAt(i).onUserSetupInProgressChanged();
            }
        }
    }

    @Override
    public void onDisplayAdded(int displayId) {
        PerDisplay pd = new PerDisplay(displayId);
        pd.register();
        // Lazy loading policy control filters instead of during boot.
        synchronized (mPerDisplaySparseArrayLock) {
            if (mPerDisplaySparseArray == null) {
                mPerDisplaySparseArray = new SparseArray<>();
                mBarControlPolicy.reloadFromSetting(mContext);
                mBarControlPolicy.registerSystemBarVisibilityOverrideObserver(mContext,
                        mHandler,
                        () -> {
                            synchronized (mPerDisplaySparseArrayLock) {
                                int size = mPerDisplaySparseArray.size();
                                for (int i = 0; i < size; i++) {
                                    mPerDisplaySparseArray.valueAt(
                                            i).updateDisplayWindowRequestedVisibleTypes();
                                }
                            }
                            // Add a return statement to satisfy the compiler's inferred return
                            // type.
                            return null;
                        });
            }
            mPerDisplaySparseArray.put(displayId, pd);
        }
    }

    @Override
    public void onDisplayRemoved(int displayId) {
        synchronized (mPerDisplaySparseArrayLock) {
            PerDisplay pd = mPerDisplaySparseArray.get(displayId);
            pd.unregister();
            mPerDisplaySparseArray.remove(displayId);
        }
    }

    private void registerOverlayChangeBroadcastReceiver() {
        IntentFilter overlayFilter = new IntentFilter(ACTION_OVERLAY_CHANGED);
        overlayFilter.addDataScheme(OVERLAY_FILTER_DATA_SCHEME);
        overlayFilter.addDataSchemeSpecificPart(mContext.getPackageName(),
                PatternMatcher.PATTERN_LITERAL);
        mOverlayChangeBroadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mBehavior = mContext.getResources().getInteger(
                        R.integer.config_systemBarPersistency);
                mSuwBehavior = mContext.getResources().getInteger(
                        R.integer.config_systemBarSuwBehavior);
                Slog.d(TAG, "Update system bar persistency behavior to" + mBehavior
                        + " and suw behavior to " + mSuwBehavior
                        + " on overlay change on userId = " + mContext.getUserId());
            }
        };
        mContext.registerReceiver(mOverlayChangeBroadcastReceiver,
                overlayFilter, /* broadcastPermission= */ null, /* handler= */ null);
    }

    private void registerOccupantZoneChangeListener() {
        mOccupantChangeListener = () -> {
            synchronized (mPerDisplaySparseArrayLock) {
                if (mPerDisplaySparseArray == null) {
                    return;
                }
                for (int i = 0; i < mPerDisplaySparseArray.size(); i++) {
                    // SUW depends on user <-> display assignment and must be updated on
                    // occupant zone changes.
                    mPerDisplaySparseArray.valueAt(i).onUserSetupInProgressChanged();
                }
            }
        };
        mUserHelper.addOccupantZoneChangeListener(mOccupantChangeListener);
    }

    @VisibleForTesting
    protected String getBarPolicyString() {
        return mBarControlPolicy.getSettingValue();
    }

    private boolean isSuwInProgress(int userId) {
        if (userId == INVALID_USER_ID) {
            return false;
        }
        return Settings.Secure.getIntForUser(mContext.getContentResolver(),
                CarSettings.Secure.KEY_SETUP_WIZARD_IN_PROGRESS, 0,
                userId) != 0;
    }

    class PerDisplay implements DisplayInsetsController.OnInsetsChangedListener {
        int mDisplayId;
        InsetsController mInsetsController;
        @InsetsType
        int mRequestedVisibleTypes = WindowInsets.Type.defaultVisible();
        @InsetsType
        int mWindowRequestedVisibleTypes = WindowInsets.Type.defaultVisible();
        @InsetsType
        int mAppRequestedVisibleTypes = WindowInsets.Type.defaultVisible();
        @InsetsType
        int mImmersiveState = systemBars();
        boolean mIsSuwInProgress;
        String mPackageName;

        PerDisplay(int displayId) {
            mDisplayId = displayId;
            InputMethodManager inputMethodManager =
                    mContext.getSystemService(InputMethodManager.class);
            mInsetsController = new InsetsController(
                    new DisplaySystemBarsInsetsControllerHost(mHandler, requestedVisibleTypes -> {
                        mRequestedVisibleTypes = requestedVisibleTypes;
                        updateDisplayWindowRequestedVisibleTypes();
                    }, inputMethodManager)
            );
            mIsSuwInProgress = isSuwInProgress(mUserHelper.getUserIdForDisplay(mDisplayId));
        }

        public void register() {
            mDisplayInsetsController.addInsetsChangedListener(mDisplayId, this);
        }

        public void unregister() {
            mDisplayInsetsController.removeInsetsChangedListener(mDisplayId, this);
        }

        @Override
        public void insetsChanged(InsetsState insetsState) {
            mInsetsController.onStateChanged(insetsState);
            updateDisplayWindowRequestedVisibleTypes();
        }

        @Override
        public void hideInsets(@InsetsType int types, @Nullable ImeTracker.Token statsToken) {
            if ((types & WindowInsets.Type.ime()) == 0) {
                mInsetsController.hide(types, statsToken);
            }
        }

        @Override
        public void showInsets(@InsetsType int types, @Nullable ImeTracker.Token statsToken) {
            if ((types & WindowInsets.Type.ime()) == 0) {
                mInsetsController.show(types, statsToken);
            }
        }

        @Override
        public void insetsControlChanged(InsetsState insetsState,
                InsetsSourceControl[] activeControls) {
            InsetsSourceControl[] nonImeControls = null;
            // Need to filter out IME control to prevent control after leash is released
            if (activeControls != null) {
                nonImeControls = Arrays.stream(activeControls).filter(
                        c -> c.getType() != WindowInsets.Type.ime()).toArray(
                        InsetsSourceControl[]::new);
            }
            mInsetsController.onControlsChanged(nonImeControls);
        }

        @Override
        public void topFocusedWindowChanged(ComponentName component,
                @InsetsType int requestedVisibleTypes) {
            if (DEBUG) {
                Slog.d(TAG, "topFocusedWindowChanged behavior = " + mBehavior
                        + ", component = " + component
                        + ", requestedVisibleTypes = " + requestedVisibleTypes
                        + ", mWindowRequestedVisibleTypes = " + mWindowRequestedVisibleTypes
                        + ", mPackageName = " + mPackageName
                        + ", userId = " + mContext.getUserId()
                        + ", display id = " + mDisplayId
                );
            }
            String packageName = component != null ? component.getPackageName() : null;

            if (mBehavior == SYSTEM_BAR_PERSISTENCY_CONFIG_BARPOLICY) {
                if (Objects.equals(mPackageName, packageName) && (!packageLevelSystemBarVisibility()
                        || mWindowRequestedVisibleTypes == requestedVisibleTypes)) {
                    return;
                }
            } else {
                if (mWindowRequestedVisibleTypes == requestedVisibleTypes) {
                    return;
                }
            }

            updateImmersiveState(requestedVisibleTypes);
            mWindowRequestedVisibleTypes = requestedVisibleTypes;
            mPackageName = packageName;
            updateDisplayWindowRequestedVisibleTypes();
        }

        private void updateImmersiveState(@InsetsType int requestedVisibleTypes) {
            boolean showNavRequest =
                    (requestedVisibleTypes & navigationBars()) == navigationBars();
            boolean showStatusRequest =
                    (requestedVisibleTypes & statusBars()) == statusBars();

            if (mBehavior == SYSTEM_BAR_PERSISTENCY_CONFIG_IMMERSIVE) {
                mImmersiveState = 0;
                if (showNavRequest) {
                    mImmersiveState |= navigationBars();
                }
                if (showStatusRequest) {
                    mImmersiveState |= statusBars();
                }
            } else if (mBehavior == SYSTEM_BAR_PERSISTENCY_CONFIG_IMMERSIVE_WITH_NAV) {
                mImmersiveState = navigationBars();
                if (showStatusRequest) {
                    mImmersiveState |= statusBars();
                }
            } else if (mBehavior == SYSTEM_BAR_PERSISTENCY_CONFIG_NON_IMMERSIVE) {
                mImmersiveState = systemBars();
            }
            Slog.d(TAG, "ImmersiveState =" + mImmersiveState);
        }

        @Override
        public void setImeInputTargetRequestedVisibility(boolean visible,
                @NonNull ImeTracker.Token statsToken) {
            // no-op - IME visibility is handled by the DisplayImeController
        }

        protected void updateDisplayWindowRequestedVisibleTypes() {
            if (mPackageName == null) {
                return;
            }

            int[] barVisibilities = getBarVisibilities(mImmersiveState);

            updateRequestedVisibleTypes(
                    barVisibilities[VISIBLE_BAR_VISIBILITIES_TYPES_INDEX],
                    /* visible= */ true);
            updateRequestedVisibleTypes(
                    barVisibilities[INVISIBLE_BAR_VISIBILITIES_TYPES_INDEX],
                    /* visible= */ false);

            if (mAppRequestedVisibleTypes == mRequestedVisibleTypes) {
                return;
            }
            mAppRequestedVisibleTypes = mRequestedVisibleTypes;

            showInsets(barVisibilities[VISIBLE_BAR_VISIBILITIES_TYPES_INDEX],
                    /* statsToken= */ null);
            hideInsets(barVisibilities[INVISIBLE_BAR_VISIBILITIES_TYPES_INDEX],
                    /* statsToken = */ null);

            int insetMask = barVisibilities[VISIBLE_BAR_VISIBILITIES_TYPES_INDEX]
                    | barVisibilities[INVISIBLE_BAR_VISIBILITIES_TYPES_INDEX];
            try {
                mWmService.updateDisplayWindowRequestedVisibleTypes(mDisplayId,
                        barVisibilities[VISIBLE_BAR_VISIBILITIES_TYPES_INDEX], insetMask,
                        /* imeStatsToken= */ null);
            } catch (RemoteException e) {
                Slog.w(TAG, "Unable to update window manager service.");
            }
        }

        private int[] getBarVisibilities(int immersiveState) {
            int[] barVisibilities;
            if (mIsSuwInProgress && mSuwBehavior != SYSTEM_BAR_SUW_PERSISTENCY_CONFIG_DISABLED) {
                barVisibilities = getBarVisibilitiesForSuw();
            } else if (mBehavior == SYSTEM_BAR_PERSISTENCY_CONFIG_BARPOLICY) {
                BarVisibility barVis = packageLevelSystemBarVisibility()
                        ? mBarControlPolicy.getBarVisibilities(
                        mPackageName, mWindowRequestedVisibleTypes)
                        : mBarControlPolicy.getBarVisibilities(mPackageName);
                barVisibilities =
                        new int[]{barVis.getShowTypes(), barVis.getHideTypes()};
            } else if (immersiveState == STATE_IMMERSIVE_WITH_NAV_BAR) {
                barVisibilities = mImmersiveWithNavBarVisibilities;
            } else if (immersiveState == STATE_IMMERSIVE_WITH_STATUS_BAR) {
                barVisibilities = mImmersiveWithStatusBarVisibilities;
            } else if (immersiveState == STATE_IMMERSIVE) {
                barVisibilities = mImmersiveVisibilities;
            } else if (immersiveState == STATE_NON_IMMERSIVE) {
                barVisibilities = mDefaultVisibilities;
            } else {
                barVisibilities = mDefaultVisibilities;
            }
            if (DEBUG) {
                Slog.d(TAG, "mBehavior=" + mBehavior + ", mImmersiveState = " + immersiveState
                        + ", mIsSuwInProgress = " + mIsSuwInProgress
                        + ", mSuwBehavior = " + mSuwBehavior
                        + ", mDisplayId = " + mDisplayId
                        + ", barVisibilities to " + Arrays.toString(barVisibilities));
            }
            return barVisibilities;
        }

        private int[] getBarVisibilitiesForSuw() {
            if (mSuwBehavior == SYSTEM_BAR_SUW_PERSISTENCY_CONFIG_IMMERSIVE) {
                return mImmersiveVisibilities;
            } else if (mSuwBehavior == SYSTEM_BAR_SUW_PERSISTENCY_CONFIG_IMMERSIVE_WITH_NAV) {
                return mImmersiveWithNavBarVisibilities;
            } else if (mSuwBehavior == SYSTEM_BAR_SUW_PERSISTENCY_CONFIG_IMMERSIVE_WITH_STATUS) {
                return mImmersiveWithStatusBarVisibilities;
            } else {
                Slog.e(TAG, "Invalid SUW visibility config " + mSuwBehavior
                        + " - using default visibility");
                return mDefaultVisibilities;
            }
        }

        void onUserSetupInProgressChanged() {
            boolean inProgress = isSuwInProgress(mUserHelper.getUserIdForDisplay(mDisplayId));
            if (inProgress == mIsSuwInProgress) {
                return;
            }
            mIsSuwInProgress = inProgress;
            updateDisplayWindowRequestedVisibleTypes();
        }

        protected void updateRequestedVisibleTypes(@InsetsType int types, boolean visible) {
            mRequestedVisibleTypes = visible
                    ? (mRequestedVisibleTypes | types)
                    : (mRequestedVisibleTypes & ~types);
        }
    }
}
