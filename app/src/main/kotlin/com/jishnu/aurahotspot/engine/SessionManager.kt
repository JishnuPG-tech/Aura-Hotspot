package com.jishnu.aurahotspot.engine

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

data class ClientSession(
    val ip: String,
    val mac: String = "unknown",
    val deviceName: String = "Connecting Device",
    val isAuthenticated: Boolean = false,
    val connectedAt: Long = System.currentTimeMillis(),
    val authenticatedAt: Long = 0L,
    val bytesTransferred: Long = 0L
)

object SessionManager {
    private val sessions = ConcurrentHashMap<String, ClientSession>()
    private val _sessionsFlow = MutableStateFlow<List<ClientSession>>(emptyList())
    val sessionsFlow: StateFlow<List<ClientSession>> = _sessionsFlow.asStateFlow()

    fun registerClient(ip: String, userAgent: String = ""): ClientSession {
        val deviceName = parseDevice(userAgent)
        val existing = sessions[ip]
        val session = existing?.copy(deviceName = deviceName) ?: ClientSession(
            ip = ip,
            deviceName = deviceName,
            isAuthenticated = false
        )
        sessions[ip] = session
        updateFlow()
        return session
    }

    fun isClientAuthenticated(ip: String): Boolean {
        return sessions[ip]?.isAuthenticated == true
    }

    fun markAuthenticated(ip: String): ClientSession? {
        val existing = sessions[ip] ?: ClientSession(ip = ip)
        val updated = existing.copy(
            isAuthenticated = true,
            authenticatedAt = System.currentTimeMillis()
        )
        sessions[ip] = updated
        updateFlow()
        return updated
    }

    fun removeClient(ip: String) {
        sessions.remove(ip)
        updateFlow()
    }

    fun clearAll() {
        sessions.clear()
        updateFlow()
    }

    private fun updateFlow() {
        _sessionsFlow.value = sessions.values.toList()
    }

    private fun parseDevice(ua: String): String {
        val lower = ua.lowercase()
        return when {
            lower.contains("iphone") -> "Apple iPhone"
            lower.contains("ipad") -> "Apple iPad"
            lower.contains("macintosh") || lower.contains("mac os") -> "Apple Mac"
            lower.contains("android") -> {
                when {
                    lower.contains("samsung") -> "Samsung Galaxy"
                    lower.contains("pixel") -> "Google Pixel"
                    lower.contains("xiaomi") || lower.contains("redmi") -> "Xiaomi / Redmi"
                    else -> "Android Device"
                }
            }
            lower.contains("windows") -> "Windows PC"
            lower.contains("linux") -> "Linux PC"
            else -> "Wi-Fi Client"
        }
    }
}
