package io.github.plvzfqrit.plvinyl.core.settings

/** How warm the phone says it is (Android's thermal status, simplified). */
enum class Heat { Cool, Warm, Hot }

/** What the phone is going through right now. */
data class PhoneState(
    val heat: Heat = Heat.Cool,
    /** The app has dimmed itself: nobody has touched it for a while. */
    val dimmed: Boolean = false,
)

/** Why the motion is lighter than chosen. */
enum class SlowDown { Hot, Warm, Dimmed }

/**
 * @param framesPerSecond for the moving parts, or [MotionPolicy.EVERY_FRAME]
 * @param titlePasses how many times a long title goes round per song
 * @param slowDown why it's lighter than the chosen motion right now (null = it isn't)
 */
data class Pace(val framesPerSecond: Int, val titlePasses: Int, val slowDown: SlowDown? = null)

/**
 * How often the moving parts of the turntable (the record, a long title) are redrawn:
 * most of the app's battery use, and most of the phone's warmth. The record turns at
 * the screen's own rate (it looks steppy any slower); a warm phone or a dimmed screen
 * gets lighter motion, and a hot phone the lightest. (Not Battery Saver: the record
 * would look janky all the time it's on.)
 */
object MotionPolicy {
    /** Every frame the screen shows, at its own rate. */
    const val EVERY_FRAME = 0

    /** Light motion: at this rate or below, scrolling text steps visibly, so it only goes round a few times. */
    private const val LIGHT_FPS = 20

    /** A hot phone: the record turns in slow steps and the title rests. */
    private const val HOT_FPS = 10

    fun pace(motion: MotionQuality, phone: PhoneState = PhoneState()): Pace {
        val chosen = when (motion) {
            MotionQuality.Smooth, MotionQuality.Balanced -> EVERY_FRAME   // Balanced holds the screen at 60 Hz
            MotionQuality.Battery -> LIGHT_FPS
        }
        val (fps, why) = when {
            phone.heat == Heat.Hot -> HOT_FPS to SlowDown.Hot
            phone.heat == Heat.Warm -> atMost(chosen, LIGHT_FPS) to SlowDown.Warm
            phone.dimmed -> atMost(chosen, LIGHT_FPS) to SlowDown.Dimmed
            else -> chosen to null
        }
        val passes = when {
            phone.heat == Heat.Hot -> 0
            fps in 1..LIGHT_FPS -> 3
            else -> Int.MAX_VALUE
        }
        return Pace(fps, passes, why.takeIf { fps != chosen })
    }

    private fun atMost(fps: Int, cap: Int) = if (fps == EVERY_FRAME || fps > cap) cap else fps
}
