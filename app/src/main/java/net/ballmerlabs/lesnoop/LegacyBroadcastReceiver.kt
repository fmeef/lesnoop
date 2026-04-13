package net.ballmerlabs.lesnoop

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import com.polidea.rxandroidble3.RxBleClient
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import io.reactivex.rxjava3.core.Scheduler
import net.ballmerlabs.lesnoop.scan.BroadcastReceiverState
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Named

@AndroidEntryPoint
class LegacyBroadcastReceiver : BroadcastReceiver() {
    @Inject
    lateinit var client: RxBleClient

    @Inject
    @ApplicationContext
    lateinit var context: Context

    @Inject
    lateinit var state: BroadcastReceiverState

    @Inject
    lateinit var scannerFactory: ScannerFactory

    @Inject
    @Named(Module.TIMEOUT_SCHEDULER)
    lateinit var timeoutScheduler: Scheduler

    @Inject
    lateinit var prefs: SharedPreferences

    override fun onReceive(context: Context, intent: Intent) {
        try {
            val result = client.backgroundScanner.onScanResultReceived(intent)
            state.batch(result, true)
        } catch (exc: Exception) {
            Timber.w("exception in broadcastreceiver $exc")
            exc.printStackTrace()
        }
    }

    companion object {
        const val SCAN_REQUEST_CODE = 45
    }
}