package net.ballmerlabs.lesnoop

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import io.reactivex.rxjava3.plugins.RxJavaPlugins
import timber.log.Timber

@HiltAndroidApp
class MainApp : Application() {

    init {
        Timber.plant(Timber.DebugTree())
        Timber.tag("global").v("set global error handler")
        RxJavaPlugins.setErrorHandler { err: Throwable ->
            Timber.e("unhandled rxjava exception: $err")
            err.printStackTrace()
        }
    }
}