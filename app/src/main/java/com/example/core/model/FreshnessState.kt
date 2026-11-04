package com.example.core.model

enum class FreshnessState {
    ACTUAL,       // <= delayedThresholdMs (default 5 min)
    RETRASADA,    // delayedThresholdMs .. obsoleteThresholdMs (default 5-10 min)
    OBSOLETA,     // > obsoleteThresholdMs (default > 10 min, max 15 min)
    SIN_DATOS     // never had a reading
}

enum class ActiveSourceType(val displayName: String) {
    LIBRELINKUP("LibreLinkUp (Seguidor)"),
    NIGHTSCOUT("Nightscout"),
    JUGGLUCO("Servidor local (Juggluco, xDrip+ u otro GlucoseFrame)"),
    LOCAL_BROADCAST("Apps locales (xDrip+, Juggluco, Dexcom BYODA)"),
    SIMULATED("Simulado (Debug)")
}

enum class OrientationOption(val displayName: String) {
    AUTO("Automática"),
    LANDSCAPE("Horizontal"),
    PORTRAIT("Vertical")
}

enum class AppThemeMode(val displayName: String) {
    DARK("Oscuro"),
    AMOLED("Negro AMOLED"),
    LIGHT("Claro"),
    MINIMALIST("Minimalista"),
    NIGHT("Nocturno")
}

enum class AccessGesture(val displayName: String) {
    LONG_PRESS_2S("Pulsación larga (2 s)"),
    TRIPLE_TAP_CORNER("Triple toque en esquina")
}

data class ThresholdSettings(
    val urgentLowMgDl: Int = 54,
    val lowMgDl: Int = 70,
    val targetReferenceMgDl: Int = 100,
    val highMgDl: Int = 180,
    val veryHighMgDl: Int = 250,
    val nearLowMgDl: Int = 90,   // [lowMgDl, nearLowMgDl) = "Casi bajo"
    val elevatedMgDl: Int = 140, // [elevatedMgDl, highMgDl] = "Algo alto"
    val delayedMinutes: Int = 5,
    val obsoleteMinutes: Int = 10 // Max 15
) {
    fun zoneFor(valueMgDl: Int): GlucoseZone = when {
        valueMgDl < urgentLowMgDl -> GlucoseZone.URGENT_LOW
        valueMgDl < lowMgDl -> GlucoseZone.LOW
        valueMgDl < nearLowMgDl -> GlucoseZone.NEAR_LOW
        valueMgDl < elevatedMgDl -> GlucoseZone.IN_RANGE
        valueMgDl <= highMgDl -> GlucoseZone.ELEVATED
        valueMgDl <= veryHighMgDl -> GlucoseZone.HIGH
        else -> GlucoseZone.VERY_HIGH
    }
}
