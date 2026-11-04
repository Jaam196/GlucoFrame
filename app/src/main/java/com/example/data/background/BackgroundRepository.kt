package com.example.data.background

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import com.example.core.common.BitmapUtils
import com.example.core.common.ColorAnalysisEngine
import com.example.core.database.background.BackgroundDatabase
import com.example.core.database.background.BackgroundImageEntity
import com.example.core.model.BackgroundMode
import com.example.core.model.BackgroundPreferences
import com.example.data.community.CommunityIndex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

data class AnalysisProgress(
    val isAnalyzing: Boolean = false,
    val analyzedCount: Int = 0,
    val totalCount: Int = 0
)

/** Result of the last scan of the user's custom folder (shown in the settings screen). */
data class FolderScanResult(
    val scanning: Boolean = false,
    val finished: Boolean = false,
    val imageCount: Int = 0,
    val error: String? = null
)

class BackgroundRepository(
    private val context: Context,
    private val database: BackgroundDatabase
) {
    private val dao = database.backgroundImageDao()
    private val repositoryScope = CoroutineScope(Dispatchers.Default + Job())

    private val _analysisProgress = MutableStateFlow(AnalysisProgress())
    val analysisProgress: StateFlow<AnalysisProgress> = _analysisProgress.asStateFlow()

    // Scans are queued (never dropped): picking a folder while the first start-up scan is still
    // running used to be ignored, so the new folder was never indexed.
    private val scanMutex = Mutex()
    private val analysisMutex = Mutex()

    private val _folderScan = MutableStateFlow(FolderScanResult())
    val folderScan: StateFlow<FolderScanResult> = _folderScan.asStateFlow()

    fun getAllImagesFlow(): Flow<List<BackgroundImageEntity>> = dao.getAllFlow()

    fun getPresetImagesFlow(): Flow<List<BackgroundImageEntity>> = dao.getBySourceTypeFlow("PRESET")

    fun getCustomImagesFlow(): Flow<List<BackgroundImageEntity>> = dao.getBySourceTypeFlow("CUSTOM")

    suspend fun getAllImagesSync(): List<BackgroundImageEntity> = dao.getAllSync()

    suspend fun getPresetsSync(): List<BackgroundImageEntity> = dao.getBySourceTypeSync("PRESET")

    suspend fun getCustomSync(): List<BackgroundImageEntity> = dao.getBySourceTypeSync("CUSTOM")

    suspend fun toggleFavorite(id: Long, isFavorite: Boolean) {
        dao.updateFavorite(id, isFavorite)
    }

    suspend fun toggleDisabled(id: Long, isDisabled: Boolean) {
        dao.updateDisabled(id, isDisabled)
    }

    fun checkCustomFolderPermission(uriString: String?): Boolean {
        if (uriString.isNullOrBlank()) return false
        val uri = Uri.parse(uriString)
        return try {
            context.contentResolver.persistedUriPermissions.any {
                it.uri == uri && it.isReadPermission
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Scans preset images (procedural + assets) and custom folder images.
     * Identifies new/modified images and kicks off background color analysis.
     */
    fun scanAndSyncImages(customFolderUri: String?) {
        repositoryScope.launch {
            scanMutex.withLock {
                try {
                    // 1. Scan and index presets
                    scanPresets()

                    // 2. Scan and index the custom folder (also its sub-folders)
                    if (customFolderUri.isNullOrBlank()) {
                        removeAllCustomImages()
                        _folderScan.value = FolderScanResult()
                    } else {
                        _folderScan.value = FolderScanResult(scanning = true)
                        _folderScan.value = scanCustomFolder(Uri.parse(customFolderUri))
                    }
                } catch (e: Exception) {
                    Log.e("BackgroundRepository", "Error during image scan: ${e.message}")
                    _folderScan.value = FolderScanResult(finished = true, error = e.message ?: "Error desconocido")
                }
            }

            // 3. Color analysis runs outside the scan lock so a new scan is never blocked by it
            if (analysisMutex.tryLock()) {
                try {
                    analyzePendingImages()
                } catch (e: Exception) {
                    Log.e("BackgroundRepository", "Error during color analysis: ${e.message}")
                } finally {
                    analysisMutex.unlock()
                }
            }
        }
    }

    private suspend fun removeAllCustomImages() {
        for (entity in dao.getBySourceTypeSync("CUSTOM")) {
            dao.deleteByUri(entity.uriString)
        }
    }

    private suspend fun scanPresets() = withContext(Dispatchers.IO) {
        // Ensure procedural presets exist
        val proceduralFiles = ProceduralBackgroundGenerator.ensureProceduralPresets(context)
        val validPresetUris = mutableListOf<String>()

        for (file in proceduralFiles) {
            val uriString = file.absolutePath
            validPresetUris.add(uriString)
            val existing = dao.getByUri(uriString)
            if (existing == null || existing.lastModified != file.lastModified()) {
                val entity = BackgroundImageEntity(
                    id = existing?.id ?: 0,
                    uriString = uriString,
                    sourceType = "PRESET",
                    category = "abstractos",
                    displayName = file.nameWithoutExtension.replace('_', ' ').replaceFirstChar { it.uppercase() },
                    lastModified = file.lastModified(),
                    fileSize = file.length(),
                    isFavorite = existing?.isFavorite ?: false,
                    isDisabled = existing?.isDisabled ?: false
                )
                dao.insertOrUpdate(entity)
            }
        }

        // Also check assets/backgrounds/<category>/
        try {
            val assetCategories = context.assets.list("backgrounds") ?: emptyArray()
            for (category in assetCategories) {
                val assetFiles = context.assets.list("backgrounds/$category") ?: emptyArray()
                for (assetFile in assetFiles) {
                    if (isImageFile(assetFile)) {
                        val uriString = "asset://backgrounds/$category/$assetFile"
                        validPresetUris.add(uriString)
                        val existing = dao.getByUri(uriString)
                        if (existing == null) {
                            val entity = BackgroundImageEntity(
                                uriString = uriString,
                                sourceType = "PRESET",
                                category = category,
                                displayName = assetFile.substringBeforeLast('.').replace('_', ' ').replaceFirstChar { it.uppercase() },
                                lastModified = 1L,
                                fileSize = 0L
                            )
                            dao.insertOrUpdate(entity)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Assets directory might not exist yet
        }

        // Wallpapers downloaded from the community (Wikimedia Commons, CC0 / public domain)
        try {
            for (item in CommunityIndex.load(context)) {
                val file = File(item.path)
                if (!file.exists()) continue
                validPresetUris.add(item.path)
                if (dao.getByUri(item.path) == null) {
                    dao.insertOrUpdate(
                        BackgroundImageEntity(
                            uriString = item.path,
                            sourceType = "PRESET",
                            category = item.category,
                            displayName = item.title.take(40),
                            lastModified = file.lastModified(),
                            fileSize = file.length()
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("BackgroundRepository", "Error indexing community wallpapers: ${e.message}")
        }

        // Delete removed presets if any
        if (validPresetUris.isNotEmpty()) {
            dao.deleteOrphaned("PRESET", validPresetUris)
        }
    }

    private suspend fun scanCustomFolder(treeUri: Uri): FolderScanResult = withContext(Dispatchers.IO) {
        val rootDocId = try {
            DocumentsContract.getTreeDocumentId(treeUri)
        } catch (e: Exception) {
            return@withContext FolderScanResult(finished = true, error = "La carpeta seleccionada no es válida. Elígela de nuevo.")
        }

        val found = LinkedHashSet<String>()
        val errors = mutableListOf<String>()
        collectFolderImages(treeUri, rootDocId, 0, found, errors)

        // Only drop stale entries when the listing really worked; a permission error must not wipe the library
        if (errors.isEmpty() || found.isNotEmpty()) {
            for (entity in dao.getBySourceTypeSync("CUSTOM")) {
                if (entity.uriString !in found) dao.deleteByUri(entity.uriString)
            }
        }

        FolderScanResult(
            finished = true,
            imageCount = found.size,
            error = if (found.isEmpty()) errors.firstOrNull() else null
        )
    }

    /** Lists [parentDocId] and, recursively, its sub-folders (up to [MAX_FOLDER_DEPTH] levels). */
    private suspend fun collectFolderImages(
        treeUri: Uri,
        parentDocId: String,
        depth: Int,
        found: MutableSet<String>,
        errors: MutableList<String>
    ) {
        if (depth > MAX_FOLDER_DEPTH || found.size >= MAX_FOLDER_IMAGES) return

        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            DocumentsContract.Document.COLUMN_SIZE
        )

        val subFolders = mutableListOf<String>()
        var cursor: Cursor? = null
        try {
            cursor = context.contentResolver.query(childrenUri, projection, null, null, null)
            if (cursor == null) {
                errors.add("No se pudo leer la carpeta (el sistema no devolvió contenido).")
                return
            }
            while (cursor.moveToNext() && found.size < MAX_FOLDER_IMAGES) {
                val childDocId = cursor.getString(0) ?: continue
                val displayName = cursor.getString(1) ?: "Imagen"
                val mimeType = cursor.getString(2) ?: ""
                val lastModified = if (cursor.isNull(3)) 0L else cursor.getLong(3)
                val size = if (cursor.isNull(4)) 0L else cursor.getLong(4)

                if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                    subFolders.add(childDocId)
                    continue
                }

                val isImage = mimeType.startsWith("image/") || isImageFile(displayName)
                if (!isImage) continue

                val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childDocId)
                val uriString = docUri.toString()
                found.add(uriString)

                val existing = dao.getByUri(uriString)
                if (existing == null || existing.lastModified != lastModified) {
                    val entity = BackgroundImageEntity(
                        id = existing?.id ?: 0,
                        uriString = uriString,
                        sourceType = "CUSTOM",
                        category = "custom",
                        displayName = displayName,
                        lastModified = lastModified,
                        fileSize = size,
                        isFavorite = existing?.isFavorite ?: false,
                        isDisabled = existing?.isDisabled ?: false
                    )
                    dao.insertOrUpdate(entity)
                }
            }
        } catch (e: SecurityException) {
            Log.e("BackgroundRepository", "No read permission for folder: ${e.message}")
            errors.add("Android no permite leer esa carpeta. Pulsa «Seleccionar carpeta» y vuelve a elegirla.")
        } catch (e: Exception) {
            Log.e("BackgroundRepository", "Error querying documents: ${e.message}")
            errors.add("Error al leer la carpeta: ${e.message}")
        } finally {
            cursor?.close()
        }

        for (sub in subFolders) {
            collectFolderImages(treeUri, sub, depth + 1, found, errors)
        }
    }

    private suspend fun analyzePendingImages() = withContext(Dispatchers.Default) {
        val all = dao.getAllSync()
        val pending = all.filter { it.analyzedAtUtcMs == 0L }

        val total = all.size
        var analyzed = total - pending.size

        if (pending.isEmpty()) {
            _analysisProgress.value = AnalysisProgress(isAnalyzing = false, analyzedCount = total, totalCount = total)
            return@withContext
        }

        _analysisProgress.value = AnalysisProgress(isAnalyzing = true, analyzedCount = analyzed, totalCount = total)

        for (item in pending) {
            try {
                // Decode small preview (64x64) for ultra fast color analysis
                val bitmap = BitmapUtils.decodeSampledBitmap(context, item.uriString, 64, 64)
                if (bitmap != null) {
                    val analysis = ColorAnalysisEngine.analyzeBitmap(bitmap)
                    bitmap.recycle()

                    val updated = item.copy(
                        primaryHue = analysis.primaryHue,
                        primaryHueQuota = analysis.primaryHueQuota,
                        secondaryHue = analysis.secondaryHue,
                        secondaryHueQuota = analysis.secondaryHueQuota,
                        neutralQuota = analysis.neutralQuota,
                        averageBrightness = analysis.averageBrightness,
                        primaryClassification = analysis.primaryClassification,
                        secondaryClassification = analysis.secondaryClassification,
                        histogramCsv = analysis.toHistogramCsv(),
                        analyzedAtUtcMs = System.currentTimeMillis()
                    )
                    dao.insertOrUpdate(updated)
                }
            } catch (e: Exception) {
                Log.e("BackgroundRepository", "Analysis failed for ${item.displayName}: ${e.message}")
            }

            analyzed++
            _analysisProgress.value = AnalysisProgress(
                isAnalyzing = analyzed < total,
                analyzedCount = analyzed,
                totalCount = total
            )
        }
    }

    fun getEligibleImages(prefs: BackgroundPreferences, allImages: List<BackgroundImageEntity>): List<BackgroundImageEntity> {
        val nonDisabled = allImages.filter { !it.isDisabled }
        return when (prefs.mode) {
            BackgroundMode.PRESET_LIBRARY -> {
                val presets = nonDisabled.filter { it.sourceType == "PRESET" }
                if (prefs.presetCategory == "todos" || prefs.presetCategory.isBlank()) {
                    presets
                } else {
                    presets.filter { it.category.equals(prefs.presetCategory, ignoreCase = true) }
                }
            }
            BackgroundMode.CUSTOM_FOLDER -> {
                val custom = nonDisabled.filter { it.sourceType == "CUSTOM" }
                if (prefs.customFolderSelectedAll) {
                    custom
                } else {
                    custom.filter { it.uriString in prefs.customFolderSelectedIds }
                }
            }
            BackgroundMode.SMART_COLOR -> {
                // Color mode considers all non-disabled images (or custom folder if permission granted, plus presets)
                nonDisabled
            }
        }
    }

    private fun isImageFile(name: String): Boolean {
        val lower = name.lowercase()
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg") ||
            lower.endsWith(".png") || lower.endsWith(".webp") ||
            lower.endsWith(".bmp") || lower.endsWith(".gif") ||
            lower.endsWith(".heic") || lower.endsWith(".heif")
    }

    private companion object {
        const val MAX_FOLDER_DEPTH = 4
        const val MAX_FOLDER_IMAGES = 2000
    }
}
