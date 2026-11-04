package com.example.feature.displaystyle

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.common.GlucoseUtils
import com.example.core.designsystem.LocalDisplayStyle
import com.example.core.designsystem.StyleColors
import com.example.core.model.AppThemeMode
import com.example.core.model.ChartStyle
import com.example.core.model.ClockPosition
import com.example.core.model.ColorSource
import com.example.core.model.FreshnessState
import com.example.core.model.GlucoseReading
import com.example.core.model.GlucoseUnit
import com.example.core.model.GlucoseZone
import com.example.core.model.NightNumberMode
import com.example.core.model.NumberEffect
import com.example.core.model.NumberFont
import com.example.core.model.ExtraStatusIcon
import com.example.core.model.NotificationCardStyle
import com.example.core.model.StatusIconMode
import com.example.core.model.TextColorSource
import com.example.core.model.ThresholdSettings
import com.example.core.model.TrendArrow
import com.example.core.model.WidgetBackground
import com.example.core.model.WidgetCorner
import com.example.feature.background.settings.HsvColorPickerDialog
import com.example.feature.frame.DiscreetClock
import com.example.feature.frame.GlucoseBigDisplay
import com.example.feature.frame.GlucoseChartCanvas
import kotlin.math.sin

/** A colour the user is currently editing in the colour picker. */
private class PendingColor(
    val title: String,
    val hex: String,
    val onPick: (String) -> Unit
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DisplayStyleScreen(
    state: DisplayStyleUiState,
    viewModel: DisplayStyleViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }

    val style = state.style
    var pendingColor by remember { mutableStateOf<PendingColor?>(null) }
    var confirmReset by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Estilo de pantalla", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("style_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            // ---------------- Live preview ----------------
            PreviewCard(state)
            Spacer(modifier = Modifier.height(20.dp))

            // ---------------- Colours by glucose level ----------------
            StyleSection("Colores por nivel de glucosa", Icons.Default.Palette) {
                Text(
                    "Cada tramo de azúcar tiene su color. Toca uno para cambiarlo por el que quieras " +
                        "(rueda de color o código HEX). Se usa en el número, la gráfica, los widgets y la notificación.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                GlucoseZone.entries.forEach { zone ->
                    val hex = state.zoneColors.colorHexForZone(zone)
                    ColorRow(
                        label = zone.labelEs,
                        sublabel = zoneRangeText(zone, state.thresholds, state.unit),
                        hex = hex,
                        onClick = {
                            pendingColor = PendingColor("Color para: ${zone.labelEs}", hex) { picked ->
                                viewModel.setZoneColor(zone, picked)
                            }
                        }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                }
                TextButton(onClick = { viewModel.restoreZoneColors() }) {
                    Text("Restaurar colores predeterminados", fontSize = 12.sp)
                }
            }

            // ---------------- Big number ----------------
            StyleSection("Número de glucosa", Icons.Default.TextFields) {
                ChipRow("Tipo de letra", NumberFont.entries, style.numberFont, { it.displayName }) { choice ->
                    viewModel.update { it.copy(numberFont = choice) }
                }
                SliderRow("Tamaño del número", style.numberSizeScale, 0.5f..1.5f, { "${(it * 100).toInt()} %" }) { v ->
                    viewModel.update { it.copy(numberSizeScale = v) }
                }
                ChipRow("Efecto", NumberEffect.entries, style.numberEffect, { it.displayName }) { choice ->
                    viewModel.update { it.copy(numberEffect = choice) }
                }
                ChipRow("Color del número", ColorSource.entries, style.numberColorSource, { it.displayName }) { choice ->
                    viewModel.update { it.copy(numberColorSource = choice) }
                }
                if (style.numberColorSource == ColorSource.CUSTOM) {
                    ColorRow("Color del número", null, style.numberCustomHex) {
                        pendingColor = PendingColor("Color del número", style.numberCustomHex) { picked ->
                            viewModel.update { it.copy(numberCustomHex = picked) }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
                ToggleRow("Icono del nivel (círculo con símbolo)", style.showZoneBadge) { on ->
                    viewModel.update { it.copy(showZoneBadge = on) }
                }
                ToggleRow("Flecha de tendencia", style.showTrendArrow) { on ->
                    viewModel.update { it.copy(showTrendArrow = on) }
                }
                ToggleRow("Unidad (mg/dL)", style.showUnit) { on ->
                    viewModel.update { it.copy(showUnit = on) }
                }
                ToggleRow("Hora y antigüedad de la lectura", style.showInfoRow) { on ->
                    viewModel.update { it.copy(showInfoRow = on) }
                }
                ToggleRow("Diferencia con la lectura anterior", style.showDelta) { on ->
                    viewModel.update { it.copy(showDelta = on) }
                }
                SliderRow("Tamaño de unidad, hora y diferencia", style.infoTextScale, 0.7f..1.8f, { "${(it * 100).toInt()} %" }) { v ->
                    viewModel.update { it.copy(infoTextScale = v) }
                }
                ToggleRow("Tarjeta de fondo detrás del número", style.showPanel) { on ->
                    viewModel.update { it.copy(showPanel = on) }
                }
                if (style.showPanel) {
                    SliderRow("Opacidad de la tarjeta", style.panelOpacity, 0f..1f, { "${(it * 100).toInt()} %" }) { v ->
                        viewModel.update { it.copy(panelOpacity = v) }
                    }
                }
            }

            // ---------------- Number at night ----------------
            StyleSection("Número de noche", Icons.Default.NightsStay) {
                Text(
                    "Se aplica con el horario nocturno activo o con el tema «Nocturno».",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                ChipRow("Color de noche", NightNumberMode.entries, style.nightNumberMode, { it.displayName }) { choice ->
                    viewModel.update { it.copy(nightNumberMode = choice) }
                }
                if (style.nightNumberMode == NightNumberMode.DIMMED) {
                    SliderRow("Brillo del número de noche", style.nightDimLevel, 0.15f..1f, { "${(it * 100).toInt()} %" }) { v ->
                        viewModel.update { it.copy(nightDimLevel = v) }
                    }
                }
                if (style.nightNumberMode == NightNumberMode.CUSTOM) {
                    ColorRow("Color del número de noche", null, style.nightCustomHex) {
                        pendingColor = PendingColor("Color del número de noche", style.nightCustomHex) { picked ->
                            viewModel.update { it.copy(nightCustomHex = picked) }
                        }
                    }
                }
            }

            // ---------------- Chart ----------------
            StyleSection("Gráfica", Icons.Default.Timeline) {
                ToggleRow("Mostrar la gráfica", style.chartVisible) { on ->
                    viewModel.update { it.copy(chartVisible = on) }
                }
                if (style.chartVisible) {
                    ChipRow("Estilo", ChartStyle.entries, style.chartStyle, { it.displayName }) { choice ->
                        viewModel.update { it.copy(chartStyle = choice) }
                    }
                    ChipRow("Color de la gráfica", ColorSource.entries, style.chartColorSource, { it.displayName }) { choice ->
                        viewModel.update { it.copy(chartColorSource = choice) }
                    }
                    if (style.chartColorSource == ColorSource.CUSTOM) {
                        ColorRow("Color de la gráfica", null, style.chartCustomHex) {
                            pendingColor = PendingColor("Color de la gráfica", style.chartCustomHex) { picked ->
                                viewModel.update { it.copy(chartCustomHex = picked) }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    if (style.chartStyle != ChartStyle.POINTS) {
                        SliderRow("Grosor de la línea", style.chartLineWidthDp, 1f..8f, { "${it.toInt()} dp" }) { v ->
                            viewModel.update { it.copy(chartLineWidthDp = v) }
                        }
                    }
                    if (style.chartStyle == ChartStyle.LINE_POINTS || style.chartStyle == ChartStyle.POINTS) {
                        SliderRow("Tamaño de los puntos", style.chartPointRadiusDp, 1f..8f, { "${it.toInt()} dp" }) { v ->
                            viewModel.update { it.copy(chartPointRadiusDp = v) }
                        }
                    }
                    ChipRow("Altura máxima de la gráfica", listOf(250, 300, 350, 400), style.chartMaxMgDl, { "$it mg/dL" }) { choice ->
                        viewModel.update { it.copy(chartMaxMgDl = choice) }
                    }
                    ToggleRow("Banda del rango objetivo", style.chartShowTargetBand) { on ->
                        viewModel.update { it.copy(chartShowTargetBand = on) }
                    }
                    if (style.chartShowTargetBand) {
                        SliderRow("Intensidad de la banda", style.chartBandOpacity, 0f..0.5f, { "${(it * 200).toInt()} %" }) { v ->
                            viewModel.update { it.copy(chartBandOpacity = v) }
                        }
                    }
                    ToggleRow("Línea de referencia", style.chartShowRefLine) { on ->
                        viewModel.update { it.copy(chartShowRefLine = on) }
                    }
                    ToggleRow("Cuadrícula", style.chartShowGrid) { on ->
                        viewModel.update { it.copy(chartShowGrid = on) }
                    }
                    ToggleRow("Números de los ejes", style.chartShowAxisLabels) { on ->
                        viewModel.update { it.copy(chartShowAxisLabels = on) }
                    }
                    SliderRow("Tamaño de los textos", style.chartTextScale, 0.8f..1.8f, { "${(it * 100).toInt()} %" }) { v ->
                        viewModel.update { it.copy(chartTextScale = v) }
                    }
                    SliderRow("Fondo oscuro detrás de la gráfica", style.chartPanelOpacity, 0f..0.8f, { "${(it * 125).toInt()} %" }) { v ->
                        viewModel.update { it.copy(chartPanelOpacity = v) }
                    }
                    ToggleRow("Botones de horas (1h, 3h, 6h…)", style.showWindowChips) { on ->
                        viewModel.update { it.copy(showWindowChips = on) }
                    }
                }
            }

            // ---------------- Clock ----------------
            StyleSection("Reloj", Icons.Default.Schedule) {
                ToggleRow("Mostrar el reloj", style.clockVisible) { on ->
                    viewModel.update { it.copy(clockVisible = on) }
                }
                if (style.clockVisible) {
                    ChipRow("Posición", ClockPosition.entries, style.clockPosition, { it.displayName }) { choice ->
                        viewModel.update { it.copy(clockPosition = choice) }
                    }
                    ToggleRow("Formato de 24 horas", style.clock24h) { on ->
                        viewModel.update { it.copy(clock24h = on) }
                    }
                    ToggleRow("Mostrar segundos", style.clockShowSeconds) { on ->
                        viewModel.update { it.copy(clockShowSeconds = on) }
                    }
                    ToggleRow("Mostrar la fecha", style.clockShowDate) { on ->
                        viewModel.update { it.copy(clockShowDate = on) }
                    }
                    SliderRow("Tamaño del reloj", style.clockSizeSp, 10f..56f, { "${it.toInt()} sp" }) { v ->
                        viewModel.update { it.copy(clockSizeSp = v) }
                    }
                    Text(
                        "Consejo: mantén el reloj claramente más pequeño que el número de glucosa para no confundirlos.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    ChipRow("Tipo de letra", NumberFont.entries, style.clockFont, { it.displayName }) { choice ->
                        viewModel.update { it.copy(clockFont = choice) }
                    }
                    ChipRow("Color del reloj", TextColorSource.entries, style.clockColorSource, { it.displayName }) { choice ->
                        viewModel.update { it.copy(clockColorSource = choice) }
                    }
                    if (style.clockColorSource == TextColorSource.CUSTOM) {
                        ColorRow("Color del reloj", null, style.clockCustomHex) {
                            pendingColor = PendingColor("Color del reloj", style.clockCustomHex) { picked ->
                                viewModel.update { it.copy(clockCustomHex = picked) }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    SliderRow("Opacidad del reloj", style.clockOpacity, 0.2f..1f, { "${(it * 100).toInt()} %" }) { v ->
                        viewModel.update { it.copy(clockOpacity = v) }
                    }
                    ToggleRow("Recuadro detrás del reloj", style.clockPanel) { on ->
                        viewModel.update { it.copy(clockPanel = on) }
                    }
                }
            }

            // ---------------- Rest of the screen ----------------
            StyleSection("Resto de la pantalla", Icons.Default.Tv) {
                ToggleRow("Indicador de conexión (arriba a la izquierda)", style.showConnectionBadge) { on ->
                    viewModel.update { it.copy(showConnectionBadge = on) }
                }
                ToggleRow("Botón de campana (activar/desactivar todas las notificaciones)", style.showNotificationButton) { on ->
                    viewModel.update { it.copy(showNotificationButton = on) }
                }
                ToggleRow("Botón «+» y marcas de insulina/comida en la gráfica", style.showTreatments) { on ->
                    viewModel.update { it.copy(showTreatments = on) }
                }
                ToggleRow("Día del sensor (ej. «Sensor 6/14»)", style.showSensorDays) { on ->
                    viewModel.update { it.copy(showSensorDays = on) }
                }
            }

            // ---------------- Status bar notification ----------------
            StyleSection("Barra de estado y notificación", Icons.Default.Tv) {
                Text(
                    "Como GlucoDataHandler: el número y la flecha van en el color del nivel y la flecha se inclina según la " +
                        "tendencia. Android dibuja el iconito de la barra superior en un solo color. Actívala en Ajustes → Barra y widget.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                ChipRow("Aspecto de la notificación", NotificationCardStyle.entries, style.notificationCard, { it.displayName }) { choice ->
                    viewModel.update { it.copy(notificationCard = choice) }
                }
                ChipRow("Iconito de la barra superior", StatusIconMode.entries, style.statusIconMode, { it.displayName }) { choice ->
                    viewModel.update { it.copy(statusIconMode = choice) }
                }
                ToggleRow("Iconito del color del nivel (si tu móvil lo permite)", style.statusIconColored) { on ->
                    viewModel.update { it.copy(statusIconColored = on) }
                }
                ChipRow("Segundo iconito en la barra (notificación extra vacía)", ExtraStatusIcon.entries, style.extraStatusIcon, { it.displayName }) { choice ->
                    viewModel.update { it.copy(extraStatusIcon = choice) }
                }
            }

            // ---------------- Widgets ----------------
            StyleSection("Widgets", Icons.Default.Widgets) {
                Text(
                    "Afecta a los widgets de la pantalla de inicio. Si alguno no cambia al momento, quítalo y vuelve a añadirlo.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                ChipRow("Fondo", WidgetBackground.entries, style.widgetBackground, { it.displayName }) { choice ->
                    viewModel.update { it.copy(widgetBackground = choice) }
                }
                if (style.widgetBackground == WidgetBackground.CUSTOM_COLOR) {
                    ColorRow("Color de fondo del widget", null, style.widgetCustomBgHex) {
                        pendingColor = PendingColor("Color de fondo del widget", style.widgetCustomBgHex) { picked ->
                            viewModel.update { it.copy(widgetCustomBgHex = picked) }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
                if (style.widgetBackground != WidgetBackground.TRANSPARENT) {
                    SliderRow("Opacidad del fondo", style.widgetOpacity, 0.1f..1f, { "${(it * 100).toInt()} %" }) { v ->
                        viewModel.update { it.copy(widgetOpacity = v) }
                    }
                }
                ChipRow("Esquinas", WidgetCorner.entries, style.widgetCorner, { it.displayName }) { choice ->
                    viewModel.update { it.copy(widgetCorner = choice) }
                }
                ChipRow("Color del texto", TextColorSource.entries, style.widgetTextSource, { it.displayName }) { choice ->
                    viewModel.update { it.copy(widgetTextSource = choice) }
                }
                if (style.widgetTextSource == TextColorSource.CUSTOM) {
                    ColorRow("Color del texto del widget", null, style.widgetTextHex) {
                        pendingColor = PendingColor("Color del texto del widget", style.widgetTextHex) { picked ->
                            viewModel.update { it.copy(widgetTextHex = picked) }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
                SliderRow("Tamaño de los textos", style.widgetSizeScale, 0.7f..1.8f, { "${(it * 100).toInt()} %" }) { v ->
                    viewModel.update { it.copy(widgetSizeScale = v) }
                }
                ToggleRow("Flecha de tendencia", style.widgetShowTrend) { on ->
                    viewModel.update { it.copy(widgetShowTrend = on) }
                }
                ToggleRow("Unidad (widget grande)", style.widgetShowUnit) { on ->
                    viewModel.update { it.copy(widgetShowUnit = on) }
                }
                ToggleRow("Nombre del nivel (widget grande)", style.widgetShowZone) { on ->
                    viewModel.update { it.copy(widgetShowZone = on) }
                }
                ToggleRow("Antigüedad de la lectura (widget grande)", style.widgetShowAge) { on ->
                    viewModel.update { it.copy(widgetShowAge = on) }
                }
                ToggleRow("Diferencia con la lectura anterior (+4, -7…)", style.widgetShowDelta) { on ->
                    viewModel.update { it.copy(widgetShowDelta = on) }
                }
                ChipRow("Horas de la gráfica (widget «Glucosa + gráfica»)", listOf(1, 3, 6, 12, 24), style.widgetGraphHours, { "$it h" }) { choice ->
                    viewModel.update { it.copy(widgetGraphHours = choice) }
                }
            }

            OutlinedButton(
                onClick = { confirmReset = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("style_reset_button")
            ) {
                Text("Restablecer todo el estilo")
            }
            Spacer(modifier = Modifier.height(40.dp))
        }
    }

    pendingColor?.let { pending ->
        HsvColorPickerDialog(
            title = pending.title,
            initialHex = pending.hex,
            onConfirm = { picked ->
                pending.onPick(picked)
                pendingColor = null
            },
            onDismiss = { pendingColor = null }
        )
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Restablecer estilo") },
            text = { Text("Se recuperará el aspecto original del número, la gráfica, el reloj y los widgets. Los colores por nivel de glucosa no cambian.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.resetAll()
                    confirmReset = false
                }) { Text("Restablecer") }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) { Text("Cancelar") }
            }
        )
    }
}

// ============================================================================================
// Live preview
// ============================================================================================

@Composable
private fun PreviewCard(state: DisplayStyleUiState) {
    var previewZone by remember { mutableStateOf(GlucoseZone.IN_RANGE) }
    var previewNight by remember { mutableStateOf(false) }

    val sampleValue = when (previewZone) {
        GlucoseZone.URGENT_LOW -> 48
        GlucoseZone.LOW -> 62
        GlucoseZone.NEAR_LOW -> 80
        GlucoseZone.IN_RANGE -> 112
        GlucoseZone.ELEVATED -> 160
        GlucoseZone.HIGH -> 215
        GlucoseZone.VERY_HIGH -> 285
    }
    val nowMs = remember(previewZone) { System.currentTimeMillis() }
    val reading = remember(previewZone) {
        GlucoseReading(
            id = 2L, sourceId = "preview", measuredAtUtcMs = nowMs - 60_000L,
            valueMgDl = sampleValue, trend = TrendArrow.FLAT
        )
    }
    val previous = remember(previewZone) {
        GlucoseReading(
            id = 1L, sourceId = "preview", measuredAtUtcMs = nowMs - 360_000L,
            valueMgDl = sampleValue - 4, trend = TrendArrow.FLAT
        )
    }
    val series = remember(previewZone) {
        (0 until 36).map { i ->
            val wave = 30.0 * (sin(i / 4.0) - sin(35 / 4.0))
            GlucoseReading(
                id = 10L + i, sourceId = "preview",
                measuredAtUtcMs = nowMs - (35 - i) * 300_000L,
                valueMgDl = (sampleValue + wave).toInt().coerceIn(45, 320),
                trend = TrendArrow.FLAT
            )
        }
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Visibility, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Vista previa en tiempo real", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GlucoseZone.entries.forEach { zone ->
                    FilterChip(
                        selected = previewZone == zone,
                        onClick = { previewZone = zone },
                        label = { Text(zone.labelEs, fontSize = 11.sp) }
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Ver como de noche", fontSize = 13.sp, modifier = Modifier.weight(1f))
                Switch(checked = previewNight, onCheckedChange = { previewNight = it })
            }
            Spacer(modifier = Modifier.height(8.dp))

            CompositionLocalProvider(LocalDisplayStyle provides state.style) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(330.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (previewNight) Color(0xFF140803) else MaterialTheme.colorScheme.background)
                        .padding(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        DiscreetClock(themeMode = if (previewNight) AppThemeMode.NIGHT else AppThemeMode.DARK)
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(if (state.style.chartVisible) 160.dp else 260.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        GlucoseBigDisplay(
                            reading = reading,
                            previousReading = previous,
                            freshness = FreshnessState.ACTUAL,
                            nowMs = System.currentTimeMillis(),
                            thresholds = state.thresholds,
                            unit = state.unit,
                            isNight = previewNight
                        )
                    }
                    if (state.style.chartVisible) {
                        GlucoseChartCanvas(
                            readings = series,
                            windowHours = 3,
                            thresholds = state.thresholds,
                            unit = state.unit,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

// ============================================================================================
// Small building blocks
// ============================================================================================

@Composable
private fun StyleSection(
    title: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
            Spacer(modifier = Modifier.height(10.dp))
            content()
        }
    }
    Spacer(modifier = Modifier.height(20.dp))
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** Slider that keeps its own value while dragging and saves only when the finger is lifted. */
@Composable
private fun SliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    valueText: (Float) -> String,
    onCommit: (Float) -> Unit
) {
    var local by remember(value) { mutableFloatStateOf(value.coerceIn(range.start, range.endInclusive)) }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Text(valueText(local), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Slider(
            value = local,
            onValueChange = { local = it },
            onValueChangeFinished = { onCommit(local) },
            valueRange = range
        )
    }
}

@Composable
private fun <T> ChipRow(
    label: String,
    options: List<T>,
    selected: T,
    optionName: (T) -> String,
    onSelect: (T) -> Unit
) {
    Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    Spacer(modifier = Modifier.height(4.dp))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        options.forEach { option ->
            FilterChip(
                selected = option == selected,
                onClick = { onSelect(option) },
                label = { Text(optionName(option), fontSize = 12.sp) }
            )
        }
    }
    Spacer(modifier = Modifier.height(10.dp))
}

@Composable
private fun ColorRow(label: String, sublabel: String?, hex: String, onClick: () -> Unit) {
    val color = StyleColors.parse(hex, Color.Gray)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(color)
                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(label, fontWeight = FontWeight.Medium, fontSize = 14.sp)
            if (sublabel != null) {
                Text(sublabel, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(hex.uppercase(), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Human-readable glucose range of a zone, e.g. "70 – 90 mg/dL". */
private fun zoneRangeText(zone: GlucoseZone, t: ThresholdSettings, unit: GlucoseUnit): String {
    fun f(v: Int) = GlucoseUtils.formatValue(v, unit)
    return when (zone) {
        GlucoseZone.URGENT_LOW -> "menos de ${f(t.urgentLowMgDl)} ${unit.label}"
        GlucoseZone.LOW -> "${f(t.urgentLowMgDl)} – ${f(t.lowMgDl)} ${unit.label}"
        GlucoseZone.NEAR_LOW -> "${f(t.lowMgDl)} – ${f(t.nearLowMgDl)} ${unit.label}"
        GlucoseZone.IN_RANGE -> "${f(t.nearLowMgDl)} – ${f(t.elevatedMgDl)} ${unit.label}"
        GlucoseZone.ELEVATED -> "${f(t.elevatedMgDl)} – ${f(t.highMgDl)} ${unit.label}"
        GlucoseZone.HIGH -> "${f(t.highMgDl)} – ${f(t.veryHighMgDl)} ${unit.label}"
        GlucoseZone.VERY_HIGH -> "más de ${f(t.veryHighMgDl)} ${unit.label}"
    }
}
