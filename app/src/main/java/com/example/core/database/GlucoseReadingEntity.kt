package com.example.core.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.core.model.GlucoseReading
import com.example.core.model.TrendArrow

@Entity(
    tableName = "glucose_reading",
    indices = [
        Index(value = ["sourceId", "measuredAtUtcMs"], unique = true),
        Index(value = ["measuredAtUtcMs"])
    ]
)
data class GlucoseReadingEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sourceId: String,
    val measuredAtUtcMs: Long,
    val valueMgDl: Int,
    val trend: String,
    val isHigh: Boolean,
    val isLow: Boolean,
    val receivedAtUtcMs: Long
) {
    fun toDomain(): GlucoseReading = GlucoseReading(
        id = id,
        sourceId = sourceId,
        measuredAtUtcMs = measuredAtUtcMs,
        valueMgDl = valueMgDl,
        trend = try { TrendArrow.valueOf(trend) } catch (e: Exception) { TrendArrow.UNKNOWN },
        isHigh = isHigh,
        isLow = isLow,
        receivedAtUtcMs = receivedAtUtcMs
    )

    companion object {
        fun fromDomain(domain: GlucoseReading): GlucoseReadingEntity = GlucoseReadingEntity(
            id = domain.id,
            sourceId = domain.sourceId,
            measuredAtUtcMs = domain.measuredAtUtcMs,
            valueMgDl = domain.valueMgDl,
            trend = domain.trend.name,
            isHigh = domain.isHigh,
            isLow = domain.isLow,
            receivedAtUtcMs = domain.receivedAtUtcMs
        )
    }
}
