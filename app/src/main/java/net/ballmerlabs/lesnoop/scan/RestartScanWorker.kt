package net.ballmerlabs.lesnoop.scan

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.Worker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.AndroidEntryPoint
import net.ballmerlabs.lesnoop.BackgroundScanService
import net.ballmerlabs.lesnoop.ScannerFactory
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

val RESTART_SCAN_WORKER_ID = UUID.fromString("0942F382-3EF9-4111-A400-27D589543046")


@HiltWorker
class RestartScanWorker @AssistedInject constructor(
    @Assisted val context: Context,
    @Assisted workerParameters: WorkerParameters,
    val service: ScannerFactory,
) : Worker(context, workerParameters) {

    override fun doWork(): Result {
        Timber.tag("debug").e("restarting background scan service")
        service.createScanner().pauseScan()
        service.createScanner().unpauseScan()
        return Result.success()
    }
}