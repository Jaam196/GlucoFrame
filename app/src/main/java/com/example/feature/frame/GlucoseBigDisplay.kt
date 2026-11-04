package com.example.feature.frame

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardDoubleArrowDown
import androidx.compose.material.icons.filled.KeyboardDoubleArrowUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.common.GlucoseUtils
import com.example.core.designsystem.GlucoseColors
import com.example.core.designsystem.LocalDisplayStyle
import com.example.core.designsystem.StyleColors
import com.example.core.designsystem.rememberFontFamily
import com.example.core.model.ColorSource
import com.example.core.model.NightNumberMode
import com.example.core.model.NumberEffect
import com.example.core.model.FreshnessState
import com.example.core.model.GlucoseReading
import com.example.core.model.GlucoseUnit
import com.example.core.model.GlucoseZone
import com.example.core.model.ThresholdSettings

@Composable
fun GlucoseBigDisplay(
    reading: GlucoseReading?,
    previousReading: GlucoseReading?,
    freshness: FreshnessState,
    nowMs: Long,
    thresholds: ThresholdSettings,
    unit: GlucoseUnit,
    modifier: Modifier = Modifier,
    isNight: Boolean = false
) {
    val style = LocalDisplayStyle.current
    val numberFontFamily = style.numberFont.rememberFontFamily()

    BoxWithConstraints(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        val availableHeight = maxHeight
        val availableWidth = maxWidth

        when (freshness) {
            FreshnessState.SIN_DATOS, FreshnessState.OBSOLETA -> {
                // OBSOLETE or NO DATA state:
                // Rule 3 & Section 5: The big number disappears!
                // Displays "SIN DATOS ACTUALES" and the last reading appears small in gray with time and age, without arrow.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.padding(bottom = 12.dp)
                    ) {
                        Text(
                            text = if (freshness == FreshnessState.SIN_DATOS) "SIN DATOS" else "SIN DATOS ACTUALES",
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                        )
                    }

                    if (reading != null) {
                        Text(
                            text = "Última lectura: ${GlucoseUtils.formatValue(reading.valueMgDl, unit)} ${unit.label}",
                            color = GlucoseColors.ObsoleteGray,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${GlucoseUtils.formatTime(reading.measuredAtUtcMs)} (${GlucoseUtils.formatAge(reading.measuredAtUtcMs, nowMs)})",
                            color = GlucoseColors.ObsoleteGray.copy(alpha = 0.8f),
                            fontSize = 14.sp
                        )
                    } else {
                        Text(
                            text = "Esperando la primera lectura de la fuente activa...",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            FreshnessState.ACTUAL, FreshnessState.RETRASADA -> {
                if (reading == null) return@BoxWithConstraints

                val zone = thresholds.zoneFor(reading.valueMgDl)
                val zoneColor = GlucoseColors.colorForZone(zone)
                val isDelayed = freshness == FreshnessState.RETRASADA
                val numberAlpha = if (isDelayed) 0.55f else 1.0f

                // Colour of the number: by glucose level (default) or one colour chosen by the user,
                // and a separate treatment at night (same / dimmed / own colour).
                val dayColor = if (style.numberColorSource == ColorSource.CUSTOM) {
                    StyleColors.parse(style.numberCustomHex, zoneColor)
                } else {
                    zoneColor
                }
                val numberColor = if (isNight) {
                    when (style.nightNumberMode) {
                        NightNumberMode.SAME -> dayColor
                        NightNumberMode.DIMMED -> dayColor.copy(alpha = style.nightDimLevel.coerceIn(0.1f, 1f))
                        NightNumberMode.CUSTOM -> StyleColors.parse(style.nightCustomHex, dayColor)
                    }
                } else {
                    dayColor
                }

                // Font size fits BOTH the available height and width (so 3-digit values never wrap or
                // get clipped on narrow phones). Computed in dp so the system font scale cannot overflow it.
                val valueText = GlucoseUtils.formatValue(reading.valueMgDl, unit)
                val effectiveChars = valueText.count { it.isDigit() } + 0.4f * valueText.count { !it.isDigit() }
                val widthLimitedDp = (availableWidth.value - 120f) / (0.64f * effectiveChars + 0.55f)
                val heightLimitedDp = availableHeight.value * 0.45f
                val baseDp = minOf(heightLimitedDp, widthLimitedDp).coerceIn(44f, 150f)
                // The user's size factor can grow the number only as far as it still fits the screen
                val maxDp = maxOf(baseDp, minOf(widthLimitedDp, availableHeight.value * 0.7f))
                val fontDp = (baseDp * style.numberSizeScale).coerceIn(24f, maxDp)
                val infoScale = style.infoTextScale
                val density = LocalDensity.current
                val dynamicFontSize = with(density) { fontDp.dp.toSp() }
                val arrowFontSize = with(density) { (fontDp * 0.42f).dp.toSp() }
                val unitFontSize = with(density) { ((fontDp * 0.22f).coerceAtLeast(14f) * infoScale).dp.toSp() }

                val numberShadow: Shadow? = when (style.numberEffect) {
                    NumberEffect.NONE -> null
                    NumberEffect.SHADOW -> Shadow(
                        color = Color.Black.copy(alpha = 0.35f),
                        offset = Offset(2f, 2f),
                        blurRadius = 4f
                    )
                    NumberEffect.GLOW -> Shadow(
                        color = numberColor.copy(alpha = 0.85f),
                        offset = Offset(0f, 0f),
                        blurRadius = 28f
                    )
                }

                val glucoseBlock: @Composable () -> Unit = {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        // Main Giant Row: Zone Shape + Giant Number + Trend Arrow + Unit
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .alpha(numberAlpha)
                        ) {
                            // Zone Shape Indicator (not relying on color alone)
                            if (style.showZoneBadge) {
                                ZoneShapeBadge(zone = zone, color = zoneColor)
                                Spacer(modifier = Modifier.width(12.dp))
                            }

                            // Giant Number
                            Text(
                                text = valueText,
                                color = numberColor,
                                fontSize = dynamicFontSize,
                                maxLines = 1,
                                softWrap = false,
                                fontFamily = numberFontFamily,
                                style = TextStyle(shadow = numberShadow),
                                modifier = Modifier.testTag("glucose_big_number")
                            )

                            // Trend Arrow & Unit column
                            if (style.showTrendArrow || style.showUnit) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(
                                    horizontalAlignment = Alignment.Start,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    if (style.showTrendArrow) {
                                        Text(
                                            text = reading.trend.symbol,
                                            color = numberColor,
                                            fontSize = arrowFontSize,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.testTag("trend_arrow")
                                        )
                                    }
                                    if (style.showUnit) {
                                        Text(
                                            text = unit.label,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = unitFontSize,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }

                        // Secondary info row: Time + Age counter + Delta from previous reading
                        if (style.showInfoRow || style.showDelta) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp)
                            ) {
                                if (style.showInfoRow) {
                                    Text(
                                        text = GlucoseUtils.formatTime(reading.measuredAtUtcMs),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = (16f * infoScale).sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "•",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = GlucoseUtils.formatAge(reading.measuredAtUtcMs, nowMs),
                                        color = if (isDelayed) GlucoseColors.StatusAmber else MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = (15f * infoScale).sp,
                                        fontWeight = if (isDelayed) FontWeight.Bold else FontWeight.Normal
                                    )
                                }

                                // Delta from previous reading
                                if (style.showDelta && previousReading != null && previousReading.id != reading.id) {
                                    val delta = reading.valueMgDl - previousReading.valueMgDl
                                    if (style.showInfoRow) Spacer(modifier = Modifier.width(10.dp))
                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = GlucoseUtils.formatDelta(delta, unit),
                                            color = if (delta > 0) GlucoseColors.High else if (delta < 0) GlucoseColors.Low else MaterialTheme.colorScheme.onSurface,
                                            fontSize = (13f * infoScale).sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Amber banner when RETRASADA (5-10 min)
                    if (isDelayed) {
                        Surface(
                            color = GlucoseColors.StatusAmber.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.padding(bottom = 6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = "Lectura retrasada",
                                    tint = GlucoseColors.StatusAmber,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Lectura retrasada (${GlucoseUtils.formatAge(reading.measuredAtUtcMs, nowMs)})",
                                    color = GlucoseColors.StatusAmber,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    if (style.showPanel) {
                        // Scrim container behind the glucose block to guarantee contrast over any wallpaper
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = style.panelOpacity.coerceIn(0f, 1f)),
                            tonalElevation = 2.dp,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            glucoseBlock()
                        }
                    } else {
                        Box(modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)) {
                            glucoseBlock()
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ZoneShapeBadge(zone: GlucoseZone, color: Color) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .background(color.copy(alpha = 0.18f), CircleShape)
            .border(2.dp, color, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        when (zone) {
            GlucoseZone.URGENT_LOW -> Icon(
                imageVector = Icons.Default.KeyboardDoubleArrowDown,
                contentDescription = zone.labelEs,
                tint = color,
                modifier = Modifier.size(20.dp)
            )
            GlucoseZone.LOW -> Icon(
                imageVector = Icons.Default.KeyboardDoubleArrowDown,
                contentDescription = zone.labelEs,
                tint = color,
                modifier = Modifier.size(18.dp)
            )
            GlucoseZone.NEAR_LOW -> Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = zone.labelEs,
                tint = color,
                modifier = Modifier.size(20.dp)
            )
            GlucoseZone.ELEVATED -> Icon(
                imageVector = Icons.Default.KeyboardArrowUp,
                contentDescription = zone.labelEs,
                tint = color,
                modifier = Modifier.size(20.dp)
            )
            GlucoseZone.IN_RANGE -> Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = zone.labelEs,
                tint = color,
                modifier = Modifier.size(18.dp)
            )
            GlucoseZone.HIGH -> Icon(
                imageVector = Icons.Default.KeyboardDoubleArrowUp,
                contentDescription = zone.labelEs,
                tint = color,
                modifier = Modifier.size(18.dp)
            )
            GlucoseZone.VERY_HIGH -> Icon(
                imageVector = Icons.Default.KeyboardDoubleArrowUp,
                contentDescription = zone.labelEs,
                tint = color,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
