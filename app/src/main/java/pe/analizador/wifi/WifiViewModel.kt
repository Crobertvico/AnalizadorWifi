package pe.analizador.wifi

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Build
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.Inet4Address

class WifiViewModel(app: Application) : AndroidViewModel(app) {

    private val ctx: Context = app.applicationContext
    private val wifi = ctx.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val prefs = ctx.getSharedPreferences("ajustes", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(UiState(settings = loadSettings()))
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val history = HashMap<String, ArrayDeque<Sample>>()
    private val lastTimestamp = HashMap<String, Long>()
    private val scanTimes = ArrayDeque<Long>()

    private var scanJob: Job? = null
    private var connJob: Job? = null
    private var registered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            readResults()
        }
    }

    // ---------------------------------------------------------------- ciclo de vida

    fun start() {
        if (!registered) {
            try {
                ContextCompat.registerReceiver(
                    ctx, receiver,
                    IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION),
                    ContextCompat.RECEIVER_EXPORTED
                )
                registered = true
            } catch (_: Exception) {
            }
        }
        if (scanJob?.isActive != true) {
            scanJob = viewModelScope.launch {
                while (isActive) {
                    if (!_state.value.paused) requestScan(force = false)
                    readResults()
                    delay(_state.value.settings.intervalSec.coerceAtLeast(2) * 1000L)
                }
            }
        }
        if (connJob?.isActive != true) {
            connJob = viewModelScope.launch {
                while (isActive) {
                    if (!_state.value.paused) pollConnection()
                    delay(1000)
                }
            }
        }
    }

    fun stop() {
        scanJob?.cancel()
        connJob?.cancel()
        scanJob = null
        connJob = null
        if (registered) {
            try {
                ctx.unregisterReceiver(receiver)
            } catch (_: Exception) {
            }
            registered = false
        }
    }

    override fun onCleared() {
        stop()
        super.onCleared()
    }

    fun togglePause() {
        _state.update { it.copy(paused = !it.paused) }
    }

    fun scanNow() {
        requestScan(force = true)
        readResults()
    }

    // ---------------------------------------------------------------- ajustes

    private fun loadSettings() = Settings(
        intervalSec = prefs.getInt("interval", 5),
        maxAgeSec = prefs.getInt("maxAge", 60),
        showHidden = prefs.getBoolean("hidden", true),
        allowCh1213 = prefs.getBoolean("ch1213", false),
        themeMode = prefs.getInt("theme", 0),
        sort = prefs.getInt("sort", 0),
        minRssi = prefs.getInt("minRssi", -100)
    )

    fun updateSettings(s: Settings) {
        prefs.edit()
            .putInt("interval", s.intervalSec)
            .putInt("maxAge", s.maxAgeSec)
            .putBoolean("hidden", s.showHidden)
            .putBoolean("ch1213", s.allowCh1213)
            .putInt("theme", s.themeMode)
            .putInt("sort", s.sort)
            .putInt("minRssi", s.minRssi)
            .apply()
        _state.update { it.copy(settings = s) }
        readResults()
    }

    // ---------------------------------------------------------------- escaneo

    private fun throttleEnabled(): Boolean = when {
        Build.VERSION.SDK_INT >= 30 -> try {
            wifi.isScanThrottleEnabled
        } catch (_: Exception) {
            true
        }
        Build.VERSION.SDK_INT >= 28 -> true
        else -> false
    }

    /**
     * Android 9+ limita a 4 escaneos cada 2 minutos. En vez de gastar los 4 en
     * pocos segundos y quedar congelado ~2 min (queja típica), con la limitación
     * activa se reparte un escaneo cada 30 s y entre medio se aprovechan los
     * escaneos que hace el propio sistema.
     */
    private fun requestScan(force: Boolean) {
        val enabled = wifi.isWifiEnabled
        val throttle = throttleEnabled()
        val now = SystemClock.elapsedRealtime()
        while (scanTimes.isNotEmpty() && now - scanTimes.first() >= WINDOW_MS) scanTimes.removeFirst()

        if (!enabled) {
            _state.update { it.copy(wifiEnabled = false, throttleEnabled = throttle) }
            return
        }
        if (throttle) {
            if (scanTimes.size >= MAX_SCANS) {
                _state.update {
                    it.copy(
                        wifiEnabled = true, throttleEnabled = true,
                        scansInWindow = scanTimes.size, nextScanAt = scanTimes.first() + WINDOW_MS
                    )
                }
                return
            }
            if (!force && scanTimes.isNotEmpty() && now - scanTimes.last() < SPACING_MS) {
                _state.update {
                    it.copy(
                        wifiEnabled = true, throttleEnabled = true,
                        scansInWindow = scanTimes.size, nextScanAt = scanTimes.last() + SPACING_MS
                    )
                }
                return
            }
        }
        @Suppress("DEPRECATION")
        val ok = try {
            wifi.startScan()
        } catch (_: Exception) {
            false
        }
        if (ok) scanTimes.addLast(now)
        val next = when {
            !throttle -> 0L
            !ok -> now + SPACING_MS
            scanTimes.size >= MAX_SCANS -> scanTimes.first() + WINDOW_MS
            else -> now + SPACING_MS
        }
        _state.update {
            it.copy(
                wifiEnabled = true, throttleEnabled = throttle,
                scansInWindow = scanTimes.size, nextScanAt = next
            )
        }
    }

    private fun readResults() {
        val raw: List<ScanResult> = try {
            wifi.scanResults ?: emptyList()
        } catch (_: SecurityException) {
            emptyList()
        }
        val conn = readConnection()
        val settings = _state.value.settings
        val nowWall = System.currentTimeMillis()
        val nowBoot = SystemClock.elapsedRealtime()
        val connBssid = conn?.bssid

        // Quita duplicados por BSSID (algunos drivers repiten entradas y eso
        // provocaba cierres en otras apps).
        val byBssid = LinkedHashMap<String, ScanResult>()
        for (r in raw) {
            val b = r.BSSID?.lowercase() ?: continue
            val prev = byBssid[b]
            if (prev == null || r.timestamp > prev.timestamp) byBssid[b] = r
        }

        val nets = ArrayList<WifiNetwork>()
        for ((b, r) in byBssid) {
            val ageMs = (nowBoot - r.timestamp / 1000).coerceAtLeast(0)
            // Descarta redes "fantasma" que ya no se ven.
            if (ageMs > settings.maxAgeSec * 1000L) continue
            val band = WifiMath.bandOf(r.frequency) ?: continue
            val width = WifiMath.widthFromCode(r.channelWidth)
            val center = WifiMath.centerFreq(r.frequency, width, r.centerFreq0, r.centerFreq1)
            val stdCode = if (Build.VERSION.SDK_INT >= 30) r.wifiStandard else 0
            @Suppress("DEPRECATION")
            val rawSsid: String = r.SSID ?: ""
            val cap = r.capabilities ?: ""
            val isConn = b == connBssid
            nets += WifiNetwork(
                ssid = cleanSsid(rawSsid),
                bssid = b,
                rssi = if (isConn && conn != null && conn.rssi > -127) conn.rssi else r.level,
                frequency = r.frequency,
                centerFrequency = center,
                widthMhz = width,
                band = band,
                channel = WifiMath.channelOf(r.frequency),
                centerChannel = WifiMath.channelOf(center),
                capabilities = cap,
                security = WifiMath.parseSecurity(cap),
                wps = cap.contains("WPS"),
                tkip = cap.contains("TKIP"),
                standard = WifiMath.standardLabel(stdCode, band, width),
                distanceM = WifiMath.distance(r.level, r.frequency),
                isConnected = isConn,
                isSameDeviceAsConnected = connBssid != null && !isConn && WifiMath.sameDevice(b, connBssid),
                randomizedMac = WifiMath.isLocallyAdministered(b),
                supports80211mc = r.is80211mcResponder(),
                lastSeenMs = nowWall - ageMs
            )

            // Historial: solo se agrega una muestra cuando el resultado es nuevo.
            if (lastTimestamp[b] != r.timestamp) {
                lastTimestamp[b] = r.timestamp
                history.getOrPut(b) { ArrayDeque() }.addLast(Sample(nowWall - ageMs, r.level))
            }
        }

        val withSiblings = nets.map { n ->
            val sib = nets.filter { o -> WifiMath.sameDevice(o.bssid, n.bssid) }
                .map { o -> "${o.displaySsid} (${o.band.label})" }
                .distinct()
            if (sib.isEmpty()) n else n.copy(siblings = sib)
        }

        trimHistory(nowWall)
        _state.update {
            it.copy(
                networks = withSiblings.sortedByDescending { n -> n.rssi },
                connection = conn,
                wifiEnabled = wifi.isWifiEnabled,
                history = history.mapValues { e -> e.value.toList() },
                lastUpdate = withSiblings.maxOfOrNull { n -> n.lastSeenMs } ?: it.lastUpdate
            )
        }
    }

    /** La señal de la red conectada se lee cada segundo sin gastar escaneos. */
    private fun pollConnection() {
        val conn = readConnection()
        val now = System.currentTimeMillis()
        var newHistory: Map<String, List<Sample>>? = null
        if (conn != null && conn.rssi > -127) {
            val dq = history.getOrPut(conn.bssid) { ArrayDeque() }
            if (dq.isEmpty() || now - dq.last().timeMs >= 900) {
                dq.addLast(Sample(now, conn.rssi))
                newHistory = _state.value.history + (conn.bssid to dq.toList())
            }
        }
        _state.update { st ->
            st.copy(
                connection = conn,
                wifiEnabled = wifi.isWifiEnabled,
                history = newHistory ?: st.history,
                networks = if (conn == null || conn.rssi <= -127) st.networks else st.networks.map { n ->
                    if (n.bssid == conn.bssid) n.copy(rssi = conn.rssi) else n
                }
            )
        }
    }

    private fun trimHistory(nowWall: Long) {
        val cutoff = nowWall - HISTORY_MS
        val empty = ArrayList<String>()
        for ((k, dq) in history) {
            while (dq.isNotEmpty() && dq.first().timeMs < cutoff) dq.removeFirst()
            if (dq.isEmpty()) empty += k
        }
        for (k in empty) {
            history.remove(k)
            lastTimestamp.remove(k)
        }
    }

    @Suppress("DEPRECATION")
    private fun readConnection(): ConnectionInfo? {
        if (!wifi.isWifiEnabled) return null
        val maybeInfo: android.net.wifi.WifiInfo? = try {
            wifi.connectionInfo
        } catch (_: Exception) {
            null
        }
        val info = maybeInfo ?: return null
        val bssid = info.bssid ?: return null
        if (bssid == "02:00:00:00:00:00" || bssid == "00:00:00:00:00:00") return null

        var ssid = info.ssid ?: ""
        if (ssid == WifiManager.UNKNOWN_SSID) ssid = ""
        ssid = ssid.removeSurrounding("\"")

        val freq = info.frequency
        val band = WifiMath.bandOf(freq)

        var ip: String? = null
        var gw: String? = null
        var dns: List<String> = emptyList()
        try {
            val net = cm.allNetworks.firstOrNull { n ->
                cm.getNetworkCapabilities(n)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
            }
            val lp = if (net != null) cm.getLinkProperties(net) else null
            if (lp != null) {
                ip = lp.linkAddresses.map { it.address }.firstOrNull { it is Inet4Address }?.hostAddress
                gw = lp.routes.firstOrNull { it.isDefaultRoute && it.gateway is Inet4Address }?.gateway?.hostAddress
                dns = lp.dnsServers.mapNotNull { it.hostAddress }
            }
        } catch (_: Exception) {
        }

        return ConnectionInfo(
            ssid = ssid,
            bssid = bssid.lowercase(),
            rssi = info.rssi,
            linkSpeed = info.linkSpeed,
            txSpeed = if (Build.VERSION.SDK_INT >= 29) info.txLinkSpeedMbps else -1,
            rxSpeed = if (Build.VERSION.SDK_INT >= 29) info.rxLinkSpeedMbps else -1,
            frequency = freq,
            channel = if (band != null) WifiMath.channelOf(freq) else 0,
            band = band,
            standard = if (Build.VERSION.SDK_INT >= 30) WifiMath.standardLabel(info.wifiStandard, band) else "No informado",
            ip = ip,
            gateway = gw,
            dns = dns
        )
    }

    private fun cleanSsid(s: String): String {
        val t = s.removeSurrounding("\"")
        if (t == WifiManager.UNKNOWN_SSID) return ""
        // Algunas redes ocultas se anuncian con bytes nulos.
        return if (t.all { it == '\u0000' }) "" else t
    }

    companion object {
        private const val WINDOW_MS = 120_000L
        private const val MAX_SCANS = 4
        private const val SPACING_MS = 30_000L
        private const val HISTORY_MS = 10 * 60_000L
    }
}
