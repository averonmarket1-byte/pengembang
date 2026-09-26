package com.jendelamengambang.app

import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.jendelamengambang.app.service.FloatingCropperService
import com.jendelamengambang.app.ui.DashboardScreen
import com.jendelamengambang.app.ui.theme.JendelaMengambangTheme
import com.jendelamengambang.app.util.PermissionUtils

/**
 * MainActivity = Dashboard Utama.
 * TIDAK ADA LOGIN / REGISTER. Pengguna langsung tiba di layar ini
 * dan bisa mengaktifkan layanan jendela mengambang dalam satu klik.
 */
class MainActivity : ComponentActivity() {

    // Status izin overlay yang reaktif terhadap Compose (badge hijau/merah)
    private var overlayGranted by mutableStateOf(false)
    private var serviceActive by mutableStateOf(false)

    private lateinit var mediaProjectionManager: MediaProjectionManager

    // Launcher untuk membuka halaman pengaturan overlay khusus aplikasi ini
    private val overlaySettingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // Saat kembali dari Settings, status akan diperbarui otomatis di onResume()
    }

    // Launcher untuk meminta izin capture layar (MediaProjection) sistem Android
    private val mediaProjectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            startFloatingService(result.resultCode, result.data!!)
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* granted or not, lanjutkan alur */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        mediaProjectionManager =
            getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        setContent {
            JendelaMengambangTheme {
                DashboardScreen(
                    overlayGranted = overlayGranted,
                    serviceActive = serviceActive,
                    onRequestOverlayPermission = ::requestOverlayPermission,
                    onActivateFloatingWindow = ::onActivateClicked,
                    onDeactivateFloatingWindow = ::stopFloatingService
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Perbarui status badge izin setiap kali pengguna kembali ke aplikasi
        overlayGranted = PermissionUtils.hasOverlayPermission(this)
    }

    /** Jika izin overlay belum aktif, langsung buka halaman pengaturan khusus aplikasi ini. */
    private fun requestOverlayPermission() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        overlaySettingsLauncher.launch(intent)
    }

    /** Tombol utama "Aktifkan Jendela Mengambang" ditekan (satu klik). */
    private fun onActivateClicked() {
        if (!PermissionUtils.hasOverlayPermission(this)) {
            requestOverlayPermission()
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        // Minta izin capture layar (wajib untuk fitur tangkap area soal)
        mediaProjectionLauncher.launch(mediaProjectionManager.createScreenCaptureIntent())
    }

    private fun startFloatingService(resultCode: Int, data: Intent) {
        val serviceIntent = Intent(this, FloatingCropperService::class.java).apply {
            action = FloatingCropperService.ACTION_START
            putExtra(FloatingCropperService.EXTRA_RESULT_CODE, resultCode)
            putExtra(FloatingCropperService.EXTRA_RESULT_DATA, data)
        }
        ContextCompat.startForegroundService(this, serviceIntent)
        serviceActive = true
        // Kirim aplikasi ke background agar bubble langsung terlihat di atas layar Home/App lain
        moveTaskToBack(true)
    }

    private fun stopFloatingService() {
        val serviceIntent = Intent(this, FloatingCropperService::class.java).apply {
            action = FloatingCropperService.ACTION_STOP
        }
        startService(serviceIntent)
        serviceActive = false
    }
}
