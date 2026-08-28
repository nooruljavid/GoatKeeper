package com.goatkeeper.app

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
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
import coil.compose.AsyncImage
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.goatkeeper.app.data.*
import com.goatkeeper.app.ui.LoginScreen
import com.goatkeeper.app.ui.components.*
import com.goatkeeper.app.ui.dashboards.Dashboard
import com.goatkeeper.app.ui.dashboards.HealthDashboard
import com.goatkeeper.app.ui.dashboards.SafetyDashboard
import com.goatkeeper.app.ui.dialogs.GoatDialog
import com.goatkeeper.app.ui.dialogs.RecordDialog
import com.goatkeeper.app.ui.profile.GoatProfile
import com.goatkeeper.app.ui.records.Herd
import com.goatkeeper.app.ui.records.Records
import com.goatkeeper.app.ui.reports.Reports
import com.goatkeeper.app.util.*
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun GoatKeeperApp(dao: FarmDao, share: (String, String, File?) -> Unit) {
    val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
    var user by remember { mutableStateOf(auth.currentUser) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val syncManager = remember(dao) { SyncManager(dao) }

    if (user == null) {
        LoginScreen { newUser ->
            user = newUser
            // On a fresh login, pull the cloud copy. Any local data that belongs to this
            // account is synchronized first when the account is already active.
            scope.launch { syncManager.syncNow() }
        }
    } else {
        MainAppContent(dao, share, syncManager, user!!) {
            scope.launch {
                // Never clear Room data until the current local state has been pushed.
                // This is critical for offline edits followed by sign-out.
                val synced = syncManager.syncNow()
                if (!synced) {
                    android.widget.Toast.makeText(
                        context,
                        "Could not sync your changes. Connect to the internet before signing out.",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                    return@launch
                }

                auth.signOut()

                val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                    .requestIdToken("39674470741-uj8j4igkp4sgsr3cbh346pptu5td2214.apps.googleusercontent.com")
                    .requestEmail()
                    .build()
                GoogleSignIn.getClient(context, gso).signOut()

                dao.clearGoats()
                dao.clearRecords()
                user = null
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun MainAppContent(
    dao: FarmDao,
    share: (String, String, File?) -> Unit,
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
    var selectedGoatTab by rememberSaveable { mutableStateOf("Info") }

    var tabHistory by rememberSaveable { mutableStateOf(listOf(0)) }
    var herdQuery by rememberSaveable { mutableStateOf("") }
    var herdGender by rememberSaveable { mutableStateOf("All") }
    var herdStatus by rememberSaveable { mutableStateOf("All") }
    var recordsType by rememberSaveable { mutableStateOf("All") }
    var recordsQuery by rememberSaveable { mutableStateOf("") }
    var healthFilterType by rememberSaveable { mutableStateOf("All") }
    var safetyFilterType by rememberSaveable { mutableStateOf("All") }

    fun openGoat(id: String, initialTab: String = "Info") {
        selectedGoat = id
        selectedGoatTab = initialTab
        scope.launch {
            dao.updateLastViewed(id, System.currentTimeMillis())
            syncManager.uploadToCloud()
        }
    }

    BackHandler(enabled = selectedGoat != null || tabHistory.size > 1) {
        if (selectedGoat != null) {
            selectedGoat = null
        } else if (tabHistory.size > 1) {
            val newHistory = tabHistory.dropLast(1)
            tab = newHistory.last()
            tabHistory = newHistory
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
            // Use the stable cloud identity, not the editable Goat ID.
            syncManager.deleteGoatFromCloud(goat.cloudId.ifBlank { goat.id })
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
                            onClick = {
                                if (tab != i) {
                                    tab = i
                                    tabHistory = tabHistory + i
                                }
                            },
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
                    initialTab = selectedGoatTab,
                    onAdd = { type -> openAddRecord(type, id) },
                    onEdit = { editGoat = it },
                    onDeleteGoat = { deleteGoat(it) },
                    onEditRecord = { editRecord = it }
                )
            } ?: when (tab) {
                0 -> Dashboard(goats, records, onOpen = { openGoat(it) })
                1 -> Herd(
                    goats = goats,
                    query = herdQuery,
                    onQueryChange = { herdQuery = it },
                    filterGender = herdGender,
                    onGenderChange = { herdGender = it },
                    filterStatus = herdStatus,
                    onStatusChange = { herdStatus = it },
                    onOpen = { openGoat(it) }
                )
                2 -> Records(
                    records = records,
                    goats = goats,
                    type = recordsType,
                    onTypeChange = { recordsType = it },
                    query = recordsQuery,
                    onQueryChange = { recordsQuery = it },
                    healthFilter = healthFilterType,
                    onHealthFilterChange = { healthFilterType = it },
                    safetyFilter = safetyFilterType,
                    onSafetyFilterChange = { safetyFilterType = it },
                    onAdd = { openAddRecord(it) },
                    onEdit = { editRecord = it },
                    onOpen = { id, t -> openGoat(id, t) }
                )
                else -> Reports(goats, records, share)
            }
        }
    }

    if (addGoat) {
        val existingBreeds = goats.map { it.breed }.filter { it.isNotBlank() }.distinct().sorted()
        GoatDialog(
            existing = null,
            allGoats = goats,
            existingBreeds = existingBreeds,
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
        val existingBreeds = goats.map { it.breed }.filter { it.isNotBlank() }.distinct().sorted()
        GoatDialog(
            existing = goat,
            allGoats = goats,
            existingBreeds = existingBreeds,
            onDismiss = { editGoat = null },
            onSave = { updatedGoat ->
                scope.launch {
                    // The cloud document identity is stable, so a Goat ID rename is an update,
                    // not a delete + create. This prevents 0001 and 0002 from co-existing.
                    if (updatedGoat.id != goat.id) {
                        dao.updateGoatId(goat.id, updatedGoat.id)
                        selectedGoat = updatedGoat.id
                    }
                    dao.updateGoat(updatedGoat)
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
            allRecords = records,
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
            allRecords = records,
            initialGoatId = record.goatId,
            existing = record,
            onDismiss = { editRecord = null },
            onSave = ::saveRecord,
            onDelete = { deleteRecord(it) }
        )
    }
}
