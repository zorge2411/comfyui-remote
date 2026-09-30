package com.example.comfyui_remote.ui.components

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Short vibrations for sending a run and for its result (Phase 105). The app's own Vibration setting
 * decides; the system's touch-feedback setting doesn't (it was off on the test phone, which silenced
 * `View.performHapticFeedback`). Android still applies its own vibration rules, such as Do Not Disturb.
 */
class Haptics(private val vibrator: Vibrator?, private val enabled: Boolean) {

    /** A light click: Generate or Queue sent a run. */
    fun click() = play(
        predefined = VibrationEffect.EFFECT_CLICK,
        fallback = { VibrationEffect.createOneShot(20, VibrationEffect.DEFAULT_AMPLITUDE) }
    )

    /** A confirmation: the run's result arrived. */
    fun confirm() = play(
        predefined = VibrationEffect.EFFECT_DOUBLE_CLICK,
        fallback = { VibrationEffect.createWaveform(longArrayOf(0, 40, 80, 40), -1) }
    )

    private fun play(predefined: Int, fallback: () -> VibrationEffect) {
        val v = vibrator ?: return
        if (!enabled || !v.hasVibrator()) return
        val effect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            VibrationEffect.createPredefined(predefined)
        } else {
            fallback()
        }
        try {
            // Without attributes Android files a click as TOUCH, which follows the system's touch-feedback
            // switch (off on the test phone: "ignored_for_settings"). Media vibration follows only the
            // media vibration intensity, so the app's own switch decides.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                v.vibrate(effect, android.os.VibrationAttributes.createForUsage(android.os.VibrationAttributes.USAGE_MEDIA))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(
                    effect,
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                        .build()
                )
            }
        } catch (e: Exception) {
            android.util.Log.w("HAPTICS", "Vibration failed: ${e.message}")
        }
    }
}

private fun vibratorOf(context: Context): Vibrator? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

@Composable
fun rememberHaptics(enabled: Boolean): Haptics {
    val context = LocalContext.current
    return remember(context, enabled) { Haptics(vibratorOf(context), enabled) }
}
