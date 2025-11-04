/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.systemui.car.wm.scalableui.view;

import static com.android.car.scalableui.loader.xml.PanelTagXmlParser.DRAG_DEC_EVENT_ID_TAG;
import static com.android.car.scalableui.loader.xml.PanelTagXmlParser.DRAG_INC_EVENT_ID_TAG;
import static com.android.car.scalableui.loader.xml.PanelTagXmlParser.EVENT_ID_TAG;
import static com.android.car.scalableui.loader.xml.PanelTagXmlParser.ORIENTATION_TAG;
import static com.android.car.scalableui.loader.xml.PanelTagXmlParser.SNAPTHREADHOLD_TAG;
import static com.android.systemui.car.wm.scalableui.systemevents.SystemEventConstants.PANEL_DRAG_DIRECTION_ID;

import android.annotation.SuppressLint;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;

import com.android.car.scalableui.model.BreakPoint;
import com.android.car.scalableui.model.Event;
import com.android.car.scalableui.model.KeyFrameEvent;
import com.android.car.scalableui.model.PanelControllerMetadata;
import com.android.car.scalableui.panel.DecorPanelController;
import com.android.systemui.car.wm.scalableui.EventDispatcher;
import com.android.systemui.car.wm.scalableui.panel.controller.DecorPanelViewMap;

import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;
import dagger.assisted.AssistedInject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.inject.Provider;

/**
 * A Controller for the {@link GripBar}
 * <p>
 * Configuration for the GripBar is read from a themed attribute, which is
 * expected to be an array resource. The array should contain values defining
 * the behavior of the GripBar. The following indices in the configuration
 * array are used:
 * </p>
 * <ul>
 * <li>Index 0: View provider name (String)</li>
 * <li>Index 1: View class name (String)</li>
 * <li>Index 2: Drag event ID (String)</li>
 * <li>Index 3: Orientation (0 for vertical, 1 for horizontal) (Integer)</li>
 * <li>Index 4: Snap threshold (Dimension)</li>
 * <li>Index 5: Resource ID of the breakpoint definition array (Integer)</li>
 * </ul>
 */
public class GripBarViewController extends DecorPanelControllerBase implements
        GripBar.GripBarEventHandler {
    private static final String TAG = GripBarViewController.class.getSimpleName();
    private static final String DRAG_NO_CHANGE = "noChange";
    private static final String DRAG_INCREASE = "increase";
    private static final String DRAG_DECREASE = "decrease";
    private final String mPanelId;
    private final EventDispatcher mEventDispatcher;
    private final List<BreakPoint> mBreakPoints;
    private GripBar mGripBar;
    private boolean mIsHorizontal;
    private String mDragEventId;
    private String mDragDecreaseEventId;
    private String mDragIncreaseEventId;
    private float mSnapThreshold;
    private float mDragStart;
    private int mState = 0;
    private BreakPoint mStartBreakPoint;
    private float mLastDispatchedProgress;

    @Override
    public void onClick() {
        Event event = new Event.Builder((mBreakPoints.get(mState).getEventId())).build();
        dispatchEvent(event);
        mState = (mState + 1) % mBreakPoints.size();
        logIfDebuggable("onclick " + event);
    }

    @AssistedInject
    public GripBarViewController(@Assisted String panelId,
            @Assisted PanelControllerMetadata metadata,
            @DecorPanelViewMap Map<Class<?>, Provider<View>> decorPanelViewMap,
            EventDispatcher eventDispatcher) {
        super(metadata, decorPanelViewMap);
        mPanelId = panelId;
        mEventDispatcher = eventDispatcher;
        mBreakPoints = new ArrayList<>();
        init(metadata);
    }

    @AssistedFactory
    public interface Factory extends DecorPanelController.Factory<GripBarViewController> {
        /** Create an instance of GripBarViewController with the provided PanelControllerMetadata */
        GripBarViewController create(String panelId, PanelControllerMetadata metadata);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    @Nullable
    public View getView() {
        View view = super.getView();
        if (view instanceof GripBar gripBar) {
            mGripBar = gripBar;
        } else {
            throw new RuntimeException("GripBarViewController mush have a gripBar view");
        }
        mGripBar.addGripBarEventHandlers(this);
        return mGripBar;
    }

    private void init(PanelControllerMetadata metadata) {
        mDragEventId = metadata.getStringConfiguration(EVENT_ID_TAG);
        mDragDecreaseEventId = metadata.getStringConfiguration(DRAG_DEC_EVENT_ID_TAG);
        mDragIncreaseEventId = metadata.getStringConfiguration(DRAG_INC_EVENT_ID_TAG);
        mIsHorizontal = Integer.parseInt(
                metadata.getStringConfiguration(ORIENTATION_TAG)) == 1;
        mSnapThreshold = Integer.parseInt(
                metadata.getStringConfiguration(SNAPTHREADHOLD_TAG));
        mBreakPoints.clear();
        mBreakPoints.addAll(metadata.getBreakPoints());
        logIfDebuggable("Parse array: " + this);
    }

    private void dispatchEvent(Event event) {
        if (mEventDispatcher == null) {
            Log.e(TAG, "EventDispatcher is null");
            return;
        }
        mEventDispatcher.executeEvent(event);
    }

    private float getDistance(BreakPoint breakPoint, float value) {
        return Math.abs(value - breakPoint.getPoint());
    }

    private BreakPoint findClosestBreakPoint(float value) {
        BreakPoint closest = mBreakPoints.getFirst();
        float minDistance = Float.MAX_VALUE;
        for (BreakPoint breakPoint : mBreakPoints) {
            float distance = getDistance(breakPoint, value);
            if (distance < minDistance) {
                minDistance = distance;
                closest = breakPoint;
            }
        }
        return closest;
    }

    @Override
    public void onTouch(MotionEvent event) {
        if (mBreakPoints.size() < 2) {
            Log.w(TAG, "break point not valid " + mBreakPoints.size());
            return;
        }

        float value = mIsHorizontal ? event.getRawX() : event.getRawY();
        float min = mBreakPoints.getFirst().getPoint();
        float max = mBreakPoints.getLast().getPoint();
        BreakPoint closest = findClosestBreakPoint(value);
        float closestDistance = getDistance(closest, value);
        if (closestDistance < mSnapThreshold) {
            value = closest.getPoint();
        }

        if (mStartBreakPoint == null) {
            mStartBreakPoint = findClosestBreakPoint(value);
        }

        float progress = (value - min) / (max - min);
        if (progress < 0 || progress > 1) {
            progress = progress < 0 ? 0 : 1;
        }

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                mDragStart = mIsHorizontal ? event.getRawX() : event.getRawY();
                return;
            case MotionEvent.ACTION_MOVE:
                dispatchEvent(progress, value, event);
                break;
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP:
                dispatchDirectionEvent(value, closest);
                mStartBreakPoint = null;
                break;
            default:
        }
    }

    private void dispatchEvent(float progress, float value, MotionEvent event) {
        if (progress == mLastDispatchedProgress && event.getAction() != MotionEvent.ACTION_UP) {
            // don't dispatch multiple events from same progress
            return;
        }
        if (mDragDecreaseEventId != null && value < mDragStart) {
            Event keyFrameEvent = new KeyFrameEvent.Builder(mDragDecreaseEventId, progress)
                    .setPanelId(mPanelId)
                    .build();
            dispatchEvent(keyFrameEvent);
        } else if (mDragIncreaseEventId != null) {
            Event keyFrameEvent = new KeyFrameEvent.Builder(mDragIncreaseEventId, progress)
                    .setPanelId(mPanelId)
                    .build();
            dispatchEvent(keyFrameEvent);
        } else {
            Event keyFrameEvent = new KeyFrameEvent.Builder(mDragEventId, progress)
                    .setPanelId(mPanelId)
                    .build();
            dispatchEvent(keyFrameEvent);
        }
        mLastDispatchedProgress = progress;
    }

    private void dispatchDirectionEvent(float value, BreakPoint breakPoint) {
        String direction;
        if (mStartBreakPoint.getEventId().equals(breakPoint.getEventId())) {
            direction = DRAG_NO_CHANGE;
        } else if (value < mDragStart) {
            direction = DRAG_DECREASE;
        } else {
            direction = DRAG_INCREASE;
        }
        dispatchEvent(new Event.Builder(breakPoint.getEventId())
                .addToken(PANEL_DRAG_DIRECTION_ID, direction)
                .setPanelId(mPanelId)
                .build());
    }

    @Override
    public String toString() {
        String breakpointsString = (mBreakPoints == null) ? "null" :
                mBreakPoints.stream()
                        .map(Object::toString)
                        .collect(Collectors.joining(", ", "[", "]"));

        return "GripBarViewProvider {"
                + "mPanelId=" + mPanelId
                + ", mGripBar=" + mGripBar
                + ", mIsHorizontal=" + mIsHorizontal
                + ", mDragEventId='" + mDragEventId + '\''
                + ", mSnapThreshold=" + mSnapThreshold
                + ", mBreakPoints=" + breakpointsString + '}';
    }
}
