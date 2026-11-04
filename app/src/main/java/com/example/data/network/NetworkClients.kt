package com.example.data.network

import okhttp3.ConnectionSpec
import okhttp3.OkHttpClient
import java.net.InetAddress
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

object NetworkClients {

    // Cloud client: strictly HTTPS with modern TLS
    val cloudClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectionSpecs(listOf(ConnectionSpec.MODERN_TLS))
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    // Local client: used for Juggluco local network
    val localClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Checks if a URL or hostname points to a private RFC1918 IPv4 or localhost:
     * 10.0.0.0/8
     * 172.16.0.0/12
     * 192.168.0.0/16
     * 127.0.0.1 / localhost
     */
    fun isPrivateNetworkAddress(hostOrUrl: String): Boolean {
        val cleanHost = hostOrUrl
            .removePrefix("http://")
            .removePrefix("https://")
            .substringBefore(":")
            .substringBefore("/")
            .trim()

        if (cleanHost.equals("localhost", ignoreCase = true) || cleanHost == "127.0.0.1") {
            return true
        }

        val parts = cleanHost.split(".")
        if (parts.size == 4) {
            val octets = parts.mapNotNull { it.toIntOrNull() }
            if (octets.size == 4) {
                val o1 = octets[0]
                val o2 = octets[1]
                if (o1 == 10) return true
                if (o1 == 192 && o2 == 168) return true
                if (o1 == 172 && o2 in 16..31) return true
                // 100.64.0.0/10: used by VPNs such as Tailscale to reach your home devices from outside
                if (o1 == 100 && o2 in 64..127) return true
            }
        }
        return false
    }

    /**
     * Sanitize sensitive strings so tokens/passwords never leak into logs or UI
     */
    fun sanitize(text: String?): String {
        if (text == null) return ""
        return text
            .replace(Regex("\"token\"\\s*:\\s*\"[^\"]+\""), "\"token\":\"***\"")
            .replace(Regex("\"password\"\\s*:\\s*\"[^\"]+\""), "\"password\":\"***\"")
            .replace(Regex("\"email\"\\s*:\\s*\"[^\"]+\""), "\"email\":\"***\"")
            .replace(Regex("Bearer\\s+[A-Za-z0-9._~+/-]+"), "Bearer ***")
            .replace(Regex("api-secret:\\s*[a-fA-F0-9]+"), "api-secret: ***")
    }
}
