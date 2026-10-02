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
