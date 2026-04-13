package net.ballmerlabs.lesnoop.db

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.polidea.rxandroidble3.PhyPair
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import net.ballmerlabs.lesnoop.db.entity.Characteristic
import net.ballmerlabs.lesnoop.db.entity.DbScanResult
import net.ballmerlabs.lesnoop.db.entity.Descriptor
import net.ballmerlabs.lesnoop.db.entity.DiscoveredService
import net.ballmerlabs.lesnoop.db.entity.Metrics
import net.ballmerlabs.lesnoop.db.entity.ServiceScanResultMapping
import net.ballmerlabs.lesnoop.db.entity.ServicesWithChildren
import timber.log.Timber

@Dao
interface ScanResultDao {

    @Transaction
    @Query("SELECT * FROM discovered_services")
    fun getServices(): Single<List<ServicesWithChildren>>

    @Query("SELECT COUNT(DISTINCT macAddress) FROM scan_results")
    fun scanResultCount(): Observable<Int>

    @Query("SELECT * FROM scan_results")
    fun getScanResults(): Observable<List<DbScanResult>>

    @Query("SELECT macAddress FROM scan_results")
    fun getMacs(): Observable<List<String>>

    @Insert
    fun insertScanResult(scanResult: DbScanResult): Single<Long>

    @Query("SELECT COUNT(*) FROM scan_results WHERE macAddress = :mac")
    fun countMac(mac: String): Single<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertCharacteristic(characteristic: Characteristic): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertDescriptors(descriptors: List<Descriptor>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertDiscoveredService(service: DiscoveredService): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertMapping(mapping: ServiceScanResultMapping)

    @Update
    fun updateScanResult(scanResult: DbScanResult): Completable

    @Insert
    fun insertMetrics(metrics: Metrics): Completable

    @Query("SELECT * FROM metrics ORDER BY date DESC LIMIT 1")
    fun getTopMetrics(): Maybe<Metrics>

    @Query("SELECT * FROM metrics ORDER BY date DESC LIMIT 1")
    fun observeTopMetrics(): Observable<Metrics>

    @Query("INSERT INTO metrics DEFAULT VALUES")
    fun newMetricsSession(): Completable

    @Update
    fun updateMetrics(metrics: Metrics): Completable

    @Query(
        "UPDATE OR IGNORE metrics SET old_count = old_count+1 WHERE run = (" + "SELECT run FROM metrics ORDER BY date DESC LIMIT 1)"
    )
    fun incrementOldCount(): Completable

    @Query(
        "UPDATE OR IGNORE metrics SET new_count = new_count+1 WHERE run = (" + "SELECT run FROM metrics ORDER BY date DESC LIMIT 1)"
    )
    fun incrementNewCount(): Completable


    @Query(
        "UPDATE OR IGNORE metrics SET connected = connected+1 WHERE run = (" + "SELECT run FROM metrics ORDER BY date DESC LIMIT 1)"
    )
    fun incrementConnected(): Completable

    @Insert
    fun insertService(
        services: List<ServicesWithChildren>,
        scanResult: Long? = null,
        phy: PhyPair? = null
    ) {
        for(service in services ) {
           insertDiscoveredService(service.discoveredService)
            for (char in service.characteristics) {
                char.characteristic.parentService = service.discoveredService.uid
                try {
                    val l = insertCharacteristic(char.characteristic)

                    try {
                        insertDescriptors(char.descriptors.map { v ->
                            v.parentCharacteristic = l
                            v
                        })
                    } catch (exc: SQLiteConstraintException) {
                        Timber.w("constraint exception $exc on descriptor ${service.discoveredService.uid} $l")
                    }
                    if (scanResult != null) {
                        try {
                            insertMapping(
                                ServiceScanResultMapping(
                                    service = service.discoveredService.uid, scanResult = scanResult
                                )
                            )
                        }  catch (exc: SQLiteConstraintException) {
                            Timber.w("constraint exception $exc on result mapping ${service.discoveredService.uid}")
                        }
                    }
                } catch (exc: SQLiteConstraintException) {
                    Timber.w("constraint exception $exc on characteristic ${service.discoveredService.uid}")
                }

            }
        }
    }
}