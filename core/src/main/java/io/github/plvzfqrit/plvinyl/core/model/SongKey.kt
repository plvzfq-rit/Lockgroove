package io.github.plvzfqrit.plvinyl.core.model

/**
 * Identifies a song as "title|artist". A value class, so a typo can't build two
 * different keys for the same song: there's only one way to make one.
 */
@JvmInline
value class SongKey private constructor(val value: String) {
    override fun toString() = value

    companion object {
        fun of(title: String?, artist: String?) = SongKey("$title|$artist")

        /** A key read back from storage, already in "title|artist" form. */
        fun parse(value: String) = SongKey(value)
    }
}
