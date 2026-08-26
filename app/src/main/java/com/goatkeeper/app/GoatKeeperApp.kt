package com.goatkeeper.app
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import coil.compose.AsyncImage
import com.goatkeeper.app.data.*
import com.goatkeeper.app.ui.LoginScreen
import com.goatkeeper.app.ui.components.*
import com.goatkeeper.app.util.*
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate

private val today get() = LocalDate.now().toString()
private val nextWeek get() = LocalDate.now().plusDays(7).toString()

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun GoatKeeperApp(dao: FarmDao, share: (String, String) -> Unit) {
    val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
    var user by remember { mutableStateOf(auth.currentUser) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    
    val syncManager = remember(dao) { SyncManager(dao) }

    if (user == null) {
        LoginScreen { newUser ->
            user = newUser
            syncManager.downloadFromCloud()
        }
    } else {
        MainAppContent(dao, share, syncManager, user!!) {
            scope.launch {
                // 1. Sign out from Firebase
                auth.signOut()
                
                // 2. Sign out from Google to force account selection next time
                val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                    .requestIdToken("39674470741-uj8j4igkp4sgsr3cbh346pptu5td2214.apps.googleusercontent.com")
                    .requestEmail()
                    .build()
                GoogleSignIn.getClient(context, gso).signOut()
                
                // 3. Clear local data to ensure privacy for the next user
                dao.clearGoats()
                dao.clearRecords()
                
                // 4. Update UI state
                user = null
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun MainAppContent(
    dao: FarmDao,
    share: (String, String) -> Unit,
    syncManager: SyncManager,
    user: com.google.firebase.auth.FirebaseUser,
    onSignOut: () -> Unit
) {
    val goats by dao.goats().collectAsState(initial = emptyList())
    val records by dao.records().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val calendarManager = remember { CalendarManager(context) }

    val calendarPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    var tab by rememberSaveable { mutableIntStateOf(0) }
    var selectedGoat by rememberSaveable { mutableStateOf<String?>(null) }
    var addGoat by remember { mutableStateOf(false) }
    var editGoat by remember { mutableStateOf<Goat?>(null) }
    var addRecordType by remember { mutableStateOf<String?>(null) }
    var recordGoatId by remember { mutableStateOf<String?>(null) }
    var editRecord by remember { mutableStateOf<FarmRecord?>(null) }
    var showAccountMenu by remember { mutableStateOf(false) }

    fun openGoat(id: String) {
        selectedGoat = id
        scope.launch {
            dao.updateLastViewed(id, System.currentTimeMillis())
            syncManager.uploadToCloud() // Sync the new timestamp
        }
    }

    fun openAddRecord(type: String, goatId: String? = null) {
        addRecordType = type
        recordGoatId = goatId
    }

    fun saveRecord(record: FarmRecord) {
        scope.launch {
            if (record.recordId == 0L) dao.saveRecord(record) else dao.updateRecord(record)
            when (record.type) {
                "Sale" -> dao.updateGoatStatus(record.goatId, "Sold")
                "Transfer" -> dao.updateGoatStatus(record.goatId, "Transferred")
            }
            syncManager.uploadToCloud()
            
            // Sync with Google Calendar if a due date is set
            if (record.dueDate.isNotBlank()) {
                val goatName = goats.find { it.id == record.goatId }?.name?.ifBlank { record.goatId } ?: record.goatId
                calendarManager.addReminder(record, goatName)
            }
        }
        addRecordType = null
        recordGoatId = null
        editRecord = null
    }

    fun deleteRecord(record: FarmRecord) {
        scope.launch {
            dao.deleteRecord(record)
            syncManager.deleteRecordFromCloud(record.recordId)
        }
        editRecord = null
    }

    fun deleteGoat(goat: Goat) {
        scope.launch {
            dao.deleteGoat(goat)
            syncManager.deleteGoatFromCloud(goat.id)
        }
        selectedGoat = null
        editGoat = null
    }

    LaunchedEffect(Unit) {
        calendarPermissionLauncher.launch(android.Manifest.permission.WRITE_CALENDAR)
    }

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
                    if (selectedGoat != null) {
                        IconButton(onClick = { selectedGoat = null }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                },
                actions = {
                    if (selectedGoat == null) {
                        Box {
                            IconButton(onClick = { showAccountMenu = true }) {
                                if (user.photoUrl != null) {
                                    AsyncImage(
                                        model = user.photoUrl,
                                        contentDescription = "Profile",
                                        modifier = Modifier.size(32.dp).clip(RoundedCornerShape(16.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Icon(Icons.Default.AccountCircle, "Account", tint = MaterialTheme.colorScheme.onPrimary)
                                }
                            }
                            DropdownMenu(
                                expanded = showAccountMenu,
                                onDismissRequest = { showAccountMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text("Signed in as:", style = MaterialTheme.typography.labelSmall)
                                            Text(user.email ?: "Unknown User", fontWeight = FontWeight.Bold)
                                        }
                                    },
                                    onClick = { },
                                    enabled = false
                                )
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.AutoMirrored.Filled.Logout, null, modifier = Modifier.size(20.dp))
                                            Spacer(Modifier.width(12.dp))
                                            Text("Sign Out")
                                        }
                                    },
                                    onClick = {
                                        showAccountMenu = false
                                        onSignOut()
                                    }
                                )
                            }
                        }
                    }
                }
            )
        },
        bottomBar = {
            if (selectedGoat == null) {
                NavigationBar {
                    listOf(
                        "Dashboard" to Icons.Default.Home,
                        "Herd" to Icons.Default.Pets,
                        "Records" to Icons.AutoMirrored.Filled.List,
                        "Reports" to Icons.Default.Assessment
                    ).forEachIndexed { i, item ->
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
                FloatingActionButton(onClick = { addGoat = true }) {
                    Icon(Icons.Default.Add, "Add goat")
                }
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding)) {
            selectedGoat?.let { id ->
                GoatProfile(
                    id = id,
                    dao = dao,
                    goats = goats,
                    onAdd = { type -> openAddRecord(type, id) },
                    onEdit = { editGoat = it },
                    onDeleteGoat = { deleteGoat(it) },
                    onEditRecord = { editRecord = it },
                    onMarkDeceased = { goatId -> scope.launch { dao.updateGoatStatus(goatId, "Deceased") } }
                )
            } ?: when (tab) {
                0 -> Dashboard(goats, records, onOpen = ::openGoat)
                1 -> Herd(goats, onOpen = ::openGoat)
                2 -> Records(
                    records,
                    goats,
                    onAdd = { openAddRecord(it) },
                    onEdit = { editRecord = it }
                )
                else -> Reports(goats, records, share)
            }
        }
    }

    if (addGoat) {
        GoatDialog(
            existing = null,
            onDismiss = { addGoat = false },
            onSave = {
                scope.launch { 
                    dao.saveGoat(it)
                    syncManager.uploadToCloud()
                }
                addGoat = false
            }
        )
    }

    editGoat?.let { goat ->
        GoatDialog(
            existing = goat,
            onDismiss = { editGoat = null },
            onSave = { updatedGoat ->
                scope.launch { 
                    if (updatedGoat.id != goat.id) {
                        // 1. Delete the old ID from the cloud
                        syncManager.deleteGoatFromCloud(goat.id)
                        
                        // 2. Update the local ID (cascades to records)
                        dao.updateGoatId(goat.id, updatedGoat.id)
                        selectedGoat = updatedGoat.id
                    }
                    // 3. Save the new/updated goat locally
                    dao.updateGoat(updatedGoat)
                    
                    // 4. Push all changes to the cloud
                    syncManager.uploadToCloud()
                }
                editGoat = null
            },
            onDelete = { deleteGoat(it) }
        )
    }

    addRecordType?.let { type ->
        RecordDialog(
            type = type,
            goats = goats,
            initialGoatId = recordGoatId,
            existing = null,
            onDismiss = {
                addRecordType = null
                recordGoatId = null
            },
            onSave = ::saveRecord
        )
    }

    editRecord?.let { record ->
        RecordDialog(
            type = record.type,
            goats = goats,
            initialGoatId = record.goatId,
            existing = record,
            onDismiss = { editRecord = null },
            onSave = ::saveRecord,
            onDelete = { deleteRecord(it) }
        )
    }
}

@Composable
private fun Dashboard(goats: List<Goat>, records: List<FarmRecord>, onOpen: (String) -> Unit) {
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
        item { Text("Upcoming alerts", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
        if (alerts.isEmpty()) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFD1FAE5).copy(alpha = 0.5f))) {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text("No upcoming alerts in the next 7 days. ✅", color = Color(0xFF065F46))
                    }
                }
            }
        } else {
            items(alerts, key = { it.recordId }) { record ->
                RecordItem(
                    record = record,
                    goatName = goats.find { it.id == record.goatId }?.name?.ifBlank { record.goatId } ?: "Unknown",
                    onClick = { if (record.goatId.isNotBlank()) onOpen(record.goatId) }
                )
            }
        }
        item { Text("Recent Goats", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
        val recentGoats = goats.sortedByDescending { it.lastViewed }.take(3)
        items(recentGoats, key = { it.id }) { goat -> GoatCard(goat, onOpen) }
    }
}

@Composable
private fun Herd(goats: List<Goat>, onOpen: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    var filterGender by remember { mutableStateOf("All") }
    var filterStatus by remember { mutableStateOf("All") }
    var showStatusMenu by remember { mutableStateOf(false) }

    val shown = goats.filter {
        (it.id.contains(query, true) || it.name.contains(query, true) || it.breed.contains(query, true)) &&
            (filterGender == "All" || it.gender == filterGender) &&
            (filterStatus == "All" || it.status == filterStatus)
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        OutlinedTextField(
            query,
            { query = it },
            Modifier.fillMaxWidth(),
            label = { Text("Search ID, name, or breed") },
            leadingIcon = { Icon(Icons.Default.Search, null) }
        )
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = filterGender == "Female", onClick = { filterGender = if (filterGender == "Female") "All" else "Female" }, label = { Text("♀ Female") })
            FilterChip(selected = filterGender == "Male", onClick = { filterGender = if (filterGender == "Male") "All" else "Male" }, label = { Text("♂ Male") })
            Box {
                AssistChip(onClick = { showStatusMenu = true }, label = { Text("Status: $filterStatus") }, trailingIcon = { Icon(Icons.Default.ArrowDropDown, null) })
                DropdownMenu(expanded = showStatusMenu, onDismissRequest = { showStatusMenu = false }) {
                    listOf("All", "Active", "Sold", "Deceased", "Transferred").forEach { status ->
                        DropdownMenuItem(text = { Text(status) }, onClick = { filterStatus = status; showStatusMenu = false })
                    }
                }
            }
        }
        if (shown.isEmpty()) {
            Empty("No goats match your filters.")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(shown, key = { it.id }) { GoatCard(it, onOpen) }
            }
        }
    }
}

@Composable
private fun GoatCard(goat: Goat, open: (String) -> Unit) = Card(
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
            Text("${goat.breed} · ${goat.gender} · ${age(goat.dateOfBirth)}", style = MaterialTheme.typography.bodySmall)
        }
        val statusColor = when (goat.status) {
            "Active" -> Color(0xFF10B981)
            "Sold" -> Color(0xFFF59E0B)
            "Deceased" -> Color(0xFFEF4444)
            else -> Color(0xFF6B7280)
        }
        AssistChip(onClick = { }, label = { Text(goat.status, color = statusColor, fontWeight = FontWeight.Bold, fontSize = 11.sp) })
    }
}

@Composable
private fun Records(
    records: List<FarmRecord>,
    goats: List<Goat>,
    onAdd: (String) -> Unit,
    onEdit: (FarmRecord) -> Unit
) {
    var type by remember { mutableStateOf("All") }
    val types = listOf("All", "Health", "Breeding", "Insurance", "Sale", "Transfer")
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        ScrollableTabRow(selectedTabIndex = types.indexOf(type), edgePadding = 0.dp) {
            types.forEach { Tab(type == it, { type = it }, text = { Text(it) }) }
        }
        Spacer(Modifier.height(8.dp))
        FilledTonalButton(onClick = { onAdd(if (type == "All") "Health" else type) }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text("Add ${if (type == "All") "record" else type.lowercase()}")
        }
        val shown = records.filter { type == "All" || it.type == type }
        if (shown.isEmpty()) {
            Empty("No $type records yet.")
        } else {
            LazyColumn(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(shown, key = { it.recordId }) { record ->
                    RecordItem(
                        record = record,
                        goatName = goats.find { it.id == record.goatId }?.name?.ifBlank { record.goatId } ?: record.goatId,
                        onClick = { onEdit(record) }
                    )
                }
            }
        }
    }
}

@Composable
private fun GoatProfile(
    id: String,
    dao: FarmDao,
    goats: List<Goat>,
    onAdd: (String) -> Unit,
    onEdit: (Goat) -> Unit,
    onDeleteGoat: (Goat) -> Unit,
    onEditRecord: (FarmRecord) -> Unit,
    onMarkDeceased: (String) -> Unit
) {
    val goat by dao.goat(id).collectAsState(initial = null)
    val records by dao.recordsFor(id).collectAsState(initial = emptyList())
    var activeTab by remember { mutableStateOf("Info") }
    val tabs = listOf("Info", "Health", "Breeding", "Insurance", "Sales")

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
            "Info" -> GoatInfoTab(goat, goats, onEdit, onDeleteGoat, onMarkDeceased)
            else -> GoatRecordsTab(records, goats, activeTab, onAdd, onEditRecord)
        }
    }
}

@Composable
private fun GoatInfoTab(
    goat: Goat?,
    allGoats: List<Goat>,
    onEdit: (Goat) -> Unit,
    onDelete: (Goat) -> Unit,
    onMarkDeceased: (String) -> Unit
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
                        if (g.damId.isNotBlank()) InfoRow("Dam", allGoats.find { it.id == g.damId }?.name?.ifBlank { g.damId } ?: g.damId)
                        if (g.sireId.isNotBlank()) InfoRow("Sire", allGoats.find { it.id == g.sireId }?.name?.ifBlank { g.sireId } ?: g.sireId)
                        if (g.colorMarkings.isNotBlank()) InfoRow("Color / Markings", g.colorMarkings)
                        if (g.microchipId.isNotBlank()) InfoRow("Microchip", g.microchipId)
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { onEdit(g) }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Edit, null); Spacer(Modifier.width(6.dp)); Text("Edit Goat")
                        }
                        if (g.status == "Active") {
                            OutlinedButton(onClick = { onMarkDeceased(g.id) }, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Default.Close, null); Spacer(Modifier.width(6.dp)); Text("Deceased")
                            }
                        }
                    }
                    OutlinedButton(
                        onClick = { onDelete(g) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.Delete, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Delete Goat Entry")
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
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.outline)
        Text(value, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun GoatRecordsTab(
    records: List<FarmRecord>,
    goats: List<Goat>,
    type: String,
    onAdd: (String) -> Unit,
    onEdit: (FarmRecord) -> Unit
) {
    val recordType = if (type == "Sales") "Sale" else type
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
                    goatName = goats.find { it.id == record.goatId }?.let { if (it.name.isBlank()) it.id else it.name } ?: record.goatId,
                    onClick = { onEdit(record) }
                )
            }
        }
    }
}

@Composable
private fun Reports(goats: List<Goat>, records: List<FarmRecord>, share: (String, String) -> Unit) {
    val sales = records.filter { it.type == "Sale" }
    val revenue = sales.sumOf { it.amount ?: 0.0 }
    val report = buildString {
        appendLine("GOATKEEPER HERD REPORT")
        appendLine("Generated: ${formatDate(today)}")
        appendLine("Total goats: ${goats.size}")
        appendLine("Active goats: ${goats.count { it.status == "Active" }}")
        appendLine("Health records: ${records.count { it.type == "Health" }}")
        appendLine("Breeding records: ${records.count { it.type == "Breeding" }}")
        appendLine("Sales revenue: $revenue")
        appendLine()
        goats.forEach { appendLine("${it.id}, ${it.name}, ${it.breed}, ${it.gender}, ${it.status}") }
    }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        StatCard("Total sales revenue", revenue.toString(), "💰", modifier = Modifier.fillMaxWidth())
        Text("Create a shareable herd summary. Android’s share sheet lets you send it to a vet, insurer, buyer, email, or messaging app.")
        Button(onClick = { share("GoatKeeper Herd Report", report) }, Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Share, null); Spacer(Modifier.width(8.dp)); Text("Share Herd Summary")
        }
        OutlinedButton(onClick = { share("GoatKeeper Financial Report", sales.joinToString("\n") { "${formatDate(it.date)}, ${it.title}, ${it.party}, ${it.amount}, ${it.paymentStatus}" }) }, Modifier.fillMaxWidth()) {
            Text("Share Sales CSV")
        }
        Text("Tip: choose a spreadsheet app from the share sheet to open the sales CSV.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun Empty(text: String) = Box(Modifier.fillMaxWidth().padding(30.dp), contentAlignment = Alignment.Center) { Text(text) }

@Composable
private fun GoatDialog(
    existing: Goat?,
    onDismiss: () -> Unit,
    onSave: (Goat) -> Unit,
    onDelete: ((Goat) -> Unit)? = null
) {
    var id by remember(existing?.id) { mutableStateOf(existing?.id ?: "") }
    var name by remember(existing?.id) { mutableStateOf(existing?.name ?: "") }
    var breed by remember(existing?.id) { mutableStateOf(existing?.breed ?: "") }
    var dob by remember(existing?.id) { mutableStateOf(existing?.dateOfBirth ?: today) }
    var gender by remember(existing?.id) { mutableStateOf(existing?.gender ?: "Female") }
    var status by remember(existing?.id) { mutableStateOf(existing?.status ?: "Active") }
    var dam by remember(existing?.id) { mutableStateOf(existing?.damId ?: "") }
    var sire by remember(existing?.id) { mutableStateOf(existing?.sireId ?: "") }
    var color by remember(existing?.id) { mutableStateOf(existing?.colorMarkings ?: "") }
    var microchip by remember(existing?.id) { mutableStateOf(existing?.microchipId ?: "") }
    var notes by remember(existing?.id) { mutableStateOf(existing?.notes ?: "") }
    var photoUri by remember(existing?.id) { mutableStateOf(existing?.photoUri ?: "") }
    var showPhotoOptions by remember { mutableStateOf(false) }
    
    val context = LocalContext.current
    var tempCameraUri by remember { mutableStateOf<android.net.Uri?>(null) }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri -> if (uri != null) photoUri = uri.toString() }
    )

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
        onResult = { success ->
            if (success && tempCameraUri != null) {
                photoUri = tempCameraUri.toString()
            }
        }
    )

    fun takePhoto() {
        val file = File(context.getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES), "goat_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(context, "com.goatkeeper.app.fileprovider", file)
        tempCameraUri = uri
        cameraLauncher.launch(uri)
    }

    if (showPhotoOptions) {
        AlertDialog(
            onDismissRequest = { showPhotoOptions = false },
            title = { Text("Goat Photo") },
            text = { Text("Choose a photo source for your goat.") },
            confirmButton = {
                TextButton(onClick = {
                    showPhotoOptions = false
                    takePhoto()
                }) {
                    Icon(Icons.Default.CameraAlt, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Camera")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showPhotoOptions = false
                    photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }) {
                    Icon(Icons.Default.PhotoLibrary, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Gallery")
                }
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Register Goat" else "Edit Goat", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { showPhotoOptions = true },
                    contentAlignment = Alignment.Center
                ) {
                    if (photoUri.isBlank()) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.AddAPhoto, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(4.dp))
                            Text("Add Photo", style = MaterialTheme.typography.labelMedium)
                        }
                    } else {
                        AsyncImage(
                            model = photoUri,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                        Surface(
                            modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                        ) {
                            Icon(Icons.Default.Edit, null, modifier = Modifier.padding(4.dp).size(16.dp), tint = Color.White)
                        }
                    }
                }
                
                Field("Goat ID *", id, change = { id = it })
                Field("Name", name, change = { name = it })
                Field("Breed *", breed, change = { breed = it })
                DatePickerField("Date of Birth *", dob, { dob = it })
                
                Column {
                    Text("Gender", style = MaterialTheme.typography.labelMedium)
                    Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = gender == "Female", onClick = { gender = "Female" }, label = { Text("♀ Female") }, modifier = Modifier.weight(1f))
                        FilterChip(selected = gender == "Male", onClick = { gender = "Male" }, label = { Text("♂ Male") }, modifier = Modifier.weight(1f))
                    }
                }
                
                Column {
                    Text("Status", style = MaterialTheme.typography.labelMedium)
                    @OptIn(ExperimentalLayoutApi::class)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("Active", "Sold", "Deceased", "Transferred").forEach { value ->
                            FilterChip(selected = status == value, onClick = { status = value }, label = { Text(value) })
                        }
                    }
                }
                
                Field("Dam ID", dam, change = { dam = it })
                Field("Sire ID", sire, change = { sire = it })
                Field("Color / Markings", color, change = { color = it })
                Field("Microchip ID", microchip, change = { microchip = it })
                OutlinedTextField(notes, { notes = it }, label = { Text("Notes") }, modifier = Modifier.fillMaxWidth().height(100.dp))
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
                TextButton(enabled = id.isNotBlank() && breed.isNotBlank(), onClick = {
                    onSave(
                        Goat(
                            id = id.trim(),
                            name = name.trim(),
                            breed = breed.trim(),
                            dateOfBirth = dob.trim(),
                            gender = gender.trim(),
                            status = status,
                            damId = dam.trim(),
                            sireId = sire.trim(),
                            photoUri = photoUri,
                            colorMarkings = color.trim(),
                            microchipId = microchip.trim(),
                            notes = notes.trim()
                        )
                    )
                }) { Text("Save") }
            }
        },
        dismissButton = {}
    )
}

@Composable
private fun RecordDialog(
    type: String,
    goats: List<Goat>,
    initialGoatId: String?,
    existing: FarmRecord?,
    onDismiss: () -> Unit,
    onSave: (FarmRecord) -> Unit,
    onDelete: ((FarmRecord) -> Unit)? = null
) {
    var goatId by remember(existing?.recordId, initialGoatId) { mutableStateOf(existing?.goatId ?: initialGoatId ?: goats.firstOrNull()?.id.orEmpty()) }
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

    LaunchedEffect(goats) {
        if (goatId.isBlank() && goats.isNotEmpty()) {
            goatId = goats.first().id
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
                        value = goats.find { it.id == goatId }?.let { if (it.name.isBlank()) it.id else "${it.name} (${it.id})" } ?: goatId,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Goat *") },
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
                Field("Title / Description *", title) { title = it }

                when (type) {
                    "Health" -> {
                        Field("Veterinarian", party) { party = it }
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
                        Field("Insurer", party) { party = it }
                        Field("Policy Number", detail) { detail = it }
                        DatePickerField("Expiry Date *", due, { due = it })
                        Field("Coverage Amount", amount) { amount = it }
                    }
                    "Sale" -> {
                        Field("Buyer", party) { party = it }
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
                        Field("New Owner / Farm", party) { party = it }
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
                TextButton(enabled = goatId.isNotBlank() && title.isNotBlank(), onClick = {
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

@Composable
private fun Field(label: String, value: String, modifier: Modifier = Modifier, change: (String) -> Unit) =
    OutlinedTextField(value, change, label = { Text(label) }, singleLine = true, modifier = modifier.fillMaxWidth())
