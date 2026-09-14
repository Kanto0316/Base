package com.netk.mvola.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.netk.mvola.data.local.HistoryEntity
import com.netk.mvola.data.local.NetKDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Owns playback independently from the activity; Media3 supplies the foreground media notification. */
class MusicService : MediaSessionService() {
    private lateinit var player: ExoPlayer
    private lateinit var session: MediaSession
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        player = ExoPlayer.Builder(this).build().apply {
            addListener(object : Player.Listener {
                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    mediaItem ?: return
                    val metadata = mediaItem.mediaMetadata
                    serviceScope.launch {
                        NetKDatabase.getInstance(applicationContext).musicDao().insertHistory(
                            HistoryEntity(uri = mediaItem.localConfiguration?.uri.toString(), title = metadata.title?.toString().orEmpty(), artist = metadata.artist?.toString().orEmpty())
                        )
                    }
                }
            })
        }
        session = MediaSession.Builder(this, player).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession = session

    override fun onDestroy() {
        session.release()
        player.release()
        serviceScope.cancel()
        super.onDestroy()
    }
}
