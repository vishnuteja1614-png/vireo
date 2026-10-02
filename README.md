# Vireo — Android Video Editor

A full-featured, open-source-powered video editor for Android, built with **Kotlin + Jetpack Compose**
and the **AndroidX Media3** engine (ExoPlayer + Transformer, Apache-2.0).

## Features

| Area | What's implemented |
|---|---|
| Timeline | Multi-track (video / audio / text), zoom, scrub, playhead, drag trim handles |
| Clip ops | Split at playhead, duplicate, delete, reorder, trim, speed 0.25x–4x |
| Colour | 8 GPU filter presets + brightness / contrast / saturation (Media3 Effect pipeline) |
| Text | Overlays with position, size, colour, 6 animation presets |
| Audio | Per-clip volume, music tracks, fade in/out, mute, voiceover model |
| Transitions | 12 transition types with duration + easing |
| Playback | Real ExoPlayer preview with clipping configuration |
| Export | 720p → 4K, 24/30/60 fps, MP4 (H.264/AAC) or WebM (VP9/Opus), hardware accelerated |
| Output | Saves to `Movies/Vireo` via MediaStore, one-tap share sheet |
| Design | Dark Material 3, purple→cyan brand gradient, spring animations throughout |

## Architecture

```
app/src/main/java/com/vireo/editor/
├── MainActivity.kt           # Compose entry + Navigation graph
├── data/                     # Models, MediaStore repository, formatters
├── engine/
│   ├── FilterFactory.kt      # GPU colour effects (RgbMatrix / Brightness / Contrast / HSL)
│   └── VideoExporter.kt      # Media3 Transformer pipeline + progress Flow + gallery save
└── ui/
    ├── theme/                # Colour system + typography
    ├── Components.kt         # Reusable gradient button, chips, sliders
    ├── home/HomeScreen.kt
    ├── picker/MediaPickerScreen.kt
    ├── editor/               # EditorScreen, Timeline, ToolPanel, EditorViewModel
    └── export/ExportScreen.kt
```

Single `EditorViewModel` holds immutable `Project` state in a `StateFlow`,
with a 50-step undo/redo stack. UI is fully reactive — no manual refresh anywhere.

## Build the APK

### Option A — GitHub Actions (no Android Studio needed)
Push to `main`. The **Build APK** workflow produces downloadable artifacts:
`Actions → latest run → Artifacts → vireo-release-apk`.

### Option B — Locally
```bash
./gradlew :app:assembleRelease
# → app/build/outputs/apk/release/app-release.apk
```
Requires JDK 17 and Android SDK 34.

## Emulator testing
The **Emulator Test** workflow boots a Pixel 6 / Android 14 emulator on GitHub's
runners, installs the APK, launches it, and uploads a screenshot artifact.
Run it from the Actions tab (`workflow_dispatch`).

## Requirements
- minSdk 26 (Android 8.0) · targetSdk 34
- Kotlin 1.9.24 · AGP 8.5.2 · Compose BOM 2024.06
