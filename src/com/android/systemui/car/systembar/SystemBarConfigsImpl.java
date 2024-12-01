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
package com.android.systemui.car.systembar;

import static android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;

import static com.android.car.dockutil.Flags.dockFeature;
import static com.android.systemui.car.Flags.displayCompatibilityV2;
import static com.android.systemui.car.systembar.CarSystemBarController.BOTTOM;
import static com.android.systemui.car.systembar.CarSystemBarController.LEFT;
import static com.android.systemui.car.systembar.CarSystemBarController.RIGHT;
import static com.android.systemui.car.systembar.CarSystemBarController.TOP;

import android.annotation.IdRes;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.PixelFormat;
import android.os.Binder;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.Log;
import android.view.Gravity;
import android.view.InsetsFrameProvider;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowManager;

import com.android.car.dockutil.Flags;
import com.android.internal.annotations.VisibleForTesting;
import com.android.systemui.R;
import com.android.systemui.car.notification.BottomNotificationPanelViewMediator;
import com.android.systemui.car.notification.TopNotificationPanelViewMediator;
import com.android.systemui.car.systembar.CarSystemBarController.SystemBarSide;
import com.android.systemui.dagger.qualifiers.Main;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;

/**
 * Reads configs for system bars for each side (TOP, BOTTOM, LEFT, and RIGHT) and returns the
 * corresponding {@link android.view.WindowManager.LayoutParams} per the configuration.
 */
public class SystemBarConfigsImpl implements SystemBarConfigs {

    private static final String TAG = SystemBarConfigs.class.getSimpleName();
    private static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);

    // The z-order from which system bars will start to appear on top of HUN's.
    @VisibleForTesting
    static final int HUN_Z_ORDER = 10;

    private static final Binder INSETS_OWNER = new Binder();

    /*
        NOTE: The elements' order in the map below must be preserved as-is since the correct
        corresponding values are obtained by the index.
     */
    private static final InsetsFrameProvider[] BAR_PROVIDER_MAP = {
            new InsetsFrameProvider(
                    INSETS_OWNER, 0 /* index */, WindowInsets.Type.statusBars()),
            new InsetsFrameProvider(
                    INSETS_OWNER, 0 /* index */, WindowInsets.Type.navigationBars()),
            new InsetsFrameProvider(
                    INSETS_OWNER, 1 /* index */, WindowInsets.Type.statusBars()),
            new InsetsFrameProvider(
                    INSETS_OWNER, 1 /* index */, WindowInsets.Type.navigationBars()),
    };

    private static final Map<@SystemBarSide Integer, Integer> BAR_GRAVITY_MAP = new ArrayMap<>();
    private static final Map<@SystemBarSide Integer, String> BAR_TITLE_MAP = new ArrayMap<>();
    private static final Map<@SystemBarSide Integer, InsetsFrameProvider> BAR_GESTURE_MAP =
            new ArrayMap<>();

    private final Context mContext;
    private final Resources mResources;
    private final Map<@SystemBarSide Integer, SystemBarConfig> mSystemBarConfigMap =
            new ArrayMap<>();
    private final List<@SystemBarSide Integer> mSystemBarSidesByZOrder = new ArrayList<>();
    /** Maps @WindowManager.LayoutParams.WindowType to window contexts for that type. */
    private final Map<Integer, Context> mWindowContexts = new ArrayMap<>();

    private boolean mTopNavBarEnabled;
    private boolean mBottomNavBarEnabled;
    private boolean mLeftNavBarEnabled;
    private boolean mRightNavBarEnabled;
    private int mDisplayCompatToolbarState = 0;

    @Inject
    public SystemBarConfigsImpl(Context context, @Main Resources resources) {
        mContext = context;
        mResources = resources;
        init();
    }

    private void init() {
        populateMaps();
        readConfigs();

        checkOnlyOneDisplayCompatIsEnabled();
        checkEnabledBarsHaveUniqueBarTypes();
        checkAllOverlappingBarsHaveDifferentZOrders();
        checkSystemBarEnabledForNotificationPanel();
        checkHideBottomBarForKeyboardConfigSync();

        setInsetPaddingsForOverlappingCorners();
        sortSystemBarTypesByZOrder();
    }

    /**
     * Invalidate cached resources and fetch from resources config file.
     * TODO: b/260206944, Can remove this after we have a fix for overlaid resources not applied.
     * <p>
     * Since SystemBarConfig is a Scoped(Dagger Singleton Annotation), We will have stale values, of
     * all the resources after the RRO is applied.
     * Another way is to remove the Scope(Singleton), but the downside is that it will be re-created
     * everytime.
     * </p>
     */
    @Override
    public void resetSystemBarConfigs() {
        init();
    }

    @Override
    public Context getWindowContextBySide(@SystemBarSide int side) {
        SystemBarConfig config = mSystemBarConfigMap.get(side);
        if (config == null) {
            return null;
        }
        int windowType = config.mapZOrderToBarType(config.getZOrder());
        if (mWindowContexts.containsKey(windowType)) {
            return mWindowContexts.get(windowType);
        }
        Context context = mContext.createWindowContext(windowType, /* options= */ null);
        mWindowContexts.put(windowType, context);
        return context;
    }

    /**
     * Returns the system bar layout for the given side. {@code null} if side is unknown.
     */
    @Override
    public ViewGroup getSystemBarLayoutBySide(@SystemBarSide int side, boolean isSetUp) {
        int layoutId = getSystemBarLayoutResBySide(side, isSetUp);
        if (layoutId == 0) {
            return null;
        }

        return (ViewGroup) View.inflate(getWindowContextBySide(side), layoutId, /* root= */ null);
    }

    private int getSystemBarLayoutResBySide(@SystemBarSide int side, boolean isSetUp) {
        switch (side) {
            case LEFT:
                if (!isSetUp) {
                    return R.layout.car_left_system_bar_unprovisioned;
                } else {
                    return R.layout.car_left_system_bar;
                }
            case TOP:
                if (!isSetUp) {
                    return R.layout.car_top_system_bar_unprovisioned;
                } else if (Flags.dockFeature()) {
                    return R.layout.car_top_system_bar_dock;
                } else {
                    return R.layout.car_top_system_bar;
                }
            case RIGHT:
                if (!isSetUp) {
                    return R.layout.car_right_system_bar_unprovisioned;
                } else {
                    return R.layout.car_right_system_bar;
                }
            case BOTTOM:
                if (!isSetUp) {
                    return R.layout.car_bottom_system_bar_unprovisioned;
                } else if (Flags.dockFeature()) {
                    return R.layout.car_bottom_system_bar_dock;
                } else {
                    return R.layout.car_bottom_system_bar;
                }
            default:
                return 0;
        }
    }

    /**
     * Returns the system bar window for the given side.
     */
    @Override
    public ViewGroup getWindowLayoutBySide(@SystemBarSide int side) {
        int windowId = getWindowIdBySide(side);
        if (windowId == 0) {
            return null;
        }
        ViewGroup window = (ViewGroup) View.inflate(getWindowContextBySide(side),
                R.layout.navigation_bar_window, /* root= */ null);
        // Setting a new id to each window because we're inflating the same layout and that layout
        // already has an id. and we don't want to have the same id on all the system bar windows.
        window.setId(windowId);
        return window;
    }

    /**
     * Returns an id for the given side that can be set on the system bar window.
     * 0 means the side is unknown.
     */
    @IdRes
    private int getWindowIdBySide(@SystemBarSide int side) {
        return switch (side) {
            case TOP -> R.id.car_top_bar_window;
            case BOTTOM -> R.id.car_bottom_bar_window;
            case LEFT -> R.id.car_left_bar_window;
            case RIGHT -> R.id.car_right_bar_window;
            default -> 0;
        };
    }

    @Override
    public WindowManager.LayoutParams getLayoutParamsBySide(@SystemBarSide int side) {
        return mSystemBarConfigMap.get(side) != null
                ? mSystemBarConfigMap
                .get(side).getLayoutParams()
                : null;
    }

    @Override
    public boolean getEnabledStatusBySide(@SystemBarSide int side) {
        switch (side) {
            case TOP:
                return mTopNavBarEnabled;
            case BOTTOM:
                return mBottomNavBarEnabled;
            case LEFT:
                return mLeftNavBarEnabled || isLeftDisplayCompatToolbarEnabled();
            case RIGHT:
                return mRightNavBarEnabled || isRightDisplayCompatToolbarEnabled();
            default:
                return false;
        }
    }

    @Override
    public boolean getHideForKeyboardBySide(@SystemBarSide int side) {
        return mSystemBarConfigMap.get(side) != null
                && mSystemBarConfigMap.get(side).getHideForKeyboard();
    }

    @Override
    public void insetSystemBar(@SystemBarSide int side, ViewGroup view) {
        if (mSystemBarConfigMap.get(side) == null) return;

        int[] paddings = mSystemBarConfigMap.get(side).getPaddings();
        if (DEBUG) {
            Log.d(TAG, "Set padding to side = " + side + ", to " + Arrays.toString(paddings));
        }
        view.setPadding(paddings[LEFT], paddings[TOP], paddings[RIGHT], paddings[BOTTOM]);
    }

    @Override
    public List<@SystemBarSide Integer> getSystemBarSidesByZOrder() {
        return mSystemBarSidesByZOrder;
    }

    @Override
    public int getSystemBarInsetTypeBySide(@SystemBarSide int side) {
        return mSystemBarConfigMap.get(side) != null
                ? mSystemBarConfigMap.get(side).getBarType() : -1;
    }

    @Override
    public InsetsFrameProvider getInsetsFrameProvider(int index) {
        return BAR_PROVIDER_MAP[index];
    }

    @VisibleForTesting
    void updateInsetPaddings(@SystemBarSide int side,
            Map<@SystemBarSide Integer, Boolean> barVisibilities) {
        SystemBarConfig currentConfig = mSystemBarConfigMap.get(side);

        if (currentConfig == null) return;

        int defaultLeftPadding = 0;
        int defaultRightPadding = 0;
        int defaultTopPadding = 0;
        int defaultBottomPadding = 0;

        switch (side) {
            case LEFT: {
                defaultLeftPadding = mResources
                        .getDimensionPixelSize(R.dimen.car_left_system_bar_left_padding);
                defaultRightPadding = mResources
                        .getDimensionPixelSize(R.dimen.car_left_system_bar_right_padding);
                defaultTopPadding = mResources
                        .getDimensionPixelSize(R.dimen.car_left_system_bar_top_padding);
                defaultBottomPadding = mResources
                        .getDimensionPixelSize(R.dimen.car_left_system_bar_bottom_padding);
                break;
            }
            case RIGHT: {
                defaultLeftPadding = mResources
                        .getDimensionPixelSize(R.dimen.car_right_system_bar_left_padding);
                defaultRightPadding = mResources
                        .getDimensionPixelSize(R.dimen.car_right_system_bar_right_padding);
                defaultTopPadding = mResources
                        .getDimensionPixelSize(R.dimen.car_right_system_bar_top_padding);
                defaultBottomPadding = mResources
                        .getDimensionPixelSize(R.dimen.car_right_system_bar_bottom_padding);
                break;
            }
            case TOP: {
                defaultLeftPadding = mResources
                        .getDimensionPixelSize(R.dimen.car_top_system_bar_left_padding);
                defaultRightPadding = mResources
                        .getDimensionPixelSize(R.dimen.car_top_system_bar_right_padding);
                defaultTopPadding = mResources
                        .getDimensionPixelSize(R.dimen.car_top_system_bar_top_padding);
                defaultBottomPadding = mResources
                        .getDimensionPixelSize(R.dimen.car_top_system_bar_bottom_padding);
                break;
            }
            case BOTTOM: {
                defaultLeftPadding = mResources
                        .getDimensionPixelSize(R.dimen.car_bottom_system_bar_left_padding);
                defaultRightPadding = mResources
                        .getDimensionPixelSize(R.dimen.car_bottom_system_bar_right_padding);
                defaultTopPadding = mResources
                        .getDimensionPixelSize(R.dimen.car_bottom_system_bar_top_padding);
                defaultBottomPadding = mResources
                        .getDimensionPixelSize(R.dimen.car_bottom_system_bar_bottom_padding);
                break;
            }
            default:
        }

        currentConfig.setPaddingBySide(LEFT, defaultLeftPadding);
        currentConfig.setPaddingBySide(RIGHT, defaultRightPadding);
        currentConfig.setPaddingBySide(TOP, defaultTopPadding);
        currentConfig.setPaddingBySide(BOTTOM, defaultBottomPadding);

        if (isHorizontalBar(side)) {
            if (mLeftNavBarEnabled && currentConfig.getZOrder() < mSystemBarConfigMap.get(
                    LEFT).getZOrder()) {
                currentConfig.setPaddingBySide(LEFT,
                        barVisibilities.get(LEFT)
                                ? mSystemBarConfigMap.get(LEFT).getGirth()
                                : defaultLeftPadding);
            }
            if (mRightNavBarEnabled && currentConfig.getZOrder() < mSystemBarConfigMap.get(
                    RIGHT).getZOrder()) {
                currentConfig.setPaddingBySide(RIGHT,
                        barVisibilities.get(RIGHT)
                                ? mSystemBarConfigMap.get(RIGHT).getGirth()
                                : defaultRightPadding);
            }
        }
        if (isVerticalBar(side)) {
            if (mTopNavBarEnabled && currentConfig.getZOrder() < mSystemBarConfigMap.get(
                    TOP).getZOrder()) {
                currentConfig.setPaddingBySide(TOP,
                        barVisibilities.get(TOP)
                                ? mSystemBarConfigMap.get(TOP).getGirth()
                                : defaultTopPadding);
            }
            if (mBottomNavBarEnabled && currentConfig.getZOrder() < mSystemBarConfigMap.get(
                    BOTTOM).getZOrder()) {
                currentConfig.setPaddingBySide(BOTTOM,
                        barVisibilities.get(BOTTOM)
                                ? mSystemBarConfigMap.get(BOTTOM).getGirth()
                                : defaultBottomPadding);
            }

        }
        if (DEBUG) {
            Log.d(TAG, "Update padding for side = " + side + " to "
                    + Arrays.toString(currentConfig.getPaddings()));
        }
    }

    @SuppressLint("RtlHardcoded")
    private static void populateMaps() {
        BAR_GRAVITY_MAP.put(TOP, Gravity.TOP);
        BAR_GRAVITY_MAP.put(BOTTOM, Gravity.BOTTOM);
        BAR_GRAVITY_MAP.put(LEFT, Gravity.LEFT);
        BAR_GRAVITY_MAP.put(RIGHT, Gravity.RIGHT);

        BAR_TITLE_MAP.put(TOP, "TopCarSystemBar");
        BAR_TITLE_MAP.put(BOTTOM, "BottomCarSystemBar");
        BAR_TITLE_MAP.put(LEFT, "LeftCarSystemBar");
        BAR_TITLE_MAP.put(RIGHT, "RightCarSystemBar");

        BAR_GESTURE_MAP.put(TOP, new InsetsFrameProvider(
                INSETS_OWNER, 0 /* index */, WindowInsets.Type.mandatorySystemGestures()));
        BAR_GESTURE_MAP.put(BOTTOM, new InsetsFrameProvider(
                INSETS_OWNER, 1 /* index */, WindowInsets.Type.mandatorySystemGestures()));
        BAR_GESTURE_MAP.put(LEFT, new InsetsFrameProvider(
                INSETS_OWNER, 2 /* index */, WindowInsets.Type.mandatorySystemGestures()));
        BAR_GESTURE_MAP.put(RIGHT, new InsetsFrameProvider(
                INSETS_OWNER, 3 /* index */, WindowInsets.Type.mandatorySystemGestures()));
    }

    private void readConfigs() {
        mTopNavBarEnabled = mResources.getBoolean(R.bool.config_enableTopSystemBar);
        mBottomNavBarEnabled = mResources.getBoolean(R.bool.config_enableBottomSystemBar);
        mLeftNavBarEnabled = mResources.getBoolean(R.bool.config_enableLeftSystemBar);
        mRightNavBarEnabled = mResources.getBoolean(R.bool.config_enableRightSystemBar);
        mDisplayCompatToolbarState =
            mResources.getInteger(R.integer.config_showDisplayCompatToolbarOnSystemBar);
        mSystemBarConfigMap.clear();

        if ((mLeftNavBarEnabled && isLeftDisplayCompatToolbarEnabled())
                || (mRightNavBarEnabled && isRightDisplayCompatToolbarEnabled())) {
            throw new IllegalStateException(
                "Navigation Bar and Display Compat toolbar can't be "
                    + "on the same side");
        }

        if (mTopNavBarEnabled) {
            SystemBarConfig topBarConfig =
                    new SystemBarConfigBuilder()
                            .setSide(TOP)
                            .setGirth(mResources.getDimensionPixelSize(
                                    R.dimen.car_top_system_bar_height))
                            .setBarType(
                                    mResources.getInteger(R.integer.config_topSystemBarType))
                            .setZOrder(
                                    mResources.getInteger(R.integer.config_topSystemBarZOrder))
                            .setHideForKeyboard(mResources.getBoolean(
                                    R.bool.config_hideTopSystemBarForKeyboard))
                            .build();
            mSystemBarConfigMap.put(TOP, topBarConfig);
        }

        if (mBottomNavBarEnabled) {
            SystemBarConfig bottomBarConfig =
                    new SystemBarConfigBuilder()
                            .setSide(BOTTOM)
                            .setGirth(mResources.getDimensionPixelSize(
                                    R.dimen.car_bottom_system_bar_height))
                            .setBarType(
                                    mResources.getInteger(R.integer.config_bottomSystemBarType))
                            .setZOrder(
                                    mResources.getInteger(
                                            R.integer.config_bottomSystemBarZOrder))
                            .setHideForKeyboard(mResources.getBoolean(
                                    R.bool.config_hideBottomSystemBarForKeyboard))
                            .build();
            mSystemBarConfigMap.put(BOTTOM, bottomBarConfig);
        }

        if (mLeftNavBarEnabled || isLeftDisplayCompatToolbarEnabled()) {
            SystemBarConfig leftBarConfig =
                    new SystemBarConfigBuilder()
                            .setSide(LEFT)
                            .setGirth(mResources.getDimensionPixelSize(
                                    R.dimen.car_left_system_bar_width))
                            .setBarType(
                                    mResources.getInteger(R.integer.config_leftSystemBarType))
                            .setZOrder(
                                    mResources.getInteger(R.integer.config_leftSystemBarZOrder))
                            .setHideForKeyboard(mResources.getBoolean(
                                    R.bool.config_hideLeftSystemBarForKeyboard))
                            .build();
            mSystemBarConfigMap.put(LEFT, leftBarConfig);
        }

        if (mRightNavBarEnabled || isRightDisplayCompatToolbarEnabled()) {
            SystemBarConfig rightBarConfig =
                    new SystemBarConfigBuilder()
                            .setSide(RIGHT)
                            .setGirth(mResources.getDimensionPixelSize(
                                    R.dimen.car_right_system_bar_width))
                            .setBarType(
                                    mResources.getInteger(R.integer.config_rightSystemBarType))
                            .setZOrder(mResources.getInteger(
                                    R.integer.config_rightSystemBarZOrder))
                            .setHideForKeyboard(mResources.getBoolean(
                                    R.bool.config_hideRightSystemBarForKeyboard))
                            .build();
            mSystemBarConfigMap.put(RIGHT, rightBarConfig);
        }
    }

    private void checkOnlyOneDisplayCompatIsEnabled() throws IllegalStateException {
        boolean useRemoteLaunchTaskView =
                mResources.getBoolean(R.bool.config_useRemoteLaunchTaskView);
        int displayCompatEnabled =
                mResources.getInteger(R.integer.config_showDisplayCompatToolbarOnSystemBar);
        if (useRemoteLaunchTaskView && displayCompatEnabled != 0) {
            throw new IllegalStateException("config_useRemoteLaunchTaskView is enabled but "
                    + "config_showDisplayCompatToolbarOnSystemBar is non-zero");
        }
    }

    private void checkEnabledBarsHaveUniqueBarTypes() throws RuntimeException {
        Set<Integer> barTypesUsed = new ArraySet<>();
        int enabledNavBarCount = mSystemBarConfigMap.size();

        for (SystemBarConfig systemBarConfig : mSystemBarConfigMap.values()) {
            barTypesUsed.add(systemBarConfig.getBarType());
        }

        // The number of bar types used cannot be fewer than that of enabled system bars.
        if (barTypesUsed.size() < enabledNavBarCount) {
            throw new RuntimeException("Each enabled system bar must have a unique bar type. Check "
                    + "the configuration in config.xml");
        }
    }

    private void checkAllOverlappingBarsHaveDifferentZOrders() {
        checkOverlappingBarsHaveDifferentZOrders(TOP, LEFT);
        checkOverlappingBarsHaveDifferentZOrders(TOP, RIGHT);
        checkOverlappingBarsHaveDifferentZOrders(BOTTOM, LEFT);
        checkOverlappingBarsHaveDifferentZOrders(BOTTOM, RIGHT);
    }

    private void checkSystemBarEnabledForNotificationPanel() throws RuntimeException {
        String notificationPanelMediatorName =
                mResources.getString(R.string.config_notificationPanelViewMediator);
        if (notificationPanelMediatorName == null) {
            return;
        }

        Class<?> notificationPanelMediatorUsed = null;
        try {
            notificationPanelMediatorUsed = Class.forName(notificationPanelMediatorName);
        } catch (ClassNotFoundException e) {
            Log.e(TAG, "notification panel mediator class not found", e);
        }

        if (!mTopNavBarEnabled && TopNotificationPanelViewMediator.class.isAssignableFrom(
                notificationPanelMediatorUsed)) {
            throw new RuntimeException(
                    "Top System Bar must be enabled to use " + notificationPanelMediatorName);
        }

        if (!mBottomNavBarEnabled && BottomNotificationPanelViewMediator.class.isAssignableFrom(
                notificationPanelMediatorUsed)) {
            throw new RuntimeException("Bottom System Bar must be enabled to use "
                    + notificationPanelMediatorName);
        }
    }

    private void checkHideBottomBarForKeyboardConfigSync() throws RuntimeException {
        if (mBottomNavBarEnabled) {
            boolean actual = mResources.getBoolean(R.bool.config_hideBottomSystemBarForKeyboard);
            boolean expected = mResources.getBoolean(
                    com.android.internal.R.bool.config_hideNavBarForKeyboard);

            if (actual != expected) {
                throw new RuntimeException("config_hideBottomSystemBarForKeyboard must not be "
                        + "overlaid directly and should always refer to"
                        + "config_hideNavBarForKeyboard. However, their values "
                        + "currently do not sync. Set config_hideBottomSystemBarForKeyguard to "
                        + "@*android:bool/config_hideNavBarForKeyboard. To change its "
                        + "value, overlay config_hideNavBarForKeyboard in "
                        + "framework/base/core/res/res.");
            }
        }
    }

    private void setInsetPaddingsForOverlappingCorners() {
        Map<@SystemBarSide Integer, Boolean> systemBarVisibilityOnInit =
                getSystemBarsVisibilityOnInit();
        updateInsetPaddings(TOP, systemBarVisibilityOnInit);
        updateInsetPaddings(BOTTOM, systemBarVisibilityOnInit);
        updateInsetPaddings(LEFT, systemBarVisibilityOnInit);
        updateInsetPaddings(RIGHT, systemBarVisibilityOnInit);
    }

    private void sortSystemBarTypesByZOrder() {
        List<SystemBarConfig> systemBarsByZOrder = new ArrayList<>(mSystemBarConfigMap.values());

        systemBarsByZOrder.sort(new Comparator<SystemBarConfig>() {
            @Override
            public int compare(SystemBarConfig o1, SystemBarConfig o2) {
                return o1.getZOrder() - o2.getZOrder();
            }
        });

        mSystemBarSidesByZOrder.clear();
        systemBarsByZOrder.forEach(systemBarConfig -> {
            mSystemBarSidesByZOrder.add(systemBarConfig.getSide());
        });
    }

    // On init, system bars are visible as long as they are enabled.
    private Map<@SystemBarSide Integer, Boolean> getSystemBarsVisibilityOnInit() {
        ArrayMap<@SystemBarSide Integer, Boolean> visibilityMap = new ArrayMap<>();
        visibilityMap.put(TOP, mTopNavBarEnabled);
        visibilityMap.put(BOTTOM, mBottomNavBarEnabled);
        visibilityMap.put(LEFT, mLeftNavBarEnabled || isLeftDisplayCompatToolbarEnabled());
        visibilityMap.put(RIGHT, mRightNavBarEnabled || isRightDisplayCompatToolbarEnabled());
        return visibilityMap;
    }

    private void checkOverlappingBarsHaveDifferentZOrders(@SystemBarSide int horizontalSide,
            @SystemBarSide int verticalSide) {

        if (isVerticalBar(horizontalSide) || isHorizontalBar(verticalSide)) {
            Log.w(TAG, "configureBarPaddings: Returning immediately since the horizontal and "
                    + "vertical sides were not provided correctly.");
            return;
        }

        SystemBarConfig horizontalBarConfig = mSystemBarConfigMap.get(horizontalSide);
        SystemBarConfig verticalBarConfig = mSystemBarConfigMap.get(verticalSide);

        if (verticalBarConfig != null && horizontalBarConfig != null) {
            int horizontalBarZOrder = horizontalBarConfig.getZOrder();
            int verticalBarZOrder = verticalBarConfig.getZOrder();

            if (horizontalBarZOrder == verticalBarZOrder) {
                throw new RuntimeException(
                        BAR_TITLE_MAP.get(horizontalSide) + " " + BAR_TITLE_MAP.get(verticalSide)
                                + " have the same Z-Order, and so their placing order cannot be "
                                + "determined. Determine which bar should be placed on top of the "
                                + "other bar and change the Z-order in config.xml accordingly."
                );
            }
        }
    }

    private static boolean isHorizontalBar(@SystemBarSide int side) {
        return side == TOP || side == BOTTOM;
    }

    private static boolean isVerticalBar(@SystemBarSide int side) {
        return side == LEFT || side == RIGHT;
    }

    @Override
    public boolean isLeftDisplayCompatToolbarEnabled() {
        return displayCompatibilityV2() && mDisplayCompatToolbarState == 1;
    }

    @Override
    public boolean isRightDisplayCompatToolbarEnabled() {
        return displayCompatibilityV2() && mDisplayCompatToolbarState == 2;
    }

    private static final class SystemBarConfig {
        private final int mSide;
        private final int mBarType;
        private final int mGirth;
        private final int mZOrder;
        private final boolean mHideForKeyboard;

        private int[] mPaddings = new int[]{0, 0, 0, 0};

        private SystemBarConfig(@SystemBarSide int side, int barType, int girth, int zOrder,
                boolean hideForKeyboard) {
            mSide = side;
            mBarType = barType;
            mGirth = girth;
            mZOrder = zOrder;
            mHideForKeyboard = hideForKeyboard;
        }

        private int getSide() {
            return mSide;
        }

        private int getBarType() {
            return mBarType;
        }

        private int getGirth() {
            return mGirth;
        }

        private int getZOrder() {
            return mZOrder;
        }

        private boolean getHideForKeyboard() {
            return mHideForKeyboard;
        }

        private int[] getPaddings() {
            return mPaddings;
        }

        private WindowManager.LayoutParams getLayoutParams() {
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                    isHorizontalBar(mSide) ? ViewGroup.LayoutParams.MATCH_PARENT : mGirth,
                    isHorizontalBar(mSide) ? mGirth : ViewGroup.LayoutParams.MATCH_PARENT,
                    mapZOrderToBarType(mZOrder),
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                            | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                            | WindowManager.LayoutParams.FLAG_SPLIT_TOUCH,
                    PixelFormat.TRANSLUCENT);
            lp.setTitle(BAR_TITLE_MAP.get(mSide));
            lp.providedInsets = new InsetsFrameProvider[] {
                    BAR_PROVIDER_MAP[mBarType],
                    BAR_GESTURE_MAP.get(mSide)
            };
            lp.setFitInsetsTypes(0);
            lp.windowAnimations = 0;
            lp.gravity = BAR_GRAVITY_MAP.get(mSide);
            lp.layoutInDisplayCutoutMode = LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
            if (dockFeature()) {
                lp.privateFlags = lp.privateFlags
                        | WindowManager.LayoutParams.PRIVATE_FLAG_INTERCEPT_GLOBAL_DRAG_AND_DROP;
            }
            return lp;
        }

        private int mapZOrderToBarType(int zOrder) {
            return zOrder >= HUN_Z_ORDER ? WindowManager.LayoutParams.TYPE_NAVIGATION_BAR_PANEL
                    : WindowManager.LayoutParams.TYPE_STATUS_BAR_ADDITIONAL;
        }

        private void setPaddingBySide(@SystemBarSide int side, int padding) {
            mPaddings[side] = padding;
        }
    }

    private static final class SystemBarConfigBuilder {
        private int mSide;
        private int mBarType;
        private int mGirth;
        private int mZOrder;
        private boolean mHideForKeyboard;

        private SystemBarConfigBuilder setSide(@SystemBarSide int side) {
            mSide = side;
            return this;
        }

        private SystemBarConfigBuilder setBarType(int type) {
            mBarType = type;
            return this;
        }

        private SystemBarConfigBuilder setGirth(int girth) {
            mGirth = girth;
            return this;
        }

        private SystemBarConfigBuilder setZOrder(int zOrder) {
            mZOrder = zOrder;
            return this;
        }

        private SystemBarConfigBuilder setHideForKeyboard(boolean hide) {
            mHideForKeyboard = hide;
            return this;
        }

        private SystemBarConfig build() {
            return new SystemBarConfig(mSide, mBarType, mGirth, mZOrder, mHideForKeyboard);
        }
    }
}
