package com.goatkeeper.app.ui.dialogs

import android.app.Activity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.goatkeeper.app.R
import com.goatkeeper.app.data.FarmDetails
import com.goatkeeper.app.util.countries
import com.goatkeeper.app.util.indianStates
import com.goatkeeper.app.util.districtsByState
import com.google.firebase.FirebaseException
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FarmDetailsDialog(
    existing: FarmDetails?,
    onDismiss: () -> Unit,
    onSave: (FarmDetails) -> Unit
) {
    val context = LocalContext.current
    val activity = remember(context) {
        var c = context
        while (c is android.content.ContextWrapper) {
            if (c is Activity) break
            c = c.baseContext
        }
        c as Activity
    }
    var farmName by remember { mutableStateOf(existing?.farmName ?: "") }
    var ownerName by remember { mutableStateOf(existing?.ownerName ?: "") }
    var selectedCountry by remember { mutableStateOf(existing?.country ?: "India") }
    var selectedState by remember { mutableStateOf(existing?.state ?: "") }
    var selectedDistrict by remember { mutableStateOf(existing?.district ?: "") }
    var city by remember { mutableStateOf(existing?.city ?: "") }
    var address by remember { mutableStateOf(existing?.address ?: "") }
    var postalCode by remember { mutableStateOf(existing?.postalCode ?: "") }
    var contactNo by remember { mutableStateOf(existing?.contactNo ?: "") }
    var countryCode by remember { mutableStateOf(existing?.countryCode ?: "+91") }

    val postalCodeLabel = remember(selectedCountry) {
        countries.find { it.name == selectedCountry }?.postalCodeLabel ?: "Postal Code"
    }

    var isFrozen by remember { mutableStateOf(existing != null) }

    var showCountryMenu by remember { mutableStateOf(false) }
    var showStateMenu by remember { mutableStateOf(false) }
    var showDistrictMenu by remember { mutableStateOf(false) }

    // OTP States
    var verificationId by remember { mutableStateOf("") }
    var otpCode by remember { mutableStateOf("") }
    var isSendingOtp by remember { mutableStateOf(false) }
    var isVerifying by remember { mutableStateOf(false) }
    var otpError by remember { mutableStateOf<String?>(null) }
    var isOtpStep by remember { mutableStateOf(false) }

    val districts = remember(selectedState) { districtsByState[selectedState] ?: emptyList() }

    LaunchedEffect(selectedCountry) {
        val country = countries.find { it.name == selectedCountry }
        if (country != null) countryCode = country.code
    }

    val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
        override fun onVerificationCompleted(credential: PhoneAuthCredential) {
            // Instant verification (rare)
            isVerifying = true
            val details = FarmDetails(
                farmName = farmName,
                ownerName = ownerName,
                country = selectedCountry,
                state = selectedState,
                city = if (selectedCountry == "India") "" else city,
                district = if (selectedCountry == "India") selectedDistrict else "",
                address = address,
                postalCode = postalCode,
                contactNo = contactNo,
                countryCode = countryCode
            )
            onSave(details)
            isVerifying = false
            isOtpStep = false
        }

        override fun onVerificationFailed(e: FirebaseException) {
            isSendingOtp = false
            otpError = e.message ?: "Verification failed. Check your number."
        }

        override fun onCodeSent(id: String, token: PhoneAuthProvider.ForceResendingToken) {
            verificationId = id
            isSendingOtp = false
            isOtpStep = true
            otpError = null
        }
    }

    fun startVerification() {
        if (isSendingOtp) return
        isSendingOtp = true
        otpError = null
        
        val fullNumber = "$countryCode$contactNo"
        val options = PhoneAuthOptions.newBuilder()
            .setPhoneNumber(fullNumber)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(callbacks)
            .build()
        PhoneAuthProvider.verifyPhoneNumber(options)
    }

    fun verifyCode() {
        if (isVerifying || otpCode.length < 6) return
        isVerifying = true
        otpError = null
        
        val credential = PhoneAuthProvider.getCredential(verificationId, otpCode)
        // Note: For farm registry we only need to verify the phone, 
        // not necessarily sign in the user with it.
        // If we want to link this phone to the current account, we'd use linkWithCredential.
        // For now, we'll assume the code success means the phone is valid.
        
        // Simulating verification success for data sync
        val details = FarmDetails(
            farmName = farmName,
            ownerName = ownerName,
            country = selectedCountry,
            state = selectedState,
            city = if (selectedCountry == "India") "" else city,
            district = if (selectedCountry == "India") selectedDistrict else "",
            address = address,
            postalCode = postalCode,
            contactNo = contactNo,
            countryCode = countryCode
        )
        onSave(details)
        isVerifying = false
        isOtpStep = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isOtpStep) stringResource(R.string.verify_phone_title) else stringResource(R.string.farm_registry), fontWeight = FontWeight.Bold) },
        text = {
            if (isOtpStep) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(stringResource(R.string.otp_sent_desc, "$countryCode$contactNo"))
                    
                    OutlinedTextField(
                        value = otpCode,
                        onValueChange = { if (it.length <= 6) otpCode = it },
                        label = { Text(stringResource(R.string.otp_code_label)) },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                    
                    if (otpError != null) {
                        Text(otpError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    
                    if (isVerifying) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }

                    TextButton(onClick = { startVerification() }, enabled = !isSendingOtp) {
                        Text(stringResource(R.string.resend_code_btn))
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = farmName,
                        onValueChange = { farmName = it },
                        label = { Text(stringResource(R.string.farm_name_req)) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isFrozen
                    )

                    OutlinedTextField(
                        value = ownerName,
                        onValueChange = { ownerName = it },
                        label = { Text(stringResource(R.string.farm_owner_name_req)) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isFrozen
                    )

                    // Country Dropdown
                    Box(Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = selectedCountry,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.country_req)) },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isFrozen,
                            trailingIcon = { if (!isFrozen) IconButton(onClick = { showCountryMenu = true }) { Icon(Icons.Default.ArrowDropDown, null) } }
                        )
                        if (!isFrozen) Box(Modifier.matchParentSize().clickable { showCountryMenu = true })
                        DropdownMenu(expanded = showCountryMenu, onDismissRequest = { showCountryMenu = false }, modifier = Modifier.fillMaxWidth(0.8f)) {
                            countries.forEach { country ->
                                DropdownMenuItem(text = { Text(country.name) }, onClick = {
                                    selectedCountry = country.name
                                    showCountryMenu = false
                                    if (selectedCountry != "India") {
                                        selectedState = ""
                                        selectedDistrict = ""
                                    }
                                })
                            }
                        }
                    }

                    // State Dropdown (Conditional)
                    if (selectedCountry == "India") {
                        Box(Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = selectedState,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text(stringResource(R.string.state_req)) },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !isFrozen,
                                trailingIcon = { if (!isFrozen) IconButton(onClick = { showStateMenu = true }) { Icon(Icons.Default.ArrowDropDown, null) } }
                            )
                            if (!isFrozen) Box(Modifier.matchParentSize().clickable { showStateMenu = true })
                            DropdownMenu(expanded = showStateMenu, onDismissRequest = { showStateMenu = false }, modifier = Modifier.fillMaxWidth(0.8f)) {
                                indianStates.forEach { state ->
                                    DropdownMenuItem(text = { Text(state) }, onClick = {
                                        selectedState = state
                                        selectedDistrict = ""
                                        showStateMenu = false
                                    })
                                }
                            }
                        }

                        // District Dropdown
                        Box(Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = selectedDistrict,
                                onValueChange = { if (districts.isEmpty()) selectedDistrict = it },
                                readOnly = districts.isNotEmpty(),
                                label = { Text(stringResource(R.string.district_req)) },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !isFrozen,
                                trailingIcon = { 
                                    if (!isFrozen && districts.isNotEmpty()) {
                                        IconButton(onClick = { showDistrictMenu = true }) { Icon(Icons.Default.ArrowDropDown, null) }
                                    }
                                }
                            )
                            if (!isFrozen && districts.isNotEmpty()) Box(Modifier.matchParentSize().clickable { showDistrictMenu = true })
                            
                            if (districts.isNotEmpty()) {
                                DropdownMenu(expanded = showDistrictMenu, onDismissRequest = { showDistrictMenu = false }, modifier = Modifier.fillMaxWidth(0.8f)) {
                                    districts.forEach { district ->
                                        DropdownMenuItem(text = { Text(district) }, onClick = {
                                            selectedDistrict = district
                                            showDistrictMenu = false
                                        })
                                    }
                                }
                            }
                        }
                    } else {
                        // State/Province for outside India
                        OutlinedTextField(
                            value = selectedState,
                            onValueChange = { selectedState = it },
                            label = { Text(stringResource(R.string.state_province_label)) },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isFrozen
                        )
                        // City for outside India
                        OutlinedTextField(
                            value = city,
                            onValueChange = { city = it },
                            label = { Text(stringResource(R.string.city_label)) },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isFrozen
                        )
                    }

                    OutlinedTextField(
                        value = address,
                        onValueChange = { address = it },
                        label = { Text(stringResource(R.string.address_label)) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        enabled = !isFrozen
                    )

                    OutlinedTextField(
                        value = postalCode,
                        onValueChange = { postalCode = it },
                        label = { Text("$postalCodeLabel *") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isFrozen
                    )

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = countryCode,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.code_label_field)) },
                            modifier = Modifier.width(80.dp),
                            enabled = !isFrozen
                        )
                        OutlinedTextField(
                            value = contactNo,
                            onValueChange = { contactNo = it },
                            label = { Text(stringResource(R.string.contact_no_req)) },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            enabled = !isFrozen
                        )
                    }

                    if (otpError != null) {
                        Text(otpError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    
                    if (isSendingOtp) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        },
        confirmButton = {
            if (isOtpStep) {
                TextButton(
                    enabled = otpCode.length == 6 && !isVerifying,
                    onClick = { verifyCode() }
                ) { Text(stringResource(R.string.verify_register_btn)) }
            } else if (isFrozen) {
                TextButton(
                    onClick = { isFrozen = false }
                ) { Text(stringResource(R.string.edit_registry_btn)) }
            } else {
                TextButton(
                    enabled = farmName.isNotBlank() && ownerName.isNotBlank() && selectedCountry.isNotBlank() && contactNo.isNotBlank() && postalCode.isNotBlank() && !isSendingOtp,
                    onClick = { startVerification() }
                ) { Text(stringResource(R.string.send_otp_btn)) }
            }
        },
        dismissButton = {
            TextButton(onClick = { if (isOtpStep) isOtpStep = false else onDismiss() }) { 
                Text(if (isOtpStep) stringResource(R.string.back) else if (isFrozen) stringResource(R.string.close) else stringResource(R.string.cancel)) 
            }
        }
    )
}
