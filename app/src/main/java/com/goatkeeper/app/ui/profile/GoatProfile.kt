package com.goatkeeper.app.ui.profile

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.goatkeeper.app.data.*
import com.goatkeeper.app.ui.components.Empty
import com.goatkeeper.app.ui.components.RecordItem
import com.goatkeeper.app.util.*

@Composable
fun GoatProfile(
    id: String,
    dao: FarmDao,
    goats: List<Goat>,
    initialTab: String = "Info",
    onAdd: (String) -> Unit,
    onEdit: (Goat) -> Unit,
    onDeleteGoat: (Goat) -> Unit,
    onEditRecord: (FarmRecord) -> Unit
) {
    val goat by dao.goat(id).collectAsState(initial = null)
    val records by dao.recordsFor(id).collectAsState(initial = emptyList())
    var activeTab by remember(id, initialTab) { mutableStateOf(initialTab) }
    val tabs = listOf("Info", "Health", "Breeding", "Safety", "Sales")

    Column(Modifier.fillMaxSize()) {
        ScrollableTabRow(
            selectedTabIndex = tabs.indexOf(activeTab),
            edgePadding = 16.dp,
            divider = {}
        ) {
            tabs.forEach { tab ->
                Tab(
                    selected = activeTab == tab,
                    onClick = { activeTab = tab },
                    text = { Text(tab) }
                )
            }
        }
        when (activeTab) {
            "Info" -> GoatInfoTab(goat, goats, records, onEdit, onDeleteGoat)
            else -> GoatRecordsTab(records, goats, activeTab, onAdd, onEditRecord)
        }
    }
}

@Composable
fun GoatInfoTab(
    goat: Goat?,
    allGoats: List<Goat>,
    records: List<FarmRecord>,
    onEdit: (Goat) -> Unit,
    onDelete: (Goat) -> Unit
) {
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        goat?.let { g ->
            if (g.photoUri.isNotBlank()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth().height(200.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        AsyncImage(
                            model = g.photoUri,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
            }
            item {
                Card(elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
                    Column(Modifier.padding(16.dp).fillMaxWidth()) {
                        Text(if (g.name.isBlank()) g.id else "${g.name} (${g.id})", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        HorizontalDivider(Modifier.padding(vertical = 8.dp))
                        InfoRow("Breed", g.breed)
                        InfoRow("Gender", g.gender)
                        InfoRow("Age", age(g.dateOfBirth))
                        InfoRow("Born", formatDate(g.dateOfBirth))
                        InfoRow("Status", g.status)
                        InfoRow("Health", "${calculateHealthStatus(g, records, today)}%")
                        InfoRow("Insurance", calculateInsuranceStatus(records, today))
                        if (g.colorMarkings.isNotBlank()) InfoRow("Color / Markings", g.colorMarkings)
                        if (g.microchipId.isNotBlank()) InfoRow("Microchip", g.microchipId)
                    }
                }
            }

            item {
                Text("Family Tree / Pedigree", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                FamilyTree(g, allGoats)
            }

            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onEdit(g) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Edit, null); Spacer(Modifier.width(6.dp)); Text("Edit Goat")
                    }
                    OutlinedButton(
                        onClick = { onDelete(g) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.Delete, null)
                        Spacer(Modifier.width(6.dp))
                        Text("Delete Goat")
                    }
                }
            }
            if (g.notes.isNotBlank()) {
                item {
                    Text("Notes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Card(modifier = Modifier.fillMaxWidth()) { Text(g.notes, modifier = Modifier.padding(16.dp)) }
                }
            }
        }
    }
}

@Composable
fun FamilyTree(goat: Goat, allGoats: List<Goat>) {
    val dam = allGoats.find { it.id == goat.damId }
    val sire = allGoats.find { it.id == goat.sireId }

    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Row for Parents
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            FamilyTreeNode(
                id = goat.damId,
                name = dam?.name ?: "",
                breed = dam?.breed ?: "Unknown",
                role = "Dam (Mother)",
                color = Color(0xFFFCE7F3) // Light Pink
            )
            FamilyTreeNode(
                id = goat.sireId,
                name = sire?.name ?: "",
                breed = sire?.breed ?: "Unknown",
                role = "Sire (Father)",
                color = Color(0xFFDBEAFE) // Light Blue
            )
        }

        // Connector lines
        Box(modifier = Modifier.height(40.dp).fillMaxWidth()) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val width = size.width
                val height = size.height
                
                // Draw lines from parents towards center
                drawLine(color = Color.LightGray, start = androidx.compose.ui.geometry.Offset(width * 0.25f, 0f), end = androidx.compose.ui.geometry.Offset(width * 0.25f, height / 2), strokeWidth = 2.dp.toPx())
                drawLine(color = Color.LightGray, start = androidx.compose.ui.geometry.Offset(width * 0.75f, 0f), end = androidx.compose.ui.geometry.Offset(width * 0.75f, height / 2), strokeWidth = 2.dp.toPx())
                
                // Draw horizontal connector
                drawLine(color = Color.LightGray, start = androidx.compose.ui.geometry.Offset(width * 0.25f, height / 2), end = androidx.compose.ui.geometry.Offset(width * 0.75f, height / 2), strokeWidth = 2.dp.toPx())
                
                // Draw line down to current goat
                drawLine(color = Color.LightGray, start = androidx.compose.ui.geometry.Offset(width / 2, height / 2), end = androidx.compose.ui.geometry.Offset(width / 2, height), strokeWidth = 2.dp.toPx())
            }
        }

        // Current Goat
        FamilyTreeNode(
            id = goat.id,
            name = goat.name,
            breed = goat.breed,
            role = "This Goat",
            color = Color(0xFFD1FAE5) // Light Green
        )
    }
}

@Composable
fun FamilyTreeNode(id: String, name: String, breed: String, role: String, color: Color) {
    if (id.isBlank() && role != "This Goat") {
        Card(
            modifier = Modifier.width(160.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Box(Modifier.padding(12.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("$role\nNot Recorded", style = MaterialTheme.typography.labelSmall, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
    } else {
        Card(
            modifier = Modifier.width(160.dp),
            colors = CardDefaults.cardColors(containerColor = color),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(Modifier.padding(10.dp)) {
                Text(role, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text(if (name.isBlank()) id else "$id - $name", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(breed, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline, maxLines = 1)
            }
        }
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.outline)
        Text(value, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun GoatRecordsTab(
    records: List<FarmRecord>,
    goats: List<Goat>,
    type: String,
    onAdd: (String) -> Unit,
    onEdit: (FarmRecord) -> Unit
) {
    val recordType = when (type) {
        "Sales" -> "Sale"
        "Safety" -> "Insurance"
        else -> type
    }
    val shown = records.filter { it.type == recordType }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Button(onClick = { onAdd(recordType) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("Add $recordType Record")
            }
        }
        if (shown.isEmpty()) {
            item { Empty("No $recordType records for this goat.") }
        } else {
            items(shown, key = { it.recordId }) { record ->
                RecordItem(
                    record = record,
                    goatName = record.goatId?.let { gid -> 
                        goats.find { it.id == gid }?.let { if (it.name.isBlank()) it.id else it.name } ?: gid
                    } ?: "Entire Herd",
                    onClick = { onEdit(record) }
                )
            }
        }
    }
}
