package com.example.core.designsystem

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import com.example.core.model.AppThemeMode
import com.example.core.model.BackgroundPreferences
import com.example.core.model.GlucoseZone
import com.example.core.model.ZoneDefaults

object GlucoseColors {
    // Pure-Kotlin parser (no android.graphics) so it also works in plain JVM unit tests
    private fun parseHex(hex: String, fallback: Color): Color {
        val digits = hex.trim().removePrefix("#")
        val value = digits.toLongOrNull(16) ?: return fallback
        return when (digits.length) {
            6 -> Color((0xFF000000L or value).toInt())
            8 -> Color(value.toInt())
            else -> fallback
        }
    }

    private fun defaultPalette(): Map<GlucoseZone, Color> =
        ZoneDefaults.HEX.mapValues { (_, hex) -> parseHex(hex, Color.Gray) }

    // Palette chosen by the user (Compose snapshot state, so every screen recomposes when it changes).
    // Used by the glucose number, chart, widgets and notification.
    private var palette: Map<GlucoseZone, Color> by mutableStateOf(defaultPalette())

    /** Applies the colors saved in the user's preferences. */
    fun applyPalette(prefs: BackgroundPreferences) {
        val defaults = defaultPalette()
        palette = GlucoseZone.entries.associateWith { zone ->
            parseHex(prefs.colorHexForZone(zone), defaults.getValue(zone))
        }
    }

    fun colorForZone(zone: GlucoseZone): Color = palette[zone] ?: Color.Gray

    fun argbForZone(zone: GlucoseZone): Int = colorForZone(zone).toArgb()

    /** True when text drawn over this zone's color must be dark to stay readable. */
    fun prefersDarkText(zone: GlucoseZone): Boolean = colorForZone(zone).luminance() > 0.30f

    // Named accessors kept for existing call sites
    val UrgentLow: Color get() = colorForZone(GlucoseZone.URGENT_LOW)
    val Low: Color get() = colorForZone(GlucoseZone.LOW)
    val NearLow: Color get() = colorForZone(GlucoseZone.NEAR_LOW)
    val InRange: Color get() = colorForZone(GlucoseZone.IN_RANGE)
    val Elevated: Color get() = colorForZone(GlucoseZone.ELEVATED)
    val High: Color get() = colorForZone(GlucoseZone.HIGH)
    val VeryHigh: Color get() = colorForZone(GlucoseZone.VERY_HIGH)
    val ObsoleteGray = Color(0xFF757575)
    val StatusAmber = Color(0xFFFFB300)

    // Themes Color Schemes
    val DarkColorScheme = darkColorScheme(
        primary = Color(0xFF00E5FF),
        onPrimary = Color(0xFF00363D),
        primaryContainer = Color(0xFF004F58),
        onPrimaryContainer = Color(0xFF97F0FF),
        secondary = Color(0xFF4DD0E1),
        background = Color(0xFF0B1320),
        surface = Color(0xFF131F33),
        surfaceVariant = Color(0xFF1A2A44),
        onBackground = Color(0xFFF1F5F9),
        onSurface = Color(0xFFF1F5F9),
        onSurfaceVariant = Color(0xFF94A3B8)
    )

    val AmoledColorScheme = darkColorScheme(
        primary = Color(0xFF00E5FF),
        onPrimary = Color.Black,
        primaryContainer = Color(0xFF1A1A1A),
        onPrimaryContainer = Color(0xFF97F0FF),
        secondary = Color(0xFF80DEEA),
        background = Color(0xFF000000),
        surface = Color(0xFF0A0A0A),
        surfaceVariant = Color(0xFF141414),
        onBackground = Color(0xFFFFFFFF),
        onSurface = Color(0xFFFFFFFF),
        onSurfaceVariant = Color(0xFFAAAAAA)
    )

    val LightColorScheme = lightColorScheme(
        primary = Color(0xFF006877),
        onPrimary = Color.White,
        primaryContainer = Color(0xFF97F0FF),
        onPrimaryContainer = Color(0xFF001F25),
        secondary = Color(0xFF4A6267),
        background = Color(0xFFF8FAFC),
        surface = Color(0xFFFFFFFF),
        surfaceVariant = Color(0xFFE2E8F0),
        onBackground = Color(0xFF0F172A),
        onSurface = Color(0xFF0F172A),
        onSurfaceVariant = Color(0xFF475569)
    )

    val MinimalistColorScheme = darkColorScheme(
        primary = Color(0xFFE2E8F0),
        onPrimary = Color.Black,
        primaryContainer = Color(0xFF1E293B),
        onPrimaryContainer = Color(0xFFF8FAFC),
        secondary = Color(0xFF94A3B8),
        background = Color(0xFF0F172A),
        surface = Color(0xFF1E293B),
        surfaceVariant = Color(0xFF334155),
        onBackground = Color(0xFFF8FAFC),
        onSurface = Color(0xFFF8FAFC),
        onSurfaceVariant = Color(0xFFCBD5E1)
    )

    val NightColorScheme = darkColorScheme(
        primary = Color(0xFFFF8A65),
        onPrimary = Color(0xFF3E1100),
        primaryContainer = Color(0xFF4E1602),
        onPrimaryContainer = Color(0xFFFFCCBC),
        secondary = Color(0xFFFFAB91),
        background = Color(0xFF140803),
        surface = Color(0xFF1F0E06),
        surfaceVariant = Color(0xFF2E150A),
        onBackground = Color(0xFFFFDBCF),
        onSurface = Color(0xFFFFDBCF),
        onSurfaceVariant = Color(0xFFD7A797)
    )

    fun schemeForMode(mode: AppThemeMode): ColorScheme = when (mode) {
        AppThemeMode.DARK -> DarkColorScheme
        AppThemeMode.AMOLED -> AmoledColorScheme
        AppThemeMode.LIGHT -> LightColorScheme
        AppThemeMode.MINIMALIST -> MinimalistColorScheme
        AppThemeMode.NIGHT -> NightColorScheme
    }
}
