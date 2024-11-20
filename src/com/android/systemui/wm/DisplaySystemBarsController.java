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

import static android.content.Intent.ACTION_OVERLAY_CHANGED;
import static android.view.WindowInsets.Type.navigationBars;
import static android.view.WindowInsets.Type.statusBars;
import static android.view.WindowInsets.Type.systemBars;

import static com.android.systemui.car.systembar.SystemBarUtil.SYSTEM_BAR_PERSISTENCY_CONFIG_BARPOLICY;
import static com.android.systemui.car.systembar.SystemBarUtil.SYSTEM_BAR_PERSISTENCY_CONFIG_IMMERSIVE;
import static com.android.systemui.car.systembar.SystemBarUtil.SYSTEM_BAR_PERSISTENCY_CONFIG_IMMERSIVE_WITH_NAV;
import static com.android.systemui.car.systembar.SystemBarUtil.SYSTEM_BAR_PERSISTENCY_CONFIG_NON_IMMERSIVE;
import static com.android.systemui.car.systembar.SystemBarUtil.VISIBLE_BAR_VISIBILITIES_TYPES_INDEX;
import static com.android.systemui.car.systembar.SystemBarUtil.INVISIBLE_BAR_VISIBILITIES_TYPES_INDEX;
import static com.android.systemui.car.users.CarSystemUIUserUtil.isSecondaryMUMDSystemUI;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Handler;
import android.os.PatternMatcher;
import android.os.RemoteException;
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
import com.android.systemui.dagger.qualifiers.Main;
import com.android.wm.shell.common.DisplayController;
import com.android.wm.shell.common.DisplayInsetsController;

import java.util.Arrays;
import java.util.Objects;

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

    @VisibleForTesting
    SparseArray<PerDisplay> mPerDisplaySparseArray;
    @InsetsType
    private int mWindowRequestedVisibleTypes = WindowInsets.Type.defaultVisible();
    @InsetsType
    private int mAppRequestedVisibleTypes = WindowInsets.Type.defaultVisible();
    @InsetsType
    private int mImmersiveState = systemBars();

    public DisplaySystemBarsController(
            Context context,
            IWindowManager wmService,
            DisplayController displayController,
            DisplayInsetsController displayInsetsController,
            @Main Handler mainHandler) {
        mContext = context;
        mWmService = wmService;
        mDisplayInsetsController = displayInsetsController;
        mHandler = mainHandler;
        if (!isSecondaryMUMDSystemUI()) {
            // This WM controller should only be initialized once for the primary SystemUI, as it
            // will affect insets on all displays.
            // TODO(b/262773276): support per-user remote inset controllers
            displayController.addDisplayWindowListener(this);
        }
    }

    @Override
    public void onDisplayAdded(int displayId) {
        PerDisplay pd = new PerDisplay(displayId);
        pd.register();
        // Lazy loading policy control filters instead of during boot.
        if (mPerDisplaySparseArray == null) {
            mPerDisplaySparseArray = new SparseArray<>();
            BarControlPolicy.reloadFromSetting(mContext);
            BarControlPolicy.registerContentObserver(mContext, mHandler, () -> {
                int size = mPerDisplaySparseArray.size();
                for (int i = 0; i < size; i++) {
                    mPerDisplaySparseArray.valueAt(i).updateDisplayWindowRequestedVisibleTypes();
                }
            });
        }
        mPerDisplaySparseArray.put(displayId, pd);
    }

    @Override
    public void onDisplayRemoved(int displayId) {
        PerDisplay pd = mPerDisplaySparseArray.get(displayId);
        pd.unregister();
        mPerDisplaySparseArray.remove(displayId);
    }

    class PerDisplay implements DisplayInsetsController.OnInsetsChangedListener {
        private static final String OVERLAY_FILTER_DATA_SCHEME = "package";

        int mDisplayId;
        InsetsController mInsetsController;
        @InsetsType
        int mRequestedVisibleTypes = WindowInsets.Type.defaultVisible();
        String mPackageName;
        int mBehavior = 0;
        BroadcastReceiver mOverlayChangeBroadcastReceiver;

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
            mBehavior = mContext.getResources().getInteger(
                    R.integer.config_systemBarPersistency);
        }

        public void register() {
            mDisplayInsetsController.addInsetsChangedListener(mDisplayId, this);
            registerOverlayChangeBroadcastReceiver();
        }

        public void unregister() {
            mDisplayInsetsController.removeInsetsChangedListener(mDisplayId, this);
            unregisterOverlayChangeBroadcastReceiver();
        }

        @Override
        public void insetsChanged(InsetsState insetsState) {
            mInsetsController.onStateChanged(insetsState);
            updateDisplayWindowRequestedVisibleTypes();
        }

        @Override
        public void hideInsets(@InsetsType int types, boolean fromIme,
                @Nullable ImeTracker.Token statsToken) {
            if ((types & WindowInsets.Type.ime()) == 0) {
                mInsetsController.hide(types, /* fromIme = */ false, statsToken);
            }
        }

        @Override
        public void showInsets(@InsetsType int types, boolean fromIme,
                @Nullable ImeTracker.Token statsToken) {
            if ((types & WindowInsets.Type.ime()) == 0) {
                mInsetsController.show(types, /* fromIme= */ false, statsToken);
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
                if (Objects.equals(mPackageName, packageName)) {
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
            // TODO
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
                    Slog.d(TAG, "Update system bar persistency behavior to" + mBehavior
                            + " on overlay change on userId = " + mContext.getUserId()
                            + " on display = " + mDisplayId);
                }
            };
            mContext.registerReceiver(mOverlayChangeBroadcastReceiver,
                    overlayFilter, /* broadcastPermission= */ null, /* handler= */ null);
        }

        private void unregisterOverlayChangeBroadcastReceiver() {
            if (mOverlayChangeBroadcastReceiver != null) {
                mContext.unregisterReceiver(mOverlayChangeBroadcastReceiver);
            }
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
                    /* fromIme= */ false, /* statsToken= */ null);
            hideInsets(barVisibilities[INVISIBLE_BAR_VISIBILITIES_TYPES_INDEX],
                    /* fromIme= */ false, /* statsToken = */ null);

            int insetMask = barVisibilities[VISIBLE_BAR_VISIBILITIES_TYPES_INDEX]
                    | barVisibilities[INVISIBLE_BAR_VISIBILITIES_TYPES_INDEX];
            try {
                mWmService.updateDisplayWindowRequestedVisibleTypes(mDisplayId,
                        barVisibilities[VISIBLE_BAR_VISIBILITIES_TYPES_INDEX], insetMask,
                        null /* TODO(b/380891919) pass IME statsToken */);
            } catch (RemoteException e) {
                Slog.w(TAG, "Unable to update window manager service.");
            }
        }

        private int[] getBarVisibilities(int immersiveState) {
            int[] barVisibilities;
            if (mBehavior == SYSTEM_BAR_PERSISTENCY_CONFIG_BARPOLICY) {
                barVisibilities = BarControlPolicy.getBarVisibilities(mPackageName);
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
                        + ", barVisibilities to " + Arrays.toString(barVisibilities));
            }
            return barVisibilities;
        }

        protected void updateRequestedVisibleTypes(@InsetsType int types, boolean visible) {
            mRequestedVisibleTypes = visible
                    ? (mRequestedVisibleTypes | types)
                    : (mRequestedVisibleTypes & ~types);
        }
    }
}
