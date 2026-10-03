package com.vireo.editor.ui.ai

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vireo.editor.ai.*
import com.vireo.editor.engine.TtsEngine
import com.vireo.editor.engine.EdgeTts
import com.vireo.editor.engine.EdgeVoices
import android.graphics.Bitmap
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.TimeZone

class AiViewModel(app: Application) : AndroidViewModel(app) {

    val config = AiConfig(app)
    val router = AiRouter(app, config)
    private val ai = ContentAi(router)
    private val tts = TtsEngine(app)
    private val edgeTts = EdgeTts()

    /** Selected neural voice id, persisted so a creator keeps their voice. */
    private val _voiceId = MutableStateFlow(config.voiceId)
    val voiceId = _voiceId.asStateFlow()

    private val _voiceRate = MutableStateFlow(0)
    val voiceRate = _voiceRate.asStateFlow()

    private val _voicePitch = MutableStateFlow(0)
    val voicePitch = _voicePitch.asStateFlow()

    /** Set when a render fell back to the robotic on-device engine. */
    private val _voiceNotice = MutableStateFlow<String?>(null)
    val voiceNotice = _voiceNotice.asStateFlow()

    val voices = EdgeVoices.ALL
    val featuredVoices = EdgeVoices.featured()

    fun selectVoice(id: String) { _voiceId.value = id; config.voiceId = id }
    fun setVoiceRate(v: Int) { _voiceRate.value = v.coerceIn(-50, 100) }
    fun setVoicePitch(v: Int) { _voicePitch.value = v.coerceIn(-50, 50) }

    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    private val _pack = MutableStateFlow<ContentPack?>(null)
    val pack = _pack.asStateFlow()

    private val _ideas = MutableStateFlow<List<VideoIdea>>(emptyList())
    val ideas = _ideas.asStateFlow()

    private val _script = MutableStateFlow("")
    val script = _script.asStateFlow()

    private val _analysis = MutableStateFlow("")
    val analysis = _analysis.asStateFlow()

    private val _voiceFile = MutableStateFlow<File?>(null)
    val voiceFile = _voiceFile.asStateFlow()

    private val _platform = MutableStateFlow(Platform.YT_SHORTS)
    val platform = _platform.asStateFlow()

    private val _keySaved = MutableStateFlow(config.hasAnyKey)
    val keySaved = _keySaved.asStateFlow()

    // ---- backend / Puter account ----
    private val _backend = MutableStateFlow(config.backend)
    val backend = _backend.asStateFlow()

    private val _puterUser = MutableStateFlow<String?>(null)
    val puterUser = _puterUser.asStateFlow()

    /** True when the user can actually make AI calls right now. */
    val aiAvailable: Boolean
        get() = config.backend == AiBackend.PUTER || config.hasAnyKey

    fun setBackend(b: AiBackend) { config.backend = b; _backend.value = b }
    fun setPuterModel(m: String) { config.puterModel = m }

    fun refreshPuterUser() = viewModelScope.launch {
        _puterUser.value = router.puter.currentUser()
    }

    fun signInToPuter() = viewModelScope.launch {
        _busy.value = true; _error.value = null
        router.puter.signIn()
            .onSuccess { _puterUser.value = it }
            .onFailure { _error.value = it.message ?: "Puter sign-in failed" }
        _busy.value = false
    }

    fun signOutOfPuter() = viewModelScope.launch {
        router.puter.signOut()
        _puterUser.value = null
    }

    val region: String get() = TimeZone.getDefault().id

    private val _image = MutableStateFlow<Bitmap?>(null)
    val image = _image.asStateFlow()

    private val _savedImageUri = MutableStateFlow<Uri?>(null)
    val savedImageUri = _savedImageUri.asStateFlow()

    private val _imageModel = MutableStateFlow(config.imageModel)
    val imageModel = _imageModel.asStateFlow()

    fun setImageModel(m: String) { _imageModel.value = m; config.imageModel = m }

    fun generateThumbnail(topic: String, preset: ThumbnailPrompts.Preset, aspect: String, extra: String) =
        viewModelScope.launch {
            _busy.value = true; _error.value = null; _savedImageUri.value = null
            val prompt = ThumbnailPrompts.build(topic, preset, aspect, extra)
            router.image(prompt, aspect)
                .onSuccess { _image.value = it }
                .onFailure { _error.value = it.message ?: "Image generation failed" }
            _busy.value = false
        }

    fun generateImage(prompt: String, aspect: String) = viewModelScope.launch {
        _busy.value = true; _error.value = null; _savedImageUri.value = null
        router.image(prompt, aspect)
            .onSuccess { _image.value = it }
            .onFailure { _error.value = it.message ?: "Image generation failed" }
        _busy.value = false
    }

    fun saveImage() {
        _image.value?.let { bmp -> _savedImageUri.value = router.saveImage(bmp) }
    }

    fun setPlatform(p: Platform) { _platform.value = p }
    fun clearError() { _error.value = null }

    fun saveOpenRouterKey(key: String) {
        config.openRouterKey = key
        _keySaved.value = config.hasAnyKey
    }
    fun saveOpenAiKey(key: String) { config.openAiKey = key; _keySaved.value = config.hasAnyKey }
    fun saveGeminiKey(key: String) { config.geminiKey = key; _keySaved.value = config.hasAnyKey }
    fun saveGroqKey(key: String) { config.groqKey = key; _keySaved.value = config.hasAnyKey }
    fun setModel(m: String) { config.model = m }

    private fun <T> run(block: suspend () -> Result<T>, onOk: (T) -> Unit) = viewModelScope.launch {
        _busy.value = true; _error.value = null
        block().onSuccess(onOk).onFailure { _error.value = it.message ?: "AI request failed" }
        _busy.value = false
    }

    fun generatePack(topic: String, audience: String, language: String) =
        run({ ai.contentPack(topic, _platform.value, audience, language, region) }) { _pack.value = it }

    fun generateIdeas(niche: String) =
        run({ ai.ideas(niche, _platform.value) }) { _ideas.value = it }

    fun generateScript(topic: String, seconds: Int, tone: String) =
        run({ ai.script(topic, seconds, _platform.value, tone) }) { _script.value = it }

    fun analyseTrend(topic: String) =
        run({ ai.trendAnalysis(topic, _platform.value, region) }) { _analysis.value = it }

    /** Offline best-time suggestions — no key required. */
    fun offlineSlots() = UploadTiming.slotsFor(_platform.value)
    fun nextSlot() = UploadTiming.nextBest(_platform.value)

    /**
     * Narrate [text] with the selected Microsoft neural voice.
     * Falls back to the on-device engine if the device is offline, so a
     * voiceover is always produced rather than failing outright.
     */
    fun narrate(text: String) = viewModelScope.launch {
        _busy.value = true; _error.value = null; _voiceNotice.value = null
        val result = edgeTts.synthesize(
            context = getApplication(),
            text = text,
            voiceId = _voiceId.value,
            rate = _voiceRate.value,
            pitch = _voicePitch.value
        )
        result.fold(
            onSuccess = { _voiceFile.value = it },
            onFailure = {
                _voiceNotice.value = "Neural voice unavailable, used the device voice instead"
                narrateOffline(text)
            }
        )
        _busy.value = false
    }

    /** Audition a voice before committing to a full render. */
    fun previewVoice(id: String) = viewModelScope.launch {
        _busy.value = true; _error.value = null; _voiceNotice.value = null
        edgeTts.preview(getApplication(), id).fold(
            onSuccess = { _voiceFile.value = it },
            onFailure = { _error.value = it.message ?: "Could not preview this voice" }
        )
        _busy.value = false
    }

    private suspend fun narrateOffline(text: String) {
        if (!tts.init()) { _error.value = "Text-to-speech engine unavailable"; return }
        val f = tts.synthesizeToFile(text.take(3800))
        if (f == null) _error.value = "Could not synthesize audio" else _voiceFile.value = f
    }

    override fun onCleared() { tts.release(); router.release(); super.onCleared() }
}
