package com.jishnu.aurahotspot.hotspot

import android.util.Log
import com.jishnu.aurahotspot.admin.SystemShellBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.FileReader

object HotspotController {
    private const val TAG = "HotspotController"

    suspend fun startTethering(): Boolean = withContext(Dispatchers.IO) {
        Log.i(TAG, "Starting Wi-Fi Tethering via System Shell Bridge")
        val (code, out) = SystemShellBridge.executeShell("cmd connectivity start-tethering wifi")
        Log.i(TAG, "start-tethering exit: " + code + ", out: " + out)
        code == 0
    }

    suspend fun stopTethering(): Boolean = withContext(Dispatchers.IO) {
        Log.i(TAG, "Stopping Wi-Fi Tethering")
        val (code, _) = SystemShellBridge.executeShell("cmd connectivity stop-tethering wifi")
        code == 0
    }

    suspend fun getConnectedClients(): List<Pair<String, String>> = withContext(Dispatchers.IO) {
        val clients = mutableListOf<Pair<String, String>>()
        try {
            val file = File("/proc/net/arp")
            if (file.exists()) {
                val reader = BufferedReader(FileReader(file))
                var line: String?
                reader.readLine() // skip header
                while (reader.readLine().also { line = it } != null) {
                    val tokens = line!!.trim().split(Regex("\\s+"))
                    if (tokens.size >= 4) {
                        val ip = tokens[0]
                        val mac = tokens[3]
                        if (mac != "00:00:00:00:00:00" && !ip.startsWith("127.")) {
                            clients.add(Pair(ip, mac))
                        }
                    }
                }
                reader.close()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not read /proc/net/arp", e)
        }
        clients
    }
}
