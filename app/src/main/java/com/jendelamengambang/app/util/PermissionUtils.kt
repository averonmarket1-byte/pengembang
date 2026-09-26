package com.jendelamengambang.app.util

import android.content.Context
import android.provider.Settings

/**
 * Kumpulan helper untuk mengecek status izin sistem yang dibutuhkan
 * oleh fitur jendela mengambang (tanpa login/registrasi apa pun).
 */
object PermissionUtils {

    /** Mengecek apakah izin "Tampilkan di atas aplikasi lain" sudah aktif. */
    fun hasOverlayPermission(context: Context): Boolean {
        return Settings.canDrawOverlays(context)
    }
}
