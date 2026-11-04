package com.example.core.designsystem

import android.graphics.Typeface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontFamily
import com.example.core.model.DisplayStyle
import com.example.core.model.NumberFont

/** Gives every screen the style chosen by the user (provided once in MainActivity). */
val LocalDisplayStyle = compositionLocalOf { DisplayStyle() }

object StyleColors {
    /** Accepts "#RRGGBB" or "#AARRGGBB"; returns [fallback] if the text is not a valid colour. */
    fun parse(hex: String, fallback: Color): Color {
        val digits = hex.trim().removePrefix("#")
        val value = digits.toLongOrNull(16) ?: return fallback
        return when (digits.length) {
            6 -> Color((0xFF000000L or value).toInt())
            8 -> Color(value.toInt())
            else -> fallback
        }
    }

    fun parseArgb(hex: String, fallbackArgb: Int): Int = parse(hex, Color(fallbackArgb)).toArgb()
}

fun NumberFont.toFontFamily(): FontFamily =
    FontFamily(Typeface.create(systemFamily, if (bold) Typeface.BOLD else Typeface.NORMAL))

@Composable
fun NumberFont.rememberFontFamily(): FontFamily = remember(this) { this.toFontFamily() }
