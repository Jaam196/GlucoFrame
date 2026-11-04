package com.example.feature.background.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.common.BitmapUtils
import com.example.core.common.ColorAnalysisEngine
import com.example.core.database.background.BackgroundImageEntity
import com.example.core.model.BackgroundMode
import com.example.core.model.BackgroundPreferences
import com.example.core.model.CustomFolderOrder
import com.example.core.model.GlucoseZone

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackgroundSettingsScreen(
    state: BackgroundSettingsUiState,
    viewModel: BackgroundSettingsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenCommunity: () -> Unit = {}
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    BackHandler {
        onBack()
    }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(uri, takeFlags)
            } catch (e: Exception) {
                // Ignore
            }
            viewModel.onCustomFolderSelected(uri)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Personalización de fondos", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {

            // 1. VISTA PREVIA EN TIEMPO REAL
            RealTimePreviewCard(state = state, viewModel = viewModel)

            Spacer(modifier = Modifier.height(20.dp))

            // Free community wallpapers (CC0 / public domain), downloaded inside the app
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenCommunity() }
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Descargar fondos gratis de la comunidad", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text(
                        "Fotos libres (CC0 / dominio público) de Wikimedia Commons: naturaleza, paisajes, espacio, oficina, hogar… sin derechos de autor.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 2. MODO DE FONDOS
            ModeSelectionCard(state = state, viewModel = viewModel)

            Spacer(modifier = Modifier.height(20.dp))

            // 3. MODO ESPECÍFICO: BIBLIOTECA, CARPETA O COLORES
            when (state.preferences.mode) {
                BackgroundMode.PRESET_LIBRARY -> {
                    PresetLibraryCard(state = state, viewModel = viewModel)
                }
                BackgroundMode.CUSTOM_FOLDER -> {
                    CustomFolderCard(
                        state = state,
                        viewModel = viewModel,
                        onOpenFolderPicker = { folderPickerLauncher.launch(null) }
                    )
                }
                BackgroundMode.SMART_COLOR -> {
                    SmartColorStatusCard(state = state)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 4. COLORES POR ESTADO DE GLUCOSA
            ZoneColorsCard(state = state, viewModel = viewModel)

            Spacer(modifier = Modifier.height(20.dp))

            // 5. APARIENCIA Y PROTECCIÓN DE LECTURA
            AppearanceControlsCard(state = state, viewModel = viewModel)

            Spacer(modifier = Modifier.height(20.dp))

            // 6. VELOCIDAD DE TRANSICIÓN E INTERVALO
            TransitionAndIntervalCard(state = state, viewModel = viewModel)

            Spacer(modifier = Modifier.height(40.dp))
        }
    }

    // Modal color picker dialog when editing a zone color
    state.activeColorEditZone?.let { zone ->
        HsvColorPickerDialog(
            zone = zone,
            initialHex = state.preferences.colorHexForZone(zone),
            onConfirm = { hex -> viewModel.saveZoneColor(zone, hex) },
            onDismiss = { viewModel.dismissColorDialog() }
        )
    }
}

@Composable
private fun RealTimePreviewCard(
    state: BackgroundSettingsUiState,
    viewModel: BackgroundSettingsViewModel
) {
    val context = LocalContext.current
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Visibility, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Vista previa en tiempo real", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Previsualiza el contraste de lectura sobre el fondo según el estado de glucosa seleccionado:",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Zone Selection Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                GlucoseZone.entries.forEach { zone ->
                    FilterChip(
                        selected = state.previewZone == zone,
                        onClick = { viewModel.setPreviewZone(zone) },
                        label = { Text(zone.labelEs, fontSize = 11.sp) },
                        leadingIcon = {
                            val color = try {
                                Color(android.graphics.Color.parseColor(state.preferences.colorHexForZone(zone)))
                            } catch (e: Exception) {
                                Color.Gray
                            }
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(color)
                            )
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Mini Mock Frame Canvas
            val targetHex = state.preferences.colorHexForZone(state.previewZone)
            val eligible = state.allImages.filter { !it.isDisabled }
            val matchedImage = remember(state.preferences, state.previewZone, state.allImages) {
                when (state.preferences.mode) {
                    BackgroundMode.SMART_COLOR -> {
                        ColorAnalysisEngine.selectBestMatchingImage(eligible, targetHex, null)
                    }
                    BackgroundMode.PRESET_LIBRARY -> {
                        val categoryImages = if (state.preferences.presetCategory == "todos") {
                            eligible.filter { it.sourceType == "PRESET" }
                        } else {
                            eligible.filter { it.sourceType == "PRESET" && it.category == state.preferences.presetCategory }
                        }
                        categoryImages.firstOrNull()
                    }
                    BackgroundMode.CUSTOM_FOLDER -> {
                        eligible.firstOrNull { it.sourceType == "CUSTOM" }
                    }
                }
            }

            var previewBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
            LaunchedEffect(matchedImage?.uriString, state.preferences.blurRadius) {
                if (matchedImage != null) {
                    val decoded = BitmapUtils.decodeSampledBitmap(context, matchedImage.uriString, 480, 240)
                    if (decoded != null) {
                        previewBitmap = if (state.preferences.blurRadius > 0.5f) {
                            BitmapUtils.applyBlur(decoded, state.preferences.blurRadius)
                        } else {
                            decoded
                        }
                    }
                } else {
                    previewBitmap = null
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                previewBitmap?.let { bmp ->
                    if (!bmp.isRecycled) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                // Dark Overlay
                val baseOpacity = state.preferences.darkLayerOpacity
                val effectiveOpacity = if (state.preferences.autoReadingProtection) {
                    val bright = matchedImage?.averageBrightness ?: 0.5f
                    (baseOpacity + (bright - 0.35f).coerceAtLeast(0f) * 0.45f).coerceIn(0.15f, 0.92f)
                } else {
                    baseOpacity
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = effectiveOpacity))
                )

                // Simulated Glucose Reading Scrim Card
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.65f),
                    modifier = Modifier.padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val valStr = state.latestReading?.valueMgDl?.toString() ?: "112"
                        val arrow = state.latestReading?.trend?.symbol ?: "→"
                        Text(
                            text = valStr,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = arrow,
                            fontSize = 24.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "mg/dL",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ModeSelectionCard(
    state: BackgroundSettingsUiState,
    viewModel: BackgroundSettingsViewModel
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Modo de fondos", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }

            Spacer(modifier = Modifier.height(10.dp))

            BackgroundMode.entries.forEach { mode ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.setMode(mode) }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = state.preferences.mode == mode,
                        onClick = { viewModel.setMode(mode) }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(mode.displayName, fontWeight = FontWeight.Medium, fontSize = 15.sp)
                        val desc = when (mode) {
                            BackgroundMode.PRESET_LIBRARY -> "Fondos integrados y procedurales organizados por categorías"
                            BackgroundMode.CUSTOM_FOLDER -> "Tus propias fotos desde una carpeta local en el dispositivo"
                            BackgroundMode.SMART_COLOR -> "Fondo adaptativo al color configurado para tu zona de glucosa"
                        }
                        Text(desc, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun PresetLibraryCard(
    state: BackgroundSettingsUiState,
    viewModel: BackgroundSettingsViewModel
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Image, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Biblioteca predeterminada", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Categories
            LazyRow(
                contentPadding = PaddingValues(end = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(BackgroundPreferences.DEFAULT_PRESET_CATEGORIES) { cat ->
                    FilterChip(
                        selected = state.preferences.presetCategory.equals(cat, ignoreCase = true),
                        onClick = { viewModel.setPresetCategory(cat) },
                        label = { Text(cat.replaceFirstChar { it.uppercase() }) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            val presets = state.allImages.filter { it.sourceType == "PRESET" }
            val filtered = if (state.preferences.presetCategory == "todos") {
                presets
            } else {
                presets.filter { it.category.equals(state.preferences.presetCategory, ignoreCase = true) }
            }

            Text(
                text = "${filtered.size} fondos disponibles (toca para fijar, estrella para favorito):",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            var durationTarget by remember { mutableStateOf<BackgroundImageEntity?>(null) }
            LazyRow(
                contentPadding = PaddingValues(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filtered, key = { it.uriString }) { item ->
                    ThumbnailCard(
                        item = item,
                        isFixed = state.preferences.fixedImageUri == item.uriString,
                        onSelectFixed = {
                            if (state.preferences.fixedImageUri == item.uriString) {
                                viewModel.setFixedImage(null)
                            } else {
                                viewModel.setFixedImage(item.uriString)
                            }
                        },
                        onToggleFavorite = { fav -> viewModel.toggleFavorite(item.id, fav) },
                        onToggleDisabled = { dis -> viewModel.toggleDisabled(item.id, dis) },
                        durationSeconds = state.preferences.photoDurations[item.uriString],
                        onPickDuration = { durationTarget = item }
                    )
                }
            }
            durationTarget?.let { target ->
                PhotoDurationDialog(
                    photoName = target.displayName,
                    current = state.preferences.photoDurations[target.uriString],
                    generalSeconds = state.preferences.changeIntervalSeconds,
                    onSelect = { seconds ->
                        viewModel.setPhotoDuration(target.uriString, seconds)
                        durationTarget = null
                    },
                    onDismiss = { durationTarget = null }
                )
            }
        }
    }
}

@Composable
private fun ThumbnailCard(
    item: BackgroundImageEntity,
    isFixed: Boolean,
    onSelectFixed: () -> Unit,
    onToggleFavorite: (Boolean) -> Unit,
    onToggleDisabled: (Boolean) -> Unit,
    durationSeconds: Int? = null,
    onPickDuration: () -> Unit = {}
) {
    val context = LocalContext.current
    var bitmap by remember(item.uriString) { mutableStateOf<android.graphics.Bitmap?>(null) }

    LaunchedEffect(item.uriString) {
        bitmap = BitmapUtils.getThumbnail(context, item.uriString, 120)
    }

    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isFixed) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier
            .width(130.dp)
            .clickable { onSelectFixed() }
    ) {
        Column(modifier = Modifier.padding(6.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.DarkGray)
            ) {
                bitmap?.let { bmp ->
                    if (!bmp.isRecycled) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = item.displayName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                // Favorite star button
                IconButton(
                    onClick = { onToggleFavorite(!item.isFavorite) },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(28.dp)
                ) {
                    Icon(
                        imageVector = if (item.isFavorite) Icons.Default.Star else Icons.Outlined.StarOutline,
                        contentDescription = "Favorito",
                        tint = if (item.isFavorite) Color(0xFFFFD700) else Color.White
                    )
                }

                if (isFixed) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text("Fijo", fontSize = 9.sp, color = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = item.displayName,
                maxLines = 1,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = if (durationSeconds != null) "⏱ ${formatDuration(durationSeconds)}" else "⏱ tiempo general",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clickable { onPickDuration() }
                    .padding(vertical = 2.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.primaryClassification,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                IconButton(
                    onClick = { onToggleDisabled(!item.isDisabled) },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = if (item.isDisabled) Icons.Default.Warning else Icons.Default.Check,
                        contentDescription = "Activo",
                        tint = if (item.isDisabled) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun CustomFolderCard(
    state: BackgroundSettingsUiState,
    viewModel: BackgroundSettingsViewModel,
    onOpenFolderPicker: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Carpeta personalizada", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (state.customFolderPermissionLost) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Vuelve a seleccionar la carpeta. Se perdió el permiso de lectura.",
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontSize = 12.sp
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onOpenFolderPicker,
                    modifier = Modifier.testTag("choose_folder_button")
                ) {
                    Text("Seleccionar carpeta")
                }

                if (!state.preferences.customFolderUri.isNullOrBlank()) {
                    OutlinedButton(onClick = { viewModel.rescanCustomFolder() }) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Reescanear")
                    }
                }
            }

            if (!state.preferences.customFolderUri.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                val customCount = state.allImages.count { it.sourceType == "CUSTOM" }
                Text(
                    text = when {
                        state.folderScan.scanning -> "Buscando imágenes en la carpeta…"
                        state.folderScan.error != null -> state.folderScan.error.orEmpty()
                        customCount == 0 && state.folderScan.finished ->
                            "No se encontraron imágenes (se leen .jpg, .png, .webp, .bmp, .gif y subcarpetas)."
                        else -> "Carpeta configurada ($customCount imágenes)"
                    },
                    fontSize = 12.sp,
                    color = if (state.folderScan.error != null) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )

                Spacer(modifier = Modifier.height(10.dp))
                Text("Orden de reproducción:", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { viewModel.setCustomFolderOrder(CustomFolderOrder.RANDOM) }
                    ) {
                        RadioButton(
                            selected = state.preferences.customFolderOrder == CustomFolderOrder.RANDOM,
                            onClick = { viewModel.setCustomFolderOrder(CustomFolderOrder.RANDOM) }
                        )
                        Text("Aleatorio", fontSize = 13.sp)
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { viewModel.setCustomFolderOrder(CustomFolderOrder.SEQUENTIAL) }
                    ) {
                        RadioButton(
                            selected = state.preferences.customFolderOrder == CustomFolderOrder.SEQUENTIAL,
                            onClick = { viewModel.setCustomFolderOrder(CustomFolderOrder.SEQUENTIAL) }
                        )
                        Text("Secuencial", fontSize = 13.sp)
                    }
                }

                // Pictures of the folder: favourite, enable/disable and the time each one stays on screen
                val folderImages = state.allImages.filter { it.sourceType == "CUSTOM" }.take(60)
                if (folderImages.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        "Fotos de la carpeta (⏱ para darle a cada una su tiempo):",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    var folderDurationTarget by remember { mutableStateOf<BackgroundImageEntity?>(null) }
                    LazyRow(
                        contentPadding = PaddingValues(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(folderImages, key = { it.uriString }) { item ->
                            ThumbnailCard(
                                item = item,
                                isFixed = state.preferences.fixedImageUri == item.uriString,
                                onSelectFixed = {
                                    if (state.preferences.fixedImageUri == item.uriString) {
                                        viewModel.setFixedImage(null)
                                    } else {
                                        viewModel.setFixedImage(item.uriString)
                                    }
                                },
                                onToggleFavorite = { fav -> viewModel.toggleFavorite(item.id, fav) },
                                onToggleDisabled = { dis -> viewModel.toggleDisabled(item.id, dis) },
                                durationSeconds = state.preferences.photoDurations[item.uriString],
                                onPickDuration = { folderDurationTarget = item }
                            )
                        }
                    }
                    folderDurationTarget?.let { target ->
                        PhotoDurationDialog(
                            photoName = target.displayName,
                            current = state.preferences.photoDurations[target.uriString],
                            generalSeconds = state.preferences.changeIntervalSeconds,
                            onSelect = { seconds ->
                                viewModel.setPhotoDuration(target.uriString, seconds)
                                folderDurationTarget = null
                            },
                            onDismiss = { folderDurationTarget = null }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SmartColorStatusCard(state: BackgroundSettingsUiState) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.ColorLens, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Selección inteligente por colores", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "El fondo se adaptará automáticamente al color asignado a la zona de glucosa actual, buscando la mejor coincidencia cromática en las imágenes disponibles.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(10.dp))

            if (state.analysisProgress.isAnalyzing) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Analizando colores: ${state.analysisProgress.analyzedCount} de ${state.analysisProgress.totalCount} imágenes...",
                        fontSize = 12.sp
                    )
                }
            } else {
                Text(
                    "Análisis completado: ${state.allImages.size} imágenes catalogadas.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Color breakdown chips
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(state.colorCounts.entries.toList()) { entry ->
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = "${entry.key.replaceFirstChar { it.uppercase() }}: ${entry.value}",
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ZoneColorsCard(
    state: BackgroundSettingsUiState,
    viewModel: BackgroundSettingsViewModel
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Colores por estado", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                TextButton(onClick = { viewModel.restoreDefaultZoneColors() }) {
                    Text("Restaurar predeterminados", fontSize = 12.sp)
                }
            }

            Text(
                "Toca cada estado para seleccionar un color personalizado:",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(10.dp))

            GlucoseZone.entries.forEach { zone ->
                val hex = state.preferences.colorHexForZone(zone)
                val color = try {
                    Color(android.graphics.Color.parseColor(hex))
                } catch (e: Exception) {
                    Color.Gray
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { viewModel.openColorDialog(zone) }
                        .padding(vertical = 8.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(color)
                                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(zone.labelEs, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                    }

                    Text(hex.uppercase(), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            }
        }
    }
}

@Composable
private fun AppearanceControlsCard(
    state: BackgroundSettingsUiState,
    viewModel: BackgroundSettingsViewModel
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Apariencia y transparencia", fontWeight = FontWeight.Bold, fontSize = 16.sp)

            Spacer(modifier = Modifier.height(12.dp))

            // Dark Layer Opacity
            Text("Opacidad capa oscura: ${(state.preferences.darkLayerOpacity * 100).toInt()}%", fontSize = 13.sp)
            Slider(
                value = state.preferences.darkLayerOpacity,
                onValueChange = { viewModel.setDarkLayerOpacity(it) },
                valueRange = 0.0f..0.90f
            )

            // Blur Radius
            Text("Desenfoque: ${state.preferences.blurRadius.toInt()} px", fontSize = 13.sp)
            Slider(
                value = state.preferences.blurRadius,
                onValueChange = { viewModel.setBlurRadius(it) },
                valueRange = 0f..25f
            )

            // Saturation
            Text("Saturación: ${String.format(java.util.Locale.US, "%.1f", state.preferences.saturation)}x", fontSize = 13.sp)
            Slider(
                value = state.preferences.saturation,
                onValueChange = { viewModel.setSaturation(it) },
                valueRange = 0.0f..2.0f
            )

            // Brightness
            Text("Brillo: ${String.format(java.util.Locale.US, "%.1f", state.preferences.brightness)}x", fontSize = 13.sp)
            Slider(
                value = state.preferences.brightness,
                onValueChange = { viewModel.setBrightness(it) },
                valueRange = 0.5f..1.5f
            )

            // Contrast
            Text("Contraste: ${String.format(java.util.Locale.US, "%.1f", state.preferences.contrast)}x", fontSize = 13.sp)
            Slider(
                value = state.preferences.contrast,
                onValueChange = { viewModel.setContrast(it) },
                valueRange = 0.5f..1.5f
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Auto reading protection switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Protección automática de lectura", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                    Text(
                        "Incrementa automáticamente la capa oscura en fotos claras para garantizar la legibilidad del valor de glucosa.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = state.preferences.autoReadingProtection,
                    onCheckedChange = { viewModel.setAutoReadingProtection(it) }
                )
            }
        }
    }
}

@Composable
private fun TransitionAndIntervalCard(
    state: BackgroundSettingsUiState,
    viewModel: BackgroundSettingsViewModel
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Transición e intervalo", fontWeight = FontWeight.Bold, fontSize = 16.sp)

            Spacer(modifier = Modifier.height(10.dp))

            // Auto change switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Cambio automático de fondo", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                    Text("Alterna periódicamente entre las imágenes compatibles", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = state.preferences.autoChangeEnabled,
                    onCheckedChange = { viewModel.setAutoChangeEnabled(it) }
                )
            }

            if (state.preferences.autoChangeEnabled) {
                Spacer(modifier = Modifier.height(10.dp))
                val intervalMins = state.preferences.changeIntervalSeconds / 60
                Text("Intervalo de cambio: $intervalMins min (${state.preferences.changeIntervalSeconds} s)", fontSize = 13.sp)
                val intervals = listOf(15, 30, 60, 180, 300, 600, 900, 1800, 3600)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(intervals) { sec ->
                        FilterChip(
                            selected = state.preferences.changeIntervalSeconds == sec,
                            onClick = { viewModel.setChangeInterval(sec) },
                            label = { Text(if (sec >= 60) "${sec / 60} min" else "${sec} s", fontSize = 11.sp) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Crossfade Duration
            Text(
                "Duración de transición (crossfade): ${String.format(java.util.Locale.US, "%.1f", state.preferences.crossfadeDurationSeconds)} s",
                fontSize = 13.sp
            )
            Slider(
                value = state.preferences.crossfadeDurationSeconds,
                onValueChange = { viewModel.setCrossfadeDuration(it) },
                valueRange = 0.0f..5.0f
            )

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Cada foto puede tener su propio tiempo: toca «⏱ tiempo general» bajo su miniatura (solo con el cambio automático activado).",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Zoom lento (efecto cine)", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                    Text("La foto se acerca y aleja despacio. Usa algo más de batería.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = state.preferences.kenBurnsEnabled,
                    onCheckedChange = { viewModel.setKenBurns(it) }
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Mostrar el nombre de la foto", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                    Text("Pequeño rótulo abajo a la izquierda.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = state.preferences.showPhotoName,
                    onCheckedChange = { viewModel.setShowPhotoName(it) }
                )
            }
        }
    }
}


private fun formatDuration(seconds: Int): String = when {
    seconds < 60 -> "$seconds s"
    seconds < 3600 -> "${seconds / 60} min"
    else -> "${seconds / 3600} h"
}

/** Chooses how long one picture stays on screen (or goes back to the general interval). */
@Composable
private fun PhotoDurationDialog(
    photoName: String,
    current: Int?,
    generalSeconds: Int,
    onSelect: (Int?) -> Unit,
    onDismiss: () -> Unit
) {
    val options = listOf(15, 30, 60, 180, 300, 600, 900, 1800, 3600)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tiempo de «$photoName»", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(null) }
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = current == null, onClick = { onSelect(null) })
                    Text("Tiempo general (${formatDuration(generalSeconds)})", fontSize = 14.sp)
                }
                for (seconds in options) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(seconds) }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = current == seconds, onClick = { onSelect(seconds) })
                        Text(formatDuration(seconds), fontSize = 14.sp)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cerrar") }
        }
    )
}
