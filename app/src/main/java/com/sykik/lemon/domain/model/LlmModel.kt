package com.sykik.lemon.domain.model

data class LlmModel(
    val id: String,
    val name: String,
    val absolutePath: String? = null,
    val isDownloaded: Boolean = false,
    val downloadUrl: String? = null
)
