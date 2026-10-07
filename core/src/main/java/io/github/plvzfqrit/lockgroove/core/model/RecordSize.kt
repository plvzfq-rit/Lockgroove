package io.github.plvzfqrit.lockgroove.core.model

/**
 * The two record formats. Fractions are of the RECORD's own radius unless noted.
 * Needle angles were solved from the tonearm geometry so the needle plays exactly
 * the groove band of each size, never the dead wax or the label.
 */
enum class RecordSize(
    /** Record radius as a fraction of the platter radius. */
    val radiusOfPlatter: Float,
    /** Needle angle at the start / end of the music. */
    val leadInDeg: Float,
    val runOutDeg: Float,
    /** Needle angle in the locked groove (the silent circle in the dead wax, by the label). */
    val lockedGrooveDeg: Float,
    /** Label, center hole and groove band (fractions of the record radius). */
    val label: Float,
    val hole: Float,
    val grooveIn: Float,
    val grooveOut: Float,
    /** What the SPEED switch shows, and how much faster the platter turns. */
    val speedLabel: String,
    val spinFactor: Float,
) {
    /** 12" LP at 33⅓. */
    LP(
        radiusOfPlatter = 0.985f,
        leadInDeg = 9.2f, runOutDeg = 25.7f, lockedGrooveDeg = 30.0f,
        label = 0.45f, hole = 0.028f, grooveIn = 0.605f, grooveOut = 0.955f,
        speedLabel = "33", spinFactor = 1f,
    ),

    /** 7" single at 45, small spindle hole (UK / European / Japanese style, no adapter). */
    Single(
        radiusOfPlatter = 0.985f * 7f / 12f,
        leadInDeg = 27.9f, runOutDeg = 37.1f, lockedGrooveDeg = 38.75f,
        // Same physical spindle hole as the LP, so a bigger fraction of the smaller record.
        label = 0.51f, hole = 0.028f * 12f / 7f, grooveIn = 0.62f, grooveOut = 0.955f,
        speedLabel = "45", spinFactor = 45f / 33.333f,
    );

    /** Needle angle for song progress 0..1. */
    fun playingAngle(progress: Float): Float =
        leadInDeg + (runOutDeg - leadInDeg) * progress.coerceIn(0f, 1f)

    /** Song progress 0..1 for a needle angle. */
    fun progressAt(angle: Float): Float =
        ((angle - leadInDeg) / (runOutDeg - leadInDeg)).coerceIn(0f, 1f)

    /** Real time for one turn of the platter (33⅓ or 45 RPM): the locked groove's rhythm. */
    val revolutionMs: Long get() = if (this == Single) 1333L else 1800L

    companion object {
        /** Songs up to this long come as 7" singles (in Auto mode). */
        const val SINGLE_MAX_MS = 4 * 60 * 1000L
    }
}

/** Record size setting. */
enum class RecordSizeChoice(val label: String) {
    Auto("Auto"),       // 7" for songs up to 4 min, 12" otherwise
    AlwaysLP("12\" LP"),
    AlwaysSingle("7\" single"),
}

fun RecordSizeChoice.sizeFor(durationMs: Long): RecordSize = when (this) {
    RecordSizeChoice.AlwaysLP -> RecordSize.LP
    RecordSizeChoice.AlwaysSingle -> RecordSize.Single
    RecordSizeChoice.Auto ->
        if (durationMs in 1..RecordSize.SINGLE_MAX_MS) RecordSize.Single else RecordSize.LP
}