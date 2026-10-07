package io.github.plvzfqrit.lockgroove.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import io.github.plvzfqrit.lockgroove.core.history.PlayEntry
import io.github.plvzfqrit.lockgroove.core.history.PlayLog
import io.github.plvzfqrit.lockgroove.core.history.PlayLogCodec
import io.github.plvzfqrit.lockgroove.core.history.PlaySink
import io.github.plvzfqrit.lockgroove.core.history.PlayedSong
import io.github.plvzfqrit.lockgroove.core.model.SongKey
import io.github.plvzfqrit.lockgroove.media.MediaSessionWatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.concurrent.Executors

/*
 * Every play, kept in a small file inside the app's private storage (other apps can't
 * read it; nothing is uploaded; excluded from cloud backup). The core's PlayLog holds
 * them in memory and PlayLogCodec reads and writes the lines; this only touches disk.
 * A small copy of each record's cover is kept for the stats.
 */
class PlayLogStore private constructor(context: Context) : PlaySink {
    private val dir = context.applicationContext.filesDir
    private val file = File(dir, "plays.jsonl")
    private val sleeves = File(dir, "sleeves").apply { mkdirs() }
    private val writer = Executors.newSingleThreadExecutor()

    // Small file (a year of listening is a few hundred KB): read it right away, so
    // the turntable and the background counter agree on what's been counted.
    private val log = PlayLog(read())

    private val _entries = MutableStateFlow(log.entries)

    /** All plays, oldest first. */
    val entries: StateFlow<List<PlayEntry>> = _entries.asStateFlow()

    private val _counts = MutableStateFlow(log.counts)

    /** Plays per record. */
    val counts: StateFlow<Map<SongKey, Int>> = _counts.asStateFlow()

    private fun read(): List<PlayEntry> =
        runCatching { if (file.exists()) file.useLines { PlayLogCodec.parse(it) } else emptyList() }
            .onFailure { Log.w(TAG, "couldn't read plays", it) }
            .getOrDefault(emptyList())

    private fun publish() {
        _entries.value = log.entries
        _counts.value = log.counts
    }

    override fun countPlay(song: PlayedSong, listenedMs: Long): Long {
        val (entry, new) = log.countPlay(song, listenedMs, System.currentTimeMillis())
        if (!new) return entry.id
        publish()
        val art = MediaSessionWatcher.art.value[song.key]
        val sleeveArt = art?.let { runCatching { Bitmap.createScaledBitmap(it, SLEEVE_PX, SLEEVE_PX, true) }.getOrNull() }
        writer.execute {
            append(PlayLogCodec.playLine(entry))
            // A small copy of the cover for the stats (kept once per record).
            if (sleeveArt != null) {
                val f = sleeveFile(song.key)
                if (!f.exists()) runCatching {
                    f.outputStream().use { sleeveArt.compress(Bitmap.CompressFormat.PNG, 100, it) }
                }
            }
        }
        Log.d(TAG, "play counted: ${song.key} (${log.counts[song.key]} plays)")
        return entry.id
    }

    override fun updateListened(id: Long, listenedMs: Long) {
        if (!log.updateListened(id, listenedMs)) return
        publish()
        writer.execute { append(PlayLogCodec.listenLine(id, listenedMs)) }
    }

    /** Forget every play (stats and record wear start over). Deletes the files too. */
    fun clear() {
        log.clear()
        publish()
        writer.execute {
            runCatching { file.delete() }
            runCatching { sleeves.listFiles()?.forEach { it.delete() } }
        }
    }

    /** How worn [key]'s record looks, 0..1. */
    fun wearFor(key: SongKey?): Float = log.wearFor(key)

    /** The saved cover for a record, or null. Call off the main thread. */
    fun loadSleeve(key: SongKey): Bitmap? =
        sleeveFile(key).takeIf { it.exists() }?.let { runCatching { BitmapFactory.decodeFile(it.path) }.getOrNull() }

    private fun append(line: String) {
        runCatching { file.appendText(line + "\n") }.onFailure { Log.w(TAG, "couldn't save play", it) }
    }

    private fun sleeveFile(key: SongKey) = File(sleeves, Integer.toHexString(key.value.hashCode()) + ".png")

    companion object {
        private const val TAG = "PlayLogStore"
        private const val SLEEVE_PX = 160

        @Volatile private var instance: PlayLogStore? = null

        /** One store for the turntable and the listener service. */
        fun get(context: Context): PlayLogStore =
            instance ?: synchronized(this) { instance ?: PlayLogStore(context).also { instance = it } }
    }
}
