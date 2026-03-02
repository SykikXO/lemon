package com.sykik.lemon.data.engine

import com.sykik.lemon.domain.model.LlmModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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
    private val client = OkHttpClient()

    private val _downloadProgress = MutableStateFlow<String>("")
    val downloadProgress: StateFlow<String> = _downloadProgress.asStateFlow()

    /**
     * Resolves the SHA-256 digest from the Ollama Registry for a specific model namespace.
     * @param modelName e.g., "llama3.2"
     * @param tag e.g., "latest" or "3b"
     * @return Pair containing digest (String) and total size in bytes (Long).
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
     * Downloads an LLM model chunk by chunk from Ollama's registry into the given output directory.
     * @param modelName The registry namespace (e.g. "llama3.2" or "phi3")
     * @param digest The SHA256 of the model layer.
     * @param totalSize The total size of the chunk extracted from the registry.
     * @param outputDir The Directory to save the final .gguf file.
     * @param filename What you want the output file named.
     */
    suspend fun download(modelName: String, digest: String, totalSize: Long, outputDir: String, filename: String): File = coroutineScope {
        val blob = BlobDownload(
            digest = digest,
            name = "$outputDir/$filename",
            total = totalSize
        )
        
        // Ensure outputDir exists
        File(outputDir).mkdirs()

        // STEP 2: Slice into 16 parts 
        val partSize = (blob.total / 16).coerceIn(100*1024*1024, 1000*1024*1024)
        repeat(16) { i ->
            val offset = i * partSize
            val size = minOf(partSize, blob.total - offset)
            if (size > 0) {
                blob.parts.add(BlobDownloadPart(i, offset, size))
            }
        }

        // STEP 3: PARALLEL DOWNLOAD
        val file = File(blob.name + "-partial").apply {
            createNewFile()
        }

        blob.parts.forEach { part ->
            launch(Dispatchers.IO) {
                downloadPart(modelName, file, part, digest, blob)
            }
        }

        // Wait all parts (this happens organically if launch doesn't crash, wrapper coroutineScope blocks until children finish)
        file.renameTo(File(blob.name))
        File(blob.name)
    }



    private suspend fun downloadPart(modelName: String, file: File, part: BlobDownloadPart, digest: String, blob: BlobDownload) {
        val url = "https://registry.ollama.ai/v2/library/$modelName/blobs/sha256:$digest"
        val req = Request.Builder()
            .url(url)
            .header("Range", "bytes=${part.offset}-${part.offset + part.size - 1}")
            .build()
            
        client.newCall(req).execute().use { resp ->
            resp.body?.byteStream()?.use { inputStream ->
                java.io.RandomAccessFile(file, "rw").use { raf ->
                    raf.seek(part.offset)
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        raf.write(buffer, 0, bytesRead)
                        part.completed += bytesRead
                        
                        // Output total progress dynamically every ~10MB to avoid logcat spam
                        if (System.currentTimeMillis() - part.lastUpdated > 2000) {
                             part.lastUpdated = System.currentTimeMillis()
                             val sumCompleted = blob.parts.sumOf { it.completed }
                             val percent = (sumCompleted.toDouble() / blob.total * 100).toInt()
                             val mbDownloaded = sumCompleted / 1_000_000
                             val mbTotal = blob.total / 1_000_000
                             val logStr = "Downloading $modelName: $mbDownloaded / $mbTotal MB ($percent%)"
                             Log.d("OllamaDownloader", logStr)
                             _downloadProgress.value = logStr
                        }
                    }
                }
            }
        }
    }
}
