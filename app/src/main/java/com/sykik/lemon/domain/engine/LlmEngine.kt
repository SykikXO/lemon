package com.sykik.lemon.domain.engine

import com.sykik.lemon.domain.model.LlmModel
import kotlinx.coroutines.flow.Flow

interface LlmEngine {
    /**
     * Initializes the engine with a specific model.
     */
    suspend fun initialize(model: LlmModel): Result<Unit>
    
    /**
     * Generates a streaming response from the LLM based on the given prompt context.
     * @param prompt The full conversational text context.
     * @return A hot Flow emitting chunks of generated text as they become available.
     */
    fun generateResponse(prompt: String): Flow<String>

    /**
     * Cleans up resources held by the engine when it's no longer needed.
     */
    suspend fun close()
}
