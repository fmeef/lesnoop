package net.ballmerlabs.lesnoop

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothManager
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.lifecycle.MutableLiveData
import com.polidea.rxandroidble3.LogConstants
import com.polidea.rxandroidble3.LogOptions
import com.polidea.rxandroidble3.RxBleClient
import com.polidea.rxandroidble3.internal.RxBleLog
import dagger.hilt.android.AndroidEntryPoint
import io.reactivex.rxjava3.plugins.RxJavaPlugins
import net.ballmerlabs.lesnoop.ScannerFactory.Companion.PREF_LEGACY
import net.ballmerlabs.lesnoop.scan.ConnectQueue
import net.ballmerlabs.lesnoop.scan.InsertQueue
import net.ballmerlabs.lesnoop.scan.LocationTagger
import net.ballmerlabs.lesnoop.scan.ScreenOffReceiver
import timber.log.Timber
import javax.inject.Inject
import androidx.core.net.toUri

fun newPendingIntent(context: Context, c: Class<*>): PendingIntent =
    Intent(context, c).let {
        val code = if (c == LegacyBroadcastReceiver::class.java)
            LegacyBroadcastReceiver.SCAN_REQUEST_CODE
        else
            NonLegacyBroadcastReceiver.SCAN_REQUEST_CODE

        Timber.e("newPendingIntent code=$code")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.getBroadcast(
                context,
                code,
                it,
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_CANCEL_CURRENT
            )
        } else {
            PendingIntent.getBroadcast(
                context, code, it, PendingIntent.FLAG_UPDATE_CURRENT
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

    @Inject
    lateinit var queue: ConnectQueue

    @Inject
    lateinit var insertQueue: InsertQueue

    @Inject
    lateinit var clientScanner: ScannerFactory

    @Inject
    lateinit var prefs: SharedPreferences

    @Inject
    lateinit var locationTagger: LocationTagger

    @Inject
    lateinit var wakeLockProvider: WakeLockProvider

    @Inject
    lateinit var screenOffReceiver: ScreenOffReceiver


    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        val scanMode = prefs
            .getString(ScannerFactory.PREF_SCAN_MODE, ScannerFactory.SCAN_MODE_BATCH)
            ?: ScannerFactory.SCAN_MODE_BATCH
        if (intent?.action != ACTION_RELOAD) {
            wakeLockProvider.hold()
            val soundUri =
                ("android.resource://" + packageName + "/" + R.raw.red_alert).toUri()
            val notification =
                NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_FOREGROUND)
                    .setContentTitle("LeSnoop")
                    .setContentText("Scanning...\n(this uses location permission, but not actual geolocation)")
                    .setSmallIcon(R.drawable.ic_launcher_background)
                    .setSound(soundUri)
                    .setTicker("fmef am tire").build()
            startForeground(
                99, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE or
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION or
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED
            )

        }


        registerReceiver(screenOffReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF))

        val scanner = bluetoothManager.adapter?.bluetoothLeScanner

        val legacy = prefs.getBoolean(PREF_LEGACY, false)
        Timber.w("starting pendingIntent scan with phy legacy=$legacy")
        locationTagger.startLocationPoll()
        insertQueue.processDumb()

        RxBleLog.updateLogOptions(LogOptions.Builder()
            .setMacAddressLogSetting(LogConstants.MAC_ADDRESS_FULL)
            .setLogLevel(LogConstants.DEBUG).build()
        )

        if (ActivityCompat.checkSelfPermission(
                this, Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED && scanner != null
        ) {
            if (scanMode == ScannerFactory.SCAN_MODE_FOREGROUND) {
                clientScanner.createScanner().scanForeground(legacy)
            } else {
//                val legacySettings =
//                    ScanSettings.Builder()
//                        .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
//                        .setPhy(phy)
//                        .setLegacy(false)
//                        .apply {
//                            if (reportDelayEnabled)
//                                setReportDelay(reportDelay)
//                        }
//                        .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES).build()

//                scanner.startScan(
//                    listOf(ScanFilter.Builder().build()),
//                    legacySettings,
//                    legacyIntent
//                )

                running.postValue(true)
                clientScanner.createScanner().setScanRunning(true)

                clientScanner.createScanner().unpauseScan()


            }



        }

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Timber.w("service stopped")
        wakeLockProvider.releaseAll()
        running.postValue(false)
        unregisterReceiver(screenOffReceiver)
        val pendingIntent = newPendingIntent(this, NonLegacyBroadcastReceiver::class.java)
        val legacyIntent = newPendingIntent(this, LegacyBroadcastReceiver::class.java)
        clientScanner.createScanner().setScanRunning(false)
        client.backgroundScanner.stopBackgroundBleScan(pendingIntent)
        client.backgroundScanner.stopBackgroundBleScan(legacyIntent)
        clientScanner.createScanner().stopScanForeground()
        insertQueue.stopProcess()
        locationTagger.stopLocationPoll()
        queue.shutdown()


        /*
        WorkManager.getInstance(this)
            .cancelUniqueWork(SCAN_RESET_WORKER_ID.toString())
         */
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        manager.deleteNotificationChannel(NOTIFICATION_CHANNEL_FOREGROUND)
        val soundUri =
            ("android.resource://" + packageName + "/" + R.raw.red_alert).toUri()
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_FOREGROUND, "scan-notif", NotificationManager.IMPORTANCE_HIGH
        )

        val audioAttributes = AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .build()

        channel.setSound(soundUri, audioAttributes)


        manager.createNotificationChannel(channel)
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    companion object {
        const val NOTIFICATION_CHANNEL_FOREGROUND =
            "net.ballmerlabs.lesnoop.foreground-scan-sound"
        const val ACTION_RELOAD = "reload"
    }

    inner class LocalBinder : Binder() {
        // Return this instance of LocalService so clients can call public methods.
        fun getService(): BackgroundScanService = this@BackgroundScanService
    }

    init {
        Timber.tag("global").v("set global error handler")
        RxJavaPlugins.setErrorHandler { err: Throwable ->
            Timber.e("unhandled rxjava exception: $err")
            err.printStackTrace()
        }
    }

}