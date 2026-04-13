package net.ballmerlabs.lesnoop

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import com.polidea.rxandroidble3.scan.ScanResult
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.CompositeDisposable
import timber.log.Timber


@Composable
fun DeviceList(padding: PaddingValues) {
    val lazyListState = rememberLazyListState()
    val model: ScanViewModel = hiltViewModel()
    val state = rememberSwipeRefreshState(isRefreshing = false)
    var scanInProgress by remember {
        model.scanInProgress
    }
    val compositeDisposable by remember {
        mutableStateOf(CompositeDisposable())
    }

    DisposableEffect(key1 = compositeDisposable) {
        onDispose { compositeDisposable.dispose() }
    }

    SwipeRefresh(modifier = Modifier.padding(padding), state = state, onRefresh = {
        val scan = model.scannerFactory.createScanner()
        state.isRefreshing = true
        val disp = scan.startScan(false)
            .onErrorComplete()
            .distinct { s -> s.bleDevice.macAddress }
            .doOnNext { r -> Timber.v( "r $r") }
            .doOnSubscribe { d ->
                if (scanInProgress != null) {
                    scanInProgress?.dispose()
                    scanInProgress = null
                }
                scanInProgress = d
                state.isRefreshing = false
                model.currentScans.clear()
            }
            .doFinally {
                scanInProgress = null
            }
            .doOnDispose {
                scanInProgress = null
            }
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe { v ->
                model.currentScans.add(v)
            }

        compositeDisposable.add(disp)
    }) {
        LazyColumn(modifier = Modifier.fillMaxSize(), state = lazyListState) {
            if (model.currentScans.isNotEmpty()) {
                for (result in model.currentScans) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 5.dp, bottom = 5.dp)
                                .height(60.dp)
                        ) {
                            ScanResultView(scanResult = result)
                        }
                    }
                }
            } else {
                item {
                    Text(
                        text = stringResource(id = R.string.no_devices)
                    )
                }
            }
        }
    }
}

@Composable
fun ScanResultView(scanResult: ScanResult) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 8.dp, end = 8.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(
            modifier = Modifier.fillMaxHeight(),
            verticalArrangement = Arrangement.Top
        ) {
            Text(
                text = scanResult.bleDevice.name ?: "N/A",
                style = MaterialTheme.typography.labelLarge
            )
            Text(text = scanResult.bleDevice.macAddress)
        }
        Text(text = "${scanResult.rssi} dBm")
    }
}