package com.sykik.lemon.data.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.jsoup.Jsoup

data class OllamaScrapedModel(
    val name: String,
    val family: String,
    val tags: List<String>
)

class OllamaModelLister {
    
    suspend fun getAllModels(): List<OllamaScrapedModel> = withContext(Dispatchers.IO) {
        val models = mutableListOf<OllamaScrapedModel>()
        try {
            // Scrape https://ollama.com/library
            val doc = Jsoup.connect("https://ollama.com/library").get()
            
            doc.select(".model-card").forEach { card ->
                val name = card.select(".model-name").text().trim()
                if (name.isNotEmpty()) {
                    val family = name.split("/").first()
                    // Extracting text from tags
                    val tags = card.select(".model-tags a").eachText()
                    models.add(OllamaScrapedModel(name, family, tags))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        models
    }
}
