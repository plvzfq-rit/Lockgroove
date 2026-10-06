package io.github.plvzfqrit.plvinyl

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
                    TurntableScreen(backdrop = BackdropStyle.Charcoal, finish = VinylFinish.Black)
                }
            }
        }
    }
}
