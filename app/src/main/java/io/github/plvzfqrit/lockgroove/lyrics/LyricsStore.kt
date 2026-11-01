package io.github.plvzfqrit.lockgroove.lyrics

import android.content.Context
import io.github.plvzfqrit.lockgroove.core.lyrics.LyricLine
import io.github.plvzfqrit.lockgroove.core.lyrics.SavedLyrics
import java.io.File
import java.security.MessageDigest

/**
 * Lyrics that were asked for, kept on the phone (in the app's private folder) as small .lrc files,
 * so a song's lyrics are not fetched twice. They expire after [SavedLyrics.MAX_AGE_DAYS] days (counted
 * from when they were fetched), and the ones used least recently are deleted past [MAX_FILES].
 * All calls touch the disk: use them off the main thread.
 */
class LyricsStore(context: Context) {
    private val dir = File(context.applicationContext.filesDir, "lyrics")

    /** The saved lyrics, or null if there are none or they have expired (an expired file is deleted). */
    fun read(key: String): List<LyricLine>? {
        val file = fileFor(key)
        if (!file.isFile) return null
        val now = System.currentTimeMillis()
        val saved = runCatching { SavedLyrics.parse(file.readText()) }.getOrNull()
        if (saved == null || saved.lines.isEmpty() || saved.fetchedAtMs?.let { SavedLyrics.isExpired(it, now) } == true) {
            file.delete()
            return null
        }
        if (saved.fetchedAtMs == null) file.writeText(SavedLyrics.format(now, saved.lines))   // an older file: its clock starts now
        file.setLastModified(now)                                         // used lately: keep it when trimming
        return saved.lines
    }

    fun write(key: String, lines: List<LyricLine>) {
        dir.mkdirs()
        fileFor(key).writeText(SavedLyrics.format(System.currentTimeMillis(), lines))
        files().sortedBy { it.lastModified() }.dropLast(MAX_FILES).forEach { it.delete() }
    }

    /** Delete every saved lyric that has expired. */
    fun purgeExpired() {
        val now = System.currentTimeMillis()
        for (file in files()) {
            val fetched = runCatching { SavedLyrics.parse(file.readText()).fetchedAtMs }.getOrNull()
            if (fetched != null && SavedLyrics.isExpired(fetched, now)) file.delete()
        }
    }

    /** How many songs' lyrics are saved. */
    fun count(): Int = files().size

    fun clear() {
        files().forEach { it.delete() }
    }

    private fun files(): List<File> = dir.listFiles { f -> f.isFile && f.name.endsWith(".lrc") }?.toList().orEmpty()

    private fun fileFor(key: String) = File(dir, sha1(key) + ".lrc")

    private fun sha1(text: String) =
        MessageDigest.getInstance("SHA-1").digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    private companion object {
        const val MAX_FILES = 500
    }
}
