package com.sykik.lemon

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sykik.lemon.data.engine.LlamaCppEngineImpl
import com.sykik.lemon.presentation.ChatViewModel
import com.sykik.lemon.presentation.ui.ChatScreen
import com.sykik.lemon.presentation.ui.ModelDownloadPopup
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // Temporary factory until Dependency Injection (like Hilt) is added
                    val factory = object : ViewModelProvider.Factory {
                        override fun <T : ViewModel> create(modelClass: Class<T>): T {
                            return ChatViewModel(LlamaCppEngineImpl(this@MainActivity.applicationContext)) as T
                        }
                    }
                    
                    val viewModel: ChatViewModel = viewModel(factory = factory)
                    val state by viewModel.uiState.collectAsState()

            MaterialTheme(
                colorScheme = if (state.isDarkMode) darkColorScheme() else lightColorScheme()
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ChatScreen(
                        state = state,
                        onSendMessage = viewModel::sendMessage,
                        onModelSelected = viewModel::selectModel,
                        onManageModelsClicked = viewModel::toggleModelDownloadPopup
                    )

                    if (state.isModelDownloadPopupVisible) {
                        ModelDownloadPopup(
                            isDownloading = state.isDownloadingModel,
                            statusText = state.downloadStatusText,
                            remoteModels = state.remoteModels,
                            onDownloadModel = { modelName ->
                                val outputDir = getExternalFilesDir(null)?.absolutePath ?: filesDir.absolutePath
                                viewModel.downloadModel(modelName, outputDir)
                            },
                            onDismiss = viewModel::toggleModelDownloadPopup
                        )
                    }
                }
            }
        }
    }
}
