package net.ballmerlabs.lesnoop

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.then
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipState
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults.pinnedScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupPositionProvider
import androidx.core.content.edit
import androidx.core.text.isDigitsOnly
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.rxjava3.rxPreferencesDataStore
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.dialog
import androidx.navigation.compose.rememberNavController
import androidx.preference.PreferenceManager
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionState
import com.google.accompanist.permissions.PermissionStatus
import com.google.accompanist.permissions.rememberPermissionState
import dagger.hilt.android.AndroidEntryPoint
import io.reactivex.rxjava3.disposables.CompositeDisposable
import kotlinx.coroutines.launch
import net.ballmerlabs.lesnoop.db.OuiParser
import net.ballmerlabs.lesnoop.ui.theme.LeSnoopTheme
import timber.log.Timber
import javax.inject.Inject
import kotlin.math.floor

const val NAV_PREFS = "prefs"
const val NAV_SCAN = "scan"
const val NAV_DB = "database"
const val NAV_DIALOG = "dialog"
const val PREF_NAME = "scanprefs"

val Context.rxPrefs by rxPreferencesDataStore(PREF_NAME)

val PREF_BACKGROUND_SCAN = booleanPreferencesKey("background_scan")

@OptIn(ExperimentalPermissionsApi::class)
data class PermissionText(
    val permission: PermissionState, val excuse: String
)

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var scannerFactory: ScannerFactory

    @Inject
    lateinit var ouiParser: OuiParser

    private val disposable = CompositeDisposable()

    @OptIn(ExperimentalPermissionsApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val disp = ouiParser.oui.subscribe(
            { o ->
                Timber.v("cached oui requested with len ${o.size}")
            },
            { e -> Timber.e("failed to refresh oui $e") })
        disposable.add(disp)
        setContent {
            LeSnoopTheme {
                Body {
                    scannerFactory
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        disposable.dispose()
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopBar() {
    val model: ScanViewModel = hiltViewModel()
    TopAppBar(
        title = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(model.topText.value, style = MaterialTheme.typography.headlineLarge)
                val d = model.scanInProgress.value
                if (d != null) {
                    Button(
                        onClick = {
                            model.scanInProgress.value = null
                            d.dispose()
                        }) {
                        Text(text = stringResource(id = R.string.stop_scan))
                    }
                }
            }
        }, scrollBehavior = pinnedScrollBehavior()
    )
}


@Composable
@ExperimentalPermissionsApi
fun ScopePermissions(
    modifier: Modifier = Modifier, content: @Composable () -> Unit
) {
    val permissions =
        mutableListOf(
            PermissionText(
                permission = rememberPermissionState(permission = Manifest.permission.ACCESS_FINE_LOCATION),
                excuse = "LeSnoop needs the ACCESS_FINE_LOCATION permission for performing offline bluetooth scans in the background" + " and locally geotagging the discovered devices. This information is never shared or transmitted in any way."
            )
        )

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val notif = PermissionText(
            permission = rememberPermissionState(permission = Manifest.permission.POST_NOTIFICATIONS),
            excuse = "Notifications are required to CYBER HACK the termux"
        )
        permissions.add(notif)
    }


    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        for (x in listOf(
            Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT
        )) {
            val p = PermissionText(
                permission = rememberPermissionState(permission = x),
                excuse = "The $x permission is required for discovering bluetooth devices in the background"
            )
            permissions.add(p)
        }
    }

    val granted = permissions.all { s ->
        s.permission.status == PermissionStatus.Granted
    }
    if (granted) {
        Box(modifier = modifier) {
            content()
        }
    } else {
        Box(
            modifier = modifier, contentAlignment = Alignment.Center
        ) {
            val p =
                permissions.first { p -> p.permission.status != PermissionStatus.Granted }
            Button(
                onClick = { p.permission.launchPermissionRequest() }) {
                Text(modifier = Modifier.padding(8.dp), text = p.excuse)
            }
        }
    }
}

@Composable
fun ScanDialog(modifier: Modifier = Modifier, s: () -> ScannerFactory) {
    val service by remember { derivedStateOf(s) }
    val started: Boolean? by service.serviceState().observeAsState()
    Surface(
        modifier = modifier
            .background(
                color = MaterialTheme.colorScheme.background, shape = RoundedCornerShape(8.dp)
            )
            .padding(8.dp)
    ) {
        Row {
            Button(
                onClick = {
                    service.startScanToDb()
                }, enabled = !(started ?: false)
            ) {
                Text(text = stringResource(id = R.string.start_scan))
            }
            Button(
                onClick = { service.stopScan() }, enabled = started ?: false
            ) {
                Text(text = stringResource(id = R.string.stop_scan))
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
@ExperimentalPermissionsApi
fun ScanPage(s: () -> ScannerFactory, prefs: SharedPreferences) {

    val service by remember { derivedStateOf(s) }
    val legacy = remember { mutableStateOf(false) }
    val selected = remember {
        val t = mutableStateListOf<String>()
        t.addAll(service.getPhy())
        t
    }
    var reportDelayenabled by remember {
        mutableStateOf(
            prefs.getBoolean(ScannerFactory.PREF_REPORT_DELAY_ENABLED, false)
        )
    }

    val batchDelay = rememberTextFieldState(
        prefs.getLong(ScannerFactory.PREF_DELAY, 5).toString(10)
    )

    val reportDelay = rememberTextFieldState(
        prefs.getLong(ScannerFactory.PREF_REPORT_DELAY, 3000)
            .toString(10)
    )
    var sliderState by remember { mutableFloatStateOf(
        prefs.getInt(ScannerFactory.PREF_MAX_CONNECTION, 7).toFloat()
    ) }

    var connect by remember { mutableStateOf(prefs.getBoolean(ScannerFactory.PREF_CONNECT, false)) }
    val primary = remember { mutableStateOf(service.getScanPhy()) }
    val scanMode =
        remember { mutableStateOf(prefs.getString(ScannerFactory.PREF_SCAN_MODE, "batch")!!) }
    //  val p = context.rxPrefs.data().map { p -> p[PREF_BACKGROUND_SCAN]?: false }.subscribeAsState(initial = false)


    ScopePermissions {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val state = TooltipState()
                val provider = object : PopupPositionProvider {
                    override fun calculatePosition(
                        anchorBounds: IntRect,
                        windowSize: IntSize,
                        layoutDirection: LayoutDirection,
                        popupContentSize: IntSize
                    ): IntOffset {
                        return IntOffset(
                            (windowSize.width - popupContentSize.width) / 4,
                            (windowSize.height - popupContentSize.height) / 4
                        )
                    }
                }
                val scope = rememberCoroutineScope()
                TooltipBox(
                    positionProvider = provider, tooltip = {
                        Surface(
                            modifier = Modifier
                                .background(
                                    color = MaterialTheme.colorScheme.background,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .padding(8.dp)
                        ) {

                            Text(
                                text = stringResource(id = R.string.scan_disclaimer)
                            )
                        }
                    }, state = state
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = stringResource(id = R.string.connect_mode))
                            IconButton(
                                onClick = {
                                    scope.launch { state.show() }
                                },
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.baseline_info_24),
                                    contentDescription = "Info",
                                )
                            }
                        }
                        Switch(checked = connect, onCheckedChange = { v ->
                            connect = v
                            prefs.edit {
                                putBoolean(ScannerFactory.PREF_CONNECT, v)
                            }
                        })
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = stringResource(id = R.string.legacy_toggle))
                Switch(checked = legacy.value, onCheckedChange = { v ->
                    prefs.edit {
                        putBoolean(ScannerFactory.PREF_LEGACY, v)
                    }
                    legacy.value = v
                })
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = stringResource(id = R.string.connect_phy))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PhyButton(mode = ScannerFactory.PHY_NONE, selected = selected)
                    PhyButton(mode = ScannerFactory.PHY_ALL, selected = selected)
                    PhyButton(mode = ScannerFactory.PHY_CODED, selected = selected)
                    PhyButton(mode = ScannerFactory.PHY_1M, selected = selected)
                    PhyButton(mode = ScannerFactory.PHY_2M, selected = selected)
                }
            }
            Column(
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = stringResource(id = R.string.scan_phy))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PrefButton(
                        prefKey = ScannerFactory.PREF_PRIMARY_PHY,
                        mode = ScannerFactory.PHY_NONE,
                        selected = primary
                    )
                    PrefButton(
                        prefKey = ScannerFactory.PREF_PRIMARY_PHY,
                        mode = ScannerFactory.PHY_ALL,
                        selected = primary
                    )
                    PrefButton(
                        prefKey = ScannerFactory.PREF_PRIMARY_PHY,
                        mode = ScannerFactory.PHY_CODED,
                        selected = primary
                    )
                    PrefButton(
                        prefKey = ScannerFactory.PREF_PRIMARY_PHY,
                        mode = ScannerFactory.PHY_1M,
                        selected = primary
                    )
                    PrefButton(
                        prefKey = ScannerFactory.PREF_PRIMARY_PHY,
                        mode = ScannerFactory.PHY_2M,
                        selected = primary
                    )
                }
            }


            Column(
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = stringResource(id = R.string.scan_mode))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PrefButton(
                        prefKey = ScannerFactory.PREF_SCAN_MODE,
                        mode = ScannerFactory.SCAN_MODE_BATCH,
                        selected = scanMode
                    )
                    PrefButton(
                        prefKey = ScannerFactory.PREF_SCAN_MODE,
                        mode = ScannerFactory.SCAN_MODE_QUEUE,
                        selected = scanMode
                    )
                    PrefButton(
                        prefKey = ScannerFactory.PREF_SCAN_MODE,
                        mode = ScannerFactory.SCAN_MODE_FOREGROUND,
                        selected = scanMode
                    )
                }

                if (scanMode.value == ScannerFactory.SCAN_MODE_BATCH) {
                    LongTextField(
                        modifier = Modifier.fillMaxWidth(),
                        state = batchDelay,
                        text = stringResource(R.string.batch_delay),
                        pref = ScannerFactory.PREF_DELAY
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.report_delay_enabled))
                    Switch(checked = reportDelayenabled, onCheckedChange = { v ->
                        prefs.edit {
                            putBoolean(ScannerFactory.PREF_REPORT_DELAY_ENABLED, v)
                        }

                        reportDelayenabled = v
                    })
                }

                if (reportDelayenabled) {
                    LongTextField(
                        state = reportDelay,
                        text = stringResource(R.string.report_delay),
                        pref = ScannerFactory.PREF_REPORT_DELAY,
                        default = 0
                    )
                }

                Column (
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("${stringResource(R.string.max_concurrent_connections)}: $sliderState")

                    Slider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        value =  sliderState,
                        valueRange = 0.toFloat()..7.toFloat(),
                        onValueChange = { v: Float ->
                            prefs.edit {
                                val out = floor(v).toInt()
                                putInt(ScannerFactory.PREF_MAX_CONNECTION, out)
                                sliderState = out.toFloat()
                            }
                        },
                    )
                }
            }
        }

    }
}


@Composable
fun LongTextField(
    state: TextFieldState,
    text: String,
    pref: String,
    modifier: Modifier = Modifier,
    default: Long = 1
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { PreferenceManager.getDefaultSharedPreferences(context) }
    TextField(
        modifier = modifier.padding(horizontal = 16.dp),
        state = state,
        onKeyboardAction = {
            scope.launch {
                prefs.edit {
                    val i = state.text.toString().toLongOrNull()
                    if (i != null) {
                        putLong(pref, i)
                    }
                }
            }
        },
        onTextLayout = {
            scope.launch {
                prefs.edit {
                    val i = state.text.toString().toLongOrNull()
                    if (i != null) {
                        putLong(pref, i)
                    }
                }
            }
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        inputTransformation = InputTransformation.then {
            if (!asCharSequence().isDigitsOnly()) {
                revertAllChanges()
            } else if (asCharSequence().isEmpty()) {
                append(default.toString(10))
            } else if ((asCharSequence().toString().toIntOrNull() ?: -1) < 0) {
                revertAllChanges()
            }
        },
        label = {
            Text(text)
        })
}

@Composable
fun PrefButton(
    modifier: Modifier = Modifier,
    prefKey: String,
    mode: String,
    selected: MutableState<String>
) {
    val context = LocalContext.current
    val prefs = remember { PreferenceManager.getDefaultSharedPreferences(context) }
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected.value == mode, onClick = {
            prefs.edit {
                putString(prefKey, mode)
            }
            selected.value = mode
        })
        Text(text = mode)
    }
}

@Composable
fun PhyButton(modifier: Modifier = Modifier, mode: String, selected: SnapshotStateList<String>) {
    val context = LocalContext.current
    val prefs = remember { PreferenceManager.getDefaultSharedPreferences(context) }
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = selected.contains(mode), onCheckedChange = { v ->
            if (v) selected.add(mode) else selected.remove(mode)
            prefs.edit {
                putStringSet(ScannerFactory.PREF_PHY, selected.toSet())
            }
        })
        Text(text = mode)
    }
}

@Composable
fun BottomBar(navController: NavController) {
    BottomAppBar {
        Row(
            modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Button(
                modifier = Modifier.padding(start = 5.dp, end = 5.dp),
                onClick = { navController.navigate(NAV_PREFS) }) {
                Text(text = stringResource(id = R.string.settings))
            }
            Button(
                modifier = Modifier.padding(start = 5.dp, end = 5.dp),
                onClick = { navController.navigate(NAV_DB) }) {
                Text(text = stringResource(id = R.string.database))
            }
            Button(
                modifier = Modifier.padding(start = 5.dp, end = 5.dp),
                onClick = { navController.navigate(NAV_SCAN) }) {
                Text(text = stringResource(id = R.string.scan))
            }

        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
@ExperimentalPermissionsApi
fun Body(service: () -> ScannerFactory) {
    val navController = rememberNavController()
    val model: ScanViewModel = hiltViewModel()
    val context = LocalContext.current
    val prefs = remember { PreferenceManager.getDefaultSharedPreferences(context) }

    Scaffold(content = { padding ->
        NavHost(navController = navController, startDestination = NAV_PREFS) {
            composable(NAV_PREFS) {
                model.topText.value = stringResource(id = R.string.settings)
                val scrollState = ScrollState(0)
                ScopePermissions(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(padding)
                        .verticalScroll(scrollState)
                ) {
                    //  DeviceList(padding, model)
                    ScanPage(service,  prefs)
                }
            }
            composable(NAV_DB) {
                model.topText.value = stringResource(id = R.string.database)
                DbChartView(padding)
            }
            composable(NAV_SCAN) {
                model.topText.value = stringResource(id = R.string.nearby)
                DeviceList(padding = padding)
            }
            dialog(NAV_DIALOG) { ScanDialog(Modifier, service) }
        }
    }, bottomBar = { BottomBar(navController) }, topBar = { TopBar() }, floatingActionButton = {
        FloatingActionButton(onClick = {
            navController.navigate(NAV_DIALOG)
        }) {
            Icon(
                painter = painterResource(id = R.drawable.ic_baseline_perm_scan_wifi_24),
                contentDescription = "Start scan"
            )

        }
    })
}

