package com.goatkeeper.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.goatkeeper.app.data.FarmDao
import com.goatkeeper.app.data.FarmRecord
import com.goatkeeper.app.data.Goat
import com.goatkeeper.app.ui.components.*
import com.goatkeeper.app.util.age
import com.goatkeeper.app.util.isKid
import com.goatkeeper.app.util.kiddingDate
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.Period
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

private val today get() = LocalDate.now().toString()
private val nextWeek get() = LocalDate.now().plusDays(7).toString()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoatKeeperApp(dao: FarmDao, share: (String, String) -> Unit) {
    val goats by dao.goats().collectAsState(initial = emptyList())
    val records by dao.records().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var selectedGoat by rememberSaveable { mutableStateOf<String?>(null) }
    var addGoat by remember { mutableStateOf(false) }
    var addRecordType by remember { mutableStateOf<String?>(null) }
    Scaffold(
        topBar = { 
            TopAppBar(
                title = { Text(if (selectedGoat == null) listOf("Dashboard", "Herd", "Records", "Reports")[tab] else "Goat Profile") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                navigationIcon = {
                    if (selectedGoat != null) IconButton(onClick = { selectedGoat = null }) { 
                        Icon(Icons.Default.ArrowBack, "Back", tint = MaterialTheme.colorScheme.onPrimary) 
                    }
                }
            )
        },
        bottomBar = { 
            if (selectedGoat == null) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp
                ) { 
                    listOf("Dashboard" to Icons.Default.Home, "Herd" to Icons.Default.Pets, "Records" to Icons.Default.List, "Reports" to Icons.Default.Assessment).forEachIndexed { i, item ->
                        NavigationBarItem(
                            selected = tab == i, 
                            onClick = { tab = i }, 
                            icon = { Icon(item.second, item.first) }, 
                            label = { Text(item.first) }
                        )
                    } 
                } 
            }
        },
        floatingActionButton = { 
            if (selectedGoat == null && tab == 1) {
                FloatingActionButton(
                    onClick = { addGoat = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) { 
                    Icon(Icons.Default.Add, "Add goat") 
                } 
            } 
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Box(Modifier.padding(padding)) {
            selectedGoat?.let { id -> GoatProfile(id, dao, goats, onAdd = { addRecordType = it }) }
                ?: when (tab) {
                    0 -> Dashboard(goats, records, onOpen = { selectedGoat = it })
                    1 -> Herd(goats, onOpen = { selectedGoat = it })
                    2 -> Records(records, goats, onOpen = { selectedGoat = it }, onAdd = { addRecordType = it })
                    else -> Reports(goats, records, share)
                }
        }
    }
    if (addGoat) GoatDialog(onDismiss = { addGoat = false }, onSave = { scope.launch { dao.saveGoat(it) }; addGoat = false })
    addRecordType?.let { type -> RecordDialog(type, goats, onDismiss = { addRecordType = null }, onSave = { scope.launch { dao.saveRecord(it) }; addRecordType = null }) }
}

@Composable private fun Dashboard(goats: List<Goat>, records: List<FarmRecord>, onOpen: (String) -> Unit) {
    val activeCount = goats.count { it.status == "Active" }
    val femaleCount = goats.count { it.status == "Active" && it.gender == "Female" }
    val kidsCount = goats.count { it.status == "Active" && isKid(it.dateOfBirth) }
    
    val alerts = records.filter { it.dueDate.isNotBlank() && it.dueDate >= today && it.dueDate <= nextWeek }

    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Text("Your farm at a glance", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatCard("Total Active", activeCount.toString(), "🐐", modifier = Modifier.weight(1f))
                StatCard("Females", femaleCount.toString(), "♀", modifier = Modifier.weight(1f))
                StatCard("Young Kids", kidsCount.toString(), "🍼", modifier = Modifier.weight(1f))
            }
        }
        
        item { Text("Upcoming alerts", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp)) }
        
        if (alerts.isEmpty()) {
            item { 
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFD1FAE5).copy(alpha = 0.5f))) {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text("No upcoming alerts in the next 7 days. ✅", style = MaterialTheme.typography.bodyMedium, color = Color(0xFF065F46))
                    }
                }
            }
        } else {
            items(alerts) { record ->
                RecordItem(
                    record = record,
                    goatName = goats.find { it.id == record.goatId }?.let { if (it.name.isBlank()) it.id else it.name } ?: "Unknown",
                    onClick = { if (record.goatId.isNotBlank()) onOpen(record.goatId) }
                )
            }
        }
        
        item { 
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Recent Goats", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
        }
        
        items(goats.take(3)) { goat ->
            GoatCard(goat, onOpen)
        }
    }
}

@Composable private fun Herd(goats: List<Goat>, onOpen: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    var filterGender by remember { mutableStateOf("All") }
    var filterStatus by remember { mutableStateOf("All") }

    val shown = goats.filter {
        (it.id.contains(query, true) || it.name.contains(query, true) || it.breed.contains(query, true)) &&
        (filterGender == "All" || it.gender == filterGender) &&
        (filterStatus == "All" || it.status == filterStatus)
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), label = { Text("Search ID, name, or breed") }, leadingIcon = { Icon(Icons.Default.Search, null) })
        
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = filterGender == "Female", onClick = { filterGender = if (filterGender == "Female") "All" else "Female" }, label = { Text("♀ Female") })
            FilterChip(selected = filterGender == "Male", onClick = { filterGender = if (filterGender == "Male") "All" else "Male" }, label = { Text("♂ Male") })
            
            var showStatusMenu by remember { mutableStateOf(false) }
            Box {
                AssistChip(onClick = { showStatusMenu = true }, label = { Text("Status: $filterStatus") }, trailingIcon = { Icon(Icons.Default.ArrowDropDown, null) })
                DropdownMenu(expanded = showStatusMenu, onDismissRequest = { showStatusMenu = false }) {
                    listOf("All", "Active", "Sold", "Deceased", "Transferred").forEach { status ->
                        DropdownMenuItem(text = { Text(status) }, onClick = { filterStatus = status; showStatusMenu = false })
                    }
                }
            }
        }

        if (shown.isEmpty()) Empty("No goats match your filters.") else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) { items(shown, key = { it.id }) { GoatCard(it, onOpen) } }
    }
}

@Composable private fun GoatCard(goat: Goat, open: (String) -> Unit) = Card(
    modifier = Modifier.fillMaxWidth().clickable { open(goat.id) },
    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
) {
    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier.size(48.dp).background(
                if (goat.gender == "Female") Color(0xFFD1FAE5) else Color(0xFFDBEAFE),
                RoundedCornerShape(24.dp)
            ),
            contentAlignment = Alignment.Center
        ) {
            Text("🐐", fontSize = 24.sp)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(if (goat.name.isBlank()) goat.id else "${goat.name} · ${goat.id}", fontWeight = FontWeight.Bold)
            Text("${goat.breed} · ${goat.gender} · ${age(goat.dateOfBirth)}", style = MaterialTheme.typography.bodySmall)
        }
        val statusColor = when (goat.status) {
            "Active" -> Color(0xFF10B981)
            "Sold" -> Color(0xFFF59E0B)
            "Deceased" -> Color(0xFFEF4444)
            else -> Color(0xFF6B7280)
        }
        AssistChip(
            onClick = { },
            label = { Text(goat.status, color = statusColor, fontWeight = FontWeight.Bold, fontSize = 11.sp) },
            colors = AssistChipDefaults.assistChipColors(labelColor = statusColor)
        )
    }
}

@Composable private fun Records(records: List<FarmRecord>, goats: List<Goat>, onOpen: (String) -> Unit, onAdd: (String) -> Unit) {
    var type by remember { mutableStateOf("All") }; val types = listOf("All", "Health", "Breeding", "Insurance", "Sale", "Transfer")
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        ScrollableTabRow(selectedTabIndex = types.indexOf(type), edgePadding = 0.dp) { types.forEach { Tab(type == it, { type = it }, text = { Text(it) }) } }
        Spacer(Modifier.height(8.dp)); FilledTonalButton(onClick = { onAdd(if (type == "All") "Health" else type) }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text("Add ${if (type == "All") "record" else type.lowercase()}") }
        val shown = records.filter { type == "All" || it.type == type }
        if (shown.isEmpty()) Empty("No $type records yet.") else LazyColumn(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { 
            items(shown, key = { it.recordId }) { record ->
                RecordItem(
                    record = record,
                    goatName = goats.find { it.id == record.goatId }?.let { if (it.name.isBlank()) it.id else it.name } ?: record.goatId,
                    onClick = { if (record.goatId.isNotBlank()) onOpen(record.goatId) }
                )
            }
        }
    }
}


@Composable private fun GoatProfile(id: String, dao: FarmDao, goats: List<Goat>, onAdd: (String) -> Unit) {
    val goat by dao.goat(id).collectAsState(initial = null)
    val records by dao.recordsFor(id).collectAsState(initial = emptyList())
    var activeTab by remember { mutableStateOf("Info") }
    val tabs = listOf("Info", "Health", "Breeding", "Insurance", "Sales")

    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = tabs.indexOf(activeTab)) {
            tabs.forEach { tab ->
                Tab(selected = activeTab == tab, onClick = { activeTab = tab }, text = { Text(tab) })
            }
        }
        
        when (activeTab) {
            "Info" -> GoatInfoTab(goat, goats)
            else -> GoatRecordsTab(records, goats, activeTab, onAdd)
        }
    }
}

@Composable
private fun GoatInfoTab(goat: Goat?, allGoats: List<Goat>) {
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        goat?.let { g ->
            item {
                Card(elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
                    Column(Modifier.padding(16.dp).fillMaxWidth()) {
                        Text(if (g.name.isBlank()) g.id else "${g.name} (${g.id})", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        HorizontalDivider(Modifier.padding(vertical = 8.dp))
                        InfoRow("Breed", g.breed)
                        InfoRow("Gender", g.gender)
                        InfoRow("Age", age(g.dateOfBirth))
                        InfoRow("Born", g.dateOfBirth)
                        InfoRow("Status", g.status)
                        if (g.damId.isNotBlank()) InfoRow("Dam", allGoats.find { it.id == g.damId }?.let { if (it.name.isBlank()) it.id else it.name } ?: g.damId)
                        if (g.sireId.isNotBlank()) InfoRow("Sire", allGoats.find { it.id == g.sireId }?.let { if (it.name.isBlank()) it.id else it.name } ?: g.sireId)
                    }
                }
            }
            
            if (g.notes.isNotBlank()) {
                item {
                    Text("Notes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Card(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        Text(g.notes, modifier = Modifier.padding(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.outline)
        Text(value, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun GoatRecordsTab(records: List<FarmRecord>, goats: List<Goat>, type: String, onAdd: (String) -> Unit) {
    val shown = records.filter { type == "All" || it.type == type }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Button(onClick = { onAdd(type) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Add, null)
                Spacer(Modifier.width(8.dp))
                Text("Add $type Record")
            }
        }
        if (shown.isEmpty()) {
            item { Empty("No $type records for this goat.") }
        } else {
            items(shown) { record ->
                RecordItem(
                    record = record,
                    goatName = goats.find { it.id == record.goatId }?.let { if (it.name.isBlank()) it.id else it.name } ?: record.goatId,
                    onClick = { }
                )
            }
        }
    }
}

@Composable private fun Reports(goats: List<Goat>, records: List<FarmRecord>, share: (String, String) -> Unit) {
    val sales = records.filter { it.type == "Sale" }; val revenue = sales.sumOf { it.amount ?: 0.0 }
    val report = buildString { appendLine("GOATKEEPER HERD REPORT"); appendLine("Generated: $today"); appendLine("Total goats: ${goats.size}"); appendLine("Active goats: ${goats.count { it.status == "Active" }}"); appendLine("Health records: ${records.count { it.type == "Health" }}"); appendLine("Breeding records: ${records.count { it.type == "Breeding" }}"); appendLine("Sales revenue: $revenue"); appendLine(); goats.forEach { appendLine("${it.id}, ${it.name}, ${it.breed}, ${it.gender}, ${it.status}") } }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { 
        StatCard("Total sales revenue", revenue.toString(), "💰", modifier = Modifier.fillMaxWidth())
        Text("Create a shareable herd summary. Android’s share sheet lets you send it to a vet, insurer, buyer, email, or messaging app.")
        Button(onClick = { share("GoatKeeper Herd Report", report) }, Modifier.fillMaxWidth()) { Icon(Icons.Default.Share, null); Spacer(Modifier.width(8.dp)); Text("Share Herd Summary") }
        OutlinedButton(onClick = { share("GoatKeeper Financial Report", sales.joinToString("\n") { "${it.date}, ${it.title}, ${it.party}, ${it.amount}, ${it.paymentStatus}" }) }, Modifier.fillMaxWidth()) { Text("Share Sales CSV") }
        Text("Tip: choose a spreadsheet app from the share sheet to open the sales CSV.", style = MaterialTheme.typography.bodySmall) 
    }
}

@Composable private fun Empty(text: String) = Box(Modifier.fillMaxWidth().padding(30.dp), contentAlignment = Alignment.Center) { Text(text, style = MaterialTheme.typography.bodyMedium) }

@Composable private fun GoatDialog(onDismiss: () -> Unit, onSave: (Goat) -> Unit) { 
    var id by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var breed by remember { mutableStateOf("") }
    var dob by remember { mutableStateOf(today) }
    var gender by remember { mutableStateOf("Female") }
    var dam by remember { mutableStateOf("") }
    var sire by remember { mutableStateOf("") }
    var color by remember { mutableStateOf("") }
    var microchip by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss, 
        title = { Text("Register Goat", fontWeight = FontWeight.Bold) }, 
        text = { 
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) { 
                item { 
                    Field("Goat ID *", id, change = { id = it })
                    Field("Name", name, change = { name = it })
                    Field("Breed *", breed, change = { breed = it })
                    DatePickerField("Date of Birth *", dob, { dob = it }, Modifier.padding(top = 8.dp))
                    
                    Text("Gender", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = gender == "Female", onClick = { gender = "Female" }, label = { Text("♀ Female") }, modifier = Modifier.weight(1f))
                        FilterChip(selected = gender == "Male", onClick = { gender = "Male" }, label = { Text("♂ Male") }, modifier = Modifier.weight(1f))
                    }
                    
                    Field("Dam ID", dam, change = { dam = it })
                    Field("Sire ID", sire, change = { sire = it })
                    Field("Color / Markings", color, change = { color = it })
                    Field("Microchip ID", microchip, change = { microchip = it })
                    OutlinedTextField(notes, { notes = it }, label = { Text("Notes") }, modifier = Modifier.fillMaxWidth().height(100.dp))
                }
            } 
        }, 
        confirmButton = { 
            TextButton(enabled = id.isNotBlank() && breed.isNotBlank(), onClick = { 
                onSave(Goat(id.trim(), name.trim(), breed.trim(), dob.trim(), gender.trim(), "Active", dam.trim(), sire.trim(), "", color.trim(), microchip.trim(), notes.trim())) 
            }) { Text("Save") } 
        }, 
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    ) 
}

@Composable private fun RecordDialog(type: String, goats: List<Goat>, onDismiss: () -> Unit, onSave: (FarmRecord) -> Unit) { 
    var goatId by remember { mutableStateOf(goats.firstOrNull()?.id ?: "") }
    var date by remember { mutableStateOf(today) }
    var title by remember { mutableStateOf("") }
    var detail by remember { mutableStateOf("") }
    var due by remember { mutableStateOf("") }
    var party by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var payment by remember { mutableStateOf("Paid") }
    
    // Additional fields for specific types
    var quantity by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf("") }
    var sireId by remember { mutableStateOf("") }
    var actualDate by remember { mutableStateOf("") }
    var kidsCount by remember { mutableStateOf("") }
    var kidsAlive by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss, 
        title = { Text("Add $type Record", fontWeight = FontWeight.Bold) }, 
        text = { 
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) { 
                item { 
                    var showGoatMenu by remember { mutableStateOf(false) }
                    Box {
                        OutlinedTextField(
                            value = goats.find { it.id == goatId }?.let { if (it.name.isBlank()) it.id else "${it.name} (${it.id})" } ?: goatId,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Goat *") },
                            modifier = Modifier.fillMaxWidth().clickable { showGoatMenu = true },
                            trailingIcon = { Icon(Icons.Default.ArrowDropDown, null) }
                        )
                        DropdownMenu(expanded = showGoatMenu, onDismissRequest = { showGoatMenu = false }) {
                            goats.forEach { goat ->
                                DropdownMenuItem(
                                    text = { Text(if (goat.name.isBlank()) goat.id else "${goat.name} (${goat.id})") },
                                    onClick = { goatId = goat.id; showGoatMenu = false }
                                )
                            }
                        }
                    }

                    DatePickerField("Record Date *", date, { date = it }, Modifier.padding(top = 8.dp))
                    
                    Field("Title / Description *", title) { title = it }

                    when (type) {
                        "Health" -> {
                            Field("Veterinarian", party, change = { party = it })
                            DatePickerField("Next Due Date", due, { due = it }, Modifier.padding(top = 8.dp))
                            Field("Cost", amount, change = { amount = it })
                        }
                        "Breeding" -> {
                            Field("Sire ID", sireId, change = { sireId = it })
                            val expectedKidding = kiddingDate(date)
                            Text("Expected Kidding: $expectedKidding", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 4.dp))
                            DatePickerField("Actual Kidding Date", actualDate, { actualDate = it }, Modifier.padding(top = 8.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Field("Kids Born", kidsCount, Modifier.weight(1f), change = { kidsCount = it })
                                Field("Kids Alive", kidsAlive, Modifier.weight(1f), change = { kidsAlive = it })
                            }
                        }
                        "Insurance" -> {
                            Field("Insurer", party, change = { party = it })
                            Field("Policy Number", detail, change = { detail = it })
                            DatePickerField("Expiry Date *", due, { due = it }, Modifier.padding(top = 8.dp))
                            Field("Coverage Amount", amount, change = { amount = it })
                        }
                        "Sale" -> {
                            Field("Buyer", party, change = { party = it })
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Field("Quantity", quantity, Modifier.weight(1f), change = { quantity = it })
                                Field("Unit (kg, L)", unit, Modifier.weight(1f), change = { unit = it })
                            }
                            Field("Price", amount, change = { amount = it })
                            Text("Payment Status", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("Paid", "Pending", "Partial").forEach { status ->
                                    FilterChip(selected = payment == status, onClick = { payment = status }, label = { Text(status) }, modifier = Modifier.weight(1f))
                                }
                            }
                        }
                        "Transfer" -> {
                            Field("New Owner / Farm", party, change = { party = it })
                            Field("Reason", detail, change = { detail = it })
                        }
                    }

                    OutlinedTextField(
                        value = if (type == "Breeding" || type == "Sale" || type == "Transfer") "" else detail, 
                        onValueChange = { if (type != "Breeding" && type != "Sale" && type != "Transfer") detail = it }, 
                        label = { Text("Additional Details") }, 
                        modifier = Modifier.fillMaxWidth().height(80.dp)
                    )
                }
            } 
        }, 
        confirmButton = { 
            TextButton(enabled = title.isNotBlank(), onClick = { 
                onSave(FarmRecord(
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
                )) 
            }) { Text("Save") } 
        }, 
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    ) 
}

@Composable private fun Field(label: String, value: String, modifier: Modifier = Modifier, change: (String) -> Unit) = OutlinedTextField(value, change, label = { Text(label) }, singleLine = true, modifier = modifier.fillMaxWidth())
