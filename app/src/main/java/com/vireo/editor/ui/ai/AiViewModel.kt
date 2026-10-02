package com.vireo.editor.ui.ai

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vireo.editor.ai.*
import com.vireo.editor.engine.TtsEngine
import android.graphics.Bitmap
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.TimeZone

class AiViewModel(app: Application) : AndroidViewModel(app) {

    val config = AiConfig(app)
    private val client = OpenRouterClient(config)
    private val ai = ContentAi(client)
    private val tts = TtsEngine(app)
    private val imageAi = ImageAi(app, config)

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

    val region: String get() = TimeZone.getDefault().id

    private val _image = MutableStateFlow<Bitmap?>(null)
    val image = _image.asStateFlow()

    private val _savedImageUri = MutableStateFlow<Uri?>(null)
    val savedImageUri = _savedImageUri.asStateFlow()

    private val _imageModel = MutableStateFlow(ImageAi.IMAGE_MODELS.first().id)
    val imageModel = _imageModel.asStateFlow()

    fun setImageModel(m: String) { _imageModel.value = m }

    fun generateThumbnail(topic: String, preset: ThumbnailPrompts.Preset, aspect: String, extra: String) =
        viewModelScope.launch {
            _busy.value = true; _error.value = null; _savedImageUri.value = null
            val prompt = ThumbnailPrompts.build(topic, preset, aspect, extra)
            imageAi.generate(prompt, _imageModel.value, aspect)
                .onSuccess { _image.value = it }
                .onFailure { _error.value = it.message ?: "Image generation failed" }
            _busy.value = false
        }

    fun generateImage(prompt: String, aspect: String) = viewModelScope.launch {
        _busy.value = true; _error.value = null; _savedImageUri.value = null
        imageAi.generate(prompt, _imageModel.value, aspect)
            .onSuccess { _image.value = it }
            .onFailure { _error.value = it.message ?: "Image generation failed" }
        _busy.value = false
    }

    fun saveImage() {
        _image.value?.let { bmp -> _savedImageUri.value = imageAi.saveToGallery(bmp) }
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

    /** Narrate text with the on-device TTS engine. */
    fun narrate(text: String) = viewModelScope.launch {
        _busy.value = true; _error.value = null
        val ok = tts.init()
        if (!ok) { _error.value = "Text-to-speech engine unavailable"; _busy.value = false; return@launch }
        val f = tts.synthesizeToFile(text.take(3800))
        if (f == null) _error.value = "Could not synthesize audio" else _voiceFile.value = f
        _busy.value = false
    }

    override fun onCleared() { tts.release(); super.onCleared() }
}
