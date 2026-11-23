package net.ballmerlabs.lesnoop.db

import android.content.Context
import android.net.Uri
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
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
import java.util.UUID

@Dao
interface ScanResultDao {

    @Transaction
    @Query("SELECT * FROM discovered_services")
    fun getServices(): Single<List<ServicesWithChildren>>

    @Query("SELECT COUNT(DISTINCT macAddress) FROM scan_results")
    fun scanResultCount(): Observable<Int>

    @Query("SELECT * FROM scan_results")
    fun getScanResults(): Observable<List<DbScanResult>>

    @Query("SELECT DISTINCT macAddress FROM scan_results")
    fun getMacs(): Observable<List<String>>


    @Query("SELECT connected FROM scan_results WHERE connected AND macAddress = :mac")
    fun getConnected(mac: String): Maybe<Boolean>


    fun attemptConnect(mac: String): Maybe<Boolean> {
        return setConnectAttempted(mac).andThen(getConnected(mac))
    }

    @Query("UPDATE scan_results SET connected = '1' WHERE macAddress = :mac")
    fun setConnected(mac: String): Completable

    @Query("UPDATE scan_results SET connectAttempted = '1' WHERE macAddress = :mac")
    fun setConnectAttempted(mac: String): Completable

    @Insert
    fun insertScanResult(scanResult: DbScanResult): Single<Long>

    @Query("SELECT COUNT(*) FROM scan_results WHERE macAddress = :mac")
    fun countMac(mac: String): Single<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertCharacteristic(characteristic: Characteristic): Single<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertDescriptors(descriptors: List<Descriptor>): Completable

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertDescriptor(descriptors: Descriptor): Completable

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertDiscoveredService(service: DiscoveredService): Single<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertMapping(mapping: ServiceScanResultMapping): Single<Long>

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

    @Query(
        "UPDATE OR IGNORE metrics SET error = error+1, error_text = :text WHERE run = (" + "SELECT run FROM metrics ORDER BY date DESC LIMIT 1)"
    )
    fun incrementError(text: String): Completable

    @Query(
        "SELECT * FROM discovered_services INNER JOIN scan_service_mapping ON uid =  scanResult" +
                " WHERE scanResult = :id"
    )
    fun getServicesForResult(id: Long): Single<List<DiscoveredService>>

    @Query("SELECT * FROM characteristics WHERE parentService = :service")
    fun getCharacteristicsForService(service: UUID): Single<List<Characteristic>>

    @Query("SELECT * FROM descriptors WHERE parentCharacteristic = :char")
    fun getDescriptorsForCharacteristic(char: Long): Single<List<Descriptor>>

    @Query("DELETE FROM discovered_services")
    fun deleteDiscoveredServices()

    @Query("DELETE FROM characteristics")
    fun deleteCharacteristics()

    @Query("DELETE FROM descriptors")
    fun deleteDescriptors()

    @Query("DELETE FROM scan_results")
    fun deleteScanResults()


    @Transaction
    fun deleteStore() {
        deleteScanResults()
        deleteDiscoveredServices()
        deleteCharacteristics()
        deleteDescriptors()
    }

    fun mergeUri(uri: Uri, ctx: Context): Completable {
        val db = ScanDatabase.builder(ctx, "Import")
            .createFromInputStream({ ctx.contentResolver.openInputStream(uri) })
            .build()
        val altDao = db.scanResultsDao()
        return altDao.getScanResults().firstOrError()
            .flatMapObservable { results -> Observable.fromIterable(results) }
            .flatMapCompletable { result ->
                val uid = result.uid!!
                result.uid = null
                insertScanResult(result).flatMapCompletable { id ->
                    altDao.getServicesForResult(uid).flatMapCompletable { services ->
                        Observable.fromIterable(services)
                            .flatMapCompletable { service ->
                                val mapping = ServiceScanResultMapping(
                                    service = service.uid,
                                    scanResult = id
                                )
                                insertMapping(mapping).ignoreElement()
                                    .andThen(altDao.getCharacteristicsForService(service.uid))
                                    .flatMapCompletable { chars ->
                                        Observable.fromIterable(chars)
                                            .flatMapCompletable { ch ->
                                                insertCharacteristic(ch).flatMapCompletable { chid ->
                                                    altDao.getDescriptorsForCharacteristic(ch.uid!!)
                                                        .flatMapCompletable { des ->
                                                            Observable.fromIterable(des)
                                                                .flatMapCompletable { d ->
                                                                    d.parentCharacteristic =
                                                                        chid
                                                                    insertDescriptor(d)
                                                                }
                                                        }
                                                }
                                            }
                                    }
                            }
                    }
                }
            }
    }
}