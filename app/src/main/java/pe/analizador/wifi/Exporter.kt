package pe.analizador.wifi

import android.content.Context
import android.net.Uri
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Exporter {

    fun defaultFileName(): String =
        "wifi_" + SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date()) + ".csv"

    fun writeCsv(ctx: Context, uri: Uri, nets: List<WifiNetwork>): Boolean = try {
        val out = ctx.contentResolver.openOutputStream(uri) ?: throw IllegalStateException("sin salida")
        out.bufferedWriter(Charsets.UTF_8).use { w ->
            w.write("﻿")
            w.write(
                listOf(
                    "SSID", "BSSID", "RSSI_dBm", "Calidad_%", "Banda", "Canal", "Canal_central",
                    "Frecuencia_MHz", "Frecuencia_central_MHz", "Ancho_MHz", "Seguridad", "WPS",
                    "Estandar", "Distancia_m", "DFS", "Conectado", "Mismo_router", "Visto",
                    "Capacidades"
                ).joinToString(",")
            )
            w.write("\n")
            val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
            for (n in nets) {
                val row = listOf(
                    n.displaySsid, n.bssid, n.rssi.toString(), signalQuality(n.rssi).toString(),
                    n.band.label, n.channel.toString(), n.centerChannel.toString(),
                    n.frequency.toString(), n.centerFrequency.toString(), n.widthMhz.toString(),
                    n.security.label, if (n.wps) "Si" else "No", n.standard,
                    String.format(Locale.US, "%.1f", n.distanceM),
                    if (n.isDfs) "Si" else "No", if (n.isConnected) "Si" else "No",
                    if (n.isSameDeviceAsConnected) "Si" else "No",
                    fmt.format(Date(n.lastSeenMs)), n.capabilities
                )
                w.write(row.joinToString(",") { csv(it) })
                w.write("\n")
            }
        }
        true
    } catch (_: Exception) {
        false
    }

    private fun csv(s: String): String = "\"" + s.replace("\"", "\"\"") + "\""
}
