package com.github.itube

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/**
 * Android's one music session and notification owner.
 *
 * This follows AirBeats' lifecycle: the app binds this service before playback, then Media3
 * promotes its own notification only once the shared player is actually ready. Starting a
 * foreground service after audio has already begun races Android's deadline and was the source
 * of the ForegroundServiceDidNotStartInTime crash.
 */
@OptIn(UnstableApi::class)
class MusicPlaybackService : MediaSessionService() {
    private var session: MediaSession? = null

    companion object {
        private const val ACTION_PREVIOUS = "com.github.itube.music.PREVIOUS"
        private const val ACTION_NEXT = "com.github.itube.music.NEXT"
        private const val ACTION_SHUFFLE = "com.github.itube.music.SHUFFLE"
        private const val ACTION_REPEAT = "com.github.itube.music.REPEAT"
        private val previousCommand = SessionCommand(ACTION_PREVIOUS, Bundle.EMPTY)
        private val nextCommand = SessionCommand(ACTION_NEXT, Bundle.EMPTY)
        private val shuffleCommand = SessionCommand(ACTION_SHUFFLE, Bundle.EMPTY)
        private val repeatCommand = SessionCommand(ACTION_REPEAT, Bundle.EMPTY)
        @Volatile private var activeService: MusicPlaybackService? = null

        fun refreshCustomLayout() {
            activeService?.refreshLayout()
        }
    }

    override fun onCreate() {
        super.onCreate()
        activeService = this
        setListener(object : MediaSessionService.Listener {
            override fun onForegroundServiceStartNotAllowedException() {
                // A bound service retries with the next player update instead of crashing on
                // Android 12+ background-start restrictions.
            }
        })
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider(this).apply {
                setSmallIcon(R.drawable.itube)
            },
        )
        val openApp = PendingIntent.getActivity(
            this,
            1,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        session = MediaSession.Builder(this, MusicPlayerState.playerForSession(this))
            .setId("itube_music")
            .setSessionActivity(openApp)
            .setCallback(object : MediaSession.Callback {
                override fun onConnect(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo,
                ): MediaSession.ConnectionResult {
                    val base = super.onConnect(session, controller)
                    val commands = base.availableSessionCommands.buildUpon()
                        .add(previousCommand)
                        .add(nextCommand)
                        .add(shuffleCommand)
                        .add(repeatCommand)
                        .build()
                    val playerCommands = base.availablePlayerCommands.buildUpon()
                        .add(Player.COMMAND_SEEK_TO_PREVIOUS)
                        .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                        .add(Player.COMMAND_SEEK_TO_NEXT)
                        .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                        .build()
                    return MediaSession.ConnectionResult.accept(commands, playerCommands)
                }

                override fun onCustomCommand(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    customCommand: SessionCommand,
                    args: Bundle,
                ): ListenableFuture<SessionResult> {
                    when (customCommand.customAction) {
                        ACTION_PREVIOUS -> MusicPlayerState.skipPrevious()
                        ACTION_NEXT -> MusicPlayerState.skipNext()
                        ACTION_SHUFFLE -> MusicPlayerState.toggleShuffle()
                        ACTION_REPEAT -> MusicPlayerState.toggleRepeat()
                    }
                    // Repeat and shuffle update the session layout from MusicPlayerState.
                    // Don't send a second layout update from this callback: some Android
                    // notification hosts briefly remove custom actions while applying two
                    // back-to-back layouts, which made Repeat disappear after it was tapped.
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
            })
            .build()
        refreshLayout()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onUpdateNotification(session: MediaSession, startInForegroundRequired: Boolean) {
        super.onUpdateNotification(session, startInForegroundRequired)
    }

    private fun refreshLayout() {
        val activeSession = session ?: return
        val hasTrack = MusicPlayerState.currentTrack != null
        val repeatIcon = when (MusicPlayerState.repeatMode) {
            Player.REPEAT_MODE_ONE -> R.drawable.repeat_one_on
            Player.REPEAT_MODE_ALL -> R.drawable.repeat_on
            else -> R.drawable.repeat
        }
        val repeatLabel = when (MusicPlayerState.repeatMode) {
            Player.REPEAT_MODE_ONE -> "Repeat one"
            Player.REPEAT_MODE_ALL -> "Repeat all"
            else -> "Repeat off"
        }
        activeSession.setCustomLayout(
            listOf(
                CommandButton.Builder().setDisplayName(if (MusicPlayerState.shuffleEnabled) "Shuffle on" else "Shuffle off")
                    .setIconResId(if (MusicPlayerState.shuffleEnabled) R.drawable.shuffle_on else R.drawable.shuffle)
                    .setSessionCommand(shuffleCommand).setEnabled(hasTrack).build(),
                CommandButton.Builder().setDisplayName(repeatLabel)
                    .setIconResId(repeatIcon).setSessionCommand(repeatCommand)
                    .setEnabled(hasTrack).build(),
            ),
        )
    }

    override fun onDestroy() {
        if (activeService === this) activeService = null
        MusicPlayerState.onServiceDestroyed()
        session?.release()
        session = null
        super.onDestroy()
    }
}
