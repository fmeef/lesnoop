package net.ballmerlabs.lesnoop.db

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RenameTable
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.AutoMigrationSpec
import androidx.room.migration.Migration
import androidx.room.useWriterConnection
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import kotlinx.coroutines.Dispatchers
import java.io.File
import net.ballmerlabs.lesnoop.db.entity.Characteristic
import net.ballmerlabs.lesnoop.db.entity.DbLocation
import net.ballmerlabs.lesnoop.db.entity.DbScanResult
import net.ballmerlabs.lesnoop.db.entity.Descriptor
import net.ballmerlabs.lesnoop.db.entity.DiscoveredService
import net.ballmerlabs.lesnoop.db.entity.Metrics
import net.ballmerlabs.lesnoop.db.entity.MetricsScanResultMapping
import net.ballmerlabs.lesnoop.db.entity.ServiceScanResultMapping
import java.util.UUID

/**
 * Copies `oldTable` into `newTable`, converting the blob `uuid` column (named by [uuidColumn])
 * into a string UUID. [longColumns] are copied as-is.
 */
private fun copyConvertingUuid(
    connection: SQLiteConnection,
    oldTable: String,
    newTable: String,
    uuidColumn: String,
    longColumns: List<String>,
) {
    val columns = longColumns + uuidColumn
    val placeholders = columns.joinToString(", ") { "?" }
    connection.prepare("SELECT ${columns.joinToString(", ")} FROM $oldTable").use { select ->
        connection.prepare(
            "INSERT INTO $newTable (${columns.joinToString(", ")}) VALUES ($placeholders)"
        ).use { insert ->
            while (select.step()) {
                longColumns.forEachIndexed { i, _ ->
                    insert.bindLong(i + 1, select.getLong(i))
                }
                val uuid = UUID.nameUUIDFromBytes(select.getBlob(longColumns.size))
                insert.bindText(columns.size, uuid.toString())
                insert.step()
                insert.reset()
                insert.clearBindings()
            }
        }
    }
}

val MIGATION_2_3 = object : Migration(2, 3) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("DROP INDEX index_characteristics_uuid_parentService")
        connection.execSQL("ALTER TABLE characteristics RENAME TO characteristics_old")
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `characteristics` " +
                    "(`uid` INTEGER PRIMARY KEY AUTOINCREMENT, `uuid` TEXT NOT NULL, `parentService` INTEGER" +
                    ")"
        )
        connection.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_characteristics_uuid_parentService` ON `characteristics` (`uuid`, `parentService`)")
        copyConvertingUuid(
            connection, "characteristics_old", "characteristics",
            uuidColumn = "uuid", longColumns = listOf("uid", "parentService")
        )
        connection.execSQL("DROP TABLE characteristics_old")

        connection.execSQL("ALTER TABLE discovered_services RENAME TO discovered_services_old")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `discovered_services` (`uid` TEXT NOT NULL, PRIMARY KEY(`uid`))")
        copyConvertingUuid(
            connection, "discovered_services_old", "discovered_services",
            uuidColumn = "uid", longColumns = listOf()
        )
        connection.execSQL("DROP TABLE discovered_services_old")

        connection.execSQL("DROP INDEX index_descriptors_parentCharacteristic_uuid")
        connection.execSQL("ALTER TABLE descriptors RENAME TO descriptors_old")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `descriptors` (`uid` INTEGER PRIMARY KEY AUTOINCREMENT, `parentCharacteristic` INTEGER, `uuid` TEXT NOT NULL)")
        connection.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_descriptors_parentCharacteristic_uuid` ON `descriptors` (`parentCharacteristic`, `uuid`)")
        copyConvertingUuid(
            connection, "descriptors_old", "descriptors",
            uuidColumn = "uuid", longColumns = listOf("uid", "parentCharacteristic")
        )
        connection.execSQL("DROP TABLE descriptors_old")
    }
}

class UuidTypeConverter {
    @TypeConverter
    fun uuidToString(uuid: UUID): String {
        return uuid.toString()
    }

    @TypeConverter
    fun stringToUUID(string: String): UUID {
        return UUID.fromString(string)
    }
}

@Database(
    entities = [
        DbScanResult::class,
        Characteristic::class,
        DiscoveredService::class,
        Descriptor::class,
        DbLocation::class,
        ServiceScanResultMapping::class,
        Metrics::class,
        MetricsScanResultMapping::class
    ],
    exportSchema = true,
    version = 16,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 6, 7),
        AutoMigration(from = 7, to = 8),
        AutoMigration(from = 8, to = 9),
        AutoMigration(from = 9, to = 10),
        AutoMigration(from = 10, to = 11),
        AutoMigration(from = 11, to = 12),
        AutoMigration(from = 12, to = 13),
        AutoMigration(from = 13, to = 14),
        AutoMigration(from = 14, to = 15),
        AutoMigration(from = 14, to = 16),
        // AutoMigration(from = 5, to = 6, spec = ScanDatabase.AutoMigrationMappingRename::class)
    ]
)
@TypeConverters(UuidTypeConverter::class)
abstract class ScanDatabase : RoomDatabase() {
    abstract fun scanResultsDao(): ScanResultDao

    @RenameTable(fromTableName = "ServiceScanResultMapping", toTableName = "scan_service_mapping")
    class AutoMigrationMappingRename : AutoMigrationSpec

    /**
     * Writes a consistent, self-contained snapshot of the database (no -wal/-shm needed) to [dest]
     * using SQLite's VACUUM INTO. Safe to call while other connections are writing.
     */
    suspend fun exportTo(dest: File) {
        dest.delete() // VACUUM INTO refuses to overwrite an existing non-empty file
        useWriterConnection { connection ->
            connection.usePrepared("VACUUM INTO ?") { stmt ->
                stmt.bindText(1, dest.absolutePath)
                stmt.step()
            }
        }
    }

    companion object {
        const val NAME = "ScanDatabase"

        fun builder(ctx: Context, name: String): Builder<ScanDatabase> =
            Room.databaseBuilder(ctx, ScanDatabase::class.java, name)
                .setDriver(BundledSQLiteDriver())
                .setQueryCoroutineContext(Dispatchers.IO)
                .addMigrations(MIGATION_2_3)
    }
}