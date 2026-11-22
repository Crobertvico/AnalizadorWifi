@file:OptIn(ExperimentalMaterial3Api::class)

package pe.analizador.wifi.ui

import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import pe.analizador.wifi.UiState
import pe.analizador.wifi.formatDistance
import pe.analizador.wifi.signalLabel
import pe.analizador.wifi.signalQuality

@Composable
fun FinderScreen(state: UiState, bssid: String, onChange: (String) -> Unit, onBack: () -> Unit) {
    val net = state.networks.firstOrNull { it.bssid == bssid }
    val conn = state.connection
    val isConn = conn != null && conn.bssid == bssid
    val rssi: Int? = if (isConn && conn != null && conn.rssi > -127) conn.rssi else net?.rssi
    val name = net?.displaySsid ?: conn?.takeIf { isConn }?.ssid ?: bssid.uppercase()

    var sound by remember { mutableStateOf(false) }
    var showPicker by remember { mutableStateOf(false) }
    var minSeen by remember(bssid) { mutableIntStateOf(0) }
    var maxSeen by remember(bssid) { mutableIntStateOf(-200) }

    LaunchedEffect(rssi, bssid) {
        if (rssi != null) {
            if (minSeen == 0 || rssi < minSeen) minSeen = rssi
            if (rssi > maxSeen) maxSeen = rssi
        }
    }

    val currentRssi by rememberUpdatedState(rssi)
    LaunchedEffect(sound) {
        if (!sound) return@LaunchedEffect
        val maybeTg: ToneGenerator? = try {
            ToneGenerator(AudioManager.STREAM_MUSIC, 70)
        } catch (_: Exception) {
            null
        }
        val tg = maybeTg ?: return@LaunchedEffect
        try {
            while (isActive) {
                val r = currentRssi
                if (r != null) {
                    tg.startTone(ToneGenerator.TONE_PROP_BEEP, 60)
                    // Más señal = bips más rápidos (de 1.5 s a 0.15 s)
                    val q = signalQuality(r)
                    delay((1500 - q * 13.5).toLong().coerceIn(150L, 1500L))
                } else {
                    delay(500)
                }
            }
        } finally {
            tg.release()
        }
    }

    val quality = rssi?.let { signalQuality(it) } ?: 0
    val animated by animateFloatAsState(targetValue = quality / 100f, label = "calidad")
    val color = if (rssi != null) signalColor(rssi) else MaterialTheme.colorScheme.outline
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Buscador de señal") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(bssid.uppercase(), style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { showPicker = true }) { Text("Cambiar red") }
            Spacer(Modifier.height(8.dp))
            Box(contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(260.dp)) {
                    val stroke = 22.dp.toPx()
                    val arcSize = Size(size.width - stroke, size.height - stroke)
                    val tl = Offset(stroke / 2, stroke / 2)
                    drawArc(
                        color = track, startAngle = 150f, sweepAngle = 240f, useCenter = false,
                        topLeft = tl, size = arcSize, style = Stroke(width = stroke, cap = StrokeCap.Round)
                    )
                    drawArc(
                        color = color, startAngle = 150f, sweepAngle = 240f * animated, useCenter = false,
                        topLeft = tl, size = arcSize, style = Stroke(width = stroke, cap = StrokeCap.Round)
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        rssi?.toString() ?: "--",
                        fontSize = 64.sp,
                        fontWeight = FontWeight.Bold,
                        color = color
                    )
                    Text("dBm", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (rssi != null) "${signalLabel(rssi)} · $quality%" else "Sin señal",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            if (rssi != null && net != null) {
                Text("Distancia aproximada: ≈${formatDistance(net.distanceM)} (línea de vista)", style = MaterialTheme.typography.bodyMedium)
            }
            if (maxSeen > -200) {
                Text("Mínimo visto: $minSeen dBm · Máximo: $maxSeen dBm", style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                if (isConn) "Fuente: conexión activa, se actualiza cada segundo."
                else if (state.throttleEnabled) "Fuente: escaneo (Android limita los escaneos; para tiempo real conéctate a esta red o quita el límite en opciones de desarrollador)."
                else "Fuente: escaneo cada ${state.settings.intervalSec} s.",
                style = MaterialTheme.typography.labelMedium
            )
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Sonido (más rápido = más señal)", modifier = Modifier.weight(1f))
                Switch(checked = sound, onCheckedChange = { sound = it })
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = {
                minSeen = 0
                maxSeen = -200
            }) { Text("Reiniciar mínimo/máximo") }
            Spacer(Modifier.height(12.dp))
            Text(
                "Úsalo para ubicar el mejor lugar del router o del repetidor: camina despacio y " +
                    "observa dónde la señal sube. Mueve el celular a la altura donde lo usarás.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }

    if (showPicker) {
        AlertDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = { TextButton(onClick = { showPicker = false }) { Text("Cerrar") } },
            title = { Text("Elegir red") },
            text = {
                LazyColumn(Modifier.heightIn(max = 400.dp)) {
                    items(state.networks, key = { it.bssid }) { n ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onChange(n.bssid)
                                    showPicker = false
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(n.displaySsid, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                Text("${n.band.label} · canal ${n.channel}", style = MaterialTheme.typography.bodySmall)
                            }
                            Spacer(Modifier.width(8.dp))
                            Text("${n.rssi} dBm", color = signalColor(n.rssi))
                        }
                    }
                }
            }
        )
    }
}
