class OllamaModelLister {
    private val client = OkHttpClient()
    
    suspend fun getAllModels(): List<OllamaModel> = withContext(Dispatchers.IO) {
        // Scrape https://ollama.com/library (works 2026)
        val doc = Jsoup.connect("https://ollama.com/library").get()
        
        doc.select(".model-card").mapNotNull { card ->
            val name = card.select(".model-name").text()
            val family = name.split("/").first()
            val tags = card.select(".model-tags a").eachText()
        }
    }
}

class SmartDownloader {
    suspend fun downloadByName(modelName: String, tag: String = "latest"): File {
        // Step 1: Auto-resolve SHA from model name
        val digest = resolveDigest(modelName, tag)
        
        // Step 2: Your 16-part parallel ripper
        return ollamaDownloader.download(digest)
    }
    
    private suspend fun resolveDigest(modelName: String, tag: String): String {
        val manifestUrl = "https://registry.ollama.ai/v2/library/$modelName/manifests/$tag"
        val response = client.newCall(manifestUrl).execute()
        
        if (!response.isSuccessful) {
            throw Exception("Model $modelName:$tag not found")
        }
        
        val json = JSONObject(response.body!!.string())
        return json.getJSONArray("layers").getJSONObject(0)
            .getString("digest").removePrefix("sha256:")
    }
}


suspend fun getDigest(modelName: String, tag: String): String {
    val manifestUrl = "https://registry.ollama.ai/v2/library/$modelName/manifests/$tag"
    val response = client.newCall(manifestUrl).execute()
    return JSONObject(response.body!!.string())
        .getJSONArray("layers").getJSONObject(0)
        .getString("digest").removePrefix("sha256:")
}
