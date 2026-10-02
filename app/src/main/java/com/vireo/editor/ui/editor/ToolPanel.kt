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
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
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

                else -> Unit
            }
        }
    }
}

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
