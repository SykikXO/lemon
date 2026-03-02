package com.sykik.lemon.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sykik.lemon.domain.engine.LlmEngine
import com.sykik.lemon.domain.model.ChatMessage
import com.sykik.lemon.domain.model.LlmModel
import com.sykik.lemon.data.engine.OllamaDownloader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

import com.sykik.lemon.data.engine.OllamaScrapedModel
import com.sykik.lemon.data.engine.OllamaModelLister

data class ChatState(
    val messages: List<ChatMessage> = emptyList(),
    val isGenerating: Boolean = false,
    val availableModels: List<LlmModel> = listOf(
        LlmModel("mock-1", "Mock Model (Local Testing)", isDownloaded = true)
    ),
    val remoteModels: List<OllamaScrapedModel> = emptyList(),
    val selectedModel: LlmModel? = null,
    val isModelDownloadPopupVisible: Boolean = false,
    val isDownloadingModel: Boolean = false,
    val downloadStatusText: String = "",
    val errorMessage: String? = null
)

class ChatViewModel(
    private val llmEngine: LlmEngine,
    private val modelDir: String
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatState())
    val uiState: StateFlow<ChatState> = _uiState.asStateFlow()

    private val downloader = OllamaDownloader()
    private val modelLister = OllamaModelLister()

    init {
        // Scan for existing downloaded .gguf files
        scanForExistingModels()

        // Auto-select first available downloaded model that has a real path
        _uiState.value.availableModels.firstOrNull { it.isDownloaded && !it.absolutePath.isNullOrEmpty() }?.let { 
            selectModel(it)
        }
        
        viewModelScope.launch {
            downloader.downloadProgress.collect { progressText ->
                if (_uiState.value.isDownloadingModel && progressText.isNotEmpty()) {
                    _uiState.value = _uiState.value.copy(downloadStatusText = progressText)
                }
            }
        }
        
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val scraped = modelLister.getAllModels()
                _uiState.value = _uiState.value.copy(remoteModels = scraped)
            } catch (e: Exception) {
                // Ignore missing models fallback
            }
        }
    }

    private fun scanForExistingModels() {
        val dir = java.io.File(modelDir)
        if (!dir.exists() || !dir.isDirectory) return

        val ggufFiles = dir.listFiles { file ->
            file.isFile && file.name.endsWith(".gguf") && file.length() > 0
        } ?: return

        val existingModels = ggufFiles.map { file ->
            val displayName = file.nameWithoutExtension
                .replace("-", " ")
                .replace("_", "/")
            LlmModel(
                id = file.name,
                name = displayName,
                absolutePath = file.absolutePath,
                isDownloaded = true
            )
        }

        if (existingModels.isNotEmpty()) {
            _uiState.value = _uiState.value.copy(
                availableModels = existingModels
            )
        }
    }

    fun downloadModel(input: String, outputDir: String) {
        if (_uiState.value.isDownloadingModel) return

        val parts = input.split(":")
        val name = parts[0]
        val tag = parts.getOrNull(1) ?: "latest"

        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(
                isDownloadingModel = true,
                errorMessage = null,
                downloadStatusText = "Resolving digest for $name:$tag..."
            )
            
            try {
                // 1. Resolve Digest and Size from Ollama registry 
                val (digest, size) = downloader.resolveDigest(name, tag)

                _uiState.value = _uiState.value.copy(
                    downloadStatusText = "Downloading $name:$tag via parallel chunks (${size / 1_000_000} MB)..."
                )

                // 2. Download Model
                val filename = "${name.replace("/", "_")}-$tag.gguf"
                val file = downloader.download(name, digest, size, outputDir, filename)
                
                // Add downloaded model to state
                val newModel = LlmModel(
                    id = "$name:$tag",
                    name = "$name ($tag)",
                    absolutePath = file.absolutePath,
                    isDownloaded = true
                )

                _uiState.value = _uiState.value.copy(
                    availableModels = _uiState.value.availableModels + newModel,
                    isDownloadingModel = false,
                    isModelDownloadPopupVisible = false,
                    downloadStatusText = "✅ Loaded ${file.name} (${file.length() / 1_000_000}MB)"
                )
                
                selectModel(newModel)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Download Failed: ${e.message}",
                    isDownloadingModel = false,
                    downloadStatusText = "Error: ${e.message}"
                )
            }
        }
    }

    fun selectModel(model: LlmModel) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(selectedModel = model, errorMessage = null)
            if (model.absolutePath.isNullOrEmpty()) {
                android.util.Log.w("LlamaEngine", "selectModel skipped: no absolutePath for ${model.name}")
                return@launch
            }
            llmEngine.initialize(model).onFailure { error ->
                _uiState.value = _uiState.value.copy(errorMessage = "Failed to load model: ${error.message}")
            }
        }
    }

    fun toggleModelDownloadPopup() {
        _uiState.value = _uiState.value.copy(
            isModelDownloadPopupVisible = !_uiState.value.isModelDownloadPopupVisible
        )
    }

    fun deleteModel(model: LlmModel) {
        viewModelScope.launch(Dispatchers.IO) {
            // Close engine if this model is currently loaded
            if (_uiState.value.selectedModel?.id == model.id) {
                try { llmEngine.close() } catch (_: Exception) {}
            }

            // Delete the file
            model.absolutePath?.let { path ->
                val file = java.io.File(path)
                if (file.exists()) file.delete()
                // Also delete any partial file
                val partial = java.io.File("$path-partial")
                if (partial.exists()) partial.delete()
            }

            // Update UI state
            val updatedModels = _uiState.value.availableModels.filter { it.id != model.id }
            val newSelected = if (_uiState.value.selectedModel?.id == model.id) null else _uiState.value.selectedModel
            _uiState.value = _uiState.value.copy(
                availableModels = updatedModels,
                selectedModel = newSelected,
                errorMessage = null
            )
        }
    }

    fun sendMessage(text: String) {
        if (text.isBlank() || _uiState.value.isGenerating) return

        val userMessage = ChatMessage(UUID.randomUUID().toString(), text, isFromUser = true)
        
        _uiState.value = _uiState.value.copy(
            messages = _uiState.value.messages + userMessage,
            isGenerating = true,
            errorMessage = null
        )

        // Add an empty assistant message to stream into
        val assistantMessageId = UUID.randomUUID().toString()
        val initialAssistantMessage = ChatMessage(assistantMessageId, "", isFromUser = false)
        
        _uiState.value = _uiState.value.copy(
            messages = _uiState.value.messages + initialAssistantMessage
        )

        viewModelScope.launch {
            try {
                // Formatting context - simply pass the latest text for the mock engine.
                // For Llama.cpp, we would pass formatted prompt History.
                llmEngine.generateResponse(text).collect { token ->
                    updateAssistantMessage(assistantMessageId, token)
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Generation failed: ${e.message}"
                )
            } finally {
                _uiState.value = _uiState.value.copy(isGenerating = false)
            }
        }
    }

    private fun updateAssistantMessage(messageId: String, newText: String) {
        val currentMessages = _uiState.value.messages.toMutableList()
        val messageIndex = currentMessages.indexOfFirst { it.id == messageId }
        
        if (messageIndex != -1) {
            val existingMessage = currentMessages[messageIndex]
            currentMessages[messageIndex] = existingMessage.copy(
                text = existingMessage.text + newText
            )
            _uiState.value = _uiState.value.copy(messages = currentMessages)
        }
    }

    override fun onCleared() {
        super.onCleared()
        viewModelScope.launch { llmEngine.close() }
    }
}
