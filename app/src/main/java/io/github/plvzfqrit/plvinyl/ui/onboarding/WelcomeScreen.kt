package io.github.plvzfqrit.plvinyl.ui.onboarding

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.github.plvzfqrit.plvinyl.media.MediaListenerService
import io.github.plvzfqrit.plvinyl.media.MediaSessionWatcher
import io.github.plvzfqrit.plvinyl.ui.drawing.VinylFinish
import io.github.plvzfqrit.plvinyl.ui.drawing.VinylRecord
import io.github.plvzfqrit.plvinyl.ui.drawing.rememberPacedSpin

/** Has the user let PLVinyl see what's playing (notification access)? */
fun hasNowPlayingAccess(context: Context): Boolean =
    NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

/**
 * Shows the turntable once the app can see what's playing, and a short, friendly
 * explanation (plus a button to the right settings page) until then.
 * Checked again every time you come back to the app, so after flipping the switch
 * in Settings you land straight on the turntable.
 */
@Composable
fun AccessGate(content: @Composable () -> Unit) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(hasNowPlayingAccess(context)) }
    LifecycleResumeEffect(Unit) {
        granted = hasNowPlayingAccess(context)
        if (granted) MediaSessionWatcher.refresh(context)
        onPauseOrDispose { }
    }
    if (granted) content() else WelcomeScreen()
}

@Composable
private fun WelcomeScreen() {
    val context = LocalContext.current
    val bg = Color(0xFF151515)
    val fg = Color(0xFFEDE7DC)

    // A slowly turning record with a plain label: the first thing you see.
    val angle = rememberPacedSpin()

    Box(
        Modifier
            .fillMaxSize()
            .background(bg)
            .safeDrawingPadding()
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            VinylRecord(
                art = null,
                finish = VinylFinish.Black,
                seedKey = "PLVinyl|welcome",
                rotation = angle,
                title = "PLVinyl",
                artist = "Side A",
                modifier = Modifier.size(220.dp),
            )
            Spacer(Modifier.height(28.dp))
            Text(
                "Let the turntable see what's playing",
                color = fg, fontSize = 22.sp, fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "PLVinyl puts whatever you're playing in Spotify, YouTube Music or any other " +
                        "player on the record. Android only shares that through notification access, " +
                        "so that's the one permission it needs.",
                color = fg.copy(alpha = 0.75f), fontSize = 15.sp, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "It reads the song, artist, cover and position from music players, and nothing " +
                        "else. Everything stays on your phone: the app can't even use the internet.",
                color = fg.copy(alpha = 0.55f), fontSize = 13.sp, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(28.dp))
            PillButton("Turn on access", fg, filled = true) { openListenerSettings(context) }
            Spacer(Modifier.height(28.dp))
            Text(
                "Switch greyed out? Android blocks it for apps installed outside the Play Store. " +
                        "Open App info, tap ⋮ in the corner, choose \"Allow restricted settings\", then try again.",
                color = fg.copy(alpha = 0.5f), fontSize = 12.sp, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            PillButton("App info", fg, filled = false) { openAppInfo(context) }
        }
    }
}

@Composable
private fun PillButton(text: String, fg: Color, filled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Text(
        text,
        color = if (filled) Color(0xFF151515) else fg,
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(shape)
            .then(if (filled) Modifier.background(fg) else Modifier.border(1.dp, fg.copy(alpha = 0.4f), shape))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 26.dp, vertical = 12.dp),
    )
}

/** Straight to PLVinyl's own switch where Android allows it, else the list of apps. */
private fun openListenerSettings(context: Context) {
    val component = ComponentName(context, MediaListenerService::class.java)
    val direct = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
            .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, component.flattenToString())
    } else null
    val list = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
    for (intent in listOfNotNull(direct, list)) {
        val ok = runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
        if (ok) return
    }
}

private fun openAppInfo(context: Context) {
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}