package com.securecam.app.util

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.format.DateUtils
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * File/MediaStore helpers for SecureCam.
 *
 * All captures are written through MediaStore so they appear in the system
 * gallery under "Pictures/SecureCam" and "Movies/SecureCam" without any
 * legacy storage hacks. On API 29+ RELATIVE_PATH does the work; on API 26–28
 * we write into the public directories via the classic File API and then
 * index them with MediaScanner.
 */
object FileUtils {

    /** MediaStore relative paths (API 29+). */
    const val PICTURES_RELATIVE_PATH = "Pictures/SecureCam"
    const val MOVIES_RELATIVE_PATH = "Movies/SecureCam"

    /** Legacy absolute directories (API 26–28). */
    val LEGACY_PICTURES_DIR: File =
        File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            "SecureCam",
        )
    val LEGACY_MOVIES_DIR: File =
        File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
            "SecureCam",
        )

    private val fileNameFormat = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US)

    // ── Name generation ─────────────────────────────────────────────────────

    fun newPhotoName(): String = "IMG_${fileNameFormat.format(Date())}.jpg"

    fun newVideoName(): String = "VID_${fileNameFormat.format(Date())}.mp4"

    /** Background recordings are prefixed BG_ per spec (Movies/SecureCam/BG_*.mp4). */
    fun newBackgroundVideoName(): String = "BG_${fileNameFormat.format(Date())}.mp4"

    // ── Photo destination ───────────────────────────────────────────────────

    /**
     * Returns the MediaStore URI a new photo should be written to, creating
     * the pending row first. [ImageCapture.OnImageCapturedCallback] output is
     * streamed into this URI by the caller.
     */
    fun createPhotoUri(context: Context): Uri? {
        val name = newPhotoName()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                put(MediaStore.MediaColumns.RELATIVE_PATH, PICTURES_RELATIVE_PATH)
            }
            context.contentResolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                values,
            )
        } else {
            // API 26–28: write to the public folder, then hand the File to
            // CameraX; indexMediaItem makes it visible to the gallery app.
            val dir = LEGACY_PICTURES_DIR.apply { mkdirs() }
            val file = File(dir, name)
            androidx.core.content.FileProvider.getUriForFile(
                context,
                context.packageName + ".fileprovider",
                file,
            )
        }
    }

    /** The raw legacy file backing a FileProvider URI (API 26–28 only). */
    fun legacyPhotoFile(): File = File(LEGACY_PICTURES_DIR, newPhotoName())

    /**
     * Creates a MediaStore entry for a new video and returns both the target
     * file (for VideoRecordEvent output) and its final content URI.
     *
     * CameraX's Recording API needs a ParcelFileDescriptor, so on API 29+ we
     * open one from the inserted URI; on older devices we record straight to
     * a public Movies/SecureCam file.
     */
    fun createVideoOutputOptions(context: Context): VideoOutput =
        createVideoOutput(context, newVideoName())

    /** Output target for background recordings (BG_ prefix per spec). */
    fun createBackgroundVideoOutput(context: Context): VideoOutput =
        createVideoOutput(context, newBackgroundVideoName())

    private fun createVideoOutput(context: Context, name: String): VideoOutput {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
                put(MediaStore.MediaColumns.RELATIVE_PATH, MOVIES_RELATIVE_PATH)
            }
            val uri = context.contentResolver.insert(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                values,
            ) ?: error("MediaStore rejected new video entry")
            val pfd = context.contentResolver.openFileDescriptor(uri, "rw")
                ?: error("Could not open video output descriptor")
            VideoOutput.MediaStoreOutput(uri, pfd, name)
        } else {
            val dir = LEGACY_MOVIES_DIR.apply { mkdirs() }
            val file = File(dir, name)
            VideoOutput.LegacyFileOutput(file, name)
        }
    }

    /** Sealed target for a recording, normalised across API levels. */
    sealed class VideoOutput {
        abstract val displayName: String

        data class MediaStoreOutput(
            val uri: Uri,
            val pfd: android.os.ParcelFileDescriptor,
            override val displayName: String,
        ) : VideoOutput()

        data class LegacyFileOutput(
            val file: File,
            override val displayName: String,
        ) : VideoOutput()
    }

    /**
     * Indexes a legacy (API 26–28) file with MediaScanner so it shows up in
     * the gallery. No-op on API 29+, where MediaStore already knows the row.
     */
    fun indexLegacyFile(context: Context, file: File) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            android.media.MediaScannerConnection.scanFile(
                context,
                arrayOf(file.absolutePath),
                null,
                null,
            )
        }
    }

    /**
     * Removes a pending MediaStore row if a capture failed mid-write, so the
     * gallery never shows zero-byte ghosts.
     */
    fun deletePendingUri(context: Context, uri: Uri) {
        runCatching { context.contentResolver.delete(uri, null, null) }
    }

    // ── Formatting ──────────────────────────────────────────────────────────

    /** "1:23" style recording/elapsed timer text. */
    fun formatDuration(millis: Long): String {
        val totalSeconds = millis / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format(Locale.US, "%d:%02d", minutes, seconds)
    }

    /** Human-readable timestamp for gallery tiles. */
    fun formatDate(millis: Long): CharSequence =
        DateUtils.getRelativeTimeSpanString(
            millis,
            System.currentTimeMillis(),
            DateUtils.MINUTE_IN_MILLIS,
        )

    /** "2.4 MB" style file size text. */
    fun formatSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return String.format(Locale.US, "%.1f KB", kb)
        val mb = kb / 1024.0
        if (mb < 1024) return String.format(Locale.US, "%.1f MB", mb)
        return String.format(Locale.US, "%.2f GB", mb / 1024.0)
    }
}
