package com.example.data.source

import com.example.core.model.GlucoseReading
import com.example.core.model.SyncResult

sealed class SourceResult {
    data class Success(
        val readings: List<GlucoseReading>,
        val serverTimeMs: Long? = null
    ) : SourceResult()

    data class Error(val error: SourceError) : SourceResult()
}

data class SourceError(
    val resultType: SyncResult,
    val httpStatus: Int? = null,
    val message: String,
    val retryDelaySeconds: Int = 60,
    val pauseLoop: Boolean = false,
    val rateLimitRemainingSeconds: Int = 0
)

interface GlucoseSource {
    val sourceId: String
    val displayName: String
    suspend fun fetchCurrentAndGraph(fetchGraph: Boolean): SourceResult
    suspend fun testConnection(): SourceResult
}
