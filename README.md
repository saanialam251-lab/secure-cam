# SecureCam — Camera with Background Recording

A production-ready Android camera app built with **Kotlin**, **Jetpack Compose (Material 3)**,
**CameraX**, and **MVVM + Clean Architecture**. Styled with a restrained **Minimalism** theme:
near-monochrome ink/paper palette, hairline dividers, generous whitespace, and a single
recording-red accent.

- **minSdk 26** (Android 8.0) · **targetSdk 35** (Android 15)
- Photos → `Pictures/SecureCam` · Videos → `Movies/SecureCam` (visible in the system gallery)
- Background recordings → `Movies/SecureCam/BG_<timestamp>.mp4`

---

## Features

### A. Normal camera
| Feature | Where |
|---|---|
| Full-screen live preview (CameraX `Preview`) | `ui/CameraScreen.kt` |
| High-quality photo capture (`ImageCapture`, MAXIMIZE_QUALITY) | `viewmodel/CameraViewModel.kt` |
| Video recording with audio, front & back (`VideoCapture` + `Recorder`) | `viewmodel/CameraViewModel.kt` |
| Front/back switch | top/bottom bar → `switchLensFacing()` |
| Flash AUTO / ON / OFF, torch for video | `cycleFlashMode()` → `enableTorch()` |
| Pinch-to-zoom (up to hardware max, spec target 10x) | `onPinchZoom()` |
| Tap-to-focus with indicator ring + auto-cancel metering | `onTapFocus()` |
| Self-timer Off / 3s / 5s / 10s (tap countdown to cancel) | `onShutterPressed()` |
| 3×3 grid overlay toggle | `toggleGrid()` |
| Aspect ratio 4:3 / 16:9 / 1:1 (1:1 via shared ViewPort) | `setAspectRatio()` |
| Recording timer pill + shutter press animation | `RecordingPill` / `BottomBar` |
| MediaStore persistence (gallery-visible) | `util/FileUtils.kt` |
| In-app gallery: grid, preview, video playback, delete, share | `ui/GalleryScreen.kt` |
| Runtime permissions incl. Android 13+ granular media + notifications | `util/PermissionUtils.kt` |

### B. Background front-camera recording (privacy-first)
Implemented in `service/BackgroundVideoRecordingService.kt` as a **foreground service**
(`foregroundServiceType="camera|microphone"`).

- **Start / Stop buttons** live in the camera UI; *Stop* is only visible while recording.
- **Persistent notification posted immediately** in `onCreate()`, before any camera work:
  - Title: `SecureCam - Background Recording Active`
  - Content: `Front camera is recording video. Tap to open app and stop.`
  - `setOngoing(true)` — cannot be swiped away
  - Recording icon + elapsed-time **chronometer**
  - Action buttons: **Stop Recording** · **Open App**
  - `IMPORTANCE_LOW` channel (no heads-up interruption, always in the shade)
- Recording continues while the app is minimized, the screen is locked, or other apps are open.
- **Stopping** happens only via the in-app button or the notification action. The file is
  finalized, the notification swaps to **“Recording saved”** for exactly 3 seconds, then is
  dismissed, and a *“Background video saved”* toast is shown.
- **Privacy rule enforced in code**: there is no code path that records without the visible
  notification — `promoteToForeground()` runs before camera binding, and missing notification
  permission aborts the service rather than ever recording silently.

---

## Project layout (Clean Architecture)

```
android/app/src/main/java/com/securecam/app/
├── MainActivity.kt                  # Nav host (camera ⇄ gallery) + permission gate
├── SecureCamApp.kt                  # Notification channel setup
├── service/
│   └── BackgroundVideoRecordingService.kt   # Foreground service + CameraX + notification
├── viewmodel/
│   ├── CameraViewModel.kt           # Camera state, use cases, gestures, recording
│   └── GalleryViewModel.kt          # MediaStore queries, delete
├── ui/
│   ├── CameraScreen.kt              # Full viewfinder UI
│   ├── GalleryScreen.kt             # Grid + preview + share/delete
│   └── theme/                       # Minimalism Material 3 theme
└── util/
    ├── PermissionUtils.kt
    └── FileUtils.kt                 # MediaStore helpers, name/timestamp formatting
```

## Build

### Locally
```bash
cd android
./gradlew assembleDebug       # app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease     # unsigned release APK
```

### With GitHub Actions (automatic APK)
`.github/workflows/android-apk.yml` builds **debug + release APKs** on every push/PR that
touches `android/**` and uploads them as workflow artifacts. Pushing a tag like `v1.0.0`
additionally attaches the APKs to a GitHub Release.

To get an APK: open your repo → **Actions** → latest *Build SecureCam APK* run →
**Artifacts** → download `SecureCam-debug-apk`.

> The release APK is unsigned (no keystore in the repo, by design). Add a signing step with
> repo secrets if you want signed releases.

## Permissions rationale
| Permission | Why |
|---|---|
| `CAMERA` | Core camera + background front recording |
| `RECORD_AUDIO` | Video with sound (declinable — recording continues muted) |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_CAMERA/MICROPHONE` | Android 14+ typed FGS |
| `POST_NOTIFICATIONS` | Android 13+ — required to show the mandatory recording notification |
| `READ_MEDIA_IMAGES/VIDEO` (13+) · `READ/WRITE_EXTERNAL_STORAGE` (≤12) | In-app gallery + MediaStore writes on older APIs |

## Notes
- Rotation: the activity opts out of recreation (`configChanges`) and the ViewModel owns
  camera objects; `PreviewView` follows display rotation, so the preview stays stable.
- `local.properties` is generated by Android Studio / CI and is not committed.
