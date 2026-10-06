package io.github.plvzfqrit.plvinyl.media

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import io.github.plvzfqrit.plvinyl.core.model.SongKey
import io.github.plvzfqrit.plvinyl.core.playback.PlayerCommand
import io.github.plvzfqrit.plvinyl.core.playback.PlayerSnapshot
import io.github.plvzfqrit.plvinyl.core.session.Choice
import io.github.plvzfqrit.plvinyl.core.session.PlayerState
import io.github.plvzfqrit.plvinyl.core.session.SessionChooser
import io.github.plvzfqrit.plvinyl.core.session.SessionInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/*
 * Watches music players for the turntable and turns what they report into
 * PlayerSnapshots. Which player to follow is decided by the core's SessionChooser.
 *
 * Battery notes: this runs all day in the background, so it does as little as
 * possible:
 *  - Only MEDIA notifications wake it (chat, email, etc. are ignored).
 *  - Bursts of updates are merged into one refresh (players often send several
 *    in a row).
 *  - Song info (with its album art, which is big) is read once per change and
 *    kept, instead of being fetched again from every player on every update.
 */
object MediaSessionWatcher {
    private const val TAG = "MediaSessionWatcher"

    /** Updates closer together than this are handled as one. */
    private const val REFRESH_COALESCE_MS = 150L

    /** How many recent covers to keep (the record on the platter can lag the player). */
    private const val ART_KEPT = 6

    private val _snapshot = MutableStateFlow<PlayerSnapshot?>(null)

    /** The followed player's latest report, or null when nothing is playing. */
    val snapshot: StateFlow<PlayerSnapshot?> = _snapshot.asStateFlow()

    private val _art = MutableStateFlow<Map<SongKey, Bitmap>>(emptyMap())

    /** Album art of the last few songs, by song. */
    val art: StateFlow<Map<SongKey, Bitmap>> = _art.asStateFlow()

    /** Called on every published snapshot (the background play counter listens). */
    @Volatile var onSnapshot: ((Context, PlayerSnapshot?) -> Unit)? = null

    /** The player session we're currently following. */
    private var followedToken: MediaSession.Token? = null

    private var appContext: Context? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val refreshRunnable = Runnable { appContext?.let { refresh(it) } }

    /** Ask for a refresh soon; several asks in a burst become one refresh. */
    fun requestRefresh(context: Context) {
        appContext = context.applicationContext
        mainHandler.removeCallbacks(refreshRunnable)
        mainHandler.postDelayed(refreshRunnable, REFRESH_COALESCE_MS)
    }

    /** Send a command to the followed player. */
    fun send(command: PlayerCommand) {
        val controls = watched?.transportControls ?: return
        when (command) {
            PlayerCommand.Play -> controls.play()
            PlayerCommand.Pause -> controls.pause()
            PlayerCommand.Next -> controls.skipToNext()
            PlayerCommand.Previous -> controls.skipToPrevious()
            is PlayerCommand.SeekTo -> controls.seekTo(command.ms)
        }
    }

    // ---- Direct updates from the chosen player ------------------------------
    // Notifications alone can arrive late (or not at all) for a pause or a seek,
    // so we also listen to the player's session itself.
    private var watched: MediaController? = null
    /** The followed player's song info, kept from its own callback (no re-reading). */
    private var watchedMetadata: MediaMetadata? = null
    private val sessionCallback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) = refreshAgain()
        override fun onMetadataChanged(metadata: MediaMetadata?) {
            watchedMetadata = metadata
            refreshAgain()
        }
        override fun onSessionDestroyed() = refreshAgain()
    }

    // Repeat mode isn't in Android's basic media API, but players that use the
    // AndroidX media library (Spotify and most others) expose it through this.
    private var watchedCompat: MediaControllerCompat? = null
    private val compatCallback = object : MediaControllerCompat.Callback() {
        override fun onRepeatModeChanged(repeatMode: Int) = refreshAgain()
    }

    private fun refreshAgain() {
        appContext?.let { requestRefresh(it) }
    }

    private fun watch(controller: MediaController?) {
        if (controller?.sessionToken == watched?.sessionToken) return
        watched?.unregisterCallback(sessionCallback)
        watchedCompat?.unregisterCallback(compatCallback)
        watched = controller
        watchedMetadata = controller?.metadata                          // read once
        controller?.registerCallback(sessionCallback, mainHandler)
        val context = appContext
        watchedCompat = if (controller != null && context != null) runCatching {
            MediaControllerCompat(context, MediaSessionCompat.Token.fromToken(controller.sessionToken))
                .also { it.registerCallback(compatCallback, mainHandler) }
        }.getOrNull() else null
    }

    /** Song info for a session: the kept copy for the one we follow, else ask the player. */
    private fun metadataOf(c: MediaController): MediaMetadata? =
        if (c.sessionToken == watched?.sessionToken) watchedMetadata ?: c.metadata.also { watchedMetadata = it }
        else c.metadata

    /** True if the followed player is on "repeat one" (false if it doesn't say). */
    private fun isRepeatOne(): Boolean =
        runCatching { watchedCompat?.repeatMode == PlaybackStateCompat.REPEAT_MODE_ONE }.getOrDefault(false)

    // Players give us a NEW Bitmap object on every update, even for the same cover.
    // Reusing one instance per song stops everything that depends on the art
    // (record bake, colors, marble, background blur) from rebuilding over and over.
    private var lastArtKey: String? = null
    private var lastArt: Bitmap? = null

    private fun stableArt(key: String, fresh: Bitmap?): Bitmap? {
        val previous = lastArt
        val art = when {
            key != lastArtKey -> fresh                                  // new song
            fresh == null -> previous                                   // art briefly missing
            previous == null -> fresh                                   // art just arrived
            fresh.width == previous.width && fresh.height == previous.height -> previous
            else -> fresh                                               // real art change
        }
        lastArtKey = key
        lastArt = art
        return art
    }

    // -------------------------------------------------------------------------

    /** Look at every player now and publish what the followed one is doing. */
    fun refresh(context: Context) {
        appContext = context.applicationContext
        mainHandler.removeCallbacks(refreshRunnable)                    // this IS the refresh
        val manager = context.getSystemService(MediaSessionManager::class.java)
        val component = ComponentName(context, MediaListenerService::class.java)
        val sessions = runCatching { manager.getActiveSessions(component) }
            .getOrElse {
                Log.w(TAG, "no access to media sessions", it)
                return
            }

        // Read each player's state once (small), and song info only where needed.
        val states = sessions.associateWith { it.playbackState }
        val metadata = HashMap<MediaController, MediaMetadata?>()
        fun meta(c: MediaController) = metadata.getOrPut(c) { metadataOf(c) }
        val infos = sessions.map { c ->
            SessionInfo(c.sessionToken, states[c]?.state?.toPlayerState(), states[c]?.lastPositionUpdateTime ?: 0L) {
                meta(c)?.getString(MediaMetadata.METADATA_KEY_TITLE) != null
            }
        }
        val chosen = when (val choice = SessionChooser.choose(infos, followedToken)) {
            Choice.Keep -> {
                Log.d(TAG, "followed session mid-update, keeping current song")
                return
            }
            Choice.NothingPlaying -> null
            is Choice.Follow -> sessions.first { it.sessionToken == choice.token }
        }
        followedToken = chosen?.sessionToken
        watch(chosen)

        if (chosen == null) {
            publish(context, null, null)
            return
        }

        val packageName = chosen.packageName
        val meta = meta(chosen)
        val playback = states[chosen]
        val title = meta?.getString(MediaMetadata.METADATA_KEY_TITLE)
        val artist = meta?.getString(MediaMetadata.METADATA_KEY_ARTIST)
        val key = SongKey.of(title, artist)
        val artKey = "$packageName|$key"
        // Only touch the (big) art when the song changed or we don't have it yet.
        val fresh = if (artKey != lastArtKey || lastArt == null)
            meta?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) ?: meta?.getBitmap(MediaMetadata.METADATA_KEY_ART)
        else lastArt
        val art = stableArt(artKey, fresh)
        val state = playback?.state?.toPlayerState()

        // Position only moves while actually PLAYING (not buffering/connecting),
        // so our estimate can't drift ahead of the song during a network hiccup.
        val speed = if (state == PlayerState.Playing) playback.playbackSpeed.takeIf { it > 0f } ?: 1f else 0f
        val snapshot = PlayerSnapshot(
            key = key,
            title = title,
            artist = artist,
            player = packageName,
            sounding = state?.isSounding == true,
            positionMs = playback?.position ?: 0L,
            at = playback?.lastPositionUpdateTime ?: 0L,
            speed = speed,
            durationMs = (meta?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L).coerceAtLeast(0L),
            repeatOne = isRepeatOne(),
            hasArt = art != null,
        )
        Log.d(TAG, "[$packageName] $title by $artist, art=${art != null}, sounding=${snapshot.sounding}, " +
                "pos=${snapshot.positionMs}/${snapshot.durationMs}")
        publish(context, snapshot, art)
    }

    private fun publish(context: Context, snapshot: PlayerSnapshot?, art: Bitmap?) {
        if (snapshot != null && art != null && _art.value[snapshot.key] !== art) {
            // Most recent last; only the last few songs are kept.
            val kept = LinkedHashMap(_art.value)
            kept.remove(snapshot.key)
            kept[snapshot.key] = art
            while (kept.size > ART_KEPT) kept.remove(kept.keys.first())
            _art.value = kept
        }
        _snapshot.value = snapshot
        onSnapshot?.invoke(context.applicationContext, snapshot)
    }

    private fun Int.toPlayerState(): PlayerState = when (this) {
        PlaybackState.STATE_NONE -> PlayerState.None
        PlaybackState.STATE_STOPPED -> PlayerState.Stopped
        PlaybackState.STATE_ERROR -> PlayerState.Error
        PlaybackState.STATE_PLAYING -> PlayerState.Playing
        PlaybackState.STATE_PAUSED -> PlayerState.Paused
        PlaybackState.STATE_BUFFERING -> PlayerState.Buffering
        PlaybackState.STATE_CONNECTING -> PlayerState.Connecting
        PlaybackState.STATE_FAST_FORWARDING -> PlayerState.FastForwarding
        PlaybackState.STATE_REWINDING -> PlayerState.Rewinding
        PlaybackState.STATE_SKIPPING_TO_NEXT -> PlayerState.SkippingToNext
        PlaybackState.STATE_SKIPPING_TO_PREVIOUS -> PlayerState.SkippingToPrevious
        PlaybackState.STATE_SKIPPING_TO_QUEUE_ITEM -> PlayerState.SkippingToQueueItem
        else -> PlayerState.Other
    }
}
