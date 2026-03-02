package com.sykik.lemon.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sykik.lemon.domain.engine.LlmEngine
import com.sykik.lemon.domain.model.ChatMessage
import com.sykik.lemon.domain.model.LlmModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

data class ChatState(
    val messages: List<ChatMessage> = emptyList(),
    val isGenerating: Boolean = false,
    val availableModels: List<LlmModel> = listOf(
        LlmModel("mock-1", "Mock Model (Local Testing)", isDownloaded = true)
    ),
    val selectedModel: LlmModel? = null,
    val isModelDownloadPopupVisible: Boolean = false,
    val errorMessage: String? = null
)

class ChatViewModel(
    private val llmEngine: LlmEngine
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatState())
    val uiState: StateFlow<ChatState> = _uiState.asStateFlow()

    init {
        // Auto-select first available downloaded model
        _uiState.value.availableModels.firstOrNull { it.isDownloaded }?.let { 
            selectModel(it)
        }
    }

    fun selectModel(model: LlmModel) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(selectedModel = model, errorMessage = null)
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
