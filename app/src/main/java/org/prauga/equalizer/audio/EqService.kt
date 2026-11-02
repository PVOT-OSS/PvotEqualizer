package org.prauga.equalizer.audio

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.audiofx.AudioEffect
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.prauga.equalizer.MainActivity
import org.prauga.equalizer.PvotEqualizerApp
import org.prauga.equalizer.R

/**
 * Keeps the equalizer alive while the app is in the background, and listens for music apps telling us
 * they have an audio session.
 *
 * The session receiver is registered here rather than in the manifest on purpose: on Android 12+
 * starting a foreground service from a background broadcast is restricted, but a receiver that is
 * already registered inside a running service can attach an effect without starting anything. It also
 * means this only works once the user has turned the equalizer on, which is the behaviour we want.
 */
class EqService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var engine: AudioEngine
    private var sessionReceiver: BroadcastReceiver? = null

    override fun onCreate() {
        super.onCreate()
        engine = (application as PvotEqualizerApp).container.engine
        createChannel()
        startForeground(NOTIFICATION_ID, buildNotification(engine.status.value))
        registerSessionReceiver()
        scope.launch {
            engine.status.collect { status -> updateNotification(status) }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        sessionReceiver?.let { runCatching { unregisterReceiver(it) } }
        sessionReceiver = null
        scope.cancel()
        super.onDestroy()
    }

    private fun registerSessionReceiver() {
        val filter = IntentFilter().apply {
            addAction(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION)
            addAction(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION)
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val sessionId = intent.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, 0)
                if (sessionId <= 0) return
                if (intent.action == AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION) {
                    engine.attach(
                        sessionId = sessionId,
                        packageName = intent.getStringExtra(AudioEffect.EXTRA_PACKAGE_NAME)
                    )
                } else {
                    engine.detach(sessionId)
                }
            }
        }
        ContextCompat.registerReceiver(this, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        sessionReceiver = receiver
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.engine_notification_channel),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.engine_notification_channel_description)
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(status: EngineStatus): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val text = when {
            status.failure != null -> getString(R.string.engine_notification_failed, status.failure)
            status.attachedPackages.isEmpty() -> getString(R.string.engine_notification_waiting)
            else -> getString(
                R.string.engine_notification_connected,
                status.attachedPackages.joinToString()
            )
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_equalizer)
            .setContentTitle(getString(R.string.engine_notification_title))
            .setContentText(text)
            .setContentIntent(openApp)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(status: EngineStatus) {
        // Without the notification permission the service still runs; the notification is simply
        // not shown, so there is nothing to report.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        try {
            NotificationManagerCompat.from(this)
                .notify(NOTIFICATION_ID, buildNotification(status))
        } catch (_: SecurityException) {
            // The grant was revoked between the check and the call.
        }
    }

    companion object {
        /** The intent that starts this service. */
        fun intent(context: Context): Intent = Intent(context, EqService::class.java)
    }
}

private const val CHANNEL_ID = "eq_engine"
private const val NOTIFICATION_ID = 1
