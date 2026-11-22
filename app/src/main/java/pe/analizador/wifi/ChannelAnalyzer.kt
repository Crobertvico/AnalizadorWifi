package pe.analizador.wifi

import kotlin.math.log10
import kotlin.math.pow

data class ChannelScore(
    val label: String,
    val primary: Int,
    val lowFreq: Int,
    val highFreq: Int,
    val score: Double,          // 0..10
    val interferenceDbm: Double,
    val apCount: Int,
    val isDfs: Boolean,
    val preferred: Boolean,
    val recommended: Boolean = false
) {
    fun contains(freq: Int): Boolean = freq in lowFreq..highFreq
}

object ChannelAnalyzer {

    fun widths(band: Band): List<Int> =
        if (band == Band.GHZ_2_4) listOf(20, 40) else listOf(20, 40, 80, 160)

    private fun blocks(band: Band, width: Int, allow1213: Boolean): List<List<Int>> = when (band) {
        Band.GHZ_2_4 -> {
            val max = if (allow1213) 13 else 11
            if (width == 20) (1..max).map { listOf(it) }
            else (1..(max - 4)).map { listOf(it, it + 4) }
        }
        Band.GHZ_5 -> {
            val n = width / 20
            val starts = when (width) {
                20 -> WifiMath.channels5
                40 -> listOf(36, 44, 52, 60, 100, 108, 116, 124, 132, 140, 149, 157)
                80 -> listOf(36, 52, 100, 116, 132, 149)
                else -> listOf(36, 100)
            }
            starts.map { s -> (0 until n).map { s + it * 4 } }
        }
        Band.GHZ_6 -> {
            val n = width / 20
            (1..233 step 4 * n).map { s -> (0 until n).map { s + it * 4 } }
                .filter { it.last() <= 233 }
        }
    }

    /**
     * Evalúa la interferencia de cada canal/bloque sumando la potencia (en mW) de las
     * redes vecinas que se superponen, ponderada por el porcentaje de superposición.
     * En 2.4 GHz la superposición parcial pesa más que la co-canal, porque los equipos
     * no se coordinan entre sí (es el peor caso real).
     * Las redes del propio router se excluyen.
     */
    fun evaluate(
        all: List<WifiNetwork>,
        band: Band,
        width: Int,
        exclude: Set<String>,
        allow1213: Boolean
    ): List<ChannelScore> {
        val nets = all.filter { it.band == band && it.bssid !in exclude }
        val result = blocks(band, width, allow1213).map { chans ->
            val low = WifiMath.freqOf(chans.first(), band) - 10
            val high = WifiMath.freqOf(chans.last(), band) + 10
            var mw = 0.0
            var count = 0
            for (n in nets) {
                val ov = minOf(high, n.highFreq) - maxOf(low, n.lowFreq)
                if (ov <= 0) continue
                val frac = (ov.toDouble() / minOf(high - low, n.widthMhz).toDouble()).coerceAtMost(1.0)
                val aligned = (n.lowFreq >= low && n.highFreq <= high) ||
                    (low >= n.lowFreq && high <= n.highFreq)
                val factor = if (!aligned && band == Band.GHZ_2_4) 1.5 else 1.0
                mw += 10.0.pow(n.rssi / 10.0) * frac * factor
                count++
            }
            val dbm = if (mw > 0) 10 * log10(mw) else -100.0
            val score = ((-45.0 - dbm) / 45.0 * 10.0).coerceIn(0.0, 10.0)
            val first = chans.first()
            val preferred = when (band) {
                Band.GHZ_2_4 -> width == 20 && (first == 1 || first == 6 || first == 11 || (allow1213 && first == 13))
                else -> true
            }
            ChannelScore(
                label = if (chans.size == 1) "$first" else "$first–${chans.last()}",
                primary = first,
                lowFreq = low,
                highFreq = high,
                score = score,
                interferenceDbm = dbm,
                apCount = count,
                isDfs = band == Band.GHZ_5 && chans.any { it in 52..144 },
                preferred = preferred
            )
        }
        val pool = result.filter { it.preferred }.ifEmpty { result }
        val bestScore = pool.maxOfOrNull { it.score } ?: return result
        // Ante empate técnico (±1 punto) se prefiere un canal no-DFS y con menos redes.
        val best = pool.filter { it.score >= bestScore - 1.0 }
            .sortedWith(compareBy<ChannelScore>({ it.isDfs }, { it.apCount }, { -it.score }))
            .firstOrNull()
        return result.map { it.copy(recommended = it == best) }
    }
}
