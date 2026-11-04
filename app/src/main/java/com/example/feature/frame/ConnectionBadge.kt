package com.example.feature.frame

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.designsystem.GlucoseColors
import com.example.core.model.SyncResult
import com.example.sync.SyncStatus

@Composable
fun ConnectionBadge(
    status: SyncStatus,
    clockSkewDetected: Boolean,
    isSimulated: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Simulated fixed badge if debug simulation active
        if (isSimulated) {
            Box(
                modifier = Modifier
                    .background(Color(0xFFE91E63), RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
                    .testTag("simulated_badge")
            ) {
                Text(
                    text = "SIMULADO",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
        }

        // Clock skew warning badge
        if (clockSkewDetected) {
            Box(
                modifier = Modifier
                    .background(GlucoseColors.StatusAmber, RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = "Reloj de la tablet descuadrado",
                    color = Color.Black,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
        }

        // Connection dot & message
        val dotColor = when {
            status.isSyncing -> Color(0xFF00E5FF)
            status.lastResult == SyncResult.OK -> Color(0xFF00E676)
            status.rateLimitRemainingSeconds > 0 -> GlucoseColors.StatusAmber
            status.lastResult == SyncResult.TERMS_PENDING -> GlucoseColors.StatusAmber
            else -> Color(0xFFFF1744)
        }

        val textToShow = when {
            status.isSyncing -> "Sincronizando..."
            status.rateLimitRemainingSeconds > 0 -> "429: Reintento en ${status.rateLimitRemainingSeconds}s"
            status.lastResult != SyncResult.OK -> status.lastResult.userMessageEs
            else -> "En línea"
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(dotColor, CircleShape)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = textToShow,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
