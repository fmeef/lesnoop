package net.ballmerlabs.lesnoop.scan

import com.polidea.rxandroidble3.RxBleDevice
import com.polidea.rxandroidble3.Timeout
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.subjects.PublishSubject
import net.ballmerlabs.lesnoop.ScannerFactory
import timber.log.Timber
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

data class QueueItem<T>(
    val device: RxBleDevice,
    val obs: T
)

abstract class AbstractQueue<T : Any>(
    val scanner: ScannerFactory
) {
    open val delay = Timeout(10, TimeUnit.SECONDS)
    private val connectQueueDisposable = AtomicReference<Disposable?>(null)
    val size = AtomicInteger(0)
    open val maxSize = 8
    val processSet = mutableSetOf<String>()

    val lock = PublishSubject.create<QueueItem<T>>()

    fun accept(device: RxBleDevice, item: T) {
        size.incrementAndGet()
        lock.onNext(
            QueueItem(
                device = device,
                obs = item
            )
        )
    }

    abstract fun process(item: T): Completable

    fun processDumb() {
        val disp = lock
            .flatMapCompletable { i ->
                Completable.defer {
                    process(i.obs)
                        .onErrorComplete()
                }
            }
            .subscribe(
                { Timber.e( "queue completed") },
                { err -> Timber.e( "queue error: $err") }
            )
        connectQueueDisposable.getAndSet(disp)?.dispose()
    }

    fun processSmart() {
        val disp = lock
            .distinct({ i -> i.device.macAddress }, {
                if (processSet.size > 256)
                    processSet.clear()
                processSet
            })
            .concatMapCompletable { i ->
                val size = size.decrementAndGet()
                if (size > maxSize) {
                    Timber.v( "skipping due to max size $size")
                    Completable.complete()
                } else {
                    //scanner.createScanner().pauseScan()
                    Timber.v( "processing queue item capacity=$size")
                    process(i.obs)
                        .onErrorComplete()
                        .doFinally {
                        //    scanner.createScanner().unpauseScan()
                        }
                }
            }
            .subscribe(
                { Timber.e( "queue completed") },
                { err -> Timber.e( "queue error: $err") }
            )
        connectQueueDisposable.getAndSet(disp)?.dispose()
    }

    fun stopProcess() {
        connectQueueDisposable.getAndSet(null)?.dispose()
    }
}