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

import android.annotation.DimenRes;
import android.annotation.LayoutRes;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupWindow;

import com.android.systemui.broadcast.BroadcastDispatcher;
import com.android.systemui.car.CarServiceProvider;
import com.android.systemui.car.qc.SystemUIQCView;

import java.util.ArrayList;

/**
 * A subclass of {@link ControlPanelController} that supports toggling between enabling and
 * disabling {@link SystemUIQCView} listening for updates.
 */
public class QuickControlPanelController extends ControlPanelController {
    private final ArrayList<SystemUIQCView> mQCViews = new ArrayList<>();

    public QuickControlPanelController(
            Context context,
            CarServiceProvider carServiceProvider,
            BroadcastDispatcher broadcastDispatcher) {
        super(context, carServiceProvider, broadcastDispatcher);
    }

    @Override
    protected PopupWindow createPanel(@LayoutRes int layoutRes, @DimenRes int widthRes) {
        PopupWindow popupWindow = super.createPanel(layoutRes, widthRes);

        findQcViews(getPanelContentView());

        return popupWindow;
    }

    @Override
    protected void reset() {
        super.reset();

        mQCViews.forEach(v -> v.destroy());
        mQCViews.clear();
    }

    @Override
    protected void onPanelRootViewClicked(View rootView, @LayoutRes int layoutRes,
            @DimenRes int widthRes) {
        super.onPanelRootViewClicked(rootView, layoutRes, widthRes);

        if (getPanel().isShowing()) {
            mQCViews.forEach(qcView -> qcView.listen(true));
        }
    }

    @Override
    protected void onPanelDismissed() {
        super.onPanelDismissed();

        mQCViews.forEach(qcView -> qcView.listen(false));
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
