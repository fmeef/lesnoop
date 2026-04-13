package net.ballmerlabs.lesnoop.scan

import com.polidea.rxandroidble3.Timeout
import io.reactivex.rxjava3.core.Completable
import net.ballmerlabs.lesnoop.ScannerFactory
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InsertQueue @Inject constructor(
    scanner: ScannerFactory
) : AbstractQueue<Completable>(scanner) {
    override val delay: Timeout
        get() = Timeout(0, TimeUnit.SECONDS)
    override fun process(item: Completable): Completable {
        return item.doOnComplete { Timber.v( "insert without connect!") }
    }
}