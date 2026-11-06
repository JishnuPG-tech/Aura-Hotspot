package com.jishnu.aurahotspot.engine

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.jishnu.aurahotspot.admin.SystemShellBridge
import com.jishnu.aurahotspot.hotspot.HotspotController
import com.jishnu.aurahotspot.ui.MainActivity
import kotlinx.coroutines.*

class HotspotForegroundService : Service() {
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var captiveServer: CaptiveServer? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(NOTIFICATION_ID, buildNotification("Hotspot active on 5 GHz • Captive Gateway listening"))

        serviceScope.launch {
            try {
                // 1. Start Wi-Fi AP
                HotspotController.startTethering()

                // 2. Start Captive Gateway (HTTP :8080 & DNS :5353)
                captiveServer = CaptiveServer().also { it.start() }

                // 3. Apply Firewall Redirection Rules
                SystemShellBridge.applyCaptiveFirewallRules()
                
                Log.i(TAG, "Aura Hotspot Foreground Service fully operational")
            } catch (e: Exception) {
                Log.e(TAG, "Error bootstrapping service", e)
            }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.launch {
            try {
                captiveServer?.stop()
                SystemShellBridge.clearFirewallRules()
                HotspotController.stopTethering()
                SessionManager.clearAll()
            } catch (e: Exception) {}
        }
        serviceScope.cancel()
        releaseWakeLock()
        Log.i(TAG, "Aura Hotspot Foreground Service stopped cleanly")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
        wakeLock = powerManager?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AuraHotspot::Lock")?.apply {
            setReferenceCounted(false)
            acquire(24 * 60 * 60 * 1000L) // 24 hours max safeguard
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        } catch (e: Exception) {}
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Aura Hotspot Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Maintains high-speed captive hotspot routing"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(statusText: String): Notification {
        val launchIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Aura Hotspot • FastShare 5GHz")
            .setContentText(statusText)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val TAG = "AuraService"
        const val CHANNEL_ID = "aura_hotspot_channel"
        const val NOTIFICATION_ID = 45159
        const val ACTION_START = "com.jishnu.aurahotspot.START"
        const val ACTION_STOP = "com.jishnu.aurahotspot.STOP"
    }
}
