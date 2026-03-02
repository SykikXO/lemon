package com.sykik.lemon.data.engine

import com.sykik.lemon.domain.engine.LlmEngine
import com.sykik.lemon.domain.model.LlmModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class MockLlmEngine : LlmEngine {
    
    private var isInitialized = false
    
    override suspend fun initialize(model: LlmModel): Result<Unit> {
        delay(1000) // Simulate loading time
        isInitialized = true
        return Result.success(Unit)
    }

    override fun generateResponse(prompt: String): Flow<String> = flow {
        if (!isInitialized) throw IllegalStateException("Engine not initialized")
        
        val responseWords = "This is a mock response from the placeholder engine simulating LLM stream generation.".split(" ")
        
        for (word in responseWords) {
            delay(150) // Simulate token generation delay
            emit("$word ")
        }
    }

    override suspend fun close() {
        isInitialized = false
    }
}
