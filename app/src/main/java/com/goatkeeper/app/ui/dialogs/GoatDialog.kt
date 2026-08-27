package com.goatkeeper.app.ui.dialogs

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.goatkeeper.app.data.Goat
import com.goatkeeper.app.ui.components.DatePickerField
import com.goatkeeper.app.util.today
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun GoatDialog(
    existing: Goat?,
    allGoats: List<Goat>,
    existingBreeds: List<String>,
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
    
    var showBreedMenu by remember { mutableStateOf(false) }
    val filteredBreeds = existingBreeds.filter { it.contains(breed, ignoreCase = true) }

    var showDamMenu by remember { mutableStateOf(false) }
    val filteredDams = allGoats.filter { it.gender == "Female" && (it.id.contains(dam, true) || it.name.contains(dam, true)) }

    var showSireMenu by remember { mutableStateOf(false) }
    val filteredSires = allGoats.filter { it.gender == "Male" && (it.id.contains(sire, true) || it.name.contains(sire, true)) }

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
                
                Box(modifier = Modifier.fillMaxWidth()) {
                    Field("Breed *", breed, change = { 
                        breed = it
                        showBreedMenu = true
                    })
                    if (showBreedMenu && filteredBreeds.isNotEmpty()) {
                        DropdownMenu(
                            expanded = showBreedMenu,
                            onDismissRequest = { showBreedMenu = false },
                            modifier = Modifier.fillMaxWidth(0.9f),
                            properties = androidx.compose.ui.window.PopupProperties(focusable = false)
                        ) {
                            filteredBreeds.forEach { suggestion ->
                                DropdownMenuItem(
                                    text = { Text(suggestion) },
                                    onClick = {
                                        breed = suggestion
                                        showBreedMenu = false
                                    }
                                )
                            }
                        }
                    }
                }
                
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
                
                Box(modifier = Modifier.fillMaxWidth()) {
                    Field("Dam ID", dam, change = { 
                        dam = it
                        showDamMenu = true
                    })
                    if (showDamMenu && filteredDams.isNotEmpty()) {
                        DropdownMenu(
                            expanded = showDamMenu,
                            onDismissRequest = { showDamMenu = false },
                            modifier = Modifier.fillMaxWidth(0.9f),
                            properties = androidx.compose.ui.window.PopupProperties(focusable = false)
                        ) {
                            filteredDams.forEach { d ->
                                DropdownMenuItem(
                                    text = { Text("${d.id} ${if(d.name.isNotBlank()) "(${d.name})" else ""}") },
                                    onClick = {
                                        dam = d.id
                                        showDamMenu = false
                                    }
                                )
                            }
                        }
                    }
                }

                Box(modifier = Modifier.fillMaxWidth()) {
                    Field("Sire ID", sire, change = { 
                        sire = it
                        showSireMenu = true
                    })
                    if (showSireMenu && filteredSires.isNotEmpty()) {
                        DropdownMenu(
                            expanded = showSireMenu,
                            onDismissRequest = { showSireMenu = false },
                            modifier = Modifier.fillMaxWidth(0.9f),
                            properties = androidx.compose.ui.window.PopupProperties(focusable = false)
                        ) {
                            filteredSires.forEach { s ->
                                DropdownMenuItem(
                                    text = { Text("${s.id} ${if(s.name.isNotBlank()) "(${s.name})" else ""}") },
                                    onClick = {
                                        sire = s.id
                                        showSireMenu = false
                                    }
                                )
                            }
                        }
                    }
                }
                
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
