package net.ballmerlabs.lesnoop

import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.SharedPreferences
import android.location.LocationManager
import android.os.Handler
import android.os.PowerManager
import androidx.preference.PreferenceManager
import androidx.room.Room
import com.polidea.rxandroidble3.RxBleClient
import com.welie.blessed.BluetoothCentralManager
import dagger.Binds
import net.ballmerlabs.lesnoop.db.ScanDatabase
import net.ballmerlabs.lesnoop.db.ScanResultDao
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.reactivex.rxjava3.core.Scheduler
import io.reactivex.rxjava3.plugins.RxJavaPlugins
import net.ballmerlabs.lesnoop.db.MIGATION_2_3
import net.ballmerlabs.lesnoop.scan.RxBlessedCentralCallback
import net.ballmerlabs.lesnoop.scan.RxBlessedPeripheralSubcomponent
import java.io.File
import java.util.concurrent.Executor
import javax.inject.Named
import javax.inject.Singleton
@InstallIn(SingletonComponent::class)
@dagger.Module(subcomponents = [ScanSubcomponent::class, RxBlessedPeripheralSubcomponent::class])
abstract class Module {

    @Binds
    @Singleton
    abstract fun bindsWakelockProvider(wakeLockProviderImpl: WakeLockProviderImpl): WakeLockProvider

    companion object {
        const val DB_SCHEDULER = "dbsched"
        const val DB_PATH = "dbpath"
        const val EXECUTOR_COMPUTE = "computeexec"
        const val COMPUTE_SCHEDULER = "compute"
        const val CONNECT_SCHEDULER = "connect"
        const val TIMEOUT_SCHEDULER = "timeout"

        @Provides
        @Singleton
        @Named(DB_SCHEDULER)
        fun provideDbSceduler(): Scheduler {
            return RxJavaPlugins.createIoScheduler { r ->
                Thread(r)
            }
        }

        @Provides
        @Singleton
        @Named(CONNECT_SCHEDULER)
        fun provideConnectScheduler(): Scheduler {
            return RxJavaPlugins.createSingleScheduler { r ->
                Thread(r)
            }
        }

        @Provides
        @Singleton
        fun providesPowerManager(@ApplicationContext context: Context): PowerManager {
            return context.getSystemService(Context.POWER_SERVICE) as PowerManager
        }

        @Provides
        @Singleton
        @Named(TIMEOUT_SCHEDULER)
        fun provideTimeoutScheduler(): Scheduler {
            return RxJavaPlugins.createIoScheduler { r ->
                Thread(r)
            }
        }

        @Provides
        @Singleton
        fun providesDao(database: ScanDatabase): ScanResultDao {
            return database.scanResultsDao()
        }

        @Provides
        @Singleton
        fun providesLeClient(@ApplicationContext context: Context): RxBleClient {
            return RxBleClient.create(context)
        }


        @Provides
        @Singleton
        fun blessedClient(@ApplicationContext context: Context, callback: RxBlessedCentralCallback): BluetoothCentralManager {
            return BluetoothCentralManager(context,  callback,Handler())
        }

        @Provides
        @Singleton
        fun providesDatabase(@ApplicationContext ctx: Context): ScanDatabase {
            return Room.databaseBuilder(ctx, ScanDatabase::class.java, "ScanDatabase")
                .addMigrations(MIGATION_2_3)
                .fallbackToDestructiveMigration()
                .build()
        }

        @Provides
        @Singleton
        fun providesRawBluetoothManager(@ApplicationContext ctx: Context): BluetoothManager {
            return ctx.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        }

        @Provides
        @Singleton
        fun providesLocationManager(@ApplicationContext ctx: Context): LocationManager {
            return ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        }

        @Provides
        @Singleton
        @Named(EXECUTOR_COMPUTE)
        fun provideScanExecutor(@Named(DB_SCHEDULER) scheduler: Scheduler): Executor {
            return Executor { t ->
                scheduler.scheduleDirect(t)
            }
        }


        @Provides
        @Singleton
        @Named(COMPUTE_SCHEDULER)
        fun providesComputeScheduler(): Scheduler {
            return RxJavaPlugins.createComputationScheduler { r ->
                Thread(r)
            }
        }


        @Provides
        @Singleton
        fun providesSharedPreferences(@ApplicationContext applicationContext: Context): SharedPreferences {
            return PreferenceManager.getDefaultSharedPreferences(applicationContext)

        }

        @Provides
        @Singleton
        @Named(DB_PATH)
        fun providesDatabasePath(database: ScanDatabase): File {
            return File(database.openHelper.readableDatabase.path)
        }
    }
}