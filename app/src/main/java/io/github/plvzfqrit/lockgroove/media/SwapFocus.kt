package io.github.plvzfqrit.lockgroove.media

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * Holds the speaker while we skip to the next record, so a player that auto-plays after
 * "next" has to wait for the needle instead of leaking a blip of the new song.
 * Let go just before the needle's Play, or after [HOLD_LIMIT_MS] if that never comes.
 */
class SwapFocus(context: Context) {
    private val audio = context.getSystemService(AudioManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val autoRelease = Runnable { release() }
    private var request: AudioFocusRequest? = null

    fun hold() {
        handler.removeCallbacks(autoRelease)
        handler.postDelayed(autoRelease, HOLD_LIMIT_MS)             // every skip restarts the clock
        if (request != null) return
        val wanted = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            .setOnAudioFocusChangeListener { }                      // we play nothing, so nothing to react to
            .build()
        if (audio.requestAudioFocus(wanted) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) request = wanted
        Log.d(TAG, "holding audio focus: ${request != null}")
    }

    fun release() {
        handler.removeCallbacks(autoRelease)
        val held = request ?: return
        request = null
        audio.abandonAudioFocusRequest(held)
        Log.d(TAG, "released audio focus")
    }

    private companion object {
        const val TAG = "MediaSessionWatcher"                        // same tag, so one log filter shows it all
        const val HOLD_LIMIT_MS = 10_000L
    }
}
