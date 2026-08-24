package com.example.telegrambackup.utils

import android.content.Context
import android.os.Build
import android.os.Environment
import android.util.Log
import com.example.telegrambackup.data.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class LocalDeviceFile(
    val file: File,
    val path: String = file.absolutePath,
    val name: String = file.name,
    val size: Long = file.length(),
    val lastModified: Long = file.lastModified(),
    val categoryName: String,
    var isSelected: Boolean = false
)

data class FolderCategory(
    val title: String,
    val iconName: String,
    val count: Int,
    val files: List<LocalDeviceFile>
)

class DeviceFileManager(private val context: Context) {

    suspend fun scanLocalFiles(): List<FolderCategory> = withContext(Dispatchers.IO) {
        try {
            // Check storage permission on Android 11+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
                return@withContext defaultCategories()
            }

            val (uploadedPaths, uploadedNameSizes) = try {
                val database = AppDatabase.getDatabase(context)
                val files = database.uploadedFileDao().getAllFiles()
                val paths = HashSet<String>()
                val nameSizes = HashSet<String>()
                for (f in files) {
                    if (f.path.isNotEmpty()) {
                        paths.add(f.path)
                        paths.add(f.path.lowercase())
                    }
                    if (f.fileName.isNotEmpty()) {
                        nameSizes.add("${f.fileName.lowercase()}_${f.size}")
                    }
                }
                Pair(paths, nameSizes)
            } catch (e: Exception) {
                Pair(emptySet<String>(), emptySet<String>())
            }

            val rootStorage = Environment.getExternalStorageDirectory()
            val dirsToScan = mutableListOf<File>()
            if (rootStorage != null && rootStorage.exists()) {
                dirsToScan.add(rootStorage)
            } else {
                dirsToScan.add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM))
                dirsToScan.add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES))
                dirsToScan.add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS))
                dirsToScan.add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS))
            }

            val picturesList = mutableListOf<LocalDeviceFile>()
            val videosList = mutableListOf<LocalDeviceFile>()
            val docsList = mutableListOf<LocalDeviceFile>()
            val archivesApksList = mutableListOf<LocalDeviceFile>()
            val othersList = mutableListOf<LocalDeviceFile>()

            var itemCounter = 0
            val MAX_FILES_PER_CATEGORY = 600

            for (dir in dirsToScan) {
                if (dir.exists() && dir.isDirectory) {
                    try {
                        dir.walkTopDown()
                            .onEnter { subDir ->
                                try {
                                    val name = subDir.name.lowercase()
                                    !name.startsWith(".") && name != "android" && name != "cache" && name != "tmp" && name != "thumbnails" && name != ".thumbnails" && name != "node_modules"
                                } catch (e: Exception) { false }
                            }
                            .filter { f -> 
                                try {
                                    if (!f.isFile || f.name.startsWith(".")) return@filter false
                                    val pathExact = f.absolutePath
                                    val pathLower = f.absolutePath.lowercase()
                                    val nameSize = "${f.name.lowercase()}_${f.length()}"
                                    !uploadedPaths.contains(pathExact) &&
                                    !uploadedPaths.contains(pathLower) &&
                                    !uploadedNameSizes.contains(nameSize)
                                } catch (e: Exception) { false }
                            }
                            .forEach { file ->
                                try {
                                    if (file.length() > 0) {
                                        val ext = file.extension.lowercase()
                                        when {
                                            listOf("jpg", "jpeg", "png", "gif", "webp", "heic", "bmp", "raw", "svg").contains(ext) -> {
                                                if (picturesList.size < MAX_FILES_PER_CATEGORY) picturesList.add(LocalDeviceFile(file, categoryName = "Pictures & Camera"))
                                            }
                                            listOf("mp4", "mkv", "avi", "mov", "webm", "flv", "3gp", "wmv").contains(ext) -> {
                                                if (videosList.size < MAX_FILES_PER_CATEGORY) videosList.add(LocalDeviceFile(file, categoryName = "Videos"))
                                            }
                                            listOf("pdf", "doc", "docx", "txt", "ppt", "pptx", "xls", "xlsx", "csv", "epub").contains(ext) -> {
                                                if (docsList.size < MAX_FILES_PER_CATEGORY) docsList.add(LocalDeviceFile(file, categoryName = "Documents"))
                                            }
                                            listOf("zip", "rar", "7z", "tar", "gz", "apk", "xapk").contains(ext) -> {
                                                if (archivesApksList.size < MAX_FILES_PER_CATEGORY) archivesApksList.add(LocalDeviceFile(file, categoryName = "Archives & APKs"))
                                            }
                                            else -> {
                                                if (othersList.size < MAX_FILES_PER_CATEGORY) othersList.add(LocalDeviceFile(file, categoryName = "Other Files"))
                                            }
                                        }
                                        itemCounter++
                                        if (itemCounter % 100 == 0) {
                                            kotlinx.coroutines.yield()
                                        }
                                    }
                                } catch (e: Exception) { }
                            }
                    } catch (e: Exception) {
                        Log.e("DeviceFileManager", "Error scanning directory ${dir.absolutePath}", e)
                    }
                }
            }

            listOf(
                FolderCategory("🖼️ Pictures & Camera", "photo", picturesList.size, picturesList.sortedByDescending { it.lastModified }),
                FolderCategory("🎥 Videos", "video", videosList.size, videosList.sortedByDescending { it.lastModified }),
                FolderCategory("📄 Documents & PDFs", "doc", docsList.size, docsList.sortedByDescending { it.lastModified }),
                FolderCategory("📦 Archives & APKs", "zip", archivesApksList.size, archivesApksList.sortedByDescending { it.lastModified }),
                FolderCategory("📁 Other Files", "folder", othersList.size, othersList.sortedByDescending { it.lastModified })
            )
        } catch (e: Exception) {
            Log.e("DeviceFileManager", "Unhandled exception in scanLocalFiles", e)
            defaultCategories()
        }
    }

    private fun defaultCategories(): List<FolderCategory> {
        return listOf(
            FolderCategory("🖼️ Pictures & Camera", "photo", 0, emptyList()),
            FolderCategory("🎥 Videos", "video", 0, emptyList()),
            FolderCategory("📄 Documents & PDFs", "doc", 0, emptyList()),
            FolderCategory("📦 Archives & APKs", "zip", 0, emptyList()),
            FolderCategory("📁 Other Files", "folder", 0, emptyList())
        )
    }
}
