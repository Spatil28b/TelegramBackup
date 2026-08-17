package com.example.telegrambackup.ui.drive

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

enum class FileCategory(val label: String, val icon: ImageVector) {
    ALL("All", Icons.Default.Folder),
    IMAGES("Images", Icons.Default.Image),
    DOCUMENTS("Documents", Icons.AutoMirrored.Filled.InsertDriveFile),
    VIDEOS("Videos", Icons.Default.Videocam),
    AUDIO("Audio", Icons.Default.AudioFile),
    ARCHIVES("Archives", Icons.Default.FolderZip)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriveCategoryChipsRow(
    selectedCategory: FileCategory,
    onCategorySelected: (FileCategory) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
    ) {
        items(FileCategory.values()) { category ->
            FilterChip(
                selected = selectedCategory == category,
                onClick = { onCategorySelected(category) },
                label = { Text(category.label) },
                leadingIcon = {
                    Icon(
                        imageVector = category.icon,
                        contentDescription = category.label,
                        modifier = Modifier.size(18.dp)
                    )
                }
            )
        }
    }
}
