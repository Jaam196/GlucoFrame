package com.example.core.database.background

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "background_images",
    indices = [
        Index(value = ["uriString"], unique = true),
        Index(value = ["sourceType"]),
        Index(value = ["category"]),
        Index(value = ["primaryClassification"])
    ]
)
data class BackgroundImageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val uriString: String,
    val sourceType: String, // "PRESET", "CUSTOM"
    val category: String,   // "abstractos", "paisajes", "naturaleza", "tecnologia", "anime", "minimalistas", "custom"
    val displayName: String,
    val lastModified: Long,
    val fileSize: Long,
    val isFavorite: Boolean = false,
    val isDisabled: Boolean = false,
    val primaryHue: Float = 0f,
    val primaryHueQuota: Float = 0f,
    val secondaryHue: Float = 0f,
    val secondaryHueQuota: Float = 0f,
    val neutralQuota: Float = 0f,
    val averageBrightness: Float = 0f,
    val primaryClassification: String = "neutro", // "rojo", "naranja", "amarillo", "verde", "cian", "azul", "morado", "rosa", "neutro"
    val secondaryClassification: String = "",
    val histogramCsv: String = "", // 36 float values formatted as CSV
    val analyzedAtUtcMs: Long = 0L
) {
    fun parseHistogram(): FloatArray {
        if (histogramCsv.isBlank()) return FloatArray(36)
        val parts = histogramCsv.split(",")
        val result = FloatArray(36)
        for (i in 0 until minOf(parts.size, 36)) {
            result[i] = parts[i].trim().toFloatOrNull() ?: 0f
        }
        return result
    }
}
