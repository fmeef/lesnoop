package net.ballmerlabs.lesnoop

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import com.polidea.rxandroidble3.scan.ScanResult
import dagger.hilt.android.lifecycle.HiltViewModel
import io.reactivex.rxjava3.core.BackpressureStrategy
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Scheduler
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.Disposable
import me.bytebeats.views.charts.pie.PieChartData
import net.ballmerlabs.lesnoop.db.OuiParser
import net.ballmerlabs.lesnoop.db.ScanResultDao
import net.ballmerlabs.lesnoop.scan.LocationTagger
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Named

@HiltViewModel
class ScanViewModel @Inject constructor(
    val scanResultDao: ScanResultDao,
    @param:Named(Module.DB_PATH) val dbPath: File,
    private val ouiParser: OuiParser,
    val scannerFactory: ScannerFactory,
    val locationTagger: LocationTagger,
    @param:Named(Module.DB_SCHEDULER) private val dbScheduler: Scheduler,
) : ViewModel() {
    val currentScans = mutableStateListOf<ScanResult>()
    val topText = mutableStateOf("")
    var scanInProgress = mutableStateOf<Disposable?>(null)

    private val colorList = listOf(
        Color.Cyan,
        Color.Red,
        Color.Blue,
        Color.DarkGray,
        Color.Green,
        Color.Magenta,
        Color.Yellow
    )

    private val colors: Flowable<Color> = Flowable.defer {
        Flowable.fromIterable(
            colorList
        ).repeat()
    }

    fun getTopOuis(size: Int): Observable<Map<String, Int>> {
        return ouiParser.ouiForOui("FF:FF:FF").ignoreElement()
            .andThen( scanResultDao.getMacs().subscribeOn(dbScheduler))
            .flatMapSingle { r ->
                Timber.v("got topOuis ${r.size}")
                Observable.fromIterable(r)
                    // .filter { r -> r != "N/A" }
                    .map { v -> ouiParser.ouiFromMac(v) }
                    .reduce(HashMap<String, Int>()) { set, r ->
                        set.compute(r) { s, i ->
                            if (i == null) {
                                0
                            } else {
                                i + 1
                            }
                        }
                        set
                    }
            }.map { m ->
                Timber.v("getTopOuis raw map ${m.size}")
                val s = m.size - size
                val drop = if (s < 0) {
                    0
                } else {
                    s
                }
            m.toList()
                .sortedByDescending { (_, v) -> v }
                .dropLast(drop)
                .associate { (v, u) -> Pair(ouiParser.ouiForOui(v).blockingGet(), u) }
        }
            .doOnNext { v -> Timber.v("getTopOuis complete ${v.size}") }
    }

    fun legendState(v: Map<String, Int>): Single<List<Pair<String, Color>>> {
        return Observable.fromIterable(v.keys).subscribeOn(dbScheduler)
            .zipWith(Observable.fromIterable(colorList)) { name, color ->
                Pair(name, color)
            }.toList()
    }

    fun pieChartState(sv: Map<String, Int>): Single<MutableList<PieChartData.Slice>> {
        return Observable.fromIterable(sv.entries).subscribeOn(dbScheduler)
            .toFlowable(BackpressureStrategy.BUFFER).zipWith(colors) { s, color ->
                // Timber.v("slice $color ${s.key}, ${s.value}")
                PieChartData.Slice(
                    s.value.toFloat(), color
                )
            }.toList()
        //.doOnSuccess { s -> Timber.v("result: ${s.size}") }
    }
}