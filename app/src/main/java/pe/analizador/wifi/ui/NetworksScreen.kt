@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package pe.analizador.wifi.ui

import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import pe.analizador.wifi.Band
import pe.analizador.wifi.UiState
import pe.analizador.wifi.WifiNetwork
import pe.analizador.wifi.formatDistance
import pe.analizador.wifi.signalLabel
import pe.analizador.wifi.signalQuality

@Composable
fun NetworksScreen(state: UiState, onFind: (String) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var bandFilter by rememberSaveable { mutableStateOf<Band?>(null) }
    var expanded by rememberSaveable { mutableStateOf<String?>(null) }

    val s = state.settings
    val list = remember(state.networks, query, bandFilter, s) {
        state.networks
            .filter { n ->
                (bandFilter == null || n.band == bandFilter) &&
                    (s.showHidden || !n.isHidden) &&
                    n.rssi >= s.minRssi &&
                    (query.isBlank() || n.displaySsid.contains(query, ignoreCase = true) ||
                        n.bssid.contains(query, ignoreCase = true))
            }
            .let { l ->
                when (s.sort) {
                    1 -> l.sortedBy { it.displaySsid.lowercase() }
                    2 -> l.sortedWith(compareBy<WifiNetwork>({ it.band.ordinal }, { it.channel }, { -it.rssi }))
                    else -> l.sortedByDescending { it.rssi }
                }
            }
    }

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            placeholder = { Text("Buscar por nombre o MAC") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true
        )
        Row(
            Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = bandFilter == null,
                onClick = { bandFilter = null },
                label = { Text("Todas (${state.networks.size})") }
            )
            Band.entries.forEach { b ->
                val c = state.networks.count { it.band == b }
                FilterChip(
                    selected = bandFilter == b,
                    onClick = { bandFilter = b },
                    label = { Text("${b.label} ($c)") }
                )
            }
        }
        if (list.isEmpty()) {
            Text(
                if (state.networks.isEmpty()) "Buscando redes… Si no aparece ninguna, revisa que el Wi-Fi y la ubicación estén activados."
                else "Ninguna red coincide con los filtros.",
                modifier = Modifier.padding(24.dp),
                style = MaterialTheme.typography.bodyMedium
            )
        }
        LazyColumn(
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(list, key = { it.bssid }) { n ->
                NetworkCard(
                    n = n,
                    expanded = expanded == n.bssid,
                    onClick = { expanded = if (expanded == n.bssid) null else n.bssid },
                    onFind = { onFind(n.bssid) }
                )
            }
        }
    }
}

@Composable
private fun NetworkCard(n: WifiNetwork, expanded: Boolean, onClick: () -> Unit, onFind: () -> Unit) {
    val ctx = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val primary = MaterialTheme.colorScheme.primary
    val warn = Color(0xFFFB8C00)
    val danger = Color(0xFFE53935)
    val neutral = MaterialTheme.colorScheme.onSurfaceVariant

    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SignalBars(n.rssi, Modifier.size(30.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        n.displaySsid,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = colorFor(n.bssid)
                    )
                    Text(n.bssid.uppercase(), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                    Text(
                        "Canal ${n.channel}" +
                            (if (n.centerChannel != n.channel && n.widthMhz > 20) " (centro ${n.centerChannel})" else "") +
                            " · ${n.band.label} · ${n.widthMhz} MHz",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "${n.rssi}",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = signalColor(n.rssi)
                    )
                    Text("dBm", style = MaterialTheme.typography.labelSmall)
                    Text("≈" + formatDistance(n.distanceM), style = MaterialTheme.typography.labelSmall)
                }
            }
            Spacer(Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (n.isConnected) Tag("Conectado", primary)
                if (n.isSameDeviceAsConnected) Tag("Tu mismo router", primary)
                Tag(n.security.label, if (n.security.risk >= 2) danger else if (n.security.risk == 1) warn else neutral)
                if (n.wps) Tag("WPS activo", warn)
                if (n.tkip) Tag("Cifrado TKIP (débil)", warn)
                Tag(n.standard.substringBefore(" ("), neutral)
                if (n.isDfs) Tag("DFS", warn)
                if (n.randomizedMac) Tag("MAC virtual", neutral)
                if (n.isHidden) Tag("Oculta", neutral)
            }
            if (expanded) {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                Detail("Señal", "${n.rssi} dBm · ${signalQuality(n.rssi)}% · ${signalLabel(n.rssi)}")
                Detail("Frecuencia", "${n.frequency} MHz (central ${n.centerFrequency} MHz)")
                Detail("Ocupa", "${n.lowFreq}–${n.highFreq} MHz")
                Detail("Estándar", n.standard)
                Detail("Distancia", "≈${formatDistance(n.distanceM)} en línea de vista; con paredes la real es menor")
                if (n.supports80211mc) Detail("802.11mc", "Soporta medición de distancia RTT")
                if (n.siblings.isNotEmpty()) Detail("Mismo equipo que", n.siblings.joinToString(", "))
                if (n.security.risk >= 2) Detail("Riesgo", "Seguridad débil: cualquiera puede interceptar el tráfico.")
                if (n.wps) Detail("Aviso WPS", "WPS por PIN es vulnerable; conviene desactivarlo en el router.")
                Detail("Capacidades", n.capabilities.ifBlank { "—" })
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onFind) { Text("Buscar señal") }
                    TextButton(onClick = {
                        clipboard.setText(AnnotatedString(n.bssid.uppercase()))
                        Toast.makeText(ctx, "MAC copiada", Toast.LENGTH_SHORT).show()
                    }) { Text("Copiar MAC") }
                    TextButton(onClick = {
                        openUrl(ctx, "https://maclookup.app/search/result?mac=" + n.bssid.uppercase())
                    }) { Text("Fabricante (web)") }
                }
            }
        }
    }
}

@Composable
fun Detail(label: String, value: String) {
    Row(Modifier.padding(vertical = 2.dp)) {
        Text(
            label,
            modifier = Modifier.width(110.dp),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold
        )
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}
