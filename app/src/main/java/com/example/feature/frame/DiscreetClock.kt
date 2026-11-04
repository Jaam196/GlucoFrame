package com.example.feature.frame

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.designsystem.LocalDisplayStyle
import com.example.core.designsystem.StyleColors
import com.example.core.designsystem.rememberFontFamily
import com.example.core.model.AppThemeMode
import com.example.core.model.TextColorSource
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Clock of the reading screen. Size, font, colour, 12/24 h, seconds, date, panel and position are chosen
 * by the user in "Estilo de pantalla". Default look is small and discreet so it is never mistaken for glucose.
 */
@Composable
fun DiscreetClock(
    themeMode: AppThemeMode,
    modifier: Modifier = Modifier
) {
    val style = LocalDisplayStyle.current
    if (!style.clockVisible) return

    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(style.clockShowSeconds) {
        while (true) {
            nowMs = System.currentTimeMillis()
            delay(if (style.clockShowSeconds) 250L else 1000L)
        }
    }

    val timePattern = (if (style.clock24h) "HH:mm" else "h:mm") +
        (if (style.clockShowSeconds) ":ss" else "") +
        (if (style.clock24h) "" else " a")
    val timeFormat = remember(timePattern) { SimpleDateFormat(timePattern, Locale.getDefault()) }
    val dateFormat = remember { SimpleDateFormat("EEE d MMM", Locale.getDefault()) }
    val fontFamily = style.clockFont.rememberFontFamily()

    val isNight = themeMode == AppThemeMode.NIGHT
    val baseColor = if (style.clockColorSource == TextColorSource.CUSTOM) {
        StyleColors.parse(style.clockCustomHex, MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val fontColor = baseColor.copy(
        alpha = (style.clockOpacity * (if (isNight) 0.6f else 1f)).coerceIn(0.1f, 1f)
    )
    val fontSize = (if (isNight) style.clockSizeSp * 0.85f else style.clockSizeSp).sp

    val boxModifier = if (style.clockPanel) {
        modifier
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    } else {
        modifier.padding(horizontal = 4.dp, vertical = 2.dp)
    }

    Box(modifier = boxModifier) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = timeFormat.format(Date(nowMs)),
                color = fontColor,
                fontSize = fontSize,
                fontFamily = fontFamily,
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.sp,
                maxLines = 1,
                softWrap = false
            )
            if (style.clockShowDate) {
                Text(
                    text = dateFormat.format(Date(nowMs)),
                    color = fontColor,
                    fontSize = (style.clockSizeSp * 0.5f).coerceAtLeast(10f).sp,
                    fontFamily = fontFamily,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}
