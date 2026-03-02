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
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withTimeoutOrNull
import org.nehuatl.llamacpp.LlamaAndroid
import org.nehuatl.llamacpp.LlamaContext
import org.nehuatl.llamacpp.LlamaHelper
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import android.util.Log

class LlamaCppEngineImpl(private val context: Context) : LlmEngine {

    private val scope = CoroutineScope(Dispatchers.IO)
    private var llamaContext: LlamaContext? = null

    override suspend fun initialize(model: LlmModel): Result<Unit> {
        return try {
            if (model.absolutePath.isNullOrEmpty()) {
                return Result.failure(IllegalArgumentException("Model path is null or empty"))
            }

            // Validate file before passing to native code (corrupted models cause SIGSEGV)
            val modelFile = java.io.File(model.absolutePath)
            if (!modelFile.exists()) {
                return Result.failure(java.io.FileNotFoundException("Model file not found: ${model.absolutePath}"))
            }
            if (!modelFile.canRead()) {
                return Result.failure(SecurityException("Cannot read model file: ${model.absolutePath}"))
            }
            if (modelFile.length() < 1_000_000) { // < 1MB is definitely not a valid model
                return Result.failure(IllegalStateException("Model file too small (${modelFile.length()} bytes), likely corrupted or incomplete"))
            }
            // Check GGUF magic number (first 4 bytes should be "GGUF")
            try {
                java.io.FileInputStream(modelFile).use { fis ->
                    val magic = ByteArray(4)
                    if (fis.read(magic) != 4 || String(magic) != "GGUF") {
                        return Result.failure(IllegalStateException("Invalid model file: not a GGUF format"))
                    }
                }
            } catch (e: Exception) {
                return Result.failure(IllegalStateException("Cannot read model file header: ${e.message}"))
            }

            val oldContext = llamaContext
            
            Log.d("LlamaEngine", "Triggering helper.load with ${model.absolutePath}")
            val config = mapOf(
                "model" to (model.absolutePath ?: ""),
                "model_fd" to -1, // Pass -1 to bypass FD in JNI
                "use_mmap" to false,
                "use_mlock" to false,
                "n_ctx" to 2048,
                "n_threads" to 4,
            )
            
            // Create new context first — only release old after success
            val newContext = LlamaContext(1, config)
            newContext.setTokenCallback { }
            
            // New context succeeded — now release old one
            oldContext?.release()
            llamaContext = newContext
            
            Log.d("LlamaEngine", "Context successfully loaded manually via JNI with ID = ${llamaContext?.context}")
            
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("LlamaEngine", "Failed to initialize native context", e)
            Result.failure(e)
        }
    }

    override fun generateResponse(prompt: String): Flow<String> = kotlinx.coroutines.flow.channelFlow {
        val currentContext = llamaContext ?: throw IllegalStateException("Engine not initialized")
        
        // Use the model's built-in chat template instead of hardcoding one
        // This prevents crashes with models that use different token formats (e.g. thinking models)
        val formattedPrompt = try {
            val messages = listOf(
                mapOf("role" to "user", "content" to prompt)
            )
            currentContext.getFormattedChat(messages, "")
        } catch (e: Exception) {
            Log.w("LlamaEngine", "getFormattedChat failed, using raw prompt", e)
            prompt
        }
        
        Log.d("LlamaEngine", "Triggering predict() on context for prompt: $prompt")
        
        currentContext.setTokenCallback { token ->
            Log.d("LlamaEngine", "Generated token: $token")
            trySend(token).isSuccess
        }
        
        try {
            val params = mapOf(
                "prompt" to formattedPrompt,
                "n_predict" to 512,
                "emit_partial_completion" to true,
                "temperature" to 0.7,
                "top_p" to 0.95,
                "top_k" to 40
            )
            
            currentContext.completion(params)
            Log.d("LlamaEngine", "Completion finished.")
        } catch(e: Exception) {
            Log.e("LlamaEngine", "Error generating completion", e)
        } finally {
            close() // close the channelFlow
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun close() {
        llamaContext?.stopCompletion()
        llamaContext?.release()
        llamaContext = null
    }
}
