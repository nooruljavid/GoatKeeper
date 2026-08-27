package com.goatkeeper.app.ui.reports

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.goatkeeper.app.data.FarmRecord
import com.goatkeeper.app.data.Goat
import com.goatkeeper.app.util.generateGoatPDF
import com.goatkeeper.app.util.generateInventoryCSV
import java.io.File

@Composable
fun Reports(goats: List<Goat>, records: List<FarmRecord>, share: (String, String, File?) -> Unit) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var selectedGoat by remember { mutableStateOf<Goat?>(null) }

    val filteredGoats = goats.filter {
        it.id.contains(searchQuery, ignoreCase = true) || it.name.contains(searchQuery, ignoreCase = true)
    }

    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Text("Farm Reports", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }

        // 1. Inventory Export (CSV)
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Inventory Export", style = MaterialTheme.typography.titleMedium)
                    Text("Download a CSV file of all goats for Excel.", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = {
                        val file = generateInventoryCSV(context, goats)
                        share("Goat Inventory", "Inventory export for ${goats.size} goats.", file)
                    }) {
                        Icon(Icons.Default.Description, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Export CSV (Excel)")
                    }
                }
            }
        }

        // 2. Goat Record Export (PDF)
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Specific Goat Record", style = MaterialTheme.typography.titleMedium)
                    Text("Generate a PDF of all history for a single goat.", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(12.dp))

                    if (selectedGoat == null) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Search Goat ID or Name") },
                            leadingIcon = { Icon(Icons.Default.Search, null) }
                        )

                        if (searchQuery.isNotBlank()) {
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                            ) {
                                Column {
                                    filteredGoats.take(5).forEach { goat ->
                                        Row(
                                            Modifier
                                                .fillMaxWidth()
                                                .clickable { selectedGoat = goat; searchQuery = "" }
                                                .padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(if (goat.name.isBlank()) goat.id else "${goat.name} (${goat.id})", fontWeight = FontWeight.Bold)
                                        }
                                        HorizontalDivider()
                                    }
                                }
                            }
                        }
                    } else {
                        ListItem(
                            headlineContent = { Text(if (selectedGoat!!.name.isBlank()) selectedGoat!!.id else "${selectedGoat!!.name} (${selectedGoat!!.id})") },
                            supportingContent = { Text(selectedGoat!!.breed) },
                            trailingContent = {
                                TextButton(onClick = { selectedGoat = null }) { Text("Change") }
                            }
                        )
                        Button(
                            onClick = {
                                val goatRecords = records.filter { it.goatId == selectedGoat!!.id }
                                val file = generateGoatPDF(context, selectedGoat!!, goatRecords)
                                share("Goat Record: ${selectedGoat!!.id}", "History and details for goat ${selectedGoat!!.id}.", file)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.PictureAsPdf, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Generate PDF Record")
                        }
                    }
                }
            }
        }
    }
}
