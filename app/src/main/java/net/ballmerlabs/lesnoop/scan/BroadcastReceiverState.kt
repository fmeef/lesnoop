package net.ballmerlabs.lesnoop.scan

import android.content.SharedPreferences
import com.polidea.rxandroidble3.scan.IsConnectable
import com.polidea.rxandroidble3.scan.ScanResult
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Scheduler
import io.reactivex.rxjava3.disposables.Disposable
import net.ballmerlabs.lesnoop.Module
import net.ballmerlabs.lesnoop.ScannerFactory
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class BroadcastReceiverState @Inject constructor(
    @param:Named(Module.TIMEOUT_SCHEDULER)
    val timeoutScheduler: Scheduler,
    val prefs: SharedPreferences,
    val queue: ConnectQueue,
    val insertQueue: InsertQueue,
    val scanner: ScannerFactory
) {
    private val batch = ConcurrentHashMap<String, Completable>()

    var prevMode: String? = ScannerFactory.SCAN_MODE_QUEUE

    val connectDisp = AtomicReference<Disposable?>(null)

    fun batch(scanResult: List<ScanResult>, legacy: Boolean) {
//        if (scanResult.isNotEmpty() && batch.isNotEmpty() && batch.keys.any { v -> scanResult.any { s -> s.bleDevice.macAddress == v  } }) {
//            Timber.v( "skipping")
//            return
//        }

        //Timber.v( "batch len ${scanResult.size}")
        for (result in scanResult.distinctBy { v -> v.bleDevice.macAddress }) {
            executeBatch(result, legacy)
        }
    }

    fun insertWithoutConnecting(result: ScanResult, legacy: Boolean) {
        Timber.tag("debug").v("insertWithoutConnecting ${result.bleDevice.macAddress}")
        insertQueue.accept(
            result.bleDevice,
            scanner.createScanner().insertResult(result, legacy)
                .doFinally { batch.remove(result.bleDevice.macAddress) }
                .ignoreElement())
    }

    fun executeBatch(result: ScanResult, legacy: Boolean) {
        val s = scanner.createScanner()


        val delay = prefs.getLong(ScannerFactory.PREF_DELAY, 5)
        val scanMode =
            prefs.getString(ScannerFactory.PREF_SCAN_MODE, ScannerFactory.SCAN_MODE_BATCH)

        if (scanMode == ScannerFactory.SCAN_MODE_QUEUE && prevMode == ScannerFactory.SCAN_MODE_BATCH) {
            batch.clear()
        }

        prevMode = scanMode


        if (scanMode == ScannerFactory.SCAN_MODE_BATCH
        ) {
            if (batch.putIfAbsent(
                    result.bleDevice.macAddress,
                    s.insertResult(result, true)
                        .ignoreElement()
                        .andThen(s.connectWithDbCache(result, legacy))
                        .ignoreElement()
                        .doFinally {
                            batch.remove(result.bleDevice.macAddress)
                        }
                ) == null) {
                batchConnect(delay, TimeUnit.SECONDS)
            }
        } else if (scanMode == ScannerFactory.SCAN_MODE_QUEUE) {
            Timber.v("device isConnectable ${result.isConnectable} ${result.callbackType}")
            return when (result.isConnectable) {
                IsConnectable.CONNECTABLE -> {
                    insertWithoutConnecting(result, legacy)
                    queue.accept(
                        result.bleDevice,
                        s.connectWithDbCache(result, legacy)
                    )
                }

                IsConnectable.NOT_CONNECTABLE -> insertWithoutConnecting(result, legacy)
                IsConnectable.LEGACY_UNKNOWN -> {
                    if (legacy) {
                        insertWithoutConnecting(result, true)
                        queue.accept(
                            result.bleDevice, s.connectWithDbCache(result, true)
                        )
                    } else {
                        insertWithoutConnecting(result, false)
                    }
                }

                else -> insertWithoutConnecting(result, legacy)
            }
        }
    }


    fun abortConnect() {
        connectDisp.getAndSet(null)?.dispose()
    }

    fun batchConnect(delay: Long, timeUnit: TimeUnit) {
        connectDisp.updateAndGet { v ->
            when (v) {
                null -> {
                    val connect = Flowable.defer {
                        Flowable.fromIterable(batch.values.toList())
                            .doOnSubscribe { batch.clear() }
                    }

                    Completable.timer(delay, timeUnit, timeoutScheduler)
                        .andThen(Completable.fromAction {
                            scanner.createScanner().pauseScan()
                        })
                        .andThen(connect)
                        .flatMapCompletable { v -> v }
                        .doFinally {
                            connectDisp.set(null)
                            scanner.createScanner().unpauseScan()
                        }
                        .subscribe(
                            {
                                Timber.v("batch connect complete")
                            },
                            { err ->
                                Timber.e("batch connect error: $err")
                            }
                        )
                }

                else -> v
            }
        }

    }
}