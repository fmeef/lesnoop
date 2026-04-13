package net.ballmerlabs.lesnoop.db

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RenameTable
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.AutoMigrationSpec
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import net.ballmerlabs.lesnoop.db.entity.Characteristic
import net.ballmerlabs.lesnoop.db.entity.DbLocation
import net.ballmerlabs.lesnoop.db.entity.DbScanResult
import net.ballmerlabs.lesnoop.db.entity.Descriptor
import net.ballmerlabs.lesnoop.db.entity.DiscoveredService
import net.ballmerlabs.lesnoop.db.entity.Metrics
import net.ballmerlabs.lesnoop.db.entity.MetricsScanResultMapping
import net.ballmerlabs.lesnoop.db.entity.ServiceScanResultMapping
import java.util.UUID

val MIGATION_2_3 = object : Migration(2, 3) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("DROP INDEX index_characteristics_uuid_parentService")
        database.execSQL("ALTER TABLE characteristics RENAME TO characteristics_old")
        database.execSQL(
            "CREATE TABLE IF NOT EXISTS `characteristics` " +
                    "(`uid` INTEGER PRIMARY KEY AUTOINCREMENT, `uuid` TEXT NOT NULL, `parentService` INTEGER" +
                    ")"
        )
        database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_characteristics_uuid_parentService` ON `characteristics` (`uuid`, `parentService`)")


        val c = database.query("SELECT * FROM characteristics_old")
        while (c.moveToNext()) {
            val uuidindex = c.getColumnIndex("uuid")
            val uidindex = c.getColumnIndex("uid")
            val parentIndex = c.getColumnIndex("parentService")
            val blob = c.getBlob(uuidindex)
            val uuid = UUID.nameUUIDFromBytes(blob)
            val content = ContentValues().apply {
                put("uid", c.getLong(uidindex))
                put("parentService", c.getLong(parentIndex))
                put("uuid", uuid.toString())

            }
            database.insert("characteristics", SQLiteDatabase.CONFLICT_ABORT, content)
        }

        database.execSQL("DROP TABLE characteristics_old")


        database.execSQL("ALTER TABLE discovered_services RENAME TO discovered_services_old")
        database.execSQL("CREATE TABLE IF NOT EXISTS `discovered_services` (`uid` TEXT NOT NULL, PRIMARY KEY(`uid`))")

        val c2 = database.query("SELECT * FROM discovered_services_old")
        while (c2.moveToNext()) {
            val uidindex = c2.getColumnIndex("uid")
            val blob = c2.getBlob(uidindex)
            val uuid = UUID.nameUUIDFromBytes(blob)
            val content = ContentValues().apply {
                put("uid", uuid.toString())

            }
            database.insert("discovered_services", SQLiteDatabase.CONFLICT_ABORT, content)
        }

        database.execSQL("DROP TABLE discovered_services_old")


        database.execSQL("DROP INDEX index_descriptors_parentCharacteristic_uuid")
        database.execSQL("ALTER TABLE descriptors RENAME TO descriptors_old")
        database.execSQL("CREATE TABLE IF NOT EXISTS `descriptors` (`uid` INTEGER PRIMARY KEY AUTOINCREMENT, `parentCharacteristic` INTEGER, `uuid` TEXT NOT NULL)")
        database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_descriptors_parentCharacteristic_uuid` ON `descriptors` (`parentCharacteristic`, `uuid`)")

        val c3 = database.query("SELECT * FROM descriptors_old")
        while (c3.moveToNext()) {
            val uuidindex = c3.getColumnIndex("uuid")
            val uidindex = c3.getColumnIndex("uid")
            val parentIndex = c3.getColumnIndex("parentCharacteristic")
            val blob = c3.getBlob(uuidindex)
            val uuid = UUID.nameUUIDFromBytes(blob)
            val content = ContentValues().apply {
                put("uid", c3.getLong(uidindex))
                put("parentCharacteristic", c3.getLong(parentIndex))
                put("uuid", uuid.toString())

            }
            database.insert("descriptors", SQLiteDatabase.CONFLICT_ABORT, content)
        }

        database.execSQL("DROP TABLE descriptors_old")

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
    version = 8,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 6, 7),
        AutoMigration(from = 7, to = 8)
        // AutoMigration(from = 5, to = 6, spec = ScanDatabase.AutoMigrationMappingRename::class)
    ]
)
@TypeConverters(UuidTypeConverter::class)
abstract class ScanDatabase : RoomDatabase() {
    abstract fun scanResultsDao(): ScanResultDao

    @RenameTable(fromTableName = "ServiceScanResultMapping", toTableName = "scan_service_mapping")
    class AutoMigrationMappingRename : AutoMigrationSpec
}