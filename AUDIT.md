# Deep Audit — findings

| # | Severity | File | Bug | Fix |
|---|---|---|---|---|
| 1 | CRITICAL | VideoExporter | Text overlays NEVER exported — `project.texts` unused | Burn in via Media3 `OverlayEffect` + `TextOverlay` |
| 2 | CRITICAL | VideoExporter | Music/voiceover tracks NEVER exported — `project.audio` unused | Second `EditedMediaItemSequence` + `isLooping` mix |
| 3 | CRITICAL | VideoExporter | Clip `volume` ignored unless 0 | `ChannelMixingAudioProcessor` with gain matrix |
| 4 | CRITICAL | VideoExporter | Transitions ignored entirely | Crossfade via `AlphaScale`/overlay fade on sequence boundaries |
| 5 | HIGH | VideoExporter | Image clips crash (`setDurationUs` without frame rate) | `setFrameRate(30)` + duration, guard non-video |
| 6 | HIGH | VideoExporter | `bitrateMbps` and `fps` in UI do nothing | `VideoEncoderSettings` + `DefaultEncoderFactory` |
| 7 | HIGH | VideoExporter | Aspect ratio ignored, always source AR | `Presentation.createForWidthAndHeight` from `project.aspect` |
| 8 | HIGH | EditorViewModel | `canUndo`/`canRedo` are plain getters → buttons never recompose | Back with `MutableStateFlow` |
| 9 | HIGH | EditorScreen | Playhead uses `player.currentPosition` = per-item, not global timeline | Accumulate preceding clip durations |
| 10 | MED | Timeline | Playhead px math mixes dp/px with scroll → drifts when zoomed | Compute in px from density once |
| 11 | MED | EditorScreen | New ExoPlayer rebuilt on every clip edit → playback restarts | Key only on structural change |
| 12 | MED | MainActivity | `recent` projects lost on process death | Persist to DataStore |
| 13 | MED | ExportScreen | Progress ring can stick at 0 if Transformer reports late | Seed at 1%, clamp |
| 14 | LOW | MediaPicker | Audio tab shows video decoder thumbnails | Kind-aware placeholder |
| 15 | LOW | Workflow | `if/fi` in emulator script → sh syntax error | Single-line guard |
