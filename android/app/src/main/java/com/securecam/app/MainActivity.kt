package com.securecam.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.securecam.app.ui.CameraScreen
import com.securecam.app.ui.GalleryScreen
import com.securecam.app.ui.theme.Ink
import com.securecam.app.ui.theme.Paper
import com.securecam.app.ui.theme.SecureCamTheme
import com.securecam.app.util.PermissionUtils

/**
 * Single-activity host for the Compose UI.
 *
 * Routes:
 *  - "camera"  : full-screen viewfinder (start destination)
 *  - "gallery" : in-app MediaStore gallery
 *
 * Permissions: requested up-front with the modern [ActivityResultContracts
 * .RequestMultiplePermissions] API before the NavHost is shown. Camera +
 * microphone are mandatory; notification permission is requested on
 * Android 13+ because the background-recording service legally MUST be
 * able to show its persistent notification.
 */
class MainActivity : ComponentActivity() {

    private var permissionsGranted by mutableStateOf(false)
    private var secondaryRequested = false

    private lateinit var permissionLauncher: ActivityResultLauncher<Array<String>>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        permissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
                // Camera is the hard requirement; audio can be declined
                // (recording then proceeds without sound).
                permissionsGranted = result[Manifest.permission.CAMERA] == true
                // Once the camera is in, quietly ask for audio + notifications
                // (13+) so video sound and the background-service notification
                // both work without another interruption later.
                if (permissionsGranted && !secondaryRequested) {
                    secondaryRequested = true
                    requestSecondaryPermissions()
                }
            }

        permissionsGranted = PermissionUtils.hasCameraPermission(this)

        setContent {
            SecureCamTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    if (permissionsGranted) {
                        AppNavigation()
                    } else {
                        PermissionGate(
                            onRequest = { requestCameraPermissions() },
                        )
                    }
                }
            }
        }

        // Kick off the first request immediately.
        if (!permissionsGranted) requestCameraPermissions()
    }

    /** Camera first, then audio + notifications in a follow-up batch. */
    private fun requestCameraPermissions() {
        permissionLauncher.launch(PermissionUtils.CAMERA_PERMISSIONS)
    }

    /**
     * Requests everything else the app can use: audio for video sound, and
     * POST_NOTIFICATIONS on 13+ so the background service notification can
     * be displayed (privacy rule — never silent recording).
     */
    private fun requestSecondaryPermissions() {
        val wanted = buildList {
            addAll(PermissionUtils.AUDIO_PERMISSION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        permissionLauncher.launch(wanted.toTypedArray())
    }
}

/** Minimal permission explainer shown before the camera is available. */
@Composable
private fun PermissionGate(onRequest: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "SecureCam needs the camera to work.",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Recording with sound also uses the microphone. " +
                "Background recording always shows a notification.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onRequest,
            shape = RoundedCornerShape(999.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Ink,
                contentColor = Paper,
            ),
        ) {
            Text("Grant camera access")
        }
    }
}

/** Nav graph: camera ⇄ gallery. */
@Composable
private fun AppNavigation() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = ROUTE_CAMERA,
    ) {
        composable(ROUTE_CAMERA) {
            CameraScreen(
                onOpenGallery = {
                    navController.navigate(ROUTE_GALLERY) { launchSingleTop = true }
                },
            )
        }
        composable(ROUTE_GALLERY) {
            GalleryScreen(
                onBack = { navController.popBackStack() },
            )
        }
    }
}

private const val ROUTE_CAMERA = "camera"
private const val ROUTE_GALLERY = "gallery"
