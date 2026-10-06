package io.github.plvzfqrit.plvinyl

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.plvzfqrit.plvinyl.core.model.RecordSize
import io.github.plvzfqrit.plvinyl.ui.drawing.BackdropStyle
import io.github.plvzfqrit.plvinyl.ui.drawing.VinylFinish
import io.github.plvzfqrit.plvinyl.ui.theme.PLVinylTheme
import io.github.plvzfqrit.plvinyl.ui.turntable.TurntableScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PLVinylTheme {
                // Hard-coded until the controller exists (migration step 4).
                TurntableScreen(
                    backdrop = BackdropStyle.Charcoal,
                    finish = VinylFinish.Black,
                    record = RecordSize.LP,
                    seedKey = "PLVinyl|plvzfqrit",
                    title = "PLVinyl",
                    artist = "plvzfqrit",
                    art = null,
                    armAngleDeg = RecordSize.LP.playingAngle(0.3f),
                    armLift = 0f,
                )
            }
        }
    }
}
