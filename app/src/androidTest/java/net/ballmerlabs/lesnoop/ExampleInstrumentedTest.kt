package net.ballmerlabs.lesnoop

import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.ballmerlabs.lesnoop.db.OuiParser
import com.polidea.rxandroidble3.RxBleDevice
import com.polidea.rxandroidble3.mockrxandroidble.RxBleConnectionMock
import com.polidea.rxandroidble3.mockrxandroidble.RxBleDeviceMock

import org.junit.Test
import org.junit.runner.RunWith

import org.junit.Assert.*
import java.util.*

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {
    @Test
    fun useAppContext() {
        // Context of the app under test.
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("net.ballmerlabs.lesnoop", appContext.packageName)
    }

    @Test
    fun ouiParseTest() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val parser = OuiParser(context)
        val oui = parser.oui.blockingFirst()
        val testRegex = "[0-9A-F]{2}:[0-9A-F]{2}:[0-9A-F]{2}".toRegex()
        Log.v("debug", "ouisize ${oui.size}")
        assert(oui.isNotEmpty())
        for (key in oui.keys) {
            assert(testRegex.matches(key))
        }
    }
    @Test
    fun ouiMac() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val parser = OuiParser(context)
        val device: RxBleDevice = RxBleDeviceMock.Builder()
            .deviceMacAddress("A4:45:19:DC:DC:DC")
            .scanRecord(byteArrayOf(0x00))
            .deviceName("fmef")
            .connection(RxBleConnectionMock.Builder().rssi(-100).addService(UUID.randomUUID(), listOf()).build())
            .build()
        val get = parser.ouiForDevice(device).blockingGet()
        Log.v("debug", get)
        assert(get == "Xiaomi Communications Co Ltd")
    }

}