package net.ballmerlabs.lesnoop.scan

import androidx.lifecycle.LiveData
import com.polidea.rxandroidble3.RxBleDevice
import com.polidea.rxandroidble3.scan.ScanResult
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.Disposable

interface Scanner : Disposable {
    fun startScan(legacy: Boolean): Observable<ScanResult>
    fun startScanAndDiscover(legacy: Boolean): Observable<ScanResult>
    fun scanBackground()
    fun stopScanBackground()
    fun scanForeground(legacy: Boolean)
    fun stopScanForeground()
    fun insertResult(scanResult: ScanResult): Single<Pair<Long, ScanResult>>
    fun discoverServices(scanResult: RxBleDevice, dbid: Long? = null): Single<Boolean>
    fun serviceState(): LiveData<Boolean>
    fun serviceRunning(): LiveData<Boolean>
    fun pauseScan()
    fun unpauseScan()
    fun setScanRunning(v: Boolean)
}