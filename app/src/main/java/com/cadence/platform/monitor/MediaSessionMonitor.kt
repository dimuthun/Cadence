package com.cadence.platform.monitor

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import com.cadence.core.coroutines.ApplicationScope
import com.cadence.domain.permission.PermissionRequirement
import com.cadence.platform.notification.CadenceNotificationListener
import com.cadence.platform.permission.AndroidPermissionChecker
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.shareIn

/** The track a media app is playing. */
data class MediaTrack(val packageName: String, val title: String, val artist: String, val album: String)

/**
 * Other apps' media sessions. Android exposes them only to apps with notification access, so
 * this needs [PermissionRequirement.NOTIFICATION_LISTENER]; without it nothing is reported.
 * Track titles stay in memory and are never written to the log.
 */
@Singleton
class MediaSessionMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
    @ApplicationScope scope: CoroutineScope,
    private val permissionChecker: AndroidPermissionChecker,
) {
    private val sessions = context.getSystemService(MediaSessionManager::class.java)
    private val listenerComponent = ComponentName(context, CadenceNotificationListener::class.java)

    /** Emits each time a player starts a different track. Tracks already playing when collection starts are a baseline. */
    val trackChanges: SharedFlow<MediaTrack> = callbackFlow {
        val handler = Handler(Looper.getMainLooper())
        // All callbacks run on the main looper, so this state needs no locking.
        val watched = HashMap<MediaSession.Token, Pair<MediaController, MediaController.Callback>>()
        val lastTrack = HashMap<String, MediaTrack>()

        fun watch(controllers: List<MediaController>?) {
            val current = controllers.orEmpty().associateBy { it.sessionToken }
            (watched.keys - current.keys).forEach { key ->
                watched.remove(key)?.let { (controller, callback) -> controller.unregisterCallback(callback) }
            }
            current.forEach { (key, controller) ->
                if (key in watched) return@forEach
                controller.metadata?.toTrack(controller.packageName)?.let { lastTrack[controller.packageName] = it }
                val callback = object : MediaController.Callback() {
                    override fun onMetadataChanged(metadata: MediaMetadata?) {
                        val track = metadata?.toTrack(controller.packageName) ?: return
                        if (lastTrack.put(controller.packageName, track) != track) trySend(track)
                    }
                }
                controller.registerCallback(callback, handler)
                watched[key] = controller to callback
            }
        }

        val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { watch(it) }
        if (permissionChecker.isGranted(PermissionRequirement.NOTIFICATION_LISTENER)) {
            sessions.addOnActiveSessionsChangedListener(sessionsListener, listenerComponent, handler)
            handler.post { watch(sessions.getActiveSessions(listenerComponent)) }
        }
        awaitClose {
            sessions.removeOnActiveSessionsChangedListener(sessionsListener)
            handler.post {
                watched.values.forEach { (controller, callback) -> controller.unregisterCallback(callback) }
                watched.clear()
            }
        }
    }.shareIn(scope, SharingStarted.WhileSubscribed())

    /** The playing track (or the first paused one), or `null` without notification access or media. */
    fun currentTrack(): MediaTrack? {
        if (!permissionChecker.isGranted(PermissionRequirement.NOTIFICATION_LISTENER)) return null
        val controllers = try {
            sessions.getActiveSessions(listenerComponent)
        } catch (e: SecurityException) {
            return null
        }
        val controller = controllers.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: controllers.firstOrNull()
            ?: return null
        return controller.metadata?.toTrack(controller.packageName)
    }

    private fun MediaMetadata.toTrack(packageName: String): MediaTrack? {
        val title = getString(MediaMetadata.METADATA_KEY_TITLE)?.takeIf { it.isNotBlank() } ?: return null
        return MediaTrack(
            packageName = packageName,
            title = title,
            artist = getString(MediaMetadata.METADATA_KEY_ARTIST) ?: getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST).orEmpty(),
            album = getString(MediaMetadata.METADATA_KEY_ALBUM).orEmpty(),
        )
    }
}
