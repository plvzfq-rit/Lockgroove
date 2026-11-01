package io.github.plvzfqrit.lockgroove.lyrics

import android.content.Context
import io.github.plvzfqrit.lockgroove.core.lyrics.LrcParser
import io.github.plvzfqrit.lockgroove.core.lyrics.LrcWriter
import io.github.plvzfqrit.lockgroove.core.lyrics.LyricLine
import java.io.File
import java.security.MessageDigest

/**
 * Lyrics that were asked for, kept on the phone (in the app's private folder) as small .lrc files,
 * so a song's lyrics are never fetched twice. The oldest are deleted past [MAX_FILES].
 * All calls touch the disk: use them off the main thread.
 */
class LyricsStore(context: Context) {
    private val dir = File(context.applicationContext.filesDir, "lyrics")

    fun read(key: String): List<LyricLine>? {
        val file = fileFor(key)
        if (!file.isFile) return null
        file.setLastModified(System.currentTimeMillis())                  // used lately: keep it
        return runCatching { LrcParser.parse(file.readText()) }.getOrNull()?.takeIf { it.isNotEmpty() }
    }

    fun write(key: String, lines: List<LyricLine>) {
        dir.mkdirs()
        fileFor(key).writeText(LrcWriter.format(lines))
        files().sortedBy { it.lastModified() }.dropLast(MAX_FILES).forEach { it.delete() }
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
