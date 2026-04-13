package net.ballmerlabs.lesnoop.db.entity

import android.location.Location
import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey

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
    @Embedded val location: DbLocation? = null

) {
    constructor(scanResult: com.polidea.rxandroidble3.scan.ScanResult, location: Location? = null, phy: Int? = null): this(
        macAddress = scanResult.bleDevice.macAddress,
        name = scanResult.bleDevice.name,
        rssi = scanResult.rssi,
        scanPhy = phy,
        scanRecord = scanResult.scanRecord.bytes,
        location = if (location != null) DbLocation(location) else null
    )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DbScanResult

        if (uid != other.uid) return false
        if (macAddress != other.macAddress) return false
        if (name != other.name) return false
        if (rssi != other.rssi) return false
        if (!scanRecord.contentEquals(other.scanRecord)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = uid.hashCode()
        result = 31 * result + macAddress.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + rssi
        result = 31 * result + scanRecord.contentHashCode()
        return result
    }
}