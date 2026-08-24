package com.example.telegrambackup.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.telegrambackup.theme.TelegramBlue

enum class GridViewMode(val columns: Int, val icon: ImageVector, val label: String) {
    COMPACT(4, Icons.Default.GridView, "Compact (4x)"),
    COMFORTABLE(3, Icons.Default.GridOn, "Standard (3x)"),
    LARGE(2, Icons.Default.ViewModule, "Large (2x)"),
    LIST(1, Icons.Default.ViewList, "List")
}

enum class SizeFilter(val label: String, val minBytes: Long, val maxBytes: Long) {
    ANY("All Sizes", 0L, Long.MAX_VALUE),
    UNDER_10MB("< 10 MB", 0L, 10L * 1024 * 1024),
    BETWEEN_10_100MB("10–100 MB", 10L * 1024 * 1024, 100L * 1024 * 1024),
    BETWEEN_100_1GB("100 MB–1 GB", 100L * 1024 * 1024, 1024L * 1024 * 1024),
    OVER_1GB("> 1 GB", 1024L * 1024 * 1024, Long.MAX_VALUE)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartFilterBar(
    selectedCategory: String,
    onSelectCategory: (String) -> Unit,
    selectedSizeFilter: SizeFilter,
    onSelectSizeFilter: (SizeFilter) -> Unit,
    gridViewMode: GridViewMode,
    onChangeGridViewMode: (GridViewMode) -> Unit,
    totalResultsCount: Int = -1,
    modifier: Modifier = Modifier
) {
    var showFilterDropdown by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // View Mode Cycler
            Surface(
                onClick = {
                    val nextMode = when (gridViewMode) {
                        GridViewMode.COMFORTABLE -> GridViewMode.LARGE
                        GridViewMode.LARGE -> GridViewMode.COMPACT
                        GridViewMode.COMPACT -> GridViewMode.LIST
                        GridViewMode.LIST -> GridViewMode.COMFORTABLE
                    }
                    onChangeGridViewMode(nextMode)
                },
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                tonalElevation = 1.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = gridViewMode.icon,
                        contentDescription = "Change Layout",
                        modifier = Modifier.size(16.dp),
                        tint = TelegramBlue
                    )
                    Text(
                        text = "${gridViewMode.columns}x",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = TelegramBlue
                    )
                }
            }

            // Size Filter FilterChip
            Box {
                FilterChip(
                    selected = selectedSizeFilter != SizeFilter.ANY,
                    onClick = { showFilterDropdown = true },
                    label = {
                        Text(
                            text = if (selectedSizeFilter == SizeFilter.ANY) "Size" else selectedSizeFilter.label,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium)
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.FilterList,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                    },
                    trailingIcon = {
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = TelegramBlue.copy(alpha = 0.15f),
                        selectedLabelColor = TelegramBlue,
                        selectedLeadingIconColor = TelegramBlue
                    )
                )

                DropdownMenu(
                    expanded = showFilterDropdown,
                    onDismissRequest = { showFilterDropdown = false }
                ) {
                    SizeFilter.values().forEach { filter ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = filter.label,
                                    fontWeight = if (filter == selectedSizeFilter) FontWeight.Bold else FontWeight.Normal,
                                    color = if (filter == selectedSizeFilter) TelegramBlue else MaterialTheme.colorScheme.onSurface
                                )
                            },
                            onClick = {
                                onSelectSizeFilter(filter)
                                showFilterDropdown = false
                            },
                            leadingIcon = if (filter == selectedSizeFilter) {
                                { Icon(Icons.Default.Check, contentDescription = null, tint = TelegramBlue, modifier = Modifier.size(16.dp)) }
                            } else null
                        )
                    }
                }
            }

            // Category Chips
            val categories = listOf(
                "ALL" to "All",
                "PHOTOS" to "Photos 📸",
                "VIDEOS" to "Videos 🎥",
                "AUDIO" to "Audio 🎵",
                "DOCUMENTS" to "Docs 📄",
                "ARCHIVES" to "Zips 📦"
            )

            categories.forEach { (key, label) ->
                val isSelected = selectedCategory.equals(key, ignoreCase = true)
                FilterChip(
                    selected = isSelected,
                    onClick = { onSelectCategory(key) },
                    label = {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        )
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = TelegramBlue,
                        selectedLabelColor = Color.White
                    )
                )
            }
        }
    }
}
