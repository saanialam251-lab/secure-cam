package com.securecam.app.ui

import androidx.camera.view.PreviewView
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.RepeatMode
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material.icons.filled.GridOff
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TimerOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.securecam.app.ui.theme.GrayLight
import com.securecam.app.ui.theme.Ink
import com.securecam.app.ui.theme.InkElevated
import com.securecam.app.ui.theme.Paper
import com.securecam.app.ui.theme.RecordRed
import com.securecam.app.ui.theme.SecureCamCameraTheme
import com.securecam.app.util.FileUtils
import com.securecam.app.util.PermissionUtils
import com.securecam.app.viewmodel.AspectRatioOption
import com.securecam.app.viewmodel.CameraViewModel
import com.securecam.app.viewmodel.FlashMode
import com.securecam.app.viewmodel.TimerOption
import kotlin.math.roundToInt

/**
 * Full-screen camera UI.
 *
 * Layout (minimalism theme — ink background, hairline chips, one accent):
 *  ┌─────────────────────────────┐
 *  │ gallery … aspect/grid/timer │  ← top bar (translucent ink chips)
 *  │                             │
 *  │         preview             │  ← pinch-zoom + tap-to-focus
 *  │                             │
 *  │  [Start Background FR Rec]  │  ← background feature buttons
 *  │  [Stop Background FR Rec]   │    (stop only while active)
 *  │  flip     shutter     flip? │  ← bottom control bar
 *  └─────────────────────────────┘
 *
 * Rotation: the manifest handles config changes, so the camera is re-bound
 * only when lens/aspect changes; PreviewView target rotation follows its
 * display.
 */
@Composable
fun CameraScreen(
    onOpenGallery: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CameraViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
) {
    // Camera chrome always renders on the ink (dark) palette.
    SecureCamCameraTheme {
        val context = LocalContext.current
        val lifecycleOwner = LocalLifecycleOwner.current
        val uiState by viewModel.uiState.collectAsState()
        val isBackgroundRecording by viewModel.isBackgroundRecording.collectAsState()
        val snackbarHostState = remember { SnackbarHostState() }

        // Surface one-shot messages (photo saved, errors…) as snackbars.
        LaunchedEffect(viewModel) {
            viewModel.messages.collect { message ->
                snackbarHostState.showSnackbar(message)
            }
        }

        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Ink),
        ) {
            // ── Viewfinder + gestures ────────────────────────────────────
            CameraPreview(
                viewModel = viewModel,
                lifecycleOwner = lifecycleOwner,
                modifier = Modifier.fillMaxSize(),
            )

            // ── Top control bar ──────────────────────────────────────────
            TopBar(
                viewModel = viewModel,
                uiState = uiState,
                onOpenGallery = onOpenGallery,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )

            // ── Focus ring ───────────────────────────────────────────────
            uiState.focusIndicator?.let { indicator ->
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val x = (indicator.x * maxWidth.toPx()).roundToInt()
                    val y = (indicator.y * maxHeight.toPx()).roundToInt()
                    Box(
                        Modifier
                            .offset { IntOffset(x - 30.dp.toPx().roundToInt(), y - 30.dp.toPx().roundToInt()) }
                            .size(60.dp)
                            .border(1.5.dp, Paper.copy(alpha = 0.9f), CircleShape)
                            .alpha(0.9f),
                    )
                }
            }

            // ── Countdown (timer) ────────────────────────────────────────
            uiState.countdownRemaining?.let { seconds ->
                Text(
                    text = seconds.toString(),
                    style = androidx.compose.material3.MaterialTheme.typography.displayLarge,
                    color = Paper,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center),
                )
                // Cancel the pending shot by tapping the countdown.
                Box(
                    Modifier
                        .align(Alignment.Center)
                        .size(160.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { viewModel.cancelCountdown() },
                )
            }

            // ── Recording pill ───────────────────────────────────────────
            if (uiState.isRecording) {
                RecordingPill(
                    elapsedText = FileUtils.formatDuration(uiState.recordingDurationMs),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(top = 96.dp),
                )
            }

            // ── Bottom controls ──────────────────────────────────────────
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(bottom = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Background front-camera recording feature (spec §B).
                BackgroundRecordingControls(
                    isBackgroundRecording = isBackgroundRecording,
                    onStart = {
                        if (PermissionUtils.hasAllPermissions(
                                context,
                                PermissionUtils.BACKGROUND_RECORDING_PERMISSIONS,
                            )
                        ) {
                            viewModel.startBackgroundRecording(context)
                        }
                        // If permissions are missing MainActivity's gate
                        // already ran at launch; ask again to be safe.
                        else {
                            viewModel.messages.tryEmit("Camera, microphone and notification permissions are required")
                        }
                    },
                    onStop = { viewModel.stopBackgroundRecording(context) },
                )

                Spacer(Modifier.height(20.dp))

                BottomBar(
                    viewModel = viewModel,
                    uiState = uiState,
                    onOpenGallery = onOpenGallery,
                )
            }

            // ── Snackbar ─────────────────────────────────────────────────
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 140.dp),
            ) { data ->
                Snackbar(
                    containerColor = InkElevated,
                    contentColor = Paper,
                    shape = RoundedCornerShape(8.dp),
                ) { Text(data.visualMessage, style = androidx.compose.material3.MaterialTheme.typography.labelLarge) }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────
// Preview + gestures
// ─────────────────────────────────────────────────────────────────────────

@Composable
private fun CameraPreview(
    viewModel: CameraViewModel,
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }

    // Fetch the provider once.
    LaunchedEffect(Unit) {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener(
            {
                cameraProvider = runCatching { future.get() }.getOrNull()
                if (cameraProvider == null) {
                    viewModel.messages.tryEmit("Camera unavailable on this device")
                }
            },
            ContextCompat.getMainExecutor(context),
        )
    }

    // (Re)bind when the provider, surface, lens or aspect ratio changes.
    LaunchedEffect(cameraProvider, previewView, uiState.lensFacing, uiState.aspectRatio) {
        val provider = cameraProvider ?: return@LaunchedEffect
        val surface = previewView ?: return@LaunchedEffect
        viewModel.bindCamera(
            context = context,
            cameraProvider = provider,
            lifecycleOwner = lifecycleOwner,
            surfaceProvider = surface.surfaceProvider,
            targetRotation = surface.display?.rotation ?: 0,
        )
    }

    Box(modifier) {
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    previewView = this
                }
            },
            onRelease = { viewModel.unbindCamera() },
            modifier = Modifier
                .fillMaxSize()
                // Tap-to-focus at the touched point.
                .pointerInput(Unit) {
                    androidx.compose.foundation.gestures.detectTapGestures(
                        onTap = { offset ->
                            viewModel.onTapFocus(
                                x = offset.x / size.width.toFloat(),
                                y = offset.y / size.height.toFloat(),
                                viewWidth = size.width.toFloat(),
                                viewHeight = size.height.toFloat(),
                            )
                        },
                    )
                }
                // Pinch-to-zoom, clamped to the lens' supported range.
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        val base = viewModel.uiState.value.zoomRatio
                        var totalScale = 1f
                        while (true) {
                            val event = awaitPointerEvent()
                            val zoomChange = event.calculateZoom()
                            if (zoomChange != 1f) {
                                totalScale *= zoomChange
                                viewModel.onPinchZoom(base, totalScale)
                            }
                            if (event.changes.all { !it.pressed }) break
                        }
                    }
                },
        )

        // 3×3 grid overlay.
        if (uiState.gridEnabled) {
            Canvas(Modifier.fillMaxSize()) {
                val lineW = 0.8.dp.toPx()
                val color = Paper.copy(alpha = 0.35f)
                // Verticals
                (1..2).forEach { i ->
                    val x = size.width * i / 3f
                    drawLine(color, Offset(x, 0f), Offset(x, size.height), lineW, StrokeCap.Butt)
                }
                // Horizontals
                (1..2).forEach { i ->
                    val y = size.height * i / 3f
                    drawLine(color, Offset(0f, y), Offset(size.width, y), lineW, StrokeCap.Butt)
                }
            }
        }

        // Zoom ratio chip — tap to reset to 1x.
        if (uiState.maxZoom > 1.01f) {
            Text(
                text = String.format("%.1fx", uiState.zoomRatio),
                style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                color = Ink,
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Paper.copy(alpha = 0.85f))
                    .clickable { viewModel.onPinchZoom(1f, 1f) }
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────
// Top bar
// ─────────────────────────────────────────────────────────────────────────

@Composable
private fun TopBar(
    viewModel: CameraViewModel,
    uiState: com.securecam.app.viewmodel.CameraUiState,
    onOpenGallery: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
        IconButton(onClick = onOpenGallery) {
            Icon(
                Icons.Filled.Collections,
                contentDescription = "Gallery",
                tint = Paper,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            // Aspect ratio selector: 4:3 · 16:9 · 1:1
            AspectChip("4:3", uiState.aspectRatio == AspectRatioOption.FOUR_THREE) {
                viewModel.setAspectRatio(AspectRatioOption.FOUR_THREE)
            }
            AspectChip("16:9", uiState.aspectRatio == AspectRatioOption.SIXTEEN_NINE) {
                viewModel.setAspectRatio(AspectRatioOption.SIXTEEN_NINE)
            }
            AspectChip("1:1", uiState.aspectRatio == AspectRatioOption.SQUARE) {
                viewModel.setAspectRatio(AspectRatioOption.SQUARE)
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { viewModel.toggleGrid() }) {
                Icon(
                    if (uiState.gridEnabled) Icons.Filled.GridOn else Icons.Filled.GridOff,
                    contentDescription = "Grid overlay",
                    tint = if (uiState.gridEnabled) Paper else GrayLight,
                )
            }
            IconButton(onClick = { viewModel.setTimer(nextTimer(uiState.timer)) }) {
                Icon(
                    if (uiState.timer == TimerOption.OFF) Icons.Filled.TimerOff else Icons.Filled.Timer,
                    contentDescription = "Self timer",
                    tint = if (uiState.timer == TimerOption.OFF) GrayLight else Paper,
                )
            }
            IconButton(onClick = { viewModel.cycleFlashMode() }) {
                val icon = when (uiState.flashMode) {
                    FlashMode.AUTO -> Icons.Filled.FlashAuto
                    FlashMode.ON -> Icons.Filled.FlashOn
                    FlashMode.OFF -> Icons.Filled.FlashOff
                }
                Icon(
                    icon,
                    contentDescription = "Flash",
                    tint = if (uiState.flashMode == FlashMode.OFF) GrayLight else Paper,
                )
            }
        }

        }

        // Timer value badge under the bar (inside the Column, not the Row).
        if (uiState.timer != TimerOption.OFF) {
            Text(
                text = "${uiState.timer.seconds}s",
                style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                color = Paper,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
            )
        }
    }
}

private fun nextTimer(current: TimerOption): TimerOption = when (current) {
    TimerOption.OFF -> TimerOption.THREE
    TimerOption.THREE -> TimerOption.FIVE
    TimerOption.FIVE -> TimerOption.TEN
    TimerOption.TEN -> TimerOption.OFF
}

@Composable
private fun AspectChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
        color = if (selected) Ink else Paper,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) Paper else Ink.copy(alpha = 0.35f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

// ─────────────────────────────────────────────────────────────────────────
// Recording pill
// ─────────────────────────────────────────────────────────────────────────

@Composable
private fun RecordingPill(elapsedText: String, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "rec")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "recdot",
    )
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Ink.copy(alpha = 0.55f))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier
                .size(10.dp)
                .alpha(alpha)
                .background(RecordRed, CircleShape),
        )
        Text(
            elapsedText,
            style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
            color = Paper,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────
// Background recording controls (spec §B)
// ─────────────────────────────────────────────────────────────────────────

@Composable
private fun BackgroundRecordingControls(
    isBackgroundRecording: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (!isBackgroundRecording) {
            TextButton(onClick = onStart) {
                Text(
                    "Start Background Front Recording",
                    style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
                    color = Paper,
                    modifier = Modifier.alpha(0.9f),
                )
            }
        } else {
            TextButton(onClick = onStop) {
                Text(
                    "Stop Background Front Recording",
                    style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
                    color = RecordRed,
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────
// Bottom bar: flip · shutter · gallery
// ─────────────────────────────────────────────────────────────────────────

@Composable
private fun BottomBar(
    viewModel: CameraViewModel,
    uiState: com.securecam.app.viewmodel.CameraUiState,
    onOpenGallery: () -> Unit,
) {
    val context = LocalContext.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 36.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        // Flip camera (hidden while recording — CameraX forbids rebind).
        IconButton(onClick = { viewModel.switchLensFacing() }, enabled = !uiState.isRecording) {
            Icon(
                Icons.Filled.FlipCameraAndroid,
                contentDescription = "Switch camera",
                tint = if (uiState.isRecording) GrayLight else Paper,
            )
        }

        // Shutter: paper ring + solid core; red rounded-square while recording.
        val interaction = remember { MutableInteractionSource() }
        val pressed by interaction.collectIsPressedAsState()
        val scale by animateFloatAsState(
            targetValue = if (pressed) 0.9f else 1f,
            animationSpec = spring(dampingRatio = 0.5f, stiffness = 600f),
            label = "shutter",
        )
        Box(
            Modifier
                .size(78.dp)
                .scale(scale)
                .border(3.dp, Paper, CircleShape)
                .padding(7.dp)
                .clip(CircleShape)
                .background(if (uiState.isRecording) RecordRed else Paper)
                .clip(if (uiState.isRecording) RoundedCornerShape(10.dp) else CircleShape)
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                ) {
                    if (uiState.isRecording) {
                        viewModel.stopRecording()
                    } else {
                        viewModel.onShutterPressed(context)
                    }
                },
        )

        // Gallery shortcut.
        IconButton(onClick = onOpenGallery) {
            Icon(
                Icons.Filled.Collections,
                contentDescription = "Gallery",
                tint = Paper,
            )
        }
    }
}
