package com.example.data.treatments

import android.content.Context
import com.example.core.database.treatments.TreatmentDao
import com.example.core.database.treatments.TreatmentEntity
import com.example.core.database.treatments.TreatmentsDatabase
import com.example.core.model.Treatment
import com.example.core.model.TreatmentKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

/** Insulin / food / note log: typed in the app or read from Nightscout. */
class TreatmentRepository private constructor(private val dao: TreatmentDao) {

    fun observeRecent(hours: Int = 48): Flow<List<Treatment>> {
        val since = System.currentTimeMillis() - hours * 3_600_000L
        return dao.observeSince(since).map { list -> list.map { it.toModel() } }
    }

    suspend fun addLocal(kind: TreatmentKind, amount: Double, note: String, atMs: Long) {
        dao.insert(
            TreatmentEntity(
                atMs = atMs,
                kind = kind.name,
                amount = amount,
                note = note.trim(),
                origin = Treatment.ORIGIN_LOCAL,
                externalId = "local-" + UUID.randomUUID()
            )
        )
    }

    suspend fun delete(id: Long) = dao.delete(id)

    /** Entries read from a remote system; repeated entries (same externalId) are ignored. */
    suspend fun saveRemote(list: List<Treatment>) {
        if (list.isEmpty()) return
        dao.insertAll(list.map { it.toEntity() })
    }

    suspend fun purgeOld(days: Int = 30) {
        dao.purgeBefore(System.currentTimeMillis() - days * 86_400_000L)
    }

    private fun TreatmentEntity.toModel() = Treatment(
        id = id,
        atMs = atMs,
        kind = runCatching { TreatmentKind.valueOf(kind) }.getOrDefault(TreatmentKind.NOTE),
        amount = amount,
        note = note,
        origin = origin,
        externalId = externalId
    )

    private fun Treatment.toEntity() = TreatmentEntity(
        atMs = atMs,
        kind = kind.name,
        amount = amount,
        note = note,
        origin = origin,
        externalId = externalId.ifBlank { "remote-" + UUID.randomUUID() }
    )

    companion object {
        @Volatile
        private var INSTANCE: TreatmentRepository? = null

        fun getInstance(context: Context): TreatmentRepository {
            return INSTANCE ?: synchronized(this) {
                val repo = TreatmentRepository(TreatmentsDatabase.getInstance(context).treatmentDao())
                INSTANCE = repo
                repo
            }
        }
    }
}
