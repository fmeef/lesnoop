package net.ballmerlabs.lesnoop.scan

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.IBinder
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.switchMap
import androidx.preference.PreferenceManager
import com.polidea.rxandroidble3.PhyPair
import com.polidea.rxandroidble3.RxBleClient
import com.polidea.rxandroidble3.RxBleConnection
import com.polidea.rxandroidble3.RxBleDevice
import com.polidea.rxandroidble3.RxBlePhyOption
import com.polidea.rxandroidble3.Timeout
import com.polidea.rxandroidble3.exceptions.BleAlreadyConnectedException
import com.polidea.rxandroidble3.exceptions.BleDisconnectedException
import com.polidea.rxandroidble3.exceptions.BleScanException
import com.polidea.rxandroidble3.scan.ScanFilter
import com.polidea.rxandroidble3.scan.ScanResult
import com.polidea.rxandroidble3.scan.ScanSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Scheduler
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.disposables.Disposable
import kotlinx.coroutines.ExperimentalCoroutinesApi
import net.ballmerlabs.lesnoop.BackgroundScanService
import net.ballmerlabs.lesnoop.Module
import net.ballmerlabs.lesnoop.NonLegacyBroadcastReceiver
import net.ballmerlabs.lesnoop.PREF_BACKGROUND_SCAN
import net.ballmerlabs.lesnoop.ScanScope
import net.ballmerlabs.lesnoop.ScanSubcomponentFinalizer
import net.ballmerlabs.lesnoop.ScannerFactory
import net.ballmerlabs.lesnoop.ScannerFactory.Companion.PHY_1M
import net.ballmerlabs.lesnoop.ScannerFactory.Companion.PREF_LEGACY
import net.ballmerlabs.lesnoop.ScannerFactory.Companion.PREF_PRIMARY_PHY
import net.ballmerlabs.lesnoop.ScannerFactory.Companion.PREF_REPORT_DELAY
import net.ballmerlabs.lesnoop.ScannerFactory.Companion.PREF_REPORT_DELAY_ENABLED
import net.ballmerlabs.lesnoop.db.ScanResultDao
import net.ballmerlabs.lesnoop.db.entity.ServicesWithChildren
import net.ballmerlabs.lesnoop.newPendingIntent
import net.ballmerlabs.lesnoop.rxPrefs
import timber.log.Timber
import java.util.Date
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Named

private fun <T : Any> handleUndocumentedScanThrottling(
    e: Observable<Throwable>, defaultDelay: Long = 10
): Observable<T> {
    return e.concatMap { err ->
        if (err is BleScanException && err.retryDateSuggestion != null) {
            val delay = err.retryDateSuggestion!!.time - Date().time
            Timber.e( "undocumented scan throttling. Waiting $delay seconds")
            Completable.complete().delay(delay, TimeUnit.SECONDS).andThen(Observable.error(err))
        } else {
            Completable.complete().delay(defaultDelay, TimeUnit.SECONDS)
                .andThen(Observable.error(err))
        }
    }
}

@ScanScope
class ScannerImpl @Inject constructor(
    private val client: RxBleClient,
    private val database: ScanResultDao,
    @param:Named(Module.DB_SCHEDULER) private val dbScheduler: Scheduler,
    private val context: Context,
    private val locationTagger: LocationTagger,
    private val service: ScannerFactory,
    @param:Named(Module.TIMEOUT_SCHEDULER) private val timeoutScheduler: Scheduler,
    @param:Named(Module.CONNECT_SCHEDULER) private val connectScheduler: Scheduler,
    private val broadcastReceiverState: BroadcastReceiverState,
    @param:ApplicationContext val applicationContext: Context,
    val bluetoothManager: BluetoothManager,
    val finalizer: ScanSubcomponentFinalizer,
    val prefs: SharedPreferences
) : Scanner {
    private val disp = CompositeDisposable()
    private val scanRunning = AtomicBoolean(false)
    private lateinit var mService: BackgroundScanService
    private val foregroundDisp = AtomicReference<Disposable?>(null)
    private var mBound = MutableLiveData<Boolean>()
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance.
            val binder = service as BackgroundScanService.LocalBinder
            mService = binder.getService()
            mBound.postValue(true)
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            mBound.postValue(false)
        }
    }

    override fun setScanRunning(v: Boolean) {
        scanRunning.set(v)
    }

    override fun serviceState(): LiveData<Boolean> {
        return mBound.switchMap { v -> if (v) mService.running else MutableLiveData(false) }
    }

    override fun pauseScan() {
        val pendingIntent =
            newPendingIntent(applicationContext, NonLegacyBroadcastReceiver::class.java)
        val scanner = bluetoothManager.adapter?.bluetoothLeScanner
        if (ActivityCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.BLUETOOTH_SCAN
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        scanner?.stopScan(pendingIntent)
    }

    override fun unpauseScan() {
        Timber.v( "unpauseScan ${serviceState().value}")
        if (scanRunning.get()) {
            val pendingIntent =
                newPendingIntent(applicationContext, NonLegacyBroadcastReceiver::class.java)
            val scanner = bluetoothManager.adapter?.bluetoothLeScanner

            val legacy = prefs.getBoolean(PREF_LEGACY, false)
            val phy = prefs.getString(
                PREF_PRIMARY_PHY, PHY_1M
            ) ?: PHY_1M
            val phyVal = service.phyToVal(phy)
            val reportDelay = prefs.getLong(PREF_REPORT_DELAY, 3000)
            val reportDelayEnabled = prefs.getBoolean(PREF_REPORT_DELAY_ENABLED, false)
            val settings =
                android.bluetooth.le.ScanSettings.Builder()
                    .setScanMode(android.bluetooth.le.ScanSettings.SCAN_MODE_LOW_POWER)
                    .apply {
                        if (phyVal != null)
                            setPhy(phyVal)
                    }
                    .setLegacy(legacy)
                    .apply {
                        if (reportDelayEnabled)
                            setReportDelay(reportDelay)
                    }.setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
                    .setCallbackType(android.bluetooth.le.ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                    .build()
            if (ActivityCompat.checkSelfPermission(
                    applicationContext,
                    Manifest.permission.BLUETOOTH_SCAN
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
            scanner?.startScan(
                listOf(android.bluetooth.le.ScanFilter.Builder().build()),
                settings,
                pendingIntent
            )
        }
    }


    @OptIn(ExperimentalCoroutinesApi::class)
    private fun updatePrefScan(isScanning: Boolean) {
        context.rxPrefs.updateDataAsync { prefs ->
            val p = prefs.toMutablePreferences()
            p[PREF_BACKGROUND_SCAN] = isScanning
            Single.just(p)
        }.subscribeOn(dbScheduler).timeout(1, TimeUnit.SECONDS).ignoreElement().subscribe(
            { Log.v(NAME, "updated prefs") },
            { err -> Log.e(NAME, "failed to update prefs $err") })
    }

    override fun serviceRunning(): LiveData<Boolean> {
        return mService.running
    }

    fun getStartIntent(): Intent {

        return Intent(applicationContext, BackgroundScanService::class.java)
    }

    override fun scanBackground() {
        val intent = getStartIntent()

        context.applicationContext.startForegroundService(intent)
        context.applicationContext.bindService(intent, connection, 0)
        updatePrefScan(true)
    }

    override fun stopScanBackground() {
        //ontext.unregisterReceiver(reciever)
        try {
            context.applicationContext.stopService(
                Intent(context.applicationContext, BackgroundScanService::class.java)
            )
            broadcastReceiverState.abortConnect()
        } catch (exc: IllegalArgumentException) {
            Timber.tag("debug").w("service already stopped: $exc")
        }
        updatePrefScan(false)
    }

    override fun insertResult(scanResult: ScanResult): Single<Pair<Long, ScanResult>> {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val phy = prefs.getString(PREF_PRIMARY_PHY, null)
        val phyVal = service.phyToVal(phy)

        return locationTagger.tagLocation(scanResult, phyVal)
            .flatMap { scanResult ->
                database.countMac(scanResult.macAddress)
                    .subscribeOn(dbScheduler)
                    .flatMap { mac ->
                        if (mac > 0) {
                            database.incrementOldCount()
                                .subscribeOn(dbScheduler)
                        } else {
                            database.incrementNewCount()
                                .subscribeOn(dbScheduler)
                        }.toSingleDefault(scanResult)
                    }
            }.flatMap { result ->
                database.insertScanResult(result).doOnError { e -> Log.v(NAME, "insert error $e") }
                    .subscribeOn(dbScheduler)

            }.map { r -> Pair(r, scanResult) }
            .doOnError { err -> Timber.e( "failed to insert result $err") }
    }

    private fun <T : Any> smartRetry(connection: Observable<T>, times: Int): Observable<T> {
        return connection.retryWhen { errs: Observable<Throwable> ->
            errs.zipWith(
                Observable.range(
                    1, times
                )
            ) { err: Throwable, i: Int -> err }.flatMapSingle { err ->
                when (err) {
                    is BleDisconnectedException -> {
                        when (err.state) {
                            133 -> Single.timer(
                                250, TimeUnit.MILLISECONDS, connectScheduler
                            )
                            //147 -> Single.timer(30, TimeUnit.SECONDS, connectScheduler)
                            //      .flatMap { Single.error(err) }
                            // Unknown gatt error
                            257 -> Single.timer(
                                10, TimeUnit.SECONDS, connectScheduler
                            ) // Weird connect throttling
                            else -> Single.error(err)
                        }
                    }

                    is BleAlreadyConnectedException -> {
                        Single.error(err)
                    }

                    else -> Single.error(err)
                }
            }
        }
    }

    private fun <T : Any> smartRetry(connection: Single<T>, times: Int): Single<T> {
        return connection.retryWhen { errs: Flowable<Throwable> ->
            errs.zipWith(
                Flowable.range(
                    1, times
                )
            ) { err: Throwable, i: Int -> err }.flatMapSingle { err ->
                when (err) {
                    is BleDisconnectedException -> {
                        when (err.state) {
                            133 -> Single.timer(
                                200, TimeUnit.MILLISECONDS, connectScheduler
                            ) // Unknown gatt error // GATT_CONN_TERMINATE_LOCAL_HOST
                            257 -> Single.timer(
                                5, TimeUnit.SECONDS, connectScheduler
                            ) // Weird connect throttlin
                            else -> Single.error(err)
                        }
                    }

                    is BleAlreadyConnectedException -> {
                        Single.error(err)
                    }

                    else -> Single.error(err)
                }
            }
        }
    }

    override fun scanForeground(legacy: Boolean) {
        val disp = startScanAndDiscover(legacy).subscribe(
            { res ->
                Timber.v(
                    "foreground scan result: $res"
                )
            },
            { err -> Timber.e( "foreground scan error: $err") })

        foregroundDisp.getAndSet(disp)?.dispose()
    }

    override fun stopScanForeground() {
        foregroundDisp.getAndSet(null)?.dispose()
    }

    private fun discoverWithPhy(
        connection: RxBleConnection, phyPair: PhyPair? = null
    ): Observable<ServicesWithChildren> {
        return connection.discoverServices()
            .flatMapObservable { s -> Observable.fromIterable(s.bluetoothGattServices) }
            .map { s -> ServicesWithChildren(s, phy = phyPair) }
    }

//
//    override fun discoverServices(scanResult: RxBleDevice, dbid: Long?): Single<Boolean> {
//        return centralManager.connect(scanResult.macAddress).map {
//            Timber.e("connected successfully!")
//            true
//        }
//
//    }

    private fun scanInternal(mac: String): Observable<ScanResult> {
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_FIRST_MATCH)
            .build()
        val filter = ScanFilter.Builder()
            .setDeviceAddress(mac)
            .build()
        return client.scanBleDevices(
            settings, filter
        ).doOnSubscribe { d -> disp.add(d) }
            .retryWhen { e -> handleUndocumentedScanThrottling<ScanResult>(e) }
            .doOnNext { r -> Timber.tag(NAME).v("scan result $r") }
            .doOnError { e -> Timber.tag(NAME).e("scan error $e") }
    }


    override fun discoverServices(scanResult: RxBleDevice, dbid: Long?): Single<Boolean> {
        return if (prefs.getBoolean(ScannerFactory.PREF_CONNECT, false)) {
            scanResult.establishConnection(
                true,
                Timeout(300, TimeUnit.SECONDS)
            ).timeout(60, TimeUnit.SECONDS, timeoutScheduler)
                .onErrorResumeNext { err: Throwable ->
                    when (err) {
                        is BleDisconnectedException -> when (err.state) {
                            135 -> scanResult.observeConnectionStateChanges()
                                .takeUntil { v ->  v == RxBleConnection.RxBleConnectionState.DISCONNECTED }
                                .ignoreElements()
                                .andThen( scanResult.establishConnection(false))
                            else -> Observable.error(err)
                        }
                        is TimeoutException -> scanResult.observeConnectionStateChanges()
                            .takeUntil { v ->  v == RxBleConnection.RxBleConnectionState.DISCONNECTED }
                            .ignoreElements()
                            .andThen( scanResult.establishConnection(false))
                        else -> Observable.error(err)
                    }
                }
                .doOnDispose { Timber.tag("debug").e("connection disposed") }
                .doOnSubscribe {
                    Timber.v( "establishConnection ${scanResult.macAddress}")
                }.flatMapSingle { c ->
                    val tx = service.phyToRxBle()
                    Timber.e(
                        "got connection ${scanResult.macAddress} $tx"
                    )
                    val obs = if (tx.isNotEmpty()) {
                        c.setPreferredPhy(
                            tx, tx, RxBlePhyOption.PHY_OPTION_NO_PREFERRED
                        ).flatMapObservable { phy ->
                            discoverWithPhy(c, phy)
                        }.doOnError { err ->
                            Timber.e( "failed to discover services: $err")
                        }
                    } else {
                        discoverWithPhy(c)
                    }.toList()

                    obs.flatMap { services ->
                        Timber.v( "got services: $services")
                        Single.fromCallable {
                            database.insertService(services, scanResult = dbid)
                            true
                        }.subscribeOn(dbScheduler)
                    }
                        .flatMap { v ->
                            database.incrementConnected()
                                .subscribeOn(dbScheduler)
                                .toSingleDefault(v)
                        }
                        .doOnSuccess {
                            Timber.v(
                                "successfully discovered services for ${scanResult.macAddress}"
                            )
                        }.doOnError { err ->
                            Timber.v(
                                "failed to connect $tx, continuing without: $err"
                            )
                        }
                }.firstOrError()
                .ignoreElement()
                .timeout(60, TimeUnit.SECONDS, timeoutScheduler)
                .doOnError { err ->
                    Timber.e(
                        "connection error ${scanResult.macAddress} $err"
                    )
                }
        } else {
            Completable.complete()
        } .onErrorResumeNext { err: Throwable ->
            scanResult.observeConnectionStateChanges().takeUntil { v ->  v == RxBleConnection.RxBleConnectionState.DISCONNECTED }
                .ignoreElements()
                .andThen(Completable.error(err))
            }
            .andThen(scanResult.observeConnectionStateChanges().takeUntil { v ->  v == RxBleConnection.RxBleConnectionState.DISCONNECTED }.ignoreElements())
            .timeout(300, TimeUnit.SECONDS, timeoutScheduler)
            .toSingleDefault(false)


    }


    private fun scanInternal(legacy: Boolean): Observable<ScanResult> {
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES).setLegacy(legacy).build()
        val filter = ScanFilter.Builder().build()
        return client.scanBleDevices(
            settings, filter
        ).doOnSubscribe { d -> disp.add(d) }
            .retryWhen { e -> handleUndocumentedScanThrottling<ScanResult>(e) }.repeat().retry()
            .doOnNext { r -> Timber.tag(NAME).v("scan result $r") }
            .doOnError { e -> Timber.tag(NAME).e("scan error $e") }
    }

    override fun startScan(legacy: Boolean): Observable<ScanResult> {
        return scanInternal(legacy).flatMapSingle { r ->
            insertResult(r).ignoreElement().toSingleDefault(r)
        }

    }

    override fun startScanAndDiscover(legacy: Boolean): Observable<ScanResult> {
        return scanInternal(legacy).flatMapSingle { result ->
            if (prefs.getBoolean(ScannerFactory.PREF_CONNECT, false)) {
                discoverServices(result.bleDevice).ignoreElement().onErrorComplete()
                    .toSingleDefault(result)
            } else {
                Single.just(result)
            }
        }
    }


    protected fun finalize() {
        finalizer.onFinalize()
    }

    override fun dispose() {
        disp.dispose()
    }

    override fun isDisposed(): Boolean {
        return disp.isDisposed
    }

    init {
        Timber.v( "scanner crated")
    }

    companion object {
        const val NAME = "Scanner"
    }
}