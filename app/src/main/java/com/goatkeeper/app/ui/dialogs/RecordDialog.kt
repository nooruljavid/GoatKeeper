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

import androidx.compose.ui.res.stringResource
import com.goatkeeper.app.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordDialog(
    type: String,
    goats: List<Goat>,
    allRecords: List<FarmRecord>,
    initialGoatId: String?,
    existing: FarmRecord?,
    currencySymbol: String = "₹",
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
    var unitPrice by remember(existing?.recordId) { 
        mutableStateOf(
            if (existing?.amount != null && (existing.quantity ?: 0.0) > 0.0) 
                String.format(java.util.Locale.US, "%.2f", existing.amount!! / existing.quantity!!) 
            else ""
        ) 
    }
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

    val typeLabel = when(type) {
        "Health" -> stringResource(R.string.health)
        "Breeding" -> stringResource(R.string.breeding)
        "Safety", "Insurance" -> stringResource(R.string.safety)
        "Sale", "Goat Sale", "Manure Sale", "Milk Sale" -> stringResource(R.string.sale)
        "Transfer" -> stringResource(R.string.transfer)
        else -> type
    }

    LaunchedEffect(type) {
        if (unit.isBlank()) {
            unit = when (type) {
                "Manure Sale" -> "kg"
                "Milk Sale" -> "L"
                "Goat Sale" -> "Qty"
                else -> ""
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            val displayType = when(type) {
                "Goat Sale" -> stringResource(R.string.goat_selling)
                "Manure Sale" -> stringResource(R.string.manure_selling)
                "Milk Sale" -> stringResource(R.string.milk_selling)
                else -> typeLabel
            }
            Text(if (existing == null) stringResource(R.string.add_record_title, displayType) else stringResource(R.string.edit_record_title, displayType), fontWeight = FontWeight.Bold) 
        },
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
                            goatId == null -> stringResource(R.string.entire_herd)
                            else -> goats.find { it.id == goatId }?.let { if (it.name.isBlank()) it.id else "${it.name} (${it.id})" } ?: goatId!!
                        },
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.goat_group_label)) },
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
                            text = { Text(stringResource(R.string.entire_herd), fontWeight = FontWeight.Bold) },
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
                
                DatePickerField(stringResource(R.string.record_date_req), date, { date = it })
                
                if (type == "Health") {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Field(stringResource(R.string.health_event_label), title, change = { 
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
                                val label = when(option) {
                                    "Deworming" -> stringResource(R.string.deworming)
                                    "Vaccination" -> stringResource(R.string.vaccination)
                                    else -> option
                                }
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = {
                                        title = option
                                        showTitleMenu = false
                                    }
                                )
                            }
                        }
                    }
                } else {
                    SuggestionField(stringResource(R.string.title_desc_req), title, titleSuggestions) { title = it }
                }

                when (type) {
                    "Health" -> {
                        SuggestionField(stringResource(R.string.veterinarian_label), party, suggestions) { party = it }
                        DatePickerField(stringResource(R.string.next_due_date_label), due, { due = it })
                        Field(stringResource(R.string.cost_label) + " ($currencySymbol)", amount) { amount = it }
                    }
                    "Breeding" -> {
                        Field(stringResource(R.string.sire_id_label), sireId) { sireId = it }
                        Text(stringResource(R.string.expected_kidding_label, kiddingDate(date)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        DatePickerField(stringResource(R.string.actual_kidding_date_label), actualDate, { actualDate = it })
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Field(stringResource(R.string.kids_born_label), kidsCount, Modifier.weight(1f)) { kidsCount = it }
                            Field(stringResource(R.string.kids_alive_label), kidsAlive, Modifier.weight(1f)) { kidsAlive = it }
                        }
                    }
                    "Insurance" -> {
                        SuggestionField(stringResource(R.string.insurer_label), party, suggestions) { party = it }
                        Field(stringResource(R.string.policy_number_label), detail) { detail = it }
                        DatePickerField(stringResource(R.string.expiry_date_req), due, { due = it })
                        Field(stringResource(R.string.coverage_amount_label) + " ($currencySymbol)", amount) { amount = it }
                    }
                    "Sale", "Goat Sale", "Manure Sale", "Milk Sale" -> {
                        SuggestionField(stringResource(R.string.buyer_label), party, suggestions) { party = it }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Field(stringResource(R.string.quantity_label), quantity, Modifier.weight(1f)) { 
                                quantity = it 
                                // Auto-calculate total
                                val q = it.toDoubleOrNull() ?: 0.0
                                val p = unitPrice.toDoubleOrNull() ?: 0.0
                                if (q > 0 && p > 0) amount = String.format(java.util.Locale.US, "%.2f", q * p)
                            }
                            Field(stringResource(R.string.unit_kg_l_label), unit, Modifier.weight(1f)) { unit = it }
                        }
                        
                        Field(stringResource(R.string.price_per_unit) + " ($currencySymbol)", unitPrice) { 
                            unitPrice = it 
                            // Auto-calculate total
                            val q = quantity.toDoubleOrNull() ?: 0.0
                            val p = it.toDoubleOrNull() ?: 0.0
                            if (q > 0 && p > 0) amount = String.format(java.util.Locale.US, "%.2f", q * p)
                        }

                        Field(stringResource(R.string.price_label) + " ($currencySymbol)", amount) { amount = it }
                        
                        Text(stringResource(R.string.payment_status_label), style = MaterialTheme.typography.labelMedium)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("Paid", "Pending", "Partial").forEach { statusValue ->
                                val label = when(statusValue) {
                                    "Paid" -> stringResource(R.string.paid)
                                    "Pending" -> stringResource(R.string.pending)
                                    "Partial" -> stringResource(R.string.partial)
                                    else -> statusValue
                                }
                                FilterChip(selected = payment == statusValue, onClick = { payment = statusValue }, label = { Text(label) }, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                    "Transfer" -> {
                        SuggestionField(stringResource(R.string.new_owner_farm_label), party, suggestions) { party = it }
                        Field(stringResource(R.string.reason_label), detail) { detail = it }
                    }
                }

                if (type != "Insurance" && type != "Transfer") {
                    OutlinedTextField(
                        value = detail,
                        onValueChange = { detail = it },
                        label = { Text(stringResource(R.string.additional_details_label)) },
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
                        Text(stringResource(R.string.delete_btn))
                    }
                    Spacer(Modifier.weight(1f))
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
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
                }) { Text(stringResource(R.string.save)) }
            }
        },
        dismissButton = {}
    )
}
