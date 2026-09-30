package com.securecam.app.ui

import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.securecam.app.util.FileUtils
import com.securecam.app.viewmodel.GalleryItem
import com.securecam.app.viewmodel.GalleryViewModel
import com.securecam.app.ui.theme.Divider
import com.securecam.app.ui.theme.Gray
import com.securecam.app.ui.theme.Ink
import com.securecam.app.ui.theme.Paper
import com.securecam.app.ui.theme.SecureCamTheme

/**
 * In-app gallery: a 3-column grid of everything SecureCam saved to
 * Pictures/SecureCam and Movies/SecureCam.
 *
 * Minimalism styling: paper background, hairline dividers, quiet captions.
 * Tap a tile for a full-screen preview (photos zoom, videos play via
 * [VideoView]); long-press offers delete; the preview sheet has share.
 */
@Composable
fun GalleryScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: GalleryViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var itemPendingDelete by remember { mutableStateOf<GalleryItem?>(null) }

    // Load media on first composition.
    LaunchedEffect(Unit) { viewModel.refresh(context) }

    // Surface one-shot messages.
    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    SecureCamTheme {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding(),
            ) {
                // ── Header ───────────────────────────────────────────────
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                    Text(
                        text = "Gallery",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = "${uiState.items.size} items",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 16.dp),
                    )
                }

                // Hairline divider — a minimalism signature.
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant),
                )

                // ── Content ──────────────────────────────────────────────
                when {
                    uiState.loading -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                "Loading…",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    uiState.items.isEmpty() -> {
                        Column(
                            Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text(
                                "Nothing captured yet",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onBackground,
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Photos and videos you take appear here.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    else -> {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(1.dp),
                            verticalArrangement = Arrangement.spacedBy(1.dp),
                            horizontalArrangement = Arrangement.spacedBy(1.dp),
                        ) {
                            items(uiState.items, key = { it.id }) { item ->
                                GalleryTile(
                                    item = item,
                                    onClick = { viewModel.setPreviewItem(item) },
                                    onLongPress = { itemPendingDelete = item },
                                )
                            }
                        }
                    }
                }
            }

            // ── Preview dialog ───────────────────────────────────────────
            uiState.previewItem?.let { item ->
                MediaPreviewDialog(
                    item = item,
                    onDismiss = { viewModel.setPreviewItem(null) },
                    onShare = {
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = if (item.isVideo) "video/mp4" else "image/jpeg"
                            putExtra(Intent.EXTRA_STREAM, item.uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(
                            Intent.createChooser(send, "Share media"),
                        )
                    },
                    onDelete = {
                        itemPendingDelete = item
                    },
                )
            }

            // ── Delete confirmation ──────────────────────────────────────
            itemPendingDelete?.let { item ->
                AlertDialog(
                    onDismissRequest = { itemPendingDelete = null },
                    title = { Text("Delete item?", style = MaterialTheme.typography.titleLarge) },
                    text = {
                        Text(
                            "This will permanently remove it from your device.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            viewModel.deleteItem(context, item)
                            itemPendingDelete = null
                        }) {
                            Text("Delete", color = MaterialTheme.colorScheme.error)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { itemPendingDelete = null }) {
                            Text("Cancel")
                        }
                    },
                )
            }

            // ── Snackbar ─────────────────────────────────────────────────
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(16.dp),
            ) { data ->
                Snackbar(
                    containerColor = MaterialTheme.colorScheme.inverseSurface,
                    contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                    shape = RoundedCornerShape(8.dp),
                ) { Text(data.visualMessage) }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────
// Grid tile
// ─────────────────────────────────────────────────────────────────────────

/** Combined tap + long-press modifier (Compose lacks a built-in pair). */
private fun Modifier.tapAndLongPress(
    onTap: () -> Unit,
    onLongPress: () -> Unit,
): Modifier = this.then(
    Modifier.clickable(
        onClick = onTap,
        onLongClick = onLongPress,
    ),
)

@Composable
private fun GalleryTile(
    item: GalleryItem,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
) {
    Box(
        Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(2.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .tapAndLongPress(onTap, onLongPress),
    ) {
        AsyncImage(
            model = item.uri,
            contentDescription = item.displayName,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )

        // Video badge: tiny play icon + duration.
        if (item.isVideo) {
            Row(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = FileUtils.formatDuration(item.durationMillis),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────
// Full-screen preview
// ─────────────────────────────────────────────────────────────────────────

@Composable
private fun MediaPreviewDialog(
    item: GalleryItem,
    onDismiss: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Ink.copy(alpha = 0.97f)),
        ) {
            // Media
            if (item.isVideo) {
                // VideoView is the pragmatic zero-dependency player for
                // local MediaStore URIs.
                AndroidView(
                    factory = { ctx ->
                        VideoView(ctx).apply {
                            setVideoURI(item.uri)
                            setMediaController(null)
                            setOnCompletionListener { it.seekTo(0) }
                            start()
                        }
                    },
                    modifier = Modifier
                        .fillMaxSize(),
                )
            } else {
                AsyncImage(
                    model = item.uri,
                    contentDescription = item.displayName,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            // Action bar
            Row(
                Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Close",
                        tint = Paper,
                    )
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onShare) {
                    Icon(Icons.Filled.Share, contentDescription = "Share", tint = Paper)
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "Delete",
                        tint = Paper,
                    )
                }
            }

            // Caption
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    item.displayName,
                    style = MaterialTheme.typography.labelLarge,
                    color = Paper,
                )
                Text(
                    FileUtils.formatDate(item.dateTaken).toString() +
                        " · " + FileUtils.formatSize(item.sizeBytes),
                    style = MaterialTheme.typography.labelSmall,
                    color = Gray,
                )
            }
        }
    }
}
