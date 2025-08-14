package com.paraooo.screentranslator.feature

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onStartCapture: (sourceLang: String, targetLang: String) -> Unit,
    viewModel: HomeViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    // 언어 선택 상태 관리
    val languages = listOf("ja" to "Japanese", "ko" to "Korean", "en" to "English", "zh" to "Chinese")
    var sourceLang by remember { mutableStateOf(languages.first()) }
    var targetLang by remember { mutableStateOf(languages[1]) }
    var sourceExpanded by remember { mutableStateOf(false) }
    var targetExpanded by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("어떤 언어를 번역할까요?", style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(24.dp))

            // 원본 언어 선택
            ExposedDropdownMenuBox(expanded = sourceExpanded, onExpandedChange = { sourceExpanded = !sourceExpanded }) {
                OutlinedTextField(
                    value = sourceLang.second,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("원본 언어") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = sourceExpanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(expanded = sourceExpanded, onDismissRequest = { sourceExpanded = false }) {
                    languages.forEach { lang ->
                        DropdownMenuItem(
                            text = { Text(lang.second) },
                            onClick = {
                                sourceLang = lang
                                sourceExpanded = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 번역 언어 선택
            ExposedDropdownMenuBox(expanded = targetExpanded, onExpandedChange = { targetExpanded = !targetExpanded }) {
                OutlinedTextField(
                    value = targetLang.second,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("번역 언어") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = targetExpanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(expanded = targetExpanded, onDismissRequest = { targetExpanded = false }) {
                    languages.forEach { lang ->
                        DropdownMenuItem(
                            text = { Text(lang.second) },
                            onClick = {
                                targetLang = lang
                                targetExpanded = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // 모델 준비 버튼
            Button(
                onClick = { viewModel.checkAndDownloadModels(sourceLang.first, targetLang.first) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("선택한 언어 모델 준비하기")
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(text = uiState.message, style = MaterialTheme.typography.bodySmall)
            Spacer(modifier = Modifier.height(32.dp))

            // 번역 시작 버튼
            Button(
                onClick = { onStartCapture(sourceLang.first, targetLang.first) },
                enabled = uiState.isReady, // 모델이 준비되어야 활성화
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Text("번역 시작하기")
            }
        }

        // 로딩 오버레이
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable(enabled = false, onClick = {}),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color.White)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = uiState.message, color = Color.White)
                }
            }
        }
    }
}