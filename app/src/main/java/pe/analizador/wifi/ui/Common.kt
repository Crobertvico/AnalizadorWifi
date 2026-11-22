@file:OptIn(ExperimentalMaterial3Api::class)

package pe.analizador.wifi.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import pe.analizador.wifi.Band

@Composable
fun AppTheme(mode: Int, content: @Composable () -> Unit) {
    val dark = when (mode) {
        1 -> false
        2 -> true
        else -> isSystemInDarkTheme()
    }
    val ctx = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> darkColorScheme(primary = Color(0xFF82B1FF), secondary = Color(0xFF64FFDA))
        else -> lightColorScheme(primary = Color(0xFF1565C0), secondary = Color(0xFF00897B))
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

val palette = listOf(
    Color(0xFF42A5F5), Color(0xFFEF5350), Color(0xFF66BB6A), Color(0xFFFFA726),
    Color(0xFFAB47BC), Color(0xFF26C6DA), Color(0xFFEC407A), Color(0xFF9CCC65),
    Color(0xFF7E57C2), Color(0xFFFFCA28), Color(0xFF26A69A), Color(0xFF8D6E63)
)

fun colorFor(bssid: String): Color = palette[(bssid.hashCode() and 0x7fffffff) % palette.size]

fun signalColor(rssi: Int): Color = when {
    rssi >= -60 -> Color(0xFF43A047)
    rssi >= -70 -> Color(0xFF9CCC65)
    rssi >= -80 -> Color(0xFFFB8C00)
    else -> Color(0xFFE53935)
}

@Composable
fun rememberNow(): Long {
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(1000)
            value = System.currentTimeMillis()
        }
    }
    return now
}

fun agoText(now: Long, t: Long): String {
    if (t <= 0) return "sin datos"
    val s = ((now - t) / 1000).coerceAtLeast(0)
    return when {
        s < 2 -> "ahora"
        s < 60 -> "hace $s s"
        else -> "hace ${s / 60} min"
    }
}

@Composable
fun BandSelector(selected: Band, counts: Map<Band, Int>, onSelect: (Band) -> Unit) {
    Row(
        Modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Band.entries.forEach { b ->
            FilterChip(
                selected = selected == b,
                onClick = { onSelect(b) },
                label = { Text("${b.label} (${counts[b] ?: 0})") }
            )
        }
    }
}

@Composable
fun Tag(text: String, color: Color) {
    Surface(
        color = color.copy(alpha = 0.16f),
        contentColor = color,
        shape = RoundedCornerShape(6.dp)
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun SignalBars(rssi: Int, modifier: Modifier = Modifier.size(28.dp)) {
    val active = signalColor(rssi)
    val inactive = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
    val level = when {
        rssi >= -55 -> 4
        rssi >= -67 -> 3
        rssi >= -78 -> 2
        rssi >= -90 -> 1
        else -> 0
    }
    Canvas(modifier) {
        val gap = size.width * 0.08f
        val barW = (size.width - gap * 3) / 4
        for (i in 0 until 4) {
            val h = size.height * (i + 1) / 4f
            drawRoundRect(
                color = if (i < level) active else inactive,
                topLeft = Offset(i * (barW + gap), size.height - h),
                size = Size(barW, h),
                cornerRadius = CornerRadius(2f, 2f)
            )
        }
    }
}

fun openIntent(ctx: Context, intent: Intent) {
    try {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(ctx, "No hay una app para abrir esto", Toast.LENGTH_SHORT).show()
    } catch (_: SecurityException) {
        Toast.makeText(ctx, "Android no permite abrir esta pantalla", Toast.LENGTH_SHORT).show()
    }
}

fun openUrl(ctx: Context, url: String) = openIntent(ctx, Intent(Intent.ACTION_VIEW, Uri.parse(url)))
