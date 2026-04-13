package net.ballmerlabs.lesnoop

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.lifecycle.LiveData
import androidx.preference.PreferenceManager
import com.polidea.rxandroidble3.RxBlePhy
import dagger.hilt.android.qualifiers.ApplicationContext
import net.ballmerlabs.lesnoop.scan.Scanner
import timber.log.Timber
import java.lang.IllegalArgumentException
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

@Singleton
class ScannerFactory @Inject constructor(
    private val scanBuilder: ScanSubcomponent.Builder,
    private val bluetoothManager: BluetoothManager,
    @param:ApplicationContext val applicationContext: Context,
    val prefs: SharedPreferences
) {
    private val currentScan = AtomicReference<ScanSubcomponent?>(null)

    fun createScanner(): Scanner {
        return currentScan.updateAndGet { s ->
            when (s) {
                null -> scanBuilder.context(applicationContext).build()!!
                else -> s
            }
        }!!.scanner()
    }

    fun destroyScanner() {
        val s = currentScan.getAndSet(null)
        if (s != null) {
            val scanner = s.scanner()
            scanner.stopScanBackground()
            scanner.dispose()
        }
    }

    private fun stopScanBackground() {
        try {
            applicationContext.applicationContext.stopService(
                Intent(applicationContext.applicationContext, BackgroundScanService::class.java)
            )
        } catch (exc: IllegalArgumentException) {
            Timber.tag("debug").w("service already stopped: $exc")
        }
    }


    fun serviceState(): LiveData<Boolean> {
        return createScanner().serviceState()
    }

    fun phyToMask(override: List<String>? = null): Int {
        val prefs = PreferenceManager.getDefaultSharedPreferences(applicationContext)
        val phy = override ?: (prefs.getStringSet(PREF_PHY, mutableSetOf()) ?: mutableSetOf())
        var phyval = 0
        for (p in phy) {
            phyval = phyval or when(p) {
                PHY_1M -> BluetoothDevice.PHY_LE_1M_MASK
                PHY_2M -> BluetoothDevice.PHY_LE_2M_MASK
                PHY_CODED -> BluetoothDevice.PHY_LE_CODED_MASK
                else -> 0
            }
            Timber.e( "startScanToDb $p $phyval")
        }
        return phyval
    }

    fun phyToRxBle(override: List<String>? = null): Set<RxBlePhy> {
        val prefs = PreferenceManager.getDefaultSharedPreferences(applicationContext)
        val phy = override ?: (prefs.getStringSet(PREF_PHY, mutableSetOf()) ?: mutableSetOf())
        val phyList = mutableSetOf<RxBlePhy>()
        for (p in phy) {
            val v = when(p) {
                PHY_1M -> RxBlePhy.PHY_1M
                PHY_2M -> RxBlePhy.PHY_2M
                PHY_CODED -> RxBlePhy.PHY_CODED
                else -> null
            }
            if (v != null)
                phyList.add(v)
        }
        return phyList
    }


    fun startScanToDb() {
        try {
            stopScanBackground()
            createScanner().scanBackground()
        } catch (exc: Exception) {
            Timber.e( "failed to start scan $exc")
        }
    }

    fun getScanPhy(): String {
        val prefs = PreferenceManager.getDefaultSharedPreferences(applicationContext)
        return prefs.getString(PREF_PRIMARY_PHY, PHY_1M)?: PHY_1M
    }

    fun getPhy(): List<String> {
        val prefs = PreferenceManager.getDefaultSharedPreferences(applicationContext)
        val set = prefs.getStringSet(PREF_PHY, mutableSetOf())?: mutableSetOf()
        return set.toList()
    }

    fun stopScan() {
        try {
            destroyScanner()
            stopScanBackground()
        } catch (exc: Exception) {
            Timber.e( "failed to stop scan $exc")
            exc.printStackTrace()
        }
    }

    fun phyToVal(override: String? = null): Int? {
        val phy = override ?: (prefs.getString(PREF_PRIMARY_PHY, PHY_1M) ?: PHY_1M)
        return when (phy) {
            PHY_1M -> BluetoothDevice.PHY_LE_1M
            PHY_2M -> BluetoothDevice.PHY_LE_2M
            PHY_CODED -> BluetoothDevice.PHY_LE_CODED
            PHY_ALL -> ScanSettings.PHY_LE_ALL_SUPPORTED
            else -> null
        }
    }

    companion object {

        const val PHY_CODED = "coded"
        const val PHY_1M = "1M"
        const val PHY_2M = "2M"
        const val PHY_ALL = "all"
        const val PHY_NONE = "none"
        const val SCAN_MODE_BATCH = "batch"
        const val SCAN_MODE_QUEUE = "queue"
        const val SCAN_MODE_FOREGROUND = "foreground"
        const val PREF_PHY = "phy"
        const val PREF_REPORT_DELAY_ENABLED = "report-delay-enabled"
        const val PREF_REPORT_DELAY = "pref-report-delay"
        const val PREF_PRIMARY_PHY = "primaryphy"
        const val PREF_LEGACY = "legacy"
        const val PREF_MAX_CONNECTION = "max-connections"
        const val PREF_CONNECT = "connect"
        const val PREF_DELAY = "connect-delay"
        const val PREF_SCAN_MODE = "scan-mode"
    }
}