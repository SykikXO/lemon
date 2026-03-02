package com.sykik.lemon.presentation.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.sykik.lemon.data.engine.OllamaScrapedModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelDownloadPopup(
    isDownloading: Boolean,
    statusText: String,
    remoteModels: List<OllamaScrapedModel>,
    onDownloadModel: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var userInput by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            tonalElevation = 8.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Pull Ollama Model",
                    style = MaterialTheme.typography.titleLarge
                )
                
                Text("Select a model to download dynamically from Ollama Registry.")
                
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { if (!isDownloading) expanded = !expanded },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = userInput,
                        onValueChange = { userInput = it },
                        label = { Text("Model Name (e.g. llama3.2:1b)") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        enabled = !isDownloading,
                        readOnly = remoteModels.isNotEmpty(),
                        trailingIcon = { 
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                        }
                    )
                    
                    if (remoteModels.isNotEmpty()) {
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            remoteModels.forEach { scrapedModel ->
                                val defaultTag = scrapedModel.tags.firstOrNull() ?: "latest"
                                DropdownMenuItem(
                                    text = { Text("${scrapedModel.name}:$defaultTag") },
                                    onClick = {
                                        userInput = "${scrapedModel.name}:$defaultTag"
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                }
                
                Button(
                    onClick = { if (userInput.isNotBlank()) onDownloadModel(userInput) },
                    enabled = !isDownloading && userInput.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (isDownloading) "DOWNLOADING..." else "PULL")
                }

                if (statusText.isNotEmpty()) {
                    Text(text = statusText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss, enabled = !isDownloading) {
                        Text("Close")
                    }
                }
            }
        }
    }
}
