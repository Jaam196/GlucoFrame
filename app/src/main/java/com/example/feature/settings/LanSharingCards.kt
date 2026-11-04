package com.example.feature.settings

import android.content.Context
import android.net.wifi.WifiManager
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.GlucoseFrameApplication
import java.net.Inet4Address
import java.net.NetworkInterface

/** First private IPv4 address of this device on the local network (null when not connected). */
private fun localIpAddress(context: Context): String? {
    try {
        val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
        for (network in interfaces) {
            if (!network.isUp || network.isLoopback) continue
            for (address in network.inetAddresses) {
                if (address is Inet4Address && !address.isLoopbackAddress && address.isSiteLocalAddress) {
                    return address.hostAddress
                }
            }
        }
    } catch (e: Exception) {
        // Fall through to Wi-Fi manager
    }
    try {
        @Suppress("DEPRECATION")
        val ip = (context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager)?.connectionInfo?.ipAddress ?: 0
        if (ip != 0) {
            return "${ip and 0xFF}.${(ip shr 8) and 0xFF}.${(ip shr 16) and 0xFF}.${(ip shr 24) and 0xFF}"
        }
    } catch (e: Exception) {
        // Ignore
    }
    return null
}

/** "Compartir con otros dispositivos": this device acts as the main one and serves its readings. */
@Composable
fun LanServerCard(
    state: SettingsUiState,
    viewModel: SettingsViewModel
) {
    val context = LocalContext.current
    val app = context.applicationContext as? GlucoseFrameApplication
    val status by (app?.lanServerController?.status
        ?: kotlinx.coroutines.flow.MutableStateFlow("Desactivado")).collectAsState()

    val prefs = state.preferences
    val ip = remember(prefs.lanServerEnabled) { if (prefs.lanServerEnabled) localIpAddress(context) else null }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Compartir con otros dispositivos", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(
                        "Este dispositivo recibe la glucosa del sensor y la ofrece por tu Wi-Fi. Las demás tablets o móviles " +
                            "eligen «Servidor local» como fuente y se conectan a este. Así solo uno habla con Abbott y no hay bloqueos.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = prefs.lanServerEnabled,
                    onCheckedChange = { viewModel.setLanServerEnabled(it) }
                )
            }

            if (prefs.lanServerEnabled) {
                Spacer(modifier = Modifier.height(10.dp))
                Text("Estado: $status", fontSize = 13.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "Dirección: " + (if (ip != null) "http://$ip:${prefs.lanServerPort}" else "conéctate a una red Wi-Fi"),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
                Text("Código de acceso: ${prefs.lanServerToken}", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text(
                    "En el otro dispositivo: Fuente de datos → Servidor local → «Buscar dispositivos» y escribe este código. " +
                        "Mientras esté activo se muestra una notificación fija para que Android no cierre el servidor.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
                TextButton(onClick = { viewModel.regenerateLanToken() }) {
                    Text("Generar un código nuevo", fontSize = 12.sp)
                }
            }
        }
    }
}

/** Looks for other GlucoseFrame devices on the Wi-Fi and lets the user pick one as the server. */
@Composable
fun LanDiscoveryCard(onPick: (String) -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as? GlucoseFrameApplication ?: return
    val discovery = app.lanServerController.discovery
    val devices by discovery.found.collectAsState()
    val searching by discovery.searching.collectAsState()

    DisposableEffect(Unit) {
        onDispose { discovery.stopSearch() }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(onClick = { discovery.startSearch() }) {
                Text(if (searching) "Buscando…" else "Buscar dispositivos GlucoseFrame")
            }
        }
        if (devices.isEmpty() && searching) {
            Text(
                "Buscando en tu Wi-Fi… (el otro dispositivo debe tener activado «Compartir con otros dispositivos»)",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
        for (device in devices) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPick(device.url) }
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(device.name, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                    Text(device.url, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("Usar", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
        }
    }
}
