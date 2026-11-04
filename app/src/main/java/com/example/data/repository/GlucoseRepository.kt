package com.example.data.repository

import android.content.Context
import android.net.Uri
import com.example.core.common.GlucoseUtils
import com.example.core.database.GlucoseDatabase
import com.example.core.database.GlucoseReadingEntity
import com.example.core.database.SyncEventEntity
import com.example.core.model.GlucoseReading
import com.example.core.model.GlucoseUnit
import com.example.core.model.SyncEvent
import com.example.core.model.ThresholdSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.OutputStreamWriter

class GlucoseRepository(
    private val database: GlucoseDatabase
) {
    private val readingDao = database.glucoseReadingDao()
    private val syncDao = database.syncEventDao()

    fun getLatestReading(): Flow<GlucoseReading?> {
        return readingDao.getLatestReading().map { it?.toDomain() }
    }

    fun getLatestReading(sourceId: String): Flow<GlucoseReading?> {
        return readingDao.getLatestReading(sourceId).map { it?.toDomain() }
    }

    suspend fun getLatestReadingSync(): GlucoseReading? {
        return readingDao.getLatestReadingSync()?.toDomain()
    }

    suspend fun getLatestReadingSync(sourceId: String): GlucoseReading? {
        return readingDao.getLatestReadingSync(sourceId)?.toDomain()
    }

    suspend fun getPreviousReading(beforeUtcMs: Long): GlucoseReading? {
        return readingDao.getPreviousReading(beforeUtcMs)?.toDomain()
    }

    suspend fun getPreviousReading(sourceId: String, beforeUtcMs: Long): GlucoseReading? {
        return readingDao.getPreviousReading(sourceId, beforeUtcMs)?.toDomain()
    }

    fun getReadingsBetween(startMs: Long, endMs: Long): Flow<List<GlucoseReading>> {
        return readingDao.getReadingsBetween(startMs, endMs).map { list ->
            list.map { it.toDomain() }
        }
    }

    fun getReadingsBetween(sourceId: String, startMs: Long, endMs: Long): Flow<List<GlucoseReading>> {
        return readingDao.getReadingsBetween(sourceId, startMs, endMs).map { list ->
            list.map { it.toDomain() }
        }
    }

    suspend fun getReadingsBetweenSync(startMs: Long, endMs: Long): List<GlucoseReading> {
        return readingDao.getReadingsBetweenSync(startMs, endMs).map { it.toDomain() }
    }

    suspend fun getReadingsBetweenSync(sourceId: String, startMs: Long, endMs: Long): List<GlucoseReading> {
        return readingDao.getReadingsBetweenSync(sourceId, startMs, endMs).map { it.toDomain() }
    }

    fun getRecentSyncEvents(limit: Int = 200): Flow<List<SyncEvent>> {
        return syncDao.getRecentEvents(limit).map { list ->
            list.map { it.toDomain() }
        }
    }

    suspend fun saveReadings(readings: List<GlucoseReading>): Int = withContext(Dispatchers.IO) {
        if (readings.isEmpty()) return@withContext 0
        val entities = readings.map { GlucoseReadingEntity.fromDomain(it) }
        val ids = readingDao.insertReadings(entities)
        ids.count { it > 0 }
    }

    suspend fun recordSyncEvent(event: SyncEvent) = withContext(Dispatchers.IO) {
        syncDao.insertEvent(SyncEventEntity.fromDomain(event))
        syncDao.trimOlderEvents(keepCount = 500)
    }

    suspend fun purgeOldReadings(retentionDays: Int) = withContext(Dispatchers.IO) {
        val cutoffMs = System.currentTimeMillis() - (retentionDays.toLong() * 24 * 60 * 60 * 1000L)
        readingDao.deleteOlderThan(cutoffMs)
    }

    suspend fun exportToCsv(
        context: Context,
        uri: Uri,
        unit: GlucoseUnit,
        thresholds: ThresholdSettings,
        startMs: Long,
        endMs: Long
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val readings = readingDao.getReadingsBetweenSync(startMs, endMs)
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                OutputStreamWriter(outputStream, Charsets.UTF_8).use { writer ->
                    writer.write("Fecha_Hora_UTC,Fecha_Hora_Local,Fuente,Glucosa_mg_dL,Glucosa_mmol_L,Tendencia,Alerta_Alta,Alerta_Baja\n")
                    for (entity in readings) {
                        val localTime = GlucoseUtils.formatFullDateTime(entity.measuredAtUtcMs)
                        val mmol = GlucoseUtils.mgDlToMmol(entity.valueMgDl)
                        val isHigh = entity.valueMgDl > thresholds.highMgDl
                        val isLow = entity.valueMgDl < thresholds.lowMgDl
                        writer.write(
                            "${entity.measuredAtUtcMs},$localTime,${entity.sourceId},${entity.valueMgDl}," +
                                String.format(java.util.Locale.US, "%.1f", mmol) +
                                ",${entity.trend},$isHigh,$isLow\n"
                        )
                    }
                    writer.flush()
                }
            }
            true
        } catch (e: Exception) {
            false
        }
    }
}
