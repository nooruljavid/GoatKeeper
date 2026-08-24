package com.goatkeeper.app.util

import java.time.LocalDate
import java.time.Period
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

private val displayFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

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

fun isKid(dob: String): Boolean = try {
    ChronoUnit.MONTHS.between(LocalDate.parse(dob), LocalDate.now()) < 6
} catch (_: Exception) {
    false
}
