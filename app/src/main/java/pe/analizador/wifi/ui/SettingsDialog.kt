@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package pe.analizador.wifi.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import pe.analizador.wifi.Settings
import kotlin.math.roundToInt

@Composable
fun SettingsDialog(s: Settings, onChange: (Settings) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Listo") } },
        title = { Text("Ajustes") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Section("Intervalo de escaneo: ${s.intervalSec} s")
                Slider(
                    value = s.intervalSec.toFloat(),
                    onValueChange = { onChange(s.copy(intervalSec = it.roundToInt())) },
                    valueRange = 2f..30f,
                    steps = 27
                )
                Text(
                    "Con el límite de Android activo, la app escanea como máximo cada 30 s.",
                    style = MaterialTheme.typography.labelSmall
                )

                Section("Señal mínima a mostrar: ${s.minRssi} dBm")
                Slider(
                    value = s.minRssi.toFloat(),
                    onValueChange = { onChange(s.copy(minRssi = it.roundToInt())) },
                    valueRange = -100f..-50f,
                    steps = 49
                )

                Section("Ocultar redes no vistas después de")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(30, 60, 120, 300).forEach { v ->
                        FilterChip(
                            selected = s.maxAgeSec == v,
                            onClick = { onChange(s.copy(maxAgeSec = v)) },
                            label = { Text(if (v < 60) "$v s" else "${v / 60} min") }
                        )
                    }
                }

                Section("Ordenar redes por")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Señal", "Nombre", "Canal").forEachIndexed { i, label ->
                        FilterChip(selected = s.sort == i, onClick = { onChange(s.copy(sort = i)) }, label = { Text(label) })
                    }
                }

                Section("Tema")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Sistema", "Claro", "Oscuro").forEachIndexed { i, label ->
                        FilterChip(selected = s.themeMode == i, onClick = { onChange(s.copy(themeMode = i)) }, label = { Text(label) })
                    }
                }

                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                SwitchRow("Mostrar redes ocultas", s.showHidden) { onChange(s.copy(showHidden = it)) }
                SwitchRow("Considerar canales 12 y 13 (2.4 GHz)", s.allowCh1213) { onChange(s.copy(allowCh1213 = it)) }
                Text(
                    "Los canales 12 y 13 no los ven muchos equipos configurados para América; " +
                        "déjalo apagado salvo que sepas que todos tus equipos los soportan.",
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    )
}

@Composable
private fun Section(text: String) {
    Spacer(Modifier.height(10.dp))
    Text(text, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun SwitchRow(text: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
