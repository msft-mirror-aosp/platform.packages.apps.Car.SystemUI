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

import android.car.settings.CarSettings;
import android.content.Context;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.ArraySet;
import android.util.Slog;
import android.view.WindowInsets;
import android.view.WindowInsets.Type.InsetsType;

import androidx.annotation.VisibleForTesting;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * Util class to load PolicyControl and allow for querying if a package matches immersive filters.
 * Similar to {@link com.android.server.wm.PolicyControl}, but separate due to CarSystemUI needing
 * to set its own policies for system bar visibilities.
 *
 * This forces immersive mode behavior for one or both system bars (based on a package
 * list).
 *
 * Control by setting {@link Settings.Global#POLICY_CONTROL_AUTO} to one or more name-value pairs.
 * e.g.
 *   to force immersive mode everywhere:
 *     "immersive.full=*"
 *   to force hide status bars for com.package1 but not com.package2:
 *     "immersive.status=com.package1,-com.package2"
 *   to force hide navigation bar everywhere, and com.package1 to control visibility of both system
 *   bar types:
 *     "immersive.navigation=*,+com.package1"
 *
 * Separate multiple name-value pairs with ':'
 *   e.g. "immersive.status=com.package:immersive.navigation=*"
 */
public class BarControlPolicy {

    private static final String TAG = "BarControlPolicy";
    private static final boolean DEBUG = false;

    private static final String NAME_IMMERSIVE_FULL = "immersive.full";
    private static final String NAME_IMMERSIVE_STATUS = "immersive.status";
    private static final String NAME_IMMERSIVE_NAVIGATION = "immersive.navigation";

    @VisibleForTesting
    static String sSettingValue;
    @VisibleForTesting
    static Filter sImmersiveStatusFilter;
    private static Filter sImmersiveNavigationFilter;

    /** Loads values from the POLICY_CONTROL setting to set filters. */
    static boolean reloadFromSetting(Context context) {
        if (DEBUG) Slog.d(TAG, "reloadFromSetting()");
        String value = null;
        try {
            value = Settings.Global.getStringForUser(context.getContentResolver(),
                    CarSettings.Global.SYSTEM_BAR_VISIBILITY_OVERRIDE,
                    UserHandle.USER_CURRENT);
            if (sSettingValue == value || sSettingValue != null && sSettingValue.equals(value)) {
                return false;
            }
            setFilters(value);
            sSettingValue = value;
        } catch (Throwable t) {
            Slog.w(TAG, "Error loading policy control, value=" + value, t);
            return false;
        }
        return true;
    }

    /** Used in testing to reset BarControlPolicy. */
    @VisibleForTesting
    static void reset() {
        sSettingValue = null;
        sImmersiveStatusFilter = null;
        sImmersiveNavigationFilter = null;
    }

    /**
     * Registers a content observer to listen to updates to the SYSTEM_BAR_VISIBILITY_OVERRIDE flag.
     */
    static void registerContentObserver(Context context, Handler handler, FilterListener listener) {
        context.getContentResolver().registerContentObserver(
                Settings.Global.getUriFor(CarSettings.Global.SYSTEM_BAR_VISIBILITY_OVERRIDE), false,
                new ContentObserver(handler) {
                    @Override
                    public void onChange(boolean selfChange) {
                        if (reloadFromSetting(context)) {
                            listener.onFilterUpdated();
                        }
                    }
                }, UserHandle.USER_ALL);
    }

    /**
     * Returns bar visibilities based on POLICY_CONTROL_AUTO filters, window policies and the
     * requested visible system bar types.
     *
     * @return int[], where the first value is the inset types that should be shown, and the second
     *         is the inset types that should be hidden.
     */
    @InsetsType
    public static int[] getBarVisibilities(
            String packageName, @InsetsType int requestedVisibleTypes) {
        int hideTypes = 0;
        int showTypes = 0;

        if (isStatusControlAllowed(packageName)) {
            if ((requestedVisibleTypes & WindowInsets.Type.statusBars()) != 0) {
                showTypes |= WindowInsets.Type.statusBars();
            } else {
                hideTypes |= WindowInsets.Type.statusBars();
            }
        } else if (matchesStatusFilter(packageName)) {
            hideTypes |= WindowInsets.Type.statusBars();
        } else {
            showTypes |= WindowInsets.Type.statusBars();
        }

        if (isNavigationControlAllowed(packageName)) {
            if ((requestedVisibleTypes & WindowInsets.Type.navigationBars()) != 0) {
                showTypes |= WindowInsets.Type.navigationBars();
            } else {
                hideTypes |= WindowInsets.Type.navigationBars();
            }
        } else if (matchesNavigationFilter(packageName)) {
            hideTypes |= WindowInsets.Type.navigationBars();
        } else {
            showTypes |= WindowInsets.Type.navigationBars();
        }

        return new int[] { showTypes, hideTypes };
    }

    private static boolean matchesStatusFilter(String packageName) {
        return sImmersiveStatusFilter != null && sImmersiveStatusFilter.matches(packageName);
    }

    private static boolean matchesNavigationFilter(String packageName) {
        return sImmersiveNavigationFilter != null
                && sImmersiveNavigationFilter.matches(packageName);
    }

    /**
     * Returns {@code true} if the status bar visibility is allowed to be controlled by the given
     * package.
     */
    private static boolean isStatusControlAllowed(String packageName) {
        return sImmersiveStatusFilter != null
                && sImmersiveStatusFilter.isControlAllowed(packageName);
    }

    /**
     * Returns {@code true} if the navigation bar visibility is allowed to be controlled by the
     * given package.
     */
    private static boolean isNavigationControlAllowed(String packageName) {
        return sImmersiveNavigationFilter != null
                && sImmersiveNavigationFilter.isControlAllowed(packageName);
    }

    private static void setFilters(String value) {
        if (DEBUG) Slog.d(TAG, "setFilters: " + value);
        sImmersiveStatusFilter = null;
        sImmersiveNavigationFilter = null;
        if (value != null) {
            String[] nvps = value.split(":");
            for (String nvp : nvps) {
                int i = nvp.indexOf('=');
                if (i == -1) continue;
                String n = nvp.substring(0, i);
                String v = nvp.substring(i + 1);
                if (n.equals(NAME_IMMERSIVE_FULL)) {
                    Filter f = Filter.parse(v);
                    sImmersiveStatusFilter = sImmersiveNavigationFilter = f;
                } else if (n.equals(NAME_IMMERSIVE_STATUS)) {
                    Filter f = Filter.parse(v);
                    sImmersiveStatusFilter = f;
                } else if (n.equals(NAME_IMMERSIVE_NAVIGATION)) {
                    Filter f = Filter.parse(v);
                    sImmersiveNavigationFilter = f;
                }
            }
        }
        if (DEBUG) {
            Slog.d(TAG, "immersiveStatusFilter: " + sImmersiveStatusFilter);
            Slog.d(TAG, "immersiveNavigationFilter: " + sImmersiveNavigationFilter);
        }
    }

    private static class Filter {
        private static final String ALL = "*";

        private final ArraySet<String> mToInclude;
        private final ArraySet<String> mToExclude;
        private final ArraySet<String> mAllowControl;

        private Filter(ArraySet<String> toInclude, ArraySet<String> toExclude,
                ArraySet<String> allowControl) {
            mToInclude = toInclude;
            mToExclude = toExclude;
            mAllowControl = allowControl;
        }

        boolean matches(String packageName) {
            if (packageName == null) return false;
            if (toExclude(packageName)) return false;
            return toInclude(packageName);
        }

        private boolean toExclude(String packageName) {
            return mToExclude.contains(packageName) || mToExclude.contains(ALL);
        }

        private boolean toInclude(String packageName) {
            return mToInclude.contains(ALL) || mToInclude.contains(packageName);
        }

        boolean isControlAllowed(String packageName) {
            return mAllowControl.contains(ALL) || mAllowControl.contains(packageName);
        }

        void dump(PrintWriter pw) {
            pw.print("Filter[");
            dump("toInclude", mToInclude, pw);
            pw.print(',');
            dump("toExclude", mToExclude, pw);
            pw.print(',');
            dump("allowControl", mAllowControl, pw);
            pw.print(']');
        }

        private void dump(String name, ArraySet<String> set, PrintWriter pw) {
            pw.print(name); pw.print("=(");
            int n = set.size();
            for (int i = 0; i < n; i++) {
                if (i > 0) pw.print(',');
                pw.print(set.valueAt(i));
            }
            pw.print(')');
        }

        @Override
        public String toString() {
            StringWriter sw = new StringWriter();
            dump(new PrintWriter(sw, true));
            return sw.toString();
        }

        // value = comma-delimited list of tokens, where token = (package name|*)
        // e.g. "com.package1", or "com.android.systemui, com.android.keyguard" or "*"
        static Filter parse(String value) {
            if (value == null) return null;
            ArraySet<String> toInclude = new ArraySet<>();
            ArraySet<String> toExclude = new ArraySet<>();
            ArraySet<String> allowControl = new ArraySet<>();
            for (String token : value.split(",")) {
                token = token.trim();
                if (token.startsWith("-") && token.length() > 1) {
                    token = token.substring(1);
                    toExclude.add(token);
                } else if (token.startsWith("+") && token.length() > 1) {
                    token = token.substring(1);
                    allowControl.add(token);
                } else {
                    toInclude.add(token);
                }
            }
            return new Filter(toInclude, toExclude, allowControl);
        }
    }

    /**
     * Interface to listen for updates to the filter triggered by the content observer listening to
     * the SYSTEM_BAR_VISIBILITY_OVERRIDE flag.
     */
    interface FilterListener {

        /** Callback triggered when the content observer updates the filter. */
        void onFilterUpdated();
    }

    private BarControlPolicy() {}
}
