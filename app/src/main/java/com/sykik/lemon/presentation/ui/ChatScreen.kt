package com.sykik.lemon.presentation.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Subject
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Adjust
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.WbCloudy
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.filled.Mic
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sykik.lemon.domain.model.ChatMessage
import com.sykik.lemon.domain.model.LlmModel
import com.sykik.lemon.domain.model.Source
import com.sykik.lemon.presentation.ChatState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    state: ChatState,
    onSendMessage: (String) -> Unit,
    onModelSelected: (LlmModel) -> Unit,
    onManageModelsClicked: () -> Unit,
    onThemeToggleClicked: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    // Translucent layers allowing animated background to show through
    val topBarColor = MaterialTheme.colorScheme.background.copy(alpha = 0.5f)
    val bottomAreaColor = MaterialTheme.colorScheme.background.copy(alpha = 0.6f)
    val surfaceColor = MaterialTheme.colorScheme.surface

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedStarfield(isDarkMode = state.isDarkMode)

        Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                navigationIcon = {
                    Row(
                        modifier = Modifier.padding(start = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Search,
                            contentDescription = "Logo",
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                title = {
                    Box {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.clickable { expanded = true }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    state.selectedModel?.name ?: "Select Model",
                                    style = MaterialTheme.typography.labelMedium
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Switch Model", modifier = Modifier.size(16.dp))
                            }
                        }
                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            state.availableModels.forEach { model ->
                                DropdownMenuItem(
                                    text = { Text(model.name) },
                                    onClick = {
                                        onModelSelected(model)
                                        expanded = false
                                    }
                                )
                            }
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Manage Models...") },
                                onClick = {
                                    onManageModelsClicked()
                                    expanded = false
                                }
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = onThemeToggleClicked,
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Icon(
                            imageVector = if (state.isDarkMode) Icons.Default.WbSunny else Icons.Default.NightsStay,
                            contentDescription = "Toggle Theme",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = topBarColor
                )
            )
        },
        containerColor = Color.Transparent
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 140.dp),
                verticalArrangement = Arrangement.spacedBy(40.dp)
            ) {
                items(state.messages) { message ->
                    if (message.isFromUser) {
                        UserQuerySection(text = message.text)
                    } else {
                        AssistantAnswerSection(message = message)
                    }
                }
            }
            
            // Empty State: Show suggested popular prompts
            if (state.messages.isEmpty() && !state.isGenerating) {
                SuggestedPromptsSection(
                    isDarkMode = state.isDarkMode,
                    onPromptClick = onSendMessage,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 80.dp)
                )
            }

            // Floating bottom input area
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(
                        color = bottomAreaColor
                    )
                    .padding(horizontal = 16.dp, vertical = 16.dp)
            ) {
                PerplexityInputArea(
                    isGenerating = state.isGenerating,
                    onSendMessage = onSendMessage
                )
            }
        }
    }
    }
}

@Composable
fun SuggestedPromptsSection(isDarkMode: Boolean, onPromptClick: (String) -> Unit, modifier: Modifier = Modifier) {
    val prompts = listOf(
        Pair(Icons.Outlined.WbCloudy, "What's the weather like in Tokyo right now?"),
        Pair(Icons.Outlined.Code, "Write a Python script to scrape a website"),
        Pair(Icons.Outlined.Flight, "Plan a 5-day itinerary for a trip to Paris"),
        Pair(Icons.Outlined.Restaurant, "Give me a recipe for authentic carbonara")
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Text(
            text = "Where knowledge begins",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Medium,
                letterSpacing = (-0.5).sp
            ),
            color = MaterialTheme.colorScheme.onBackground
        )

        LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalItemSpacing = 12.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            items(prompts) { (icon, text) ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .clickable { onPromptClick(text) }
                        .fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun UserQuerySection(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.headlineLarge.copy(
            fontWeight = FontWeight.Medium,
            fontSize = 32.sp,
            lineHeight = 40.sp,
            letterSpacing = (-0.5).sp
        ),
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
fun AssistantAnswerSection(message: ChatMessage) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        if (message.sources.isNotEmpty()) {
            SourcesRow(sources = message.sources)
        }

        AnswerBlock(text = message.text)

        if (message.relatedQuestions.isNotEmpty()) {
            RelatedQuestionsList(questions = message.relatedQuestions)
        }
    }
}

@Composable
fun SourcesRow(sources: List<Source>) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.MenuBook,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "Sources",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 18.sp
                ),
                color = MaterialTheme.colorScheme.onBackground
            )
        }
        
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(end = 16.dp)
        ) {
            itemsIndexed(sources) { index, source ->
                SourceCard(index = index + 1, source = source)
            }
        }
    }
}

@Composable
fun SourceCard(index: Int, source: Source) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = Modifier
            .width(170.dp)
            .height(80.dp)
            .clickable { /* Omitted for brevity */ }
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = source.title,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        lineHeight = 18.sp
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                // Number badge
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.size(18.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = index.toString(),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = source.domain,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun AnswerBlock(text: String) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Subject, // Using a list/text icon roughly similar to the perplexity spark
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "Answer",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 18.sp
                ),
                color = MaterialTheme.colorScheme.onBackground
            )
        }
        
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge.copy(
                lineHeight = 28.sp,
                fontSize = 17.sp,
                letterSpacing = 0.1.sp
            ),
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

@Composable
fun RelatedQuestionsList(questions: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.FormatListBulleted,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "Related",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 18.sp
                ),
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            questions.forEach { question ->
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color.Transparent,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { /* Handle tap */ }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = question,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 16.sp
                            )
                        )
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Expand",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PerplexityInputArea(
    isGenerating: Boolean,
    onSendMessage: (String) -> Unit
) {
    var text by remember { mutableStateOf("") }

    Surface(
        shape = RoundedCornerShape(24.dp),
        shadowElevation = 0.dp,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), // Light offset color
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { 
                        Text(
                            "Ask anything...", 
                            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        ) 
                    },
                    enabled = !isGenerating,
                    maxLines = 5,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (!isGenerating && text.isNotBlank()) {
                                onSendMessage(text)
                                text = ""
                            }
                        }
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        disabledBorderColor = Color.Transparent,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent
                    )
                )

                IconButton(onClick = { /* Voice input feature */ }) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Voice Input",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    // Focus feature
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { /* Focus feature */ }) {
                        Icon(
                            imageVector = Icons.Default.Adjust,
                            contentDescription = "Focus",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            "Focus", 
                            style = MaterialTheme.typography.labelLarge.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    }

                    // Attach feature
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { /* Attach feature */ }) {
                        Icon(
                            imageVector = Icons.Default.AttachFile,
                            contentDescription = "Attach",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            "Attach", 
                            style = MaterialTheme.typography.labelLarge.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    }
                }

                // Send Button
                Surface(
                    shape = CircleShape,
                    color = if (!isGenerating && text.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .size(40.dp)
                        .clickable(enabled = !isGenerating && text.isNotBlank()) {
                            onSendMessage(text)
                            text = ""
                        }
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Send Message",
                            tint = if (!isGenerating && text.isNotBlank()) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}
