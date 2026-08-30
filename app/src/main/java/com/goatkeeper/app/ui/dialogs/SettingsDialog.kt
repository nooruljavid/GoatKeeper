package com.goatkeeper.app.ui.dialogs

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import com.goatkeeper.app.R
import com.goatkeeper.app.data.AppSettings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDialog(
    existing: AppSettings?,
    onDismiss: () -> Unit,
    onSave: (AppSettings) -> Unit
) {
    var selectedLanguage by remember { mutableStateOf(existing?.language ?: "en") }

    val languages = listOf(
        "en" to stringResource(R.string.english),
        "ta" to stringResource(R.string.tamil)
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings), fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.language),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                // Radio Button (Toggle) Selection Method
                languages.forEach { lang ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { 
                                selectedLanguage = lang.first
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (selectedLanguage == lang.first),
                            onClick = { 
                                selectedLanguage = lang.first
                            }
                        )
                        Text(
                            text = lang.second,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 16.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(AppSettings(language = selectedLanguage))
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}
