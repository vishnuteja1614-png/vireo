package com.vireo.editor.ui.editor

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import com.vireo.editor.data.*
import com.vireo.editor.engine.ExportState
import com.vireo.editor.engine.VideoExporter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@UnstableApi
class EditorViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = MediaRepository(app)
    private val exporter = VideoExporter(app)

    private val _project = MutableStateFlow(Project())
    val project: StateFlow<Project> = _project.asStateFlow()

    private val _selectedClipId = MutableStateFlow<String?>(null)
    val selectedClipId: StateFlow<String?> = _selectedClipId.asStateFlow()

    private val _playheadMs = MutableStateFlow(0L)
    val playheadMs: StateFlow<Long> = _playheadMs.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _gallery = MutableStateFlow<List<MediaItem>>(emptyList())
    val gallery: StateFlow<List<MediaItem>> = _gallery.asStateFlow()

    private val _picked = MutableStateFlow<List<MediaItem>>(emptyList())
    val picked: StateFlow<List<MediaItem>> = _picked.asStateFlow()

    private val _exportState = MutableStateFlow<ExportState>(ExportState.Idle)
    val exportState: StateFlow<ExportState> = _exportState.asStateFlow()

    private val _settings = MutableStateFlow(ExportSettings())
    val settings: StateFlow<ExportSettings> = _settings.asStateFlow()

    // undo / redo
    private val undoStack = ArrayDeque<Project>()
    private val redoStack = ArrayDeque<Project>()

    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()
    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()

    private fun syncHistoryFlags() {
        _canUndo.value = undoStack.isNotEmpty()
        _canRedo.value = redoStack.isNotEmpty()
    }

    private fun mutate(block: (Project) -> Project) {
        undoStack.addLast(_project.value)
        if (undoStack.size > 50) undoStack.removeFirst()
        redoStack.clear()
        _project.update { block(it).copy(updatedAt = System.currentTimeMillis()) }
        syncHistoryFlags()
    }

    fun undo() {
        undoStack.removeLastOrNull()?.let { redoStack.addLast(_project.value); _project.value = it }
        syncHistoryFlags()
    }

    fun redo() {
        redoStack.removeLastOrNull()?.let { undoStack.addLast(_project.value); _project.value = it }
        syncHistoryFlags()
    }

    // ---- gallery / picker ----
    fun loadGallery(kind: MediaKind = MediaKind.VIDEO) = viewModelScope.launch {
        _gallery.value = when (kind) {
            MediaKind.VIDEO -> repo.loadVideos()
            MediaKind.IMAGE -> repo.loadImages()
            MediaKind.AUDIO -> repo.loadAudio()
        }
    }

    fun togglePick(item: MediaItem) {
        _picked.update { list -> if (list.any { it.uri == item.uri }) list.filterNot { it.uri == item.uri } else list + item }
    }

    fun clearPicks() { _picked.value = emptyList() }

    fun addPickedToTimeline() {
        val toAdd = _picked.value.map { Clip(media = it, trimEndMs = it.durationMs) }
        mutate { it.copy(clips = it.clips + toAdd) }
        _picked.value = emptyList()
        _selectedClipId.value = _project.value.clips.lastOrNull()?.id
    }

    // ---- timeline ops ----
    fun selectClip(id: String?) { _selectedClipId.value = id }

    // ---- direct manipulation of text on the canvas ----

    private val _selectedTextId = MutableStateFlow<String?>(null)
    val selectedTextId: StateFlow<String?> = _selectedTextId.asStateFlow()

    fun selectText(id: String?) { _selectedTextId.value = id }

    /**
     * Drag, pinch and twist in one update.
     *
     * Gestures arrive continuously, so this deliberately does NOT push an undo
     * step per frame - that would flood the 50-step history and make undo
     * useless. [commitGesture] records one step when the finger lifts.
     */
    fun transformText(id: String, dxFraction: Float, dyFraction: Float, zoom: Float, rotation: Float) {
        _project.value = _project.value.copy(
            texts = _project.value.texts.map { t ->
                if (t.id != id) t else t.copy(
                    xFraction = (t.xFraction + dxFraction).coerceIn(0f, 1f),
                    yFraction = (t.yFraction + dyFraction).coerceIn(0f, 1f),
                    sizeSp = (t.sizeSp * zoom).coerceIn(8f, 200f),
                    rotationDeg = t.rotationDeg + rotation
                )
            }
        )
    }

    /** Records a single undo step after a gesture finishes. */
    fun commitGesture() = mutate { it }

    /**
     * Reverse the selected clip.
     *
     * Media3 cannot do this, so FFmpeg pre-renders a reversed file and the
     * clip is re-pointed at it. The original media is untouched.
     */
    fun reverseClip(context: android.content.Context, clipId: String, onResult: (String) -> Unit) {
        val clip = _project.value.clips.firstOrNull { it.id == clipId } ?: run {
            onResult("Select a clip first"); return
        }
        viewModelScope.launch {
            onResult("Reversing...")
            val result = com.vireo.editor.engine.FfmpegEngine.reverse(
                context, clip.media.uri, clip.trimStartMs, clip.trimEndMs
            )
            result.fold(
                onSuccess = { file ->
                    val dur = clip.sourceDurationMs
                    updateClip(clipId) {
                        it.copy(
                            media = it.media.copy(
                                uri = android.net.Uri.fromFile(file),
                                durationMs = dur,
                                name = it.media.name + " (reversed)",
                                sizeBytes = file.length()
                            ),
                            trimStartMs = 0L,
                            trimEndMs = dur
                        )
                    }
                    onResult("Clip reversed")
                },
                onFailure = { onResult(it.message ?: "Reverse failed") }
            )
        }
    }

    /**
     * Remove the background from the selected clip with on-device AI.
     *
     * No green screen needed. Rendered to a new file, so the original media
     * is untouched and the result edits like any other clip.
     */
    fun removeBackgroundAi(
        context: android.content.Context,
        clipId: String,
        backgroundRgb: Int = 0x000000,
        onResult: (String) -> Unit
    ) {
        val clip = _project.value.clips.firstOrNull { it.id == clipId } ?: run {
            onResult("Select a clip first"); return
        }
        viewModelScope.launch {
            onResult("AI removing background...")
            val result = com.vireo.editor.engine.AiBackgroundRemover.removeBackground(
                context = context,
                input = clip.media.uri,
                trimStartMs = clip.trimStartMs,
                trimEndMs = clip.trimEndMs,
                backgroundRgb = backgroundRgb
            )
            result.fold(
                onSuccess = { file ->
                    val dur = clip.sourceDurationMs
                    updateClip(clipId) {
                        it.copy(
                            media = it.media.copy(
                                uri = android.net.Uri.fromFile(file),
                                durationMs = dur,
                                name = it.media.name + " (no bg)",
                                sizeBytes = file.length()
                            ),
                            trimStartMs = 0L,
                            trimEndMs = dur
                        )
                    }
                    onResult("Background removed")
                },
                onFailure = { onResult(it.message ?: "Background removal failed") }
            )
        }
    }

    /**
     * Freeze the frame at the playhead and insert it as a still image clip.
     *
     * The frame is decoded with MediaMetadataRetriever and written to the
     * cache as a PNG, then added to the timeline as a normal image clip, so
     * every existing effect, filter and transition applies to it unchanged.
     *
     * @return true if a frame was captured
     */
    fun freezeFrame(context: android.content.Context, holdMs: Long = 2000L): Boolean {
        val playhead = _playheadMs.value
        // Find the clip under the playhead and convert to a source timestamp.
        var acc = 0L
        var target: Clip? = null
        var offsetIntoClip = 0L
        for (c in _project.value.clips) {
            val d = c.outputDurationMs
            if (playhead < acc + d) { target = c; offsetIntoClip = playhead - acc; break }
            acc += d
        }
        val clip = target ?: _project.value.clips.lastOrNull() ?: return false
        if (clip.media.kind == MediaKind.AUDIO) return false

        val sourceUs = ((clip.trimStartMs + offsetIntoClip * clip.speed).toLong()) * 1000L

        val bitmap = runCatching {
            android.media.MediaMetadataRetriever().use { r ->
                r.setDataSource(context, clip.media.uri)
                r.getFrameAtTime(sourceUs, android.media.MediaMetadataRetriever.OPTION_CLOSEST)
            }
        }.getOrNull() ?: return false

        val file = java.io.File(context.cacheDir, "freeze_${System.currentTimeMillis()}.png")
        runCatching {
            java.io.FileOutputStream(file).use { out ->
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
            }
        }.getOrElse { return false }

        val frozen = Clip(
            media = MediaItem(
                uri = android.net.Uri.fromFile(file),
                kind = MediaKind.IMAGE,
                durationMs = holdMs,
                name = "Freeze frame",
                sizeBytes = file.length()
            ),
            trimStartMs = 0L,
            trimEndMs = holdMs
        )

        // Insert directly after the clip it was taken from, like a desktop NLE.
        val index = _project.value.clips.indexOfFirst { it.id == clip.id }
        mutate { p ->
            val list = p.clips.toMutableList()
            list.add((index + 1).coerceIn(0, list.size), frozen)
            p.copy(clips = list)
        }
        _selectedClipId.value = frozen.id
        return true
    }

    /** Start a clean timeline. Without this, every "new project" reused the old one. */
    fun newProject() {
        _project.value = Project()
        _selectedClipId.value = null
        _playheadMs.value = 0L
        _picked.value = emptyList()
    }

    /** Reopen a saved project from the Recent list. */
    fun openProject(p: Project) {
        _project.value = p
        _selectedClipId.value = p.clips.firstOrNull()?.id
        _playheadMs.value = 0L
    }

    /**
     * Quick "Compress" preset: drop to 720p and a low bitrate, which is what
     * people actually want when they tap Compress (smaller file, same length).
     */
    fun applyCompressPreset() {
        _settings.update { it.copy(resolution = Resolution.P720, bitrateMbps = 3) }
    }

    fun updateClip(id: String, block: (Clip) -> Clip) =
        mutate { p -> p.copy(clips = p.clips.map { if (it.id == id) block(it) else it }) }

    fun deleteClip(id: String) {
        mutate { p -> p.copy(clips = p.clips.filterNot { it.id == id }) }
        if (_selectedClipId.value == id) _selectedClipId.value = null
    }

    fun duplicateClip(id: String) = mutate { p ->
        val i = p.clips.indexOfFirst { it.id == id }
        if (i < 0) p else {
            val copy = p.clips[i].copy(id = java.util.UUID.randomUUID().toString())
            p.copy(clips = p.clips.toMutableList().apply { add(i + 1, copy) })
        }
    }

    fun moveClip(from: Int, to: Int) = mutate { p ->
        val l = p.clips.toMutableList()
        if (from in l.indices && to in l.indices) l.add(to, l.removeAt(from))
        p.copy(clips = l)
    }

    /** Split the selected clip at the current playhead. */
    fun splitAtPlayhead() {
        val p = _project.value
        var acc = 0L
        for (clip in p.clips) {
            val end = acc + clip.outputDurationMs
            if (_playheadMs.value in (acc + 200)..(end - 200)) {
                val offsetInSource = ((_playheadMs.value - acc) * clip.speed).toLong()
                val cut = clip.trimStartMs + offsetInSource
                val left = clip.copy(trimEndMs = cut)
                val right = clip.copy(id = java.util.UUID.randomUUID().toString(), trimStartMs = cut)
                mutate { pr ->
                    val l = pr.clips.toMutableList()
                    val i = l.indexOfFirst { it.id == clip.id }
                    l[i] = left; l.add(i + 1, right)
                    pr.copy(clips = l)
                }
                _selectedClipId.value = right.id
                return
            }
            acc = end
        }
    }

    fun setTrim(id: String, startMs: Long, endMs: Long) = updateClip(id) {
        it.copy(
            trimStartMs = startMs.coerceIn(0, it.media.durationMs),
            trimEndMs = endMs.coerceIn(0, it.media.durationMs)
        )
    }

    fun setSpeed(id: String, speed: Float) = updateClip(id) { it.copy(speed = speed.coerceIn(0.25f, 4f)) }
    fun setFilter(id: String, f: FilterPreset) = updateClip(id) { it.copy(filter = f) }

    // ---- framing, colour and keying ----
    fun setPanZoom(id: String, p: PanZoom) = updateClip(id) { it.copy(panZoom = p) }

    fun setCanvasBack(rgb: Int) = mutate { it.copy(canvasBackRgb = rgb, canvasFill = CanvasFill.COLOR) }

    fun setCanvasFill(f: CanvasFill) = mutate { it.copy(canvasFill = f) }

    fun setLumaWipe(id: String, patternId: String) = updateClip(id) { it.copy(lumaWipeId = patternId) }

    fun setLumaTuning(id: String, softness: Float, invert: Boolean) = updateClip(id) {
        it.copy(lumaSoftness = softness.coerceIn(0f, 1f), lumaInvert = invert)
    }

    /** Replace all text overlays with cues parsed from an SRT/VTT file. */
    fun importSubtitles(raw: String): Int {
        val cues = SubtitleIo.parse(raw)
        if (cues.isNotEmpty()) mutate { it.copy(texts = cues) }
        return cues.size
    }

    /** Turn a script into timed caption cues spread across the timeline. */
    fun captionsFromScript(script: String, wordsPerCue: Int = 4): Int {
        val total = _project.value.totalDurationMs
        val cues = SubtitleIo.fromScript(script, if (total > 0) total else 15_000L, wordsPerCue)
        if (cues.isNotEmpty()) mutate { it.copy(texts = cues) }
        return cues.size
    }

    fun subtitlesAsSrt(): String = SubtitleIo.toSrt(_project.value.texts)

    fun shiftSubtitles(deltaMs: Long) =
        mutate { it.copy(texts = SubtitleIo.shift(it.texts, deltaMs)) }

    fun setLut(id: String, lutId: String) = updateClip(id) { it.copy(lutId = lutId) }

    fun setCrop(id: String, l: Float, t: Float, r: Float, b: Float) = updateClip(id) {
        it.copy(
            cropLeft = l.coerceIn(0f, 0.9f), cropTop = t.coerceIn(0f, 0.9f),
            cropRight = r.coerceIn(0.1f, 1f), cropBottom = b.coerceIn(0.1f, 1f)
        )
    }

    fun setChromaEnabled(id: String, on: Boolean) = updateClip(id) { it.copy(chromaKey = on) }

    fun setChromaColor(id: String, rgb: Int) = updateClip(id) { it.copy(chromaColorRgb = rgb) }

    fun setChromaBack(id: String, rgb: Int) = updateClip(id) { it.copy(chromaBackRgb = rgb) }

    fun setChromaTuning(id: String, similarity: Float, smoothness: Float, spill: Float) =
        updateClip(id) {
            it.copy(
                chromaSimilarity = similarity.coerceIn(0f, 1f),
                chromaSmoothness = smoothness.coerceIn(0.001f, 1f),
                chromaSpill = spill.coerceIn(0.001f, 1f)
            )
        }

    /** Manual colour grade, the controls a desktop editor exposes. */
    fun setGrade(id: String, brightness: Float, contrast: Float, saturation: Float) =
        updateClip(id) {
            it.copy(
                brightness = brightness.coerceIn(-1f, 1f),
                contrast = contrast.coerceIn(-1f, 1f),
                saturation = saturation.coerceIn(0f, 2f)
            )
        }
    fun setTransition(id: String, t: TransitionType, ms: Long) = updateClip(id) { it.copy(transitionIn = t, transitionMs = ms) }
    fun setTransitionPreset(id: String, def: TransitionDef) =
        updateClip(id) { it.copy(transitionId = def.id, transitionMs = def.defaultMs) }
    fun setCaptionStyle(styleId: String) = mutate { it.copy(captionStyleId = styleId) }

    /** Add an external audio file (e.g. an AI voiceover) to the project. */
    fun addVoiceoverFile(file: java.io.File) {
        val item = MediaItem(
            uri = android.net.Uri.fromFile(file),
            kind = MediaKind.AUDIO,
            durationMs = 0L,
            name = file.name,
            sizeBytes = file.length()
        )
        addAudio(AudioTrack(media = item, isVoiceover = true))
    }

    /** Drop a batch of AI caption chunks onto the text track, evenly spaced. */
    fun applyCaptionChunks(chunks: List<String>) {
        if (chunks.isEmpty()) return
        val total = _project.value.totalDurationMs.coerceAtLeast(1000L)
        val per = total / chunks.size
        val texts = chunks.mapIndexed { i, c ->
            TextOverlay(text = c, startMs = i * per, endMs = (i + 1) * per, yFraction = 0.78f)
        }
        mutate { it.copy(texts = it.texts + texts) }
    }
    fun setVolume(id: String, v: Float) = updateClip(id) { it.copy(volume = v.coerceIn(0f, 2f)) }
    fun setAspect(a: AspectRatio) = mutate { it.copy(aspect = a) }
    fun rename(name: String) = mutate { it.copy(name = name) }

    // ---- overlays ----
    fun addText(t: TextOverlay = TextOverlay(startMs = _playheadMs.value, endMs = _playheadMs.value + 3000)) =
        mutate { it.copy(texts = it.texts + t) }

    fun updateText(id: String, block: (TextOverlay) -> TextOverlay) =
        mutate { p -> p.copy(texts = p.texts.map { if (it.id == id) block(it) else it }) }

    fun removeText(id: String) = mutate { p -> p.copy(texts = p.texts.filterNot { it.id == id }) }

    fun addAudio(track: AudioTrack) = mutate { it.copy(audio = it.audio + track) }
    fun updateAudio(id: String, block: (AudioTrack) -> AudioTrack) =
        mutate { p -> p.copy(audio = p.audio.map { if (it.id == id) block(it) else it }) }
    fun removeAudio(id: String) = mutate { p -> p.copy(audio = p.audio.filterNot { it.id == id }) }

    // ---- playback ----
    fun setPlayhead(ms: Long) { _playheadMs.value = ms.coerceIn(0, _project.value.totalDurationMs) }
    fun setPlaying(v: Boolean) { _isPlaying.value = v }

    // ---- export ----
    fun updateSettings(block: (ExportSettings) -> ExportSettings) { _settings.update(block) }

    fun startExport() = viewModelScope.launch {
        exporter.export(_project.value, _settings.value).collect { _exportState.value = it }
    }

    fun cancelExport() { exporter.cancel(); _exportState.value = ExportState.Idle }
    fun resetExport() { _exportState.value = ExportState.Idle }

    fun estimatedSizeBytes(): Long {
        val s = _settings.value
        val seconds = _project.value.totalDurationMs / 1000.0
        return ((s.bitrateMbps * 1_000_000.0 / 8.0) * seconds).toLong()
    }
}
