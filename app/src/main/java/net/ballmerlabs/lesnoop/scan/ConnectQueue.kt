package net.ballmerlabs.lesnoop.scan

import android.content.SharedPreferences
import com.polidea.rxandroidble3.RxBleDevice
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Scheduler
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.Disposable
import net.ballmerlabs.lesnoop.Module
import net.ballmerlabs.lesnoop.ScannerFactory
import net.ballmerlabs.lesnoop.db.ScanResultDao
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class ConnectQueue @Inject constructor(
    val prefs: SharedPreferences,
    @param:Named(Module.TIMEOUT_SCHEDULER)
    val timeoutScheduler: Scheduler
)  {

    private val inflight = ConcurrentHashMap<String, Disposable>()


    fun accept(device: RxBleDevice, value: Single<Boolean>) {
        val max = prefs.getInt(ScannerFactory.PREF_MAX_CONNECTION, 7)
        if (inflight.size <= max) {
            inflight.computeIfAbsent(device.macAddress) { key ->
                value
                    .timeout(prefs.getLong(ScannerFactory.PREF_CONNECT_TIMEOUT, 7), TimeUnit.SECONDS, timeoutScheduler)
                    .doFinally { inflight.remove(device.macAddress) }
                    .subscribe(
                        { v ->
                            Timber.e("connect success!")
                        },
                        { err ->
                            Timber.e("queue connect error $err")
                            shutdown()
                        }

                    )
            }
        }
    }

    fun shutdown() {
        val values = inflight.values.toList()
        inflight.clear()
        values.forEach { v ->
            v.dispose()
        }

    }
}