package io.github.plvzfqrit.plvinyl.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.Process
import android.util.Log
import io.github.plvzfqrit.plvinyl.core.audio.CrackleGenerator
import kotlin.math.max

/*
 * Vinyl surface noise (the sound itself is the core's CrackleGenerator).
 *
 * It plays on its own quiet audio stream next to your music. It doesn't ask for
 * audio focus, so it never pauses or ducks the player. When it's not needed the
 * stream fades out and closes, so it costs nothing while silent.
 */
class SurfaceNoise {
    private val lock = Any()
    @Volatile private var target = 0f
    @Volatile private var lockedPeriodMs = 0L
    @Volatile private var generation = 0
    private var worker: Thread? = null

    /**
     * @param level 0 = silent (fades out), up to 1 = well-loved record.
     * @param lockedGrooveMs if > 0, the needle is circling the locked groove: one
     *        thump per turn, this many ms apart.
     */
    fun set(level: Float, lockedGrooveMs: Long = 0L) {
        synchronized(lock) {
            target = level.coerceIn(0f, 1f)
            lockedPeriodMs = lockedGrooveMs
            if (target > 0f && worker == null) {
                val gen = ++generation
                worker = Thread({ loop(gen) }, "SurfaceNoise").also { it.start() }
            }
        }
    }

    /** Stop at once (app going away). */
    fun release() {
        val w: Thread?
        synchronized(lock) {
            target = 0f
            generation++                    // tells the running thread to finish
            w = worker
            worker = null
        }
        w?.join(300)
    }

    private fun loop(gen: Int) {
        Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
        val track = try {
            val minBuf = AudioTrack.getMinBufferSize(RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT)
            val builder = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(RATE)
                        .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(max(minBuf, RATE / 4 * 4))       // ~250 ms
                .setTransferMode(AudioTrack.MODE_STREAM)
            // The power-saving path: big buffers, few wakeups. (A crackle a beat late is
            // fine; the music itself takes a moment to start after the needle drops.)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                builder.setPerformanceMode(AudioTrack.PERFORMANCE_MODE_POWER_SAVING)
            }
            builder.build()
        } catch (e: Exception) {
            Log.w("SurfaceNoise", "couldn't open an audio stream", e)
            synchronized(lock) { if (generation == gen) worker = null }
            return
        }

        val synth = CrackleGenerator(RATE)
        val buf = FloatArray(2048)                      // ~46 ms a write: fewer wakeups
        var quietSamples = 0
        try {
            track.play()
            while (generation == gen) {
                synth.render(buf, target, lockedPeriodMs)
                track.write(buf, 0, buf.size, AudioTrack.WRITE_BLOCKING)
                // Faded out and nothing wanted for a moment: close the stream.
                if (target == 0f && synth.gain < 1e-4f) {
                    quietSamples += buf.size
                    if (quietSamples > RATE / 3) {
                        val done = synchronized(lock) {
                            if (target == 0f) {
                                if (generation == gen) worker = null
                                true
                            } else false
                        }
                        if (done) break
                        quietSamples = 0
                    }
                } else quietSamples = 0
            }
        } finally {
            runCatching { track.pause(); track.flush() }
            track.release()
        }
    }

    private companion object {
        const val RATE = 44_100
    }
}