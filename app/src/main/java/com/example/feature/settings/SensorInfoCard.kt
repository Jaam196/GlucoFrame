package com.example.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.librelinkup.SensorInfoStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Data of the active sensor that LibreLinkUp reports (serial, start, days left, limits). */
@Composable
fun SensorInfoCard() {
    val context = LocalContext.current
    LaunchedEffect(Unit) { SensorInfoStore.load(context) }
    val info by SensorInfoStore.info.collectAsState()
    val lifeDays by SensorInfoStore.lifeDays.collectAsState()
    val now = System.currentTimeMillis()
    val dateFormat = SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault())

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Sensor", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(modifier = Modifier.height(6.dp))
            val sensor = info
            if (sensor == null || !sensor.isKnown) {
                Text(
                    "Aún no hay datos del sensor. Aparecerán tras la primera lectura correcta con LibreLinkUp.",
                    fontSize = 13.sp
                )
            } else {
                val hoursLeft = sensor.hoursLeft(now, lifeDays)
                val left = when {
                    hoursLeft <= 0 -> "Caducado"
                    hoursLeft < 48 -> "$hoursLeft h"
                    else -> "${hoursLeft / 24} días"
                }
                InfoLine("Día de uso", "${sensor.dayOfUse(now)} de $lifeDays")
                InfoLine("Quedan", left)
                InfoLine("Activado", dateFormat.format(Date(sensor.activatedAtSec * 1000L)))
                InfoLine("Termina", dateFormat.format(Date(sensor.endsAtMs(lifeDays))))
                if (sensor.serial.isNotBlank()) InfoLine("Número de serie", sensor.serial)
                if (sensor.warmupMinutes > 0) InfoLine("Calentamiento", "${sensor.warmupMinutes} min")
                if (sensor.typeCode > 0) InfoLine("Código de tipo de sensor", sensor.typeCode.toString())
                if (sensor.appVersion.isNotBlank()) InfoLine("Versión de la app Libre", sensor.appVersion)
                if (sensor.targetLowMgDl > 0 && sensor.targetHighMgDl > 0) {
                    InfoLine("Objetivo (cuenta Libre)", "${sensor.targetLowMgDl}–${sensor.targetHighMgDl} mg/dL")
                }
                if (sensor.lowLimitMgDl > 0 && sensor.highLimitMgDl > 0) {
                    InfoLine("Límites (dispositivo)", "${sensor.lowLimitMgDl}–${sensor.highLimitMgDl} mg/dL")
                }
                InfoLine("Alarmas en la app Libre", if (sensor.alarmsEnabled) "Activadas" else "Desactivadas")
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text("Duración del sensor", fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(
                "LibreLinkUp no indica cuántos días dura tu sensor; elige la que corresponde al tuyo.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                listOf(14, 15).forEach { days ->
                    FilterChip(
                        selected = lifeDays == days,
                        onClick = { SensorInfoStore.setLifeDays(context, days) },
                        label = { Text("$days días") }
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}
