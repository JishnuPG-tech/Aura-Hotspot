package com.jishnu.aurahotspot.engine

import android.util.Log
import com.jishnu.aurahotspot.admin.SystemShellBridge
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket

class CaptiveServer(
    private val httpPort: Int = 8080,
    private val dnsPort: Int = 5353,
    private val portalUrl: String = "https://jishnupg-hermes.hf.space/card/",
    private val validPasscodes: List<String> = listOf("Jishnupg", "jishnupg", "aura2026")
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var httpServer: ServerSocket? = null
    private var dnsSocket: DatagramSocket? = null
    @Volatile private var isRunning = false

    fun start() {
        if (isRunning) return
        isRunning = true
        startHttpServer()
        startDnsHijacker()
        Log.i(TAG, "Captive Gateway Server started on HTTP :, DNS :")
    }

    fun stop() {
        isRunning = false
        try { httpServer?.close() } catch (e: Exception) {}
        try { dnsSocket?.close() } catch (e: Exception) {}
        scope.cancel()
        Log.i(TAG, "Captive Gateway Server stopped")
    }

    private fun startHttpServer() {
        scope.launch {
            try {
                httpServer = ServerSocket(httpPort)
                while (isRunning) {
                    val client = httpServer?.accept() ?: break
                    launch { handleClientSocket(client) }
                }
            } catch (e: Exception) {
                if (isRunning) Log.e(TAG, "HTTP Server accept error", e)
            }
        }
    }

    private suspend fun handleClientSocket(socket: Socket) = withContext(Dispatchers.IO) {
        val clientIp = socket.inetAddress.hostAddress ?: "127.0.0.1"
        try {
            socket.soTimeout = 5000
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            val out = socket.getOutputStream()

            val requestLine = reader.readLine() ?: return@withContext
            var userAgent = ""
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                if (line.isNullOrEmpty()) break
                if (line!!.lowercase().startsWith("user-agent:")) {
                    userAgent = line!!.substringAfter(":").trim()
                }
            }

            SessionManager.registerClient(clientIp, userAgent)
            val isAuthenticated = SessionManager.isClientAuthenticated(clientIp)

            val parts = requestLine.split(" ")
            val path = if (parts.size >= 2) parts[1] else "/"

            when {
                // Auth webhook trigger from the 3D card webapp
                path.startsWith("/auth") -> {
                    handleAuthRequest(path, clientIp, out)
                }
                // OS Connectivity check after login
                isAuthenticated -> {
                    sendHttp204(out)
                }
                // OS Probe for Android/Apple/Windows when not authenticated -> Redirect to Webapp!
                path.contains("generate_204") || 
                path.contains("hotspot-detect") || 
                path.contains("connecttest") || 
                path.contains("ncsi") || 
                path == "/" -> {
                    sendHttp302(out, clientIp)
                }
                // Default fallback for any other HTTP request -> 302 to Webapp
                else -> {
                    sendHttp302(out, clientIp)
                }
            }
        } catch (e: Exception) {
            // Socket timeout or closed
        } finally {
            try { socket.close() } catch (e: Exception) {}
        }
    }

    private suspend fun handleAuthRequest(path: String, clientIp: String, out: OutputStream) {
        val query = path.substringAfter("?", "")
        val codeParam = query.split("&")
            .firstOrNull { it.startsWith("code=") }
            ?.substringAfter("code=") ?: ""
        val decodedCode = java.net.URLDecoder.decode(codeParam, "UTF-8").trim()

        val isValid = validPasscodes.any { it.equals(decodedCode, ignoreCase = true) }
        if (isValid) {
            Log.i(TAG, "Passcode validated successfully for IP: ")
            SessionManager.markAuthenticated(clientIp)
            
            // Execute firewall rule to grant line-rate internet forwarding!
            SystemShellBridge.whitelistClientIp(clientIp)

            val response = """
                HTTP/1.1 200 OK\r
                Content-Type: application/json\r
                Access-Control-Allow-Origin: *\r
                Access-Control-Allow-Methods: GET, POST\r
                Connection: close\r
                \r
                {"ok":true,"status":"unlocked","client_ip":""}
            """.trimIndent()
            out.write(response.toByteArray())
        } else {
            val response = """
                HTTP/1.1 401 Unauthorized\r
                Content-Type: application/json\r
                Access-Control-Allow-Origin: *\r
                Connection: close\r
                \r
                {"ok":false,"error":"Invalid passcode"}
            """.trimIndent()
            out.write(response.toByteArray())
        }
        out.flush()
    }

    private fun sendHttp302(out: OutputStream, clientIp: String) {
        val targetUrl = "?client_ip="
        val response = """
            HTTP/1.1 302 Found\r
            Location: \r
            Cache-Control: no-cache, no-store, must-revalidate\r
            Content-Type: text/html\r
            Connection: close\r
            \r
            <html><head><meta http-equiv="refresh" content="0;url="></head><body>Redirecting to <a href="">Captive Portal</a></body></html>
        """.trimIndent()
        out.write(response.toByteArray())
        out.flush()
    }

    private fun sendHttp204(out: OutputStream) {
        val response = "HTTP/1.1 204 No Content\r\nConnection: close\r\n\r\n"
        out.write(response.toByteArray())
        out.flush()
    }

    private fun startDnsHijacker() {
        scope.launch {
            try {
                dnsSocket = DatagramSocket(dnsPort)
                val buffer = ByteArray(512)
                while (isRunning) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    dnsSocket?.receive(packet)
                    launch { handleDnsPacket(packet) }
                }
            } catch (e: Exception) {
                if (isRunning) Log.e(TAG, "DNS Server error", e)
            }
        }
    }

    private fun handleDnsPacket(packet: DatagramPacket) {
        try {
            val data = packet.data
            val length = packet.length
            if (length < 12) return

            // DNS Header: Copy Transaction ID, set Response Flag (0x8180)
            val response = ByteArray(length + 16)
            System.arraycopy(data, 0, response, 0, length)

            // Flags: Standard query response, No error
            response[2] = 0x81.toByte()
            response[3] = 0x80.toByte()
            // Questions: 1, Answer RRs: 1
            response[6] = 0x00.toByte()
            response[7] = 0x01.toByte()

            // Answer section: Pointer to Question name (0xC00C)
            var offset = length
            response[offset++] = 0xC0.toByte()
            response[offset++] = 0x0C.toByte()
            // Type A (0x0001), Class IN (0x0001)
            response[offset++] = 0x00.toByte()
            response[offset++] = 0x01.toByte()
            response[offset++] = 0x00.toByte()
            response[offset++] = 0x01.toByte()
            // TTL 60 seconds (0x0000003C)
            response[offset++] = 0x00.toByte()
            response[offset++] = 0x00.toByte()
            response[offset++] = 0x00.toByte()
            response[offset++] = 0x3C.toByte()
            // Data length: 4 bytes (IPv4)
            response[offset++] = 0x00.toByte()
            response[offset++] = 0x04.toByte()

            // IP: 192.168.43.1 (default Android hotspot gateway)
            response[offset++] = 192.toByte()
            response[offset++] = 168.toByte()
            response[offset++] = 43.toByte()
            response[offset++] = 1.toByte()

            val reply = DatagramPacket(response, offset, packet.address, packet.port)
            dnsSocket?.send(reply)
        } catch (e: Exception) {}
    }

    companion object {
        private const val TAG = "CaptiveServer"
    }
}
