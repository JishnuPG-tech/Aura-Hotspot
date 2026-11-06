package com.jishnu.aurahotspot.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jishnu.aurahotspot.admin.SystemShellBridge
import com.jishnu.aurahotspot.engine.ClientSession
import com.jishnu.aurahotspot.engine.HotspotForegroundService
import com.jishnu.aurahotspot.engine.SessionManager
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            AuraHotspotTheme {
                DashboardScreen(
                    onToggleHotspot = { start -> toggleHotspot(start) },
                    onOpenWebDashboard = { openWebDashboard() }
                )
            }
        }
    }

    private fun toggleHotspot(start: Boolean) {
        val intent = Intent(this, HotspotForegroundService::class.java).apply {
            action = if (start) HotspotForegroundService.ACTION_START else HotspotForegroundService.ACTION_STOP
        }
        if (start) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        } else {
            startService(intent)
        }
    }

    private fun openWebDashboard() {
        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://jishnupg-hermes.hf.space/card/"))
        startActivity(browserIntent)
    }
}

@Composable
fun DashboardScreen(
    onToggleHotspot: (Boolean) -> Unit,
    onOpenWebDashboard: () -> Unit
) {
    var isHotspotActive by remember { mutableStateOf(false) }
    var isAdbReady by remember { mutableStateOf(false) }
    val sessions by SessionManager.sessionsFlow.collectAsState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        scope.launch {
            isAdbReady = SystemShellBridge.isAdbReady()
        }
    }

    val bgDark = Color(0xFF09090B)
    val cardBg = Color(0xFF18181B)
    val borderCol = Color(0xFF27272A)
    val emerald = Color(0xFF10B981)

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = bgDark
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (isHotspotActive) emerald else Color.Gray)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isHotspotActive) "HOTSPOT ONLINE • 5 GHz" else "HOTSPOT STANDBY",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isHotspotActive) emerald else Color.Gray,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Text(
                        text = "Aura Hotspot",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                IconButton(onClick = onOpenWebDashboard) {
                    Icon(
                        imageVector = Icons.Default.Language,
                        contentDescription = "Open 3D Webapp",
                        tint = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Master Toggle Button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        if (isHotspotActive) {
                            Brush.horizontalGradient(listOf(Color(0xFF059669), Color(0xFF10B981)))
                        } else {
                            Brush.horizontalGradient(listOf(cardBg, cardBg))
                        }
                    )
                    .border(1.dp, borderCol, RoundedCornerShape(20.dp))
                    .clickable {
                        isHotspotActive = !isHotspotActive
                        onToggleHotspot(isHotspotActive)
                    }
                    .padding(vertical = 24.dp, horizontal = 20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = if (isHotspotActive) "Stop Hotspot Sharing" else "Start Captive Hotspot",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (isHotspotActive) "Zero conntrack • Line-rate forwarding active" else "Tap to broadcast SSID: Jishnu P G",
                            fontSize = 12.sp,
                            color = if (isHotspotActive) Color(0xFFD1FAE5) else Color.Gray
                        )
                    }

                    Icon(
                        imageVector = if (isHotspotActive) Icons.Default.WifiOff else Icons.Default.Wifi,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // System Badges Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatusCard(
                    title = "Active SSID",
                    value = "Jishnu P G",
                    sub = "Open / Captive",
                    modifier = Modifier.weight(1f)
                )
                StatusCard(
                    title = "Passcode",
                    value = "Jishnupg",
                    sub = "Case-insensitive",
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatusCard(
                    title = "Wi-Fi Band",
                    value = "5 GHz 80MHz",
                    sub = "Low Heat / Race-to-Sleep",
                    modifier = Modifier.weight(1f)
                )
                StatusCard(
                    title = "Embedded Bridge",
                    value = if (isAdbReady) "Connected ✓" else "Port 5555",
                    sub = if (isAdbReady) "uid=2000 Standalone" else "Zero External Apps",
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Connected Clients Section
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Connected Devices (" + sessions.size + ")",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "Live Telemetry",
                    fontSize = 11.sp,
                    color = emerald,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (sessions.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(cardBg)
                        .border(1.dp, borderCol, RoundedCornerShape(16.dp))
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Devices,
                            contentDescription = null,
                            tint = Color.Gray,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No devices connected",
                            color = Color.Gray,
                            fontSize = 13.sp
                        )
                        Text(
                            text = "Connect to Wi-Fi to trigger the 3D portal",
                            color = Color(0xFF52525B),
                            fontSize = 11.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(sessions) { session ->
                        ClientRow(session = session)
                    }
                }
            }
        }
    }
}

@Composable
fun StatusCard(
    title: String,
    value: String,
    sub: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF18181B))
            .border(1.dp, Color(0xFF27272A), RoundedCornerShape(16.dp))
            .padding(14.dp)
    ) {
        Column {
            Text(text = title, fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = value, fontSize = 15.sp, color = Color.White, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = sub, fontSize = 10.sp, color = Color(0xFFA1A1AA), fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
fun ClientRow(session: ClientSession) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF18181B))
            .border(1.dp, Color(0xFF27272A), RoundedCornerShape(14.dp))
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = session.deviceName,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "IP: " + session.ip,
                    fontSize = 11.sp,
                    color = Color.Gray,
                    fontFamily = FontFamily.Monospace
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (session.isAuthenticated) Color(0xFF064E3B) else Color(0xFF78350F))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = if (session.isAuthenticated) "UNLOCKED ✓" else "PENDING LOGIN",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (session.isAuthenticated) Color(0xFF34D399) else Color(0xFFFBBF24),
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
fun AuraHotspotTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Color(0xFF09090B),
            surface = Color(0xFF18181B),
            primary = Color(0xFF10B981)
        ),
        content = content
    )
}
