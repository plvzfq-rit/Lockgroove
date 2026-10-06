package io.github.plvzfqrit.plvinyl

import android.app.Application
import io.github.plvzfqrit.plvinyl.data.Plays

class PLVinylApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Plays.install(this)
    }
}
