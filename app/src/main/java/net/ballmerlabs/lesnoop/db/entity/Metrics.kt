package net.ballmerlabs.lesnoop.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "metrics",
    indices = [
        Index("date", name = "metrics_unique_date", unique = true)
    ]
)
data class Metrics(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "run")
    val run: Long,
    @ColumnInfo(name = "date", defaultValue = "CURRENT_TIMESTAMP")
    val date: Long,
    @ColumnInfo(name = "old_count", defaultValue = "0")
    val oldCount: Long,
    @ColumnInfo(name = "new_count", defaultValue = "0")
    val newCount: Long,
    @ColumnInfo(name = "connected", defaultValue = "0")
    val connected: Long
)