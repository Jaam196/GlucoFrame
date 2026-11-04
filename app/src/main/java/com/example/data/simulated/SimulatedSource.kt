package com.example.data.simulated

import com.example.BuildConfig
import com.example.core.model.GlucoseReading
import com.example.core.model.SyncResult
import com.example.core.model.TrendArrow
import com.example.data.source.GlucoseSource
import com.example.data.source.SourceError
import com.example.data.source.SourceResult
import kotlin.random.Random

class SimulatedSource : GlucoseSource {
    override val sourceId: String = "SIMULATED"
    override val displayName: String = "Simulado (Debug)"

    private var lastValue = 115
    private var lastTrend = TrendArrow.FLAT

    override suspend fun fetchCurrentAndGraph(fetchGraph: Boolean): SourceResult {
        if (!BuildConfig.DEBUG) {
            return SourceResult.Error(
                SourceError(
                    resultType = SyncResult.OTHER,
                    message = "La fuente simulada solo está permitida en modo debug",
                    pauseLoop = true
                )
            )
        }

        val now = System.currentTimeMillis()
        val delta = Random.nextInt(-6, 7)
        lastValue = (lastValue + delta).coerceIn(65, 230)
        lastTrend = when {
            delta >= 4 -> TrendArrow.RISING_FAST
            delta in 2..3 -> TrendArrow.RISING
            delta in -1..1 -> TrendArrow.FLAT
            delta in -3..-2 -> TrendArrow.FALLING
            else -> TrendArrow.FALLING_FAST
        }

        val readings = mutableListOf<GlucoseReading>()
        if (fetchGraph) {
            // Generate last 24h graph in 5-minute increments
            val count = 288
            var simVal = lastValue
            val intervalMs = 5 * 60 * 1000L
            for (i in count downTo 1) {
                val t = now - (i * intervalMs)
                simVal = (simVal + Random.nextInt(-4, 5)).coerceIn(70, 210)
                readings.add(
                    GlucoseReading(
                        sourceId = sourceId,
                        measuredAtUtcMs = t,
                        valueMgDl = simVal,
                        trend = TrendArrow.FLAT,
                        receivedAtUtcMs = now
                    )
                )
            }
        }

        readings.add(
            GlucoseReading(
                sourceId = sourceId,
                measuredAtUtcMs = now,
                valueMgDl = lastValue,
                trend = lastTrend,
                isHigh = lastValue > 180,
                isLow = lastValue < 70,
                receivedAtUtcMs = now
            )
        )

        return SourceResult.Success(readings, serverTimeMs = now)
    }

    override suspend fun testConnection(): SourceResult {
        return fetchCurrentAndGraph(fetchGraph = false)
    }
}
