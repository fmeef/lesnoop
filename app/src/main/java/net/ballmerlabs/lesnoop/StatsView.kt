package net.ballmerlabs.lesnoop

import android.content.res.Configuration
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rxjava3.subscribeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import io.reactivex.rxjava3.schedulers.Schedulers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.rx3.await
import kotlinx.coroutines.withContext
import me.bytebeats.views.charts.pie.PieChart
import me.bytebeats.views.charts.pie.PieChartData
import timber.log.Timber

@Composable
fun DbChartView(padding: PaddingValues) {
    val model: ScanViewModel = hiltViewModel()
    val ouis by model.getTopOuis(8).subscribeAsState(mapOf())
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/x-sqlite3")
    ){ uri: Uri? ->
        scope.launch(Dispatchers.IO) {
            val f = model.dbPath.inputStream()
            if (uri != null) {
                val out = context.contentResolver.openOutputStream(uri)
                if (out != null) {
                    Timber.e( "writing $uri")
                    f.copyTo(out)
                    out.close()
                }
            } else {
                withContext(Dispatchers.Main)  {
                    Toast.makeText(context, context.getText(R.string.invalid_file_path), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    val configuration = LocalConfiguration.current

    Column(modifier = Modifier.padding(padding).fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            ScanResultsCount(model = model)
            Button(onClick = {
                launcher.launch("output.sqlite")
            }) {
                Text(text = stringResource(id = R.string.export))
            }
        }
        when (configuration.orientation) {
            Configuration.ORIENTATION_PORTRAIT -> Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.Top,
                horizontalAlignment = Alignment.Start
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Legend(modifier = Modifier.fillMaxWidth(0.6F), model = model, ouis = ouis)
                    MetricsView()
                }
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    OuiPieChart(
                        modifier = Modifier.fillMaxSize(),
                        model = model,
                        ouis = ouis)
                }
            }
            else -> Row(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.Top
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Legend(model = model, ouis = ouis)
                    MetricsView()
                }
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    OuiPieChart(
                        modifier = Modifier.fillMaxSize(),
                        model = model,
                        ouis = ouis)
                }
            }
        }
    }
}


@Composable
fun LocationView(modifier: Modifier = Modifier) {
    val viewModel: ScanViewModel = hiltViewModel()
    val location by viewModel.locationTagger.locationSubject.subscribeAsState("none")

    Column(modifier = modifier) {
        Text("Location: $location")
    }
}

@Composable
fun MetricsView(modifier: Modifier = Modifier) {
    val viewModel: ScanViewModel = hiltViewModel()
    val metrics by viewModel.scanResultDao.observeTopMetrics().subscribeAsState(null)
    val scope = rememberCoroutineScope()
    Column(modifier = modifier,
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(32.dp)
    ) {

        Column {
            Text("new: ${metrics?.newCount}")
            Text("already seen: ${metrics?.oldCount}")
            Text("connected: ${metrics?.connected}")
        }

        LocationView()

        Button(onClick = {
            scope.launch(Dispatchers.IO) {
                 viewModel.scanResultDao.newMetricsSession().await()
            }
        }) { Text("New session") }

        Button(onClick = {
            scope.launch(Dispatchers.IO) {
                viewModel.locationTagger.startLocationPoll()
            }
        }) {
            Text("Acquire gps")
        }
    }
}

@Composable
fun ScanResultsCount(model: ScanViewModel, modifier: Modifier = Modifier) {
    val count = model.scanResultDao.scanResultCount()
        .subscribeOn(Schedulers.io())
        .subscribeAsState(initial = 0)
    Text(modifier = modifier, text = stringResource(id = R.string.indexed, count.value))
}

@Composable
fun Legend(model: ScanViewModel, modifier: Modifier = Modifier, ouis: Map<String, Int>) {
    val data = model.legendState(ouis).subscribeAsState(initial = listOf())
    LazyColumn(modifier = modifier) {
        for (v in data.value) {
            item {
                Column {
                    Text(text = v.first)
                    Surface(modifier = Modifier.size(16.dp), color = v.second) {

                    }
                }
            }
        }
    }
}

@Composable
fun OuiPieChart(model: ScanViewModel, ouis: Map<String, Int>, modifier: Modifier = Modifier) {
    val data by model.pieChartState(ouis).subscribeAsState(initial = listOf())
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        PieChart(
            modifier = Modifier
                .aspectRatio(1.0.toFloat())
                .fillMaxWidth(),
            pieChartData = PieChartData(data)
        )
    }
}