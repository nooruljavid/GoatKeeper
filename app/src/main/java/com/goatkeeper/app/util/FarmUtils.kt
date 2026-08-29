package com.goatkeeper.app.util

import com.goatkeeper.app.data.FarmRecord
import com.goatkeeper.app.data.Goat
import java.time.LocalDate
import java.time.Period
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

private val displayFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

val today: String get() = LocalDate.now().toString()
val nextWeek: String get() = LocalDate.now().plusDays(7).toString()

fun formatDate(isoDate: String?): String {
    if (isoDate.isNullOrBlank()) return ""
    return try {
        LocalDate.parse(isoDate).format(displayFormatter)
    } catch (_: Exception) {
        isoDate
    }
}

fun age(dob: String): String = try {
    Period.between(LocalDate.parse(dob), LocalDate.now()).years.let { "$it yr" }
} catch (_: Exception) {
    "—"
}

fun kiddingDate(date: String): String = try {
    LocalDate.parse(date).plusDays(150).toString()
} catch (_: Exception) {
    ""
}

fun nextDewormingDate(date: String): String = try {
    LocalDate.parse(date).plusMonths(6).toString()
} catch (_: Exception) {
    ""
}

fun isKid(dob: String): Boolean = try {
    ChronoUnit.MONTHS.between(LocalDate.parse(dob), LocalDate.now()) < 6
} catch (_: Exception) {
    false
}

fun getStatusColor(percentage: Int): androidx.compose.ui.graphics.Color {
    return when {
        percentage >= 90 -> androidx.compose.ui.graphics.Color(0xFF10B981) // Green
        percentage >= 70 -> androidx.compose.ui.graphics.Color(0xFFF59E0B) // Orange
        else -> androidx.compose.ui.graphics.Color(0xFFEF4444) // Red
    }
}

fun calculateHealthStatus(goat: Goat, records: List<FarmRecord>, today: String): Int {
    if (isKid(goat.dateOfBirth)) return 100

    var percentage = 60
    if (hasActiveDeworming(records, today)) percentage += 20
    if (hasActiveVaccination(records, today)) percentage += 20
    
    return percentage
}

fun hasActiveDeworming(records: List<FarmRecord>, today: String): Boolean {
    return records.any { 
        it.type == "Health" && it.title.contains("Deworming", ignoreCase = true) && 
        it.dueDate.isNotBlank() && it.dueDate >= today 
    }
}

fun hasActiveVaccination(records: List<FarmRecord>, today: String): Boolean {
    return records.any { 
        it.type == "Health" && it.title.contains("Vaccination", ignoreCase = true) && 
        it.dueDate.isNotBlank() && it.dueDate >= today 
    }
}

fun hasExceededDeworming(records: List<FarmRecord>, today: String): Boolean {
    val dewormingRecords = records.filter { it.type == "Health" && it.title.contains("Deworming", ignoreCase = true) }
    if (dewormingRecords.isEmpty()) return false
    val latest = dewormingRecords.maxByOrNull { it.date } ?: return false
    return latest.dueDate.isNotBlank() && latest.dueDate < today
}

fun hasExceededVaccination(records: List<FarmRecord>, today: String): Boolean {
    val vaccinationRecords = records.filter { it.type == "Health" && it.title.contains("Vaccination", ignoreCase = true) }
    if (vaccinationRecords.isEmpty()) return false
    val latest = vaccinationRecords.maxByOrNull { it.date } ?: return false
    return latest.dueDate.isNotBlank() && latest.dueDate < today
}

fun hasNoDeworming(records: List<FarmRecord>): Boolean {
    return records.none { it.type == "Health" && it.title.contains("Deworming", ignoreCase = true) }
}

fun hasNoVaccination(records: List<FarmRecord>): Boolean {
    return records.none { it.type == "Health" && it.title.contains("Vaccination", ignoreCase = true) }
}

data class HerdHealthStats(
    val totalGoats: Int,
    val averageHealth: Int,
    val vaccinatedCount: Int,
    val dewormedCount: Int,
    val vaccinationPercentage: Int,
    val dewormingPercentage: Int,
    val exceededDewormingIds: List<String>,
    val exceededVaccinationIds: List<String>,
    val missingDewormingIds: List<String>,
    val missingVaccinationIds: List<String>
)

fun calculateHerdHealth(goats: List<Goat>, allRecords: List<FarmRecord>, today: String): HerdHealthStats {
    val activeGoats = goats.filter { it.status == "Active" }
    val adultGoats = activeGoats.filter { !isKid(it.dateOfBirth) }
    
    if (activeGoats.isEmpty()) return HerdHealthStats(0, 100, 0, 0, 100, 100, emptyList(), emptyList(), emptyList(), emptyList())

    val herdRecords = allRecords.filter { it.goatId == null }
    val hasHerdDeworming = hasActiveDeworming(herdRecords, today)
    val hasHerdVaccination = hasActiveVaccination(herdRecords, today)

    // Overall health percentage based on adult goats only
    val adultHealths = adultGoats.map { goat ->
        val goatRecords = allRecords.filter { it.goatId == goat.id }
        var percentage = 60
        if (hasHerdDeworming || hasActiveDeworming(goatRecords, today)) percentage += 20
        if (hasHerdVaccination || hasActiveVaccination(goatRecords, today)) percentage += 20
        percentage
    }
    val averageHealth = if (adultHealths.isEmpty()) 100 else adultHealths.average().toInt()

    val dewormed = activeGoats.count { goat -> 
        isKid(goat.dateOfBirth) || hasHerdDeworming || hasActiveDeworming(allRecords.filter { it.goatId == goat.id }, today)
    }
    val vaccinated = activeGoats.count { goat ->
        isKid(goat.dateOfBirth) || hasHerdVaccination || hasActiveVaccination(allRecords.filter { it.goatId == goat.id }, today)
    }

    val exceededDeworming = adultGoats.filter { goat ->
        !hasHerdDeworming && hasExceededDeworming(allRecords.filter { it.goatId == goat.id }, today)
    }.map { it.id }

    val exceededVaccination = adultGoats.filter { goat ->
        !hasHerdVaccination && hasExceededVaccination(allRecords.filter { it.goatId == goat.id }, today)
    }.map { it.id }

    val missingDeworming = adultGoats.filter { goat ->
        !hasHerdDeworming && hasNoDeworming(allRecords.filter { it.goatId == goat.id })
    }.map { it.id }

    val missingVaccination = adultGoats.filter { goat ->
        !hasHerdVaccination && hasNoVaccination(allRecords.filter { it.goatId == goat.id })
    }.map { it.id }

    return HerdHealthStats(
        totalGoats = activeGoats.size,
        averageHealth = averageHealth,
        vaccinatedCount = vaccinated,
        dewormedCount = dewormed,
        vaccinationPercentage = (vaccinated.toDouble() / activeGoats.size * 100).toInt(),
        dewormingPercentage = (dewormed.toDouble() / activeGoats.size * 100).toInt(),
        exceededDewormingIds = exceededDeworming,
        exceededVaccinationIds = exceededVaccination,
        missingDewormingIds = missingDeworming,
        missingVaccinationIds = missingVaccination
    )
}

fun calculateInsuranceStatus(records: List<FarmRecord>, today: String): String {
    val insuranceRecords = records.filter { it.type == "Insurance" }
    if (insuranceRecords.isEmpty()) return "Not Insured"
    
    val latest = insuranceRecords.maxByOrNull { it.date } ?: return "Not Insured"
    return if (latest.dueDate.isNotBlank() && latest.dueDate >= today) "Active" else "Expired"
}

fun hasActiveInsurance(records: List<FarmRecord>, today: String): Boolean {
    return records.any { 
        it.type == "Insurance" && it.dueDate.isNotBlank() && it.dueDate >= today 
    }
}

fun hasExpiredInsurance(records: List<FarmRecord>, today: String): Boolean {
    val insuranceRecords = records.filter { it.type == "Insurance" }
    if (insuranceRecords.isEmpty()) return false
    val latest = insuranceRecords.maxByOrNull { it.date } ?: return false
    return latest.dueDate.isNotBlank() && latest.dueDate < today
}

fun hasNoInsurance(records: List<FarmRecord>): Boolean {
    return records.none { it.type == "Insurance" }
}

data class HerdSafetyStats(
    val totalGoats: Int,
    val averageSafety: Int,
    val insuredCount: Int,
    val insurancePercentage: Int,
    val expiredInsuranceIds: List<String>,
    val missingInsuranceIds: List<String>
)

fun calculateHerdSafety(goats: List<Goat>, allRecords: List<FarmRecord>, today: String): HerdSafetyStats {
    val activeGoats = goats.filter { it.status == "Active" }
    val adultGoats = activeGoats.filter { !isKid(it.dateOfBirth) }
    
    if (activeGoats.isEmpty()) return HerdSafetyStats(0, 100, 0, 100, emptyList(), emptyList())

    val herdRecords = allRecords.filter { it.goatId == null }
    val hasHerdInsurance = hasActiveInsurance(herdRecords, today)

    val insured = adultGoats.count { goat -> 
        hasHerdInsurance || hasActiveInsurance(allRecords.filter { it.goatId == goat.id }, today)
    }
    
    val averageSafety = if (adultGoats.isEmpty()) 100 else (insured.toDouble() / adultGoats.size * 100).toInt()

    val expired = adultGoats.filter { goat ->
        !hasHerdInsurance && hasExpiredInsurance(allRecords.filter { it.goatId == goat.id }, today)
    }.map { it.id }

    val missing = adultGoats.filter { goat ->
        !hasHerdInsurance && hasNoInsurance(allRecords.filter { it.goatId == goat.id })
    }.map { it.id }

    return HerdSafetyStats(
        totalGoats = activeGoats.size, // Still show total active goats in stats
        averageSafety = averageSafety,
        insuredCount = insured,
        insurancePercentage = averageSafety, // Percentage of adults insured
        expiredInsuranceIds = expired,
        missingInsuranceIds = missing
    )
}
