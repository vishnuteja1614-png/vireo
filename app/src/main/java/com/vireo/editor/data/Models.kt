package com.vireo.editor.data

import android.net.Uri
import androidx.compose.runtime.Immutable
import java.util.UUID

enum class MediaKind { VIDEO, IMAGE, AUDIO }

@Immutable
data class MediaItem(
    val uri: Uri,
    val kind: MediaKind,
    val durationMs: Long = 0L,
    val name: String = "",
    val sizeBytes: Long = 0L
)

/** A clip placed on the timeline. trimStart/trimEnd are source-relative. */
@Immutable
data class Clip(
    val id: String = UUID.randomUUID().toString(),
    val media: MediaItem,
    val trimStartMs: Long = 0L,
    val trimEndMs: Long = media.durationMs,
    val speed: Float = 1f,
    val volume: Float = 1f,
    val filter: FilterPreset = FilterPreset.NONE,
    val brightness: Float = 0f,
    val contrast: Float = 0f,
    val saturation: Float = 1f,
    val rotationDeg: Float = 0f,
    val transitionIn: TransitionType = TransitionType.NONE,
    val transitionMs: Long = 600L
) {
    val sourceDurationMs: Long get() = (trimEndMs - trimStartMs).coerceAtLeast(0L)
    val outputDurationMs: Long get() = (sourceDurationMs / speed).toLong()
}

enum class FilterPreset(val label: String) {
    NONE("None"), VIVID("Vivid"), NOIR("Noir"), FILM35("Film 35"),
    TEAL("Teal"), WARM("Warm"), CYBER("Cyber"), FADE("Fade")
}

enum class TransitionType(val label: String) {
    NONE("None"), FADE("Fade"), DISSOLVE("Dissolve"), SLIDE("Slide"),
    WIPE("Wipe"), ZOOM("Zoom"), SPIN("Spin"), GLITCH("Glitch"),
    BLUR("Blur"), WHIP("Whip Pan"), FLASH("Flash"), PIXELATE("Pixelate")
}

enum class TextAnim(val label: String) {
    NONE("None"), FADE_IN("Fade In"), TYPEWRITER("Typewriter"),
    POP("Pop"), SLIDE_UP("Slide Up"), BOUNCE("Bounce")
}

@Immutable
data class TextOverlay(
    val id: String = UUID.randomUUID().toString(),
    val text: String = "Your title",
    val startMs: Long = 0L,
    val endMs: Long = 3000L,
    val xFraction: Float = 0.5f,
    val yFraction: Float = 0.5f,
    val sizeSp: Float = 32f,
    val colorArgb: Int = 0xFFFFFFFF.toInt(),
    val anim: TextAnim = TextAnim.FADE_IN,
    val style: String = "Neon",
    val opacity: Float = 1f
)

@Immutable
data class AudioTrack(
    val id: String = UUID.randomUUID().toString(),
    val media: MediaItem,
    val startMs: Long = 0L,
    val volume: Float = 0.85f,
    val fadeInMs: Long = 800L,
    val fadeOutMs: Long = 1200L,
    val muted: Boolean = false,
    val isVoiceover: Boolean = false
)

@Immutable
data class Project(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Untitled Project",
    val clips: List<Clip> = emptyList(),
    val texts: List<TextOverlay> = emptyList(),
    val audio: List<AudioTrack> = emptyList(),
    val aspect: AspectRatio = AspectRatio.R16_9,
    val updatedAt: Long = System.currentTimeMillis()
) {
    val totalDurationMs: Long get() = clips.sumOf { it.outputDurationMs }
}

enum class AspectRatio(val label: String, val w: Int, val h: Int) {
    R16_9("16:9", 16, 9), R9_16("9:16", 9, 16), R1_1("1:1", 1, 1),
    R4_5("4:5", 4, 5), R4_3("4:3", 4, 3)
}

data class ExportSettings(
    val resolution: Resolution = Resolution.P1080,
    val fps: Int = 30,
    val format: ContainerFormat = ContainerFormat.MP4,
    val bitrateMbps: Int = 20,
    val hardwareAccel: Boolean = true
)

enum class Resolution(val label: String, val height: Int) {
    P720("720p", 720), P1080("1080p", 1080), P1440("2K", 1440), P2160("4K", 2160)
}

enum class ContainerFormat(val label: String, val mime: String, val ext: String) {
    MP4("MP4", "video/mp4", "mp4"),
    WEBM("WebM", "video/webm", "webm")
}
