package com.vireo.editor.ui.export

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import com.vireo.editor.data.*
import com.vireo.editor.engine.ExportState
import com.vireo.editor.ui.ChipRow
import com.vireo.editor.ui.GradientButton
import com.vireo.editor.ui.SectionCard
import com.vireo.editor.ui.theme.*

@UnstableApi
@Composable
fun ExportScreen(
    vm: com.vireo.editor.ui.editor.EditorViewModel,
    onClose: () -> Unit,
    onShare: (android.net.Uri) -> Unit
) {
    val project by vm.project.collectAsState()
    val settings by vm.settings.collectAsState()
    val state by vm.exportState.collectAsState()

    Column(
        Modifier.fillMaxSize().background(Bg).verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Close", tint = TextHi) }
            Text("Export", color = TextHi, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        }

        Spacer(Modifier.height(16.dp))

        SectionCard {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Movie, null, tint = Purple, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(project.name, color = TextHi, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    Text("${project.clips.size} clips · ${project.totalDurationMs.asTimecode()}",
                        color = TextLo, fontSize = 12.sp)
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        SectionCard("Resolution") {
            ChipRow(
                options = Resolution.entries.map { it.label },
                selectedIndex = Resolution.entries.indexOf(settings.resolution),
                onSelect = { i -> vm.updateSettings { s -> s.copy(resolution = Resolution.entries[i]) } }
            )
        }

        Spacer(Modifier.height(12.dp))

        SectionCard("Frame rate") {
            val fpsOptions = listOf(24, 30, 60)
            ChipRow(
                options = fpsOptions.map { "$it" },
                selectedIndex = fpsOptions.indexOf(settings.fps).coerceAtLeast(0),
                onSelect = { i -> vm.updateSettings { s -> s.copy(fps = fpsOptions[i]) } }
            )
        }

        Spacer(Modifier.height(12.dp))

        SectionCard("Format") {
            ChipRow(
                options = ContainerFormat.entries.map { it.label },
                selectedIndex = ContainerFormat.entries.indexOf(settings.format),
                onSelect = { i -> vm.updateSettings { s -> s.copy(format = ContainerFormat.entries[i]) } }
            )
        }

        Spacer(Modifier.height(12.dp))

        SectionCard("Quality") {
            com.vireo.editor.ui.LabeledSlider(
                label = "Bitrate",
                value = settings.bitrateMbps.toFloat(),
                range = 4f..80f,
                valueText = "${settings.bitrateMbps} Mbps",
                onChange = { v -> vm.updateSettings { s -> s.copy(bitrateMbps = v.toInt()) } }
            )
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Hardware acceleration", color = TextLo, fontSize = 13.sp)
                Spacer(Modifier.weight(1f))
                Switch(
                    checked = settings.hardwareAccel,
                    onCheckedChange = { v -> vm.updateSettings { it.copy(hardwareAccel = v) } },
                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Purple)
                )
            }
            Spacer(Modifier.height(8.dp))
            Text("Estimated size: ${vm.estimatedSizeBytes().asFileSize()}", color = Cyan, fontSize = 12.sp)
        }

        Spacer(Modifier.height(20.dp))

        when (val s = state) {
            is ExportState.Running -> ProgressRing(s.percent, s.stage)
            is ExportState.Done -> DoneCard(s) { onShare(s.uri) }
            is ExportState.Failed -> Text(s.message, color = Danger, fontSize = 13.sp)
            else -> Unit
        }

        Spacer(Modifier.height(16.dp))

        if (state is ExportState.Running) {
            OutlinedButton(
                onClick = { vm.cancelExport() },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(26.dp)
            ) { Text("Cancel", color = Danger) }
        } else {
            GradientButton(
                "Export Video", Modifier.fillMaxWidth(),
                enabled = project.clips.isNotEmpty(),
                icon = Icons.Filled.Download
            ) { vm.startExport() }
        }

        Spacer(Modifier.height(30.dp))
    }
}

@Composable
private fun ProgressRing(percent: Int, stage: String = "Exporting") {
    val animated by animateFloatAsState(percent / 100f, tween(400), label = "pr")
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                progress = { animated },
                modifier = Modifier.size(120.dp),
                color = Purple,
                trackColor = Stroke,
                strokeWidth = 8.dp
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("$percent%", color = TextHi, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text(stage, color = TextLo, fontSize = 11.sp)
            }
        }
        Spacer(Modifier.height(10.dp))
        Text("GPU accelerated · Media3 Transformer", color = TextLo, fontSize = 11.sp)
    }
}

@Composable
private fun DoneCard(s: ExportState.Done, onShare: () -> Unit) {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.CheckCircle, null, tint = Cyan, modifier = Modifier.size(32.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Export complete", color = TextHi, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text("Saved to Movies/Vireo · ${s.elapsedMs / 1000}s", color = TextLo, fontSize = 12.sp)
            }
            IconButton(onClick = onShare) { Icon(Icons.Filled.Share, "Share", tint = Purple) }
        }
    }
}
