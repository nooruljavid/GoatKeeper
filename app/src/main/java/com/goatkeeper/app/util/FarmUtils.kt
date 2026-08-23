package com.goatkeeper.app.util

import java.time.LocalDate
import java.time.Period
import java.time.temporal.ChronoUnit

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
