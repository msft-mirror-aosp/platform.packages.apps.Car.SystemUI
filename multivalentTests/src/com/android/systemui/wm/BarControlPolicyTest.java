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

import static android.view.WindowInsets.Type.navigationBars;
import static android.view.WindowInsets.Type.statusBars;

import static com.google.common.truth.Truth.assertThat;

import android.car.settings.CarSettings;
import android.provider.Settings;
import android.testing.TestableLooper;
import android.view.WindowInsets.Type.InsetsType;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.systemui.SysuiTestCase;
import com.android.systemui.car.CarSystemUiTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@CarSystemUiTest
@RunWith(AndroidJUnit4.class)
@TestableLooper.RunWithLooper
@SmallTest
public class BarControlPolicyTest extends SysuiTestCase {

    private static final String PACKAGE_NAME = "sample.app";
    private static final String PACKAGE_NAME2 = "sample2.app";

    @InsetsType
    private static final int REQUESTED_VISIBILITY_IRRELEVANT = 0;
    @InsetsType
    private static final int REQUESTED_VISIBILITY_HIDE_ALL_BARS = 0;
    @InsetsType
    private static final int REQUESTED_VISIBILITY_STATUS_BARS = statusBars();
    @InsetsType
    private static final int REQUESTED_VISIBILITY_NAVIGATION_BARS = navigationBars();
    @InsetsType
    private static final int REQUESTED_VISIBILITY_SHOW_ALL_BARS = statusBars() | navigationBars();

    @Before
    public void setUp() {
        BarControlPolicy.reset();
    }

    @After
    public void tearDown() {
        Settings.Global.clearProviderForTest();
    }

    @Test
    public void reloadFromSetting_notSet_doesNotSetFilters() {
        BarControlPolicy.reloadFromSetting(mContext);

        assertThat(BarControlPolicy.sImmersiveStatusFilter).isNull();
    }

    @Test
    public void reloadFromSetting_invalidPolicyControlString_doesNotSetFilters() {
        configureBarPolicy("sample text");

        BarControlPolicy.reloadFromSetting(mContext);

        assertThat(BarControlPolicy.sImmersiveStatusFilter).isNull();
    }

    @Test
    public void reloadFromSetting_validPolicyControlString_setsFilters() {
        configureBarPolicy("immersive.status=" + PACKAGE_NAME);

        BarControlPolicy.reloadFromSetting(mContext);

        assertThat(BarControlPolicy.sImmersiveStatusFilter).isNotNull();
    }

    @Test
    public void reloadFromSetting_filtersSet_doesNotSetFiltersAgain() {
        configureBarPolicy("immersive.status=" + PACKAGE_NAME);

        BarControlPolicy.reloadFromSetting(mContext);

        assertThat(BarControlPolicy.reloadFromSetting(mContext)).isFalse();
    }

    @Test
    public void getBarVisibilities_policyControlNotSet_showsSystemBars() {
        int[] visibilities =
                BarControlPolicy.getBarVisibilities(PACKAGE_NAME, REQUESTED_VISIBILITY_IRRELEVANT);

        assertThat(visibilities[0]).isEqualTo(statusBars() | navigationBars());
        assertThat(visibilities[1]).isEqualTo(0);
    }

    @Test
    public void getBarVisibilities_immersiveStatusForAppAndMatchingApp_hidesStatusBar() {
        configureBarPolicy("immersive.status=" + PACKAGE_NAME);
        BarControlPolicy.reloadFromSetting(mContext);

        int[] visibilities =
                BarControlPolicy.getBarVisibilities(PACKAGE_NAME, REQUESTED_VISIBILITY_IRRELEVANT);

        assertThat(visibilities[0]).isEqualTo(navigationBars());
        assertThat(visibilities[1]).isEqualTo(statusBars());
    }

    @Test
    public void getBarVisibilities_immersiveStatusForAppAndNonMatchingApp_showsSystemBars() {
        configureBarPolicy("immersive.status=" + PACKAGE_NAME);
        BarControlPolicy.reloadFromSetting(mContext);

        int[] visibilities =
                BarControlPolicy.getBarVisibilities("sample2.app", REQUESTED_VISIBILITY_IRRELEVANT);

        assertThat(visibilities[0]).isEqualTo(statusBars() | navigationBars());
        assertThat(visibilities[1]).isEqualTo(0);
    }

    @Test
    public void getBarVisibilities_immersiveStatusForAppsAndNonApp_showsSystemBars() {
        configureBarPolicy("immersive.status=apps");
        BarControlPolicy.reloadFromSetting(mContext);

        int[] visibilities =
                BarControlPolicy.getBarVisibilities(PACKAGE_NAME, REQUESTED_VISIBILITY_IRRELEVANT);

        assertThat(visibilities[0]).isEqualTo(statusBars() | navigationBars());
        assertThat(visibilities[1]).isEqualTo(0);
    }

    @Test
    public void getBarVisibilities_immersiveFullForAppAndMatchingApp_hidesSystemBars() {
        configureBarPolicy("immersive.full=" + PACKAGE_NAME);
        BarControlPolicy.reloadFromSetting(mContext);

        int[] visibilities =
                BarControlPolicy.getBarVisibilities(PACKAGE_NAME, REQUESTED_VISIBILITY_IRRELEVANT);

        assertThat(visibilities[0]).isEqualTo(0);
        assertThat(visibilities[1]).isEqualTo(statusBars() | navigationBars());
    }

    @Test
    public void getBarVisibilities_immersiveFullForAppAndNonMatchingApp_showsSystemBars() {
        configureBarPolicy("immersive.full=" + PACKAGE_NAME);
        BarControlPolicy.reloadFromSetting(mContext);

        int[] visibilities =
                BarControlPolicy.getBarVisibilities("sample2.app", REQUESTED_VISIBILITY_IRRELEVANT);

        assertThat(visibilities[0]).isEqualTo(statusBars() | navigationBars());
        assertThat(visibilities[1]).isEqualTo(0);
    }

    @Test
    public void getBarVisibilities_immersiveFullForAppsAndNonApp_showsSystemBars() {
        configureBarPolicy("immersive.full=apps");
        BarControlPolicy.reloadFromSetting(mContext);

        int[] visibilities =
                BarControlPolicy.getBarVisibilities(PACKAGE_NAME, REQUESTED_VISIBILITY_IRRELEVANT);

        assertThat(visibilities[0]).isEqualTo(statusBars() | navigationBars());
        assertThat(visibilities[1]).isEqualTo(0);
    }

    @Test
    public void getBarVisibilities_immersiveStatusWithAllowPolicy_allowsShowStatus() {
        configureBarPolicy("immersive.status=+" + PACKAGE_NAME);

        @InsetsType int[] visibilitiesShowStatus = BarControlPolicy.getBarVisibilities(
                PACKAGE_NAME, REQUESTED_VISIBILITY_STATUS_BARS);

        assertThat(barsShown(visibilitiesShowStatus, navigationBars() | statusBars())).isTrue();
        assertThat(barsHidden(visibilitiesShowStatus, /* barTypes= */ 0)).isTrue();

        @InsetsType int[] visibilitiesShowAllBars = BarControlPolicy.getBarVisibilities(
                PACKAGE_NAME, REQUESTED_VISIBILITY_SHOW_ALL_BARS);

        assertThat(barsShown(visibilitiesShowAllBars, navigationBars() | statusBars())).isTrue();
        assertThat(barsHidden(visibilitiesShowAllBars, /* barTypes= */ 0)).isTrue();
    }

    @Test
    public void getBarVisibilities_immersiveStatusWithAllowPolicy_allowsHideStatus() {
        configureBarPolicy("immersive.status=+" + PACKAGE_NAME);

        @InsetsType int[] visibilitiesShowStatus = BarControlPolicy.getBarVisibilities(
                PACKAGE_NAME, REQUESTED_VISIBILITY_NAVIGATION_BARS);

        assertThat(barsShown(visibilitiesShowStatus, navigationBars())).isTrue();
        assertThat(barsHidden(visibilitiesShowStatus, statusBars())).isTrue();

        @InsetsType int[] visibilitiesShowAllBars = BarControlPolicy.getBarVisibilities(
                PACKAGE_NAME, REQUESTED_VISIBILITY_HIDE_ALL_BARS);

        assertThat(barsShown(visibilitiesShowAllBars, navigationBars())).isTrue();
        assertThat(barsHidden(visibilitiesShowAllBars, statusBars())).isTrue();
    }

    @Test
    public void getBarVisibilities_immersiveNavigationWithAllowPolicy_allowsShowNavigation() {
        configureBarPolicy("immersive.navigation=+" + PACKAGE_NAME);

        @InsetsType int[] visibilitiesShowStatus = BarControlPolicy.getBarVisibilities(
                PACKAGE_NAME, REQUESTED_VISIBILITY_NAVIGATION_BARS);

        assertThat(barsShown(visibilitiesShowStatus, navigationBars() | statusBars())).isTrue();
        assertThat(barsHidden(visibilitiesShowStatus, /* barTypes= */ 0)).isTrue();

        @InsetsType int[] visibilitiesShowAllBars = BarControlPolicy.getBarVisibilities(
                PACKAGE_NAME, REQUESTED_VISIBILITY_SHOW_ALL_BARS);

        assertThat(barsShown(visibilitiesShowAllBars, navigationBars() | statusBars())).isTrue();
        assertThat(barsHidden(visibilitiesShowAllBars, /* barTypes= */ 0)).isTrue();
    }

    @Test
    public void getBarVisibilities_immersiveNavigationWithAllowPolicy_allowsHideNavigation() {
        configureBarPolicy("immersive.navigation=+" + PACKAGE_NAME);

        @InsetsType int[] visibilitiesShowStatus = BarControlPolicy.getBarVisibilities(
                PACKAGE_NAME, REQUESTED_VISIBILITY_STATUS_BARS);

        assertThat(barsShown(visibilitiesShowStatus, statusBars())).isTrue();
        assertThat(barsHidden(visibilitiesShowStatus, navigationBars())).isTrue();

        @InsetsType int[] visibilitiesShowAllBars = BarControlPolicy.getBarVisibilities(
                PACKAGE_NAME, REQUESTED_VISIBILITY_SHOW_ALL_BARS);

        assertThat(barsShown(visibilitiesShowAllBars, navigationBars() | statusBars())).isTrue();
        assertThat(barsHidden(visibilitiesShowAllBars, /* barTypes= */ 0)).isTrue();
    }

    @Test
    public void getBarVisibilities_immersiveFullWithAllowPolicy_allowsShowAndHideBars() {
        configureBarPolicy("immersive.full=+" + PACKAGE_NAME);

        @InsetsType int[] visibilitiesShowStatus = BarControlPolicy.getBarVisibilities(
                PACKAGE_NAME, REQUESTED_VISIBILITY_SHOW_ALL_BARS);

        assertThat(barsShown(visibilitiesShowStatus, navigationBars() | statusBars())).isTrue();
        assertThat(barsHidden(visibilitiesShowStatus, /* barTypes= */ 0)).isTrue();

        @InsetsType int[] visibilitiesShowAllBars = BarControlPolicy.getBarVisibilities(
                PACKAGE_NAME, REQUESTED_VISIBILITY_HIDE_ALL_BARS);

        assertThat(barsShown(visibilitiesShowAllBars, /* barTypes= */ 0)).isTrue();
        assertThat(barsHidden(visibilitiesShowAllBars, navigationBars() | statusBars())).isTrue();
    }

    @Test
    public void getBarVisibilities_combinedImmersiveStatusWithAllowPolicy_hidesSelectively() {
        configureBarPolicy(String.format("immersive.status=%s,+%s", PACKAGE_NAME, PACKAGE_NAME2));

        @InsetsType int[] visibilities0 = BarControlPolicy.getBarVisibilities(
                PACKAGE_NAME, REQUESTED_VISIBILITY_SHOW_ALL_BARS);

        assertThat(barsShown(visibilities0, navigationBars())).isTrue();
        assertThat(barsHidden(visibilities0, statusBars())).isTrue();

        @InsetsType int[] visibilities1 = BarControlPolicy.getBarVisibilities(
                PACKAGE_NAME2, REQUESTED_VISIBILITY_SHOW_ALL_BARS);

        assertThat(barsShown(visibilities1, statusBars() | navigationBars())).isTrue();
        assertThat(barsHidden(visibilities1, /* barTypes= */ 0)).isTrue();
    }

    @Test
    public void getBarVisibilities_combinedImmersiveNavigationWithAllowPolicy_hidesSelectively() {
        configureBarPolicy(
                String.format("immersive.navigation=+%s,%s", PACKAGE_NAME, PACKAGE_NAME2));

        @InsetsType int[] visibilities0 = BarControlPolicy.getBarVisibilities(
                PACKAGE_NAME, REQUESTED_VISIBILITY_SHOW_ALL_BARS);

        assertThat(barsShown(visibilities0, statusBars() | navigationBars())).isTrue();
        assertThat(barsHidden(visibilities0, /* barTypes= */ 0)).isTrue();

        @InsetsType int[] visibilities1 = BarControlPolicy.getBarVisibilities(
                PACKAGE_NAME2, REQUESTED_VISIBILITY_SHOW_ALL_BARS);

        assertThat(barsShown(visibilities1, statusBars())).isTrue();
        assertThat(barsHidden(visibilities1, navigationBars())).isTrue();
    }

    private void configureBarPolicy(String configuration) {
        Settings.Global.putString(
                mContext.getContentResolver(),
                CarSettings.Global.SYSTEM_BAR_VISIBILITY_OVERRIDE,
                configuration);
        BarControlPolicy.reloadFromSetting(mContext);
    }

    private static boolean barsShown(@InsetsType int[] visibilities, @InsetsType int barTypes) {
        return visibilities[0] == barTypes;
    }

    private static boolean barsHidden(@InsetsType int[] visibilities, @InsetsType int barTypes) {
        return visibilities[1] == barTypes;
    }
}
