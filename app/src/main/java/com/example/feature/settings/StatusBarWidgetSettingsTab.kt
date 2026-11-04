package com.example.feature.settings

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.core.designsystem.GlucoseColors

@Composable
fun StatusBarWidgetSettingsTab(
    state: SettingsUiState,
    viewModel: SettingsViewModel,
    activity: Activity?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var permissionDeniedMessage by remember { mutableStateOf(false) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            permissionDeniedMessage = false
            viewModel.setStatusBarNotificationEnabled(true)
        } else {
            permissionDeniedMessage = true
            viewModel.setStatusBarNotificationEnabled(false)
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Text("Barra de Estado y Widget", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(14.dp))

        NotificationsMasterCard(state, viewModel)
        Spacer(modifier = Modifier.height(16.dp))

        // Card: Status Bar Notification
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Glucosa en la Barra de Estado", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Switch: Show in status bar
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = state.preferences.statusBarNotificationEnabled,
                        onCheckedChange = { checked ->
                            if (checked) {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                                ) {
                                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    permissionDeniedMessage = false
                                    viewModel.setStatusBarNotificationEnabled(true)
                                }
                            } else {
                                permissionDeniedMessage = false
                                viewModel.setStatusBarNotificationEnabled(false)
                            }
                        }
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("Mostrar glucosa en la barra de estado", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text(
                            "Muestra una notificación fija en la barra de estado con la lectura y flecha de tendencia.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (permissionDeniedMessage) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "Se requiere permiso de notificaciones para mostrar la glucosa en la barra de estado. Por favor, habilítalo en los ajustes del sistema.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }

                if (state.preferences.statusBarNotificationEnabled) {
                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(12.dp))

                    // Switch: Hide on lock screen
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(
                            checked = state.preferences.hideOnLockscreen,
                            onCheckedChange = { viewModel.setHideOnLockscreen(it) }
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("Ocultar en pantalla de bloqueo", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text(
                                "Oculta el contenido de la notificación cuando el dispositivo está bloqueado.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Switch: Start on boot
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(
                            checked = state.preferences.startOnBoot,
                            onCheckedChange = { viewModel.setStartOnBoot(it) }
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("Iniciar al arrancar el dispositivo", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text(
                                "Inicia la monitorización y la notificación fija automáticamente al encender la tablet.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Card: glucose alarms
        AlarmsCard(state, viewModel)
        Spacer(modifier = Modifier.height(16.dp))

        // Card: Home screen widget instructions
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Widgets,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Widget para la Pantalla de Inicio", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    "Mantén pulsada la pantalla de inicio, elige Widgets y busca GlucoseFrame.",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "• Modo compacto (< 180 dp): muestra el valor grande y la flecha de tendencia.\n" +
                    "• Modo amplio (≥ 180 dp): añade la unidad, estado del rango y tiempo desde la última lectura.\n" +
                    "• El fondo se colorea según el estado de la glucosa, con texto optimizado para máximo contraste.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Card: Battery optimization (reused notice)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = if (state.isBatteryOptimizationIgnored) Color(0xFF00E676) else GlucoseColors.StatusAmber,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Optimización de Batería en Segundo Plano", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = if (state.isBatteryOptimizationIgnored) {
                        "Estado: La app ya está exenta de ahorro de batería. Las actualizaciones continuarán sin interrupciones."
                    } else {
                        "Estado: Pendiente de exención. Para garantizar que la notificación y el widget se actualicen de forma ininterrumpida, solicita la exención de ahorro de batería."
                    },
                    fontSize = 13.sp,
                    color = if (state.isBatteryOptimizationIgnored) Color(0xFF00E676) else GlucoseColors.StatusAmber
                )

                if (!state.isBatteryOptimizationIgnored && activity != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row {
                        Button(onClick = { viewModel.requestIgnoreBatteryOptimization(activity) }) {
                            Text("Solicitar exención de ahorro")
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        OutlinedButton(onClick = { viewModel.openAppSystemSettings(activity) }) {
                            Text("Ajustes del sistema")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(12.dp))

                // Reading in the background: the part that must never fail
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = state.preferences.keepSyncingInBackground,
                        onCheckedChange = { viewModel.setKeepSyncingInBackground(it) }
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("Lectura siempre activa en segundo plano", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text(
                            "Recomendado. Mantiene un aviso mínimo y silencioso y un despertador del sistema que lee la glucosa " +
                                "cada minuto aunque la app esté cerrada o la pantalla apagada, y se recupera solo si Android la detiene.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !com.example.sync.SyncAlarm.canScheduleExact(context) && activity != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        "Para que el despertador sea puntual (y no se retrase con la pantalla apagada), permite las «alarmas y recordatorios» exactos:",
                        fontSize = 12.sp,
                        color = GlucoseColors.StatusAmber
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedButton(onClick = { viewModel.openExactAlarmSettings(activity) }) {
                        Text("Permitir alarmas exactas")
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    "Samsung / One UI: Ajustes → Batería → Límites de uso en segundo plano → desactiva «Poner en suspensión las apps sin usar» " +
                        "y añade GlucoseFrame a «Apps que nunca se suspenden». En otras marcas busca «inicio automático» o «sin restricciones de batería».",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
