package com.securecam.app.viewmodel

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.securecam.app.util.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** A single photo or video row shown in the gallery grid. */
data class GalleryItem(
    val id: Long,
    val uri: Uri,
    val isVideo: Boolean,
    val displayName: String,
    val dateTaken: Long,
    val durationMillis: Long,
    val sizeBytes: Long,
)

/** UI state for the gallery screen. */
data class GalleryUiState(
    val loading: Boolean = true,
    val items: List<GalleryItem> = emptyList(),
    /** Item currently opened in the full-screen preview sheet, if any. */
    val previewItem: GalleryItem? = null,
    val message: String? = null,
)

/**
 * Gallery ViewModel — MVVM state holder for [com.securecam.app.ui.GalleryScreen].
 *
 * Reads SecureCam media from MediaStore (Pictures/SecureCam + Movies/SecureCam)
 * on Dispatchers.IO and exposes an immutable [GalleryUiState].
 */
class GalleryViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(GalleryUiState())
    val uiState: StateFlow<GalleryUiState> = _uiState.asStateFlow()

    /** (Re)loads the media list. Called on first composition and on refresh. */
    fun refresh(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(loading = true)
            val items = queryMedia(context)
            _uiState.value = _uiState.value.copy(
                loading = false,
                items = items,
                message = if (items.isEmpty()) null else null,
            )
        }
    }

    /** Opens/closes the preview sheet. */
    fun setPreviewItem(item: GalleryItem?) {
        _uiState.value = _uiState.value.copy(previewItem = item)
    }

    /** Consumes one-shot messages (snackbar/toast text). */
    fun consumeMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }

    /**
     * Deletes a media item via MediaStore. App-own items delete without any
     * extra prompt; items inserted by other apps would need the system
     * "createDeleteRequest" flow, which is intentionally out of scope here
     * because SecureCam only surfaces its own captures.
     */
    fun deleteItem(context: Context, item: GalleryItem) {
        viewModelScope.launch(Dispatchers.IO) {
            val deleted = runCatching {
                context.contentResolver.delete(item.uri, null, null)
            }.getOrDefault(0)

            if (deleted > 0) {
                _uiState.value = _uiState.value.copy(
                    items = _uiState.value.items.filterNot { it.id == item.id },
                    previewItem = null,
                    message = "Deleted",
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    previewItem = null,
                    message = "Couldn't delete this item",
                )
            }
        }
    }

    /**
     * Queries MediaStore for SecureCam-owned images and videos, newest first.
     * Filtering on RELATIVE_PATH keeps third-party media out of the grid.
     */
    private fun queryMedia(context: Context): List<GalleryItem> {
        val result = mutableListOf<GalleryItem>()

        result += queryCollection(
            context,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            isVideo = false,
            folderPrefix = FileUtils.PICTURES_RELATIVE_PATH,
        )
        result += queryCollection(
            context,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            isVideo = true,
            folderPrefix = FileUtils.MOVIES_RELATIVE_PATH,
        )

        return result.sortedByDescending { it.dateTaken }
    }

    private fun queryCollection(
        context: Context,
        collection: Uri,
        isVideo: Boolean,
        folderPrefix: String,
    ): List<GalleryItem> {
        val projection = mutableListOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.RELATIVE_PATH,
        )
        if (isVideo) {
            projection += MediaStore.Video.Media.DURATION
        }

        val items = mutableListOf<GalleryItem>()
        runCatching {
            context.contentResolver.query(
                collection,
                projection.toTypedArray(),
                "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?",
                arrayOf("$folderPrefix%"),
                "${MediaStore.MediaColumns.DATE_ADDED} DESC",
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                val durationCol =
                    if (isVideo) cursor.getColumnIndex(MediaStore.Video.Media.DURATION) else -1

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    items += GalleryItem(
                        id = id,
                        uri = android.content.ContentUris.withAppendedId(collection, id),
                        isVideo = isVideo,
                        displayName = cursor.getString(nameCol) ?: "unknown",
                        dateTaken = cursor.getLong(dateCol) * 1000L,
                        durationMillis =
                            if (durationCol >= 0) cursor.getLong(durationCol) else 0L,
                        sizeBytes = cursor.getLong(sizeCol),
                    )
                }
            }
        }
        return items
    }
}
