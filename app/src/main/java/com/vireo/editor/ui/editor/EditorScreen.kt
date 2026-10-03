package com.vireo.editor.ui.editor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import com.vireo.editor.data.*
import com.vireo.editor.ui.theme.*

enum class EditorTool { NONE, SPLIT, SPEED, FILTER, TEXT, AUDIO, TRANSITION, VOLUME, MOTION, COLOR, CHROMA }

@UnstableApi
@Composable
fun EditorScreen(
    vm: EditorViewModel,
    onBack: () -> Unit,
    onExport: () -> Unit,
    onAddMedia: () -> Unit,
    onOpenAi: () -> Unit = {},
    onOpenTransitions: () -> Unit = {},
    onOpenCaptions: () -> Unit = {}
) {
    val project by vm.project.collectAsState()
    val playhead by vm.playheadMs.collectAsState()
    val selectedId by vm.selectedClipId.collectAsState()
    val isPlaying by vm.isPlaying.collectAsState()
    var tool by remember { mutableStateOf(EditorTool.NONE) }
    var zoom by remember { mutableFloatStateOf(1f) }
    val canUndo by vm.canUndo.collectAsState()
    val canRedo by vm.canRedo.collectAsState()

    val selectedClip = project.clips.firstOrNull { it.id == selectedId }

    Column(Modifier.fillMaxSize().background(Bg)) {

        // ---------- top bar ----------
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Back", tint = TextHi) }
            Text(project.name, color = TextHi, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { vm.undo() }) {
                Icon(Icons.Filled.Undo, "Undo", tint = if (canUndo) TextHi else Stroke)
            }
            IconButton(onClick = { vm.redo() }) {
                Icon(Icons.Filled.Redo, "Redo", tint = if (canRedo) TextHi else Stroke)
            }
            IconButton(onClick = onOpenAi) {
                Icon(Icons.Filled.AutoAwesome, "AI Studio", tint = Cyan)
            }
            Spacer(Modifier.width(4.dp))
            Box(
                Modifier.clip(RoundedCornerShape(20.dp)).background(BrandGradient)
                    .clickable { onExport() }
                    .padding(horizontal = 18.dp, vertical = 9.dp)
            ) { Text("Export", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
        }

        // ---------- preview ----------
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 12.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            if (project.clips.isEmpty()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.AddPhotoAlternate, null, tint = Stroke, modifier = Modifier.size(44.dp))
                    Spacer(Modifier.height(10.dp))
                    Text("Add media to begin", color = TextLo, fontSize = 13.sp)
                    Spacer(Modifier.height(14.dp))
                    Box(
                        Modifier.clip(RoundedCornerShape(20.dp)).background(BrandGradient)
                            .clickable { onAddMedia() }.padding(horizontal = 20.dp, vertical = 10.dp)
                    ) { Text("Import", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
                }
            } else {
                PlayerSurface(vm)
                Box(Modifier.align(Alignment.TopEnd).padding(10.dp)) {
                    Surface(color = Color.Black.copy(alpha = 0.6f), shape = RoundedCornerShape(8.dp)) {
                        Text(
                            "${playhead.asTimecode(true)} / ${project.totalDurationMs.asTimecode(true)}",
                            color = Color.White, fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }

        // ---------- transport ----------
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            IconButton(onClick = { vm.setPlayhead(0) }) {
                Icon(Icons.Filled.SkipPrevious, "Start", tint = TextHi)
            }
            Box(
                Modifier.size(48.dp).clip(RoundedCornerShape(24.dp)).background(BrandGradient)
                    .clickable { vm.setPlaying(!isPlaying) },
                contentAlignment = Alignment.Center
            ) {
                Icon(if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, "Play",
                    tint = Color.White, modifier = Modifier.size(26.dp))
            }
            IconButton(onClick = { vm.setPlayhead(project.totalDurationMs) }) {
                Icon(Icons.Filled.SkipNext, "End", tint = TextHi)
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { zoom = (zoom / 1.4f).coerceAtLeast(0.3f) }) {
                Icon(Icons.Filled.ZoomOut, "Zoom out", tint = TextLo)
            }
            IconButton(onClick = { zoom = (zoom * 1.4f).coerceAtMost(6f) }) {
                Icon(Icons.Filled.ZoomIn, "Zoom in", tint = TextLo)
            }
        }

        // ---------- timeline ----------
        Timeline(
            project = project,
            playheadMs = playhead,
            selectedClipId = selectedId,
            zoom = zoom,
            onSeek = { vm.setPlayhead(it) },
            onSelectClip = { vm.selectClip(it) },
            onTrim = { id, s, e -> vm.setTrim(id, s, e) },
            modifier = Modifier.height(170.dp)
        )

        // ---------- contextual panel ----------
        AnimatedVisibility(
            visible = tool != EditorTool.NONE && selectedClip != null,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut()
        ) {
            selectedClip?.let { clip ->
                ToolPanel(clip, tool, vm) { tool = EditorTool.NONE }
            }
        }

        // ---------- tool rail ----------
        Row(
            Modifier
                .fillMaxWidth()
                .background(Surface1)
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 6.dp, horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            RailItem(Icons.Filled.Add, "Add") { onAddMedia() }
            RailItem(Icons.Filled.ContentCut, "Split") { vm.splitAtPlayhead() }
            RailItem(Icons.Filled.Speed, "Speed", tool == EditorTool.SPEED) { tool = toggle(tool, EditorTool.SPEED) }
            RailItem(Icons.Filled.FilterVintage, "Filter", tool == EditorTool.FILTER) { tool = toggle(tool, EditorTool.FILTER) }
            RailItem(Icons.Filled.TextFields, "Text", tool == EditorTool.TEXT) {
                if (vm.project.value.texts.isEmpty()) vm.addText()
                tool = toggle(tool, EditorTool.TEXT)
            }
            RailItem(Icons.Filled.Crop, "Crop", tool == EditorTool.MOTION) { tool = toggle(tool, EditorTool.MOTION) }
            RailItem(Icons.Filled.Palette, "Colour", tool == EditorTool.COLOR) { tool = toggle(tool, EditorTool.COLOR) }
            RailItem(Icons.Filled.Contrast, "Green Screen", tool == EditorTool.CHROMA) { tool = toggle(tool, EditorTool.CHROMA) }
            RailItem(Icons.Filled.VolumeUp, "Volume", tool == EditorTool.VOLUME) { tool = toggle(tool, EditorTool.VOLUME) }
            RailItem(Icons.Filled.Transform, "Transition") { onOpenTransitions() }
            RailItem(Icons.Filled.ClosedCaption, "Captions") { onOpenCaptions() }
            RailItem(Icons.Filled.AutoAwesome, "AI") { onOpenAi() }
            RailItem(Icons.Filled.ContentCopy, "Duplicate") { selectedId?.let { vm.duplicateClip(it) } }
            RailItem(Icons.Filled.Delete, "Delete") { selectedId?.let { vm.deleteClip(it) } }
        }
    }
}

private fun toggle(current: EditorTool, target: EditorTool) =
    if (current == target) EditorTool.NONE else target

@Composable
private fun RailItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean = false,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(64.dp).clickable(onClick = onClick).padding(vertical = 6.dp)
    ) {
        Icon(icon, label, tint = if (selected) Purple else TextHi, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(4.dp))
        Text(label, color = if (selected) Purple else TextLo, fontSize = 10.sp, maxLines = 1)
    }
}

@UnstableApi
@Composable
private fun PlayerSurface(vm: EditorViewModel) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val project by vm.project.collectAsState()
    val isPlaying by vm.isPlaying.collectAsState()

    val player = remember {
        androidx.media3.exoplayer.ExoPlayer.Builder(context).build()
    }

    DisposableEffect(Unit) { onDispose { player.release() } }

    LaunchedEffect(project.clips.map { it.id to it.trimStartMs to it.trimEndMs }) {
        player.clearMediaItems()
        project.clips.forEach { clip ->
            player.addMediaItem(
                androidx.media3.common.MediaItem.Builder()
                    .setUri(clip.media.uri)
                    .setClippingConfiguration(
                        androidx.media3.common.MediaItem.ClippingConfiguration.Builder()
                            .setStartPositionMs(clip.trimStartMs)
                            .setEndPositionMs(clip.trimEndMs)
                            .build()
                    ).build()
            )
        }
        player.prepare()
    }

    LaunchedEffect(isPlaying) { player.playWhenReady = isPlaying }

    LaunchedEffect(player) {
        while (true) {
            if (player.isPlaying) vm.setPlayhead(player.currentPosition)
            kotlinx.coroutines.delay(60)
        }
    }

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                this.player = player
                useController = false
                setShutterBackgroundColor(android.graphics.Color.BLACK)
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}
