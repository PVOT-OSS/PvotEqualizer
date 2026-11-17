package org.prauga.equalizer.audio

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import org.prauga.equalizer.R
import org.prauga.equalizer.data.EqStateStore

/**
 * After a restart, offers to turn the equalizer back on if it was on.
 *
 * Android 15 does not let a boot receiver start a media-playback foreground service, so this posts
 * a notification instead; tapping it is a user action, which may start the service.
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject lateinit var stateStore: EqStateStore

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (!stateStore.loadPowerOn()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            Log.i(TAG, "equalizer was on before restart; no notification permission to offer it")
            return
        }
        EqService.createChannel(context)
        val turnOn = PendingIntent.getForegroundService(
            context,
            0,
            EqService.intent(context).setAction(EqService.ACTION_TURN_ON),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(context, EqService.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_equalizer)
            .setContentTitle(context.getString(R.string.boot_notification_title))
            .setContentText(context.getString(R.string.boot_notification_text))
            .setContentIntent(turnOn)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(BOOT_NOTIFICATION_ID, notification)
        } catch (error: SecurityException) {
            Log.i(TAG, "notification permission revoked before posting", error)
        }
    }
}

private const val TAG = "EqBoot"
private const val BOOT_NOTIFICATION_ID = 2
