@file:OptIn(ExperimentalMaterial3Api::class)

package pe.analizador.wifi

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import pe.analizador.wifi.ui.AppTheme
import pe.analizador.wifi.ui.ChannelGraphScreen
import pe.analizador.wifi.ui.ConnectionScreen
import pe.analizador.wifi.ui.FinderScreen
import pe.analizador.wifi.ui.NetworksScreen
import pe.analizador.wifi.ui.RatingScreen
import pe.analizador.wifi.ui.SettingsDialog
import pe.analizador.wifi.ui.TimeGraphScreen
import pe.analizador.wifi.ui.agoText
import pe.analizador.wifi.ui.openIntent
import pe.analizador.wifi.ui.openWifiSettings
import pe.analizador.wifi.ui.rememberNow
import android.provider.Settings as AndroidSettings

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val vm: WifiViewModel = viewModel()
            val state by vm.state.collectAsState()
            AppTheme(mode = state.settings.themeMode) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AppRoot(vm, state)
                }
            }
        }
    }
}

enum class Tab(val title: String, val icon: ImageVector) {
    REDES("Redes", Icons.Filled.Wifi),
    CANALES("Canales", Icons.Filled.BarChart),
    TIEMPO("Tiempo", Icons.Filled.ShowChart),
    MEJOR("Mejor canal", Icons.Filled.Star),
    CONEXION("Conexión", Icons.Filled.Info)
}

private fun requiredPermissions(): Array<String> {
    val list = mutableListOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )
    if (Build.VERSION.SDK_INT >= 33) list += Manifest.permission.NEARBY_WIFI_DEVICES
    return list.toTypedArray()
}

private fun hasPermissions(ctx: Context): Boolean =
    ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

private fun isLocationOn(ctx: Context): Boolean {
    val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    return LocationManagerCompat.isLocationEnabled(lm)
}

@Composable
fun AppRoot(vm: WifiViewModel, state: UiState) {
    val ctx = LocalContext.current
    var permGranted by remember { mutableStateOf(hasPermissions(ctx)) }
    var locationOn by remember { mutableStateOf(isLocationOn(ctx)) }
    var askedOnce by rememberSaveable { mutableStateOf(false) }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        permGranted = hasPermissions(ctx)
        askedOnce = true
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, permGranted) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    permGranted = hasPermissions(ctx)
                    locationOn = isLocationOn(ctx)
                    if (permGranted) vm.start()
                }
                Lifecycle.Event.ON_PAUSE -> vm.stop()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (!permGranted) {
        PermissionScreen(
            showSettingsButton = askedOnce,
            onRequest = { permLauncher.launch(requiredPermissions()) },
            onOpenSettings = {
                openIntent(
                    ctx,
                    Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", ctx.packageName, null))
                )
            }
        )
        return
    }

    var tab by rememberSaveable { mutableStateOf(Tab.REDES) }
    var band by rememberSaveable { mutableStateOf(Band.GHZ_2_4) }
    var finderBssid by rememberSaveable { mutableStateOf<String?>(null) }
    var showSettings by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) {
            val ok = Exporter.writeCsv(ctx, uri, state.networks)
            Toast.makeText(ctx, if (ok) "Exportado (${state.networks.size} redes)" else "No se pudo exportar", Toast.LENGTH_SHORT).show()
        }
    }

    if (showSettings) {
        SettingsDialog(state.settings, onChange = { vm.updateSettings(it) }, onDismiss = { showSettings = false })
    }

    val finder = finderBssid
    if (finder != null) {
        BackHandler { finderBssid = null }
        FinderScreen(state, finder, onChange = { finderBssid = it }, onBack = { finderBssid = null })
        return
    }

    val now = rememberNow()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Analizador WiFi+", fontWeight = FontWeight.Bold)
                        Text(
                            "${state.networks.size} redes · " +
                                (if (state.paused) "en pausa" else agoText(now, state.lastUpdate)),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { vm.togglePause() }) {
                        Icon(
                            if (state.paused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                            contentDescription = if (state.paused) "Reanudar" else "Pausar"
                        )
                    }
                    IconButton(onClick = { vm.scanNow() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Escanear ahora")
                    }
                    IconButton(onClick = { exportLauncher.launch(Exporter.defaultFileName()) }) {
                        Icon(Icons.Filled.Share, contentDescription = "Exportar CSV")
                    }
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Filled.Settings, contentDescription = "Ajustes")
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = { Icon(t.icon, contentDescription = t.title) },
                        label = { Text(t.title, fontSize = 11.sp, maxLines = 1) }
                    )
                }
            }
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            StatusBanners(state, locationOn, now)
            when (tab) {
                Tab.REDES -> NetworksScreen(state, onFind = { finderBssid = it })
                Tab.CANALES -> ChannelGraphScreen(state, band, onBand = { band = it })
                Tab.TIEMPO -> TimeGraphScreen(state, band, onBand = { band = it })
                Tab.MEJOR -> RatingScreen(state, band, onBand = { band = it })
                Tab.CONEXION -> ConnectionScreen(state, onFind = { finderBssid = it })
            }
        }
    }
}

@Composable
private fun StatusBanners(state: UiState, locationOn: Boolean, now: Long) {
    val ctx = LocalContext.current
    if (!state.wifiEnabled) {
        Banner("El Wi-Fi está apagado.", "Activar", Color(0xFFE53935)) { openWifiSettings(ctx) }
    }
    if (!locationOn) {
        Banner(
            "La ubicación está desactivada: Android no entrega la lista de redes sin ella.",
            "Activar", Color(0xFFE53935)
        ) { openIntent(ctx, Intent(AndroidSettings.ACTION_LOCATION_SOURCE_SETTINGS)) }
    }
    if (state.throttleEnabled && !state.paused && state.nextScanAt > 0) {
        // `now` fuerza la recomposición cada segundo para el contador.
        val wait = ((state.nextScanAt - SystemClock.elapsedRealtime()) / 1000).coerceAtLeast(0)
        if (wait > 0 && now > 0) {
            Banner(
                "Android limita los escaneos. Próximo en $wait s (mientras tanto se usan los del sistema).",
                "Quitar límite", Color(0xFFFB8C00)
            ) { openIntent(ctx, Intent(AndroidSettings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)) }
        }
    }
}

@Composable
private fun Banner(text: String, action: String, color: Color, onAction: () -> Unit) {
    Surface(color = color.copy(alpha = 0.15f), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = onAction) { Text(action) }
        }
    }
}

@Composable
private fun PermissionScreen(showSettingsButton: Boolean, onRequest: () -> Unit, onOpenSettings: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Filled.Wifi, contentDescription = null, modifier = Modifier.size(72.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text("Analizador WiFi+", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Text(
            "Android exige el permiso de ubicación para mostrar las redes Wi-Fi cercanas, " +
                "porque la lista de redes puede revelar dónde estás.\n\n" +
                "Esta app no usa el GPS, no tiene anuncios y no tiene permiso de internet: " +
                "nada sale de tu celular.",
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = onRequest) { Text("Conceder permiso") }
        if (showSettingsButton) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Si elegiste \"No volver a preguntar\", actívalo desde los ajustes de la app (Permisos > Ubicación > Precisa).",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodySmall
            )
            OutlinedButton(onClick = onOpenSettings) { Text("Abrir ajustes de la app") }
        }
    }
}
