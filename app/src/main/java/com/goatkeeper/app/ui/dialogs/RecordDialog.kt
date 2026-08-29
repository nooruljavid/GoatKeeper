package com.goatkeeper.app.ui.dialogs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.goatkeeper.app.data.FarmRecord
import com.goatkeeper.app.data.Goat
import com.goatkeeper.app.ui.components.DatePickerField
import com.goatkeeper.app.util.kiddingDate
import com.goatkeeper.app.util.nextDewormingDate
import com.goatkeeper.app.util.today

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordDialog(
    type: String,
    goats: List<Goat>,
    allRecords: List<FarmRecord>,
    initialGoatId: String?,
    existing: FarmRecord?,
    onDismiss: () -> Unit,
    onSave: (FarmRecord) -> Unit,
    onDelete: ((FarmRecord) -> Unit)? = null
) {
    var goatId by remember(existing?.recordId, initialGoatId) { mutableStateOf(existing?.goatId ?: initialGoatId) }
    var date by remember(existing?.recordId) { mutableStateOf(existing?.date ?: today) }
    var title by remember(existing?.recordId) { mutableStateOf(existing?.title ?: "") }
    var detail by remember(existing?.recordId) { mutableStateOf(existing?.details ?: "") }
    var due by remember(existing?.recordId) { mutableStateOf(existing?.dueDate ?: "") }
    var party by remember(existing?.recordId) { mutableStateOf(existing?.party ?: "") }
    var amount by remember(existing?.recordId) { mutableStateOf(existing?.amount?.toString() ?: "") }
    var payment by remember(existing?.recordId) { mutableStateOf(existing?.paymentStatus?.ifBlank { "Paid" } ?: "Paid") }
    var quantity by remember(existing?.recordId) { mutableStateOf(existing?.quantity?.toString() ?: "") }
    var unit by remember(existing?.recordId) { mutableStateOf(existing?.unit ?: "") }
    var sireId by remember(existing?.recordId) { mutableStateOf(existing?.sireId ?: "") }
    var actualDate by remember(existing?.recordId) { mutableStateOf(existing?.actualDate ?: "") }
    var kidsCount by remember(existing?.recordId) { mutableStateOf(existing?.kidsCount?.toString() ?: "") }
    var kidsAlive by remember(existing?.recordId) { mutableStateOf(existing?.kidsAlive?.toString() ?: "") }
    var showGoatMenu by remember { mutableStateOf(false) }
    var showTitleMenu by remember { mutableStateOf(false) }

    val healthOptions = listOf("Deworming", "Vaccination")
    
    val suggestions = remember(type, party, allRecords) {
        if (party.isBlank()) emptyList()
        else allRecords.filter { it.type == type || (type == "Safety" && it.type == "Insurance") }
            .map { it.party }
            .filter { it.isNotBlank() && it.contains(party, ignoreCase = true) }
            .distinct()
            .sorted()
    }

    val titleSuggestions = remember(type, title, allRecords) {
        if (title.isBlank()) emptyList()
        else allRecords.filter { it.type == type || (type == "Safety" && it.type == "Insurance") }
            .map { it.title }
            .filter { it.isNotBlank() && it.contains(title, ignoreCase = true) }
            .distinct()
            .sorted()
    }

    LaunchedEffect(title, date) {
        if (type == "Health" && title.contains("Deworming", ignoreCase = true) && existing == null) {
            due = nextDewormingDate(date)
        }
    }

    LaunchedEffect(goats) {
        if (goatId == null && goats.isNotEmpty() && existing == null && initialGoatId == null) {
            // No default selection for herd records unless specified
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Add $type Record" else "Edit $type Record", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = when {
                            goatId == null -> "Entire Herd"
                            else -> goats.find { it.id == goatId }?.let { if (it.name.isBlank()) it.id else "${it.name} (${it.id})" } ?: goatId!!
                        },
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Goat / Group *") },
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = { 
                            IconButton(onClick = { showGoatMenu = !showGoatMenu }) {
                                Icon(Icons.Default.ArrowDropDown, null)
                            }
                        }
                    )
                    // Transparent overlay to catch clicks on the whole text field
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clickable { showGoatMenu = true }
                    )
                    
                    DropdownMenu(
                        expanded = showGoatMenu,
                        onDismissRequest = { showGoatMenu = false },
                        modifier = Modifier.fillMaxWidth(0.8f).heightIn(max = 300.dp)
                    ) {
                        DropdownMenuItem(
                            text = { Text("Entire Herd", fontWeight = FontWeight.Bold) },
                            onClick = {
                                goatId = null
                                showGoatMenu = false
                            }
                        )
                        HorizontalDivider()
                        goats.forEach { goat ->
                            DropdownMenuItem(
                                text = { 
                                    Text(
                                        text = if (goat.name.isBlank()) goat.id else "${goat.name} (${goat.id})",
                                        style = MaterialTheme.typography.bodyLarge
                                    ) 
                                },
                                onClick = { 
                                    goatId = goat.id
                                    showGoatMenu = false 
                                }
                            )
                        }
                    }
                }
                
                DatePickerField("Record Date *", date, { date = it })
                
                if (type == "Health") {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Field("Health Event (Deworming/Vaccination) *", title, change = { 
                            title = it
                            showTitleMenu = true
                        })
                        DropdownMenu(
                            expanded = showTitleMenu,
                            onDismissRequest = { showTitleMenu = false },
                            modifier = Modifier.fillMaxWidth(0.9f),
                            properties = androidx.compose.ui.window.PopupProperties(focusable = false)
                        ) {
                            healthOptions.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option) },
                                    onClick = {
                                        title = option
                                        showTitleMenu = false
                                    }
                                )
                            }
                        }
                    }
                } else {
                    SuggestionField("Title / Description *", title, titleSuggestions) { title = it }
                }

                when (type) {
                    "Health" -> {
                        SuggestionField("Veterinarian", party, suggestions) { party = it }
                        DatePickerField("Next Due Date", due, { due = it })
                        Field("Cost", amount) { amount = it }
                    }
                    "Breeding" -> {
                        Field("Sire ID", sireId) { sireId = it }
                        Text("Expected Kidding: ${kiddingDate(date)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        DatePickerField("Actual Kidding Date", actualDate, { actualDate = it })
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Field("Kids Born", kidsCount, Modifier.weight(1f)) { kidsCount = it }
                            Field("Kids Alive", kidsAlive, Modifier.weight(1f)) { kidsAlive = it }
                        }
                    }
                    "Insurance" -> {
                        SuggestionField("Insurer", party, suggestions) { party = it }
                        Field("Policy Number", detail) { detail = it }
                        DatePickerField("Expiry Date *", due, { due = it })
                        Field("Coverage Amount", amount) { amount = it }
                    }
                    "Sale" -> {
                        SuggestionField("Buyer", party, suggestions) { party = it }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Field("Quantity", quantity, Modifier.weight(1f)) { quantity = it }
                            Field("Unit (kg, L)", unit, Modifier.weight(1f)) { unit = it }
                        }
                        Field("Price", amount) { amount = it }
                        Text("Payment Status", style = MaterialTheme.typography.labelMedium)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("Paid", "Pending", "Partial").forEach { statusValue ->
                                FilterChip(selected = payment == statusValue, onClick = { payment = statusValue }, label = { Text(statusValue) }, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                    "Transfer" -> {
                        SuggestionField("New Owner / Farm", party, suggestions) { party = it }
                        Field("Reason", detail) { detail = it }
                    }
                }

                if (type != "Insurance" && type != "Transfer") {
                    OutlinedTextField(
                        value = detail,
                        onValueChange = { detail = it },
                        label = { Text("Additional Details") },
                        modifier = Modifier.fillMaxWidth().height(80.dp)
                    )
                }
            }
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (existing != null && onDelete != null) {
                    TextButton(
                        onClick = { onDelete(existing) },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.Delete, null)
                        Spacer(Modifier.width(4.dp))
                        Text("Delete")
                    }
                    Spacer(Modifier.weight(1f))
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
                TextButton(enabled = title.isNotBlank(), onClick = {
                    onSave(
                        FarmRecord(
                            recordId = existing?.recordId ?: 0L,
                            goatId = goatId,
                            type = type,
                            date = date,
                            dueDate = if (type == "Breeding" && due.isBlank()) kiddingDate(date) else due,
                            title = title,
                            details = detail,
                            party = party,
                            amount = amount.toDoubleOrNull(),
                            quantity = quantity.toDoubleOrNull(),
                            unit = unit,
                            paymentStatus = payment,
                            sireId = sireId,
                            actualDate = actualDate,
                            kidsCount = kidsCount.toIntOrNull(),
                            kidsAlive = kidsAlive.toIntOrNull()
                        )
                    )
                }) { Text("Save") }
            }
        },
        dismissButton = {}
    )
}
