package com.sykik.lemon.domain.model

data class Source(
    val title: String,
    val domain: String,
    val url: String = ""
)

data class ChatMessage(
    val id: String,
    val text: String,
    val isFromUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val sources: List<Source> = emptyList(),
    val relatedQuestions: List<String> = emptyList()
)
