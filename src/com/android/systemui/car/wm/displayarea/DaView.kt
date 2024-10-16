/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.systemui.car.wm.displayarea

import android.content.Context
import android.graphics.Rect
import android.graphics.Region
import android.gui.TrustedOverlay
import android.os.Binder
import android.util.AttributeSet
import android.util.Slog
import android.util.SparseArray
import android.view.InsetsSource
import android.view.SurfaceControl
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.window.DisplayAreaInfo
import android.window.DisplayAreaOrganizer
import android.window.WindowContainerTransaction
import com.android.systemui.R
import com.android.systemui.car.Flags.daviewBasedWindowing

const val INVALID_DISPLAY_AREA_FEATURE_ID = -1
const val INVALID_DAVIEW_ID = -1L

/**
 * A DaView is a SurfaceView which has surface of a display area as a child. It can either be used
 * with a DAG (DisplayAreaGroup) or a TDA (TaskDisplayArea). When used with a DAG, the DAG must
 * have only one TDA so that the atomic unit becomes (DAG -> TDA).
 *
 * <ul>
 *     <li> When used for a DAG, the displayAreaFeatureId should be the DAG and launchTaskDisplayAreaFeatureId should be
 *     the TDA inside the DAG</li>
 *     <li> When used for a TDA directly, displayAreaFeatureId and launchTaskDisplayAreaFeatureId can both point to TDA
 *     </li>
 * </ul>
 */
class DaView : SurfaceView, SurfaceHolder.Callback {
    companion object {
        private val TAG = DaView::class.java.simpleName
    }

    /**
     * A unique identifier composed of the [DaView.displayAreaFeatureId] and the display which
     * this display area is in.
     */
    val id: Long
    val cornerRadius: Int

    /**
     * Directly maps to the [com.android.server.wm.DisplayArea.mFeatureId]. This is not a unique
     * identifier though. Two display areas on different displays can have the same featureId.
     */
    val displayAreaFeatureId: Int
    val launchTaskDisplayAreaFeatureId: Int

    internal lateinit var daInfo: DisplayAreaInfo
    internal lateinit var daLeash: SurfaceControl

    /**
     * Governs if the surface change should instantly trigger a wm change without shell transitions
     * for the corresponding DisplayAreaGroup.
     * This can be helpful when using composable layouts for prototyping where the changes are need
     * to take effect right away. But should ideally be disabled in the interest
     * of using {@link DaViewTransitions} for all the WM updates.
     * This doesn't apply to surfaceCreated callback. Surface creation leads to direct wm update
     * as of today as a transition is usually not required when surface is created.
     */
    var surfaceToWmSyncEnabled = true

    private val tmpTransaction: SurfaceControl.Transaction = SurfaceControl.Transaction()
    private val insetsOwner = Binder()
    private val insets = SparseArray<Rect>()
    private val touchableInsetsProvider = TouchableInsetsProvider(this)
    private var obscuredTouchRegion: Region? = null
    private var surfaceCreated = false
    private lateinit var organizer: DisplayAreaOrganizer

    constructor(context: Context) : super(context) {
        if (!daviewBasedWindowing()) {
            throw IllegalAccessException("DaView feature not available")
        }

        cornerRadius = 0
        displayAreaFeatureId = INVALID_DISPLAY_AREA_FEATURE_ID
        launchTaskDisplayAreaFeatureId = INVALID_DISPLAY_AREA_FEATURE_ID
        id = INVALID_DAVIEW_ID

        init()
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        val typedArray = context.obtainStyledAttributes(attrs, R.styleable.DisplayAreaView)
        cornerRadius = typedArray.getInteger(R.styleable.DisplayAreaView_cornerRadius, 0)
        displayAreaFeatureId =
            typedArray.getInteger(R.styleable.DisplayAreaView_displayAreaFeatureId, -1)
        launchTaskDisplayAreaFeatureId =
            typedArray.getInteger(R.styleable.DisplayAreaView_launchTaskDisplayAreaFeatureId, -1)
        id = (context.displayId.toLong() shl 32) or (displayAreaFeatureId.toLong() and 0xffffffffL)

        typedArray.recycle()

        init()
    }

    private fun init() {
        if (displayAreaFeatureId == INVALID_DISPLAY_AREA_FEATURE_ID) {
            Slog.e(TAG, "Unknown feature ID for a DisplayAreaView")
            return
        }

        organizer = object : DisplayAreaOrganizer(context.mainExecutor) {
            override fun onDisplayAreaAppeared(
                displayAreaInfo: DisplayAreaInfo,
                leash: SurfaceControl
            ) {
                super.onDisplayAreaAppeared(displayAreaInfo, leash)
                daInfo = displayAreaInfo
                this@DaView.daLeash = leash

                if (surfaceCreated) {
                    tmpTransaction.reparent(leash, surfaceControl)
                        // Sometimes when the systemui crashes and the leash is reattached to
                        // the new surface control, it could already have some dirty position
                        // set by WM or the container of DAView. So the child leash must be
                        // repositioned to 0,0 here.
                        .setPosition(leash, 0f, 0f)
                        .show(leash)
                        .apply()
                }
            }

            override fun onDisplayAreaInfoChanged(displayAreaInfo: DisplayAreaInfo) {
                super.onDisplayAreaInfoChanged(displayAreaInfo)
                // This callback doesn't need to be handled as of now as the config changes will
                // directly propagate to the children of DisplayArea. If in the future, the
                // decors in the window owning the layout of screen are needed to be adjusted
                // based on display area's config, DaView can expose APIs to listen to these
                // changes.
            }
        }.apply {
            val displayAreaInfos = registerOrganizer(displayAreaFeatureId)
            displayAreaInfos.forEach {
                if (it.displayAreaInfo.displayId == context.displayId) {
                    // There would be just one DisplayArea with a unique (displayId,
                    // displayAreaFeatureId)
                    daInfo = it.displayAreaInfo
                    daLeash = it.leash
                }
            }
        }
        holder.addCallback(this)
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        surfaceCreated = true
        tmpTransaction.reparent(daLeash, surfaceControl)
            // DaView is meant to contain app activities which shouldn't have trusted overlays
            // flag set even when itself reparented in a window which is trusted.
            .setTrustedOverlay(surfaceControl, TrustedOverlay.DISABLED)
            .setCornerRadius(surfaceControl, cornerRadius.toFloat())
            .setPosition(daLeash, 0f, 0f)
            .show(daLeash)
            .apply()
        syncBoundsToWm()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        if (!surfaceToWmSyncEnabled) {
            return
        }
        syncBoundsToWm()
    }

    fun syncBoundsToWm() {
        val wct = WindowContainerTransaction()
        var rect = Rect()
        getBoundsOnScreen(rect)
        wct.setBounds(daInfo.token, rect)
        DaViewTransitions.sInstance?.instantApplyViaShellTransit(wct)
    }

    fun resyncLeashToView(tr: SurfaceControl.Transaction) {
        if (!surfaceCreated) {
            return
        }
        tr.reparent(daLeash, surfaceControl)
            .setPosition(daLeash, 0f, 0f)
            .show(daLeash)
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        surfaceCreated = false
        tmpTransaction.reparent(daLeash, null).apply()
    }

    public override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        touchableInsetsProvider.addToViewTreeObserver()
        DaViewTransitions.sInstance?.add(this) ?: run {
            Slog.e(TAG, "Failed adding $this to DaViewTransitions")
        }
    }

    public override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        touchableInsetsProvider.removeFromViewTreeObserver()
        DaViewTransitions.sInstance?.remove(this) ?: run {
            Slog.e(TAG, "Failed to remove $this from DaViewTransitions")
        }
    }

    /**
     * Indicates a region of the view that is not touchable.
     *
     * @param obscuredRect the obscured region of the view.
     */
    fun setObscuredTouchRect(obscuredRect: Rect) {
        obscuredTouchRegion = Region(obscuredRect)
        touchableInsetsProvider.setObscuredTouchRegion(obscuredTouchRegion)
    }

    /**
     * Indicates a region of the view that is not touchable.
     *
     * @param obscuredRegion the obscured region of the view.
     */
    fun setObscuredTouchRegion(obscuredRegion: Region) {
        obscuredTouchRegion = obscuredRegion
        touchableInsetsProvider.setObscuredTouchRegion(obscuredTouchRegion)
    }

    fun addInsets(index: Int, type: Int, frame: Rect) {
        insets.append(InsetsSource.createId(insetsOwner, index, type), frame)
        val wct = WindowContainerTransaction()
        val insetsFlags = 0
        wct.addInsetsSource(
                daInfo.token,
                insetsOwner,
                index,
                type,
                frame,
                emptyArray<Rect>(),
                insetsFlags
        )
        DaViewTransitions.sInstance?.instantApplyViaTaskOrganizer(wct)
    }

    fun removeInsets(index: Int, type: Int) {
        if (insets.size() == 0) {
            Slog.w(TAG, "No insets set.")
            return
        }
        val id = InsetsSource.createId(insetsOwner, index, type)
        if (!insets.contains(id)) {
            Slog.w(
                TAG,
                "Insets type: " + type + " can't be removed as it was not " +
                        "applied as part of the last addInsets()"
            )
            return
        }
        insets.remove(id)
        val wct = WindowContainerTransaction()
        wct.removeInsetsSource(
            daInfo.token,
            insetsOwner,
            index,
            type
        )
        DaViewTransitions.sInstance?.instantApplyViaTaskOrganizer(wct)
    }
}
