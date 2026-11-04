package com.example.data.broadcast

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * What the app last heard from other glucose apps (xDrip+, Juggluco, Dexcom BYODA...).
 * Shown in Settings so the user can see whether the connection works.
 */
object LocalBroadcastState {

    /** Time (device clock) of the last accepted broadcast; 0 when nothing arrived since the app started. */
    @Volatile
    var lastReceivedMs: Long = 0L
        private set

    private val _lastInfo = MutableStateFlow("Todavía no se ha recibido ningún dato de otras apps.")
    val lastInfo: StateFlow<String> = _lastInfo.asStateFlow()

    /** [acceptedAtMs] is null when the broadcast was ignored or not understood. */
    fun record(info: String, acceptedAtMs: Long?) {
        if (acceptedAtMs != null) lastReceivedMs = acceptedAtMs
        _lastInfo.value = info
    }
}
