/**
 * Project file manifest for the /downloads page.
 *
 * File contents are inlined at BUILD TIME via Vite raw imports — what you
 * download is byte-identical to what's in the repository, and paths can
 * never drift from the actual project structure.
 *
 * The only file not inlined is the binary `gradle-wrapper.jar` (raw text
 * imports would corrupt it). It is fetched from Gradle's official
 * repository when the ZIP is built, and added at its correct path.
 */
import mainActivity from "../../android/app/src/main/java/com/securecam/app/MainActivity.kt?raw";
import appClass from "../../android/app/src/main/java/com/securecam/app/SecureCamApp.kt?raw";
import service from "../../android/app/src/main/java/com/securecam/app/service/BackgroundVideoRecordingService.kt?raw";
import cameraVm from "../../android/app/src/main/java/com/securecam/app/viewmodel/CameraViewModel.kt?raw";
import galleryVm from "../../android/app/src/main/java/com/securecam/app/viewmodel/GalleryViewModel.kt?raw";
import cameraScreen from "../../android/app/src/main/java/com/securecam/app/ui/CameraScreen.kt?raw";
import galleryScreen from "../../android/app/src/main/java/com/securecam/app/ui/GalleryScreen.kt?raw";
import proControls from "../../android/app/src/main/java/com/securecam/app/ui/ProControls.kt?raw";
import themeColor from "../../android/app/src/main/java/com/securecam/app/ui/theme/Color.kt?raw";
import themeType from "../../android/app/src/main/java/com/securecam/app/ui/theme/Type.kt?raw";
import themeTheme from "../../android/app/src/main/java/com/securecam/app/ui/theme/Theme.kt?raw";
import permissionUtils from "../../android/app/src/main/java/com/securecam/app/util/PermissionUtils.kt?raw";
import fileUtils from "../../android/app/src/main/java/com/securecam/app/util/FileUtils.kt?raw";

import manifest from "../../android/app/src/main/AndroidManifest.xml?raw";
import stringsXml from "../../android/app/src/main/res/values/strings.xml?raw";
import colorsXml from "../../android/app/src/main/res/values/colors.xml?raw";
import themesXml from "../../android/app/src/main/res/values/themes.xml?raw";
import icNotification from "../../android/app/src/main/res/drawable/ic_notification.xml?raw";
import icLauncherFg from "../../android/app/src/main/res/drawable/ic_launcher_foreground.xml?raw";
import icLauncher from "../../android/app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml?raw";
import icLauncherRound from "../../android/app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml?raw";
import filePaths from "../../android/app/src/main/res/xml/file_paths.xml?raw";

import buildGradleApp from "../../android/app/build.gradle.kts?raw";
import buildGradleRoot from "../../android/build.gradle.kts?raw";
import settingsGradle from "../../android/settings.gradle.kts?raw";
import gradleProps from "../../android/gradle.properties?raw";
import proguard from "../../android/app/proguard-rules.pro?raw";
import wrapperProps from "../../android/gradle/wrapper/gradle-wrapper.properties?raw";
import gradlew from "../../android/gradlew?raw";
import gradlewBat from "../../android/gradlew.bat?raw";

import workflowYml from "../../.github/workflows/android-apk.yml?raw";
import gitignore from "../../.gitignore?raw";
import readme from "../../README.md?raw";

import indexHtml from "../../index.html?raw";
import mainTsx from "../../src/main.tsx?raw";
import indexCss from "../../src/index.css?raw";
import landing from "./Landing.tsx?raw";
import downloads from "./Downloads.tsx?raw";

/** One project file: its correct repository path + build-time-inlined content. */
export interface ProjectFile {
  path: string;
  content: string;
  /** Short human label for the file. */
  label: string;
  /** Group key for display. */
  group: GroupKey;
  /** Rough file type, for badges. */
  lang: "kotlin" | "xml" | "gradle" | "yaml" | "md" | "properties" | "tsx" | "html" | "css" | "svg";
}

export type GroupKey = "kotlin" | "resources" | "gradle" | "ci" | "web";

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
    blurb: "The project site you're looking at (landing + downloads)",
  },
];

export const PROJECT_FILES: ProjectFile[] = [
  // ── Kotlin sources ──────────────────────────────────────────────────────
  {
    path: "android/app/src/main/java/com/securecam/app/MainActivity.kt",
    content: mainActivity,
    label: "MainActivity — nav host, permission gate",
    group: "kotlin",
    lang: "kotlin",
  },
  {
    path: "android/app/src/main/java/com/securecam/app/SecureCamApp.kt",
    content: appClass,
    label: "SecureCamApp — notification channel setup",
    group: "kotlin",
    lang: "kotlin",
  },
  {
    path: "android/app/src/main/java/com/securecam/app/service/BackgroundVideoRecordingService.kt",
    content: service,
    label: "BackgroundVideoRecordingService — camera|mic foreground service",
    group: "kotlin",
    lang: "kotlin",
  },
  {
    path: "android/app/src/main/java/com/securecam/app/viewmodel/CameraViewModel.kt",
    content: cameraVm,
    label: "CameraViewModel — camera state, gestures, Pro-mode interop",
    group: "kotlin",
    lang: "kotlin",
  },
  {
    path: "android/app/src/main/java/com/securecam/app/viewmodel/GalleryViewModel.kt",
    content: galleryVm,
    label: "GalleryViewModel — MediaStore queries, delete",
    group: "kotlin",
    lang: "kotlin",
  },
  {
    path: "android/app/src/main/java/com/securecam/app/ui/CameraScreen.kt",
    content: cameraScreen,
    label: "CameraScreen — viewfinder, gestures, Pro panel host",
    group: "kotlin",
    lang: "kotlin",
  },
  {
    path: "android/app/src/main/java/com/securecam/app/ui/GalleryScreen.kt",
    content: galleryScreen,
    label: "GalleryScreen — grid, preview, share, delete",
    group: "kotlin",
    lang: "kotlin",
  },
  {
    path: "android/app/src/main/java/com/securecam/app/ui/ProControls.kt",
    content: proControls,
    label: "ProControls — manual ISO/shutter/WB/focus/EV + histogram",
    group: "kotlin",
    lang: "kotlin",
  },
  {
    path: "android/app/src/main/java/com/securecam/app/ui/theme/Color.kt",
    content: themeColor,
    label: "Theme colors — Ink/Paper palette",
    group: "kotlin",
    lang: "kotlin",
  },
  {
    path: "android/app/src/main/java/com/securecam/app/ui/theme/Type.kt",
    content: themeType,
    label: "Theme typography — minimalism type scale",
    group: "kotlin",
    lang: "kotlin",
  },
  {
    path: "android/app/src/main/java/com/securecam/app/ui/theme/Theme.kt",
    content: themeTheme,
    label: "SecureCamTheme — Material 3 wiring",
    group: "kotlin",
    lang: "kotlin",
  },
  {
    path: "android/app/src/main/java/com/securecam/app/util/PermissionUtils.kt",
    content: permissionUtils,
    label: "PermissionUtils — version-aware permission sets",
    group: "kotlin",
    lang: "kotlin",
  },
  {
    path: "android/app/src/main/java/com/securecam/app/util/FileUtils.kt",
    content: fileUtils,
    label: "FileUtils — MediaStore helpers, naming, formatting",
    group: "kotlin",
    lang: "kotlin",
  },

  // ── Manifest & resources ────────────────────────────────────────────────
  {
    path: "android/app/src/main/AndroidManifest.xml",
    content: manifest,
    label: "AndroidManifest — all permissions + FGS types",
    group: "resources",
    lang: "xml",
  },
  {
    path: "android/app/src/main/res/values/strings.xml",
    content: stringsXml,
    label: "strings.xml — notification + UI strings",
    group: "resources",
    lang: "xml",
  },
  {
    path: "android/app/src/main/res/values/colors.xml",
    content: colorsXml,
    label: "colors.xml — minimalism palette",
    group: "resources",
    lang: "xml",
  },
  {
    path: "android/app/src/main/res/values/themes.xml",
    content: themesXml,
    label: "themes.xml — launch theme",
    group: "resources",
    lang: "xml",
  },
  {
    path: "android/app/src/main/res/drawable/ic_notification.xml",
    content: icNotification,
    label: "ic_notification — monochrome recording icon",
    group: "resources",
    lang: "xml",
  },
  {
    path: "android/app/src/main/res/drawable/ic_launcher_foreground.xml",
    content: icLauncherFg,
    label: "launcher foreground — lens artwork",
    group: "resources",
    lang: "xml",
  },
  {
    path: "android/app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml",
    content: icLauncher,
    label: "adaptive launcher icon",
    group: "resources",
    lang: "xml",
  },
  {
    path: "android/app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml",
    content: icLauncherRound,
    label: "adaptive launcher icon (round)",
    group: "resources",
    lang: "xml",
  },
  {
    path: "android/app/src/main/res/xml/file_paths.xml",
    content: filePaths,
    label: "file_paths.xml — FileProvider paths",
    group: "resources",
    lang: "xml",
  },

  // ── Gradle build system ────────────────────────────────────────────────
  {
    path: "android/app/build.gradle.kts",
    content: buildGradleApp,
    label: "app build.gradle.kts — deps, SDK levels, R8",
    group: "gradle",
    lang: "gradle",
  },
  {
    path: "android/build.gradle.kts",
    content: buildGradleRoot,
    label: "root build.gradle.kts — plugin pins",
    group: "gradle",
    lang: "gradle",
  },
  {
    path: "android/settings.gradle.kts",
    content: settingsGradle,
    label: "settings.gradle.kts — repositories, modules",
    group: "gradle",
    lang: "gradle",
  },
  {
    path: "android/gradle.properties",
    content: gradleProps,
    label: "gradle.properties — JVM/AndroidX flags",
    group: "gradle",
    lang: "properties",
  },
  {
    path: "android/app/proguard-rules.pro",
    content: proguard,
    label: "proguard-rules.pro — R8 config",
    group: "gradle",
    lang: "properties",
  },
  {
    path: "android/gradle/wrapper/gradle-wrapper.properties",
    content: wrapperProps,
    label: "gradle-wrapper.properties — Gradle 8.9 pin",
    group: "gradle",
    lang: "properties",
  },
  {
    path: "android/gradlew",
    content: gradlew,
    label: "gradlew — Unix wrapper script",
    group: "gradle",
    lang: "properties",
  },
  {
    path: "android/gradlew.bat",
    content: gradlewBat,
    label: "gradlew.bat — Windows wrapper script",
    group: "gradle",
    lang: "properties",
  },

  // ── CI & repo ──────────────────────────────────────────────────────────
  {
    path: ".github/workflows/android-apk.yml",
    content: workflowYml,
    label: "GitHub Actions — build APK workflow",
    group: "ci",
    lang: "yaml",
  },
  {
    path: ".gitignore",
    content: gitignore,
    label: ".gitignore — Android + web ignores",
    group: "ci",
    lang: "properties",
  },
  {
    path: "README.md",
    content: readme,
    label: "README — features, privacy, build guide",
    group: "ci",
    lang: "md",
  },

  // ── Web workspace ──────────────────────────────────────────────────────
  {
    path: "index.html",
    content: indexHtml,
    label: "index.html — site entry",
    group: "web",
    lang: "html",
  },
  {
    path: "src/main.tsx",
    content: mainTsx,
    label: "main.tsx — router + providers",
    group: "web",
    lang: "tsx",
  },
  {
    path: "src/index.css",
    content: indexCss,
    label: "index.css — Tailwind theme tokens",
    group: "web",
    lang: "css",
  },
  {
    path: "src/pages/Landing.tsx",
    content: landing,
    label: "Landing.tsx — project home page",
    group: "web",
    lang: "tsx",
  },
  {
    path: "src/pages/Downloads.tsx",
    content: downloads,
    label: "Downloads.tsx — this page",
    group: "web",
    lang: "tsx",
  },
];

/** Binary wrapper JAR: fetched at ZIP-build time, never inlined as text. */
export const WRAPPER_JAR_URL =
  "https://raw.githubusercontent.com/gradle/gradle/v8.9.0/gradle/wrapper/gradle-wrapper.jar";
export const WRAPPER_JAR_PATH = "android/gradle/wrapper/gradle-wrapper.jar";
