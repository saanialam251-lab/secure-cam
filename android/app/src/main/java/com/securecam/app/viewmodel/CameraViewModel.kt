package com.securecam.app.viewmodel

import android.content.Context
import android.util.Log
import android.util.Rational
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.ViewPort
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.OutputOptions
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.securecam.app.service.BackgroundVideoRecordingService
import com.securecam.app.util.FileUtils
import com.securecam.app.util.PermissionUtils
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Flash behaviour for photos; for video only ON/OFF matter (torch). */
enum class FlashMode { AUTO, ON, OFF }

/** Supported aspect ratios. SQUARE is implemented with a 1:1 ViewPort crop. */
enum class AspectRatioOption { FOUR_THREE, SIXTEEN_NINE, SQUARE }

/** Self-timer for photos, per spec: Off / 3s / 5s / 10s. */
enum class TimerOption(val seconds: Int) { OFF(0), THREE(3), FIVE(5), TEN(10) }

/** Where the tap-to-focus indicator should render (normalised view coords). */
data class FocusIndicator(val x: Float, val y: Float, val id: Long = System.nanoTime())

/** Immutable UI state for [com.securecam.app.ui.CameraScreen]. */
data class CameraUiState(
    val lensFacing: Int = CameraSelector.LENS_FACING_BACK,
    val flashMode: FlashMode = FlashMode.AUTO,
    val gridEnabled: Boolean = false,
    val aspectRatio: AspectRatioOption = AspectRatioOption.FOUR_THREE,
    val timer: TimerOption = TimerOption.OFF,
    /** Remaining countdown while a timer photo is pending; null when idle. */
    val countdownRemaining: Int? = null,
    val isRecording: Boolean = false,
    val recordingDurationMs: Long = 0,
    val zoomRatio: Float = 1f,
    val minZoom: Float = 1f,
    val maxZoom: Float = 1f,
    val torchOn: Boolean = false,
    val focusIndicator: FocusIndicator? = null,
    /** True while the background front-camera service is alive. */
    val isBackgroundRecording: Boolean = false,
    val lastPhotoUri: android.net.Uri? = null,
)

/**
 * CameraViewModel — the MVVM state holder for the camera screen.
 *
 * Owns the CameraX use cases ([ImageCapture], [VideoCapture], torch/zoom
 * controls), photo timer, recording timer and zoom/focus state. The UI layer
 * only renders [uiState] and forwards gestures.
 *
 * Lifecycle contract:
 *  - [bindCamera] is called from the UI when the preview is composed and
 *    permissions are granted (and again whenever lens/aspect changes).
 *  - [unbindCamera] is called when the composable leaves composition.
 *  - Configuration changes are handled via manifest `configChanges`, so the
 *    ViewModel keeps camera objects alive across rotation.
 */
class CameraViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(CameraUiState())
    val uiState: StateFlow<CameraUiState> = _uiState.asStateFlow()

    /** One-shot user messages (toasts/snackbars). */
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    // CameraX objects — recreated on every (re)bind.
    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var camera: Camera? = null

    private var activeRecording: Recording? = null
    private var recordingTimerJob: Job? = null
    private var countdownJob: Job? = null
    private var focusIndicatorJob: Job? = null

    /** Guards against double-shutter taps during a capture. */
    private var capturingPhoto = false

    // ─────────────────────────────────────────────────────────────────────
    // Binding
    // ─────────────────────────────────────────────────────────────────────

    /**
     * (Re)binds CameraX use cases. Called on first composition and whenever
     * lens facing or aspect ratio changes (the UI triggers re-binding).
     */
    fun bindCamera(
        context: Context,
        cameraProvider: ProcessCameraProvider,
        lifecycleOwner: LifecycleOwner,
        surfaceProvider: Preview.SurfaceProvider,
        targetRotation: Int,
    ) {
        val state = _uiState.value

        // Aspect ratio: 4:3 and 16:9 are honored natively by CameraX use
        // cases; 1:1 is produced by cropping everything through a shared
        // square ViewPort further down.
        val targetAspectRatio = when (state.aspectRatio) {
            AspectRatioOption.FOUR_THREE -> AspectRatio.RATIO_4_3
            AspectRatioOption.SIXTEEN_NINE -> AspectRatio.RATIO_16_9
            AspectRatioOption.SQUARE -> AspectRatio.RATIO_4_3
        }

        val preview = Preview.Builder()
            .setTargetAspectRatio(targetAspectRatio)
            .setTargetRotation(targetRotation)
            .build()
            .apply { setSurfaceProvider(surfaceProvider) }

        imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .setTargetAspectRatio(targetAspectRatio)
            .setTargetRotation(targetRotation)
            .build()

        // Recorder: good default quality; audio is requested only when the
        // RECORD_AUDIO permission is granted (checked at record start).
        val recorder = Recorder.Builder()
            .setQualitySelector(
                androidx.camera.video.QualitySelector.from(
                    androidx.camera.video.Quality.HIGHEST,
                    androidx.camera.video.FallbackStrategy.higherQualityOrLowerThan(
                        androidx.camera.video.Quality.SD,
                    ),
                ),
            )
            .build()
        videoCapture = VideoCapture.withOutput(recorder)

        // 1:1 output is achieved with a shared ViewPort: every use case crops
        // to the same square region. 4:3 / 16:9 use CameraX defaults.
        val useCaseGroup = UseCaseGroup.Builder().apply {
            if (state.aspectRatio == AspectRatioOption.SQUARE) {
                setViewPort(
                    ViewPort.Builder(Rational(1, 1), targetRotation).build(),
                )
            }
            addUseCase(preview)
            addUseCase(imageCapture!!)
            addUseCase(videoCapture!!)
        }.build()

        val selector = CameraSelector.Builder()
            .requireLensFacing(state.lensFacing)
            .build()

        try {
            cameraProvider.unbindAll()
            camera = cameraProvider.bindToLifecycle(
                lifecycleOwner,
                selector,
                useCaseGroup,
            )

            // Track zoom limits for pinch-to-zoom clamping and the zoom chip.
            camera?.cameraInfo?.zoomState?.observe(lifecycleOwner) { zoomState ->
                _uiState.value = _uiState.value.copy(
                    zoomRatio = zoomState.zoomRatio,
                    minZoom = zoomState.minZoomRatio,
                    maxZoom = zoomState.maxZoomRatio,
                )
            }

            // Front cameras are mirrored by the pipeline already; nothing to
            // do here. Reset zoom when switching lenses for a clean start.
            if (state.zoomRatio != 1f) setZoom(1f)
        } catch (e: Exception) {
            Log.e(TAG, "Camera binding failed", e)
            _messages.tryEmit("Could not start camera: ${e.localizedMessage ?: "unknown error"}")
        }
    }

    /** Releases the camera when the camera screen leaves composition. */
    fun unbindCamera() {
        countdownJob?.cancel()
        recordingTimerJob?.cancel()
        try {
            activeRecording?.stop()
        } catch (_: Exception) {
            // Recording already finished or was never started.
        }
        activeRecording = null
        camera = null
        imageCapture = null
        videoCapture = null
    }

    // ─────────────────────────────────────────────────────────────────────
    // Settings toggles
    // ─────────────────────────────────────────────────────────────────────

    /** Cycles flash AUTO → ON → OFF. While recording, ON enables the torch. */
    fun cycleFlashMode() {
        if (_uiState.value.isRecording) return
        val next = when (_uiState.value.flashMode) {
            FlashMode.AUTO -> FlashMode.ON
            FlashMode.ON -> FlashMode.OFF
            FlashMode.OFF -> FlashMode.AUTO
        }
        _uiState.value = _uiState.value.copy(flashMode = next)
        applyTorchForVideo()
    }

    fun toggleGrid() {
        _uiState.value = _uiState.value.copy(gridEnabled = !_uiState.value.gridEnabled)
    }

    fun setAspectRatio(option: AspectRatioOption) {
        if (_uiState.value.isRecording) return
        if (option != _uiState.value.aspectRatio) {
            _uiState.value = _uiState.value.copy(aspectRatio = option)
            _messages.tryEmit("Re-opening camera for ${option.name.lowercase()} ratio…")
        }
    }

    fun setTimer(option: TimerOption) {
        if (_uiState.value.isRecording) return
        _uiState.value = _uiState.value.copy(timer = option)
    }

    /**
     * Switches front/back camera. The UI observes [uiState] and calls
     * [bindCamera] again to apply the new lens.
     */
    fun switchLensFacing() {
        if (_uiState.value.isRecording) return
        val newFacing =
            if (_uiState.value.lensFacing == CameraSelector.LENS_FACING_BACK) {
                CameraSelector.LENS_FACING_FRONT
            } else {
                CameraSelector.LENS_FACING_BACK
            }
        _uiState.value = _uiState.value.copy(
            lensFacing = newFacing,
            zoomRatio = 1f,
        )
    }

    /** Maps photo flash mode onto ImageCapture and video onto the torch. */
    private fun applyTorchForVideo() {
        val torch =
            _uiState.value.flashMode == FlashMode.ON && _uiState.value.isRecording
        _uiState.value = _uiState.value.copy(torchOn = torch)
        camera?.cameraControl?.enableTorch(torch)
    }

    // ─────────────────────────────────────────────────────────────────────
    // Gestures
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Pinch-to-zoom: [totalScale] is the accumulated gesture scale relative
     * to gesture start. Clamped to the camera's supported range (spec: up to
     * 10x when the hardware allows it).
     */
    fun onPinchZoom(baseRatio: Float, totalScale: Float) {
        val cam = camera ?: return
        val zoomState = cam.cameraInfo.zoomState.value ?: return
        val target = (baseRatio * totalScale)
            .coerceIn(zoomState.minZoomRatio, zoomState.maxZoomRatio)
        cam.cameraControl.setZoomRatio(target)
    }

    /**
     * Tap-to-focus at the given normalised (0..1) view coordinates. The
     * metering point is mapped through [androidx.camera.core.SurfaceOrientedMeteringPointFactory]
     * so it matches what the user sees in the preview.
     */
    fun onTapFocus(x: Float, y: Float, viewWidth: Float, viewHeight: Float) {
        val cam = camera ?: return
        if (viewWidth <= 0f || viewHeight <= 0f) return
        val factory = androidx.camera.core.SurfaceOrientedMeteringPointFactory(
            viewWidth,
            viewHeight,
        )
        val point = factory.createPoint(x * viewWidth, y * viewHeight)
        val action = FocusMeteringAction.Builder(
            point,
            FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE,
        )
            .setAutoCancelDuration(3, TimeUnit.SECONDS)
            .build()
        cam.cameraControl.startFocusAndMetering(action)

        // Show the focus ring briefly where the user tapped.
        val indicator = FocusIndicator(x, y)
        _uiState.value = _uiState.value.copy(focusIndicator = indicator)
        focusIndicatorJob?.cancel()
        focusIndicatorJob = viewModelScope.launch {
            delay(1200)
            if (_uiState.value.focusIndicator?.id == indicator.id) {
                _uiState.value = _uiState.value.copy(focusIndicator = null)
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Photo capture (with optional timer)
    // ─────────────────────────────────────────────────────────────────────

    /** Called by the shutter button. Honours the selected self-timer. */
    fun onShutterPressed(context: Context) {
        val timer = _uiState.value.timer
        if (timer == TimerOption.OFF) {
            takePhoto(context)
        } else {
            startCountdown(context, timer.seconds)
        }
    }

    private fun startCountdown(context: Context, seconds: Int) {
        if (countdownJob?.isActive == true) return
        countdownJob = viewModelScope.launch {
            for (remaining in seconds downTo 1) {
                _uiState.value = _uiState.value.copy(countdownRemaining = remaining)
                delay(1_000)
            }
            _uiState.value = _uiState.value.copy(countdownRemaining = null)
            takePhoto(context)
        }
    }

    fun cancelCountdown() {
        countdownJob?.cancel()
        _uiState.value = _uiState.value.copy(countdownRemaining = null)
    }

    /**
     * Captures a photo. Output goes to MediaStore (Pictures/SecureCam) on
     * API 29+, or to the public Pictures/SecureCam directory + MediaScanner
     * on API 26–28 (see [FileUtils]).
     */
    private fun takePhoto(context: Context) {
        val imageCapture = imageCapture ?: return
        if (capturingPhoto) return
        capturingPhoto = true

        val legacyFile = if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q) {
            FileUtils.legacyPhotoFile().also { it.parentFile?.mkdirs() }
        } else {
            null
        }

        val photoUri: android.net.Uri? =
            if (legacyFile == null) FileUtils.createPhotoUri(context) else null

        val outputOptions = when {
            photoUri != null ->
                ImageCapture.OutputFileOptions.Builder(
                    context.contentResolver,
                    photoUri,
                    null,
                ).build()
            legacyFile != null ->
                ImageCapture.OutputFileOptions.Builder(legacyFile).build()
            else -> {
                capturingPhoto = false
                _messages.tryEmit("Could not create output file")
                return
            }
        }

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    capturingPhoto = false
                    if (legacyFile != null) {
                        FileUtils.indexLegacyFile(context, legacyFile)
                    }
                    val savedUri = outputFileResults.savedUri
                        ?: photoUri
                    _uiState.value = _uiState.value.copy(lastPhotoUri = savedUri)
                    _messages.tryEmit("Photo saved")
                }

                override fun onError(exception: ImageCaptureException) {
                    capturingPhoto = false
                    Log.e(TAG, "Photo capture failed", exception)
                    // Clean up the pending MediaStore row on failure.
                    photoUri?.let { FileUtils.deletePendingUri(context, it) }
                    _messages.tryEmit("Photo failed: ${exception.localizedMessage ?: "error"}")
                }
            },
        )
    }

    // ─────────────────────────────────────────────────────────────────────
    // Video recording
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Starts recording with audio when permitted. Output is a MediaStore
     * entry under Movies/SecureCam (API 29+) or a direct file (API 26–28).
     */
    fun startRecording(context: Context) {
        if (_uiState.value.isRecording || _uiState.value.isBackgroundRecording) return
        val videoCapture = videoCapture ?: run {
            _messages.tryEmit("Camera is not ready yet")
            return
        }

        val output = FileUtils.createVideoOutputOptions(context)
        val outputOptions: OutputOptions = when (output) {
            is FileUtils.VideoOutput.MediaStoreOutput ->
                MediaStoreOutputOptions.Builder(context.contentResolver, output.uri)
                    .build()
            is FileUtils.VideoOutput.LegacyFileOutput ->
                FileOutputOptions.Builder(output.file).build()
        }

        val pending = videoCapture.output
            .prepareRecording(context, outputOptions)
            .apply {
                if (PermissionUtils.hasAudioPermission(context)) withAudio()
            }

        try {
            val recording = pending.start(ContextCompat.getMainExecutor(context)) { event ->
                when (event) {
                    is VideoRecordEvent.Start -> {
                        _uiState.value = _uiState.value.copy(
                            isRecording = true,
                            recordingDurationMs = 0,
                        )
                        applyTorchForVideo()
                        startRecordingTicker()
                    }

                    is VideoRecordEvent.Finalize -> {
                        stopRecordingTicker()
                        val hadError = event.hasError()
                        if (hadError) {
                            val code = event.error
                            Log.e(TAG, "Recording finalize error: $code")
                            _messages.tryEmit("Recording failed (error $code)")
                            if (event is VideoRecordEvent.Finalize) {
                                event.outputResults.outputUri?.let {
                                    FileUtils.deletePendingUri(context, it)
                                }
                            }
                        } else {
                            if (output is FileUtils.VideoOutput.LegacyFileOutput) {
                                FileUtils.indexLegacyFile(context, output.file)
                            }
                            _messages.tryEmit("Video saved")
                        }
                        _uiState.value = _uiState.value.copy(
                            isRecording = false,
                            torchOn = false,
                            recordingDurationMs = 0,
                        )
                        activeRecording = null
                    }
                }
            }
            activeRecording = recording
        } catch (e: Exception) {
            Log.e(TAG, "Could not start recording", e)
            if (output is FileUtils.VideoOutput.MediaStoreOutput) {
                FileUtils.deletePendingUri(context, output.uri)
            }
            _messages.tryEmit("Could not start recording")
        }
    }

    /** Stops the in-app recording (the finalize callback handles saving). */
    fun stopRecording() {
        activeRecording?.stop()
    }

    private fun startRecordingTicker() {
        recordingTimerJob?.cancel()
        recordingTimerJob = viewModelScope.launch {
            val startedAt = System.currentTimeMillis()
            while (isActive && _uiState.value.isRecording) {
                _uiState.value = _uiState.value.copy(
                    recordingDurationMs = System.currentTimeMillis() - startedAt,
                )
                delay(250)
            }
        }
    }

    private fun stopRecordingTicker() {
        recordingTimerJob?.cancel()
        recordingTimerJob = null
    }

    // ─────────────────────────────────────────────────────────────────────
    // Background front-camera recording (delegated to the service)
    // ─────────────────────────────────────────────────────────────────────

    /** Mirrors the service's live state so the UI can toggle its buttons. */
    val isBackgroundRecording: StateFlow<Boolean>
        get() = BackgroundVideoRecordingService.isRunning

    /** Kicks off the foreground service (permissions are checked by the UI). */
    fun startBackgroundRecording(context: Context) {
        BackgroundVideoRecordingService.start(context)
        _messages.tryEmit("Background front recording started")
    }

    fun stopBackgroundRecording(context: Context) {
        BackgroundVideoRecordingService.stop(context)
    }

    override fun onCleared() {
        unbindCamera()
        super.onCleared()
    }

    private companion object {
        const val TAG = "CameraViewModel"
    }
}
