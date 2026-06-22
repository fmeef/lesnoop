package net.ballmerlabs.lesnoop

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.WorkManager
import dagger.hilt.android.HiltAndroidApp
import io.reactivex.rxjava3.plugins.RxJavaPlugins
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class MainApp : Application(), Configuration.Provider{

    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)

            .build()

    override fun onCreate() {
        super.onCreate()
        WorkManager.initialize(applicationContext, workManagerConfiguration)
    }

    init {
        Timber.plant(Timber.DebugTree())
        Timber.tag("global").v("set global error handler")
        RxJavaPlugins.setErrorHandler { err: Throwable ->
            Timber.e("unhandled rxjava exception: $err")
            err.printStackTrace()
        }
    }
}