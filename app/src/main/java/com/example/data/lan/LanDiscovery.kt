package com.example.data.lan

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class FoundDevice(val name: String, val host: String, val port: Int) {
    val url: String get() = "http://$host:$port"
}

/**
 * Finds other devices running GlucoseFrame's local server on the same Wi-Fi (mDNS / Android NSD),
 * so nobody has to type IP addresses. The access token is never advertised.
 */
class LanDiscovery(context: Context) {

    private val nsd = context.applicationContext.getSystemService(Context.NSD_SERVICE) as? NsdManager

    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null

    private val _found = MutableStateFlow<List<FoundDevice>>(emptyList())
    val found: StateFlow<List<FoundDevice>> = _found.asStateFlow()

    private val _searching = MutableStateFlow(false)
    val searching: StateFlow<Boolean> = _searching.asStateFlow()

    // Android resolves one service at a time
    private val toResolve = ArrayDeque<NsdServiceInfo>()
    private var resolving = false

    /** Announces this device so others can find it. */
    @Synchronized
    fun register(port: Int) {
        val manager = nsd ?: return
        unregister()
        val info = NsdServiceInfo().apply {
            serviceName = "GlucoseFrame ${Build.MODEL ?: ""}".trim()
            serviceType = SERVICE_TYPE
            setPort(port)
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {}
            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.w(TAG, "NSD registration failed: $errorCode")
            }
            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {}
            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
        }
        registrationListener = listener
        try {
            manager.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener)
        } catch (e: Exception) {
            Log.w(TAG, "NSD register error", e)
            registrationListener = null
        }
    }

    @Synchronized
    fun unregister() {
        val listener = registrationListener ?: return
        try {
            nsd?.unregisterService(listener)
        } catch (e: Exception) {
            // Already unregistered
        }
        registrationListener = null
    }

    /** Starts looking for other GlucoseFrame devices (results appear in [found]). */
    @Synchronized
    fun startSearch() {
        val manager = nsd ?: return
        stopSearch()
        _found.value = emptyList()
        toResolve.clear()
        resolving = false

        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {
                _searching.value = true
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                synchronized(this@LanDiscovery) {
                    toResolve.addLast(serviceInfo)
                }
                resolveNext()
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                _found.value = _found.value.filter { it.name != serviceInfo.serviceName }
            }

            override fun onDiscoveryStopped(serviceType: String) {
                _searching.value = false
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                _searching.value = false
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                _searching.value = false
            }
        }
        discoveryListener = listener
        try {
            manager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
        } catch (e: Exception) {
            Log.w(TAG, "NSD discovery error", e)
            discoveryListener = null
            _searching.value = false
        }
    }

    @Synchronized
    fun stopSearch() {
        val listener = discoveryListener ?: return
        try {
            nsd?.stopServiceDiscovery(listener)
        } catch (e: Exception) {
            // Already stopped
        }
        discoveryListener = null
        _searching.value = false
    }

    private fun resolveNext() {
        val manager = nsd ?: return
        val next: NsdServiceInfo
        synchronized(this) {
            if (resolving) return
            next = toResolve.removeFirstOrNull() ?: return
            resolving = true
        }
        try {
            manager.resolveService(next, object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    finishResolve()
                }

                override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                    val host = serviceInfo.host?.hostAddress
                    if (host != null) {
                        val device = FoundDevice(serviceInfo.serviceName ?: host, host, serviceInfo.port)
                        _found.value = (_found.value.filter { it.url != device.url } + device)
                    }
                    finishResolve()
                }
            })
        } catch (e: Exception) {
            finishResolve()
        }
    }

    private fun finishResolve() {
        synchronized(this) { resolving = false }
        resolveNext()
    }

    companion object {
        private const val TAG = "LanDiscovery"
        private const val SERVICE_TYPE = "_glucoseframe._tcp."
    }
}
