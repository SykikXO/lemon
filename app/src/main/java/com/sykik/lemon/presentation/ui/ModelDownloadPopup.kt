package com.sykik.lemon.presentation.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

@Composable
fun ModelDownloadPopup(
    isDownloading: Boolean,
    statusText: String,
    onDownloadModel: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var userInput by remember { mutableStateOf("") }

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
                
                Text("Enter a model name to download dynamically from Ollama Registry.")
                
                OutlinedTextField(
                    value = userInput,
                    onValueChange = { userInput = it },
                    label = { Text("e.g. llama3.2:1b, codeup:latest") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isDownloading,
                    trailingIcon = { 
                        Button(
                            onClick = { if (userInput.isNotBlank()) onDownloadModel(userInput) },
                            enabled = !isDownloading && userInput.isNotBlank()
                        ) {
                            Text(if (isDownloading) "..." else "PULL")
                        }
                    }
                )

                if (statusText.isNotEmpty()) {
                    Text(text = statusText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Close")
                    }
                }
            }
        }
    }
}
