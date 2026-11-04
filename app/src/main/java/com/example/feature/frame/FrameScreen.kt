package com.example.feature.frame

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.core.content.ContextCompat
import com.example.core.designsystem.LocalDisplayStyle
import com.example.core.model.AccessGesture
import com.example.core.model.AppThemeMode
import com.example.core.model.ClockPosition
import com.example.core.model.ActiveSourceType
import com.example.feature.background.BackgroundLayer
import com.example.feature.background.BackgroundLayerUiState

@Composable
fun FrameScreen(
    state: FrameUiState,
    onSelectWindow: (Int) -> Unit,
    onNavigateToSettings: () -> Unit,
    onVerifyPin: (String, () -> Unit) -> Unit,
    onDismissPin: () -> Unit,
    onRequestSettingsWithPin: (() -> Unit) -> Unit,
    backgroundState: BackgroundLayerUiState? = null,
    modifier: Modifier = Modifier,
    onToggleNotifications: (Boolean) -> Unit = {},
    showBatteryWarning: Boolean = false,
    onFixBattery: () -> Unit = {}
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    var cornerTapCount by remember { mutableIntStateOf(0) }
    var wakeFromBlackScreen by remember { mutableStateOf(false) }

    // Bell button: all notifications and alarms on/off (asks for the notification permission when turning on)
    val context = LocalContext.current

    // Insulin / food log (typed here or read from Nightscout) and sensor data
    val treatmentRepository = remember { com.example.data.treatments.TreatmentRepository.getInstance(context) }
    val treatmentsFlow = remember { treatmentRepository.observeRecent(48) }
    val treatments by treatmentsFlow.collectAsState(initial = emptyList())
    LaunchedEffect(Unit) { com.example.data.librelinkup.SensorInfoStore.load(context) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { onToggleNotifications(true) }

    // If black screen night mode is active and not manually awakened
    if (state.isNightDimmed && state.preferences.dimmingUseBlackScreen && !wakeFromBlackScreen) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .pointerInput(Unit) {
                    detectTapGestures {
                        wakeFromBlackScreen = true
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Toca para despertar",
                color = Color.White.copy(alpha = 0.2f),
                fontSize = 12.sp
            )
        }
        return
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .offset(x = state.pixelShiftX.dp, y = state.pixelShiftY.dp)
            .pointerInput(state.preferences.accessGesture) {
                detectTapGestures(
                    onLongPress = {
                        if (state.preferences.accessGesture == AccessGesture.LONG_PRESS_2S) {
                            onRequestSettingsWithPin(onNavigateToSettings)
                        }
                    }
                )
            }
    ) {
        if (backgroundState != null) {
            BackgroundLayer(state = backgroundState)
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            )
        }
        val style = LocalDisplayStyle.current
        val isNight = state.isNightDimmed || state.preferences.themeMode == AppThemeMode.NIGHT

        val bigDisplay: @Composable () -> Unit = {
            GlucoseBigDisplay(
                reading = state.latestReading,
                previousReading = state.previousReading,
                freshness = state.freshness,
                nowMs = state.currentNowMs,
                thresholds = state.preferences.thresholds,
                unit = state.preferences.unit,
                isNight = isNight
            )
        }

        val chartPanel: @Composable (Modifier) -> Unit = { panelModifier ->
            Column(modifier = panelModifier) {
                // Window selector chips (1h, 3h, 6h, 12h, 24h)
                if (style.showWindowChips) {
                    ChartWindowSelectorRow(
                        selectedHours = state.selectedWindowHours,
                        onSelect = onSelectWindow
                    )
                }

                // Chart Canvas
                GlucoseChartCanvas(
                    readings = state.windowReadings,
                    windowHours = state.selectedWindowHours,
                    thresholds = state.preferences.thresholds,
                    unit = state.preferences.unit,
                    treatments = if (style.showTreatments) treatments else emptyList(),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )
            }
        }

        Column(modifier = Modifier.fillMaxSize()) {
            // Top Bar: connection badge, discreet clock and corner tap area for alternative gesture
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left slot: connection badge (+ clock when placed on the left)
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start
                ) {
                    if (style.showConnectionBadge) {
                        ConnectionBadge(
                            status = state.syncStatus,
                            clockSkewDetected = state.preferences.clockSkewDetected,
                            isSimulated = state.preferences.activeSource == ActiveSourceType.SIMULATED
                        )
                    }
                    if (style.showNotificationButton) {
                        val notificationsOn = state.preferences.notificationsEnabled
                        IconButton(
                            onClick = {
                                if (notificationsOn) {
                                    onToggleNotifications(false)
                                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                                ) {
                                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    onToggleNotifications(true)
                                }
                            },
                            modifier = Modifier
                                .size(40.dp)
                                .testTag("notifications_toggle_button")
                        ) {
                            Icon(
                                imageVector = if (notificationsOn) Icons.Default.Notifications else Icons.Default.NotificationsOff,
                                contentDescription = if (notificationsOn) "Desactivar todas las notificaciones" else "Activar todas las notificaciones",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (notificationsOn) 0.9f else 0.6f)
                            )
                        }
                    }
                    if (style.showTreatments) {
                        TreatmentsButton(treatments = treatments)
                    }
                    if (style.showSensorDays) {
                        SensorDaysText(nowMs = state.currentNowMs, modifier = Modifier.padding(start = 4.dp))
                    }
                    if (style.clockPosition == ClockPosition.LEFT) {
                        Spacer(modifier = Modifier.width(8.dp))
                        DiscreetClock(themeMode = state.preferences.themeMode)
                    }
                }

                // Center slot
                if (style.clockPosition == ClockPosition.CENTER) {
                    DiscreetClock(themeMode = state.preferences.themeMode)
                }

                // Right slot: clock (when placed on the right) + corner tap detector
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Box(
                        modifier = Modifier.pointerInput(state.preferences.accessGesture) {
                            detectTapGestures {
                                if (state.preferences.accessGesture == AccessGesture.TRIPLE_TAP_CORNER) {
                                    cornerTapCount++
                                    if (cornerTapCount >= 3) {
                                        cornerTapCount = 0
                                        onRequestSettingsWithPin(onNavigateToSettings)
                                    }
                                }
                            }
                        }
                    ) {
                        if (style.clockPosition == ClockPosition.RIGHT && style.clockVisible) {
                            DiscreetClock(themeMode = state.preferences.themeMode)
                        } else {
                            // Keeps the corner tap area even when the clock is hidden or placed elsewhere
                            Spacer(modifier = Modifier.size(width = 56.dp, height = 32.dp))
                        }
                    }
                }
            }

            // Warning when Android may stop the app in the background (readings would stop arriving)
            if (showBatteryWarning) {
                Surface(
                    color = Color(0xFFFFB300).copy(alpha = 0.92f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 2.dp)
                        .clickable { onFixBattery() }
                        .testTag("battery_warning")
                ) {
                    Text(
                        text = "⚠ Para no perder lecturas con la pantalla apagada, toca aquí y permite la app «sin restricciones» de batería.",
                        color = Color(0xFF1A1A1A),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            // Main Content Area: Landscape (side-by-side) vs Portrait (top-down)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (isLandscape) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left Pane: Big Number Display (44% width, or all the width without chart)
                        Box(
                            modifier = Modifier
                                .weight(if (style.chartVisible) 0.44f else 1f)
                                .fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            bigDisplay()
                        }

                        // Right Pane: Canvas Chart (56% width)
                        if (style.chartVisible) {
                            chartPanel(
                                Modifier
                                    .weight(0.56f)
                                    .fillMaxSize()
                                    .padding(end = 12.dp, bottom = 8.dp)
                            )
                        }
                    }
                } else {
                    // Portrait Mode (Vertical)
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Top: Big Number Display (40% height, or all the height without chart)
                        Box(
                            modifier = Modifier
                                .weight(if (style.chartVisible) 0.40f else 1f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            bigDisplay()
                        }

                        // Bottom: Chart Canvas (60% height)
                        if (style.chartVisible) {
                            chartPanel(
                                Modifier
                                    .weight(0.60f)
                                    .fillMaxWidth()
                                    .padding(start = 8.dp, end = 8.dp, bottom = 8.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    // PIN Verification Dialog if enabled
    if (state.pinDialogOpen) {
        var enteredPin by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = onDismissPin,
            title = { Text("Introduce PIN de acceso") },
            text = {
                Column {
                    Text(
                        text = "El acceso a la configuración está protegido con PIN.",
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = enteredPin,
                        onValueChange = { enteredPin = it },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        enabled = state.pinLockoutSeconds == 0,
                        isError = state.pinError || state.pinLockoutSeconds > 0,
                        label = { Text("PIN") },
                        modifier = Modifier.fillMaxWidth().testTag("pin_input")
                    )
                    if (state.pinLockoutSeconds > 0) {
                        Text(
                            text = "Demasiados intentos. Espera ${state.pinLockoutSeconds} s.",
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    } else if (state.pinError) {
                        Text(
                            text = "PIN incorrecto",
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onVerifyPin(enteredPin, onNavigateToSettings)
                    },
                    enabled = state.pinLockoutSeconds == 0,
                    modifier = Modifier.testTag("confirm_pin_button")
                ) {
                    Text("Aceptar")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissPin) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
fun ChartWindowSelectorRow(
    selectedHours: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val windows = listOf(1, 3, 6, 12, 24)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (h in windows) {
            val isSelected = selectedHours == h
            FilterChip(
                selected = isSelected,
                onClick = { onSelect(h) },
                label = { Text("${h}h", fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                modifier = Modifier
                    .padding(horizontal = 2.dp)
                    .height(28.dp)
                    .testTag("window_${h}h_chip"),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    }
}
