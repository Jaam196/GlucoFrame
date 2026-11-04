package com.example.feature.background.settings

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.model.GlucoseZone

@Composable
fun HsvColorPickerDialog(
    zone: GlucoseZone,
    initialHex: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    HsvColorPickerDialog(
        title = "Color para: ${zone.labelEs}",
        initialHex = initialHex,
        onConfirm = onConfirm,
        onDismiss = onDismiss
    )
}

/** Same picker for any element (number at night, clock, chart line, widget...). */
@Composable
fun HsvColorPickerDialog(
    title: String,
    initialHex: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val initialHsv = remember(initialHex) {
        val hsv = FloatArray(3)
        try {
            AndroidColor.colorToHSV(AndroidColor.parseColor(initialHex), hsv)
        } catch (e: Exception) {
            hsv[0] = 0f
            hsv[1] = 1f
            hsv[2] = 1f
        }
        hsv
    }

    var hue by remember { mutableFloatStateOf(initialHsv[0]) }
    var sat by remember { mutableFloatStateOf(initialHsv[1]) }
    var value by remember { mutableFloatStateOf(initialHsv[2]) }
    var hexText by remember { mutableStateOf(initialHex.uppercase()) }

    fun updateHexFromHsv() {
        val colorInt = AndroidColor.HSVToColor(floatArrayOf(hue, sat, value))
        val hex = String.format("#%06X", 0xFFFFFF and colorInt)
        hexText = hex
    }

    fun updateHsvFromHex(hex: String) {
        try {
            val colorInt = AndroidColor.parseColor(hex.trim())
            val hsv = FloatArray(3)
            AndroidColor.colorToHSV(colorInt, hsv)
            hue = hsv[0]
            sat = hsv[1]
            value = hsv[2]
        } catch (e: Exception) {
            // Keep current HSV if invalid hex
        }
    }

    val currentColor = remember(hue, sat, value) {
        Color(AndroidColor.HSVToColor(floatArrayOf(hue, sat, value)))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 1. 2D Saturation / Value Box
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .pointerInput(hue) {
                            detectTapGestures { offset ->
                                sat = (offset.x / size.width).coerceIn(0f, 1f)
                                value = (1f - (offset.y / size.height)).coerceIn(0f, 1f)
                                updateHexFromHsv()
                            }
                        }
                        .pointerInput(hue) {
                            detectDragGestures { change, _ ->
                                sat = (change.position.x / size.width).coerceIn(0f, 1f)
                                value = (1f - (change.position.y / size.height)).coerceIn(0f, 1f)
                                updateHexFromHsv()
                            }
                        }
                ) {
                    val pureHueColor = Color(AndroidColor.HSVToColor(floatArrayOf(hue, 1f, 1f)))
                    Canvas(modifier = Modifier.matchParentSize()) {
                        // White to Pure Hue horizontal gradient
                        drawRect(
                            brush = Brush.horizontalGradient(listOf(Color.White, pureHueColor))
                        )
                        // Transparent to Black vertical gradient
                        drawRect(
                            brush = Brush.verticalGradient(listOf(Color.Transparent, Color.Black))
                        )

                        // Selector point
                        val cx = sat * size.width
                        val cy = (1f - value) * size.height
                        drawCircle(color = Color.Black, radius = 9.dp.toPx(), center = Offset(cx, cy))
                        drawCircle(color = Color.White, radius = 7.dp.toPx(), center = Offset(cx, cy))
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 2. 1D Rainbow Hue Slider Bar
                Text(
                    text = "Tono (${hue.toInt()}°)",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Start)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .pointerInput(Unit) {
                            detectTapGestures { offset ->
                                hue = (offset.x / size.width * 360f).coerceIn(0f, 360f)
                                updateHexFromHsv()
                            }
                        }
                        .pointerInput(Unit) {
                            detectDragGestures { change, _ ->
                                hue = (change.position.x / size.width * 360f).coerceIn(0f, 360f)
                                updateHexFromHsv()
                            }
                        }
                ) {
                    val rainbow = listOf(
                        Color(0xFFFF0000),
                        Color(0xFFFFFF00),
                        Color(0xFF00FF00),
                        Color(0xFF00FFFF),
                        Color(0xFF0000FF),
                        Color(0xFFFF00FF),
                        Color(0xFFFF0000)
                    )
                    Canvas(modifier = Modifier.matchParentSize()) {
                        drawRect(brush = Brush.horizontalGradient(rainbow))
                        val hx = (hue / 360f) * size.width
                        drawLine(
                            color = Color.White,
                            start = Offset(hx, 0f),
                            end = Offset(hx, size.height),
                            strokeWidth = 3.dp.toPx()
                        )
                        drawCircle(color = Color.Black, radius = 5.dp.toPx(), center = Offset(hx, size.height / 2))
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 3. Live Swatch & Editable HEX
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Swatch
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(currentColor)
                            .border(2.dp, MaterialTheme.colorScheme.outline, CircleShape)
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    // HEX Field
                    OutlinedTextField(
                        value = hexText,
                        onValueChange = { input ->
                            val clean = if (input.startsWith("#")) input else "#$input"
                            hexText = clean.uppercase()
                            if (clean.length == 7) {
                                updateHsvFromHex(clean)
                            }
                        },
                        label = { Text("Código HEX") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("hex_color_input")
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalHex = if (hexText.startsWith("#") && hexText.length == 7) {
                        hexText
                    } else {
                        String.format("#%06X", 0xFFFFFF and AndroidColor.HSVToColor(floatArrayOf(hue, sat, value)))
                    }
                    onConfirm(finalHex)
                },
                modifier = Modifier.testTag("confirm_color_button")
            ) {
                Text("Aceptar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}
