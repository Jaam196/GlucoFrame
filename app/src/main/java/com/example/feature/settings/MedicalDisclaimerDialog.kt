package com.example.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun MedicalDisclaimerDialog(
    onAccept: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { /* Must explicitly accept */ },
        title = {
            Text(
                text = "Aviso médico importante",
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "GlucoseFrame es un marco digital pasivo secundario.",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "• NO sustituye a la app oficial de Abbott (FreeStyle LibreLink / LibreLinkUp) ni a sus alarmas sonoras o críticas de glucosa.\n" +
                        "• NO debe usarse como única fuente para tomar decisiones de tratamiento o dosificación de insulina.\n" +
                        "• Las lecturas mostradas pueden sufrir retrasos por falta de conectividad en el teléfono del usuario o el sensor.\n" +
                        "• Consulta y valida siempre cualquier ajuste de rangos con tu equipo médico especialista.",
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Licencias y atribución:",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Basado en la lógica del protocolo de conexión de GlucoDataHandler (Copyright © 2023 Michael Pach, Licencia MIT).",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onAccept,
                modifier = Modifier.testTag("accept_disclaimer_button")
            ) {
                Text("He leído y acepto el aviso")
            }
        }
    )
}
