package com.goatkeeper.app.ui.dashboards

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.goatkeeper.app.data.FarmRecord
import com.goatkeeper.app.data.Goat
import com.goatkeeper.app.ui.components.RecordItem
import com.goatkeeper.app.ui.components.StatCard
import com.goatkeeper.app.ui.records.GoatCard
import com.goatkeeper.app.util.*

import androidx.compose.ui.res.stringResource
import com.goatkeeper.app.R

@Composable
fun Dashboard(goats: List<Goat>, records: List<FarmRecord>, currencySymbol: String = "₹", onOpen: (String) -> Unit) {
    val activeCount = goats.count { it.status == "Active" }
    val femaleCount = goats.count { it.status == "Active" && it.gender == "Female" && !isKid(it.dateOfBirth) }
    val maleCount = goats.count { it.status == "Active" && it.gender == "Male" && !isKid(it.dateOfBirth) }
    val kidsCount = goats.count { it.status == "Active" && isKid(it.dateOfBirth) }
    val alerts = records.filter { it.dueDate.isNotBlank() && it.dueDate >= today && it.dueDate <= nextWeek }

    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Text(stringResource(R.string.farm_glance), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatCard(stringResource(R.string.total_goats), activeCount.toString(), "🐐", modifier = Modifier.weight(1f))
                
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("♂ " + stringResource(R.string.male), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            Text(maleCount.toString(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                        }
                        Spacer(Modifier.height(8.dp))
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("♀ " + stringResource(R.string.female), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            Text(femaleCount.toString(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                        }
                    }
                }

                StatCard(stringResource(R.string.babies), kidsCount.toString(), "🍼", modifier = Modifier.weight(1f))
            }
        }
        item { Text(stringResource(R.string.upcoming_alerts), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
        if (alerts.isEmpty()) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFD1FAE5).copy(alpha = 0.5f))) {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.no_upcoming_alerts), color = Color(0xFF065F46))
                    }
                }
            }
        } else {
            items(alerts, key = { it.recordId }) { record ->
                RecordItem(
                    record = record,
                    goatName = record.goatId?.let { gid -> 
                        goats.find { it.id == gid }?.name?.ifBlank { gid } ?: gid
                    } ?: stringResource(R.string.entire_herd),
                    onClick = { record.goatId?.let { onOpen(it) } }
                )
            }
        }
        item { Text(stringResource(R.string.recent_goats), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
        val recentGoats = goats.sortedByDescending { it.lastViewed }.take(3)
        items(recentGoats, key = { it.id }) { goat -> GoatCard(goat, onOpen) }
    }
}

@Composable
fun SafetyDashboard(
    goats: List<Goat>,
    records: List<FarmRecord>,
    filterType: String,
    onFilterChange: (String) -> Unit,
    onOverallClick: () -> Unit,
    onOpenGoat: (String, String) -> Unit
) {
    val stats = calculateHerdSafety(goats, records, today)
    val color = getStatusColor(stats.averageSafety)

    Column(Modifier.fillMaxSize()) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp).clickable { onOverallClick() },
            colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f))
        ) {
            Row(
                Modifier.padding(20.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(stringResource(R.string.overall_safety), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("${stats.averageSafety}%", style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Black, color = color)
                }
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(80.dp)) {
                    CircularProgressIndicator(
                        progress = { stats.averageSafety / 100f },
                        modifier = Modifier.fillMaxSize(),
                        strokeWidth = 8.dp,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        color = color,
                        strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                    )
                }
            }
        }

        Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SafetyStatCard(
                title = stringResource(R.string.insurance),
                count = stats.insuredCount,
                total = stats.totalGoats,
                percentage = stats.insurancePercentage,
                exceededCount = stats.expiredInsuranceIds.size + stats.missingInsuranceIds.size,
                onClick = { onFilterChange(if (filterType == "Exceeded Insurance") "All" else "Exceeded Insurance") },
                selected = filterType == "Exceeded Insurance",
                modifier = Modifier.fillMaxWidth()
            )
        }

        val listTitle = when (filterType) {
            "Exceeded Insurance" -> stringResource(R.string.safety_alerts_count, stats.expiredInsuranceIds.size + stats.missingInsuranceIds.size)
            else -> stringResource(R.string.safety_summary)
        }

        Text(listTitle, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))

        val combinedIds = when (filterType) {
            "Exceeded Insurance" -> (stats.expiredInsuranceIds + stats.missingInsuranceIds).distinct()
            else -> emptyList()
        }

        if (filterType != "All" && combinedIds.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFD1FAE5).copy(alpha = 0.5f))
            ) {
                Box(Modifier.padding(24.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.all_protected), color = Color(0xFF065F46))
                }
            }
        } else if (filterType == "All") {
             Card(modifier = Modifier.fillMaxWidth()) {
                 Column(Modifier.padding(16.dp)) {
                     Text(stringResource(R.string.herd_insured_desc, stats.averageSafety), style = MaterialTheme.typography.bodyMedium)
                     Spacer(Modifier.height(8.dp))
                     Text(stringResource(R.string.expired_insurance_desc, stats.expiredInsuranceIds.size), style = MaterialTheme.typography.bodySmall)
                     Text(stringResource(R.string.missing_insurance_desc, stats.missingInsuranceIds.size), style = MaterialTheme.typography.bodySmall)
                 }
             }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(combinedIds) { id ->
                    val goat = goats.find { it.id == id }
                    val isMissing = stats.missingInsuranceIds.contains(id)
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { onOpenGoat(id, "Safety") },
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(goat?.name?.ifBlank { id } ?: id, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Icon(Icons.Default.Security, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(if (isMissing) stringResource(R.string.no_record_found) else stringResource(R.string.policy_expired), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SafetyStatCard(
    title: String,
    count: Int,
    total: Int,
    percentage: Int,
    exceededCount: Int,
    onClick: () -> Unit,
    selected: Boolean,
    modifier: Modifier = Modifier
) {
    val color = getStatusColor(percentage)
    Card(
        modifier = modifier.clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (selected) color.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surface
        ),
        border = if (selected) androidx.compose.foundation.BorderStroke(2.dp, color) else null
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
            Text("$count / $total", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("$percentage%", style = MaterialTheme.typography.titleSmall, color = color)
            if (exceededCount > 0) {
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Error, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.needs_attention_count, exceededCount), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
fun HealthDashboard(
    goats: List<Goat>,
    records: List<FarmRecord>,
    filterType: String,
    onFilterChange: (String) -> Unit,
    onOverallClick: () -> Unit,
    onOpenGoat: (String, String) -> Unit
) {
    val stats = calculateHerdHealth(goats, records, today)
    val color = getStatusColor(stats.averageHealth)

    Column(Modifier.fillMaxSize()) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp).clickable { onOverallClick() },
            colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f))
        ) {
            Row(
                Modifier.padding(20.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(stringResource(R.string.overall_health), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("${stats.averageHealth}%", style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Black, color = color)
                }
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(80.dp)) {
                    CircularProgressIndicator(
                        progress = { stats.averageHealth / 100f },
                        modifier = Modifier.fillMaxSize(),
                        strokeWidth = 8.dp,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        color = color,
                        strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                    )
                }
            }
        }

        Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            HealthStatCard(
                title = stringResource(R.string.deworming),
                count = stats.dewormedCount,
                total = stats.totalGoats,
                percentage = stats.dewormingPercentage,
                exceededCount = stats.exceededDewormingIds.size + stats.missingDewormingIds.size,
                onClick = { onFilterChange(if (filterType == "Exceeded Deworming") "All" else "Exceeded Deworming") },
                selected = filterType == "Exceeded Deworming",
                modifier = Modifier.weight(1f)
            )
            HealthStatCard(
                title = stringResource(R.string.vaccination),
                count = stats.vaccinatedCount,
                total = stats.totalGoats,
                percentage = stats.vaccinationPercentage,
                exceededCount = stats.exceededVaccinationIds.size + stats.missingVaccinationIds.size,
                onClick = { onFilterChange(if (filterType == "Exceeded Vaccination") "All" else "Exceeded Vaccination") },
                selected = filterType == "Exceeded Vaccination",
                modifier = Modifier.weight(1f)
            )
        }

        val listTitle = when (filterType) {
            "Exceeded Deworming" -> stringResource(R.string.due_deworming_count, stats.exceededDewormingIds.size + stats.missingDewormingIds.size)
            "Exceeded Vaccination" -> stringResource(R.string.due_vaccination_count, stats.exceededVaccinationIds.size + stats.missingVaccinationIds.size)
            else -> stringResource(R.string.health_summary)
        }

        Text(listTitle, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))

        val combinedIds = when (filterType) {
            "Exceeded Deworming" -> (stats.exceededDewormingIds + stats.missingDewormingIds).distinct()
            "Exceeded Vaccination" -> (stats.exceededVaccinationIds + stats.missingVaccinationIds).distinct()
            else -> emptyList()
        }

        if (filterType != "All" && combinedIds.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFD1FAE5).copy(alpha = 0.5f))
            ) {
                Box(Modifier.padding(24.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.all_healthy), color = Color(0xFF065F46))
                }
            }
        } else if (filterType == "All") {
             Card(modifier = Modifier.fillMaxWidth()) {
                 Column(Modifier.padding(16.dp)) {
                     Text(stringResource(R.string.herd_health_desc, stats.averageHealth), style = MaterialTheme.typography.bodyMedium)
                     Spacer(Modifier.height(8.dp))
                     Text(stringResource(R.string.need_deworming_desc, stats.exceededDewormingIds.size + stats.missingDewormingIds.size), style = MaterialTheme.typography.bodySmall)
                     Text(stringResource(R.string.need_vaccination_desc, stats.exceededVaccinationIds.size + stats.missingVaccinationIds.size), style = MaterialTheme.typography.bodySmall)
                 }
             }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(combinedIds) { id ->
                    val goat = goats.find { it.id == id }
                    val isMissing = when (filterType) {
                        "Exceeded Deworming" -> stats.missingDewormingIds.contains(id)
                        "Exceeded Vaccination" -> stats.missingVaccinationIds.contains(id)
                        else -> false
                    }
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { onOpenGoat(id, "Health") },
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(goat?.name?.ifBlank { id } ?: id, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(if (isMissing) stringResource(R.string.no_record_found) else stringResource(R.string.due_exceeded), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HealthStatCard(
    title: String,
    count: Int,
    total: Int,
    percentage: Int,
    exceededCount: Int,
    onClick: () -> Unit,
    selected: Boolean,
    modifier: Modifier = Modifier
) {
    val color = getStatusColor(percentage)
    Card(
        modifier = modifier.clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (selected) color.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surface
        ),
        border = if (selected) androidx.compose.foundation.BorderStroke(2.dp, color) else null
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
            Text("$count / $total", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("$percentage%", style = MaterialTheme.typography.titleSmall, color = color)
            if (exceededCount > 0) {
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Error, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.due_count_label, exceededCount), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
