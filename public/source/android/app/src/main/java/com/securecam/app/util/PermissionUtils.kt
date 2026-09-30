package com.securecam.app.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Centralised permission logic for SecureCam.
 *
 * Permission groups:
 *  - [CAMERA_PERMISSIONS] : must-have for anything the app does.
 *  - [AUDIO_PERMISSION]   : needed for video capture with sound.
 *  - [GALLERY_PERMISSIONS]: version-dependent set for listing/deleting media.
 *  - [NOTIFICATION_PERMISSION]: Android 13+ gate for showing any notification,
 *    including the privacy-mandated background-recording one.
 */
object PermissionUtils {

    const val REQUEST_CODE_PERMISSIONS = 1001

    /** Runtime permission arrays used by the Compose permission requests. */
    val CAMERA_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    val AUDIO_PERMISSION = arrayOf(Manifest.permission.RECORD_AUDIO)

    /** Everything required before background recording may start. */
    val BACKGROUND_RECORDING_PERMISSIONS: Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.POST_NOTIFICATIONS,
            )
        } else {
            arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO,
            )
        }

    /**
     * Permissions needed by the in-app gallery. On API ≤ 28 we also request
     * WRITE_EXTERNAL_STORAGE because MediaStore inserts there require it.
     */
    val GALLERY_PERMISSIONS: Array<String> =
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
            )
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
            )
            else -> arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
            )
        }

    /** True when every permission in [permissions] has been granted. */
    fun hasAllPermissions(context: Context, permissions: Array<String>): Boolean =
        permissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }

    /** True when the app may open the camera preview / take photos. */
    fun hasCameraPermission(context: Context): Boolean =
        hasAllPermissions(context, CAMERA_PERMISSIONS)

    /** True when video with audio can be recorded. */
    fun hasAudioPermission(context: Context): Boolean =
        hasAllPermissions(context, AUDIO_PERMISSION)

    /** True when POST_NOTIFICATIONS is granted or not required (< Android 13). */
    fun hasNotificationPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            hasAllPermissions(context, arrayOf(Manifest.permission.POST_NOTIFICATIONS))

    /** True when the gallery screen can read MediaStore. */
    fun hasGalleryPermissions(context: Context): Boolean =
        hasAllPermissions(context, GALLERY_PERMISSIONS)
}
