package com.example.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.BuildConfig
import com.example.data.broadcast.LocalBroadcastState

/** Step-by-step help and live status for the "Apps locales" source. */
@Composable
fun LocalBroadcastHelpCard() {
    val lastInfo by LocalBroadcastState.lastInfo.collectAsState()

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Cómo conectar otras apps", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "En la otra app hay que decirle que envíe la glucosa a esta app. El nombre de paquete de esta app es:",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                BuildConfig.APPLICATION_ID,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                modifier = Modifier.padding(vertical = 4.dp)
            )
            Text(
                "• xDrip+: Ajustes → Ajustes entre apps (Inter-app settings) → «Transmitir localmente» (Broadcast locally) activado, " +
                    "y en «Identificar receptor» (Identify receiver) escribe el paquete de arriba.\n" +
                    "• Juggluco: menú izquierdo → Ajustes → activa «xDrip+ broadcast» (o «Glucodata broadcast») y añade el paquete de arriba como receptor.\n" +
                    "• Dexcom BYODA: activa el envío de glucosa a otras apps (broadcast) en su configuración.\n" +
                    "Los nombres exactos pueden variar según la versión de cada app.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text("Estado", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Text(lastInfo, fontSize = 13.sp)
        }
    }
}
