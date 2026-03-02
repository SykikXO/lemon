package com.sykik.lemon.data.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import android.util.Log
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.TimeUnit

data class BlobDownload(
    val digest: String,
    val name: String,
    var total: Long = 0,
    val parts: MutableList<BlobDownloadPart> = mutableListOf()
)

data class BlobDownloadPart(
    val n: Int,
    val offset: Long,
    val size: Long,
    var completed: Long = 0,
    var lastUpdated: Long = 0
)

class OllamaDownloader {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val _downloadProgress = MutableStateFlow<String>("")
    val downloadProgress: StateFlow<String> = _downloadProgress.asStateFlow()

    companion object {
        private const val TAG = "OllamaDownloader"
        private const val NUM_PARTS = 16
        private const val MAX_RETRIES = 3
    }

    /**
     * Resolves the SHA-256 digest from the Ollama Registry for a specific model namespace.
     */
    suspend fun resolveDigest(modelName: String, tag: String = "latest"): Pair<String, Long> = coroutineScope {
        val commonTags = listOf(tag, "latest", "1b", "3b", "7b", "mini").distinct()
        
        for (t in commonTags) {
            try {
                val manifestUrl = "https://registry.ollama.ai/v2/library/$modelName/manifests/$t"
                val request = Request.Builder().url(manifestUrl).build()

                val responseString = client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) response.body?.string() else null
                }
                
                if (responseString != null) {
                    val json = JSONObject(responseString)
                    val layer = json.getJSONArray("layers")
                        .getJSONObject(0)
                        
                    return@coroutineScope Pair(
                        layer.getString("digest").removePrefix("sha256:"),
                        layer.getLong("size")
                    )
                }
            } catch (e: Exception) {
                // Ignore and try next tag
            }
        }
        
        throw Exception("Model $modelName:$tag not found. Tried tags: $commonTags")
    }

    /**
     * Downloads an LLM model with parallel chunks and per-part retry.
     * If a part fails, it retries up to MAX_RETRIES times.
     * Uses supervisorScope so one part failure doesn't cancel others.
     */
    suspend fun download(modelName: String, digest: String, totalSize: Long, outputDir: String, filename: String): File {
        val blob = BlobDownload(
            digest = digest,
            name = "$outputDir/$filename",
            total = totalSize
        )
        
        File(outputDir).mkdirs()

        // Slice into NUM_PARTS parts
        val partSize = blob.total / NUM_PARTS
        for (i in 0 until NUM_PARTS) {
            val offset = i * partSize
            val size = if (i == NUM_PARTS - 1) blob.total - offset else partSize
            if (size > 0) {
                blob.parts.add(BlobDownloadPart(i, offset, size))
            }
        }

        val partialFile = File(blob.name + "-partial")
        
        // Resume: check if partial file exists and has content
        if (partialFile.exists() && partialFile.length() > 0) {
            Log.d(TAG, "Resuming download, partial file size: ${partialFile.length()}")
            _downloadProgress.value = "Resuming download..."
        } else {
            partialFile.createNewFile()
            // Pre-allocate: write a single byte at the end to reserve space
            java.io.RandomAccessFile(partialFile, "rw").use { raf ->
                raf.setLength(totalSize)
            }
        }

        // Parallel download with supervisorScope — one part failure won't cancel others
        supervisorScope {
            val jobs = blob.parts.map { part ->
                async(Dispatchers.IO) {
                    downloadPartWithRetry(modelName, partialFile, part, digest, blob)
                }
            }
            jobs.awaitAll()
        }

        // Verify all parts completed
        val totalCompleted = blob.parts.sumOf { it.completed }
        if (totalCompleted < blob.total * 0.99) { // Allow tiny rounding margin
            throw Exception("Download incomplete: got $totalCompleted of ${blob.total} bytes")
        }

        // Rename partial to final
        val finalFile = File(blob.name)
        if (finalFile.exists()) finalFile.delete()
        partialFile.renameTo(finalFile)
        Log.d(TAG, "Download complete: ${finalFile.absolutePath} (${finalFile.length()} bytes)")
        return finalFile
    }

    private suspend fun downloadPartWithRetry(
        modelName: String, file: File, part: BlobDownloadPart, digest: String, blob: BlobDownload
    ) {
        var attempt = 0
        while (attempt < MAX_RETRIES) {
            try {
                downloadPart(modelName, file, part, digest, blob)
                return // Success
            } catch (e: Exception) {
                attempt++
                Log.w(TAG, "Part ${part.n} failed (attempt $attempt/$MAX_RETRIES): ${e.message}")
                if (attempt >= MAX_RETRIES) {
                    Log.e(TAG, "Part ${part.n} failed permanently after $MAX_RETRIES attempts")
                    _downloadProgress.value = "⚠️ Part ${part.n} failed: ${e.message}"
                    throw e
                }
                // Reset part progress for retry, offset from where we left off
                val alreadyDownloaded = part.completed
                Log.d(TAG, "Retrying part ${part.n} from byte offset ${part.offset + alreadyDownloaded}")
            }
        }
    }

    private fun downloadPart(modelName: String, file: File, part: BlobDownloadPart, digest: String, blob: BlobDownload) {
        val resumeOffset = part.offset + part.completed
        val endByte = part.offset + part.size - 1
        
        if (resumeOffset >= endByte) {
            Log.d(TAG, "Part ${part.n} already complete, skipping")
            return
        }

        val url = "https://registry.ollama.ai/v2/library/$modelName/blobs/sha256:$digest"
        val req = Request.Builder()
            .url(url)
            .header("Range", "bytes=$resumeOffset-$endByte")
            .build()
            
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw Exception("HTTP ${resp.code} for part ${part.n}")
            }
            resp.body?.byteStream()?.use { inputStream ->
                java.io.RandomAccessFile(file, "rw").use { raf ->
                    raf.seek(resumeOffset)
                    val buffer = ByteArray(32 * 1024) // 32KB buffer for faster IO
                    var bytesRead: Int
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        raf.write(buffer, 0, bytesRead)
                        part.completed += bytesRead
                        
                        if (System.currentTimeMillis() - part.lastUpdated > 2000) {
                             part.lastUpdated = System.currentTimeMillis()
                             val sumCompleted = blob.parts.sumOf { it.completed }
                             val percent = (sumCompleted.toDouble() / blob.total * 100).toInt()
                             val mbDownloaded = sumCompleted / 1_000_000
                             val mbTotal = blob.total / 1_000_000
                             _downloadProgress.value = "⬇️ $modelName: $mbDownloaded / $mbTotal MB ($percent%)"
                        }
                    }
                }
            }
        }
    }
}
