package com.sykik.lemon.presentation.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.sykik.lemon.domain.model.ChatMessage
import com.sykik.lemon.domain.model.LlmModel
import com.sykik.lemon.presentation.ChatState
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    state: ChatState,
    onSendMessage: (String) -> Unit,
    onModelSelected: (LlmModel) -> Unit,
    onDeleteModel: (LlmModel) -> Unit,
    onManageModelsClicked: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val messageCount = state.messages.size
    val isGenerating = state.isGenerating

    // Auto-scroll: fire on message count change AND continuously during generation
    LaunchedEffect(messageCount, isGenerating) {
        if (messageCount > 0) {
            listState.animateScrollToItem(messageCount - 1)
        }
        // Keep scrolling while generating
        if (isGenerating) {
            while (true) {
                delay(300)
                if (messageCount > 0) {
                    listState.scrollToItem(messageCount - 1)
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Lemon Chat") },
                actions = {
                    Box {
                        TextButton(onClick = { expanded = true }) {
                            Text(state.selectedModel?.name ?: "Select Model")
                            Icon(Icons.Default.ArrowDropDown, contentDescription = "Switch Model")
                        }
                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            state.availableModels.forEach { model ->
                                DropdownMenuItem(
                                    text = {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(model.name, modifier = Modifier.weight(1f))
                                            if (!model.absolutePath.isNullOrEmpty()) {
                                                IconButton(
                                                    onClick = {
                                                        onDeleteModel(model)
                                                        expanded = false
                                                    },
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Default.Delete,
                                                        contentDescription = "Delete ${model.name}",
                                                        tint = MaterialTheme.colorScheme.error,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        }
                                    },
                                    onClick = {
                                        onModelSelected(model)
                                        expanded = false
                                    }
                                )
                            }
                            Divider()
                            DropdownMenuItem(
                                text = { Text("Manage Models...") },
                                onClick = {
                                    onManageModelsClicked()
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.messages, key = { it.id }) { message ->
                    val isLastAssistantMessage = !message.isFromUser &&
                        state.messages.lastOrNull()?.id == message.id
                    ChatBubble(
                        message = message,
                        isStreaming = isLastAssistantMessage && isGenerating
                    )
                }
                if (isGenerating) {
                    item(key = "typing_indicator") {
                        TypingIndicator()
                    }
                }
            }

            ChatInputArea(
                isGenerating = isGenerating,
                onSendMessage = onSendMessage
            )
        }
    }
}

@Composable
fun TypingIndicator() {
    val dotCount = 3
    val animDuration = 400

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.widthIn(max = 80.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(dotCount) { index ->
                    val infiniteTransition = rememberInfiniteTransition(label = "dot_$index")
                    val alpha by infiniteTransition.animateFloat(
                        initialValue = 0.3f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(
                                durationMillis = animDuration,
                                delayMillis = index * 150,
                                easing = FastOutSlowInEasing
                            ),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "alpha_$index"
                    )
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .alpha(alpha)
                            .background(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                shape = CircleShape
                            )
                    )
                }
            }
        }
    }
}

@Composable
fun ChatBubble(message: ChatMessage, isStreaming: Boolean = false) {
    val color = if (message.isFromUser)
        MaterialTheme.colorScheme.primaryContainer
    else
        MaterialTheme.colorScheme.surfaceVariant

    // Gradual text reveal: displayed text lags behind actual text, catching up smoothly
    var revealedLength by remember { mutableIntStateOf(message.text.length) }
    val actualLength = message.text.length

    // Gradually reveal characters during streaming
    LaunchedEffect(actualLength) {
        if (isStreaming) {
            while (revealedLength < actualLength) {
                revealedLength++
                delay(40) // 15ms per character = smooth flowing text
            }
        } else {
            revealedLength = actualLength
        }
    }

    val displayedText = if (isStreaming && revealedLength < actualLength) {
        message.text.substring(0, revealedLength)
    } else {
        message.text
    }

    // Fade in the whole bubble once when it first appears
    val bubbleAlpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        bubbleAlpha.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing)
        )
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .alpha(bubbleAlpha.value),
        contentAlignment = if (message.isFromUser) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = color,
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Text(
                text = displayedText,
                modifier = Modifier.padding(12.dp),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun ChatInputArea(
    isGenerating: Boolean,
    onSendMessage: (String) -> Unit
) {
    var text by remember { mutableStateOf("") }

    Surface(
        tonalElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
    ) {
        Row(
            modifier = Modifier
                .padding(8.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Ask anything...") },
                enabled = !isGenerating,
                maxLines = 4
            )
            
            Spacer(modifier = Modifier.width(8.dp))
            
            IconButton(
                onClick = {
                    if (text.isNotBlank()) {
                        onSendMessage(text)
                        text = ""
                    }
                },
                enabled = !isGenerating && text.isNotBlank()
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send Message",
                    tint = if (!isGenerating && text.isNotBlank()) MaterialTheme.colorScheme.primary else Color.Gray
                )
            }
        }
    }
}
