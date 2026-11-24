package com.example.gymtime.ui.theme

import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalView

/**
 * Calibrated API 34+ tactile feedback engine wrapping [View.performHapticFeedback].
 * Never bypasses OS accessibility / touch-feedback settings (`FLAG_IGNORE_GLOBAL_SETTING` is never passed).
 */
@Immutable
class IronHaptics(
    private val view: View? = null,
    val enabled: Boolean = true
) {
    fun keyTap() = perform(HapticFeedbackConstants.KEYBOARD_TAP)

    fun tick() = perform(HapticFeedbackConstants.CLOCK_TICK)

    fun frequentTick() = perform(HapticFeedbackConstants.SEGMENT_FREQUENT_TICK)

    fun segmentTick() = perform(HapticFeedbackConstants.SEGMENT_TICK)

    fun toggle(enabled: Boolean) = perform(
        if (enabled) HapticFeedbackConstants.TOGGLE_ON else HapticFeedbackConstants.TOGGLE_OFF
    )

    fun confirm() = perform(HapticFeedbackConstants.CONFIRM)

    fun reject() = perform(HapticFeedbackConstants.REJECT)

    fun thresholdActivate() = perform(HapticFeedbackConstants.GESTURE_THRESHOLD_ACTIVATE)

    fun longPress() = perform(HapticFeedbackConstants.LONG_PRESS)

    private fun perform(constant: Int) {
        if (!enabled) return
        val targetView = view ?: return
        runCatching { targetView.performHapticFeedback(constant) }
    }

    companion object {
        val NoOp = IronHaptics(view = null, enabled = false)
    }
}

val LocalIronHaptics = staticCompositionLocalOf { IronHaptics.NoOp }

@Composable
fun rememberIronHaptics(enabled: Boolean = true): IronHaptics {
    val view = LocalView.current
    return remember(view, enabled) {
        IronHaptics(view = view, enabled = enabled)
    }
}
