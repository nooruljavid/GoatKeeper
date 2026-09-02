package com.goatkeeper.app

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun GoatKeeperApp(dao: FarmDao, share: (String, String, File?) -> Unit) {
    val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
    var user by remember { mutableStateOf(auth.currentUser) }
    var isSigningOut by remember { mutableStateOf(false) }
    var showLogin by remember { mutableStateOf(false) }
    var forceRegistryAfterLogin by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val syncManager = remember(dao) { SyncManager(context.applicationContext, dao) }

    val farmDetails by dao.farmDetails().collectAsState(initial = null)
    val appSettings by dao.appSettings().collectAsState(initial = null)

    LaunchedEffect(appSettings) {
        appSettings?.let {
            val appLocale = androidx.core.os.LocaleListCompat.forLanguageTags(it.language)
            val currentLocales = androidx.appcompat.app.AppCompatDelegate.getApplicationLocales()
            
            // Only apply if the locale is actually different to prevent recreation loops
            // Comparison is done using language tags to handle partial matches (e.g., 'ta' vs 'ta-IN')
            val currentLang = currentLocales.toLanguageTags().split(",").firstOrNull()?.split("-")?.firstOrNull() ?: "en"
            if (currentLang != it.language) {
                androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(appLocale)
            }
        }
    }

    fun performSignOut(clearLocalData: Boolean = true) {
        scope.launch {
            if (clearLocalData) {
                isSigningOut = true
                // Never clear Room data until the current local state has been pushed.
                val synced = syncManager.syncNow()
                if (!synced && user != null) {
                    isSigningOut = false
                    android.widget.Toast.makeText(
                        context,
                        "Could not sync your changes. Connect to the internet before signing out.",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                    return@launch
                }
            }

            auth.signOut()

            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken("39674470741-uj8j4igkp4sgsr3cbh346pptu5td2214.apps.googleusercontent.com")
                .requestEmail()
                .build()
            GoogleSignIn.getClient(context, gso).signOut()

            if (clearLocalData) {
                dao.clearGoats()
                dao.clearRecords()
                dao.clearFarmDetails()
            }
            
            user = null
            isSigningOut = false
            forceRegistryAfterLogin = false
        }
    }

    Box(Modifier.fillMaxSize()) {
        if (showLogin) {
            LoginScreen(
                onLoginSuccess = { newUser ->
                    user = newUser
                    showLogin = false
                    // Start sync to check for existing cloud registry
                    scope.launch {
                        syncManager.syncNow()
                        // After sync, if still no local details, trigger the registry dialog
                        if (dao.farmDetails().first() == null) {
                            forceRegistryAfterLogin = true
                        }
                    }
                },
                onDismiss = { showLogin = false }
            )
        } else {
            MainAppContent(
                dao = dao,
                share = share,
                syncManager = syncManager,
                user = user,
                appSettings = appSettings,
                editFarmDetails = forceRegistryAfterLogin,
                onCloseFarmDetails = { confirmed ->
                    if (forceRegistryAfterLogin && !confirmed) {
                        // User cancelled the mandatory registry, so log them out 
                        // but KEEP their local data.
                        performSignOut(clearLocalData = false)
                    }
                    forceRegistryAfterLogin = false 
                },
                onSignIn = { showLogin = true },
                onSignOut = { performSignOut(clearLocalData = true) }
            )
        }

        if (isSigningOut) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color.Black.copy(alpha = 0.5f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(16.dp))
                            Text("Signing out and syncing...", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainAppContent(
    dao: FarmDao,
    share: (String, String, File?) -> Unit,
    syncManager: SyncManager,
    user: com.google.firebase.auth.FirebaseUser?,
    appSettings: AppSettings?,
    editFarmDetails: Boolean = false,
    onCloseFarmDetails: (Boolean) -> Unit = {},
    onSignIn: () -> Unit,
    onSignOut: () -> Unit
) {
    val goats by dao.goats().collectAsState(initial = emptyList())
    val records by dao.records().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val calendarManager = remember { CalendarManager(context) }

    // Smooth entry transition for language switches
    val alphaAnim = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        alphaAnim.animateTo(1f, animationSpec = tween(600))
    }

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
    var showFarmDetails by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showAccountMenu by remember { mutableStateOf(false) }
    var selectedGoatTab by rememberSaveable { mutableStateOf("Info") }

    val farmDetails by dao.farmDetails().collectAsState(initial = null)

    val currencySymbol = remember(farmDetails) {
        val selected = countries.find { it.name.trim().equals(farmDetails?.country?.trim(), ignoreCase = true) }
        android.util.Log.d("GoatKeeperApp", "Country: ${farmDetails?.country}, Selected: ${selected?.name}, Symbol: ${selected?.currencySymbol}")
        selected?.currencySymbol ?: "₹"
    }

    val activeEditFarmDetails = editFarmDetails || showFarmDetails

    var tabHistory by rememberSaveable { mutableStateOf(listOf(0)) }
    var herdQuery by rememberSaveable { mutableStateOf("") }
    var herdGender by rememberSaveable { mutableStateOf("All") }
    var herdStatus by rememberSaveable { mutableStateOf("All") }
    var recordsType by rememberSaveable { mutableStateOf("All") }
    var recordsQuery by rememberSaveable { mutableStateOf("") }
    var healthFilterType by rememberSaveable { mutableStateOf("All") }
    var safetyFilterType by rememberSaveable { mutableStateOf("All") }
    var salesFilterType by rememberSaveable { mutableStateOf("All") }
    var salesTimeFilter by rememberSaveable { mutableStateOf("Monthly") }

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
            record.goatId?.let { gid ->
                when (record.type) {
                    "Sale", "Goat Sale" -> dao.updateGoatStatus(gid, "Sold")
                    "Transfer" -> dao.updateGoatStatus(gid, "Transferred")
                }
            }
            syncManager.uploadToCloud()

            if (record.dueDate.isNotBlank()) {
                val goatName = record.goatId?.let { gid ->
                    goats.find { it.id == gid }?.name?.ifBlank { gid } ?: gid
                } ?: "Entire Herd"
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
            // If deleting an individual goat sale, revert status to Active
            if ((record.type == "Sale" || record.type == "Goat Sale") && record.goatId != null) {
                dao.updateGoatStatus(record.goatId, "Active")
            }
            syncManager.deleteRecordFromCloud(record.recordId)
        }
        editRecord = null
    }

    fun deleteGoat(goat: Goat) {
        scope.launch {
            dao.deleteGoat(goat)
            // Use the stable cloud identity for the document, and tag ID to clean up records.
            syncManager.deleteGoatFromCloud(
                cloudId = goat.cloudId.ifBlank { goat.id },
                tagId = goat.id
            )
        }
        selectedGoat = null
        editGoat = null
    }

    LaunchedEffect(Unit) {
        calendarPermissionLauncher.launch(android.Manifest.permission.WRITE_CALENDAR)
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .alpha(alphaAnim.value),
        color = MaterialTheme.colorScheme.background
    ) {
        Scaffold(
            topBar = {
            TopAppBar(
                title = { 
                    Text(
                        if (selectedGoat == null) {
                            listOf(
                                stringResource(R.string.dashboard),
                                stringResource(R.string.herd),
                                stringResource(R.string.records),
                                stringResource(R.string.reports)
                            )[tab]
                        } else {
                            stringResource(R.string.goat_profile)
                        }
                    ) 
                },
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
                                if (user?.photoUrl != null) {
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
                                if (user != null) {
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
                                } else {
                                    DropdownMenuItem(
                                        text = {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.Login, null, modifier = Modifier.size(20.dp))
                                                Spacer(Modifier.width(12.dp))
                                                Text(stringResource(R.string.sign_in))
                                            }
                                        },
                                        onClick = {
                                            showAccountMenu = false
                                            onSignIn()
                                        }
                                    )
                                }
                                
                                HorizontalDivider()

                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Storefront, null, modifier = Modifier.size(20.dp))
                                            Spacer(Modifier.width(12.dp))
                                            Text(stringResource(R.string.farm_registry))
                                        }
                                    },
                                    onClick = {
                                        showAccountMenu = false
                                        showFarmDetails = true
                                    }
                                )

                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Settings, null, modifier = Modifier.size(20.dp))
                                            Spacer(Modifier.width(12.dp))
                                            Text(stringResource(R.string.settings))
                                        }
                                    },
                                    onClick = {
                                        showAccountMenu = false
                                        showSettings = true
                                    }
                                )

                                if (user != null) {
                                    HorizontalDivider()
                                    DropdownMenuItem(
                                        text = {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.AutoMirrored.Filled.Logout, null, modifier = Modifier.size(20.dp))
                                                Spacer(Modifier.width(12.dp))
                                                Text(stringResource(R.string.sign_out))
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
                }
            )
        },
        bottomBar = {
            if (selectedGoat == null) {
                NavigationBar {
                    listOf(
                        stringResource(R.string.dashboard) to Icons.Default.Home,
                        stringResource(R.string.herd) to Icons.Default.Agriculture,
                        stringResource(R.string.records) to Icons.AutoMirrored.Filled.List,
                        stringResource(R.string.reports) to Icons.Default.Assessment
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
                0 -> Dashboard(goats, records, currencySymbol, onOpen = { openGoat(it) })
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
                    salesFilter = salesFilterType,
                    onSalesFilterChange = { salesFilterType = it },
                    salesTimeFilter = salesTimeFilter,
                    onSalesTimeFilterChange = { salesTimeFilter = it },
                    currencySymbol = currencySymbol,
                    onAdd = { openAddRecord(it) },
                    onEdit = { editRecord = it },
                    onOpen = { id, t -> openGoat(id, t) }
                )
                else -> Reports(goats, records, share)
            }
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
                    // Handle automatic sale record deletion if status changed from Sold to Active
                    if (goat.status == "Sold" && updatedGoat.status == "Active") {
                        val saleRecords = dao.findRecordsByTypeForGoat(goat.id, "Goat Sale") + dao.findRecordsByTypeForGoat(goat.id, "Sale")
                        saleRecords.forEach { syncManager.deleteRecordFromCloud(it.recordId) }
                        dao.deleteRecordsByTypeForGoat(goat.id, "Goat Sale")
                        dao.deleteRecordsByTypeForGoat(goat.id, "Sale")
                    }
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
            currencySymbol = currencySymbol,
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
            currencySymbol = currencySymbol,
            onDismiss = { editRecord = null },
            onSave = ::saveRecord,
            onDelete = { deleteRecord(it) }
        )
    }

    if (activeEditFarmDetails) {
        com.goatkeeper.app.ui.dialogs.FarmDetailsDialog(
            existing = farmDetails,
            onDismiss = { 
                showFarmDetails = false
                onCloseFarmDetails(false)
            },
            onSave = {
                scope.launch {
                    dao.saveFarmDetails(it)
                    syncManager.uploadToCloud()
                }
                showFarmDetails = false
                onCloseFarmDetails(true)
            }
        )
    }

    if (showSettings) {
        com.goatkeeper.app.ui.dialogs.SettingsDialog(
            existing = appSettings,
            onDismiss = { showSettings = false },
            onSave = {
                scope.launch {
                    dao.saveAppSettings(it)
                }
                showSettings = false
            }
        )
    }
}

