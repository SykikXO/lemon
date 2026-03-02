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

    /**
     * Resolves the SHA-256 digest from the Ollama Registry for a specific model namespace.
     * @param modelName e.g., "llama3.2"
     * @param tag e.g., "latest" or "3b"
     */
    suspend fun resolveDigest(modelName: String, tag: String = "latest"): String = coroutineScope {
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
                    return@coroutineScope json.getJSONArray("layers")
                        .getJSONObject(0)
                        .getString("digest")
                        .removePrefix("sha256:")
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
     * @param outputDir The Directory to save the final .gguf file.
     * @param filename What you want the output file named.
     */
    suspend fun download(modelName: String, digest: String, outputDir: String, filename: String): File = coroutineScope {
        val blob = BlobDownload(
            digest = digest,
            name = "$outputDir/$filename"
        )
        
        // Ensure outputDir exists
        File(outputDir).mkdirs()

        // STEP 1: HEAD gets total size
        blob.total = headRequest("https://registry.ollama.ai/v2/library/$modelName/blobs/sha256:$digest")

        // STEP 2: Slice into 16 parts 
        val partSize = (blob.total / 16).coerceIn(100*1024*1024, 1000*1024*1024)
        repeat(16) { i ->
            val offset = i * partSize
            val size = minOf(partSize, blob.total - offset)
            blob.parts.add(BlobDownloadPart(i, offset, size))
        }

        // STEP 3: PARALLEL DOWNLOAD
        val file = File(blob.name + "-partial").apply {
            createNewFile()
        }

        blob.parts.forEach { part ->
            launch(Dispatchers.IO) {
                downloadPart(modelName, file, part, digest)
            }
        }

        // Wait all parts (this happens organically if launch doesn't crash, wrapper coroutineScope blocks until children finish)
        file.renameTo(File(blob.name))
        File(blob.name)
    }

    private suspend fun headRequest(url: String): Long {
        val req = Request.Builder().head().url(url).build()
        return client.newCall(req).execute().use { 
            it.body?.contentLength() ?: 0 
        }
    }

    private suspend fun downloadPart(modelName: String, file: File, part: BlobDownloadPart, digest: String) {
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
                    }
                }
            }
        }
        part.completed = part.size
    }
}
