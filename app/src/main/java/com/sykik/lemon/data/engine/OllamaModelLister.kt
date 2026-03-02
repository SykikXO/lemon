package com.sykik.lemon.data.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
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
            // 1. Scrape main library for top models
            val doc = Jsoup.connect("https://ollama.com/library").get()
            
            val modelNames = doc.select(".model-card").mapNotNull { card ->
                val name = card.select(".model-name").text().trim()
                if (name.isNotEmpty()) name else null
            }

            // 2. Concurrently fetch tags for each model
            val deferredModels = modelNames.map { name ->
                async {
                    val family = name.split("/").first()
                    var tags = listOf("latest")
                    
                    try {
                        val tagsDoc = Jsoup.connect("https://ollama.com/library/$name/tags").get()
                        
                        // Grab specific tag variants (like 1b, 2b) and sizes
                        val specificTags = tagsDoc.select("a[href^='/library/$name:']").mapNotNull { tagHref ->
                            tagHref.attr("href").substringAfterLast(":")
                        }.distinct()
                        
                        if (specificTags.isNotEmpty()) {
                            tags = specificTags
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    
                    OllamaScrapedModel(name, family, tags)
                }
            }
            
            models.addAll(deferredModels.awaitAll())
            
        } catch (e: Exception) {
            e.printStackTrace()
        }
        models
    }
}
