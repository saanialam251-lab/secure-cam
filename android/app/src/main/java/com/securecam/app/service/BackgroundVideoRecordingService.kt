package com.securecam.app.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.CountDownTimer
import android.util.Log
import android.widget.Toast
import androidx.camera.core.CameraSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.OutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ServiceLifecycleDispatcher
import androidx.lifecycle.lifecycleScope
import com.securecam.app.MainActivity
import com.securecam.app.R
import com.securecam.app.util.FileUtils
import com.securecam.app.util.PermissionUtils
import java.util.concurrent.Executor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Foreground service that records video from the FRONT camera while the app
 * is minimized, the screen is locked, or other apps are in the foreground.
 *
 * ── Privacy contract (Google Play policy) ────────────────────────────────
 * The persistent notification is posted IMMEDIATELY in [onCreate], before
 * any camera work starts. It is `setOngoing(true)` so it cannot be swiped
 * away, uses IMPORTANCE_LOW (channel created in SecureCamApp), shows a
 * recording icon and an elapsed-time chronometer, and carries [Stop
 * Recording] and [Open App] action buttons. It remains visible for the
 * entire duration of the recording. There is no code path in this app that
 * records without this notification being visible.
 *
 * ── CameraX inside a Service ─────────────────────────────────────────────
 * [ProcessCameraProvider.bindToLifecycle] needs a [LifecycleOwner]. The
 * service becomes one via [ServiceLifecycleDispatcher], whose registry is
 * driven from the service callbacks. Recording output lands in
 * Movies/SecureCam as BG_<timestamp>.mp4 through [FileUtils].
 */
class BackgroundVideoRecordingService : Service(), LifecycleOwner {

    private val dispatcher = ServiceLifecycleDispatcher(this)

    private var cameraProvider: ProcessCameraProvider? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var activeRecording: Recording? = null
    private var output: FileUtils.VideoOutput? = null
    private var dismissTimer: CountDownTimer? = null

    /** Guards double-saves if Finalize fires twice or stop is called late. */
    private var isFinalizing = false

    override val lifecycle: LifecycleRegistry
        get() = dispatcher.lifecycle

    // ─────────────────────────────────────────────────────────────────────
    // Static helpers + shared live state (observed by the camera screen)
    // ─────────────────────────────────────────────────────────────────────
    companion object {
        private const val TAG = "BgVideoRecordingSvc"
        const val CHANNEL_ID = "securecam_bg_recording"
        const val NOTIFICATION_ID = 42

        /** Notification / intent actions. */
        const val ACTION_STOP = "com.securecam.app.action.STOP_RECORDING"

        /** Observable live state consumed by the camera screen. */
        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

        /** Starts the foreground service (permissions must already be granted). */
        fun start(context: Context) {
            val intent = Intent(context, BackgroundVideoRecordingService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        /**
         * Asks a running service to stop and save. Delivered as a plain
         * startService with an action; the service is already in the
         * foreground, so this is allowed even from the notification while
         * the app is backgrounded or the screen is locked.
         */
        fun stop(context: Context) {
            val intent = Intent(context, BackgroundVideoRecordingService::class.java)
                .setAction(ACTION_STOP)
            context.startService(intent)
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Service lifecycle
    // ─────────────────────────────────────────────────────────────────────

    override fun onCreate() {
        // ServiceLifecycleDispatcher MUST be told before super calls.
        dispatcher.onServicePreSuperOnCreate()
        super.onCreate()

        // PRIVACY RULE: post the persistent, non-dismissable notification
        // FIRST — before the camera is bound, before a single frame is
        // captured. The user must always be able to see that recording is
        // happening and be able to stop it.
        promoteToForeground()
        _isRunning.value = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        dispatcher.onServicePreSuperOnStart()

        when (intent?.action) {
            ACTION_STOP -> {
                Log.i(TAG, "Stop requested (notification action or in-app button)")
                stopRecordingAndSave()
            }
            else -> startRecording()
        }
        // Redeliver so an unexpected process death restarts the service —
        // the persistent notification returns with it (never silent).
        return START_REDELIVER_INTENT
    }

    // ─────────────────────────────────────────────────────────────────────
    // Notification (privacy-mandated, always visible)
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Builds and posts the persistent notification and promotes the service
     * to foreground with camera|microphone types (mandatory on Android 14+).
     */
    private fun promoteToForeground() {
        val notification = buildLiveNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA or
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildLiveNotification(): Notification {
        // Tapping the notification body opens the app so the user can stop.
        val openAppIntent = PendingIntent.getActivity(
            this,
            REQUEST_CODE_OPEN_APP,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        // [Stop Recording] action — delivered straight to the service.
        val stopIntent = PendingIntent.getService(
            this,
            REQUEST_CODE_STOP,
            Intent(this, BackgroundVideoRecordingService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.bg_notification_title))
            .setContentText(getString(R.string.bg_notification_content))
            .setSmallIcon(R.drawable.ic_notification) // recording icon (monochrome)
            .setOngoing(true) // cannot be swiped away
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW) // matches IMPORTANCE_LOW
            // Elapsed-time chronometer next to the recording icon.
            .setUsesChronometer(true)
            .setWhen(System.currentTimeMillis())
            .setContentIntent(openAppIntent)
            .addAction(0, getString(R.string.bg_notification_action_stop), stopIntent)
            .addAction(0, getString(R.string.bg_notification_action_open), openAppIntent)
            // Show immediately even while the user is using another app.
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    // ─────────────────────────────────────────────────────────────────────
    // CameraX recording
    // ─────────────────────────────────────────────────────────────────────

    private fun startRecording() {
        // Defensive re-check: never record without the full permission set.
        if (!PermissionUtils.hasAllPermissions(
                this,
                PermissionUtils.BACKGROUND_RECORDING_PERMISSIONS,
            )
        ) {
            Log.w(TAG, "Missing permissions — refusing to record (no silent recording, ever).")
            stopSelf()
            return
        }

        // A second start while already recording is a no-op.
        if (activeRecording != null) return

        val future = ProcessCameraProvider.getInstance(this)
        future.addListener(
            {
                val provider = try {
                    future.get()
                } catch (e: Exception) {
                    Log.e(TAG, "Could not obtain camera provider", e)
                    stopRecordingAndSave()
                    return@addListener
                }
                cameraProvider = provider
                bindAndStart(provider)
            },
            mainExecutor,
        )
    }

    /** Runs on the main thread once the camera provider is available. */
    private fun bindAndStart(provider: ProcessCameraProvider) {
        // High-quality recorder with a graceful quality fallback.
        val recorder = Recorder.Builder()
            .setQualitySelector(
                QualitySelector.from(
                    Quality.HIGHEST,
                    FallbackStrategy.lowerQualityOrHigherThan(Quality.SD),
                ),
            )
            .build()
        val videoCapture = VideoCapture.withOutput(recorder)
        this.videoCapture = videoCapture

        // BG_<timestamp>.mp4 in Movies/SecureCam (MediaStore on API 29+,
        // direct public file + MediaScanner on API 26–28) — see FileUtils.
        val output = FileUtils.createBackgroundVideoOutput(this)
        this.output = output

        val outputOptions: OutputOptions = when (output) {
            is FileUtils.VideoOutput.MediaStoreOutput ->
                MediaStoreOutputOptions.Builder(contentResolver, output.uri).build()
            is FileUtils.VideoOutput.LegacyFileOutput ->
                FileOutputOptions.Builder(output.file).build()
        }

        val pendingRecording = videoCapture.output
            .prepareRecording(this, outputOptions)
            .apply {
                // Audio is included only when RECORD_AUDIO was granted.
                if (PermissionUtils.hasAudioPermission(this@BackgroundVideoRecordingService)) {
                    withAudio()
                }
            }

        try {
            // FRONT camera only, per spec.
            provider.unbindAll()
            provider.bindToLifecycle(
                this,
                CameraSelector.DEFAULT_FRONT_CAMERA,
                videoCapture,
            )

            activeRecording = pendingRecording.start(mainExecutor) { event ->
                handleRecordingEvent(event)
            }
            Log.i(TAG, "Background front-camera recording started (notification visible)")
        } catch (e: Exception) {
            Log.e(TAG, "Recording start failed", e)
            (output as? FileUtils.VideoOutput.MediaStoreOutput)?.let {
                FileUtils.deletePendingUri(this, it.uri)
            }
            Toast.makeText(this, "Background recording failed to start", Toast.LENGTH_SHORT)
                .show()
            stopSelf()
        }
    }

    private fun handleRecordingEvent(event: VideoRecordEvent) {
        when (event) {
            is VideoRecordEvent.Start -> {
                Log.i(TAG, "Recording started")
            }

            is VideoRecordEvent.Finalize -> {
                val hadError = event.hasError()
                if (hadError) {
                    Log.e(TAG, "Recording finalized with error ${event.error}")
                }

                // Make sure the pending MediaStore row resolves correctly.
                val currentOutput = output
                if (!hadError && currentOutput is FileUtils.VideoOutput.LegacyFileOutput) {
                    FileUtils.indexLegacyFile(this, currentOutput.file)
                }
                if (hadError && currentOutput is FileUtils.VideoOutput.MediaStoreOutput) {
                    FileUtils.deletePendingUri(this, currentOutput.uri)
                }

                // Release the camera right away; the service lingers only to
                // show the "saved" confirmation for the required 3 seconds.
                runCatching { cameraProvider?.unbindAll() }
                videoCapture = null
                activeRecording = null
                _isRunning.value = false

                if (!hadError) {
                    showSavedNotificationThenDismiss()
                    Toast.makeText(this, "Background video saved", Toast.LENGTH_SHORT).show()
                } else {
                    finishServiceNow()
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Stopping & saving
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Single stop entry point used by BOTH the in-app "Stop Background Front
     * Recording" button and the notification's [Stop Recording] action.
     * Calling [Recording.stop] triggers a Finalize event, which saves the
     * file and swaps the notification.
     */
    private fun stopRecordingAndSave() {
        val recording = activeRecording
        if (recording == null || isFinalizing) {
            // Nothing to record / already tearing down.
            stopSelf()
            return
        }
        isFinalizing = true
        try {
            recording.stop()
        } catch (e: Exception) {
            Log.e(TAG, "Stop failed — forcing teardown", e)
            finishServiceNow()
        }
    }

    /**
     * Replaces the live notification with "Recording saved", keeps it for
     * exactly 3 seconds, then cancels it and stops the service.
     */
    private fun showSavedNotificationThenDismiss() {
        isFinalizing = true

        // Detach from foreground but keep the process alive briefly.
        stopForeground(STOP_FOREGROUND_REMOVE)

        val saved = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.bg_notification_saved_title))
            .setContentText(getString(R.string.bg_notification_saved_content))
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(false)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, saved)

        dismissTimer = object : CountDownTimer(SAVED_NOTIFICATION_DURATION_MS, 500L) {
            override fun onTick(millisUntilFinished: Long) = Unit
            override fun onFinish() {
                NotificationManagerCompat.from(this@BackgroundVideoRecordingService)
                    .cancel(NOTIFICATION_ID)
                stopSelf()
            }
        }.start()
    }

    /** Immediate teardown used on unrecoverable errors. */
    private fun finishServiceNow() {
        NotificationManagerCompat.from(this).cancel(NOTIFICATION_ID)
        stopSelf()
    }

    // ─────────────────────────────────────────────────────────────────────
    // Cleanup
    // ─────────────────────────────────────────────────────────────────────

    override fun onDestroy() {
        // Make sure the camera is always released, even on abnormal teardown.
        runCatching { activeRecording?.stop() }
        runCatching { cameraProvider?.unbindAll() }
        activeRecording = null
        videoCapture = null
        cameraProvider = null
        dismissTimer?.cancel()
        dismissTimer = null
        _isRunning.value = false
        dispatcher.onServicePreSuperOnDestroy()
        super.onDestroy()
    }

    /** CameraX callbacks must run on the main thread. */
    private val mainExecutor: Executor
        get() = ContextCompat.getMainExecutor(this)

    private companion object {
        const val REQUEST_CODE_OPEN_APP = 10
        const val REQUEST_CODE_STOP = 11
        const val SAVED_NOTIFICATION_DURATION_MS = 3_000L

        /** Alias for readability; available since minSdk 26. */
        const val STOP_FOREGROUND_REMOVE = Service.STOP_FOREGROUND_REMOVE
    }
}
