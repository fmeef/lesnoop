package net.ballmerlabs.lesnoop.scan

import com.jakewharton.rxrelay3.PublishRelay
import com.welie.blessed.BluetoothCentralManager
import com.welie.blessed.BluetoothPeripheral
import com.welie.blessed.BluetoothPeripheralCallback
import com.welie.blessed.GattStatus
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Completable
import net.ballmerlabs.lesnoop.BlessedPeripheralScope
import javax.inject.Inject

@BlessedPeripheralScope
class RxBlessedPeripheral @Inject constructor(
    val currentPeripheral: BluetoothPeripheral,
    val manager: BluetoothCentralManager
) {
    private val connectionStateRelay = PublishRelay.create<GattStatus>()

    fun connect(): Completable {
        return connectionStateRelay
            .mergeWith(Completable.fromCallable {
                manager.connect(currentPeripheral, callback)
            }.subscribeOn(AndroidSchedulers.mainThread()))
            .firstOrError().flatMapCompletable { v -> when (v) {
                GattStatus.SUCCESS -> Completable.complete()
                else -> Completable.error(GattStatusError(v))
            } }
    }

    val callback = object : BluetoothPeripheralCallback() {
        override fun onConnectionUpdated(
            peripheral: BluetoothPeripheral,
            interval: Int,
            latency: Int,
            timeout: Int,
            status: GattStatus
        ) {
            super.onConnectionUpdated(peripheral, interval, latency, timeout, status)
            if (peripheral.address == currentPeripheral.address && connectionStateRelay.hasObservers()) {
                connectionStateRelay.accept(status)
            }
        }
    }

}