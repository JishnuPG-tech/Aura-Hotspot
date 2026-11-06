package com.jishnu.aurahotspot.admin

import android.content.pm.PackageManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader

object ShizukuBridge {
    private const val TAG = "ShizukuBridge"

    fun isShizukuAvailable(): Boolean {
        return try {
            Shizuku.pingBinder()
        } catch (e: Throwable) {
            false
        }
    }

    fun hasShizukuPermission(): Boolean {
        return try {
            if (!isShizukuAvailable()) return false
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (e: Throwable) {
            false
        }
    }

    suspend fun executeShell(cmd: String): Pair<Int, String> = withContext(Dispatchers.IO) {
        if (!hasShizukuPermission()) {
            Log.w(TAG, "Shizuku permission not granted, command execution skipped: " + cmd)
            return@withContext Pair(-1, "Shizuku permission not granted")
        }

        try {
            val newProcessMethod = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            ).apply {
                isAccessible = true
            }
            val process = newProcessMethod.invoke(null, arrayOf("sh", "-c", cmd), null, null) as Process
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                output.append(line).append('\n')
            }
            val exitCode = process.waitFor()
            Pair(exitCode, output.toString().trim())
        } catch (e: Exception) {
            Log.e(TAG, "Error running shell command: " + cmd, e)
            Pair(-1, e.message ?: "Execution error")
        }
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
