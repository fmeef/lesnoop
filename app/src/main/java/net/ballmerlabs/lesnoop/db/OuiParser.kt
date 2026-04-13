package net.ballmerlabs.lesnoop.db

import android.content.Context
import com.github.doyaaaaaken.kotlincsv.dsl.csvReader
import javax.inject.Inject
import javax.inject.Singleton
import net.ballmerlabs.lesnoop.R
import com.polidea.rxandroidble3.RxBleDevice
import dagger.hilt.android.qualifiers.ApplicationContext
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import io.reactivex.rxjava3.subjects.BehaviorSubject
import timber.log.Timber

@Singleton
class OuiParser @Inject constructor(
    @param:ApplicationContext val context: Context
)  {
    val oui  = BehaviorSubject.create<HashMap<String, String>>()

    private fun readOuiFile(): Single<HashMap<String, String>> {
        return  Single.just(context.resources.openRawResource(R.raw.oui))
            .flatMapObservable { f ->
                Observable.fromIterable(csvReader().readAll(f))
            }.reduce(HashMap<String, String>()) { map, l->
                map[l[1]] = l[2]
                map
            }.subscribeOn(Schedulers.io())
               .doOnSuccess { Timber.v( "oui file initialized") }
    }

//    private fun parseLine(line: String): Maybe<Pair<String, String>> {
//        return Maybe.defer {
//            if (line.contains("(hex)")) {
//                val mac = macaddr.find(line)
//                if (mac != null) {
//                    val hex = "(hex)\t\t"
//                    val name = line.slice(line.indexOf(hex) + hex.length until line.length)
//                    val newmac = mac.value.replace("-", ":")
//                    Maybe.just(Pair(newmac, name))
//                } else {
//                    Maybe.empty()
//                }
//            } else {
//                Maybe.empty()
//            }
//        }
//    }

    fun ouiFromMac(mac: String): String {
        return mac.slice(0 until   8).uppercase()
    }


    fun ouiForDevice(device: RxBleDevice): Single<String> {
        return oui.firstOrError().map { map ->
            val oui = ouiFromMac(device.macAddress)
            map[oui.replace(":","")]?:oui
        }
    }


    fun ouiForDevice(device: String): Single<String> {
        return oui.firstOrError().map { map ->
            val oui = ouiFromMac(device)
            map[oui.replace(":","")]?:oui
        }
    }

    fun ouiForOui(deviceOui: String): Single<String> {
        return oui.firstOrError().map { map ->
            map[deviceOui.replace(":","")]?:deviceOui
        }
    }

    init {
        readOuiFile().toObservable().concatWith(Observable.never()).subscribe(oui)
    }

}