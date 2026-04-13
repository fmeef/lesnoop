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
import net.ballmerlabs.lesnoop.WakeLockProvider
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
    val scanner: ScannerFactory,
    val wakeLockProvider: WakeLockProvider
) {
    private val batch = ConcurrentHashMap<String, Completable>()
    private val connected = ConcurrentHashMap<String, Boolean>()

    val connectDisp = AtomicReference<Disposable?>(null)

    fun batch(scanResult: List<ScanResult>, legacy: Boolean) {
//        if (scanResult.isNotEmpty() && batch.isNotEmpty() && batch.keys.any { v -> scanResult.any { s -> s.bleDevice.macAddress == v  } }) {
//            Timber.v( "skipping")
//            return
//        }

        //Timber.v( "batch len ${scanResult.size}")
        for (result in scanResult.distinctBy { v -> v.bleDevice.macAddress }) {

            if (connected.putIfAbsent(result.bleDevice.macAddress, true) == null)
                executeBatch(result)
        }
    }

    fun insertWithoutConnecting(result: ScanResult) {
        Timber.tag("debug").v("insertWithoutConnecting ${result.bleDevice.macAddress}")
        insertQueue.accept(
            result.bleDevice,
            scanner.createScanner().insertResult(result)
                .doFinally { batch.remove(result.bleDevice.macAddress) }
                .ignoreElement())
    }

    fun executeBatch(result: ScanResult) {
        val s = scanner.createScanner()
        val f = s.insertResult(result)
            .doOnSubscribe { wakeLockProvider.hold() }
            .doOnSuccess { Timber.v( "inserted result?") }
            .flatMapMaybe { scanResult ->
                s.discoverServices(
                    scanResult.second.bleDevice, scanResult.first
                ).onErrorComplete()
            }.doOnComplete { Timber.w( "connect complete") }
            .ignoreElement()
            .timeout(200, TimeUnit.SECONDS, timeoutScheduler).onErrorComplete()
            .doOnSubscribe {
                Timber.v( "batch with size ${batch.size}")
            }
            .doFinally {
                batch.remove(result.bleDevice.macAddress)
                connected.remove(result.bleDevice.macAddress)
                wakeLockProvider.release()
                Timber.v( "removing device ${result.bleDevice.macAddress}")
            }

        val delay = prefs.getLong(ScannerFactory.PREF_DELAY, 5)
        val scanMode =
            prefs.getString(ScannerFactory.PREF_SCAN_MODE, ScannerFactory.SCAN_MODE_BATCH)
        if (scanMode == ScannerFactory.SCAN_MODE_BATCH && batch.putIfAbsent(
                result.bleDevice.macAddress,
                f
            ) == null
        ) {
            batchConnect(delay, TimeUnit.SECONDS)
        } else if (scanMode == ScannerFactory.SCAN_MODE_QUEUE) {
            Timber.v( "device isConnectable ${result.isConnectable} ${result.callbackType}")
            return when (result.isConnectable) {
                IsConnectable.CONNECTABLE -> {
                    insertWithoutConnecting(result)
                    queue.accept(
                        result.bleDevice,
                        s.insertResult(result).ignoreElement().andThen(f)
                    )
                }

                IsConnectable.NOT_CONNECTABLE -> insertWithoutConnecting(result)
                IsConnectable.LEGACY_UNKNOWN -> insertWithoutConnecting(result)
                else -> insertWithoutConnecting(result)
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
                    val connect = Flowable.fromIterable(batch.values.toList())
                    Completable.timer(delay, timeUnit, timeoutScheduler).andThen(connect)
                        .flatMapCompletable { v -> v }
                        .doFinally {
                            connectDisp.set(null)
                        }
                        .subscribe(
                            {
                                Timber.v( "batch connect complete")
                            },
                            { err ->
                                Timber.e( "batch connect error")
                            }
                        )
                }

                else -> v
            }
        }

    }
}