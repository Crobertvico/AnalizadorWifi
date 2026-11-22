package pe.analizador.wifi

import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.pow

enum class Band(val label: String) {
    GHZ_2_4("2.4 GHz"),
    GHZ_5("5 GHz"),
    GHZ_6("6 GHz")
}

enum class SecurityType(val label: String, val risk: Int) {
    OPEN("Abierta", 3),
    WEP("WEP", 3),
    WPA("WPA", 2),
    WPA2("WPA2", 0),
    WPA2_WPA3("WPA2/WPA3", 0),
    WPA3("WPA3", 0),
    OWE("OWE (abierta cifrada)", 1),
    ENTERPRISE("Enterprise 802.1X", 0)
}

data class WifiNetwork(
    val ssid: String,
    val bssid: String,
    val rssi: Int,
    val frequency: Int,
    val centerFrequency: Int,
    val widthMhz: Int,
    val band: Band,
    val channel: Int,
    val centerChannel: Int,
    val capabilities: String,
    val security: SecurityType,
    val wps: Boolean,
    val tkip: Boolean,
    val standard: String,
    val distanceM: Double,
    val isConnected: Boolean,
    val isSameDeviceAsConnected: Boolean,
    val randomizedMac: Boolean,
    val supports80211mc: Boolean,
    val lastSeenMs: Long,
    val siblings: List<String> = emptyList()
) {
    val displaySsid: String get() = ssid.ifBlank { "(Red oculta)" }
    val isHidden: Boolean get() = ssid.isBlank()
    val lowFreq: Int get() = centerFrequency - widthMhz / 2
    val highFreq: Int get() = centerFrequency + widthMhz / 2
    val isDfs: Boolean get() = band == Band.GHZ_5 && channel in 52..144
}

data class Sample(val timeMs: Long, val rssi: Int)

data class ConnectionInfo(
    val ssid: String,
    val bssid: String,
    val rssi: Int,
    val linkSpeed: Int,
    val txSpeed: Int,
    val rxSpeed: Int,
    val frequency: Int,
    val channel: Int,
    val band: Band?,
    val standard: String,
    val ip: String?,
    val gateway: String?,
    val dns: List<String>
)

data class Settings(
    val intervalSec: Int = 5,
    val maxAgeSec: Int = 60,
    val showHidden: Boolean = true,
    val allowCh1213: Boolean = false,
    val themeMode: Int = 0,      // 0 sistema, 1 claro, 2 oscuro
    val sort: Int = 0,           // 0 señal, 1 nombre, 2 canal
    val minRssi: Int = -100
)

data class UiState(
    val networks: List<WifiNetwork> = emptyList(),
    val connection: ConnectionInfo? = null,
    val history: Map<String, List<Sample>> = emptyMap(),
    val paused: Boolean = false,
    val wifiEnabled: Boolean = true,
    val throttleEnabled: Boolean = false,
    val scansInWindow: Int = 0,
    val nextScanAt: Long = 0L,      // SystemClock.elapsedRealtime()
    val lastUpdate: Long = 0L,      // System.currentTimeMillis()
    val settings: Settings = Settings()
)

object WifiMath {

    val channels5: List<Int> = listOf(
        36, 40, 44, 48, 52, 56, 60, 64,
        100, 104, 108, 112, 116, 120, 124, 128, 132, 136, 140, 144,
        149, 153, 157, 161, 165
    )

    fun bandOf(f: Int): Band? = when (f) {
        in 2400..2500 -> Band.GHZ_2_4
        in 4900..5924 -> Band.GHZ_5
        in 5925..7125 -> Band.GHZ_6
        else -> null
    }

    fun channelOf(f: Int): Int = when {
        f == 2484 -> 14
        f in 2400..2483 -> (f - 2407) / 5
        f == 5935 -> 2
        f in 5925..7125 -> (f - 5950) / 5
        f in 4900..5924 -> (f - 5000) / 5
        else -> 0
    }

    fun freqOf(ch: Int, band: Band): Int = when (band) {
        Band.GHZ_2_4 -> if (ch == 14) 2484 else 2407 + ch * 5
        Band.GHZ_5 -> 5000 + ch * 5
        Band.GHZ_6 -> 5950 + ch * 5
    }

    fun widthFromCode(code: Int): Int = when (code) {
        1 -> 40
        2 -> 80
        3 -> 160
        4 -> 160
        5 -> 320
        else -> 20
    }

    /**
     * Calcula la frecuencia central real del bloque.
     * Corrige dos fallas comunes: drivers que en 160 MHz informan en centerFreq0 el
     * centro del segmento de 80 MHz (y el de 160 en centerFreq1), y datos incoherentes
     * donde el canal primario queda fuera del bloque.
     */
    fun centerFreq(freq: Int, width: Int, cf0: Int, cf1: Int): Int {
        if (width <= 20) return freq
        var c = if (cf0 > 0) cf0 else freq
        if (width == 160 && cf1 > 0 && abs(cf1 - cf0) == 40) c = cf1
        if (abs(freq - c) > width / 2 - 10) c = freq
        return c
    }

    fun distance(rssi: Int, freq: Int): Double =
        10.0.pow((27.55 - 20 * log10(freq.toDouble()) + abs(rssi)) / 20.0)

    fun parseSecurity(capabilities: String): SecurityType {
        val c = capabilities.uppercase()
        val sae = c.contains("SAE") || c.contains("WPA3")
        val psk = c.contains("PSK")
        return when {
            c.contains("EAP") && !sae -> SecurityType.ENTERPRISE
            sae && psk -> SecurityType.WPA2_WPA3
            sae -> SecurityType.WPA3
            c.contains("OWE") -> SecurityType.OWE
            c.contains("RSN") || c.contains("WPA2") -> SecurityType.WPA2
            c.contains("WPA") -> SecurityType.WPA
            c.contains("WEP") -> SecurityType.WEP
            else -> SecurityType.OPEN
        }
    }

    fun standardLabel(code: Int, band: Band?, width: Int = 20): String = when (code) {
        1 -> "Legacy (802.11a/b/g)"
        4 -> "Wi-Fi 4 (802.11n)"
        5 -> "Wi-Fi 5 (802.11ac)"
        6 -> if (band == Band.GHZ_6) "Wi-Fi 6E (802.11ax)" else "Wi-Fi 6 (802.11ax)"
        7 -> "WiGig (802.11ad)"
        8 -> "Wi-Fi 7 (802.11be)"
        else -> when {
            band == Band.GHZ_6 -> "Wi-Fi 6E o superior"
            band == Band.GHZ_5 && width >= 80 -> "Wi-Fi 5 o superior"
            else -> "No informado"
        }
    }

    fun macBytes(mac: String): IntArray? {
        val p = mac.split(":")
        if (p.size != 6) return null
        return try {
            IntArray(6) { p[it].toInt(16) }
        } catch (e: NumberFormatException) {
            null
        }
    }

    fun isLocallyAdministered(mac: String): Boolean {
        val b = macBytes(mac) ?: return false
        return (b[0] and 0x02) != 0
    }

    /** Heurística: dos BSSID muy cercanos suelen ser radios/SSID del mismo equipo. */
    fun sameDevice(a: String, b: String): Boolean {
        if (a.equals(b, ignoreCase = true)) return false
        val x = macBytes(a) ?: return false
        val y = macBytes(b) ?: return false
        if (x[0] != y[0]) {
            val localBit = (x[0] and 0x02) != 0 || (y[0] and 0x02) != 0
            if (!localBit || (x[0] and 0xF1) != (y[0] and 0xF1)) return false
        }
        if (x[1] != y[1] || x[2] != y[2]) return false
        val tx = (x[3] shl 16) or (x[4] shl 8) or x[5]
        val ty = (y[3] shl 16) or (y[4] shl 8) or y[5]
        return abs(tx - ty) <= 16
    }

    /** Segmentos de frecuencia a dibujar (en 5 GHz se omite el hueco 5340–5480 MHz). */
    fun segments(band: Band): List<Pair<Int, Int>> = when (band) {
        Band.GHZ_2_4 -> listOf(2400 to 2496)
        Band.GHZ_5 -> listOf(5160 to 5340, 5480 to 5845)
        Band.GHZ_6 -> listOf(5925 to 7125)
    }

    fun axisChannels(band: Band): List<Int> = when (band) {
        Band.GHZ_2_4 -> (1..14).toList()
        Band.GHZ_5 -> channels5
        Band.GHZ_6 -> (1..233 step 4).toList()
    }
}

fun formatDistance(m: Double): String = when {
    m < 1 -> "<1 m"
    m < 10 -> String.format(java.util.Locale.US, "%.1f m", m)
    m < 1000 -> "${m.toInt()} m"
    else -> String.format(java.util.Locale.US, "%.1f km", m / 1000)
}

fun signalQuality(rssi: Int): Int = ((rssi + 100) * 2).coerceIn(0, 100)

fun signalLabel(rssi: Int): String = when {
    rssi >= -50 -> "Excelente"
    rssi >= -60 -> "Muy buena"
    rssi >= -70 -> "Buena"
    rssi >= -80 -> "Débil"
    else -> "Muy débil"
}
