package com.jendelamengambang.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jendelamengambang.app.ui.theme.*

@Composable
fun DashboardScreen(
    overlayGranted: Boolean,
    serviceActive: Boolean,
    onRequestOverlayPermission: () -> Unit,
    onActivateFloatingWindow: () -> Unit,
    onDeactivateFloatingWindow: () -> Unit
) {
    Scaffold(containerColor = BgSoft) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Spacer(Modifier.height(12.dp))

            Text(
                text = "Jendela Mengambang",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Text(
                text = "Bidik soal, biar kami carikan jawabannya.",
                fontSize = 14.sp,
                color = TextSecondary
            )

            // ---------- Kartu status izin ----------
            PermissionStatusCard(
                granted = overlayGranted,
                onRequestPermission = onRequestOverlayPermission
            )

            // ---------- Kartu aksi utama (gelap, kontras tinggi) ----------
            MainActionCard(
                overlayGranted = overlayGranted,
                serviceActive = serviceActive,
                onActivate = onActivateFloatingWindow,
                onDeactivate = onDeactivateFloatingWindow
            )

            // ---------- Kartu langkah penggunaan ----------
            HowItWorksCard()
        }
    }
}

@Composable
private fun PermissionStatusCard(granted: Boolean, onRequestPermission: () -> Unit) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Izin Tampil di Atas Aplikasi Lain",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = TextPrimary
                )
                Spacer(Modifier.height(6.dp))
                StatusPill(active = granted)
            }
            if (!granted) {
                Button(
                    onClick = onRequestPermission,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary)
                ) {
                    Text("Aktifkan", color = Color.White, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun StatusPill(active: Boolean) {
    val bg = if (active) StatusGreen.copy(alpha = 0.12f) else StatusRed.copy(alpha = 0.12f)
    val fg = if (active) StatusGreen else StatusRed
    val label = if (active) "Aktif" else "Belum Aktif"
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(label, color = fg, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun MainActionCard(
    overlayGranted: Boolean,
    serviceActive: Boolean,
    onActivate: () -> Unit,
    onDeactivate: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = CardDark),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(22.dp)) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(AccentPrimary.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Layers, contentDescription = null, tint = AccentPrimary)
            }
            Spacer(Modifier.height(14.dp))
            Text(
                if (serviceActive) "Jendela mengambang sedang berjalan"
                else "Aktifkan gelembung pencari jawaban",
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Cukup satu klik, tanpa perlu login atau mengetik ulang soal.",
                color = Color.White.copy(alpha = 0.65f),
                fontSize = 13.sp
            )
            Spacer(Modifier.height(18.dp))
            Button(
                onClick = { if (serviceActive) onDeactivate() else onActivate() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (serviceActive) StatusRed else AccentPrimary
                )
            ) {
                Icon(
                    if (serviceActive) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = Color.White
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (serviceActive) "Matikan Jendela Mengambang" else "Aktifkan Jendela Mengambang",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun HowItWorksCard() {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Search, contentDescription = null, tint = AccentPrimary)
                Spacer(Modifier.width(8.dp))
                Text("Cara Pakai", fontWeight = FontWeight.SemiBold, color = TextPrimary)
            }
            Spacer(Modifier.height(10.dp))
            listOf(
                "1. Tekan \"Aktifkan Jendela Mengambang\".",
                "2. Geser gelembung ke area soal di aplikasi mana pun.",
                "3. Ketuk gelembung, atur kotak bidik ke teks soal.",
                "4. Tekan \"Cari Jawaban\" — Google akan terbuka otomatis."
            ).forEach {
                Text(it, fontSize = 13.sp, color = TextSecondary, modifier = Modifier.padding(vertical = 3.dp))
            }
        }
    }
}
