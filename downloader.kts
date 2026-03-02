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
    
    suspend fun download(digest: String): File = coroutineScope {
        val blob = BlobDownload(
            digest = digest,
            name = "/sdcard/Download/llama3.2-3b.gguf"
        )
        
        // STEP 1: HEAD gets total size (your curl worked)
        blob.total = headRequest("https://registry.ollama.ai/v2/library/llama3.2/blobs/sha256:$digest")
        
        // STEP 2: Slice into 16 parts (100MB-1GB each)
        val partSize = (blob.total / 16).coerceIn(100*1024*1024, 1000*1024*1024)
        repeat(16) { i ->
            val offset = i * partSize
            val size = minOf(partSize, blob.total - offset)
            blob.parts.add(BlobDownloadPart(i, offset, size))
        }
        
        // STEP 3: PARALLEL DOWNLOAD (errgroup equivalent)
        val file = File(blob.name + "-partial").apply {
            createNewFile()
            // truncate to total size (sparse file)
        }
        
        blob.parts.forEach { part ->
            launch(Dispatchers.IO) {
                downloadPart(file, part, digest)
            }
        }
        
        // Wait all parts
        blob.parts.forEach { it.join() }
        file.renameTo(File(blob.name))
        File(blob.name)
    }
    
    private suspend fun headRequest(url: String): Long {
        val req = Request.Builder().head().url(url).build()
        return client.newCall(req).execute().use { 
            it.body?.contentLength() ?: 0 
        }
    }
    
    private suspend fun downloadPart(file: File, part: BlobDownloadPart, digest: String) {
        val url = "https://registry.ollama.ai/v2/library/llama3.2/blobs/sha256:$digest"
        val req = Request.Builder()
            .url(url)
            .header("Range", "bytes=${part.offset}-${part.offset + part.size - 1}")
            .build()
            
        client.newCall(req).execute().use { resp ->
            resp.body?.byteStream()?.copyTo(
                FileOutputStream(file, true).channel.position(part.offset)
            )
        }
        part.completed = part.size
    }
}

lifecycleScope.launch {
    val downloader = OllamaDownloader()
    val modelFile = downloader.download("74701a8c35f6c8d9a4b91f3f3497643001d63e0c7a84e085bed452548fa88d45")
    println("Model ready: ${modelFile.length()} bytes")
    llamaHelper.load(modelFile.absolutePath)
}
