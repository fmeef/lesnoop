package net.ballmerlabs.lesnoop.scan

import android.bluetooth.le.ScanResult
import com.welie.blessed.BluetoothCentralManagerCallback
import com.welie.blessed.BluetoothPeripheral
import com.welie.blessed.HciStatus
import com.welie.blessed.ScanFailure
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RxBlessedCentralCallback @Inject constructor() : BluetoothCentralManagerCallback() {

}