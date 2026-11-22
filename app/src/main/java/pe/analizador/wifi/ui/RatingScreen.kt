@file:OptIn(ExperimentalMaterial3Api::class)

package pe.analizador.wifi.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import pe.analizador.wifi.Band
import pe.analizador.wifi.ChannelAnalyzer
import pe.analizador.wifi.ChannelScore
import pe.analizador.wifi.UiState
import java.util.Locale

private fun fmt(d: Double) = String.format(Locale.US, "%.1f", d)

private fun scoreColor(s: Double): Color = when {
    s >= 8 -> Color(0xFF43A047)
    s >= 6 -> Color(0xFF9CCC65)
    s >= 4 -> Color(0xFFFB8C00)
    else -> Color(0xFFE53935)
}

@Composable
fun RatingScreen(state: UiState, band: Band, onBand: (Band) -> Unit) {
    var selectedWidth by rememberSaveable { mutableStateOf(20) }
    val widths = ChannelAnalyzer.widths(band)
    val width = if (selectedWidth in widths) selectedWidth else 20

    val own = remember(state.networks) {
        state.networks.filter { it.isConnected || it.isSameDeviceAsConnected }.map { it.bssid }.toSet()
    }
    val scores = remember(state.networks, band, width, state.settings.allowCh1213, own) {
        ChannelAnalyzer.evaluate(state.networks, band, width, own, state.settings.allowCh1213)
    }
    val best = scores.firstOrNull { it.recommended }
    val myNet = state.networks.firstOrNull { it.isConnected && it.band == band }
    val myScore: ChannelScore? = myNet?.let { m ->
        val f = pe.analizador.wifi.WifiMath.freqOf(m.channel, band)
        scores.firstOrNull { it.contains(f) && it.primary <= m.channel }
            ?: scores.firstOrNull { it.contains(f) }
    }

    Column(Modifier.fillMaxSize()) {
        BandSelector(band, Band.entries.associateWith { b -> state.networks.count { it.band == b } }, onBand)
        Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            widths.forEach { w ->
                FilterChip(selected = width == w, onClick = { selectedWidth = w }, label = { Text("$w MHz") })
            }
        }
        LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(14.dp)) {
                        if (best == null || state.networks.isEmpty()) {
                            Text("Aún no hay datos suficientes. Espera el primer escaneo.")
                        } else {
                            Text("Canal recomendado", style = MaterialTheme.typography.labelMedium)
                            Text(
                                "${best.label}  ·  ${fmt(best.score)}/10",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(6.dp))
                            val msg = buildString {
                                if (myNet != null && myScore != null) {
                                    if (myScore.label == best.label) {
                                        append("Tu red \"${myNet.displaySsid}\" ya está en el mejor canal. ")
                                    } else {
                                        val gain = myScore.interferenceDbm - best.interferenceDbm
                                        append("Tu red \"${myNet.displaySsid}\" está en el canal ${myNet.channel} (${fmt(myScore.score)}/10). ")
                                        if (gain > 0.5) append("Cambiando al ${best.label} reducirías la interferencia en ~${fmt(gain)} dB. ")
                                    }
                                } else if (myNet == null) {
                                    append("No estás conectado en esta banda; la recomendación es para configurar tu router. ")
                                }
                                if (best.isDfs) append("Es un canal DFS: algunos equipos antiguos no lo ven y el router puede cambiar de canal si detecta un radar. ")
                                if (band == Band.GHZ_2_4 && width > 20) append("En 2.4 GHz se recomienda 20 MHz: 40 MHz invade casi toda la banda. ")
                                if (band == Band.GHZ_2_4 && width == 20) append("Solo se recomiendan 1, 6 y 11 porque no se superponen entre sí. ")
                                if (own.isNotEmpty()) append("Se excluyen las redes de tu propio router.")
                            }
                            Text(msg, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
            items(scores, key = { it.label }) { s ->
                ScoreRow(s, isMine = myScore?.label == s.label)
            }
            item {
                Text(
                    "La calificación suma la potencia de las redes vecinas que se superponen con cada canal, " +
                        "ponderada por cuánto se superponen. En 2.4 GHz una superposición parcial cuenta más que " +
                        "compartir el mismo canal, porque los equipos no se coordinan entre sí.",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun ScoreRow(s: ChannelScore, isMine: Boolean) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (s.recommended) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                s.label,
                modifier = Modifier.width(72.dp),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Column(Modifier.weight(1f)) {
                LinearProgressIndicator(
                    progress = { (s.score / 10.0).toFloat() },
                    color = scoreColor(s.score),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "${s.apCount} redes" + if (s.apCount > 0) " · ${fmt(s.interferenceDbm)} dBm" else "",
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (s.recommended) Tag("Recomendado", Color(0xFF43A047))
                    if (isMine) Tag("Tu canal", MaterialTheme.colorScheme.primary)
                    if (s.isDfs) Tag("DFS", Color(0xFFFB8C00))
                }
            }
            Spacer(Modifier.width(10.dp))
            Text(fmt(s.score), fontWeight = FontWeight.Bold, color = scoreColor(s.score))
        }
    }
}
