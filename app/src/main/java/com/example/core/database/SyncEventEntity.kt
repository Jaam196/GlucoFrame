package com.example.core.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.core.model.SyncEvent
import com.example.core.model.SyncResult

@Entity(
    tableName = "sync_event",
    indices = [
        Index(value = ["atUtcMs"])
    ]
)
data class SyncEventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val atUtcMs: Long,
    val sourceId: String,
    val result: String,
    val httpStatus: Int?,
    val message: String
) {
    fun toDomain(): SyncEvent = SyncEvent(
        id = id,
        atUtcMs = atUtcMs,
        sourceId = sourceId,
        result = try { SyncResult.valueOf(result) } catch (e: Exception) { SyncResult.OTHER },
        httpStatus = httpStatus,
        message = message
    )

    companion object {
        fun fromDomain(domain: SyncEvent): SyncEventEntity = SyncEventEntity(
            id = domain.id,
            atUtcMs = domain.atUtcMs,
            sourceId = domain.sourceId,
            result = domain.result.name,
            httpStatus = domain.httpStatus,
            message = domain.message
        )
    }
}
