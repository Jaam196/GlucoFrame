package com.example.core.model

enum class BackgroundMode(val displayName: String) {
    PRESET_LIBRARY("Biblioteca predeterminada"),
    CUSTOM_FOLDER("Carpeta personalizada"),
    SMART_COLOR("Selección inteligente por colores")
}

enum class CustomFolderOrder(val displayName: String) {
    RANDOM("Aleatorio"),
    SEQUENTIAL("Secuencial")
}

/** Default colors (HEX) for every glucose level. Shared by the reading, chart, widget and backgrounds. */
object ZoneDefaults {
    val HEX: Map<GlucoseZone, String> = mapOf(
        GlucoseZone.URGENT_LOW to "#FF1744",
        GlucoseZone.LOW to "#FF9100",
        GlucoseZone.NEAR_LOW to "#FFC107",
        GlucoseZone.IN_RANGE to "#00E676",
        GlucoseZone.ELEVATED to "#C6FF00",
        GlucoseZone.HIGH to "#FFD600",
        GlucoseZone.VERY_HIGH to "#FF5252"
    )
}

data class BackgroundPreferences(
    val mode: BackgroundMode = BackgroundMode.PRESET_LIBRARY,
    val presetCategory: String = "todos",
    val customFolderUri: String? = null,
    val customFolderOrder: CustomFolderOrder = CustomFolderOrder.RANDOM,
    val customFolderSelectedAll: Boolean = true,
    val customFolderSelectedIds: Set<String> = emptySet(),
    val fixedImageUri: String? = null,
    val changeIntervalSeconds: Int = 180, // 3 min default
    val autoChangeEnabled: Boolean = false, // backgrounds only change when the user asks for it
    val crossfadeDurationSeconds: Float = 1.0f, // 0 = disabled
    val darkLayerOpacity: Float = 0.40f, // 0.0 to 0.9
    val blurRadius: Float = 0f, // 0 to 25
    val saturation: Float = 1.0f, // 0.0 to 2.0
    val brightness: Float = 1.0f, // 0.5 to 1.5
    val contrast: Float = 1.0f, // 0.5 to 1.5
    val autoReadingProtection: Boolean = true,
    // Zone Colors (HEX): used for the glucose number, chart, widget, notification and smart backgrounds
    val colorUrgentLowHex: String = ZoneDefaults.HEX.getValue(GlucoseZone.URGENT_LOW),
    val colorLowHex: String = ZoneDefaults.HEX.getValue(GlucoseZone.LOW),
    val colorNearLowHex: String = ZoneDefaults.HEX.getValue(GlucoseZone.NEAR_LOW),
    val colorInRangeHex: String = ZoneDefaults.HEX.getValue(GlucoseZone.IN_RANGE),
    val colorElevatedHex: String = ZoneDefaults.HEX.getValue(GlucoseZone.ELEVATED),
    val colorHighHex: String = ZoneDefaults.HEX.getValue(GlucoseZone.HIGH),
    val colorVeryHighHex: String = ZoneDefaults.HEX.getValue(GlucoseZone.VERY_HIGH),
    // Extras for the wallpaper
    val kenBurnsEnabled: Boolean = false,          // slow zoom on the picture
    val showPhotoName: Boolean = false,            // small caption with the picture's name
    val photoDurations: Map<String, Int> = emptyMap() // seconds each picture stays; missing = general interval
) {
    fun colorHexForZone(zone: GlucoseZone): String = when (zone) {
        GlucoseZone.URGENT_LOW -> colorUrgentLowHex
        GlucoseZone.LOW -> colorLowHex
        GlucoseZone.NEAR_LOW -> colorNearLowHex
        GlucoseZone.IN_RANGE -> colorInRangeHex
        GlucoseZone.ELEVATED -> colorElevatedHex
        GlucoseZone.HIGH -> colorHighHex
        GlucoseZone.VERY_HIGH -> colorVeryHighHex
    }

    companion object {
        val DEFAULT_PRESET_CATEGORIES = listOf(
            "todos",
            "abstractos",
            "paisajes",
            "naturaleza",
            "tecnologia",
            "anime",
            "minimalistas",
            "lugares",
            "oficina",
            "gaming",
            "hogar",
            "familia",
            "espacio"
        )
    }
}
