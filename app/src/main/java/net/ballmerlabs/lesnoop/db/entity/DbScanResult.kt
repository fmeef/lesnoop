package net.ballmerlabs.lesnoop.db.entity

import android.location.Location
import android.view.Display
import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(
    tableName = "scan_results"
)
data class DbScanResult(
    @PrimaryKey(autoGenerate = true)
    val uid: Long? = null,
    val macAddress: String,
    val name: String?,
    val rssi: Int,
    @ColumnInfo(defaultValue = "null")
    val scanPhy: Int?,
    val scanRecord: ByteArray,
    val timestamp: Long? = null,
    @Embedded val location: DbLocation? = null,
    val display: Int? = null,
    var legacy: Boolean? = null,
    var tag: String? = null,
    @ColumnInfo(defaultValue = "0")
    var connected: Boolean = false,
    @ColumnInfo(defaultValue = "0")
    var connectAttempted: Boolean = false

) {
    constructor(
        scanResult: com.polidea.rxandroidble3.scan.ScanResult,
        location: Location? = null,
        phy: Int? = null,
        timestamp: Long? = Date().time,
        display: Int? = null
    ): this(
        macAddress = scanResult.bleDevice.macAddress,
        name = scanResult.bleDevice.name,
        rssi = scanResult.rssi,
        scanPhy = phy,
        scanRecord = scanResult.scanRecord.bytes,
        location = if (location != null) DbLocation(location) else null,
        timestamp = Date().time
    )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DbScanResult

        if (uid != other.uid) return false
        if (rssi != other.rssi) return false
        if (scanPhy != other.scanPhy) return false
        if (timestamp != other.timestamp) return false
        if (display != other.display) return false
        if (legacy != other.legacy) return false
        if (macAddress != other.macAddress) return false
        if (name != other.name) return false
        if (!scanRecord.contentEquals(other.scanRecord)) return false
        if (location != other.location) return false

        return true
    }

    override fun hashCode(): Int {
        var result = uid?.hashCode() ?: 0
        result = 31 * result + rssi
        result = 31 * result + (scanPhy ?: 0)
        result = 31 * result + (timestamp?.hashCode() ?: 0)
        result = 31 * result + (display ?: 0)
        result = 31 * result + (legacy?.hashCode() ?: 0)
        result = 31 * result + macAddress.hashCode()
        result = 31 * result + (name?.hashCode() ?: 0)
        result = 31 * result + scanRecord.contentHashCode()
        result = 31 * result + (location?.hashCode() ?: 0)
        return result
    }
}