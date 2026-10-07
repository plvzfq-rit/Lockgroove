package io.github.plvzfqrit.lockgroove.core.model

/**
 * A record on (or headed for) the platter. The album art itself stays in the app;
 * the core only needs to know whether there is any.
 */
data class Disc(
    val key: SongKey,
    val size: RecordSize = RecordSize.LP,
    val title: String? = null,
    val artist: String? = null,
    /** False = the record gets a plain printed label (title and artist). */
    val hasArt: Boolean = false,
)
