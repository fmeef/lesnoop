package net.ballmerlabs.lesnoop.db.entity

import android.location.Location
import androidx.room.Entity

@Entity(
    tableName = "location",
    primaryKeys = [
        "latitude",
        "longitude",
        "altitude"
    ]
)
data class DbLocation(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double
) {
    constructor(location: Location): this(
        latitude = location.latitude,
        longitude = location.longitude,
        altitude = location.altitude
    )
}