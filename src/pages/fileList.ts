/**
 * Metadata for the /downloads listing — paths, labels and groups only.
 *
 * The actual file contents are NOT inlined here. The whole project is
 * copied into public/source/ and served as real static files, so downloads
 * are plain HTTP links to /source/<exact repo path>. The listing below must
 * match that copy (see the sync check in Downloads.tsx).
 */

export type GroupKey = "kotlin" | "resources" | "gradle" | "ci" | "web";

export interface ProjectFile {
  /** Exact repository path (also the path under /source/). */
  path: string;
  label: string;
  group: GroupKey;
  /** Badge text, e.g. "kt", "xml". */
  lang: string;
}

export const GROUPS: { key: GroupKey; title: string; blurb: string }[] = [
  {
    key: "kotlin",
    title: "Kotlin sources",
    blurb: "MVVM + CameraX code — activity, service, viewmodels, UI, theme, utils",
  },
  {
    key: "resources",
    title: "Manifest & resources",
    blurb: "Permissions, notification strings, icons, XML theme, FileProvider paths",
  },
  {
    key: "gradle",
    title: "Gradle build system",
    blurb: "Build scripts, dependency pins, R8 rules, wrapper",
  },
  {
    key: "ci",
    title: "CI & repo",
    blurb: "GitHub Actions APK workflow, .gitignore, README",
  },
  {
    key: "web",
    title: "Web workspace",
    blurb: "The project site you're looking at",
  },
];

export const PROJECT_FILES: ProjectFile[] = [
  // ── Kotlin sources ──────────────────────────────────────────────────────
  {
    path: "android/app/src/main/java/com/securecam/app/MainActivity.kt",
    label: "MainActivity — nav host, permission gate",
    group: "kotlin",
    lang: "kt",
  },
  {
    path: "android/app/src/main/java/com/securecam/app/SecureCamApp.kt",
    label: "SecureCamApp — notification channel setup",
    group: "kotlin",
    lang: "kt",
  },
  {
    path: "android/app/src/main/java/com/securecam/app/service/BackgroundVideoRecordingService.kt",
    label: "BackgroundVideoRecordingService — camera|mic foreground service",
    group: "kotlin",
    lang: "kt",
  },
  {
    path: "android/app/src/main/java/com/securecam/app/viewmodel/CameraViewModel.kt",
    label: "CameraViewModel — camera state, gestures, Pro-mode interop",
    group: "kotlin",
    lang: "kt",
  },
  {
    path: "android/app/src/main/java/com/securecam/app/viewmodel/GalleryViewModel.kt",
    label: "GalleryViewModel — MediaStore queries, delete",
    group: "kotlin",
    lang: "kt",
  },
  {
    path: "android/app/src/main/java/com/securecam/app/ui/CameraScreen.kt",
    label: "CameraScreen — viewfinder, gestures, Pro panel host",
    group: "kotlin",
    lang: "kt",
  },
  {
    path: "android/app/src/main/java/com/securecam/app/ui/GalleryScreen.kt",
    label: "GalleryScreen — grid, preview, share, delete",
    group: "kotlin",
    lang: "kt",
  },
  {
    path: "android/app/src/main/java/com/securecam/app/ui/ProControls.kt",
    label: "ProControls — manual ISO/shutter/WB/focus/EV + histogram",
    group: "kotlin",
    lang: "kt",
  },
  {
    path: "android/app/src/main/java/com/securecam/app/ui/theme/Color.kt",
    label: "Theme colors — Ink/Paper palette",
    group: "kotlin",
    lang: "kt",
  },
  {
    path: "android/app/src/main/java/com/securecam/app/ui/theme/Type.kt",
    label: "Theme typography — minimalism type scale",
    group: "kotlin",
    lang: "kt",
  },
  {
    path: "android/app/src/main/java/com/securecam/app/ui/theme/Theme.kt",
    label: "SecureCamTheme — Material 3 wiring",
    group: "kotlin",
    lang: "kt",
  },
  {
    path: "android/app/src/main/java/com/securecam/app/util/PermissionUtils.kt",
    label: "PermissionUtils — version-aware permission sets",
    group: "kotlin",
    lang: "kt",
  },
  {
    path: "android/app/src/main/java/com/securecam/app/util/FileUtils.kt",
    label: "FileUtils — MediaStore helpers, naming, formatting",
    group: "kotlin",
    lang: "kt",
  },

  // ── Manifest & resources ────────────────────────────────────────────────
  {
    path: "android/app/src/main/AndroidManifest.xml",
    label: "AndroidManifest — all permissions + FGS types",
    group: "resources",
    lang: "xml",
  },
  {
    path: "android/app/src/main/res/values/strings.xml",
    label: "strings.xml — notification + UI strings",
    group: "resources",
    lang: "xml",
  },
  {
    path: "android/app/src/main/res/values/colors.xml",
    label: "colors.xml — minimalism palette",
    group: "resources",
    lang: "xml",
  },
  {
    path: "android/app/src/main/res/values/themes.xml",
    label: "themes.xml — launch theme",
    group: "resources",
    lang: "xml",
  },
  {
    path: "android/app/src/main/res/drawable/ic_notification.xml",
    label: "ic_notification — monochrome recording icon",
    group: "resources",
    lang: "xml",
  },
  {
    path: "android/app/src/main/res/drawable/ic_launcher_foreground.xml",
    label: "launcher foreground — lens artwork",
    group: "resources",
    lang: "xml",
  },
  {
    path: "android/app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml",
    label: "adaptive launcher icon",
    group: "resources",
    lang: "xml",
  },
  {
    path: "android/app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml",
    label: "adaptive launcher icon (round)",
    group: "resources",
    lang: "xml",
  },
  {
    path: "android/app/src/main/res/xml/file_paths.xml",
    label: "file_paths.xml — FileProvider paths",
    group: "resources",
    lang: "xml",
  },

  // ── Gradle build system ────────────────────────────────────────────────
  {
    path: "android/app/build.gradle.kts",
    label: "app build.gradle.kts — deps, SDK levels, R8",
    group: "gradle",
    lang: "kts",
  },
  {
    path: "android/build.gradle.kts",
    label: "root build.gradle.kts — plugin pins",
    group: "gradle",
    lang: "kts",
  },
  {
    path: "android/settings.gradle.kts",
    label: "settings.gradle.kts — repositories, modules",
    group: "gradle",
    lang: "kts",
  },
  {
    path: "android/gradle.properties",
    label: "gradle.properties — JVM/AndroidX flags",
    group: "gradle",
    lang: "props",
  },
  {
    path: "android/app/proguard-rules.pro",
    label: "proguard-rules.pro — R8 config",
    group: "gradle",
    lang: "pro",
  },
  {
    path: "android/gradle/wrapper/gradle-wrapper.properties",
    label: "gradle-wrapper.properties — Gradle 8.9 pin",
    group: "gradle",
    lang: "props",
  },
  {
    path: "android/gradle/wrapper/gradle-wrapper.jar",
    label: "gradle-wrapper.jar — wrapper binary",
    group: "gradle",
    lang: "jar",
  },
  {
    path: "android/gradlew",
    label: "gradlew — Unix wrapper script",
    group: "gradle",
    lang: "sh",
  },
  {
    path: "android/gradlew.bat",
    label: "gradlew.bat — Windows wrapper script",
    group: "gradle",
    lang: "bat",
  },

  // ── CI & repo ──────────────────────────────────────────────────────────
  {
    path: ".github/workflows/android-apk.yml",
    label: "GitHub Actions — build APK workflow",
    group: "ci",
    lang: "yml",
  },
  {
    path: ".gitignore",
    label: ".gitignore — Android + web ignores",
    group: "ci",
    lang: "git",
  },
  {
    path: "README.md",
    label: "README — features, privacy, build guide",
    group: "ci",
    lang: "md",
  },

  // ── Web workspace (key files) ──────────────────────────────────────────
  {
    path: "index.html",
    label: "index.html — site entry",
    group: "web",
    lang: "html",
  },
  {
    path: "package.json",
    label: "package.json — web dependencies",
    group: "web",
    lang: "json",
  },
  {
    path: "src/main.tsx",
    label: "main.tsx — router + providers",
    group: "web",
    lang: "tsx",
  },
  {
    path: "src/index.css",
    label: "index.css — Tailwind theme tokens",
    group: "web",
    lang: "css",
  },
  {
    path: "src/pages/Landing.tsx",
    label: "Landing.tsx — project home page",
    group: "web",
    lang: "tsx",
  },
  {
    path: "src/pages/Downloads.tsx",
    label: "Downloads.tsx — this page",
    group: "web",
    lang: "tsx",
  },
  {
    path: "src/pages/fileList.ts",
    label: "fileList.ts — download listing manifest",
    group: "web",
    lang: "ts",
  },
];
