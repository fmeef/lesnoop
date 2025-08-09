package net.ballmerlabs.lesnoop

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.lifecycle.MutableLiveData
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import com.polidea.rxandroidble3.RxBleClient
import dagger.hilt.android.AndroidEntryPoint
import java.util.concurrent.TimeUnit
import javax.inject.Inject
fun newPendingIntent(context: Context): PendingIntent =
    Intent(context, ScanBroadcastReceiver::class.java).let {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.getBroadcast(
                context,
                SCAN_REQUEST_CODE,
                it,
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        } else {
            PendingIntent.getBroadcast(
                context,
                SCAN_REQUEST_CODE,
                it,
                PendingIntent.FLAG_UPDATE_CURRENT
            )
        }
    }

@AndroidEntryPoint
class BackgroundScanService : Service() {

    val running = MutableLiveData(false)

    private val binder = LocalBinder()



    @Inject
    lateinit var client: RxBleClient

    @Inject
    lateinit var bluetoothManager: BluetoothManager

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        Log.w("debug", "service started")
        val legacy = intent?.getBooleanExtra(EXTRA_LEGACY_MODE, false) ?: false
        val phy = intent?.getIntExtra(EXTRA_PHY, 0) ?: 0
        val pendingIntent = newPendingIntent(this)
        if (intent?.action != ACTION_RELOAD) {
            val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_FOREGROUND)
                .setContentTitle("LeSnoop")
                .setContentText("Scanning...\n(this uses location permission, but not actual geolocation)")
                .setSmallIcon(R.drawable.ic_launcher_background)
                .setTicker("fmef am tire")
                .build()
            startForeground(1, notification)
        }
        val scanner = bluetoothManager.adapter?.bluetoothLeScanner
        if(scanner != null) {
            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .setPhy(phy)
                .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
                .setLegacy(legacy)
                .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                .build()

            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_SCAN
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                Log.w("debug", "starting scan with phy $phy")
                scanner.stopScan(pendingIntent)
                scanner.startScan(listOf(ScanFilter.Builder().build()), settings, pendingIntent)
            }


        }

        running.postValue(true)
        return START_STICKY
    }
    override fun onDestroy() {
        super.onDestroy()
        Log.w("debug", "service stopped")
        running.postValue(false)
        val pendingIntent = newPendingIntent(this)
        client.backgroundScanner.stopBackgroundBleScan(pendingIntent)
        /*
        WorkManager.getInstance(this)
            .cancelUniqueWork(SCAN_RESET_WORKER_ID.toString())
         */
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    override fun onCreate() {
        super.onCreate()
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_FOREGROUND,
            "subrosanotif",
            NotificationManager.IMPORTANCE_DEFAULT
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }
    override fun onBind(intent: Intent): IBinder? {
       return binder
    }

    companion object {
        private const val NOTIFICATION_CHANNEL_FOREGROUND = "foreground"
        const val EXTRA_LEGACY_MODE = "legacy"
        const val EXTRA_PHY = "phy"
        const val ACTION_RELOAD = "reload"
    }
    inner class LocalBinder : Binder() {
        // Return this instance of LocalService so clients can call public methods.
        fun getService(): BackgroundScanService = this@BackgroundScanService
    }

}