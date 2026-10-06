package io.github.plvzfqrit.plvinyl

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.plvzfqrit.plvinyl.core.model.Disc
import io.github.plvzfqrit.plvinyl.core.model.RecordSize
import io.github.plvzfqrit.plvinyl.media.MediaSessionWatcher
import io.github.plvzfqrit.plvinyl.ui.drawing.BackdropStyle
import io.github.plvzfqrit.plvinyl.ui.drawing.VinylFinish
import io.github.plvzfqrit.plvinyl.ui.onboarding.AccessGate
import io.github.plvzfqrit.plvinyl.ui.theme.PLVinylTheme
import io.github.plvzfqrit.plvinyl.ui.turntable.TurntableScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PLVinylTheme {
                // First run: explain and ask for notification access; then the turntable.
                AccessGate {
                    val song by MediaSessionWatcher.snapshot.collectAsStateWithLifecycle()
                    val arts by MediaSessionWatcher.art.collectAsStateWithLifecycle()
                    val s = song
                    // Until the controller exists (migration step 4) the record simply
                    // follows the player, and the arm rests.
                    TurntableScreen(
                        backdrop = BackdropStyle.Charcoal,
                        finish = VinylFinish.Black,
                        disc = s?.let { Disc(it.key, RecordSize.LP, it.title, it.artist, it.hasArt) },
                        art = s?.let { arts[it.key] },
                        songTitle = s?.title ?: "Nothing playing",
                        songArtist = if (s == null) "Play something in any music app" else s.artist,
                        armAngleDeg = 0f,
                        armLift = 0f,
                    )
                }
            }
        }
    }
}
