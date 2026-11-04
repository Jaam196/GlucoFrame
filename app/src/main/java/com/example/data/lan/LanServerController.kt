package com.example.data.lan

import android.util.Log
import com.example.GlucoseFrameApplication
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Starts and stops the local server (and its network announcement) following the user's settings. */
class LanServerController(private val app: GlucoseFrameApplication) {

    val server = LanServer(app)
    val discovery = LanDiscovery(app)

    private val _status = MutableStateFlow("Desactivado")
    val status: StateFlow<String> = _status.asStateFlow()

    private data class Config(val enabled: Boolean, val port: Int, val token: String)

    fun start() {
        app.appScope.launch {
            app.preferencesRepository.userPreferencesFlow
                .map { Config(it.lanServerEnabled, it.lanServerPort, it.lanServerToken) }
                .distinctUntilChanged()
                .collect { config -> apply(config) }
        }
    }

    private fun apply(config: Config) {
        if (!config.enabled || config.token.isBlank()) {
            server.stop()
            discovery.unregister()
            _status.value = if (config.enabled) "Falta el token" else "Desactivado"
            return
        }
        val error = server.start(config.port, config.token)
        if (error == null) {
            discovery.register(config.port)
            _status.value = "Activo en el puerto ${config.port}"
        } else {
            discovery.unregister()
            _status.value = error
            Log.w("LanServerController", error)
        }
    }
}
