package net.ballmerlabs.lesnoop.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "metrics_to_scanresults",
    foreignKeys = [
        ForeignKey(
            entity = Metrics::class,
            parentColumns = [ "run" ],
            childColumns = [ "metricsRun" ],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = DbScanResult::class,
            parentColumns = [ "uid" ],
            childColumns = [ "scanResult" ],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE
        )
    ]
)
data class MetricsScanResultMapping(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val metricsRun: Long,
    val scanResult: Long
)