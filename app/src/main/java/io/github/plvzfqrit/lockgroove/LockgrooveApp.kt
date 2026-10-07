package io.github.plvzfqrit.lockgroove

import android.app.Application
import io.github.plvzfqrit.lockgroove.data.Plays

class LockgrooveApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Plays.install(this)
    }
}
