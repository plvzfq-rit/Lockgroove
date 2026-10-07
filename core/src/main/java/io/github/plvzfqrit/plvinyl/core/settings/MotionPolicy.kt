package io.github.plvzfqrit.plvinyl.core.settings

/**
 * How often the moving parts of the turntable (the record, a long title) are redrawn:
 * most of the app's battery use, and most of the phone's warmth. A slowly turning
 * record looks the same at 30 frames a second as at 60, so it gets 30; a record at
 * 33⅓ RPM needs 60 to stay smooth.
 */
object MotionPolicy {
    /** Every frame the screen shows, at its own rate. */
    const val EVERY_FRAME = 0

    /** Spins up to this fast (degrees a second: Slow and Medium) look smooth at 30 fps. */
    const val SLOW_SPIN = 60f

    /** Frames a second for the moving parts, or [EVERY_FRAME]. */
    fun framesPerSecond(motion: MotionQuality, spinDegPerSec: Float): Int = when (motion) {
        MotionQuality.Smooth -> EVERY_FRAME
        MotionQuality.Balanced -> if (spinDegPerSec <= SLOW_SPIN) 30 else 60
        MotionQuality.Battery -> 20
    }

    /** How many times a long title goes round per song: for good, or a few times when motion is light. */
    fun titlePasses(framesPerSecond: Int): Int =
        if (framesPerSecond in 1..LIGHT_FPS) 3 else Int.MAX_VALUE

    /** At this rate or below, scrolling text steps visibly: it only goes round a few times. */
    private const val LIGHT_FPS = 20
}
