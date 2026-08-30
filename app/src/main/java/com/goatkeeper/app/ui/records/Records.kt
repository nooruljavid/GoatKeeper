package com.goatkeeper.app.ui.records

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.goatkeeper.app.data.FarmRecord
import com.goatkeeper.app.data.Goat
import com.goatkeeper.app.ui.components.Empty
import com.goatkeeper.app.ui.components.RecordItem
import com.goatkeeper.app.ui.dashboards.HealthDashboard
import com.goatkeeper.app.ui.dashboards.SafetyDashboard
import com.goatkeeper.app.util.age

import androidx.compose.ui.res.stringResource
import com.goatkeeper.app.R

@Composable
fun Herd(
    goats: List<Goat>,
    query: String,
    onQueryChange: (String) -> Unit,
    filterGender: String,
    onGenderChange: (String) -> Unit,
    filterStatus: String,
    onStatusChange: (String) -> Unit,
    onOpen: (String) -> Unit
) {
    var showStatusMenu by remember { mutableStateOf(false) }

    val shown = goats.filter {
        (it.id.contains(query, true) || it.name.contains(query, true) || it.breed.contains(query, true)) &&
            (filterGender == "All" || it.gender == filterGender) &&
            (filterStatus == "All" || it.status == filterStatus)
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        OutlinedTextField(
            query,
            onQueryChange,
            Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.search_placeholder)) },
            leadingIcon = { Icon(Icons.Default.Search, null) }
        )
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = filterGender == "Female", onClick = { onGenderChange(if (filterGender == "Female") "All" else "Female") }, label = { Text("♀ " + stringResource(R.string.female)) })
            FilterChip(selected = filterGender == "Male", onClick = { onGenderChange(if (filterGender == "Male") "All" else "Male") }, label = { Text("♂ " + stringResource(R.string.male)) })
            Box {
                val statusText = when(filterStatus) {
                    "Active" -> stringResource(R.string.active)
                    "Sold" -> stringResource(R.string.sold)
                    "Deceased" -> stringResource(R.string.deceased)
                    "Transferred" -> stringResource(R.string.transferred)
                    else -> stringResource(R.string.all)
                }
                AssistChip(onClick = { showStatusMenu = true }, label = { Text(stringResource(R.string.status_label, statusText)) }, trailingIcon = { Icon(Icons.Default.ArrowDropDown, null) })
                DropdownMenu(expanded = showStatusMenu, onDismissRequest = { showStatusMenu = false }) {
                    listOf("All", "Active", "Sold", "Deceased", "Transferred").forEach { status ->
                        val label = when(status) {
                            "Active" -> stringResource(R.string.active)
                            "Sold" -> stringResource(R.string.sold)
                            "Deceased" -> stringResource(R.string.deceased)
                            "Transferred" -> stringResource(R.string.transferred)
                            else -> stringResource(R.string.all)
                        }
                        DropdownMenuItem(text = { Text(label) }, onClick = { onStatusChange(status); showStatusMenu = false })
                    }
                }
            }
        }
        if (shown.isEmpty()) {
            Empty(stringResource(R.string.no_goats_match))
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(shown, key = { it.id }) { GoatCard(it, onOpen) }
            }
        }
    }
}

@Composable
fun GoatCard(goat: Goat, open: (String) -> Unit) = Card(
    modifier = Modifier.fillMaxWidth().clickable { open(goat.id) },
    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
) {
    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(24.dp)).background(
                if (goat.gender == "Female") Color(0xFFD1FAE5) else Color(0xFFDBEAFE)
            ),
            contentAlignment = Alignment.Center
        ) {
            if (goat.photoUri.isNotBlank()) {
                AsyncImage(
                    model = goat.photoUri,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Text("🐐", fontSize = 24.sp)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(if (goat.name.isBlank()) goat.id else "${goat.name} · ${goat.id}", fontWeight = FontWeight.Bold)
            val genderLabel = if (goat.gender == "Female") stringResource(R.string.female) else stringResource(R.string.male)
            Text("${goat.breed} · $genderLabel · ${age(goat.dateOfBirth)}", style = MaterialTheme.typography.bodySmall)
        }
        val statusColor = when (goat.status) {
            "Active" -> Color(0xFF10B981)
            "Sold" -> Color(0xFFF59E0B)
            "Deceased" -> Color(0xFFEF4444)
            else -> Color(0xFF6B7280)
        }
        val statusLabel = when(goat.status) {
            "Active" -> stringResource(R.string.active)
            "Sold" -> stringResource(R.string.sold)
            "Deceased" -> stringResource(R.string.deceased)
            "Transferred" -> stringResource(R.string.transferred)
            else -> goat.status
        }
        AssistChip(onClick = { }, label = { Text(statusLabel, color = statusColor, fontWeight = FontWeight.Bold, fontSize = 11.sp) })
    }
}

@Composable
fun Records(
    records: List<FarmRecord>,
    goats: List<Goat>,
    type: String,
    onTypeChange: (String) -> Unit,
    query: String,
    onQueryChange: (String) -> Unit,
    healthFilter: String,
    onHealthFilterChange: (String) -> Unit,
    safetyFilter: String,
    onSafetyFilterChange: (String) -> Unit,
    onAdd: (String) -> Unit,
    onEdit: (FarmRecord) -> Unit,
    onOpen: (String, String) -> Unit
) {
    val types = listOf("All", "Health", "Breeding", "Safety", "Sale", "Transfer")
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        ScrollableTabRow(selectedTabIndex = types.indexOf(type), edgePadding = 0.dp) {
            types.forEach { t -> 
                val label = when(t) {
                    "All" -> stringResource(R.string.all)
                    "Health" -> stringResource(R.string.health)
                    "Breeding" -> stringResource(R.string.breeding)
                    "Safety" -> stringResource(R.string.safety)
                    "Sale" -> stringResource(R.string.sale)
                    "Transfer" -> stringResource(R.string.transfer)
                    else -> t
                }
                Tab(type == t, { onTypeChange(t) }, text = { Text(label) }) 
            }
        }
        Spacer(Modifier.height(8.dp))

        if (type == "Health") {
            if (healthFilter == "Global Records") {
                GlobalRecordsList("Health", records, goats, query, onQueryChange, onAdd, onEdit) { onHealthFilterChange("All") }
            } else {
                HealthDashboard(goats, records, healthFilter, onHealthFilterChange, { onHealthFilterChange("Global Records") }, onOpen)
            }
        } else if (type == "Safety") {
            if (safetyFilter == "Global Records") {
                GlobalRecordsList("Safety", records, goats, query, onQueryChange, onAdd, onEdit) { onSafetyFilterChange("All") }
            } else {
                SafetyDashboard(goats, records, safetyFilter, onSafetyFilterChange, { onSafetyFilterChange("Global Records") }, onOpen)
            }
        } else {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                Modifier.fillMaxWidth().padding(bottom = 8.dp),
                label = { Text(stringResource(R.string.search_records_placeholder)) },
                leadingIcon = { Icon(Icons.Default.Search, null) }
            )

            if (type != "All") {
                val typeLabel = when(type) {
                    "Health" -> stringResource(R.string.health)
                    "Breeding" -> stringResource(R.string.breeding)
                    "Safety" -> stringResource(R.string.safety)
                    "Sale" -> stringResource(R.string.sale)
                    "Transfer" -> stringResource(R.string.transfer)
                    else -> type
                }
                FilledTonalButton(onClick = { onAdd(type) }, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                    Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.add_record_btn, typeLabel))
                }
            }

            val shown = records.filter { r ->
                (type == "All" || r.type == type) && 
                (query.isBlank() || r.goatId?.let { gid -> 
                    goats.find { it.id == gid }?.let { it.id.contains(query, true) || it.name.contains(query, true) } ?: gid.contains(query, true)
                } ?: "Entire Herd".contains(query, true))
            }

            if (shown.isEmpty()) {
                val typeLabel = when(type) {
                    "Health" -> stringResource(R.string.health)
                    "Breeding" -> stringResource(R.string.breeding)
                    "Safety" -> stringResource(R.string.safety)
                    "Sale" -> stringResource(R.string.sale)
                    "Transfer" -> stringResource(R.string.transfer)
                    else -> stringResource(R.string.all)
                }
                Empty(stringResource(R.string.no_records_yet, typeLabel))
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(shown, key = { it.recordId }) { record ->
                        RecordItem(
                            record = record,
                            goatName = record.goatId?.let { gid -> 
                                goats.find { it.id == gid }?.name?.ifBlank { gid } ?: gid
                            } ?: stringResource(R.string.entire_herd),
                            onClick = { onEdit(record) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun GlobalRecordsList(
    type: String,
    records: List<FarmRecord>,
    goats: List<Goat>,
    query: String,
    onQueryChange: (String) -> Unit,
    onAdd: (String) -> Unit,
    onEdit: (FarmRecord) -> Unit,
    onBack: () -> Unit
) {
    val recordType = if (type == "Safety") "Insurance" else type
    val shown = records.filter { r ->
        r.type == recordType && 
        (query.isBlank() || r.goatId?.let { gid -> 
            goats.find { it.id == gid }?.let { it.id.contains(query, true) || it.name.contains(query, true) } ?: gid.contains(query, true)
        } ?: "Entire Herd".contains(query, true))
    }
    
    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack, contentPadding = PaddingValues(0.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.back_to_dashboard))
            }
            Spacer(Modifier.weight(1f))
        }

        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            Modifier.fillMaxWidth().padding(bottom = 8.dp),
            label = { Text(stringResource(R.string.search_records_placeholder)) },
            leadingIcon = { Icon(Icons.Default.Search, null) }
        )
        
        val typeLabel = when(type) {
            "Safety" -> stringResource(R.string.safety)
            else -> type
        }
        FilledTonalButton(onClick = { onAdd(recordType) }, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
            Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.add_record_btn, typeLabel))
        }
        
        if (shown.isEmpty()) {
            Empty(stringResource(R.string.no_records_yet, typeLabel))
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(shown, key = { it.recordId }) { record ->
                    RecordItem(
                        record = record,
                        goatName = record.goatId?.let { gid -> 
                            goats.find { it.id == gid }?.name?.ifBlank { gid } ?: gid
                        } ?: stringResource(R.string.entire_herd),
                        onClick = { onEdit(record) }
                    )
                }
            }
        }
    }
}
