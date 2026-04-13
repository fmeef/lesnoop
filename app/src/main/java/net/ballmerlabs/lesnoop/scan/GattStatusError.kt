package net.ballmerlabs.lesnoop.scan

import com.welie.blessed.GattStatus

class GattStatusError(
    val status: GattStatus
): Throwable() {
    override fun toString(): String {
        return "Gatt status: $status"
    }
}