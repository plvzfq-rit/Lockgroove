package io.github.plvzfqrit.lockgroove.lyrics

import android.icu.text.Transliterator
import android.os.SystemClock
import android.util.Log
import com.atilika.kuromoji.ipadic.Tokenizer
import io.github.plvzfqrit.lockgroove.core.lyrics.LyricReading
import java.io.ByteArrayInputStream

/**
 * Reads Japanese with Kuromoji (a dictionary-based analyzer, Apache-2.0, using the IPADIC
 * dictionary). Each word is looked up IN CONTEXT, so a kanji gets the reading it has in that
 * sentence. The dictionary is big, so it is only loaded when a Japanese line first needs it, and
 * [release] lets it go again.
 */
internal object KuromojiReader : JapaneseReader {
    private const val TAG = "Kuromoji"

    private var tokenizer: Tokenizer? = null

    /** Katakana to plain Latin letters (the long mark ー becomes no mark: トーキョー is "tokyo"). */
    private val kana: Transliterator? by lazy {
        runCatching { Transliterator.getInstance("Katakana-Latin; Latin-ASCII") }.getOrNull()
    }

    @Synchronized
    override fun romaji(text: String): String? {
        val reader = tokenizer ?: load()
        return try {
            val parts = reader.tokenize(text).map { token ->
                // "pronunciation" is how it is SAID (the particle は is "wa"); "*" means the dictionary has none.
                val katakana = token.pronunciation.takeIf { it != "*" } ?: token.reading.takeIf { it != "*" }
                if (katakana != null) kana?.transliterate(katakana) ?: katakana else token.surface
            }
            LyricReading.join(parts).takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            Log.d(TAG, "could not read \"$text\": $e")
            null
        }
    }

    @Synchronized
    override fun release() {
        if (tokenizer != null) Log.d(TAG, "dictionary released")
        tokenizer = null
    }

    private fun load(): Tokenizer {
        val start = SystemClock.elapsedRealtime()
        // The general dictionary plus our own fixes for words it reads wrongly (二人 is "futari").
        val loaded: Tokenizer = Tokenizer.Builder()
            .userDictionary(ByteArrayInputStream(IrregularReadings.dictionary.toByteArray(Charsets.UTF_8)))
            .build()
        Log.d(TAG, "dictionary ready in ${SystemClock.elapsedRealtime() - start} ms")
        tokenizer = loaded
        return loaded
    }
}
