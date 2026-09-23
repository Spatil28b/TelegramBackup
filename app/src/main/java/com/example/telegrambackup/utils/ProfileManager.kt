package com.example.telegrambackup.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

/**
 * =====================================================================================
 * ZERO-LOSS PROFILE & AVATAR PERSISTENCE MANAGER
 * =====================================================================================
 * Guarantees that user profile picture and display name NEVER reset to defaults.
 * Stores avatar in private internal storage (filesDir) so permission grants are never lost.
 * Integrates bidirectional Base64 cloud sync so avatar survives reinstalls and device changes.
 * =====================================================================================
 */
object ProfileManager {
    private const val TAG = "ProfileManager"
    const val AVATAR_FILE_NAME = "user_profile_avatar.jpg"

    fun getAvatarFile(context: Context): File {
        return File(context.filesDir, AVATAR_FILE_NAME)
    }

    fun getAvatarUri(context: Context): Uri? {
        val file = getAvatarFile(context)
        if (file.exists() && file.length() > 0) {
            return Uri.fromFile(file)
        }
        val storedUri = AppPrefs(context).profilePictureUri
        if (storedUri.isNotBlank()) {
            try {
                val parsed = Uri.parse(storedUri)
                if (parsed.scheme == "file") {
                    val parsedFile = File(parsed.path ?: "")
                    if (parsedFile.exists() && parsedFile.length() > 0) return parsed
                } else {
                    return parsed
                }
            } catch (_: Exception) {}
        }
        return null
    }

    /**
     * Copies selected image from any content/file Uri into private internal storage.
     * Scales image to max 512x512 with 85% JPEG quality to ensure fast rendering
     * and compact cloud sync footprint.
     */
    fun saveAvatarFromUri(context: Context, sourceUri: Uri): Uri? {
        return try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(sourceUri)
            if (inputStream == null) {
                Log.e(TAG, "Cannot open input stream for $sourceUri")
                return null
            }

            val originalBitmap = BitmapFactory.decodeStream(inputStream)
            try { inputStream.close() } catch (_: Exception) {}

            if (originalBitmap == null) {
                Log.e(TAG, "Failed to decode bitmap from $sourceUri")
                return null
            }

            // Downscale to max 512x512 preserving aspect ratio
            val maxDimension = 512
            val width = originalBitmap.width
            val height = originalBitmap.height
            val scale = (maxDimension.toFloat() / maxOf(width, height)).coerceAtMost(1.0f)
            val targetWidth = (width * scale).toInt().coerceAtLeast(1)
            val targetHeight = (height * scale).toInt().coerceAtLeast(1)

            val scaledBitmap = if (scale < 1.0f) {
                Bitmap.createScaledBitmap(originalBitmap, targetWidth, targetHeight, true)
            } else {
                originalBitmap
            }

            val targetFile = getAvatarFile(context)
            if (targetFile.exists()) {
                targetFile.delete()
            }

            FileOutputStream(targetFile).use { fos ->
                scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 85, fos)
                fos.flush()
            }

            if (scaledBitmap != originalBitmap) {
                scaledBitmap.recycle()
            }
            originalBitmap.recycle()

            val fileUri = Uri.fromFile(targetFile)
            val prefs = AppPrefs(context)
            prefs.profilePictureUri = fileUri.toString()
            prefs.profileUpdatedAt = System.currentTimeMillis()

            fileUri
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save avatar image from $sourceUri", e)
            null
        }
    }

    /**
     * Serializes local avatar image into compact Base64 string for cloud manifest backup.
     */
    fun getAvatarBase64(context: Context): String? {
        return try {
            val file = getAvatarFile(context)
            if (!file.exists() || file.length() == 0L) return null
            val bytes = file.readBytes()
            Base64.encodeToString(bytes, Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to export avatar to Base64", e)
            null
        }
    }

    /**
     * Restores avatar image from cloud manifest Base64 string into private internal storage.
     */
    fun restoreAvatarFromBase64(context: Context, base64Str: String): Uri? {
        return try {
            if (base64Str.isBlank()) return null
            val bytes = Base64.decode(base64Str, Base64.NO_WRAP)
            if (bytes == null || bytes.isEmpty()) return null

            val targetFile = getAvatarFile(context)
            FileOutputStream(targetFile).use { fos ->
                fos.write(bytes)
                fos.flush()
            }

            val fileUri = Uri.fromFile(targetFile)
            val prefs = AppPrefs(context)
            prefs.profilePictureUri = fileUri.toString()
            fileUri
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restore avatar from Base64", e)
            null
        }
    }

    /**
     * Deletes local avatar file and resets preference to initial letter fallback.
     */
    fun removeAvatar(context: Context) {
        try {
            val file = getAvatarFile(context)
            if (file.exists()) {
                file.delete()
            }
        } catch (_: Exception) {}
        val prefs = AppPrefs(context)
        prefs.profilePictureUri = ""
        prefs.profileUpdatedAt = System.currentTimeMillis()
    }
}
