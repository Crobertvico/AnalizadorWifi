@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package pe.analizador.wifi.ui

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import pe.analizador.wifi.UiState
import pe.analizador.wifi.signalLabel
import pe.analizador.wifi.signalQuality

@Composable
fun ConnectionScreen(state: UiState, onFind: (String) -> Unit) {
    val ctx = LocalContext.current
    val c = state.connection
    val net = c?.let { conn -> state.networks.firstOrNull { it.bssid == conn.bssid } }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp)) {
                if (c == null) {
                    Text("No estás conectado a una red Wi-Fi.", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { openWifiSettings(ctx) }) { Text("Abrir ajustes de Wi-Fi") }
                } else {
                    Text("Conexión actual", style = MaterialTheme.typography.labelMedium)
                    Text(
                        c.ssid.ifBlank { "(nombre no disponible)" },
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "${c.rssi} dBm · ${signalLabel(c.rssi)} · ${signalQuality(c.rssi)}%",
                        color = signalColor(c.rssi),
                        fontWeight = FontWeight.Bold
                    )
                    LinearProgressIndicator(
                        progress = { signalQuality(c.rssi) / 100f },
                        color = signalColor(c.rssi),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                    )
                    Detail("BSSID", c.bssid.uppercase())
                    Detail("Banda / canal", "${c.band?.label ?: "?"} · canal ${c.channel} · ${c.frequency} MHz")
                    if (net != null) Detail("Ancho", "${net.widthMhz} MHz (central ${net.centerFrequency} MHz)")
                    Detail("Estándar", c.standard)
                    if (net != null) Detail("Seguridad", net.security.label + if (net.wps) " · WPS activo" else "")
                    Detail(
                        "Velocidad",
                        if (c.txSpeed > 0 || c.rxSpeed > 0) "Subida ${c.txSpeed} Mbps · Bajada ${c.rxSpeed} Mbps"
                        else "${c.linkSpeed} Mbps"
                    )
                    Detail("IP", c.ip ?: "—")
                    Detail("Puerta de enlace", c.gateway ?: "—")
                    Detail("DNS", c.dns.joinToString(", ").ifBlank { "—" })
                    if (net != null && net.siblings.isNotEmpty()) Detail("Tu router emite", net.siblings.joinToString(", "))
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { onFind(c.bssid) }) { Text("Buscador de señal") }
                        val gw = c.gateway
                        if (gw != null) {
                            OutlinedButton(onClick = { openUrl(ctx, "http://$gw") }) { Text("Panel del router") }
                        }
                        OutlinedButton(onClick = { openWifiSettings(ctx) }) { Text("Ajustes Wi-Fi") }
                    }
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp)) {
                Text("Límite de escaneo de Android", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                if (state.throttleEnabled) {
                    Text(
                        "Activado: Android solo permite 4 escaneos cada 2 minutos. La app los reparte " +
                            "(uno cada 30 s) y aprovecha los escaneos del sistema, pero para ver cambios en " +
                            "tiempo real desactiva el límite:\n\n" +
                            "1. Ajustes > Acerca del teléfono > toca 7 veces \"Número de compilación\".\n" +
                            "2. Ajustes > Sistema > Opciones de desarrollador.\n" +
                            "3. Desactiva \"Limitación de búsqueda de Wi-Fi\".",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = {
                        openIntent(ctx, Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
                    }) { Text("Abrir opciones de desarrollador") }
                } else {
                    Text(
                        "Desactivado: la app escanea cada ${state.settings.intervalSec} s.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "Escaneos en los últimos 2 min: ${state.scansInWindow}",
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp)) {
                Text("Cómo leer la señal", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Detail("-30 a -50", "Excelente (al lado del router)")
                Detail("-50 a -60", "Muy buena: video 4K y juegos")
                Detail("-60 a -70", "Buena: navegación y streaming")
                Detail("-70 a -80", "Débil: cortes y lentitud")
                Detail("< -80", "Muy débil: conexión inestable")
            }
        }
    }
}

fun openWifiSettings(ctx: android.content.Context) {
    val intent = if (Build.VERSION.SDK_INT >= 29) Intent(Settings.Panel.ACTION_WIFI) else Intent(Settings.ACTION_WIFI_SETTINGS)
    openIntent(ctx, intent)
}
