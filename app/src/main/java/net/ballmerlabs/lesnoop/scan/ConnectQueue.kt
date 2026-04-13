package net.ballmerlabs.lesnoop.scan

import android.content.SharedPreferences
import com.polidea.rxandroidble3.RxBleDevice
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Scheduler
import io.reactivex.rxjava3.disposables.Disposable
import net.ballmerlabs.lesnoop.Module
import net.ballmerlabs.lesnoop.ScannerFactory
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class ConnectQueue @Inject constructor(
    val prefs: SharedPreferences,
    @param:Named(Module.TIMEOUT_SCHEDULER)
    val timeoutScheduler: Scheduler
)  {

    private var shutdown = false
    private val inflight = ConcurrentHashMap<String, Disposable>()


    fun accept(device: RxBleDevice, value: Completable) {
        if(shutdown)
            return
        val max = prefs.getInt(ScannerFactory.PREF_MAX_CONNECTION, 7)
        if (inflight.size <= max) {
            inflight.computeIfAbsent(device.macAddress) { key ->
                value
                    .doFinally { inflight.remove(key)?.dispose() }
                    .subscribe(
                        { },
                        { err -> }

                    )
            }
        }
    }

    fun shutdown() {
        shutdown = true
        inflight.values.forEach { v ->
            v.dispose()
        }
        inflight.clear()
    }
}