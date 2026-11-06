package com.jishnu.aurahotspot.admin

import android.util.Log
import dadb.Dadb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object SystemShellBridge {
    private const val TAG = "SystemShellBridge"
    private var dadbInstance: Dadb? = null

    suspend fun getDadb(): Dadb? = withContext(Dispatchers.IO) {
        if (dadbInstance != null) return@withContext dadbInstance
        try {
            val dadb = Dadb.create("127.0.0.1", 5555)
            dadbInstance = dadb
            Log.i(TAG, "Connected to internal ADB on 127.0.0.1:5555!")
            dadb
        } catch (e: Exception) {
            Log.w(TAG, "Direct ADB connection on 127.0.0.1:5555 failed: " + e.message)
            null
        }
    }

    suspend fun isAdbReady(): Boolean = withContext(Dispatchers.IO) {
        try {
            val d = getDadb()
            if (d != null) {
                val res = d.shell("id")
                if (res.exitCode == 0) return@withContext true
            }
        } catch (e: Exception) {}

        return@withContext ShizukuBridge.hasShizukuPermission()
    }

    suspend fun executeShell(cmd: String): Pair<Int, String> = withContext(Dispatchers.IO) {
        // 1. Try Direct Embedded ADB (Zero Third-Party Apps required!)
        try {
            val d = getDadb()
            if (d != null) {
                val response = d.shell(cmd)
                return@withContext Pair(response.exitCode, response.allOutput.trim())
            }
        } catch (e: Exception) {
            Log.w(TAG, "Direct ADB shell failed, trying fallback: " + e.message)
            dadbInstance = null
        }

        // 2. Secondary fallback to Shizuku if present
        if (ShizukuBridge.hasShizukuPermission()) {
            return@withContext ShizukuBridge.executeShell(cmd)
        }

        Pair(-1, "ADB on 127.0.0.1:5555 is not connected")
    }

    suspend fun applyCaptiveFirewallRules(tetherInterface: String = "wlan1"): Boolean {
        Log.i(TAG, "Applying captive portal iptables rules on " + tetherInterface)
        executeShell("iptables -t nat -F PREROUTING 2>/dev/null || true")
        executeShell("iptables -t nat -A PREROUTING -i " + tetherInterface + " -p udp --dport 53 -j REDIRECT --to-ports 5353")
        val (code, _) = executeShell("iptables -t nat -A PREROUTING -i " + tetherInterface + " -p tcp --dport 80 -j REDIRECT --to-ports 8080")
        return code == 0
    }

    suspend fun whitelistClientIp(clientIp: String, tetherInterface: String = "wlan1"): Boolean {
        Log.i(TAG, "Whitelisting authenticated IP: " + clientIp)
        val (code, _) = executeShell("iptables -t nat -I PREROUTING 1 -i " + tetherInterface + " -s " + clientIp + " -j ACCEPT")
        return code == 0
    }

    suspend fun clearFirewallRules(): Boolean {
        Log.i(TAG, "Clearing captive portal iptables rules")
        val (code, _) = executeShell("iptables -t nat -F PREROUTING 2>/dev/null || true")
        return code == 0
    }
}
