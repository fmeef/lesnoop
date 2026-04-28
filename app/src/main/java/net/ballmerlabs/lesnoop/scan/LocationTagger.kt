package net.ballmerlabs.lesnoop.scan

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Criteria
import android.location.Location
import android.location.LocationManager
import android.location.LocationRequest
import android.os.Build
import android.os.CancellationSignal
import android.provider.Settings
import androidx.core.app.ActivityCompat
import com.polidea.rxandroidble3.scan.ScanResult
import dagger.hilt.android.qualifiers.ApplicationContext
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.core.Scheduler
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.subjects.BehaviorSubject
import net.ballmerlabs.lesnoop.Module
import net.ballmerlabs.lesnoop.db.entity.DbScanResult
import timber.log.Timber
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

fun Context.checkAirplaneMode(): Boolean {
    return Settings.Global.getInt(contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) != 0
}

@Singleton
class LocationTagger @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val locationService: LocationManager,
    @param:Named(Module.TIMEOUT_SCHEDULER) val timeoutScheduler: Scheduler,
    @param:Named(Module.EXECUTOR_COMPUTE) private val executor: Executor
) {
    private val locationDisposable = AtomicReference<Disposable?>()
    val locationSubject = BehaviorSubject.create<String>()

    private val location = AtomicReference<Location?>(null)



    fun startLocationPoll() {
        Timber.w( "startLocationPoll")
        val obs = Completable.timer(1, TimeUnit.SECONDS, timeoutScheduler).andThen(
            getLocation()
        )
            .ignoreElement()
            .repeat()
            .retry()
            .subscribe({ }, { err ->
                Timber.e("failed to update location: $err")
            })
        locationDisposable.getAndSet(obs)?.dispose()
    }

    fun stopLocationPoll() {
        Timber.w( "stopLocationPoll")
        locationDisposable.getAndSet(null)?.dispose()
    }


    fun postLocation(location: Location?) {
        this.location.set(location)
        locationSubject.onNext(this.location.get()?.provider?:"none")
    }


    fun getLocation(): Maybe<Location> {
        return Maybe.create { m ->
            val provider =  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                LocationManager.FUSED_PROVIDER
            else
                LocationManager.GPS_PROVIDER

            Timber.w("starting with provider $provider")
            val signal = CancellationSignal()
            m.setCancellable {
                Timber.w("location canceled!")
                signal.cancel()
            }
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED || ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    locationService.getCurrentLocation(provider,
                        LocationRequest.Builder(1000)
                            .setQuality(LocationRequest.QUALITY_BALANCED_POWER_ACCURACY)
                            .build(),signal, executor) { l ->
                        if (l != null) {
                            m.onSuccess(l)
                        } else {
                            Timber.w("got null location")
                            m.onComplete()
                        }
                    }
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    locationService.getCurrentLocation(provider, signal, executor) { l ->
                        if (l != null) {
                            m.onSuccess(l)
                        } else {
                            Timber.w("got null location")
                            m.onComplete()
                        }
                    }
                }else {
                    locationService.requestSingleUpdate(provider, { l ->
                        m.onSuccess(l)
                    }, null)
                }
            } else {
                m.onError(SecurityException("missing location permission"))
            }
        }
            .doOnError { err ->
                Timber.e( "location error: $err")
                postLocation(null)
            }
            .doOnSuccess { l ->
                Timber.v( "setLocation: ${l.provider}")
                postLocation(l)
            }

    }

    fun tagLocation(scanResult: ScanResult, phyVal: Int?): Single<DbScanResult> {
        return Single.defer {
            val loc = location.get()
            if (loc != null) {
                Single.just(DbScanResult(scanResult, loc, phyVal))
            } else {
                Single.just(DbScanResult(scanResult,  null, phyVal))
            }
        }
    }
}