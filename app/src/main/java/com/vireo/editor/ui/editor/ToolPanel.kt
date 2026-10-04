package com.vireo.editor.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.util.UnstableApi
import com.vireo.editor.data.Clip
import com.vireo.editor.data.FilterPreset
import com.vireo.editor.data.AspectRatio
import com.vireo.editor.data.CanvasFill
import com.vireo.editor.data.PanZoom
import com.vireo.editor.data.TransitionType
import com.vireo.editor.ui.LabeledSlider
import com.vireo.editor.ui.theme.*

@UnstableApi
@Composable
fun ToolPanel(clip: Clip, tool: EditorTool, vm: EditorViewModel, onClose: () -> Unit) {
    Surface(color = Surface1, shadowElevation = 12.dp) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    when (tool) {
                        EditorTool.SPEED -> "Speed"
                        EditorTool.FILTER -> "Filters"
                        EditorTool.VOLUME -> "Volume"
                        EditorTool.TRANSITION -> "Transition"
                        EditorTool.TEXT -> "Text"
                        EditorTool.MOTION -> "Crop & Motion"
                        EditorTool.COLOR -> "Colour Grade"
                        EditorTool.CHROMA -> "Green Screen"
                        EditorTool.WIPE -> "Shaped Wipe"
                        EditorTool.CANVAS -> "Canvas & Ratio"
                        else -> ""
                    },
                    color = TextHi, fontSize = 15.sp, fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier.size(32.dp).clip(RoundedCornerShape(16.dp)).background(BrandGradient)
                        .clickable { onClose() },
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Filled.Check, "Apply", tint = Color.White, modifier = Modifier.size(18.dp)) }
            }
            Spacer(Modifier.height(12.dp))

            when (tool) {
                EditorTool.SPEED -> {
                    Row(Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(0.25f, 0.5f, 1f, 1.5f, 2f, 3f, 4f).forEach { s ->
                            PresetChip("${s}x", clip.speed == s) { vm.setSpeed(clip.id, s) }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    LabeledSlider("Custom", clip.speed, 0.25f..4f, "${"%.2f".format(clip.speed)}x") {
                        vm.setSpeed(clip.id, it)
                    }
                }

                EditorTool.FILTER -> {
                    Row(Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterPreset.entries.forEach { f ->
                            PresetChip(f.label, clip.filter == f) { vm.setFilter(clip.id, f) }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    LabeledSlider("Brightness", clip.brightness, -1f..1f) {
                        vm.updateClip(clip.id) { c -> c.copy(brightness = it) }
                    }
                    LabeledSlider("Contrast", clip.contrast, -1f..1f) {
                        vm.updateClip(clip.id) { c -> c.copy(contrast = it) }
                    }
                    LabeledSlider("Saturation", clip.saturation, 0f..2f) {
                        vm.updateClip(clip.id) { c -> c.copy(saturation = it) }
                    }
                }

                EditorTool.VOLUME -> {
                    LabeledSlider("Clip volume", clip.volume, 0f..2f,
                        "${(clip.volume * 100).toInt()}%") { vm.setVolume(clip.id, it) }
                }

                EditorTool.TRANSITION -> {
                    Row(Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TransitionType.entries.forEach { t ->
                            PresetChip(t.label, clip.transitionIn == t) {
                                vm.setTransition(clip.id, t, clip.transitionMs)
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    LabeledSlider("Duration", clip.transitionMs / 1000f, 0.2f..2f,
                        "${"%.1f".format(clip.transitionMs / 1000f)}s") {
                        vm.setTransition(clip.id, clip.transitionIn, (it * 1000).toLong())
                    }
                }

                EditorTool.TEXT -> TextPanel(vm)
                EditorTool.MOTION -> MotionPanel(clip, vm)
                EditorTool.COLOR -> ColorPanel(clip, vm)
                EditorTool.CHROMA -> ChromaPanel(clip, vm)
                EditorTool.WIPE -> LumaWipePanel(clip, vm)
                EditorTool.CANVAS -> CanvasPanel(vm)

                else -> Unit
            }
        }
    }
}

/**
 * Edit the selected text overlay.
 *
 * Previously `EditorTool.TEXT` existed in the enum but had no branch here, so
 * tapping "Text" inserted a hardcoded "Your title" overlay that could never be
 * changed - `updateText` was never called from anywhere in the UI. This is that
 * missing editor: content, size, colour, placement, timing and delete.
 */
@UnstableApi
@Composable
private fun TextPanel(vm: EditorViewModel) {
    val project by vm.project.collectAsState()
    val overlays = project.texts
    var editingId by remember(overlays.size) { mutableStateOf(overlays.lastOrNull()?.id) }
    val overlay = overlays.firstOrNull { it.id == editingId }

    if (overlay == null) {
        Text("Tap Text on the toolbar to add a caption.", color = TextLo, fontSize = 13.sp)
        return
    }

    // Pick between multiple overlays when there is more than one.
    if (overlays.size > 1) {
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            overlays.forEachIndexed { index, t ->
                PresetChip(
                    label = t.text.take(10).ifBlank { "Text ${index + 1}" },
                    selected = t.id == editingId
                ) { editingId = t.id }
            }
        }
        Spacer(Modifier.height(10.dp))
    }

    OutlinedTextField(
        value = overlay.text,
        onValueChange = { v -> vm.updateText(overlay.id) { it.copy(text = v) } },
        label = { Text("Text", color = TextLo, fontSize = 12.sp) },
        textStyle = LocalTextStyle.current.copy(color = TextHi, fontSize = 15.sp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Purple,
            unfocusedBorderColor = Stroke,
            cursorColor = Cyan
        ),
        modifier = Modifier.fillMaxWidth(),
        maxLines = 3
    )

    Spacer(Modifier.height(12.dp))
    Text("Colour", color = TextLo, fontSize = 11.sp)
    Spacer(Modifier.height(6.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        TEXT_COLOURS.forEach { argb ->
            val on = overlay.colorArgb == argb
            Box(
                Modifier
                    .size(if (on) 32.dp else 26.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color(argb))
                    .border(
                        width = if (on) 2.dp else 1.dp,
                        color = if (on) Cyan else Stroke,
                        shape = RoundedCornerShape(50)
                    )
                    .clickable { vm.updateText(overlay.id) { it.copy(colorArgb = argb) } }
            )
        }
    }

    Spacer(Modifier.height(10.dp))
    LabeledSlider("Size", overlay.sizeSp, 12f..96f, "${overlay.sizeSp.toInt()}sp") { v ->
        vm.updateText(overlay.id) { it.copy(sizeSp = v) }
    }
    LabeledSlider("Horizontal", overlay.xFraction, 0f..1f,
        "${(overlay.xFraction * 100).toInt()}%") { v ->
        vm.updateText(overlay.id) { it.copy(xFraction = v) }
    }
    LabeledSlider("Vertical", overlay.yFraction, 0f..1f,
        "${(overlay.yFraction * 100).toInt()}%") { v ->
        vm.updateText(overlay.id) { it.copy(yFraction = v) }
    }
    LabeledSlider("Opacity", overlay.opacity, 0.1f..1f,
        "${(overlay.opacity * 100).toInt()}%") { v ->
        vm.updateText(overlay.id) { it.copy(opacity = v) }
    }

    Spacer(Modifier.height(4.dp))
    Text("Timing", color = TextLo, fontSize = 11.sp)
    LabeledSlider("Start", overlay.startMs / 1000f, 0f..60f,
        "${"%.1f".format(overlay.startMs / 1000f)}s") { v ->
        val start = (v * 1000).toLong()
        vm.updateText(overlay.id) {
            it.copy(startMs = start, endMs = maxOf(it.endMs, start + 500L))
        }
    }
    LabeledSlider("Duration", (overlay.endMs - overlay.startMs) / 1000f, 0.5f..30f,
        "${"%.1f".format((overlay.endMs - overlay.startMs) / 1000f)}s") { v ->
        vm.updateText(overlay.id) { it.copy(endMs = it.startMs + (v * 1000).toLong()) }
    }

    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        TextButton(onClick = { vm.addText() }) {
            Text("+ Add another", color = Cyan, fontSize = 13.sp)
        }
        Spacer(Modifier.weight(1f))
        TextButton(onClick = {
            vm.removeText(overlay.id)
            editingId = vm.project.value.texts.lastOrNull()?.id
        }) {
            Text("Delete", color = Danger, fontSize = 13.sp)
        }
    }
}


/** Ken Burns motion plus a simple edge crop. */
@UnstableApi
@Composable
private fun MotionPanel(clip: Clip, vm: EditorViewModel) {
    Text("Motion (Ken Burns)", color = TextLo, fontSize = 11.sp)
    Spacer(Modifier.height(6.dp))
    Row(Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PanZoom.entries.forEach { p ->
            PresetChip(p.label, clip.panZoom == p) { vm.setPanZoom(clip.id, p) }
        }
    }

    Spacer(Modifier.height(14.dp))
    Text("Crop", color = TextLo, fontSize = 11.sp)
    LabeledSlider("Left", clip.cropLeft, 0f..0.45f, "${(clip.cropLeft * 100).toInt()}%") {
        vm.setCrop(clip.id, it, clip.cropTop, clip.cropRight, clip.cropBottom)
    }
    LabeledSlider("Right", clip.cropRight, 0.55f..1f, "${(clip.cropRight * 100).toInt()}%") {
        vm.setCrop(clip.id, clip.cropLeft, clip.cropTop, it, clip.cropBottom)
    }
    LabeledSlider("Top", clip.cropTop, 0f..0.45f, "${(clip.cropTop * 100).toInt()}%") {
        vm.setCrop(clip.id, clip.cropLeft, it, clip.cropRight, clip.cropBottom)
    }
    LabeledSlider("Bottom", clip.cropBottom, 0.55f..1f, "${(clip.cropBottom * 100).toInt()}%") {
        vm.setCrop(clip.id, clip.cropLeft, clip.cropTop, clip.cropRight, it)
    }
    TextButton(onClick = { vm.setCrop(clip.id, 0f, 0f, 1f, 1f) }) {
        Text("Reset crop", color = Cyan, fontSize = 12.sp)
    }
}

/** Film LUTs plus the manual grade that previously had no UI at all. */
@UnstableApi
@Composable
private fun ColorPanel(clip: Clip, vm: EditorViewModel) {
    Text("Film look (LUT)", color = TextLo, fontSize = 11.sp)
    Spacer(Modifier.height(6.dp))
    Row(Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        com.vireo.editor.engine.LutPreset.entries.forEach { l ->
            PresetChip(l.label, clip.lutId == l.name) { vm.setLut(clip.id, l.name) }
        }
    }

    Spacer(Modifier.height(14.dp))
    Text("Manual grade", color = TextLo, fontSize = 11.sp)
    LabeledSlider("Brightness", clip.brightness, -1f..1f,
        "${(clip.brightness * 100).toInt()}") {
        vm.setGrade(clip.id, it, clip.contrast, clip.saturation)
    }
    LabeledSlider("Contrast", clip.contrast, -1f..1f,
        "${(clip.contrast * 100).toInt()}") {
        vm.setGrade(clip.id, clip.brightness, it, clip.saturation)
    }
    LabeledSlider("Saturation", clip.saturation, 0f..2f,
        "${(clip.saturation * 100).toInt()}%") {
        vm.setGrade(clip.id, clip.brightness, clip.contrast, it)
    }
    TextButton(onClick = { vm.setGrade(clip.id, 0f, 0f, 1f) }) {
        Text("Reset grade", color = Cyan, fontSize = 12.sp)
    }
}

/** Green-screen keying, tuned on the GPU. */
@UnstableApi
@Composable
private fun ChromaPanel(clip: Clip, vm: EditorViewModel) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Remove background", color = TextHi, fontSize = 14.sp)
        Spacer(Modifier.weight(1f))
        Switch(
            checked = clip.chromaKey,
            onCheckedChange = { vm.setChromaEnabled(clip.id, it) },
            colors = SwitchDefaults.colors(checkedTrackColor = Purple)
        )
    }

    if (!clip.chromaKey) {
        Spacer(Modifier.height(6.dp))
        Text(
            "Shoot against an evenly lit green or blue sheet for the cleanest cut-out.",
            color = TextLo, fontSize = 11.sp
        )
        return
    }

    Spacer(Modifier.height(12.dp))
    Text("Colour to remove", color = TextLo, fontSize = 11.sp)
    Spacer(Modifier.height(6.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        KEY_COLOURS.forEach { (label, rgb) ->
            val on = clip.chromaColorRgb == rgb
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .size(if (on) 32.dp else 26.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color(0xFF000000.toInt() or rgb))
                        .border(
                            if (on) 2.dp else 1.dp,
                            if (on) Cyan else Stroke,
                            RoundedCornerShape(50)
                        )
                        .clickable { vm.setChromaColor(clip.id, rgb) }
                )
                Text(label, color = TextLo, fontSize = 9.sp)
            }
        }
    }

    Spacer(Modifier.height(12.dp))
    Text("Replace with", color = TextLo, fontSize = 11.sp)
    Spacer(Modifier.height(6.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        BACK_COLOURS.forEach { rgb ->
            val on = clip.chromaBackRgb == rgb
            Box(
                Modifier
                    .size(if (on) 30.dp else 24.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xFF000000.toInt() or rgb))
                    .border(if (on) 2.dp else 1.dp, if (on) Cyan else Stroke, RoundedCornerShape(50))
                    .clickable { vm.setChromaBack(clip.id, rgb) }
            )
        }
    }

    Spacer(Modifier.height(10.dp))
    LabeledSlider("Strength", clip.chromaSimilarity, 0.05f..0.9f,
        "${(clip.chromaSimilarity * 100).toInt()}%") {
        vm.setChromaTuning(clip.id, it, clip.chromaSmoothness, clip.chromaSpill)
    }
    LabeledSlider("Edge softness", clip.chromaSmoothness, 0.01f..0.4f,
        "${(clip.chromaSmoothness * 100).toInt()}%") {
        vm.setChromaTuning(clip.id, clip.chromaSimilarity, it, clip.chromaSpill)
    }
    LabeledSlider("Spill removal", clip.chromaSpill, 0.01f..0.6f,
        "${(clip.chromaSpill * 100).toInt()}%") {
        vm.setChromaTuning(clip.id, clip.chromaSimilarity, clip.chromaSmoothness, it)
    }

    Spacer(Modifier.height(6.dp))
    Text(
        "Video encoders cannot store transparency, so the keyed area is filled " +
            "with the colour you picked above.",
        color = TextLo, fontSize = 10.sp
    )
}

private val KEY_COLOURS = listOf(
    "Green" to 0x00FF00, "Blue" to 0x0047FF, "Cyan" to 0x00FFD1,
    "Magenta" to 0xFF00E5, "White" to 0xFFFFFF, "Black" to 0x000000
)

private val BACK_COLOURS = listOf(
    0x000000, 0xFFFFFF, 0x0A0A0C, 0x8B5CF6, 0x22D3EE, 0xEF4444, 0x34D399
)


/**
 * Shaped "luma wipe" reveals, the mechanism MLT (Shotcut/Kdenlive) and
 * libopenshot (OpenShot) use for their wipe transitions: a grayscale map plus
 * a sweeping threshold and a softness value.
 */
@UnstableApi
@Composable
private fun LumaWipePanel(clip: Clip, vm: EditorViewModel) {
    Text("Shape", color = TextLo, fontSize = 11.sp)
    Spacer(Modifier.height(6.dp))
    Row(Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PresetChip("None", clip.lumaWipeId == "NONE") { vm.setLumaWipe(clip.id, "NONE") }
        com.vireo.editor.engine.LumaPattern.entries.forEach { p ->
            PresetChip(p.label, clip.lumaWipeId == p.name) { vm.setLumaWipe(clip.id, p.name) }
        }
    }

    if (clip.lumaWipeId == "NONE") {
        Spacer(Modifier.height(8.dp))
        Text(
            "A shaped wipe reveals the clip through a pattern - iris, clock, " +
                "blinds, spiral - instead of simply cutting in.",
            color = TextLo, fontSize = 11.sp
        )
        return
    }

    Spacer(Modifier.height(10.dp))
    LabeledSlider("Edge softness", clip.lumaSoftness, 0f..0.6f,
        if (clip.lumaSoftness < 0.02f) "sharp" else "${(clip.lumaSoftness * 100).toInt()}%") {
        vm.setLumaTuning(clip.id, it, clip.lumaInvert)
    }
    LabeledSlider("Duration", clip.transitionMs / 1000f, 0.2f..3f,
        "${"%.1f".format(clip.transitionMs / 1000f)}s") {
        vm.setTransition(clip.id, clip.transitionIn, (it * 1000).toLong())
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Invert wipe", color = TextHi, fontSize = 13.sp)
        Spacer(Modifier.weight(1f))
        Switch(
            checked = clip.lumaInvert,
            onCheckedChange = { vm.setLumaTuning(clip.id, clip.lumaSoftness, it) },
            colors = SwitchDefaults.colors(checkedTrackColor = Purple)
        )
    }
}


/**
 * Output aspect ratio and the fill shown behind letterboxed video.
 *
 * Every social platform wants a different shape, so this is a first-class
 * control rather than something buried in the export screen: changing it
 * reshapes the preview canvas immediately.
 */
@UnstableApi
@Composable
private fun CanvasPanel(vm: EditorViewModel) {
    val project by vm.project.collectAsState()

    Text("Aspect ratio", color = TextLo, fontSize = 11.sp)
    Spacer(Modifier.height(6.dp))
    Row(Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AspectRatio.entries.forEach { r ->
            val on = project.aspect == r
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (on) Purple.copy(alpha = 0.22f) else Surface2)
                    .border(1.dp, if (on) Purple else Stroke, RoundedCornerShape(10.dp))
                    .clickable { vm.setAspect(r) }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                // Miniature of the shape, so the choice is readable at a glance.
                Box(
                    Modifier
                        .height(26.dp)
                        .aspectRatio(r.w.toFloat() / r.h.toFloat())
                        .clip(RoundedCornerShape(3.dp))
                        .background(if (on) Purple else Stroke)
                )
                Spacer(Modifier.height(5.dp))
                Text(r.label, color = if (on) Purple else TextHi, fontSize = 10.sp)
            }
        }
    }

    Spacer(Modifier.height(14.dp))
    Text("Fit", color = TextLo, fontSize = 11.sp)
    Spacer(Modifier.height(6.dp))
    Row(Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CanvasFill.entries.forEach { f ->
            PresetChip(f.label, project.canvasFill == f) { vm.setCanvasFill(f) }
        }
    }

    Spacer(Modifier.height(14.dp))
    Text("Background fill", color = TextLo, fontSize = 11.sp)
    Spacer(Modifier.height(4.dp))
    Text(
        "Shown behind the video when it does not fill the chosen ratio.",
        color = TextLo, fontSize = 10.sp
    )
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        CANVAS_FILLS.forEach { rgb ->
            val on = project.canvasBackRgb == rgb
            Box(
                Modifier
                    .size(if (on) 30.dp else 24.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xFF000000.toInt() or rgb))
                    .border(if (on) 2.dp else 1.dp, if (on) Cyan else Stroke, RoundedCornerShape(50))
                    .clickable { vm.setCanvasBack(rgb) }
            )
        }
    }
}

private val CANVAS_FILLS = listOf(
    0x000000, 0xFFFFFF, 0x0A0A0C, 0x1C1C22, 0x8B5CF6, 0x22D3EE, 0xF59E0B
)

/** Swatches that read well burned over video. */
private val TEXT_COLOURS = listOf(
    0xFFFFFFFF.toInt(), 0xFF000000.toInt(), 0xFFFFD60A.toInt(), 0xFF8B5CF6.toInt(),
    0xFF22D3EE.toInt(), 0xFFEF4444.toInt(), 0xFF34D399.toInt(), 0xFFF472B6.toInt()
)

@Composable
private fun PresetChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) Purple.copy(alpha = 0.25f) else Surface2)
            .border(1.dp, if (selected) Purple else Stroke, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(label, color = if (selected) Purple else TextLo, fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
    }
}
