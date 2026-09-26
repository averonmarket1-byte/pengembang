package com.jendelamengambang.app.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.util.DisplayMetrics
import android.view.*
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.core.app.NotificationCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.jendelamengambang.app.R
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Foreground Service yang menampung:
 *  1. Bubble melayang (draggable, snap ke tepi layar)
 *  2. Overlay "kotak bidik" (crop frame) yang bisa digeser & di-resize
 *  3. Penangkapan layar via MediaProjection pada area kotak bidik
 *  4. OCR on-device (ML Kit Text Recognition)
 *  5. Membuka Google Search secara otomatis dari hasil OCR
 */
class FloatingCropperService : Service() {

    companion object {
        const val ACTION_START = "com.jendelamengambang.app.action.START"
        const val ACTION_STOP = "com.jendelamengambang.app.action.STOP"
        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_RESULT_DATA = "extra_result_data"

        private const val NOTIF_CHANNEL_ID = "floating_service_channel"
        private const val NOTIF_ID = 101
        private const val HANDLE_TOUCH_SLOP = 60 // px, area sentuh sudut untuk resize
    }

    private lateinit var windowManager: WindowManager
    private lateinit var mediaProjectionManager: MediaProjectionManager
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null

    private var bubbleView: View? = null
    private var cropOverlayView: CropOverlayView? = null
    private var cropControlsView: View? = null

    private var screenWidth = 0
    private var screenHeight = 0
    private var screenDensity = 0

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        mediaProjectionManager =
            getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(metrics)
        screenWidth = metrics.widthPixels
        screenHeight = metrics.heightPixels
        screenDensity = metrics.densityDpi
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                startForeground(NOTIF_ID, buildNotification())
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
                val data: Intent? = intent.getParcelableExtra(EXTRA_RESULT_DATA)
                if (data != null) {
                    runCatching {
                        mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data)
                        // WAJIB sejak Android 14 (API 34): MediaProjection harus punya callback
                        // terdaftar SEBELUM createVirtualDisplay() dipanggil, atau akan crash
                        // dengan IllegalStateException("Must register a callback before using...").
                        mediaProjection?.registerCallback(object : MediaProjection.Callback() {
                            override fun onStop() {
                                // Sistem menghentikan projection (mis. izin dicabut) -> bersihkan semua
                                stopEverything()
                            }
                        }, android.os.Handler(mainLooper))
                        setupVirtualDisplay()
                    }
                }
                showBubble()
            }
            ACTION_STOP -> {
                stopEverything()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        stopEverything()
    }

    // ---------------------------------------------------------------------
    // Notifikasi Foreground Service
    // ---------------------------------------------------------------------
    private fun buildNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIF_CHANNEL_ID,
                getString(R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_MIN
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(this, NOTIF_CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.notif_content_text))
            .setSmallIcon(android.R.drawable.ic_menu_search)
            .setOngoing(true)
            .build()
    }

    // ---------------------------------------------------------------------
    // MediaProjection & VirtualDisplay untuk menangkap konten layar
    // ---------------------------------------------------------------------
    private fun setupVirtualDisplay() {
        imageReader = ImageReader.newInstance(screenWidth, screenHeight, PixelFormat.RGBA_8888, 2)
        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "JendelaMengambangCapture",
            screenWidth, screenHeight, screenDensity,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface, null, null
        )
    }

    /** Mengambil satu frame layar penuh sebagai Bitmap dari ImageReader. */
    private fun captureFullScreenBitmap(): Bitmap? {
        val reader = imageReader ?: return null
        val image = reader.acquireLatestImage() ?: return null
        return try {
            val plane = image.planes[0]
            val buffer = plane.buffer
            val pixelStride = plane.pixelStride
            val rowStride = plane.rowStride
            val rowPadding = rowStride - pixelStride * screenWidth

            val bitmap = Bitmap.createBitmap(
                screenWidth + rowPadding / pixelStride,
                screenHeight,
                Bitmap.Config.ARGB_8888
            )
            bitmap.copyPixelsFromBuffer(buffer)
            Bitmap.createBitmap(bitmap, 0, 0, screenWidth, screenHeight)
        } finally {
            image.close()
        }
    }

    // ---------------------------------------------------------------------
    // BUBBLE MELAYANG (draggable + snap ke tepi)
    // ---------------------------------------------------------------------
    private fun showBubble() {
        if (bubbleView != null) return

        val inflatedBubble = ImageViewBubble(this)
        bubbleView = inflatedBubble

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = screenWidth - 180
            y = screenHeight / 3
        }

        var initialX = 0
        var initialY = 0
        var touchX = 0f
        var touchY = 0f
        var isDragging = false

        inflatedBubble.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    touchX = event.rawX
                    touchY = event.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - touchX).toInt()
                    val dy = (event.rawY - touchY).toInt()
                    if (abs(dx) > 10 || abs(dy) > 10) isDragging = true
                    params.x = initialX + dx
                    params.y = initialY + dy
                    windowManager.updateViewLayout(v, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragging) {
                        v.performClick()
                        showCropOverlay()
                    } else {
                        // Snap otomatis ke tepi layar terdekat (kiri/kanan)
                        val targetX = if (params.x + v.width / 2 < screenWidth / 2) 0
                        else screenWidth - v.width
                        animateSnap(v, params, targetX)
                    }
                    true
                }
                else -> false
            }
        }

        windowManager.addView(bubbleView, params)
    }

    private fun animateSnap(view: View, params: WindowManager.LayoutParams, targetX: Int) {
        val startX = params.x
        val steps = 10
        var step = 0
        val handler = android.os.Handler(mainLooper)
        val runnable = object : Runnable {
            override fun run() {
                step++
                params.x = startX + ((targetX - startX) * step / steps)
                windowManager.updateViewLayout(view, params)
                if (step < steps) handler.postDelayed(this, 8)
            }
        }
        handler.post(runnable)
    }

    // ---------------------------------------------------------------------
    // OVERLAY KOTAK BIDIK (Crop Frame) + Tombol Kontrol
    // ---------------------------------------------------------------------
    private fun showCropOverlay() {
        if (cropOverlayView != null) return

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        // 1) Layer full-screen berisi kotak bidik yang bisa digeser/di-resize
        val cropView = CropOverlayView(
            context = this,
            screenWidth = screenWidth,
            screenHeight = screenHeight
        )
        cropOverlayView = cropView

        val cropParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        windowManager.addView(cropView, cropParams)

        // 2) Layer kecil berisi tombol [Tutup/Batal] dan [Cari Jawaban]
        val controls = buildControlsView()
        cropControlsView = controls
        val controlsParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = 140
        }
        windowManager.addView(controls, controlsParams)

        // Sembunyikan bubble sementara saat mode bidik aktif
        bubbleView?.visibility = View.GONE
    }

    private fun buildControlsView(): View {
        val container = LinearLayoutButtons(this) { confirmed ->
            if (confirmed) {
                processCropAndSearch()
            } else {
                closeCropOverlay()
            }
        }
        return container
    }

    private fun closeCropOverlay() {
        cropOverlayView?.let { runCatching { windowManager.removeView(it) } }
        cropControlsView?.let { runCatching { windowManager.removeView(it) } }
        cropOverlayView = null
        cropControlsView = null
        bubbleView?.visibility = View.VISIBLE
    }

    /** Ambil bitmap layar penuh, potong sesuai kotak bidik, jalankan OCR, lalu cari di Google. */
    private fun processCropAndSearch() {
        val rect = cropOverlayView?.getCropRect() ?: return

        // Sembunyikan overlay dulu (agar tidak ikut ter-capture), lalu capture setelah 1 frame
        cropOverlayView?.visibility = View.INVISIBLE
        cropControlsView?.visibility = View.INVISIBLE

        android.os.Handler(mainLooper).postDelayed({
            val fullBitmap = captureFullScreenBitmap()
            closeCropOverlay()

            if (fullBitmap == null) return@postDelayed

            val safeRect = Rect(
                max(0, rect.left), max(0, rect.top),
                min(screenWidth, rect.right), min(screenHeight, rect.bottom)
            )
            if (safeRect.width() <= 0 || safeRect.height() <= 0) return@postDelayed

            val croppedBitmap = Bitmap.createBitmap(
                fullBitmap, safeRect.left, safeRect.top, safeRect.width(), safeRect.height()
            )

            runOcrAndSearch(croppedBitmap)
        }, 150)
    }

    private fun runOcrAndSearch(bitmap: Bitmap) {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val inputImage = InputImage.fromBitmap(bitmap, 0)
        recognizer.process(inputImage)
            .addOnSuccessListener { visionText ->
                val recognizedText = visionText.text.trim()
                openGoogleSearch(recognizedText)
            }
            .addOnFailureListener {
                // OCR gagal membaca teks; tampilkan bubble kembali tanpa mencari apa pun
                bubbleView?.visibility = View.VISIBLE
            }
    }

    private fun openGoogleSearch(query: String) {
        if (query.isBlank()) {
            bubbleView?.visibility = View.VISIBLE
            return
        }
        val searchIntent = Intent(Intent.ACTION_WEB_SEARCH).apply {
            putExtra(android.app.SearchManager.QUERY, query)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val resolvable = searchIntent.resolveActivity(packageManager) != null
        val finalIntent = if (resolvable) {
            searchIntent
        } else {
            // Fallback: buka via browser jika ACTION_WEB_SEARCH tidak tersedia di perangkat
            Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("https://www.google.com/search?q=" + Uri.encode(query))
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        startActivity(finalIntent)
        bubbleView?.visibility = View.VISIBLE
    }

    // ---------------------------------------------------------------------
    // Cleanup
    // ---------------------------------------------------------------------
    private fun stopEverything() {
        closeCropOverlay()
        bubbleView?.let { runCatching { windowManager.removeView(it) } }
        bubbleView = null
        virtualDisplay?.release()
        imageReader?.close()
        mediaProjection?.stop()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }
}

// =============================================================================
// View kustom sederhana untuk bubble (lingkaran ikon)
// =============================================================================
private class ImageViewBubble(context: Context) : android.widget.ImageView(context) {
    init {
        setImageResource(com.jendelamengambang.app.R.drawable.ic_bubble)
        val sizePx = (56 * resources.displayMetrics.density).toInt()
        layoutParams = FrameLayout.LayoutParams(sizePx, sizePx)
        isClickable = true
    }
}

// =============================================================================
// View kustom baris tombol [Tutup/Batal] & [Cari Jawaban]
// =============================================================================
private class LinearLayoutButtons(
    context: Context,
    private val onAction: (confirmed: Boolean) -> Unit
) : LinearLayout(context) {
    init {
        orientation = HORIZONTAL
        val density = resources.displayMetrics.density
        setPadding((16 * density).toInt(), (10 * density).toInt(), (16 * density).toInt(), (10 * density).toInt())

        val btnCancel = android.widget.Button(context).apply {
            text = "Batal"
            setBackgroundColor(Color.parseColor("#EF4444"))
            setTextColor(Color.WHITE)
            setOnClickListener { onAction(false) }
        }
        val btnSearch = android.widget.Button(context).apply {
            text = "Cari Jawaban"
            setBackgroundColor(Color.parseColor("#5B6CFF"))
            setTextColor(Color.WHITE)
            setOnClickListener { onAction(true) }
        }

        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { marginEnd = (12 * density).toInt() }

        addView(btnCancel, lp)
        addView(btnSearch)
    }
}

// =============================================================================
// CropOverlayView: layar transparan penuh berisi kotak bidik yang bisa
// digeser (drag di tengah) dan diperbesar/diperkecil (drag dari sudut).
// Garis panjang di sisi ATAS & BAWAH, garis pendek di sisi KIRI & KANAN,
// sesuai spesifikasi desain.
// =============================================================================
private class CropOverlayView(
    context: Context,
    private val screenWidth: Int,
    private val screenHeight: Int
) : View(context) {

    private val cropRect: Rect

    private val dimPaint = Paint().apply {
        color = Color.parseColor("#99000000")
        style = Paint.Style.FILL
    }
    private val borderPaint = Paint().apply {
        color = Color.parseColor("#5B6CFF")
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    private val longLinePaint = Paint().apply {
        color = Color.WHITE
        strokeWidth = 8f
        strokeCap = Paint.Cap.ROUND
    }
    private val shortLinePaint = Paint().apply {
        color = Color.WHITE
        strokeWidth = 8f
        strokeCap = Paint.Cap.ROUND
    }
    private val cornerHandlePaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }

    private enum class DragMode { NONE, MOVE, RESIZE_TL, RESIZE_TR, RESIZE_BL, RESIZE_BR }
    private var dragMode = DragMode.NONE
    private var lastTouchX = 0f
    private var lastTouchY = 0f

    private val minSize = 120

    init {
        val defaultW = (screenWidth * 0.7f).toInt()
        val defaultH = (screenHeight * 0.18f).toInt()
        val left = (screenWidth - defaultW) / 2
        val top = (screenHeight - defaultH) / 2
        cropRect = Rect(left, top, left + defaultW, top + defaultH)
        setWillNotDraw(false)
    }

    fun getCropRect(): Rect = Rect(cropRect)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Area gelap di luar kotak bidik
        canvas.drawRect(0f, 0f, width.toFloat(), cropRect.top.toFloat(), dimPaint)
        canvas.drawRect(0f, cropRect.bottom.toFloat(), width.toFloat(), height.toFloat(), dimPaint)
        canvas.drawRect(0f, cropRect.top.toFloat(), cropRect.left.toFloat(), cropRect.bottom.toFloat(), dimPain
