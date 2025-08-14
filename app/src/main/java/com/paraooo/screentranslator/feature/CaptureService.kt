package com.paraooo.screentranslator.feature

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.WindowManager
import androidx.annotation.RequiresApi
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.paraooo.screentranslator.R
import java.nio.ByteBuffer
import androidx.core.graphics.createBitmap
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc


private fun imageToBitmap(image: android.media.Image): Bitmap {
    val planes = image.planes
    val buffer: ByteBuffer = planes[0].buffer
    val pixelStride = planes[0].pixelStride
    val rowStride = planes[0].rowStride
    val rowPadding = rowStride - pixelStride * image.width

    val bitmap = createBitmap(image.width + rowPadding / pixelStride, image.height)
    bitmap.copyPixelsFromBuffer(buffer)

    return Bitmap.createBitmap(bitmap, 0, 0, image.width, image.height)
}

class CaptureService : Service() {

    private lateinit var mediaProjectionManager: MediaProjectionManager
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null

    private var sourceLanguage: String = "ja" // 기본값
    private var targetLanguage: String = "ko" // 기본값
    private var translator: Translator? = null


    // ★★★ 1. MediaProjection 상태 변화를 감지할 콜백 객체 생성 (기존 코드 유지) ★★★
    private val mediaProjectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            super.onStop()
            Log.w(TAG, "MediaProjection session was stopped. Cleaning up.")
            stopCapture()
        }
    }

    override fun onCreate() {
        super.onCreate()
        mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    }

    // @RequiresApi 어노테이션은 불필요하므로 제거해도 좋습니다 (내부에서 버전 분기 처리)
    @RequiresApi(Build.VERSION_CODES.O)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopCapture()
            return START_NOT_STICKY
        }

        startForegroundWithNotification()

        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED) ?: Activity.RESULT_CANCELED
        val data = intent?.getParcelableExtra<Intent>(EXTRA_DATA)

        // ★★★ 서비스가 시작될 때마다 언어 설정을 받아 번역기를 준비합니다 ★★★
        this.sourceLanguage = intent?.getStringExtra("SOURCE_LANG") ?: "ja"
        this.targetLanguage = intent?.getStringExtra("TARGET_LANG") ?: "ko"
        prepareTranslator(sourceLanguage, targetLanguage)


        if (resultCode == Activity.RESULT_OK && data != null) {
            startCapture(resultCode, data)
        } else {
            // resultCode가 OK가 아닌 경우(예: 서비스 재시작)에는 startCapture를 호출하지 않습니다.
            // 하지만 서비스 자체는 계속 실행될 수 있습니다.
            Log.d(TAG, "Service started without new capture data (resultCode: $resultCode)")
        }

        return START_STICKY
    }

    /**
     * ★★★ 2. 번역기를 안전하게 준비(또는 재설정)하는 함수 ★★★
     */
    private fun prepareTranslator(sourceLang: String, targetLang: String) {
        Log.d(TAG, "Preparing translator for $sourceLang -> $targetLang")
        // 기존 번역기가 있다면 안전하게 리소스를 해제합니다.
        translator?.close()

        // 새로운 언어 설정으로 옵션을 만듭니다.
        val options = TranslatorOptions.Builder()
            .setSourceLanguage(sourceLang)
            .setTargetLanguage(targetLang)
            .build()

        // 새로운 번역기 인스턴스를 생성합니다.
        translator = Translation.getClient(options)
    }

    private fun processImage(bitmap: Bitmap) {
        val mat = Mat()
        Utils.bitmapToMat(bitmap, mat)
        Imgproc.cvtColor(mat, mat, Imgproc.COLOR_BGR2GRAY)
        Imgproc.threshold(mat, mat, 0.0, 255.0, Imgproc.THRESH_BINARY or Imgproc.THRESH_OTSU)
        val processedBitmap = createBitmap(mat.cols(), mat.rows())
        Utils.matToBitmap(mat, processedBitmap)

        val image = InputImage.fromBitmap(processedBitmap, 0)

        // sourceLanguage 변수는 이제 onStartCommand에서 설정되므로, 여기서 직접 사용합니다.
        val recognizerOptions = when (this.sourceLanguage) {
            "ja" -> com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions.Builder().build()
            "ko" -> com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions.Builder().build()
            "zh" -> com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions.Builder().build()
            else -> com.google.mlkit.vision.text.latin.TextRecognizerOptions.Builder().build()
        }
        val recognizer = TextRecognition.getClient(recognizerOptions)

        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                val extractedText = visionText.text
                if (extractedText.isNotBlank()) {
                    translateText(extractedText)
                }
            }
            .addOnFailureListener { e -> Log.e(TAG, "OCR Failed: ${e.message}") }
    }

    private fun translateText(text: String) {
        Log.d(TAG, "translateText: $text")
        translator?.translate(text)?.addOnSuccessListener { translatedText ->
            Log.d(TAG, "SUCCESS: $text -> $translatedText")
            // TODO: 오버레이로 띄우기
        }?.addOnFailureListener { e ->
            Log.e(TAG, "Translation failed for '$text'", e)
        }
    }

    private fun startCapture(resultCode: Int, data: Intent) {
        mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data)

        if (mediaProjection == null) {
            Log.e(TAG, "Failed to get MediaProjection.")
            stopSelf()
            return
        }

        mediaProjection?.registerCallback(mediaProjectionCallback, Handler(Looper.getMainLooper()))

        val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val width: Int
        val height: Int

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val metrics = windowManager.currentWindowMetrics
            width = metrics.bounds.width()
            height = metrics.bounds.height()
        } else {
            @Suppress("DEPRECATION")
            val display = windowManager.defaultDisplay
            @Suppress("DEPRECATION")
            width = display.width
            @Suppress("DEPRECATION")
            height = display.height
        }
        val density = resources.displayMetrics.densityDpi

        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2).apply {
            setOnImageAvailableListener({ reader ->
                val image = reader.acquireLatestImage()
                if (image != null) {
                    Log.d(TAG, ">>>>>> New image captured: ${image.timestamp} <<<<<<")
                    processImage(imageToBitmap(image))
                    image.close()
                }
            }, null)
        }

        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "ScreenCapture",
            width, height, density,
            0,
            imageReader?.surface,
            null,
            null
        )
        Log.d(TAG, "startCapture: Virtual display created successfully.")
    }

    private fun stopCapture() {
        Log.d(TAG, "stopCapture: Stopping projection and releasing resources.")
        virtualDisplay?.release()
        imageReader?.close()
        mediaProjection?.unregisterCallback(mediaProjectionCallback)
        mediaProjection?.stop()
        mediaProjection = null
        stopSelf()
    }

    // @RequiresApi 어노테이션은 불필요하므로 제거해도 좋습니다
    @RequiresApi(Build.VERSION_CODES.O)
    private fun startForegroundWithNotification() {
        val channelId = "capture_service_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "화면 캡처 서비스", NotificationManager.IMPORTANCE_DEFAULT)
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }

        val notification: Notification = Notification.Builder(this, channelId)
            .setContentTitle("번역 서비스 실행 중")
            .setContentText("화면의 텍스트를 실시간으로 번역하고 있습니다.")
            .setSmallIcon(R.mipmap.ic_launcher)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(SERVICE_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(SERVICE_ID, notification)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        translator?.close() // 서비스가 완전히 소멸될 때도 리소스를 해제합니다.
        stopCapture()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "CaptureService"
        private const val SERVICE_ID = 101
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_DATA = "data"
        const val ACTION_STOP = "action_stop"
    }
}