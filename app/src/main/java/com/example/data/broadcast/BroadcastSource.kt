package com.example.data.broadcast

import com.example.core.model.SyncResult
import com.example.data.source.GlucoseSource
import com.example.data.source.SourceError
import com.example.data.source.SourceResult

/**
 * "Source" for readings that other apps push to us by broadcast. There is nothing to download:
 * [GlucoseBroadcastReceiver] stores each reading as it arrives. This class only reports whether data
 * has been arriving, so the connection badge is honest.
 */
class BroadcastSource : GlucoseSource {
    override val sourceId: String = "LOCAL_BROADCAST"
    override val displayName: String = "Apps locales (xDrip+, Juggluco, Dexcom BYODA)"

    override suspend fun fetchCurrentAndGraph(fetchGraph: Boolean): SourceResult {
        val last = LocalBroadcastState.lastReceivedMs
        val ageMs = System.currentTimeMillis() - last
        return if (last == 0L || ageMs > 10 * 60_000L) {
            SourceResult.Error(
                SourceError(
                    resultType = SyncResult.OTHER,
                    message = "No llegan datos de otras apps. Revisa que xDrip+ o Juggluco envíen la difusión a esta app.",
                    retryDelaySeconds = 60
                )
            )
        } else {
            SourceResult.Success(emptyList())
        }
    }

    override suspend fun testConnection(): SourceResult = fetchCurrentAndGraph(fetchGraph = false)
}
