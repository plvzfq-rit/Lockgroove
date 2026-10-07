package io.github.plvzfqrit.lockgroove.ui.common

import android.os.Build
import android.os.PowerManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import io.github.plvzfqrit.lockgroove.core.settings.Heat
import io.github.plvzfqrit.lockgroove.core.settings.PhoneState

/**
 * How warm the phone is, kept up to date while this is on screen. (The phone's warmth
 * is only reported from Android 10; before that, Cool.)
 *
 * @param dimmed whether the app has dimmed itself (passed through)
 */
@Composable
fun rememberPhoneState(dimmed: Boolean): PhoneState {
    val context = LocalContext.current
    val power = remember(context) { context.getSystemService(PowerManager::class.java) }
    var heat by remember { mutableStateOf(Heat.Cool) }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        DisposableEffect(power) {
            val listener = PowerManager.OnThermalStatusChangedListener { heat = heatOf(it) }
            heat = heatOf(power.currentThermalStatus)
            power.addThermalStatusListener(context.mainExecutor, listener)
            onDispose { power.removeThermalStatusListener(listener) }
        }
    }
    return PhoneState(heat, dimmed)
}

/** Android's thermal status: moderate is warm (it starts slowing the phone down), severe and up is hot. */
private fun heatOf(status: Int): Heat = when {
    status >= PowerManager.THERMAL_STATUS_SEVERE -> Heat.Hot
    status >= PowerManager.THERMAL_STATUS_MODERATE -> Heat.Warm
    else -> Heat.Cool
}
