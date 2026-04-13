package net.ballmerlabs.lesnoop

import android.bluetooth.BluetoothManager
import android.content.Context
import dagger.Binds
import dagger.BindsInstance
import dagger.Module
import dagger.Provides
import dagger.Subcomponent
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import io.reactivex.rxjava3.core.Scheduler
import io.reactivex.rxjava3.plugins.RxJavaPlugins
import net.ballmerlabs.lesnoop.scan.Scanner
import net.ballmerlabs.lesnoop.scan.ScannerImpl
import javax.inject.Named
import javax.inject.Provider

@Subcomponent(modules = [ScanSubcomponent.ScanModule::class])
@ScanScope
interface ScanSubcomponent {

    companion object {
        const val SCHEDULER_SCAN = "scansched"
        const val SCHEDULER_COMPUTE = "computesched"
        const val BLUETOOTH_SERVICE = "bluetooth-service"
    }

    @Subcomponent.Builder
    interface Builder {
        @BindsInstance
        fun context(context: Context): Builder
        fun build(): ScanSubcomponent?
    }

    @InstallIn(ViewModelComponent::class)
    @Module
    abstract class ScanModule {

        @Binds
        @ScanScope
        abstract fun bindsScanner(scannerImpl: ScannerImpl): Scanner

        companion object {

            @Provides
            @ScanScope
            @Named(SCHEDULER_SCAN)
            fun provideScheduler(): Scheduler {
                return RxJavaPlugins.createSingleScheduler { r ->
                    Thread(r)
                }
            }

            @Provides
            @ScanScope
            @Named(BLUETOOTH_SERVICE)
            fun provideBluetoothService(context: Context): BluetoothManager {
                return context.getSystemService(Context.BLUETOOTH_SERVICE)!! as BluetoothManager
            }

            @Provides
            @ScanScope
            fun providesFinalizer(
                @Named(SCHEDULER_COMPUTE)
                computeScheduler: Scheduler,
                @Named(SCHEDULER_SCAN)
                scanScheduler: Scheduler,
                scanner: Provider<Scanner>
            ): ScanSubcomponentFinalizer {
                return object : ScanSubcomponentFinalizer {
                    override fun onFinalize() {
                        scanner.get().stopScanForeground()
                        scanner.get().stopScanBackground()
                        scanScheduler.shutdown()
                        computeScheduler.shutdown()
                    }

                }
            }


            @Provides
            @ScanScope
            @Named(SCHEDULER_COMPUTE)
            fun providesComputeScheduler(): Scheduler {
                return RxJavaPlugins.createIoScheduler { r ->
                    Thread(r)
                }
            }
        }
    }
    fun scanner(): Scanner
}

interface ScanSubcomponentFinalizer  {
    fun onFinalize()
}