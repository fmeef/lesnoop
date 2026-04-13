package net.ballmerlabs.lesnoop.scan

import com.welie.blessed.BluetoothCentralManager
import com.welie.blessed.BluetoothPeripheral
import io.reactivex.rxjava3.core.Single
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RxBlessedCentralManager @Inject constructor(
    val centralManager: BluetoothCentralManager,
    val peripheralBuilder: RxBlessedPeripheralSubcomponent.Builder,
) {

    fun connect(mac: String): Single<RxBlessedPeripheralSubcomponent> {
        return Single.defer {
            connect(centralManager.getPeripheral(mac))
        }
    }

    fun connect(peripheral: BluetoothPeripheral): Single<RxBlessedPeripheralSubcomponent> {
        return Single.defer {
            val manager = peripheralBuilder.peripheral(peripheral).build()!!
            manager.peripheral().connect().toSingleDefault(manager)
        }

   }
}