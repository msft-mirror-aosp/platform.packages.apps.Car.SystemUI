/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.systemui.car.minimizedcontrols

import android.content.Context
import android.graphics.drawable.Drawable
import android.net.Uri
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import com.android.car.telephony.common.TelecomUtils.createLetterTile
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions

/**
 * A custom view to display minimized dialer controls.
 */
class MinimizedDialerControlsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private lateinit var profileContainer: View
    private lateinit var muteButton: ImageButton
    private lateinit var endCallButton: ImageButton
    private lateinit var dialpadButton: ImageButton
    private lateinit var contactImage: ImageView
    private lateinit var appIcon: ImageView

    init {
        LayoutInflater.from(context).inflate(R.layout.minimized_dialer_controls_view, this, true)

        profileContainer = findViewById(R.id.dialer_profile_container)
        muteButton = findViewById(R.id.dialer_mute_button)
        endCallButton = findViewById(R.id.dialer_end_call_button)
        dialpadButton = findViewById(R.id.dialer_dialpad_button)

        contactImage = findViewById(R.id.dialer_contact_image)
        appIcon = findViewById(R.id.dialer_app_icon)
    }

    fun setOnProfileClickListener(listener: OnClickListener) {
        profileContainer.setOnClickListener(listener)
    }

    fun setOnMuteClickListener(listener: OnClickListener) {
        muteButton.setOnClickListener(listener)
    }

    fun setOnEndCallClickListener(listener: OnClickListener) {
        endCallButton.setOnClickListener(listener)
    }

    fun setOnDialpadClickListener(listener: OnClickListener) {
        dialpadButton.setOnClickListener(listener)
    }

    fun updateAppIcon(icon: Drawable?) {
        val container = appIcon.parent as View
        if (icon != null) {
            appIcon.setImageDrawable(icon)
            container.visibility = View.VISIBLE
        } else {
            container.visibility = View.GONE
        }
    }

    fun updateAvatar(avatarUri: Uri?, initials: String?, identifier: String?) {
        val letterTileDrawable = createLetterTile(context, initials, identifier)
        Glide.with(context.applicationContext)
            .load(avatarUri)
            .apply(RequestOptions().centerCrop().error(letterTileDrawable))
            .into(contactImage)
    }

    fun updateAudioState(isMuted: Boolean) {
        muteButton.isSelected = isMuted
    }

    companion object {
        private const val TAG = "MinimizedDialerControlsView"
    }
}
