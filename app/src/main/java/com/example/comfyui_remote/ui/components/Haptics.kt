package com.example.comfyui_remote.ui.components

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/**
 * Short vibrations for sending a run and for its result (Phase 105). Nothing happens when the app's
 * Vibration setting is off; `performHapticFeedback` also follows the system's touch-feedback setting.
 */
class Haptics(private val view: View, private val enabled: Boolean) {

    /** A light click: Generate or Queue sent a run. */
    fun click() {
        if (enabled) view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
    }

    /** A confirmation: the run's result arrived. */
    fun confirm() {
        if (!enabled) return
        val effect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            HapticFeedbackConstants.CONFIRM
        } else {
            HapticFeedbackConstants.LONG_PRESS
        }
        view.performHapticFeedback(effect)
    }
}

@Composable
fun rememberHaptics(enabled: Boolean): Haptics {
    val view = LocalView.current
    return remember(view, enabled) { Haptics(view, enabled) }
}
