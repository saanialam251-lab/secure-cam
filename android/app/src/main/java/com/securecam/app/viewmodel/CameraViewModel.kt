package com.securecam.app.viewmodel

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest
import android.util.Log
import android.util.Rational
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.ViewPort
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.OutputOptions
import androidx.camera.video.FileDescriptorOutputOptions
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.PendingRecording
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
import java.nio.ByteBuffer
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt
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

/** White-balance presets plus a manual Kelvin value in Pro mode. */
enum class WhiteBalanceMode(val kelvin: Int) {
    AUTO(0),          // 0 = let the 3A decide
    DAYLIGHT(5_500),
    CLOUDY(6_500),
    SHADE(7_500),
    TUNGSTEN(3_200),
    FLUORESCENT(4_000),
}

/**
 * Manual ("DSLR") capture settings. `null` always means "Auto" — the 3A
 * algorithms keep control of that axis. Values map 1:1 onto Camera2
 * CaptureRequest keys (SENSOR_SENSITIVITY, SENSOR_EXPOSURE_TIME,
 * COLOR_CORRECTION_GAINS via Kelvin, LENS_FOCUS_DISTANCE, AE compensation).
 */
data class ProSettings(
    val iso: Int? = null,                    // null = auto ISO
    /** Exposure time in nanoseconds; null = auto. */
    val exposureTimeNanos: Long? = null,
    /** Manual white-balance temperature; null = AUTO preset or 3A WB. */
    val wbKelvin: Int? = null,
    /** Focus distance in diopters (Camera2 unit); null = continuous AF. */
    val focusDistanceDiopters: Float? = null,
    /** Exposure compensation index in steps of [CameraUiState.proCapabilities].evStep. */
    val evIndex: Int = 0,
)

/** Hard limits reported by the current lens, probed from Camera2 metadata. */
data class ProCapabilities(
    val proSupported: Boolean = false,
    val rawSupported: Boolean = false,
    val isoRange: IntRange = 50..6400,
    /** Exposure time range in nanoseconds. */
    val exposureRangeNanos: LongRange = 10_000L..1_000_000_000L,
    val minFocusDiopters: Float = 0f,
    val maxFocusDiopters: Float = 10f,
    val evRange: IntRange = -4..4,
    val evStep: Float = 0.5f,
    /** CameraX formats a 1/1000 s shutter as 1000000 ns in the UI layer. */
) {
    companion object {
        val Unavailable = ProCapabilities()
    }
}

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
    // ── Pro / DSLR mode ───────────────────────────────────────────────
    val proModeEnabled: Boolean = false,
    /** DNG (RAW) capture toggle — only meaningful when capabilities allow. */
    val rawEnabled: Boolean = false,
    val proSettings: ProSettings = ProSettings(),
    val proCapabilities: ProCapabilities = ProCapabilities.Unavailable,
    /** 64-bin luminance histogram from the ImageAnalysis stream (0..255). */
    val histogram: IntArray = IntArray(64) { 0 },
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

    /** Lets the UI post a one-shot message (SharedFlow itself has no tryEmit). */
    fun postMessage(text: String) {
        _messages.tryEmit(text)
    }

    /** One-shot user messages (toasts/snackbars). */
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    // CameraX objects — recreated on every (re)bind.
    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var camera: Camera? = null

    // Pro mode: luminance-histogram analyzer stream (bound only in Pro mode).
    private val analyzerExecutor = Executors.newSingleThreadExecutor()
    private var histogramAnalyzer: ImageAnalysis? = null

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
     * lens facing, aspect ratio, or Pro mode toggles (the UI triggers
     * re-binding) — ImageAnalysis joins/leaves the session with Pro mode.
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
            // NOTE: RAW/DNG output (ImageCapture.OUTPUT_FORMAT_RAW) needs
            // CameraX 1.5+, which needs compileSdk 36 + AGP 8.9.1+. Re-enable
            // here after upgrading the toolchain.
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

        // Pro mode: YUV analysis stream feeding the luminance histogram.
        // Deliberately low resolution — it only needs statistics.
        histogramAnalyzer = if (state.proModeEnabled) {
            ImageAnalysis.Builder()
                .setTargetAspectRatio(targetAspectRatio)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                .build()
                .also { analysis ->
                    analysis.setAnalyzer(analyzerExecutor) { image -> analyzeHistogram(image) }
                }
        } else {
            null
        }

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
            histogramAnalyzer?.let { addUseCase(it) }
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
            if (state.zoomRatio != 1f) camera?.cameraControl?.setZoomRatio(1f)

            // Pro mode: probe this lens' manual limits and (re)apply any
            // pending manual settings to the fresh camera session.
            if (state.proModeEnabled) {
                probeProCapabilities(state.lensFacing)
                applyProSettings(state.proSettings)
            }
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
        histogramAnalyzer?.clearAnalyzer()
        histogramAnalyzer = null
    }

    // ─────────────────────────────────────────────────────────────────────
    // Pro / DSLR mode
    // ─────────────────────────────────────────────────────────────────────

    /** Toggles Pro mode. Toggling re-binds so ImageAnalysis joins/leaves. */
    fun setProMode(enabled: Boolean) {
        if (_uiState.value.isRecording) return
        if (_uiState.value.proModeEnabled == enabled) return
        _uiState.value = _uiState.value.copy(
            proModeEnabled = enabled,
            // Manual settings reset when leaving Pro mode; capabilities are
            // re-probed on the next bind.
            proSettings = if (enabled) _uiState.value.proSettings else ProSettings(),
            histogram = IntArray(64) { 0 },
        )
        _messages.tryEmit(if (enabled) "Pro mode on" else "Auto mode")
    }

    /** DNG (RAW) capture toggle; requires a re-bind to change output format. */
    fun setRawEnabled(enabled: Boolean) {
        if (_uiState.value.isRecording) return
        if (!_uiState.value.proCapabilities.rawSupported && enabled) {
            _messages.tryEmit("RAW/DNG is not supported on this lens")
            return
        }
        _uiState.value = _uiState.value.copy(rawEnabled = enabled)
    }

    /**
     * Probes the current lens' manual-control limits from Camera2 metadata.
     * Runs on the main thread after a successful bind; results land in
     * [CameraUiState.proCapabilities].
     */
    private fun probeProCapabilities(lensFacing: Int) {
        val cam = camera ?: return
        runCatching {
            val camera2Info = Camera2CameraInfo.from(cam.cameraInfo)
            val characteristics = camera2Info.getCameraCharacteristic(
                CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE,
            )
            val exposureRange = camera2Info.getCameraCharacteristic(
                CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE,
            )
            val focusRange = camera2Info.getCameraCharacteristic(
                CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE,
            )

            // RAW support at the characteristic level (CameraX also exposes
            // ImageCaptureCapabilities; this check mirrors it for older APIs).
            val rawAvailable =
                camera2Info.getCameraCharacteristic(
                    CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES,
                )?.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW) == true

            val evRange = cam.cameraInfo.exposureState.compensationRange
            val evStep = cam.cameraInfo.exposureState.compensationStep.toFloat()

            val caps = ProCapabilities(
                proSupported = characteristics != null || exposureRange != null,
                // RAW capture is disabled with CameraX 1.4.x (see bindCamera).
                rawSupported = false && rawAvailable,
                isoRange = characteristics?.let { it.lower..it.upper } ?: ProCapabilities.Unavailable.isoRange,
                exposureRangeNanos = exposureRange?.let { it.lower..it.upper }
                    ?: ProCapabilities.Unavailable.exposureRangeNanos,
                minFocusDiopters = 0f,
                maxFocusDiopters = focusRange ?: 10f,
                evRange = evRange.lower..evRange.upper,
                evStep = if (evStep > 0f) evStep else 0.5f,
            )
            _uiState.value = _uiState.value.copy(proCapabilities = caps)

            // RAW toggle can't stay on if the lens can't deliver DNG.
            if (!caps.rawSupported && _uiState.value.rawEnabled) {
                _uiState.value = _uiState.value.copy(rawEnabled = false)
            }
        }.onFailure { e ->
            Log.w(TAG, "Pro capability probe failed", e)
            _uiState.value = _uiState.value.copy(proCapabilities = ProCapabilities.Unavailable)
        }
        // Keep lensFacing referenced for future per-lens caching.
        @Suppress("UNUSED_EXPRESSION")
        lensFacing
    }

    /**
     * Manual exposure/focus/WB entry points from the Pro panel. Each setter
     * stores the value and pushes it into the camera session immediately.
     * Passing `null` returns that axis to the 3A algorithms.
     */
    fun setManualIso(iso: Int?) {
        val clamped = iso?.coerceIn(
            _uiState.value.proCapabilities.isoRange.first,
            _uiState.value.proCapabilities.isoRange.last,
        )
        _uiState.value = _uiState.value.copy(
            proSettings = _uiState.value.proSettings.copy(iso = clamped),
        )
        applyProSettings(_uiState.value.proSettings)
    }

    fun setManualExposureTime(nanos: Long?) {
        val clamped = nanos?.coerceIn(
            _uiState.value.proCapabilities.exposureRangeNanos.first,
            _uiState.value.proCapabilities.exposureRangeNanos.last,
        )
        _uiState.value = _uiState.value.copy(
            proSettings = _uiState.value.proSettings.copy(exposureTimeNanos = clamped),
        )
        applyProSettings(_uiState.value.proSettings)
    }

    fun setManualWhiteBalance(kelvin: Int?) {
        _uiState.value = _uiState.value.copy(
            proSettings = _uiState.value.proSettings.copy(wbKelvin = kelvin),
        )
        applyProSettings(_uiState.value.proSettings)
    }

    fun setManualFocusDistance(diopters: Float?) {
        val clamped = diopters?.coerceIn(
            _uiState.value.proCapabilities.minFocusDiopters,
            _uiState.value.proCapabilities.maxFocusDiopters,
        )
        _uiState.value = _uiState.value.copy(
            proSettings = _uiState.value.proSettings.copy(focusDistanceDiopters = clamped),
        )
        applyProSettings(_uiState.value.proSettings)
    }

    /** EV compensation in steps; 0 = neutral. */
    fun setExposureCompensation(index: Int) {
        val evRange = _uiState.value.proCapabilities.evRange
        val clamped = index.coerceIn(evRange.first, evRange.last)
        _uiState.value = _uiState.value.copy(
            proSettings = _uiState.value.proSettings.copy(evIndex = clamped),
        )
        applyProSettings(_uiState.value.proSettings)
    }

    /** Resets every manual axis back to auto (3A control). */
    fun resetProSettings() {
        val fresh = ProSettings()
        _uiState.value = _uiState.value.copy(proSettings = fresh)
        applyProSettings(fresh)
        _messages.tryEmit("Manual controls reset to auto")
    }

    /**
     * Pushes the current [ProSettings] into the running camera session via
     * Camera2 interop. Manual AE values only take effect when the device's
     * AE is told to stand down; we combine them into one request so the
     * camera doesn't flap between states on every slider tick.
     */
    private fun applyProSettings(settings: ProSettings) {
        val cam = camera ?: return
        if (!_uiState.value.proModeEnabled) return

        val builder = CaptureRequestOptions.Builder()

        // ── Exposure: ISO + shutter ─────────────────────────────────────
        if (settings.iso != null || settings.exposureTimeNanos != null) {
            // SENSOR_SENSITIVITY / SENSOR_EXPOSURE_TIME only take effect when
            // AE_MODE is OFF. That is the manual-exposure contract.
            builder.setCaptureRequestOption(
                CaptureRequest.CONTROL_AE_MODE,
                CaptureRequest.CONTROL_AE_MODE_OFF,
            )
            settings.iso?.let {
                builder.setCaptureRequestOption(CaptureRequest.SENSOR_SENSITIVITY, it)
            }
            settings.exposureTimeNanos?.let {
                builder.setCaptureRequestOption(CaptureRequest.SENSOR_EXPOSURE_TIME, it)
            }
        }

        // ── EV compensation (auto-exposure path) ───────────────────────
        if (settings.evIndex != 0 && settings.iso == null && settings.exposureTimeNanos == null) {
            val evStep = _uiState.value.proCapabilities.evStep
            val evIndexRaw = (settings.evIndex / evStep).roundToInt()
            cam.cameraControl.setExposureCompensationIndex(evIndexRaw)
        }

        // ── White balance: Kelvin → RGB gains (approximation) ──────────
        if (settings.wbKelvin != null) {
            applyManualWhiteBalance(builder, settings.wbKelvin)
        }

        // ── Focus distance ─────────────────────────────────────────────
        // Manual lens position requires AF off; LENS_FOCUS_DISTANCE is then
        // honored by the lens driver on all devices shipping today.
        if (settings.focusDistanceDiopters != null) {
            builder.setCaptureRequestOption(
                CaptureRequest.CONTROL_AF_MODE,
                CaptureRequest.CONTROL_AF_MODE_OFF,
            )
            builder.setCaptureRequestOption(
                CaptureRequest.LENS_FOCUS_DISTANCE,
                settings.focusDistanceDiopters,
            )
        }

        runCatching {
            Camera2CameraControl.from(cam.cameraControl).setCaptureRequestOptions(builder.build())
        }.onFailure { e ->
            Log.w(TAG, "Pro settings apply failed", e)
            _messages.tryEmit("This lens rejected a manual control")
        }
    }

    /**
     * Approximates a colour temperature as RGB channel gains using a
     * compact Planckian-locus approximation — accurate enough for creative
     * WB, vastly simpler than carrying CCT curves per sensor.
     */
    private fun applyManualWhiteBalance(
        builder: CaptureRequestOptions.Builder,
        kelvin: Int,
    ) {
        val temp = kelvin / 100.0
        val r: Double
        val b: Double
        when {
            temp <= 66.0 -> {
                r = 255.0
                b = 255.0.coerceAtMost(308.76 * kotlin.math.ln(temp) - 254.28)
            }
            else -> {
                r = 329.7 * Math.pow(temp - 60.0, -0.1332)
                b = 255.0
            }
        }
        val g = if (temp < 66.0) {
            99.47 * kotlin.math.ln(temp) - 161.12
        } else {
            288.12 * Math.pow(temp - 60.0, -0.0755)
        }

        // Normalise around the neutral gain (1.0) so the transform stays
        // brightness-preserving.
        val gains = floatArrayOf(
            (r / 255.0).toFloat().coerceIn(0.5f, 2.5f),
            (g / 255.0).toFloat().coerceIn(0.5f, 2.5f),
            (b / 255.0).toFloat().coerceIn(0.5f, 2.5f),
        )

        builder.setCaptureRequestOption(
            CaptureRequest.CONTROL_AWB_MODE,
            CaptureRequest.CONTROL_AWB_MODE_OFF,
        )
        builder.setCaptureRequestOption(
            CaptureRequest.COLOR_CORRECTION_MODE,
            CaptureRequest.COLOR_CORRECTION_MODE_TRANSFORM_MATRIX,
        )
        builder.setCaptureRequestOption(
            CaptureRequest.COLOR_CORRECTION_GAINS,
            android.hardware.camera2.params.RggbChannelVector(
                gains[0], gains[1], gains[1], gains[2],
            ),
        )
    }

    /**
     * ImageAnalysis analyzer: 64-bin luminance histogram on a background
     * thread. Emits at most ~15 updates/second to keep recomposition cheap.
     */
    private fun analyzeHistogram(image: ImageProxy) {
        try {
            val bins = IntArray(64)
            val luma = image.planes[0]
            val buffer: ByteBuffer = luma.buffer
            val rowStride = luma.rowStride
            val width = image.width
            val height = image.height
            // Sample every 4th pixel in every 4th row — statistically plenty
            // for a 64-bin chart and cheap even at 1080p.
            var i = 0
            while (i < height) {
                var j = 0
                while (j < width) {
                    val v = buffer.get(i * rowStride + j).toInt() and 0xFF
                    bins[v shr 2]++
                    j += 4
                }
                i += 4
            }
            val total = (width / 4) * (height / 4)
            // Normalise to percentage-of-frame so the UI scale is stable.
            val normalised = IntArray(64) { (bins[it] * 100.0 / total).roundToInt() }
            updateHistogram(normalised)
        } catch (_: Exception) {
            // A failed analysis frame must never break the camera stream.
        } finally {
            image.close()
        }
    }

    /** Throttled state emit for the histogram (~15 fps of updates). */
    private fun updateHistogram(normalised: IntArray) {
        val now = System.currentTimeMillis()
        if (now - lastHistogramEmitMs < 66) return
        lastHistogramEmitMs = now
        _uiState.value = _uiState.value.copy(histogram = normalised)
    }

    @Volatile
    private var lastHistogramEmitMs = 0L

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

        val state = _uiState.value
        val rawCapture = state.proModeEnabled &&
            state.rawEnabled &&
            state.proCapabilities.rawSupported

        val legacyFile = if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q) {
            FileUtils.legacyPhotoFile(isRaw = rawCapture).also { it.parentFile?.mkdirs() }
        } else {
            null
        }

        val photoUri: android.net.Uri? =
            if (legacyFile == null) FileUtils.createPhotoUri(context, isRaw = rawCapture) else null

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

        val output = try {
            FileUtils.createVideoOutputOptions(context)
        } catch (e: Exception) {
            Log.e(TAG, "Could not create video output", e)
            _messages.tryEmit("Could not create video file")
            return
        }
        val prepared: PendingRecording = when (output) {
            is FileUtils.VideoOutput.MediaStoreOutput ->
                videoCapture.output.prepareRecording(
                    context,
                    FileDescriptorOutputOptions.Builder(output.pfd).build(),
                )
            is FileUtils.VideoOutput.LegacyFileOutput ->
                videoCapture.output.prepareRecording(
                    context,
                    FileOutputOptions.Builder(output.file).build(),
                )
        }
        @SuppressLint("MissingPermission") // guarded by hasAudioPermission()
        val pending = if (PermissionUtils.hasAudioPermission(context)) prepared.withAudio() else prepared

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
                        (output as? FileUtils.VideoOutput.MediaStoreOutput)?.let {
                            runCatching { it.pfd.close() }
                        }
                        val hadError = event.hasError()
                        if (hadError) {
                            val code = event.error
                            Log.e(TAG, "Recording finalize error: $code")
                            _messages.tryEmit("Recording failed (error $code)")
                            (output as? FileUtils.VideoOutput.MediaStoreOutput)?.let {
                                FileUtils.deletePendingUri(context, it.uri)
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
        analyzerExecutor.shutdown()
        super.onCleared()
    }

    private companion object {
        const val TAG = "CameraViewModel"
    }
}
