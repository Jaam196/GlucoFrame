package com.example.sync

import android.content.Context
import com.example.core.datastore.PreferencesRepository
import com.example.core.model.ActiveSourceType
import com.example.core.model.SyncEvent
import com.example.core.model.SyncResult
import com.example.core.security.KeystoreManager
import com.example.data.broadcast.BroadcastSource
import com.example.data.librelinkup.LibreLinkUpSource
import com.example.data.nightscout.LocalNightscoutSource
import com.example.data.nightscout.NightscoutSource
import com.example.data.repository.GlucoseRepository
import com.example.data.simulated.SimulatedSource
import com.example.data.source.GlucoseSource
import com.example.data.source.SourceError
import com.example.data.source.SourceResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class SyncStatus(
    val isSyncing: Boolean = false,
    val lastResult: SyncResult = SyncResult.OK,
    val lastMessage: String = "",
    val lastSyncTimeMs: Long = 0L,
    val rateLimitRemainingSeconds: Int = 0
)

class SyncEngine(
    private val context: Context,
    private val repository: GlucoseRepository,
    private val preferencesRepository: PreferencesRepository,
    private val keystoreManager: KeystoreManager
) {
    private val syncMutex = Mutex()
    private var lastFullGraphTimeMs: Long = 0L
    private var lastDataPurgeDay: Long = 0L
    private var lastTreatmentsFetchMs: Long = 0L
    private val treatmentRepository = com.example.data.treatments.TreatmentRepository.getInstance(context)

    private val _syncStatus = MutableStateFlow(SyncStatus())
    val syncStatus: StateFlow<SyncStatus> = _syncStatus.asStateFlow()

    // Sources cache
    private val libreLinkUpSource = LibreLinkUpSource(keystoreManager, preferencesRepository)
    private val nightscoutSource = NightscoutSource(keystoreManager)
    private val localNightscoutSource = LocalNightscoutSource(keystoreManager)
    private val simulatedSource = SimulatedSource()
    private val broadcastSource = BroadcastSource()

    fun getSourceForType(type: ActiveSourceType): GlucoseSource = when (type) {
        ActiveSourceType.LIBRELINKUP -> libreLinkUpSource
        ActiveSourceType.NIGHTSCOUT -> nightscoutSource
        ActiveSourceType.JUGGLUCO -> localNightscoutSource
        ActiveSourceType.LOCAL_BROADCAST -> broadcastSource
        ActiveSourceType.SIMULATED -> simulatedSource
    }

    suspend fun testActiveSource(): SourceResult = syncMutex.withLock {
        val prefs = preferencesRepository.userPreferencesFlow.first()
        val source = getSourceForType(prefs.activeSource)
        source.testConnection()
    }

    suspend fun syncOnce(forceGraph: Boolean = false): SourceResult {
        if (!syncMutex.tryLock()) {
            return SourceResult.Error(
                SourceError(
                    resultType = SyncResult.OTHER,
                    message = "Sincronización ya en curso",
                    retryDelaySeconds = 10
                )
            )
        }

        try {
            _syncStatus.value = _syncStatus.value.copy(isSyncing = true)
            val prefs = preferencesRepository.userPreferencesFlow.first()
            val source = getSourceForType(prefs.activeSource)

            // Fill graph at startup and every 30 minutes
            val now = System.currentTimeMillis()
            val thirtyMinutesMs = 30 * 60 * 1000L
            val needGraph = forceGraph || (now - lastFullGraphTimeMs > thirtyMinutesMs) || lastFullGraphTimeMs == 0L

            val result = source.fetchCurrentAndGraph(fetchGraph = needGraph)

            when (result) {
                is SourceResult.Success -> {
                    if (result.serverTimeMs != null) {
                        val offset = result.serverTimeMs - now
                        val skew = kotlin.math.abs(offset) > 120_000L
                        if (prefs.clockSkewDetected != skew || kotlin.math.abs(offset - prefs.lastServerClockOffsetMs) > 5_000L) {
                            preferencesRepository.setClockSkew(skew, offset)
                        }
                    } else {
                        if (prefs.clockSkewDetected || prefs.lastServerClockOffsetMs != 0L) {
                            preferencesRepository.setClockSkew(false, 0L)
                        }
                    }
                    if (needGraph) {
                        lastFullGraphTimeMs = now
                    }
                    val count = repository.saveReadings(result.readings)
                    val rawMsg = "OK: ${result.readings.size} lecturas procesadas ($count nuevas)"
                    val event = SyncEvent(
                        atUtcMs = now,
                        sourceId = source.sourceId,
                        result = SyncResult.OK,
                        httpStatus = 200,
                        message = com.example.data.network.NetworkClients.sanitize(rawMsg)
                    )
                    repository.recordSyncEvent(event)
                    _syncStatus.value = SyncStatus(
                        isSyncing = false,
                        lastResult = SyncResult.OK,
                        lastMessage = "Conectado",
                        lastSyncTimeMs = now
                    )

                    // Insulin / food entries recorded in Nightscout (every 5 minutes at most)
                    if (source is NightscoutSource && source !is LocalNightscoutSource &&
                        now - lastTreatmentsFetchMs > 5 * 60 * 1000L
                    ) {
                        lastTreatmentsFetchMs = now
                        try {
                            treatmentRepository.saveRemote(source.fetchTreatments())
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            // extra data only
                        }
                    }

                    // Daily purge of readings exceeding retentionDays
                    val currentDay = now / (24 * 60 * 60 * 1000L)
                    if (currentDay > lastDataPurgeDay) {
                        repository.purgeOldReadings(prefs.retentionDays)
                        try { treatmentRepository.purgeOld(30) } catch (e: Exception) { /* ignore */ }
                        lastDataPurgeDay = currentDay
                    }
                }
                is SourceResult.Error -> {
                    val event = SyncEvent(
                        atUtcMs = now,
                        sourceId = source.sourceId,
                        result = result.error.resultType,
                        httpStatus = result.error.httpStatus,
                        message = com.example.data.network.NetworkClients.sanitize(result.error.message)
                    )
                    repository.recordSyncEvent(event)
                    _syncStatus.value = SyncStatus(
                        isSyncing = false,
                        lastResult = result.error.resultType,
                        lastMessage = result.error.message,
                        lastSyncTimeMs = now,
                        rateLimitRemainingSeconds = result.error.rateLimitRemainingSeconds
                    )
                }
            }
            return result
        } finally {
            syncMutex.unlock()
        }
    }
}
