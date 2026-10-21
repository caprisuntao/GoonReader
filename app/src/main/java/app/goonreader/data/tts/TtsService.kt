package app.goonreader.data.tts

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import app.goonreader.MainActivity
import app.goonreader.R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Foreground service that keeps read-aloud playback alive (PRD 10.7).
 *
 * It renders [TtsController]'s state into a notification with transport actions and stops itself
 * when playback ends. The controller does the speaking, so a dead notification never means dead
 * audio: it only means the process lost its foreground handle.
 */
@AndroidEntryPoint
class TtsService : Service() {

    @Inject lateinit var controller: TtsController

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        createChannel()
        show(controller.state.value)
        scope.launch {
            controller.state.collect { state ->
                if (state.status == TtsStatus.Idle || state.status == TtsStatus.Error) {
                    stopSelf()
                } else {
                    show(state)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TOGGLE -> controller.toggle()
            ACTION_NEXT -> controller.nextParagraph()
            ACTION_PREV -> controller.previousParagraph()
            ACTION_STOP -> {
                controller.stop()
                stopSelf()
                return START_NOT_STICKY
            }
        }
        // Always called: the system expects it after every startForegroundService, even when the
        // service is already in the foreground.
        show(controller.state.value)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun show(state: TtsState) {
        startForeground(NOTIFICATION_ID, notification(state))
    }

    private fun notification(state: TtsState): Notification {
        val playing = state.status == TtsStatus.Playing
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_tts)
            .setContentTitle(state.chapterLabel.ifBlank { "Reading aloud" })
            .setContentText(state.workTitle.ifBlank { "GoonReader" })
            .setContentIntent(
                PendingIntent.getActivity(
                    this,
                    0,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
            .setOngoing(true)
            .setSilent(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(0, "Previous", action(ACTION_PREV))
            .addAction(0, if (playing) "Pause" else "Play", action(ACTION_TOGGLE))
            .addAction(0, "Next", action(ACTION_NEXT))
            .addAction(0, "Stop", action(ACTION_STOP))
            .build()
    }

    private fun action(action: String): PendingIntent = PendingIntent.getService(
        this,
        action.hashCode(),
        Intent(this, TtsService::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Read aloud", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Playback controls while GoonReader reads a chapter out loud."
                setShowBadge(false)
            },
        )
    }

    private companion object {
        const val CHANNEL_ID = "tts"
        const val NOTIFICATION_ID = 41
        const val ACTION_TOGGLE = "app.goonreader.tts.TOGGLE"
        const val ACTION_NEXT = "app.goonreader.tts.NEXT"
        const val ACTION_PREV = "app.goonreader.tts.PREV"
        const val ACTION_STOP = "app.goonreader.tts.STOP"
    }
}
