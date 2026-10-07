package io.github.plvzfqrit.lockgroove

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.plvzfqrit.lockgroove.ui.onboarding.AccessGate
import io.github.plvzfqrit.lockgroove.ui.theme.LockgrooveTheme
import io.github.plvzfqrit.lockgroove.ui.turntable.TurntableScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LockgrooveTheme {
                // First run: explain and ask for notification access; then the turntable.
                AccessGate {
                    TurntableScreen()
                }
            }
        }
    }
}
