package com.example.feature.frame

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.common.GlucoseUtils
import com.example.core.model.Treatment
import com.example.core.model.TreatmentKind
import com.example.data.librelinkup.SensorInfoStore
import com.example.data.treatments.TreatmentRepository
import kotlinx.coroutines.launch

private val InsulinColor = Color(0xFF4FC3F7)
private val FoodColor = Color(0xFFFFB74D)

/** "Sensor 6/14": day of use of the active sensor (hidden until LibreLinkUp has reported it). */
@Composable
fun SensorDaysText(nowMs: Long, modifier: Modifier = Modifier) {
    val info by SensorInfoStore.info.collectAsState()
    val lifeDays by SensorInfoStore.lifeDays.collectAsState()
    val sensor = info ?: return
    if (!sensor.isKnown) return

    val day = sensor.dayOfUse(nowMs)
    val hoursLeft = sensor.hoursLeft(nowMs, lifeDays)
    val color = when {
        hoursLeft <= 0 -> Color(0xFFEF5350)
        hoursLeft <= 24 -> Color(0xFFFFA726)
        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
    }
    val text = if (hoursLeft <= 0) "Sensor caducado" else "Sensor $day/$lifeDays"
    Text(text = text, color = color, fontSize = 12.sp, modifier = modifier)
}

/** "+" button that opens the insulin / food log. */
@Composable
fun TreatmentsButton(treatments: List<Treatment>, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    IconButton(
        onClick = { open = true },
        modifier = modifier
            .size(40.dp)
            .testTag("treatments_button")
    ) {
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = "Anotar insulina o comida",
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.9f)
        )
    }
    if (open) {
        TreatmentsDialog(treatments = treatments, onDismiss = { open = false })
    }
}

@Composable
private fun TreatmentsDialog(treatments: List<Treatment>, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { TreatmentRepository.getInstance(context) }

    var kind by remember { mutableStateOf(TreatmentKind.INSULIN) }
    var amountText by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var minutesAgo by remember { mutableLongStateOf(0L) }

    val amount = amountText.replace(',', '.').toDoubleOrNull()
    val needsAmount = kind != TreatmentKind.NOTE
    val canSave = if (needsAmount) (amount != null && amount > 0.0 && amount <= 1000.0) else note.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Insulina y comida") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = kind == TreatmentKind.INSULIN, onClick = { kind = TreatmentKind.INSULIN }, label = { Text("Insulina") })
                    FilterChip(selected = kind == TreatmentKind.FOOD, onClick = { kind = TreatmentKind.FOOD }, label = { Text("Comida") })
                    FilterChip(selected = kind == TreatmentKind.NOTE, onClick = { kind = TreatmentKind.NOTE }, label = { Text("Nota") })
                }
                if (needsAmount) {
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = it.take(6) },
                        label = { Text(if (kind == TreatmentKind.INSULIN) "Unidades" else "Hidratos de carbono (g)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(80) },
                    label = { Text(if (kind == TreatmentKind.NOTE) "Nota" else "Nota (opcional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(0L to "Ahora", 15L to "-15 min", 30L to "-30 min", 60L to "-1 h").forEach { (m, label) ->
                        FilterChip(selected = minutesAgo == m, onClick = { minutesAgo = m }, label = { Text(label, fontSize = 12.sp) })
                    }
                }

                val recent = treatments.sortedByDescending { it.atMs }.take(12)
                if (recent.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Últimas anotaciones", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    LazyColumn(modifier = Modifier.heightIn(max = 160.dp)) {
                        items(recent, key = { it.externalId.ifBlank { it.id.toString() } }) { t ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = describe(t),
                                    fontSize = 13.sp,
                                    color = when (t.kind) {
                                        TreatmentKind.INSULIN -> InsulinColor
                                        TreatmentKind.FOOD -> FoodColor
                                        TreatmentKind.NOTE -> MaterialTheme.colorScheme.onSurface
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(vertical = 4.dp)
                                )
                                if (t.origin == Treatment.ORIGIN_LOCAL) {
                                    IconButton(
                                        onClick = { scope.launch { repo.delete(t.id) } },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Borrar", modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = canSave,
                onClick = {
                    val at = System.currentTimeMillis() - minutesAgo * 60_000L
                    scope.launch {
                        repo.addLocal(kind, if (needsAmount) (amount ?: 0.0) else 0.0, note, at)
                    }
                    amountText = ""
                    note = ""
                    minutesAgo = 0L
                }
            ) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } }
    )
}

private fun describe(t: Treatment): String {
    val time = GlucoseUtils.formatTime(t.atMs)
    val what = when (t.kind) {
        TreatmentKind.INSULIN -> formatAmount(t.amount) + " U insulina"
        TreatmentKind.FOOD -> formatAmount(t.amount) + " g hidratos"
        TreatmentKind.NOTE -> "Nota"
    }
    val extra = if (t.note.isNotBlank()) " · ${t.note}" else ""
    val origin = if (t.origin == Treatment.ORIGIN_NIGHTSCOUT) " (Nightscout)" else ""
    return "$time  $what$extra$origin"
}
