package com.example.feature.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.core.common.GlucoseUtils

@Composable
private fun SwitchLine(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
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

/** General switch: every notification and alarm on or off (the same as the bell button on the main screen). */
@Composable
fun NotificationsMasterCard(state: SettingsUiState, viewModel: SettingsViewModel) {
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        viewModel.setNotificationsEnabled(true)
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Todas las notificaciones", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text(
                    "Interruptor general de la barra de estado, el icono extra y las alarmas. Los ajustes de cada una se conservan. " +
                        "También está el botón de la campana en la pantalla principal.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                )
            }
            Switch(
                checked = state.preferences.notificationsEnabled,
                onCheckedChange = { on ->
                    if (!on) {
                        viewModel.setNotificationsEnabled(false)
                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                    ) {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        viewModel.setNotificationsEnabled(true)
                    }
                }
            )
        }
    }
}

/** Optional glucose alarms like GlucoDataHandler's: per type, repeat, snooze, sound and full screen. */
@Composable
fun AlarmsCard(state: SettingsUiState, viewModel: SettingsViewModel) {
    val prefs = state.preferences
    val t = prefs.thresholds
    val unit = prefs.unit
    fun f(v: Int) = "${GlucoseUtils.formatValue(v, unit)} ${unit.label}"

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Alarmas de glucosa", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(
                "Ayuda secundaria: NO sustituye las alarmas oficiales de tu sensor ni de la app de Abbott. " +
                    "Para que funcionen, la app debe seguir ejecutándose en segundo plano (sin restricciones de batería).",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            SwitchLine("Activar alarmas", prefs.alarmsEnabled) { viewModel.setAlarmsEnabled(it) }

            if (prefs.alarmsEnabled) {
                Spacer(modifier = Modifier.height(6.dp))
                SwitchLine("Muy baja (menos de ${f(t.urgentLowMgDl)})", prefs.alarmUrgentLow) { viewModel.setAlarmFlag("urgent_low", it) }
                SwitchLine("Baja (menos de ${f(t.lowMgDl)})", prefs.alarmLow) { viewModel.setAlarmFlag("low", it) }
                SwitchLine("Alta (más de ${f(t.highMgDl)})", prefs.alarmHigh) { viewModel.setAlarmFlag("high", it) }
                SwitchLine("Muy alta (más de ${f(t.veryHighMgDl)})", prefs.alarmVeryHigh) { viewModel.setAlarmFlag("very_high", it) }
                SwitchLine("Sin datos (más de ${t.obsoleteMinutes} min)", prefs.alarmObsolete) { viewModel.setAlarmFlag("obsolete", it) }

                Spacer(modifier = Modifier.height(8.dp))
                SwitchLine("Con sonido (si no, solo vibración)", prefs.alarmSound) { viewModel.setAlarmFlag("sound", it) }
                SwitchLine("Pantalla completa con el móvil bloqueado", prefs.alarmFullScreen) { viewModel.setAlarmFlag("full_screen", it) }
                Text(
                    "En Android 14 o superior la pantalla completa puede requerir permiso en Ajustes del sistema → Aplicaciones → GlucoseFrame → Notificaciones.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(10.dp))
                Text("Repetir mientras siga igual:", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    for (minutes in listOf(1, 2, 5, 10, 15, 30)) {
                        FilterChip(
                            selected = prefs.alarmRepeatMinutes == minutes,
                            onClick = { viewModel.setAlarmTimes(minutes, prefs.alarmSnoozeMinutes) },
                            label = { Text("$minutes min") }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text("Tiempo del botón «Posponer»:", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    for (minutes in listOf(15, 30, 60, 120, 240)) {
                        FilterChip(
                            selected = prefs.alarmSnoozeMinutes == minutes,
                            onClick = { viewModel.setAlarmTimes(prefs.alarmRepeatMinutes, minutes) },
                            label = { Text(if (minutes >= 60) "${minutes / 60} h" else "$minutes min") }
                        )
                    }
                }

                val snoozedUntil = prefs.alarmSnoozeUntilMs
                if (snoozedUntil > System.currentTimeMillis()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Alarmas pospuestas hasta las ${GlucoseUtils.formatTime(snoozedUntil)}",
                            fontSize = 13.sp,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { viewModel.clearAlarmSnooze() }) { Text("Cancelar") }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(onClick = { viewModel.testAlarm() }) { Text("Probar alarma") }
            }
        }
    }
}
