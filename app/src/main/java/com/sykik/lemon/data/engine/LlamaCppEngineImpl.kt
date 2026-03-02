package com.sykik.lemon.data.engine

import android.content.Context
import com.sykik.lemon.domain.engine.LlmEngine
import com.sykik.lemon.domain.model.LlmModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.takeWhile
import org.nehuatl.llamacpp.LlamaHelper
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

class LlamaCppEngineImpl(private val context: Context) : LlmEngine {

    private val scope = CoroutineScope(Dispatchers.IO)
    private val eventFlow = MutableSharedFlow<LlamaHelper.LLMEvent>(extraBufferCapacity = 256)
    private var helper: LlamaHelper? = null

    override suspend fun initialize(model: LlmModel): Result<Unit> {
        return try {
            helper?.release()
            helper = LlamaHelper(context.contentResolver, scope, eventFlow)
            
            suspendCancellableCoroutine { continuation ->
                helper?.load(model.absolutePath ?: "", 2048) { _ ->
                    continuation.resume(Result.success(Unit))
                }
                
                // If it fails immediately or doesn't invoke callback, we might hang here,
                // but we trust the library invokes the onLoaded callback.
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun generateResponse(prompt: String): Flow<String> {
        val currentHelper = helper ?: throw IllegalStateException("Engine not initialized")
        val formattedPrompt = "<|user|>\n$prompt<|end|>\n<|assistant|>\n"
        
        currentHelper.predict(formattedPrompt, false)
        
        return eventFlow
            .takeWhile { it !is LlamaHelper.LLMEvent.Done && it !is LlamaHelper.LLMEvent.Error }
            .filterIsInstance<LlamaHelper.LLMEvent.Ongoing>()
            .map { it.word }
            .onCompletion {
                currentHelper.stopPrediction()
            }
    }

    override suspend fun close() {
        helper?.stopPrediction()
        helper?.release()
        helper = null
    }
}
