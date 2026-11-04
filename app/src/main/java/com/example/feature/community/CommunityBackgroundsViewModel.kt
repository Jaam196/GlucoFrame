package com.example.feature.community

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.datastore.BackgroundPreferencesRepository
import com.example.core.model.BackgroundMode
import com.example.data.background.BackgroundRepository
import com.example.data.community.CommunityBackgroundsRepository
import com.example.data.community.CommunityIndex
import com.example.data.community.CommunityPhoto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** A theme the user can browse: what is searched and in which library category the pictures are saved. */
data class CommunityTopic(val label: String, val terms: String, val category: String)

val COMMUNITY_TOPICS = listOf(
    CommunityTopic("Naturaleza", "nature forest flowers", "naturaleza"),
    CommunityTopic("Paisajes", "landscape mountains lake", "paisajes"),
    CommunityTopic("Lugares", "city skyline architecture night", "lugares"),
    CommunityTopic("Espacio", "nebula galaxy stars", "espacio"),
    CommunityTopic("Oficina", "office desk workspace", "oficina"),
    CommunityTopic("Gaming", "video game neon", "gaming"),
    CommunityTopic("Hogar", "living room interior cozy", "hogar"),
    CommunityTopic("Familia", "family sunset silhouette", "familia")
)

data class CommunityUiState(
    val topicIndex: Int = 0,
    val customQuery: String = "",
    val results: List<CommunityPhoto> = emptyList(),
    val nextOffset: Int? = null,
    val loading: Boolean = false,
    val error: String? = null,
    val downloading: Set<Long> = emptySet(),
    val downloaded: List<CommunityIndex.Item> = emptyList(),
    val showDownloaded: Boolean = false,
    val message: String? = null
)

class CommunityBackgroundsViewModel(
    private val communityRepository: CommunityBackgroundsRepository,
    private val backgroundRepository: BackgroundRepository,
    private val backgroundPreferencesRepository: BackgroundPreferencesRepository,
    private val appContext: android.content.Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(CommunityUiState())
    val uiState: StateFlow<CommunityUiState> = _uiState.asStateFlow()

    init {
        refreshDownloaded()
        search(reset = true)
    }

    private fun currentTopic(): CommunityTopic = COMMUNITY_TOPICS[_uiState.value.topicIndex.coerceIn(0, COMMUNITY_TOPICS.lastIndex)]

    fun selectTopic(index: Int) {
        _uiState.value = _uiState.value.copy(topicIndex = index, customQuery = "", showDownloaded = false)
        search(reset = true)
    }

    fun setCustomQuery(text: String) {
        _uiState.value = _uiState.value.copy(customQuery = text)
    }

    fun searchCustom() {
        _uiState.value = _uiState.value.copy(showDownloaded = false)
        search(reset = true)
    }

    fun showDownloaded(show: Boolean) {
        _uiState.value = _uiState.value.copy(showDownloaded = show)
        if (show) refreshDownloaded()
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }

    fun loadMore() {
        if (_uiState.value.nextOffset != null && !_uiState.value.loading) search(reset = false)
    }

    private fun search(reset: Boolean) {
        val state = _uiState.value
        val terms = state.customQuery.trim().ifBlank { currentTopic().terms }
        val offset = if (reset) 0 else (state.nextOffset ?: 0)
        _uiState.value = state.copy(loading = true, error = null, results = if (reset) emptyList() else state.results)
        viewModelScope.launch {
            try {
                val page = communityRepository.search(terms, offset)
                val merged = if (reset) page.photos else (_uiState.value.results + page.photos).distinctBy { it.id }
                _uiState.value = _uiState.value.copy(
                    results = merged,
                    nextOffset = page.nextOffset,
                    loading = false,
                    error = if (merged.isEmpty()) "No se encontraron fondos libres para esta búsqueda. Prueba con otras palabras (en inglés suele dar más resultados)." else null
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    error = "No se pudo buscar: ${e.message ?: "revisa tu conexión a internet"}"
                )
            }
        }
    }

    suspend fun loadThumbnail(url: String): Bitmap? = communityRepository.loadThumbnail(url)

    /** Downloads a picture into the library; when [apply] is true it also becomes the wallpaper right away. */
    fun download(photo: CommunityPhoto, apply: Boolean) {
        if (photo.id in _uiState.value.downloading) return
        val category = if (_uiState.value.customQuery.isBlank()) currentTopic().category else "naturaleza"
        _uiState.value = _uiState.value.copy(downloading = _uiState.value.downloading + photo.id)
        viewModelScope.launch {
            try {
                val path = communityRepository.download(photo, category)
                val customFolder = backgroundPreferencesRepository.backgroundPreferencesFlow.first().customFolderUri
                backgroundRepository.scanAndSyncImages(customFolder)
                if (apply) applyPath(path)
                _uiState.value = _uiState.value.copy(
                    downloading = _uiState.value.downloading - photo.id,
                    message = if (apply) "Fondo aplicado" else "Descargado en la biblioteca («$category»)"
                )
                refreshDownloaded()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    downloading = _uiState.value.downloading - photo.id,
                    message = "No se pudo descargar: ${e.message ?: "error desconocido"}"
                )
            }
        }
    }

    fun applyDownloaded(item: CommunityIndex.Item) {
        viewModelScope.launch {
            applyPath(item.path)
            _uiState.value = _uiState.value.copy(message = "Fondo aplicado")
        }
    }

    private suspend fun applyPath(path: String) {
        backgroundPreferencesRepository.updateMode(BackgroundMode.PRESET_LIBRARY)
        backgroundPreferencesRepository.updateFixedImageUri(path)
    }

    fun deleteDownloaded(item: CommunityIndex.Item) {
        viewModelScope.launch {
            val prefs = backgroundPreferencesRepository.backgroundPreferencesFlow.first()
            if (prefs.fixedImageUri == item.path) backgroundPreferencesRepository.updateFixedImageUri(null)
            communityRepository.delete(item)
            backgroundRepository.scanAndSyncImages(prefs.customFolderUri)
            refreshDownloaded()
        }
    }

    private fun refreshDownloaded() {
        _uiState.value = _uiState.value.copy(downloaded = CommunityIndex.load(appContext))
    }
}
