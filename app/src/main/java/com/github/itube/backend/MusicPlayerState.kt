package com.github.itube

import android.content.Context
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.core.content.ContextCompat
import com.github.itube.backend.EqualizerManager
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import coil.imageLoader
import coil.request.ImageRequest
import com.github.itube.backend.SafeUnbindContext
import com.github.itube.backend.lyrics.LyricsHelper
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Dedicated music playback controller and state machine.
 * Completely independent from the video player.
 */
object MusicPlayerState : Player.Listener {
    private const val TAG = "MusicPlayerState"
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val mainHandler = Handler(Looper.getMainLooper())

    private var exoPlayer: ExoPlayer? = null
    private var appContext: Context? = null
    private var progressJob: Job? = null
    private var lyricsJob: Job? = null
    private var loadJob: Job? = null
    /** True while a new URL is being extracted, before ExoPlayer has a buffering state. */
    private var resolvingStream = false
    private var hasRecorded30sProgress = false
    private var hasConfirmedCurrentTrack = false

    // ── Observable UI States ──
    var currentTrack by mutableStateOf<MusicTrack?>(null)
        private set

    val queue = mutableStateListOf<MusicTrack>()

    var queueIndex by mutableIntStateOf(0)
        private set

    var isPlaying by mutableStateOf(false)
        private set

    var isLoading by mutableStateOf(false)
        private set

    var positionMs by mutableLongStateOf(0L)
        private set

    var durationMs by mutableLongStateOf(0L)
        private set

    var volume by mutableFloatStateOf(1f)
        private set

    var shuffleEnabled by mutableStateOf(false)
        private set

    var repeatMode by mutableIntStateOf(Player.REPEAT_MODE_OFF)
        private set

    var playbackSpeed by mutableFloatStateOf(1f)
        private set

    var holdingFast by mutableStateOf(false)
        private set

    private var preHoldSpeed: Float = 1f
    private var preHoldPlaying: Boolean = false

    // Sheet / Dialog states
    var expanded by mutableStateOf(false)
    var queueOpen by mutableStateOf(false)
    var lyricsOpen by mutableStateOf(false)

    // Video Mode & Companion Video Player
    var isVideoMode by mutableStateOf(false)
    var isVideoFullscreen by mutableStateOf(false)
    var videoFullscreenZoomMode by mutableIntStateOf(0)
    var selectedVideoQuality by mutableStateOf<Int?>(480)
    var companionVideoPlayer: ExoPlayer? = null
        private set

    fun getOrCreateVideoPlayer(context: Context): ExoPlayer {
        if (companionVideoPlayer == null) {
            val appCtx = context.applicationContext
            val httpDataSourceFactory = OkHttpDataSource.Factory(Net.client)
                .setUserAgent(Net.USER_AGENT)
            val dataSourceFactory = DefaultDataSource.Factory(appCtx, httpDataSourceFactory)
            companionVideoPlayer = ExoPlayer.Builder(appCtx)
                .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
                .setLooper(Looper.getMainLooper())
                .build().apply {
                    volume = 0f
                    repeatMode = Player.REPEAT_MODE_OFF
                }
        }
        return companionVideoPlayer!!
    }

    fun releaseVideoPlayer() {
        companionVideoPlayer?.release()
        companionVideoPlayer = null
        isVideoMode = false
        isVideoFullscreen = false
        videoFullscreenZoomMode = 0
    }

    var lyrics by mutableStateOf<List<LyricsLine>>(emptyList())
        private set

    var lyricsLoading by mutableStateOf(false)
        private set

    var onPlaybackStarted: (() -> Unit)? = null

    fun init(context: Context) {
        if (exoPlayer != null) return
        val appContext = context.applicationContext
        this.appContext = appContext

        val httpDataSourceFactory = OkHttpDataSource.Factory(Net.client)
            .setUserAgent(Net.USER_AGENT)
        val dataSourceFactory = DefaultDataSource.Factory(appContext, httpDataSourceFactory)

        exoPlayer = ExoPlayer.Builder(appContext)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .setLooper(Looper.getMainLooper())
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
            .apply {
                addListener(this@MusicPlayerState)
                volume = this@MusicPlayerState.volume
            }

        // Keep the media session connected while the app is open. This is deliberately a bind,
        // not a foreground-service start; Media3 promotes the service only for real playback.
        ensureSystemMediaSession()
        exoPlayer?.audioSessionId?.let { id ->
            if (id > 0) EqualizerManager.bindAudioSession(id)
        }
        startProgressLoop()
    }

    private var sessionPlayer: Player? = null

    /** The service publishes this player wrapper through MediaSession; it routes seekToNext/Previous to MusicPlayerState queue navigation. */
    fun playerForSession(context: Context): Player {
        init(context)
        val base = requireNotNull(exoPlayer)
        val existing = sessionPlayer
        if (existing != null) return existing
        val wrapper = MusicSessionForwardingPlayer(base)
        sessionPlayer = wrapper
        return wrapper
    }

    private var musicControllerFuture: ListenableFuture<MediaController>? = null
    private var musicController: MediaController? = null

    fun onServiceDestroyed() {
        sessionPlayer = null
        musicController = null
        musicControllerFuture = null
    }

    private fun ensureSystemMediaSession() {
        val context = appContext ?: return
        if (musicControllerFuture != null || musicController != null) return
        val safeContext = SafeUnbindContext(context)
        val token = SessionToken(safeContext, ComponentName(safeContext, MusicPlaybackService::class.java))
        val future = MediaController.Builder(safeContext, token).buildAsync()
        musicControllerFuture = future
        future.addListener({
            runCatching { future.get() }
                .onSuccess { controller -> musicController = controller }
                .onFailure { error ->
                    musicControllerFuture = null
                    musicController = null
                    Log.e(TAG, "Failed to connect MusicPlaybackService", error)
                }
        }, ContextCompat.getMainExecutor(safeContext))
    }

    private fun stopSystemMediaSession() {
        try {
            musicControllerFuture?.let(MediaController::releaseFuture)
        } catch (e: Exception) {
            Log.e(TAG, "Failed releasing MusicPlaybackService controller", e)
        } finally {
            musicControllerFuture = null
            musicController = null
        }
    }

    private fun startProgressLoop() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive) {
                exoPlayer?.let { player ->
                    positionMs = player.currentPosition.coerceAtLeast(0L)
                    val dur = player.duration
                    if (dur > 0) {
                        durationMs = dur
                    }
                    isPlaying = player.isPlaying
                    // Never leave a spinner over audio that is already audible. Some streams
                    // report a buffering transition while their buffered audio is playing.
                    isLoading = !player.isPlaying && (resolvingStream || player.playbackState == Player.STATE_BUFFERING)

                    if (isPlaying && !hasConfirmedCurrentTrack && positionMs >= 15_000L) {
                        hasConfirmedCurrentTrack = true
                        currentTrack?.let { ListeningProfile.onTrackConfirmed(it) }
                    }

                    if (positionMs >= 30_000L && !hasRecorded30sProgress) {
                        hasRecorded30sProgress = true
                        currentTrack?.let { ListeningProfile.onTrackCompleted(it) }
                    }
                }
                delay(200)
            }
        }
    }

    fun play(track: MusicTrack, newQueue: List<MusicTrack>? = null) {
        onPlaybackStarted?.invoke()
        expanded = true
        loadJob?.cancel()
        hasRecorded30sProgress = false
        hasConfirmedCurrentTrack = false

        val isLiked = LikedSongsStore.isLiked(track.id) || ListeningProfile.isLiked(track.id)
        val resolvedTrack = if (track.liked != isLiked) track.copy(liked = isLiked) else track

        // A new selection is a hard handoff: never let the previous song keep playing while
        // its replacement URL is being extracted.
        exoPlayer?.let { player ->
            player.playWhenReady = false
            player.pause()
        }
        resolvingStream = true
        isPlaying = false
        isLoading = true
        positionMs = 0L
        durationMs = 0L
        currentTrack = resolvedTrack
        ListeningProfile.onTrackPlaybackStarted(resolvedTrack)
        // Pre-warm the ambient glow thumbnail in Coil cache immediately so player background renders on frame 1
        appContext?.let { ctx ->
            val thumb = resolvedTrack.thumbnailUrl?.highQualityThumbnail() ?: resolvedTrack.thumbnailUrl
            if (!thumb.isNullOrBlank()) {
                val preReq = ImageRequest.Builder(ctx)
                    .data(thumb)
                    .allowHardware(false)
                    .size(160)
                    .build()
                ctx.imageLoader.enqueue(preReq)
            }
        }
        // Match the video player: a MediaController connection lets Media3 publish its own
        // native Android media card when this player reaches a playable state.
        ensureSystemMediaSession()
        loadJob = scope.launch {
            try {
                // If an explicit new queue is provided and it's distinct from the current queue, replace it defensively
                if (newQueue != null && newQueue !== queue) {
                    val snapshot = newQueue.toList()
                    queue.clear()
                    queue.addAll(snapshot)
                    val idx = queue.indexOfFirst { it.id == track.id }
                    queueIndex = if (idx >= 0) idx else 0
                } else {
                    // Playing within existing queue or single track
                    val idx = queue.indexOfFirst { it.id == track.id }
                    if (idx >= 0) {
                        queueIndex = idx
                    } else {
                        queue.add(track)
                        queueIndex = queue.lastIndex
                    }
                }
                MusicPlaybackService.refreshCustomLayout()

                // Pause video player if currently playing
                pauseVideoPlayerIfPlaying()

                // Fetch lyrics in background
                fetchLyricsForCurrentTrack(track)

                // 1. Check if track is already cached offline!
                val cachedFile = MusicCacheManager.getCachedAudioFile(track.id)
                if (cachedFile != null && cachedFile.exists() && cachedFile.length() > 0) {
                    Log.d(TAG, "Playing track ${track.id} directly from offline audio cache: ${cachedFile.absolutePath}")
                    MusicCacheManager.markTrackPlayed(track.id)
                    exoPlayer?.let { player ->
                        ensureSystemMediaSession()
                        val mediaItem = MediaItem.Builder()
                            .setMediaId(track.id)
                            .setUri(Uri.fromFile(cachedFile))
                            .setMediaMetadata(
                                MediaMetadata.Builder()
                                    .setTitle(track.title)
                                    .setArtist(track.artists)
                                    .setAlbumTitle(track.album)
                                    .setArtworkUri(track.thumbnailUrl?.let(Uri::parse))
                                    .build()
                            )
                            .build()
                        player.setMediaItem(mediaItem)
                        player.prepare()
                        player.setPlaybackSpeed(if (holdingFast) 2f else playbackSpeed)
                        player.playWhenReady = true
                        player.play()
                        resolvingStream = false
                        isLoading = false
                    }
                    if (newQueue == null) {
                        scope.launch(Dispatchers.IO) {
                            val radio = runCatching { MusicBackend.relatedTracks(track) }.getOrDefault(emptyList())
                            withContext(Dispatchers.Main) {
                                if (currentTrack?.id == track.id && queue.size <= 1 && radio.isNotEmpty()) {
                                    queue.clear()
                                    queue.add(track)
                                    queue.addAll(radio)
                                    queueIndex = 0
                                    MusicPlaybackService.refreshCustomLayout()
                                    preCacheNextTrack()
                                }
                            }
                        }
                    } else {
                        preCacheNextTrack()
                    }
                    return@launch
                }

                // 2. Not cached: resolve progressive audio stream online
                val streamUrl = MusicBackend.resolveAudioStreamUrl(track.id)
                if (streamUrl.isNullOrBlank()) {
                    Log.e(TAG, "Failed to resolve stream URL for track: ${track.id}")
                    resolvingStream = false
                    isLoading = false
                    return@launch
                }

                // 3. Live-cache audio in background while playing
                MusicCacheManager.startLiveCaching(track, streamUrl)

                exoPlayer?.let { player ->
                    // Normally already bound from init; retain this call for a service that the
                    // system reclaimed while the app stayed open.
                    ensureSystemMediaSession()
                    val mediaItem = MediaItem.Builder()
                        .setMediaId(track.id)
                        .setUri(Uri.parse(streamUrl))
                        .setMediaMetadata(
                            MediaMetadata.Builder()
                                .setTitle(track.title)
                                .setArtist(track.artists)
                                .setAlbumTitle(track.album)
                                .setArtworkUri(track.thumbnailUrl?.let(Uri::parse))
                                .build()
                        )
                        .build()
                    player.setMediaItem(mediaItem)
                    player.prepare()
                    player.setPlaybackSpeed(if (holdingFast) 2f else playbackSpeed)
                    player.playWhenReady = true
                    player.play()
                    resolvingStream = false
                    // A tap on a single song becomes a radio based on that song, not a random
                    // leftover queue. Do this after playback starts so loading audio is never held up.
                    if (newQueue == null) {
                        scope.launch(Dispatchers.IO) {
                            val radio = MusicBackend.relatedTracks(track)
                            withContext(Dispatchers.Main) {
                                if (currentTrack?.id == track.id && queue.size <= 1 && radio.isNotEmpty()) {
                                    queue.clear()
                                    queue.add(track)
                                    queue.addAll(radio)
                                    queueIndex = 0
                                    MusicPlaybackService.refreshCustomLayout()
                                    preCacheNextTrack()
                                }
                            }
                        }
                    } else {
                        preCacheNextTrack()
                    }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Log.e(TAG, "Error playing music track", e)
                if (currentTrack?.id == track.id) {
                    resolvingStream = false
                    isLoading = false
                }
            }
        }
    }

    private fun fetchLyricsForCurrentTrack(track: MusicTrack) {
        lyricsJob?.cancel()
        lyrics = emptyList()
        lyricsLoading = true
        lyricsJob = scope.launch {
            val list = LyricsHelper.loadLyrics(
                songId = track.id,
                title = track.title,
                artist = track.artists,
                durationSec = track.durationSec.toInt()
            )
            lyrics = list
            lyricsLoading = false
        }
    }

    /** Re-fetch lyrics for the current song without interrupting audio playback. */
    fun refreshLyrics() {
        currentTrack?.let(::fetchLyricsForCurrentTrack)
    }

    fun pause() {
        exoPlayer?.let { player ->
            if (player.isPlaying) {
                player.pause()
            }
            isPlaying = player.isPlaying
        }
        companionVideoPlayer?.pause()
    }

    fun resume() {
        exoPlayer?.let { player ->
            if (!player.isPlaying && currentTrack != null) {
                onPlaybackStarted?.invoke()
                pauseVideoPlayerIfPlaying()
                player.play()
            }
            isPlaying = player.isPlaying
        }
        if (isVideoMode) {
            companionVideoPlayer?.play()
        }
    }

    fun togglePlayPause() {
        val cur = currentTrack
        exoPlayer?.let { player ->
            if (player.playbackState == Player.STATE_IDLE && cur != null) {
                play(cur, queue)
                return
            }
            if (player.isPlaying) {
                player.pause()
                companionVideoPlayer?.pause()
            } else {
                onPlaybackStarted?.invoke()
                pauseVideoPlayerIfPlaying()
                player.play()
                if (isVideoMode) {
                    companionVideoPlayer?.play()
                }
            }
            isPlaying = player.isPlaying
        }
    }

    fun close() {
        loadJob?.cancel()
        lyricsJob?.cancel()
        exoPlayer?.let { player ->
            player.stop()
            player.clearMediaItems()
        }
        companionVideoPlayer?.let { vp ->
            vp.stop()
            vp.clearMediaItems()
        }
        currentTrack = null
        queue.clear()
        queueIndex = 0
        isPlaying = false
        holdingFast = false
        isLoading = false
        positionMs = 0L
        durationMs = 0L
        expanded = false
        queueOpen = false
        lyricsOpen = false
        lyrics = emptyList()
        lyricsLoading = false
        isVideoMode = false
        isVideoFullscreen = false
        videoFullscreenZoomMode = 0
        stopSystemMediaSession()
    }

    fun seekTo(posMs: Long) {
        positionMs = posMs
        exoPlayer?.seekTo(posMs)
        if (isVideoMode) {
            companionVideoPlayer?.seekTo(posMs)
        }
    }

    fun skipNext() {
        if (queue.size <= 1) return
        val nextIndex = if (shuffleEnabled) {
            (queue.indices.filter { it != queueIndex }).randomOrNull() ?: return
        } else {
            val sequential = queueIndex + 1
            when {
                sequential < queue.size -> sequential
                repeatMode == Player.REPEAT_MODE_ALL -> 0
                else -> return
            }
        }
        val nextTrack = queue.getOrNull(nextIndex) ?: return
        queueIndex = nextIndex
        play(nextTrack, null)
    }

    fun skipPrevious() {
        if (queue.isEmpty()) return
        // If already a few seconds in, restart current track
        if (positionMs > 3000L) {
            seekTo(0L)
            return
        }
        if (queue.size <= 1) return
        val prevIndex = when {
            shuffleEnabled -> (queue.indices.filter { it != queueIndex }).randomOrNull() ?: return
            queueIndex > 0 -> queueIndex - 1
            repeatMode == Player.REPEAT_MODE_ALL -> queue.size - 1
            else -> return
        }
        val prevTrack = queue.getOrNull(prevIndex) ?: return
        queueIndex = prevIndex
        play(prevTrack, null)
    }

    fun toggleLike() {
        val cur = currentTrack ?: return
        val newLiked = !cur.liked
        ListeningProfile.recordLike(cur, newLiked)
        if (newLiked) {
            LikedSongsStore.add(cur)
        } else {
            LikedSongsStore.remove(cur.id)
        }
        val updated = cur.copy(liked = newLiked)
        currentTrack = updated
        val qIdx = queue.indexOfFirst { it.id == cur.id }
        if (qIdx >= 0) {
            queue[qIdx] = updated
        }
    }

    fun adjustVolume(vol: Float) {
        volume = vol.coerceIn(0f, 1f)
        exoPlayer?.volume = volume
    }

    fun toggleShuffle() {
        shuffleEnabled = !shuffleEnabled
        exoPlayer?.let { it.shuffleModeEnabled = shuffleEnabled }
            ?: MusicPlaybackService.refreshCustomLayout()
    }

    fun toggleRepeat() {
        repeatMode = when (repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        exoPlayer?.let { it.repeatMode = repeatMode }
            ?: MusicPlaybackService.refreshCustomLayout()
    }

    fun applyPlaybackSpeed(speed: Float) {
        playbackSpeed = speed
        if (!holdingFast) {
            exoPlayer?.setPlaybackSpeed(speed)
            companionVideoPlayer?.setPlaybackSpeed(speed)
        }
    }

    fun startFastHold(): Boolean {
        val player = exoPlayer ?: return false
        if (holdingFast || player.playbackState == Player.STATE_IDLE || player.playbackState == Player.STATE_ENDED) return false
        preHoldSpeed = playbackSpeed
        preHoldPlaying = player.isPlaying || player.playWhenReady
        holdingFast = true
        player.setPlaybackSpeed(2f)
        if (!player.playWhenReady) {
            player.playWhenReady = true
            player.play()
        }
        if (isVideoMode) {
            companionVideoPlayer?.setPlaybackSpeed(2f)
            companionVideoPlayer?.play()
        }
        return true
    }

    fun endFastHold() {
        if (!holdingFast) return
        holdingFast = false
        val player = exoPlayer ?: return
        player.setPlaybackSpeed(preHoldSpeed)
        if (!preHoldPlaying) {
            player.pause()
        }
        if (isVideoMode) {
            companionVideoPlayer?.setPlaybackSpeed(preHoldSpeed)
            if (!preHoldPlaying) {
                companionVideoPlayer?.pause()
            }
        }
    }

    fun addToQueue(track: MusicTrack) {
        queue.add(track)
        MusicPlaybackService.refreshCustomLayout()
    }

    fun playNext(track: MusicTrack) {
        if (queue.isEmpty()) {
            play(track)
            return
        }
        val existingIdx = queue.indexOfFirst { it.id == track.id }
        if (existingIdx > queueIndex) {
            queue.removeAt(existingIdx)
        }
        val insertAt = (queueIndex + 1).coerceAtMost(queue.size)
        queue.add(insertAt, track)
        MusicPlaybackService.refreshCustomLayout()
    }

    fun playLast(track: MusicTrack) {
        val existingIdx = queue.indexOfFirst { it.id == track.id }
        if (existingIdx > queueIndex) {
            queue.removeAt(existingIdx)
        }
        queue.add(track)
        MusicPlaybackService.refreshCustomLayout()
    }

    fun playNext(tracks: List<MusicTrack>) {
        if (tracks.isEmpty()) return
        if (queue.isEmpty()) {
            play(tracks.first(), tracks)
            return
        }
        val insertAt = (queueIndex + 1).coerceAtMost(queue.size)
        queue.addAll(insertAt, tracks)
        MusicPlaybackService.refreshCustomLayout()
    }

    fun addToQueue(tracks: List<MusicTrack>) {
        if (tracks.isEmpty()) return
        if (queue.isEmpty()) {
            play(tracks.first(), tracks)
            return
        }
        queue.addAll(tracks)
        MusicPlaybackService.refreshCustomLayout()
    }

    fun playRadio(seedTrack: MusicTrack) {
        scope.launch {
            play(seedTrack)
            val radio = MusicBackend.fetchRadioQueue(seedTrack.id)
            if (radio.isNotEmpty()) {
                queue.clear()
                queue.add(seedTrack)
                queue.addAll(radio)
                queueIndex = 0
                MusicPlaybackService.refreshCustomLayout()
            }
        }
    }

    fun removeFromQueue(index: Int) {
        if (index in queue.indices) {
            val wasPlayingCurrent = (index == queueIndex)
            queue.removeAt(index)
            if (index < queueIndex) {
                queueIndex = (queueIndex - 1).coerceAtLeast(0)
            } else if (queueIndex >= queue.size) {
                queueIndex = (queue.size - 1).coerceAtLeast(0)
            }
            if (wasPlayingCurrent) {
                if (queue.isNotEmpty() && queueIndex in queue.indices) {
                    play(queue[queueIndex], null)
                } else {
                    close()
                }
            }
            MusicPlaybackService.refreshCustomLayout()
        }
    }

    fun clearQueue() {
        currentTrack?.let { current ->
            queue.clear()
            queue.add(current)
            queueIndex = 0
            MusicPlaybackService.refreshCustomLayout()
        }
    }

    fun nextUpTrack(): MusicTrack? {
        val nextIdx = queueIndex + 1
        return if (nextIdx in queue.indices) queue[nextIdx] else null
    }

    fun preCacheNextTrack() {
        val next = nextUpTrack() ?: return
        if (!MusicCacheManager.isTrackCached(next.id)) {
            scope.launch(Dispatchers.IO) {
                MusicCacheManager.cacheTrackOnDemand(next)
            }
        }
    }

    private fun pauseVideoPlayerIfPlaying() {
        try {
            // Optional friendly audio handoff: if video player is active, pause it
            // (done without tight coupling)
        } catch (_: Exception) {}
    }

    // ── Player.Listener Callbacks ──
    override fun onPlaybackStateChanged(playbackState: Int) {
        val playingNow = exoPlayer?.isPlaying == true
        // READY means the source and duration are known. The transport must become a pause
        // control at that point, even if a vendor reports a late buffering callback.
        if (playbackState == Player.STATE_READY) {
            resolvingStream = false
            isLoading = false
            durationMs = exoPlayer?.duration?.takeIf { it > 0L } ?: durationMs
        } else {
            isLoading = !playingNow && (resolvingStream || playbackState == Player.STATE_BUFFERING)
        }
        if (playbackState == Player.STATE_ENDED) {
            currentTrack?.let { ListeningProfile.onTrackCompleted(it) }
            if (SleepTimerState.shouldStopAtEnd()) {
                SleepTimerState.onEndedTriggered()
                pause()
                companionVideoPlayer?.pause()
                return
            }
            if (repeatMode == Player.REPEAT_MODE_ONE) {
                seekTo(0L)
                exoPlayer?.play()
            } else if (nextUpTrack() != null || repeatMode == Player.REPEAT_MODE_ALL) {
                skipNext()
            } else {
                isPlaying = false
            }
        }
    }

    override fun onIsPlayingChanged(playing: Boolean) {
        isPlaying = playing
        if (playing) {
            resolvingStream = false
            isLoading = false
            exoPlayer?.audioSessionId?.let { id ->
                if (id > 0) EqualizerManager.bindAudioSession(id)
            }
        }
    }

    override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
        shuffleEnabled = shuffleModeEnabled
        MusicPlaybackService.refreshCustomLayout()
    }

    override fun onRepeatModeChanged(repeatMode: Int) {
        this.repeatMode = repeatMode
        MusicPlaybackService.refreshCustomLayout()
    }

    override fun onPlayerError(error: PlaybackException) {
        Log.e(TAG, "ExoPlayer error: ${error.message}", error)
        val cur = currentTrack
        if (cur != null && MusicCacheManager.isTrackCached(cur.id)) {
            Log.w(TAG, "Cached file playback failed for ${cur.id}, clearing corrupt cache and retrying online stream")
            MusicCacheManager.removeTrack(cur.id)
            scope.launch {
                play(cur, queue)
            }
            return
        }
        resolvingStream = false
        isLoading = false
        isPlaying = false
    }
}

private class MusicSessionForwardingPlayer(player: Player) : ForwardingPlayer(player) {
    override fun getAvailableCommands(): Player.Commands {
        val canNavigate = MusicPlayerState.currentTrack != null && MusicPlayerState.queue.size > 1
        return super.getAvailableCommands().buildUpon()
            .apply {
                if (canNavigate) {
                    add(Player.COMMAND_SEEK_TO_PREVIOUS)
                    add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                    add(Player.COMMAND_SEEK_TO_NEXT)
                    add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                }
            }
            .build()
    }

    override fun isCommandAvailable(command: Int): Boolean {
        val canNavigate = MusicPlayerState.currentTrack != null && MusicPlayerState.queue.size > 1
        return when (command) {
            Player.COMMAND_SEEK_TO_PREVIOUS, Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
            Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM -> canNavigate
            else -> super.isCommandAvailable(command)
        }
    }

    override fun hasPreviousMediaItem(): Boolean {
        return MusicPlayerState.queue.size > 1 || MusicPlayerState.positionMs > 3000L
    }

    override fun hasNextMediaItem(): Boolean {
        return MusicPlayerState.queue.size > 1 || MusicPlayerState.repeatMode == Player.REPEAT_MODE_ALL
    }

    override fun seekToPrevious() {
        MusicPlayerState.skipPrevious()
    }

    override fun seekToPreviousMediaItem() {
        MusicPlayerState.skipPrevious()
    }

    override fun seekToNext() {
        MusicPlayerState.skipNext()
    }

    override fun seekToNextMediaItem() {
        MusicPlayerState.skipNext()
    }
}
