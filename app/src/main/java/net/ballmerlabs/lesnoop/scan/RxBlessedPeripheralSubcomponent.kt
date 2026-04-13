package net.ballmerlabs.lesnoop.scan

import com.welie.blessed.BluetoothPeripheral
import dagger.BindsInstance
import dagger.Module
import dagger.Subcomponent
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.ballmerlabs.lesnoop.BlessedPeripheralScope

@Subcomponent(modules = [RxBlessedPeripheralSubcomponent.RxBlessedPeripheralModule::class])
@BlessedPeripheralScope
interface RxBlessedPeripheralSubcomponent {
    @Subcomponent.Builder
    interface Builder {
        @BindsInstance
        fun peripheral(peripheral: BluetoothPeripheral): Builder
        fun build(): RxBlessedPeripheralSubcomponent?
    }

    @Module
    @InstallIn(SingletonComponent::class)
    abstract class RxBlessedPeripheralModule

    fun peripheral(): RxBlessedPeripheral
}