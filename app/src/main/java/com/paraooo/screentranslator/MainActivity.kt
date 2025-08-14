package com.paraooo.screentranslator

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.paraooo.screentranslator.feature.HomeScreen
import com.paraooo.screentranslator.ui.theme.ScreenTranslatorTheme
import androidx.core.net.toUri
import com.paraooo.screentranslator.feature.CaptureService
class MainActivity : ComponentActivity() {

    // ★★★ 1. 사용자가 선택한 언어를 기억할 멤버 변수 추가 ★★★
    private var selectedSourceLang: String = "ja"
    private var selectedTargetLang: String = "ko"

    @RequiresApi(Build.VERSION_CODES.O)
    private val screenCaptureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            // ★★★ 2. 기억해 둔 언어 정보로 서비스 인텐트 생성 ★★★
            val serviceIntent = Intent(this, CaptureService::class.java).apply {
                putExtra(CaptureService.EXTRA_RESULT_CODE, result.resultCode)
                putExtra(CaptureService.EXTRA_DATA, result.data)
                putExtra("SOURCE_LANG", selectedSourceLang)
                putExtra("TARGET_LANG", selectedTargetLang)
            }
            startForegroundService(serviceIntent)
        } else {
            Toast.makeText(this, "화면 공유를 허용해야 합니다.", Toast.LENGTH_SHORT).show()
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun startScreenCapture() {
        val mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        // 화면 캡처 인텐트에는 이제 언어 정보를 담을 필요가 없습니다.
        val captureIntent = mediaProjectionManager.createScreenCaptureIntent()
        screenCaptureLauncher.launch(captureIntent)
    }

    // @RequiresApi 어노테이션은 이제 불필요
    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ScreenTranslatorTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    HomeScreen(
                        onStartCapture = { sourceLang, targetLang ->
                            // ★★★ 3. 사용자가 버튼을 누르면, 선택된 언어를 멤버 변수에 저장 ★★★
                            this.selectedSourceLang = sourceLang
                            this.selectedTargetLang = targetLang
                            startScreenCapture()
                        }
                    )
                }
            }
        }
    }
}