package com.paraooo.screentranslator.feature

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.common.moduleinstall.ModuleInstall
import com.google.android.gms.common.moduleinstall.ModuleInstallRequest
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

// Context가 필요하므로 AndroidViewModel을 사용합니다.
class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(LanguageUiState())
    val uiState: StateFlow<LanguageUiState> = _uiState.asStateFlow()

    private val translateModelManager = RemoteModelManager.getInstance()
    private val moduleInstallClient = ModuleInstall.getClient(application)

    fun checkAndDownloadModels(sourceLang: String, targetLang: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, message = "모델 확인 중...") }
            try {
                // 1. OCR 모델 다운로드 (ModuleInstallClient 방식)
                downloadOcrModelIfNeeded(sourceLang)

                // 2. 번역 모델 다운로드 (RemoteModelManager 방식)
                downloadTranslationModelsIfNeeded(sourceLang, targetLang)

                // 3. 모든 다운로드 완료
                _uiState.update { it.copy(isLoading = false, isReady = true, message = "모든 모델이 준비되었습니다.") }

            } catch (e: Exception) {
                Log.e("LanguageViewModel", "Model download failed", e)
                _uiState.update { it.copy(isLoading = false, message = "오류: ${e.message}") }
            }
        }
    }

    private suspend fun downloadOcrModelIfNeeded(langCode: String) {
        // 라틴어는 다운로드 불필요
        if (langCode == "en") {
            _uiState.update { it.copy(message = "영어 OCR 모델은 내장되어 있습니다.") }
            return
        }
        _uiState.update { it.copy(message = "$langCode OCR 모듈 준비 중...") }

        // 1. 필요한 OCR 인식기 클라이언트 생성
        val recognizer = when (langCode) {
            "ja" -> TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
            "ko" -> TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
            "zh" -> TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
            else -> TextRecognition.getClient(TextRecognizerOptions.Builder().build())
        }

        // 2. 모듈 설치 요청 생성
        val installRequest = ModuleInstallRequest.newBuilder()
            .addApi(recognizer) // 클라이언트 자체를 API로 추가
            .build()

        // 3. 모듈 설치 및 다운로드
        moduleInstallClient.installModules(installRequest).await()
        Log.d("LanguageViewModel", "OCR module for $langCode is now ready.")
    }

    private suspend fun downloadTranslationModelsIfNeeded(sourceLang: String, targetLang: String) {
        _uiState.update { it.copy(message = "$sourceLang -> $targetLang 번역 모델 준비 중...") }

        // 소스 언어 모델
        val sourceModel = TranslateRemoteModel.Builder(sourceLang).build()
        if (!isModelDownloaded(sourceModel)) {
            Log.d("LanguageViewModel", "Downloading source translation model: $sourceLang")
            translateModelManager.download(sourceModel, com.google.mlkit.common.model.DownloadConditions.Builder().build()).await()
        }

        // 타겟 언어 모델
        val targetModel = TranslateRemoteModel.Builder(targetLang).build()
        if (!isModelDownloaded(targetModel)) {
            Log.d("LanguageViewModel", "Downloading target translation model: $targetLang")
            translateModelManager.download(targetModel, com.google.mlkit.common.model.DownloadConditions.Builder().build()).await()
        }
        Log.d("LanguageViewModel", "Translation models are now ready.")
    }

    private suspend fun isModelDownloaded(model: TranslateRemoteModel): Boolean {
        return translateModelManager.getDownloadedModels(TranslateRemoteModel::class.java).await().contains(model)
    }
}

// 데이터 클래스는 변경 없음
data class LanguageUiState(
    val isLoading: Boolean = false,
    val isReady: Boolean = false,
    val message: String = "언어를 선택하고 모델을 준비해주세요."
)