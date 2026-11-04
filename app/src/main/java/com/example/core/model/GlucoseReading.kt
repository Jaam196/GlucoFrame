package com.example.core.model

enum class TrendArrow(val symbol: String, val descriptionEs: String) {
    FALLING_FAST("↓↓", "Baja rápido"),
    FALLING("↓", "Baja"),
    FLAT("→", "Estable"),
    RISING("↑", "Sube"),
    RISING_FAST("↑↑", "Sube rápido"),
    UNKNOWN("—", "Sin tendencia");

    companion object {
        fun fromLibre(trendValue: Int): TrendArrow = when (trendValue) {
            1 -> FALLING_FAST
            2 -> FALLING
            3 -> FLAT
            4 -> RISING
            5 -> RISING_FAST
            else -> UNKNOWN
        }

        fun fromNightscout(direction: String?): TrendArrow = when (direction?.lowercase()?.trim()) {
            "doubledown", "singledown" -> FALLING_FAST
            "fortyfivedown" -> FALLING
            "flat" -> FLAT
            "fortyfiveup" -> RISING
            "singleup", "doubleup" -> RISING_FAST
            else -> UNKNOWN
        }
    }
}

enum class GlucoseUnit(val label: String) {
    MG_DL("mg/dL"),
    MMOL_L("mmol/L")
}

enum class GlucoseZone(val labelEs: String) {
    URGENT_LOW("Bajo urgente"),
    LOW("Bajo"),
    NEAR_LOW("Casi bajo"),
    IN_RANGE("En rango"),
    ELEVATED("Algo alto"),
    HIGH("Alto"),
    VERY_HIGH("Muy alto")
}

data class GlucoseReading(
    val id: Long = 0,
    val sourceId: String,
    val measuredAtUtcMs: Long,
    val valueMgDl: Int,
    val trend: TrendArrow,
    val isHigh: Boolean = false,
    val isLow: Boolean = false,
    val receivedAtUtcMs: Long = System.currentTimeMillis()
)
