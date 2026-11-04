package com.example.feature.settings

import android.app.Activity
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.SettingsBrightness
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.common.GlucoseUtils
import com.example.core.designsystem.GlucoseColors
import com.example.core.model.AccessGesture
import com.example.core.model.ActiveSourceType
import com.example.core.model.AppThemeMode
import com.example.core.model.GlucoseUnit
import com.example.core.model.OrientationOption
import com.example.core.model.SyncEvent
import com.example.core.model.SyncResult
import com.example.core.model.ThresholdSettings

enum class SettingsTab(val title: String, val icon: ImageVector) {
    STATUS("Estado", Icons.Default.Home),
    SOURCE("Fuente", Icons.Default.Cloud),
    LIBRELINK("LibreLinkUp", Icons.Default.MedicalServices),
    NIGHTSCOUT("Nightscout / Juggluco", Icons.Default.Science),
    THRESHOLDS("Rangos", Icons.Default.Tune),
    APPEARANCE("Apariencia", Icons.Default.ColorLens),
    SCREEN("Pantalla", Icons.Default.Tv),
    STATUS_BAR_WIDGET("Barra y widget", Icons.Default.Widgets),
    KIOSK("Permanente / Vivo", Icons.Default.BatteryChargingFull),
    HISTORY("Historial", Icons.Default.History),
    DIAGNOSTICS("Diagnóstico", Icons.Default.NetworkCheck),
    ABOUT("Acerca de", Icons.Default.Info)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    viewModel: SettingsViewModel,
    onBackToFrame: () -> Unit,
    onNavigateToBackgroundSettings: () -> Unit = {},
    onNavigateToDisplayStyle: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? Activity
    BackHandler { onBackToFrame() }

    var selectedTab by remember { mutableStateOf(SettingsTab.STATUS) }

    val csvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri: Uri? ->
        uri?.let { viewModel.exportCsv(it) { success -> } }
    }

    Column(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // Top Bar
        TopAppBar(
            title = {
                Text("Configuración de GlucoseFrame", fontWeight = FontWeight.Bold)
            },
            navigationIcon = {
                IconButton(
                    onClick = onBackToFrame,
                    modifier = Modifier.testTag("back_to_frame_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver al marco")
                }
            },
            actions = {
                Button(
                    onClick = onBackToFrame,
                    modifier = Modifier.padding(end = 12.dp).testTag("return_frame_fab")
                ) {
                    Text("Volver al marco")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        )

        if (state.credentialsUnreadable) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .testTag("banner_credentials_unreadable"),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                )
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = "Alerta",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "No se pudieron recuperar las credenciales guardadas. Vuelve a introducirlas.",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        if (state.preferences.pinEnabled && state.credentials.pinHash.isBlank()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .testTag("banner_pin_lost"),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                )
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = "Alerta PIN",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "El PIN se perdió. Configura uno nuevo.",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // Responsive layout: phones (< 600dp wide) get a scrollable tab row on top,
        // tablets get a vertically scrollable navigation rail on the left.
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isCompactWidth = maxWidth < 600.dp

            val tabContent: @Composable () -> Unit = {
                when (selectedTab) {
                    SettingsTab.STATUS -> StatusTab(state, onBackToFrame, { viewModel.testConnection() })
                    SettingsTab.SOURCE -> SourceTab(state, viewModel)
                    SettingsTab.LIBRELINK -> LibreLinkUpTab(state, viewModel)
                    SettingsTab.NIGHTSCOUT -> NightscoutTab(state, viewModel)
                    SettingsTab.THRESHOLDS -> ThresholdsTab(state, viewModel)
                    SettingsTab.APPEARANCE -> AppearanceTab(state, viewModel, onNavigateToBackgroundSettings, onNavigateToDisplayStyle)
                    SettingsTab.SCREEN -> ScreenTab(state, viewModel)
                    SettingsTab.STATUS_BAR_WIDGET -> StatusBarWidgetSettingsTab(state, viewModel, activity)
                    SettingsTab.KIOSK -> KioskTab(state, viewModel, activity)
                    SettingsTab.HISTORY -> HistoryTab(state, viewModel) { csvLauncher.launch("glucose_history_${System.currentTimeMillis()}.csv") }
                    SettingsTab.DIAGNOSTICS -> DiagnosticsTab(state)
                    SettingsTab.ABOUT -> AboutTab()
                }
            }

            if (isCompactWidth) {
                Column(modifier = Modifier.fillMaxSize()) {
                    ScrollableTabRow(
                        selectedTabIndex = selectedTab.ordinal,
                        edgePadding = 8.dp,
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        for (tab in SettingsTab.values()) {
                            Tab(
                                selected = selectedTab == tab,
                                onClick = { selectedTab = tab },
                                text = { Text(tab.title, fontSize = 13.sp, maxLines = 1) },
                                icon = { Icon(tab.icon, contentDescription = tab.title) },
                                modifier = Modifier.testTag("nav_tab_${tab.name.lowercase()}")
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(12.dp)
                    ) {
                        tabContent()
                    }
                }
            } else {
                Row(modifier = Modifier.fillMaxSize()) {
                    // Tablet Navigation Rail (scrollable so all 12 tabs stay reachable on short screens)
                    NavigationRail(
                        modifier = Modifier.fillMaxHeight(),
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .verticalScroll(rememberScrollState()),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            for (tab in SettingsTab.values()) {
                                NavigationRailItem(
                                    selected = selectedTab == tab,
                                    onClick = { selectedTab = tab },
                                    icon = { Icon(tab.icon, contentDescription = tab.title) },
                                    label = { Text(tab.title, fontSize = 10.sp, maxLines = 1) },
                                    modifier = Modifier.testTag("nav_tab_${tab.name.lowercase()}")
                                )
                            }
                        }
                    }

                    // Tab Content Pane
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(16.dp)
                    ) {
                        tabContent()
                    }
                }
            }
        }
    }
}

@Composable
fun StatusTab(
    state: SettingsUiState,
    onBackToFrame: () -> Unit,
    onTestConnection: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Estado actual del marco", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(12.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    val dotColor = if (state.syncStatus.lastResult == SyncResult.OK) Color(0xFF00E676) else Color(0xFFFF1744)
                    Box(modifier = Modifier.size(14.dp).background(dotColor, CircleShape))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Fuente activa: ${state.preferences.activeSource.displayName}",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Último estado: ${state.syncStatus.lastResult.userMessageEs}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (state.syncStatus.lastMessage.isNotBlank()) {
                    Text(
                        text = state.syncStatus.lastMessage,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(16.dp))

                if (state.latestReading != null) {
                    Text(
                        text = "Última lectura: ${GlucoseUtils.formatValue(state.latestReading.valueMgDl, state.preferences.unit)} ${state.preferences.unit.label} (${state.latestReading.trend.symbol})",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = GlucoseColors.colorForZone(state.preferences.thresholds.zoneFor(state.latestReading.valueMgDl))
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Hora de medición: ${GlucoseUtils.formatFullDateTime(state.latestReading.measuredAtUtcMs)}",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text("No hay lecturas registradas en la base de datos.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                Spacer(modifier = Modifier.height(20.dp))
                Row {
                    Button(
                        onClick = onTestConnection,
                        modifier = Modifier.testTag("test_connection_btn")
                    ) {
                        if (state.testConnectionRunning) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text("Comprobar conexión ahora")
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    OutlinedButton(onClick = onBackToFrame) {
                        Text("Ver marco en pantalla completa")
                    }
                }

                if (state.testConnectionMessage != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = state.testConnectionMessage,
                            modifier = Modifier.padding(12.dp),
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SourceTab(
    state: SettingsUiState,
    viewModel: SettingsViewModel
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Text("Selección de Fuente de Datos", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Elige la fuente de lecturas de glucosa. Room es la única fuente de verdad para la interfaz.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 14.sp
        )
        Spacer(modifier = Modifier.height(16.dp))

        val sources = listOf(
            ActiveSourceType.LIBRELINKUP,
            ActiveSourceType.NIGHTSCOUT,
            ActiveSourceType.JUGGLUCO,
            ActiveSourceType.LOCAL_BROADCAST
        )

        for (src in sources) {
            val isSelected = state.preferences.activeSource == src
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .clickable { viewModel.setActiveSource(src) },
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = isSelected,
                        onClick = { viewModel.setActiveSource(src) }
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(src.displayName, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text(
                            text = when (src) {
                                ActiveSourceType.LIBRELINKUP -> "Conexión en la nube con protocolo LibreLinkUp (experimental, FreeStyle Libre 2 Plus)."
                                ActiveSourceType.NIGHTSCOUT -> "Servidor Nightscout mediante HTTPS y token/secreto de solo lectura."
                                ActiveSourceType.JUGGLUCO -> "Servidor local HTTP de Juggluco en la red privada de tu hogar."
                                ActiveSourceType.LOCAL_BROADCAST -> "Recibe las lecturas directamente de xDrip+, Juggluco, Diabox o Dexcom BYODA instalados en este mismo dispositivo, sin internet."
                                else -> ""
                            },
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        if (state.preferences.activeSource == ActiveSourceType.LOCAL_BROADCAST) {
            Spacer(modifier = Modifier.height(8.dp))
            LocalBroadcastHelpCard()
        }

        if (state.preferences.activeSource == ActiveSourceType.LIBRELINKUP) {
            Spacer(modifier = Modifier.height(8.dp))
            SensorInfoCard()
        }

        // This device can also serve its readings to other devices on the same Wi-Fi
        Spacer(modifier = Modifier.height(8.dp))
        LanServerCard(state, viewModel)

        // Debug Simulated Option (only in debug builds)
        if (com.example.BuildConfig.DEBUG) {
            val isSimSelected = state.preferences.activeSource == ActiveSourceType.SIMULATED
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .clickable { viewModel.setActiveSource(ActiveSourceType.SIMULATED) },
                colors = CardDefaults.cardColors(
                    containerColor = if (isSimSelected) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surface
                )
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = isSimSelected, onClick = { viewModel.setActiveSource(ActiveSourceType.SIMULATED) })
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("Simulador de Glucosa (Solo Debug)", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                        Text("Genera datos ficticios para probar el marco. Muestra una insignia roja 'SIMULADO'.", fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun LibreLinkUpTab(
    state: SettingsUiState,
    viewModel: SettingsViewModel
) {
    var email by remember(state.credentials.libreEmail) { mutableStateOf(state.credentials.libreEmail) }
    var password by remember(state.credentials.librePassword) { mutableStateOf(state.credentials.librePassword) }

    var domain by remember(state.preferences.lluDomain) { mutableStateOf(state.preferences.lluDomain) }
    var region by remember(state.preferences.lluRegion) { mutableStateOf(state.preferences.lluRegion) }
    var product by remember(state.preferences.lluProduct) { mutableStateOf(state.preferences.lluProduct) }
    var version by remember(state.preferences.lluVersion) { mutableStateOf(state.preferences.lluVersion) }
    var autoTerms by remember(state.preferences.lluAutoAcceptTerms) { mutableStateOf(state.preferences.lluAutoAcceptTerms) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Text("Configuración de LibreLinkUp (Seguidor)", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(6.dp))
        Surface(
            color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Fuente etiquetada como EXPERIMENTAL. Usa el protocolo de la comunidad v4.x.",
                modifier = Modifier.padding(10.dp),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Important Warning: Exclusive Account Rule
        Surface(
            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Warning, contentDescription = "Aviso", tint = MaterialTheme.colorScheme.error)
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Aviso importante: La cuenta de seguidor debe ser exclusiva de esta tablet. Si GlucoDataHandler o la app LibreLinkUp usan la misma cuenta a la vez, Abbott puede devolver HTTP 429 o error de autenticación.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Onboarding / Setup Guide
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("Guía de alta de la cuenta de seguidor:", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "1. En la app FreeStyle Libre del portador del sensor: Menú → Conectar/Compartir → LibreLinkUp → Añadir conexión (indica nombre y correo del seguidor).\n" +
                        "2. El seguidor instala LibreLinkUp, crea la cuenta en el mismo país, acepta la invitación por email o en la app.\n" +
                        "3. Muy importante: Cierra sesión en la app LibreLinkUp del móvil para no generar conflictos de sesión.\n" +
                        "4. Introduce esas credenciales aquí abajo.",
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Correo de la cuenta de seguidor") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("llu_email_input")
        )
        Spacer(modifier = Modifier.height(10.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Contraseña de la cuenta de seguidor") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("llu_password_input")
        )
        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = {
                viewModel.saveLibreCredentials(email, password)
                viewModel.testConnection()
            },
            modifier = Modifier.testTag("save_and_test_llu_btn")
        ) {
            Text("Guardar credenciales y comprobar conexión")
        }

        // Connection / Patient selector
        if (state.availablePatients.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Text("Paciente / Conexión activa:", fontWeight = FontWeight.Bold)
            for (p in state.availablePatients) {
                val isSelected = state.credentials.librePatientId == p.first
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.selectLibrePatient(p.first, p.second) }
                        .padding(vertical = 4.dp)
                ) {
                    RadioButton(
                        selected = isSelected,
                        onClick = { viewModel.selectLibrePatient(p.first, p.second) }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(p.second, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        Text("Ajustes avanzados de protocolo LibreLinkUp", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Text("Modificables sin recompilar ante futuros cambios de Abbott.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(10.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = domain,
                onValueChange = { domain = it },
                label = { Text("Dominio (ej. io)") },
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(10.dp))
            OutlinedTextField(
                value = region,
                onValueChange = { region = it },
                label = { Text("Región (ej. de, es, eu)") },
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(modifier = Modifier.height(10.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = product,
                onValueChange = { product = it },
                label = { Text("Product Header") },
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(10.dp))
            OutlinedTextField(
                value = version,
                onValueChange = { version = it },
                label = { Text("Versión Header") },
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(modifier = Modifier.height(10.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(
                checked = autoTerms,
                onCheckedChange = { autoTerms = it }
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text("Aceptar términos (TOU/PP) automáticamente", fontSize = 14.sp)
        }
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedButton(
            onClick = {
                viewModel.setLluAdvanced(domain, region, product, version, autoTerms)
            }
        ) {
            Text("Guardar ajustes avanzados")
        }
    }
}

@Composable
fun NightscoutTab(
    state: SettingsUiState,
    viewModel: SettingsViewModel
) {
    var nsUrl by remember(state.credentials.nightscoutUrl) { mutableStateOf(state.credentials.nightscoutUrl) }
    var nsToken by remember(state.credentials.nightscoutToken) { mutableStateOf(state.credentials.nightscoutToken) }
    var nsSecret by remember(state.credentials.nightscoutSecret) { mutableStateOf(state.credentials.nightscoutSecret) }

    var jugUrl by remember(state.credentials.jugglucoUrl) { mutableStateOf(state.credentials.jugglucoUrl) }
    var jugToken by remember(state.credentials.jugglucoToken) { mutableStateOf(state.credentials.jugglucoToken) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Text("Configuración de Nightscout", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = nsUrl,
            onValueChange = { nsUrl = it },
            label = { Text("URL de Nightscout (HTTPS obligatorio)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("nightscout_url_input")
        )
        Spacer(modifier = Modifier.height(10.dp))

        OutlinedTextField(
            value = nsToken,
            onValueChange = { nsToken = it },
            label = { Text("Token de solo lectura (Recomendado)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("nightscout_token_input")
        )
        Spacer(modifier = Modifier.height(10.dp))

        OutlinedTextField(
            value = nsSecret,
            onValueChange = { nsSecret = it },
            label = { Text("API Secret (Opcional)") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        if (nsSecret.isNotBlank() && nsToken.isBlank()) {
            Text(
                text = "Aviso: Se recomienda usar un token de solo lectura en lugar del secreto de administrador.",
                color = GlucoseColors.StatusAmber,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
        Button(
            onClick = {
                viewModel.saveNightscoutCredentials(nsUrl, nsToken, nsSecret)
                viewModel.testConnection()
            }
        ) {
            Text("Guardar y comprobar Nightscout")
        }

        Spacer(modifier = Modifier.height(28.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(16.dp))

        Text("Juggluco Local (Servidor Web Local)", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(6.dp))

        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "Aviso de Juggluco:\n" +
                        "• Requiere que Juggluco sea quien lee el sensor en el móvil, en lugar de la app oficial de Abbott.\n" +
                        "• Solo admite direcciones IP privadas en tu red Wi-Fi (192.168.x.x, 10.x.x.x, 172.16–31.x.x).\n" +
                        "• Consulta primero /sgv.json y tiene respaldo a /api/v1/entries/current.json.",
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = jugUrl,
            onValueChange = { jugUrl = it },
            label = { Text("URL local de Juggluco (ej. http://192.168.1.120:17580)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("juggluco_url_input")
        )
        Spacer(modifier = Modifier.height(10.dp))

        OutlinedTextField(
            value = jugToken,
            onValueChange = { jugToken = it },
            label = { Text("Token de Juggluco (si está configurado)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = {
                viewModel.saveJugglucoCredentials(jugUrl, jugToken)
                viewModel.testConnection()
            }
        ) {
            Text("Guardar y comprobar Juggluco Local")
        }

        Spacer(modifier = Modifier.height(20.dp))
        Text("Otro dispositivo con GlucoseFrame", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text(
            "Elige el dispositivo principal y escribe su código de acceso en el campo «Token» de arriba.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        LanDiscoveryCard(onPick = { picked -> jugUrl = picked })
    }
}

@Composable
fun ThresholdsTab(
    state: SettingsUiState,
    viewModel: SettingsViewModel
) {
    var urgentLow by remember(state.preferences.thresholds.urgentLowMgDl) { mutableIntStateOf(state.preferences.thresholds.urgentLowMgDl) }
    var low by remember(state.preferences.thresholds.lowMgDl) { mutableIntStateOf(state.preferences.thresholds.lowMgDl) }
    var targetRef by remember(state.preferences.thresholds.targetReferenceMgDl) { mutableIntStateOf(state.preferences.thresholds.targetReferenceMgDl) }
    var high by remember(state.preferences.thresholds.highMgDl) { mutableIntStateOf(state.preferences.thresholds.highMgDl) }
    var veryHigh by remember(state.preferences.thresholds.veryHighMgDl) { mutableIntStateOf(state.preferences.thresholds.veryHighMgDl) }
    var nearLow by remember(state.preferences.thresholds.nearLowMgDl) { mutableIntStateOf(state.preferences.thresholds.nearLowMgDl) }
    var elevated by remember(state.preferences.thresholds.elevatedMgDl) { mutableIntStateOf(state.preferences.thresholds.elevatedMgDl) }

    var delayedMin by remember(state.preferences.thresholds.delayedMinutes) { mutableIntStateOf(state.preferences.thresholds.delayedMinutes) }
    var obsoleteMin by remember(state.preferences.thresholds.obsoleteMinutes) { mutableIntStateOf(state.preferences.thresholds.obsoleteMinutes) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Text("Unidades y Rangos de Glucosa", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Ajusta estos límites con tu equipo médico.",
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(16.dp))

        // Unit selector
        Text("Unidad de visualización:", fontWeight = FontWeight.SemiBold)
        Row(modifier = Modifier.padding(vertical = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = state.preferences.unit == GlucoseUnit.MG_DL,
                    onClick = { viewModel.setUnit(GlucoseUnit.MG_DL) }
                )
                Text("mg/dL", modifier = Modifier.padding(start = 4.dp, end = 16.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = state.preferences.unit == GlucoseUnit.MMOL_L,
                    onClick = { viewModel.setUnit(GlucoseUnit.MMOL_L) }
                )
                Text("mmol/L (mg/dL ÷ 18,0182)", modifier = Modifier.padding(start = 4.dp))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text("Límites de zona glucémica (en mg/dL):", fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(8.dp))

        ThresholdSliderItem("Bajo urgente (< $urgentLow mg/dL)", urgentLow, 40..65) { urgentLow = it }
        ThresholdSliderItem("Bajo ($urgentLow a $low mg/dL)", low, 60..85) { low = it }
        ThresholdSliderItem("Casi bajo ($low a $nearLow mg/dL)", nearLow, 75..110) { nearLow = it }
        ThresholdSliderItem("En rango ($nearLow a $elevated mg/dL)", elevated, 110..180) { elevated = it }
        ThresholdSliderItem("Línea de referencia del gráfico ($targetRef mg/dL)", targetRef, 80..130) { targetRef = it }
        ThresholdSliderItem("Algo alto ($elevated a $high mg/dL)", high, 140..220) { high = it }
        Text(
            "Alto ($high a $veryHigh mg/dL):",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        ThresholdSliderItem("Muy alto (> $veryHigh mg/dL)", veryHigh, 200..320) { veryHigh = it }

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(16.dp))

        Text("Modelo de Frescura y Seguridad:", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Text("Define cuándo una lectura se considera retrasada u obsoleta según su edad real.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(8.dp))

        ThresholdSliderItem("Retrasada tras: $delayedMin min (banda ámbar)", delayedMin, 3..8) { delayedMin = it }
        ThresholdSliderItem("Obsoleta tras: $obsoleteMin min (número desaparece, máx 15m)", obsoleteMin, 8..15) { obsoleteMin = it.coerceAtMost(15) }

        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = {
                viewModel.setThresholds(
                    ThresholdSettings(
                        urgentLowMgDl = urgentLow,
                        lowMgDl = low,
                        targetReferenceMgDl = targetRef,
                        highMgDl = high,
                        veryHighMgDl = veryHigh,
                        nearLowMgDl = nearLow,
                        elevatedMgDl = elevated,
                        delayedMinutes = delayedMin,
                        obsoleteMinutes = obsoleteMin.coerceAtMost(15)
                    )
                )
            }
        ) {
            Text("Guardar umbrales y frescura")
        }
    }
}

@Composable
fun ThresholdSliderItem(label: String, value: Int, range: IntRange, onChange: (Int) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, fontSize = 13.sp)
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.toInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = range.last - range.first - 1
        )
    }
}

@Composable
fun AppearanceTab(
    state: SettingsUiState,
    viewModel: SettingsViewModel,
    onNavigateToBackgroundSettings: () -> Unit = {},
    onNavigateToDisplayStyle: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Text("Temas y Apariencia", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(12.dp))

        // Personalización de fondos entry
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .clickable { onNavigateToBackgroundSettings() }
                .testTag("nav_to_background_settings"),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ),
            shape = RoundedCornerShape(14.dp)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.ColorLens,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Personalización de fondos",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        "Fondos dinámicos vinculados a la glucosa, biblioteca, carpeta y colores inteligentes.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Estilo de pantalla entry (number, chart, clock, widgets, colors by glucose level)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .clickable { onNavigateToDisplayStyle() }
                .testTag("nav_to_display_style"),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ),
            shape = RoundedCornerShape(14.dp)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Estilo de pantalla",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        "Colores por nivel de azúcar, número, gráfica, reloj y widgets, con vista previa.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text("Tema de la interfaz:", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(8.dp))

        val themes = listOf(
            AppThemeMode.DARK,
            AppThemeMode.AMOLED,
            AppThemeMode.LIGHT,
            AppThemeMode.MINIMALIST,
            AppThemeMode.NIGHT
        )

        for (t in themes) {
            val isSelected = state.preferences.themeMode == t
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clickable { viewModel.setThemeMode(t) },
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                )
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = isSelected, onClick = { viewModel.setThemeMode(t) })
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(t.displayName, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text(
                            text = when (t) {
                                AppThemeMode.DARK -> "Fondo azul marino/pizarra oscuro con acentos brillantes."
                                AppThemeMode.AMOLED -> "Fondo negro puro #000000 para máximo contraste."
                                AppThemeMode.LIGHT -> "Fondo blanco médico suave y nítido para entornos luminosos."
                                AppThemeMode.MINIMALIST -> "Monocromático austero donde solo la glucosa aporta color."
                                AppThemeMode.NIGHT -> "Tonalidad cálida y atenuada sin espectro azul para la mesita de noche."
                            },
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ScreenTab(
    state: SettingsUiState,
    viewModel: SettingsViewModel
) {
    var orientation by remember(state.preferences.orientation) { mutableStateOf(state.preferences.orientation) }
    var pixelShift by remember(state.preferences.pixelShiftEnabled) { mutableStateOf(state.preferences.pixelShiftEnabled) }

    var dimEnabled by remember(state.preferences.dimmingScheduleEnabled) { mutableStateOf(state.preferences.dimmingScheduleEnabled) }
    var startH by remember(state.preferences.dimmingStartHour) { mutableIntStateOf(state.preferences.dimmingStartHour) }
    var endH by remember(state.preferences.dimmingEndHour) { mutableIntStateOf(state.preferences.dimmingEndHour) }
    var blackScreen by remember(state.preferences.dimmingUseBlackScreen) { mutableStateOf(state.preferences.dimmingUseBlackScreen) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Text("Ajustes de Pantalla", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(14.dp))

        Text("Orientación de pantalla fija:", fontWeight = FontWeight.SemiBold)
        Row(modifier = Modifier.padding(vertical = 4.dp)) {
            for (opt in OrientationOption.values()) {
                val isSel = orientation == opt
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 12.dp)) {
                    RadioButton(selected = isSel, onClick = {
                        orientation = opt
                        viewModel.setOrientation(opt)
                    })
                    Text(opt.displayName, fontSize = 14.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(
                checked = pixelShift,
                onCheckedChange = {
                    pixelShift = it
                    viewModel.setPixelShift(it)
                }
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text("Desplazamiento sutil de píxeles (Anti-quemado)", fontWeight = FontWeight.Medium)
                Text("Desplaza suavemente la interfaz 1-2 px cada pocos minutos.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(16.dp))

        Text("Horario de Atenuación Nocturna", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = dimEnabled, onCheckedChange = { dimEnabled = it })
            Spacer(modifier = Modifier.width(10.dp))
            Text("Activar atenuación nocturna programada")
        }

        if (dimEnabled) {
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Hora inicio: ${String.format("%02d:00", startH)}")
                    Slider(value = startH.toFloat(), onValueChange = { startH = it.toInt() }, valueRange = 0f..23f, steps = 22)
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Hora fin: ${String.format("%02d:00", endH)}")
                    Slider(value = endH.toFloat(), onValueChange = { endH = it.toInt() }, valueRange = 0f..23f, steps = 22)
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = blackScreen, onCheckedChange = { blackScreen = it })
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text("Modo Pantalla Negra Total (despierta con un toque)", fontWeight = FontWeight.Medium)
                    Text("En pantallas LCD como tablets convencionales la retroiluminación permanece encendida.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            OutlinedButton(
                onClick = {
                    viewModel.setDimmingSchedule(dimEnabled, startH, 0, endH, 0, blackScreen)
                }
            ) {
                Text("Guardar horario nocturno")
            }
        }
    }
}

@Composable
fun KioskTab(
    state: SettingsUiState,
    viewModel: SettingsViewModel,
    activity: Activity?
) {
    var keepScreenOn by remember(state.preferences.keepScreenOn) { mutableStateOf(state.preferences.keepScreenOn) }
    var onlyCharging by remember(state.preferences.keepScreenOnOnlyCharging) { mutableStateOf(state.preferences.keepScreenOnOnlyCharging) }
    var syncInterval by remember(state.preferences.syncIntervalSeconds) { mutableIntStateOf(state.preferences.syncIntervalSeconds) }
    var kioskAlias by remember(state.preferences.kioskHomeEnabled) { mutableStateOf(state.preferences.kioskHomeEnabled) }
    var pinValue by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Text("Funcionamiento Permanente y Modo Kiosco", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(14.dp))

        // Screen Always On
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(
                checked = keepScreenOn,
                onCheckedChange = {
                    keepScreenOn = it
                    viewModel.setKeepScreenOn(it, onlyCharging)
                }
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text("Mantener pantalla encendida siempre", fontWeight = FontWeight.SemiBold)
                Text("Usa FLAG_KEEP_SCREEN_ON para que la tablet nunca se apague.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        if (keepScreenOn) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 32.dp)) {
                Switch(
                    checked = onlyCharging,
                    onCheckedChange = {
                        onlyCharging = it
                        viewModel.setKeepScreenOn(keepScreenOn, it)
                    }
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text("Solo mientras la tablet está cargando", fontSize = 13.sp)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(16.dp))

        // Battery Optimization
        Text("Optimización de Batería (Sin Restricciones):", fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = if (state.isBatteryOptimizationIgnored) "Estado: La app ya está exenta de ahorro de batería." else "Estado: Pendiente de exención. Para evitar cierres en segundo plano, solicita exención.",
            fontSize = 13.sp,
            color = if (state.isBatteryOptimizationIgnored) Color(0xFF00E676) else GlucoseColors.StatusAmber
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row {
            if (activity != null) {
                Button(onClick = { viewModel.requestIgnoreBatteryOptimization(activity) }) {
                    Text("Solicitar ignorar ahorro de batería")
                }
                Spacer(modifier = Modifier.width(10.dp))
                OutlinedButton(onClick = { viewModel.openAppSystemSettings(activity) }) {
                    Text("Abrir ajustes del sistema")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Manufacturer Guide (Vivo / Xiaomi / Samsung / etc)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("Guía especial para tablets Vivo y otros fabricantes:", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "En tablets como Vivo (Funtouch OS) o Xiaomi (HyperOS):\n" +
                        "1. Ajustes → Aplicaciones → GlucoseFrame → Batería → Selecciona 'Sin restricciones'.\n" +
                        "2. En 'Gestión de inicio': Activa 'Inicio automático' y 'Asociación de inicio'.\n" +
                        "3. En la vista de apps recientes, bloquea la app con el candado para que el limpiador de memoria no la cierre.",
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(16.dp))

        // Home Launcher Kiosk Option
        Text("Usar como pantalla de inicio (Lanzador):", fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(
                checked = kioskAlias,
                onCheckedChange = {
                    kioskAlias = it
                    viewModel.toggleKioskHomeLauncher(it)
                }
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text("Activar lanzador de inicio dedicado", fontWeight = FontWeight.Medium)
                Text("Habilita el componente Home para que GlucoseFrame pueda elegirse como pantalla de inicio en Ajustes del sistema.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Interval
        Text("Intervalo de actualización en red: $syncInterval s", fontWeight = FontWeight.SemiBold)
        Slider(
            value = syncInterval.toFloat(),
            onValueChange = {
                syncInterval = it.toInt()
                viewModel.setSyncInterval(syncInterval)
            },
            valueRange = 60f..300f,
            steps = 4
        )

        Spacer(modifier = Modifier.height(16.dp))

        // PIN protection
        Text("Protección de acceso con PIN (Opcional):", fontWeight = FontWeight.SemiBold)
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = pinValue,
                onValueChange = { pinValue = it },
                label = { Text("Nuevo PIN (vacío para desactivar)") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Button(onClick = { viewModel.savePin(pinValue) }) {
                Text("Guardar PIN")
            }
        }
    }
}

@Composable
fun HistoryTab(
    state: SettingsUiState,
    viewModel: SettingsViewModel,
    onExportCsv: () -> Unit
) {
    var selectedDays by remember { mutableIntStateOf(7) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Histórico y Estadísticas", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Button(onClick = onExportCsv) {
                Icon(Icons.Default.Download, contentDescription = "CSV")
                Spacer(modifier = Modifier.width(6.dp))
                Text("Exportar a CSV")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Window selector (1 día, 3 días, 7 días)
        Row {
            listOf(1, 3, 7).forEach { d ->
                FilterChip(
                    selected = selectedDays == d,
                    onClick = {
                        selectedDays = d
                        viewModel.loadHistoryStats(d)
                    },
                    label = { Text(if (d == 1) "Últimas 24h" else "Últimos $d días") },
                    modifier = Modifier.padding(end = 8.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Stats Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Resumen en la ventana seleccionada:", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(modifier = Modifier.height(10.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Media de Glucosa", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = if (state.historyTotalCount > 0) GlucoseUtils.formatValue(state.historyAverageMgDl.toInt(), state.preferences.unit) + " " + state.preferences.unit.label else "—",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Tiempo en Rango (TIR)", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = if (state.historyTotalCount > 0) String.format(java.util.Locale.US, "%.1f%%", state.historyTimeInRangePercent) else "—",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = GlucoseColors.InRange
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Lecturas registradas", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = "${state.historyTotalCount}",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                    Text(
                        text = "Bajo: ${String.format(java.util.Locale.US, "%.1f%%", state.historyTimeBelowPercent)}",
                        color = GlucoseColors.Low,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Alto: ${String.format(java.util.Locale.US, "%.1f%%", state.historyTimeAbovePercent)}",
                        color = GlucoseColors.High,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text("Últimas 50 lecturas guardadas en Room:", fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(6.dp))

        val recent50 = state.historyReadings.takeLast(50).asReversed()
        for (r in recent50) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp, horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = GlucoseUtils.formatFullDateTime(r.measuredAtUtcMs),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${GlucoseUtils.formatValue(r.valueMgDl, state.preferences.unit)} ${state.preferences.unit.label} ${r.trend.symbol}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = GlucoseColors.colorForZone(state.preferences.thresholds.zoneFor(r.valueMgDl))
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
        }
    }
}

@Composable
fun DiagnosticsTab(state: SettingsUiState) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Text("Diagnóstico del Sistema y Registro de Sincronización", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(10.dp))

        // Last crash of the app (saved on the device only): useful to report why the app closed
        val crashContext = LocalContext.current
        var lastCrash by remember { mutableStateOf(com.example.core.common.CrashLogger.read(crashContext)) }
        if (lastCrash != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("La app se cerró por un error. Detalle (cópialo y envíalo para poder arreglarlo):", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(lastCrash.orEmpty(), fontSize = 10.sp, lineHeight = 13.sp)
                    TextButton(onClick = {
                        com.example.core.common.CrashLogger.clear(crashContext)
                        lastCrash = null
                    }) { Text("Borrar este aviso") }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("Información de diagnóstico (sin credenciales ni tokens expuestos):", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Text("• Versión de la app: 1.0 (GlucoseFrame)")
                Text("• Fuente activa: ${state.preferences.activeSource.name}")
                Text("• Región activa LibreLinkUp: ${state.credentials.libreRegion.ifBlank { "Detectando..." }}")
                val tokenExp = if (state.credentials.libreTokenExpiresMs > 0) GlucoseUtils.formatFullDateTime(state.credentials.libreTokenExpiresMs) else "No autenticado"
                Text("• Caducidad de sesión: $tokenExp")
                Text("• Desfase detectado con reloj servidor: ${if (state.preferences.clockSkewDetected) "DESCUADRADO (> 2 min)" else "Correcto"}")
                Text("• Eventos registrados en memoria: ${state.recentEvents.size}")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text("Últimos eventos de sincronización:", fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(6.dp))

        for (event in state.recentEvents.take(40)) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (event.result == SyncResult.OK) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    val dotColor = if (event.result == SyncResult.OK) Color(0xFF00E676) else Color(0xFFFF1744)
                    Box(modifier = Modifier.size(10.dp).background(dotColor, CircleShape))
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(event.result.name, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text(GlucoseUtils.formatFullDateTime(event.atUtcMs), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (event.message.isNotBlank()) {
                            Text(event.message, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AboutTab() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Text("Acerca de GlucoseFrame", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(12.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Aviso médico y descargo de responsabilidad", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "GlucoseFrame es un marco digital permanente secundario.\n\n" +
                        "1. NO sustituye las alarmas sonoras oficiales de la app de Abbott ni de ningún sistema médico.\n" +
                        "2. NO debe usarse como única fuente para tomar decisiones de tratamiento ni para calcular dosis de insulina.\n" +
                        "3. Las lecturas dependen de que el teléfono del portador envíe datos a la nube o a la red local. Si el sensor o el teléfono pierden señal, las lecturas aparecerán como RETRASADAS u OBSOLETAS.\n" +
                        "4. Consulta siempre a tu médico para cualquier ajuste en los límites de glucemia.",
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(16.dp))

                Text("Licencias y Atribución", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Lógica del protocolo de LibreLinkUp basada en el proyecto de código abierto GlucoDataHandler:\n" +
                        "Copyright (c) 2023 Michael Pach\n" +
                        "Licencia MIT (ver NOTICE.md y LICENSE en el código fuente).\n\n" +
                        "Desarrollado para tablets Android en Kotlin, Jetpack Compose y Material Design 3.",
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            }
        }
    }
}
