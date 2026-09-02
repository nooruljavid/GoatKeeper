package com.goatkeeper.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.goatkeeper.app.data.FarmRecord
import com.goatkeeper.app.util.formatDate

import androidx.compose.ui.res.stringResource
import com.goatkeeper.app.R

@Composable
fun RecordItem(
    record: FarmRecord,
    goatName: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (icon, color) = getRecordStyle(record.type)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(color.copy(alpha = 0.2f), RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = color)
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = goatName,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = formatDate(record.date),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                
                val typeLabel = when(record.type) {
                    "Health" -> stringResource(R.string.health)
                    "Breeding" -> stringResource(R.string.breeding)
                    "Insurance" -> stringResource(R.string.safety)
                    "Sale", "Goat Sale", "Manure Sale", "Milk Sale" -> stringResource(R.string.sale)
                    "Transfer" -> stringResource(R.string.transfer)
                    else -> record.type
                }

                Text(
                    text = "$typeLabel: ${record.title}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = color
                )

                if (record.dueDate.isNotBlank()) {
                    Row(
                        modifier = Modifier.padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Event,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.due_label) + ": ${formatDate(record.dueDate)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
            
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline
            )
        }
    }
}

private fun getRecordStyle(type: String): Pair<ImageVector, Color> {
    return when (type) {
        "Health" -> Icons.Default.MedicalServices to Color(0xFF6366F1)
        "Breeding" -> Icons.Default.ChildCare to Color(0xFFF59E0B)
        "Insurance" -> Icons.Default.Security to Color(0xFF6366F1)
        "Sale", "Goat Sale", "Manure Sale", "Milk Sale" -> Icons.Default.AttachMoney to Color(0xFF10B981)
        "Transfer" -> Icons.Default.LocalShipping to Color(0xFF6B7280)
        else -> Icons.Default.Assignment to Color(0xFF10B981)
    }
}
