package com.example.feature.community

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.community.CommunityIndex
import com.example.data.community.CommunityPhoto

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunityBackgroundsScreen(
    state: CommunityUiState,
    viewModel: CommunityBackgroundsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }
    val context = LocalContext.current

    fun openLink(url: String) {
        if (url.isBlank()) return
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            // No browser available
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Fondos de la comunidad", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            Text(
                "Fotos gratuitas de Wikimedia Commons con licencia CC0 o dominio público: se pueden usar y publicar " +
                    "(también en Google Play) sin pedir permiso. Se guardan en tu biblioteca con su autor y licencia.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            // Browse / downloaded switch
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = !state.showDownloaded,
                    onClick = { viewModel.showDownloaded(false) },
                    label = { Text("Buscar") }
                )
                FilterChip(
                    selected = state.showDownloaded,
                    onClick = { viewModel.showDownloaded(true) },
                    label = { Text("Descargados (${state.downloaded.size})") }
                )
            }

            state.message?.let { message ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(message, fontSize = 13.sp, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.primary)
                    TextButton(onClick = { viewModel.clearMessage() }) { Text("OK") }
                }
            }

            if (state.showDownloaded) {
                DownloadedList(state, viewModel, onOpenLink = { openLink(it) })
            } else {
                BrowseList(state, viewModel, onOpenLink = { openLink(it) })
            }
        }
    }
}

@Composable
private fun BrowseList(
    state: CommunityUiState,
    viewModel: CommunityBackgroundsViewModel,
    onOpenLink: (String) -> Unit
) {
    Spacer(modifier = Modifier.height(8.dp))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        COMMUNITY_TOPICS.forEachIndexed { index, topic ->
            FilterChip(
                selected = state.customQuery.isBlank() && state.topicIndex == index,
                onClick = { viewModel.selectTopic(index) },
                label = { Text(topic.label) }
            )
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = state.customQuery,
            onValueChange = { viewModel.setCustomQuery(it) },
            label = { Text("Buscar otra cosa (mejor en inglés)") },
            singleLine = true,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Button(onClick = { viewModel.searchCustom() }, enabled = state.customQuery.isNotBlank()) { Text("Buscar") }
    }

    state.error?.let {
        Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp, modifier = Modifier.padding(vertical = 6.dp))
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(state.results, key = { it.id }) { photo ->
            PhotoCard(
                photo = photo,
                viewModel = viewModel,
                busy = photo.id in state.downloading,
                alreadyDownloaded = state.downloaded.any { it.id == photo.id },
                onOpenLink = onOpenLink
            )
        }
        item {
            Box(modifier = Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                if (state.loading) {
                    CircularProgressIndicator()
                } else if (state.nextOffset != null) {
                    OutlinedButton(onClick = { viewModel.loadMore() }) { Text("Ver más") }
                }
            }
        }
    }
}

@Composable
private fun PhotoCard(
    photo: CommunityPhoto,
    viewModel: CommunityBackgroundsViewModel,
    busy: Boolean,
    alreadyDownloaded: Boolean,
    onOpenLink: (String) -> Unit
) {
    var thumbnail by remember(photo.thumbUrl) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(photo.thumbUrl) { thumbnail = viewModel.loadThumbnail(photo.thumbUrl) }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(170.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.DarkGray),
                contentAlignment = Alignment.Center
            ) {
                val bmp = thumbnail
                if (bmp != null) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = photo.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(photo.title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 2)
            Text(
                "${photo.author} · ${photo.license} · ${photo.width}×${photo.height}",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (busy) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp))
                    Text("Descargando…", fontSize = 12.sp)
                } else {
                    Button(onClick = { viewModel.download(photo, apply = true) }) { Text("Descargar y aplicar", fontSize = 12.sp) }
                    OutlinedButton(onClick = { viewModel.download(photo, apply = false) }) {
                        Text(if (alreadyDownloaded) "Descargado ✓" else "Solo descargar", fontSize = 12.sp)
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = { onOpenLink(photo.pageUrl) }) { Text("Origen", fontSize = 12.sp) }
            }
        }
    }
}

@Composable
private fun DownloadedList(
    state: CommunityUiState,
    viewModel: CommunityBackgroundsViewModel,
    onOpenLink: (String) -> Unit
) {
    Spacer(modifier = Modifier.height(8.dp))
    if (state.downloaded.isEmpty()) {
        Text("Todavía no has descargado ningún fondo.", fontSize = 14.sp, modifier = Modifier.padding(vertical = 12.dp))
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(state.downloaded, key = { it.id }) { item ->
            DownloadedRow(item, viewModel, onOpenLink)
        }
    }
}

@Composable
private fun DownloadedRow(
    item: CommunityIndex.Item,
    viewModel: CommunityBackgroundsViewModel,
    onOpenLink: (String) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(item.title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 2)
            Text(
                "Autor: ${item.author} · Licencia: ${item.license} · Categoría: ${item.category}",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                Button(onClick = { viewModel.applyDownloaded(item) }) { Text("Aplicar", fontSize = 12.sp) }
                OutlinedButton(onClick = { onOpenLink(item.pageUrl) }) { Text("Origen", fontSize = 12.sp) }
                TextButton(onClick = { viewModel.deleteDownloaded(item) }) {
                    Text("Borrar", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
