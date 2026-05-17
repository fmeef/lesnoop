package net.ballmerlabs.lesnoop.scan

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import net.ballmerlabs.lesnoop.BackgroundScanService
import net.ballmerlabs.lesnoop.R
import javax.inject.Inject

class ScreenOffReceiver @Inject constructor(
    val soundUri: Uri
) : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {

        when(intent.action) {
            Intent.ACTION_SCREEN_OFF -> {
                if (ActivityCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED) {
                    with(NotificationManagerCompat.from(context)) {
                        val notif = NotificationCompat.Builder(
                            context,
                            BackgroundScanService.NOTIFICATION_CHANNEL_FOREGROUND
                        )
                            .setSmallIcon(R.drawable.baseline_info_24)
                            .setContentTitle("Screen turned off")
                            .setContentText("Scanning was interrupted")
                            .setPriority(NotificationCompat.PRIORITY_MAX)
                            .setSound(soundUri)
                            .build()


                            notify(99,notif)

                    }
                }
            }
        }
    }
}