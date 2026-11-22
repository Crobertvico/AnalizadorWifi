@file:OptIn(ExperimentalMaterial3Api::class)

package pe.analizador.wifi.ui

import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pe.analizador.wifi.Band
import pe.analizador.wifi.Sample
import pe.analizador.wifi.UiState
import pe.analizador.wifi.WifiMath
import pe.analizador.wifi.WifiNetwork

private class FreqMapper(private val segs: List<Pair<Int, Int>>, private val left: Float, private val right: Float) {
    private val total = segs.sumOf { it.second - it.first }.toFloat()
    fun x(f: Int): Float {
        var acc = 0f
        for ((s, e) in segs) {
            if (f <= e) {
                val within = (f - s).coerceAtLeast(0)
                return left + (acc + within) / total * (right - left)
            }
            acc += (e - s)
        }
        return right
    }
}

private fun DrawScope.yOf(dbm: Int, top: Float, bottom: Float): Float =
    bottom - ((dbm + 100).coerceIn(0, 80) / 80f) * (bottom - top)

private fun bandCounts(state: UiState): Map<Band, Int> =
    Band.entries.associateWith { b -> state.networks.count { it.band == b } }

// =====================================================================  Gráfico de canales

@Composable
fun ChannelGraphScreen(state: UiState, band: Band, onBand: (Band) -> Unit) {
    val nets = state.networks.filter { it.band == band && (state.settings.showHidden || !it.isHidden) && it.rssi >= state.settings.minRssi }
    Column(Modifier.fillMaxSize()) {
        BandSelector(band, bandCounts(state), onBand)
        val factor = when (band) {
            Band.GHZ_2_4 -> 1f
            Band.GHZ_5 -> 2.4f
            Band.GHZ_6 -> 5f
        }
        BoxWithConstraints(
            Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            val chartWidth = maxWidth * factor
            Box(Modifier.horizontalScroll(rememberScrollState())) {
                ChannelChart(
                    nets, band,
                    Modifier
                        .width(chartWidth)
                        .fillMaxHeight()
                        .padding(4.dp)
                )
            }
        }
        Text(
            (if (factor > 1f) "Desliza horizontalmente para ver toda la banda. " else "") +
                "★ = tu red. Las redes del mismo color son el mismo equipo de acceso.",
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun ChannelChart(nets: List<WifiNetwork>, band: Band, modifier: Modifier) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val gridColor = onSurface.copy(alpha = 0.12f)
    val textArgb = onSurface.copy(alpha = 0.7f).toArgb()
    val dfsColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.08f)

    Canvas(modifier) {
        val chartL = 34.dp.toPx()
        val chartR = size.width - 8.dp.toPx()
        val chartT = 8.dp.toPx()
        val chartB = size.height - 24.dp.toPx()
        val mapper = FreqMapper(WifiMath.segments(band), chartL, chartR)
        val native = drawContext.canvas.nativeCanvas

        val axisPaint = Paint().apply {
            isAntiAlias = true
            textSize = 10.sp.toPx()
            color = textArgb
        }

        // Rejilla horizontal (dBm)
        for (dbm in -90..-30 step 10) {
            val yy = yOf(dbm, chartT, chartB)
            drawLine(gridColor, Offset(chartL, yy), Offset(chartR, yy))
            axisPaint.textAlign = Paint.Align.LEFT
            native.drawText("$dbm", 2.dp.toPx(), yy + 4.dp.toPx(), axisPaint)
        }

        // Zona DFS y separación en 5 GHz
        if (band == Band.GHZ_5) {
            val x1 = mapper.x(5250)
            val x2 = mapper.x(5730)
            drawRect(dfsColor, topLeft = Offset(x1, chartT), size = Size(x2 - x1, chartB - chartT))
            axisPaint.textAlign = Paint.Align.LEFT
            native.drawText("Zona DFS (radar)", x1 + 4.dp.toPx(), chartT + 12.dp.toPx(), axisPaint)
            val gx = mapper.x(5480)
            drawLine(
                gridColor.copy(alpha = 0.5f), Offset(gx, chartT), Offset(gx, chartB),
                strokeWidth = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))
            )
        }

        // Canales en el eje X
        axisPaint.textAlign = Paint.Align.CENTER
        for (ch in WifiMath.axisChannels(band)) {
            val fx = mapper.x(WifiMath.freqOf(ch, band))
            drawLine(gridColor, Offset(fx, chartT), Offset(fx, chartB))
            native.drawText("$ch", fx, size.height - 8.dp.toPx(), axisPaint)
        }
        drawLine(onSurface.copy(alpha = 0.4f), Offset(chartL, chartB), Offset(chartR, chartB), strokeWidth = 2f)

        // Curvas: primero las débiles para que las fuertes queden encima
        clipRect(left = chartL, top = 0f, right = chartR, bottom = chartB) {
            for (n in nets.sortedBy { it.rssi }) {
                val color = colorFor(n.bssid)
                val left = mapper.x(n.lowFreq)
                val right = mapper.x(n.highFreq)
                val top = yOf(n.rssi, chartT, chartB)
                val ctrl = (4 * top - chartB) / 3f
                val path = Path().apply {
                    moveTo(left, chartB)
                    cubicTo(left, ctrl, right, ctrl, right, chartB)
                }
                drawPath(path, color.copy(alpha = if (n.isConnected) 0.30f else 0.15f))
                drawPath(path, color, style = Stroke(width = if (n.isConnected) 3.5.dp.toPx() else 1.6.dp.toPx()))
            }
        }

        // Etiquetas sin superposición (corrige textos encimados)
        val labelPaint = Paint().apply {
            isAntiAlias = true
            textSize = 11.sp.toPx()
            textAlign = Paint.Align.CENTER
        }
        val placed = ArrayList<RectF>()
        val lineH = labelPaint.textSize + 3f
        for (n in nets.sortedByDescending { it.rssi }) {
            val cx = mapper.x(n.centerFrequency)
            val baseY = yOf(n.rssi, chartT, chartB) - 4.dp.toPx()
            val text = (if (n.isConnected) "★ " else "") + n.displaySsid + " (" + n.rssi + ")"
            labelPaint.color = colorFor(n.bssid).toArgb()
            labelPaint.isFakeBoldText = n.isConnected
            val w = labelPaint.measureText(text)
            val rect = RectF(cx - w / 2, baseY - labelPaint.textSize, cx + w / 2, baseY + 2f)
            var tries = 0
            while (placed.any { RectF.intersects(it, rect) } && tries < 8) {
                rect.offset(0f, -lineH)
                tries++
            }
            if (placed.any { RectF.intersects(it, rect) } || rect.top < 0f) continue
            placed += RectF(rect)
            native.drawText(text, cx, rect.bottom - 2f, labelPaint)
        }
    }
}

// =====================================================================  Gráfico de tiempo

@Composable
fun TimeGraphScreen(state: UiState, band: Band, onBand: (Band) -> Unit) {
    val nets = state.networks
        .filter { it.band == band && (state.settings.showHidden || !it.isHidden) }
        .sortedByDescending { it.rssi }
        .take(12)
    var hidden by remember { mutableStateOf(setOf<String>()) }
    val now = rememberNow()
    var windowMin by remember { mutableStateOf(5) }

    Column(Modifier.fillMaxSize()) {
        BandSelector(band, bandCounts(state), onBand)
        Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(2, 5, 10).forEach { m ->
                androidx.compose.material3.FilterChip(
                    selected = windowMin == m,
                    onClick = { windowMin = m },
                    label = { Text("$m min") }
                )
            }
        }
        TimeChart(
            nets.filter { it.bssid !in hidden }, state.history, now, windowMin * 60_000L,
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(4.dp)
        )
        Text(
            "Toca una red para ocultarla o mostrarla. Tu red conectada se mide cada segundo.",
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 12.dp)
        )
        LazyColumn(
            Modifier
                .fillMaxWidth()
                .weight(0.6f)
        ) {
            items(nets, key = { it.bssid }) { n ->
                val off = n.bssid in hidden
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { hidden = if (off) hidden - n.bssid else hidden + n.bssid }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier
                            .size(12.dp)
                            .background(colorFor(n.bssid).copy(alpha = if (off) 0.2f else 1f), CircleShape)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        (if (n.isConnected) "★ " else "") + n.displaySsid,
                        modifier = Modifier.weight(1f),
                        fontWeight = if (n.isConnected) FontWeight.Bold else FontWeight.Normal,
                        color = if (off) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f) else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                    Text("Ch ${n.channel}  ", style = MaterialTheme.typography.bodySmall)
                    Text("${n.rssi} dBm", color = signalColor(n.rssi), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun TimeChart(
    nets: List<WifiNetwork>,
    history: Map<String, List<Sample>>,
    now: Long,
    windowMs: Long,
    modifier: Modifier
) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val gridColor = onSurface.copy(alpha = 0.12f)
    val textArgb = onSurface.copy(alpha = 0.7f).toArgb()

    Canvas(modifier) {
        val chartL = 34.dp.toPx()
        val chartR = size.width - 8.dp.toPx()
        val chartT = 8.dp.toPx()
        val chartB = size.height - 22.dp.toPx()
        val native = drawContext.canvas.nativeCanvas
        val paint = Paint().apply {
            isAntiAlias = true
            textSize = 10.sp.toPx()
            color = textArgb
        }
        fun xOf(t: Long): Float = chartL + (1f - (now - t).toFloat() / windowMs) * (chartR - chartL)

        for (dbm in -90..-30 step 10) {
            val yy = yOf(dbm, chartT, chartB)
            drawLine(gridColor, Offset(chartL, yy), Offset(chartR, yy))
            paint.textAlign = Paint.Align.LEFT
            native.drawText("$dbm", 2.dp.toPx(), yy + 4.dp.toPx(), paint)
        }
        val minutes = (windowMs / 60_000L).toInt()
        paint.textAlign = Paint.Align.CENTER
        for (m in 0..minutes) {
            val xx = xOf(now - m * 60_000L)
            drawLine(gridColor, Offset(xx, chartT), Offset(xx, chartB))
            native.drawText(if (m == 0) "ahora" else "-$m min", xx.coerceIn(chartL + 14.dp.toPx(), chartR - 14.dp.toPx()), size.height - 6.dp.toPx(), paint)
        }

        clipRect(left = chartL, top = 0f, right = chartR, bottom = chartB) {
            for (n in nets) {
                val pts = history[n.bssid]?.filter { it.timeMs >= now - windowMs } ?: continue
                if (pts.isEmpty()) continue
                val color = colorFor(n.bssid)
                val path = Path()
                var prevT = 0L
                pts.forEachIndexed { i, p ->
                    val x = xOf(p.timeMs)
                    val y = yOf(p.rssi, chartT, chartB)
                    if (i == 0 || p.timeMs - prevT > 45_000L) path.moveTo(x, y) else path.lineTo(x, y)
                    prevT = p.timeMs
                }
                drawPath(path, color, style = Stroke(width = if (n.isConnected) 3.dp.toPx() else 1.8.dp.toPx()))
                val last = pts.last()
                drawCircle(color, radius = 3.dp.toPx(), center = Offset(xOf(last.timeMs), yOf(last.rssi, chartT, chartB)))
            }
        }
    }
}
