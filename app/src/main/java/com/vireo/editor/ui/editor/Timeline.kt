package com.vireo.editor.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import com.vireo.editor.data.*
import com.vireo.editor.ui.theme.*
import kotlin.math.roundToInt

/** Pixels per second of timeline at zoom = 1. */
private const val BASE_PPS = 60f

@Composable
fun Timeline(
    project: Project,
    playheadMs: Long,
    selectedClipId: String?,
    zoom: Float,
    onSeek: (Long) -> Unit,
    onSelectClip: (String?) -> Unit,
    onTrim: (String, Long, Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val scroll = rememberScrollState()
    val density = LocalDensity.current
    val pps = BASE_PPS * zoom

    fun msToDp(ms: Long) = (ms / 1000f * pps).dp

    Box(modifier.fillMaxWidth().background(Bg)) {
        Column(
            Modifier
                .horizontalScroll(scroll)
                .padding(horizontal = 0.dp)
        ) {
            // ruler
            TimeRuler(project.totalDurationMs, pps, onSeek)

            Spacer(Modifier.height(6.dp))

            // video track
            Row(
                Modifier
                    .height(62.dp)
                    .padding(horizontal = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                project.clips.forEach { clip ->
                    ClipChip(
                        clip = clip,
                        widthDp = msToDp(clip.outputDurationMs),
                        selected = clip.id == selectedClipId,
                        onClick = { onSelectClip(if (clip.id == selectedClipId) null else clip.id) },
                        onTrimDelta = { startDelta, endDelta ->
                            val perPx = (1000f / pps)
                            onTrim(
                                clip.id,
                                clip.trimStartMs + (startDelta * perPx).toLong(),
                                clip.trimEndMs + (endDelta * perPx).toLong()
                            )
                        }
                    )
                }
                Spacer(Modifier.width(140.dp))
            }

            Spacer(Modifier.height(5.dp))

            // audio track
            AudioLane(project, pps)

            Spacer(Modifier.height(5.dp))

            // text track
            TextLane(project, pps)

            Spacer(Modifier.height(10.dp))
        }

        // playhead
        val offsetPx = with(density) { (playheadMs / 1000f * pps).dp.toPx() } - scroll.value
        Canvas(Modifier.matchParentSize()) {
            if (offsetPx in 0f..size.width) {
                drawLine(
                    color = Color.White,
                    start = Offset(offsetPx, 0f),
                    end = Offset(offsetPx, size.height),
                    strokeWidth = 2.5f
                )
                drawCircle(Color.White, radius = 7f, center = Offset(offsetPx, 5f))
            }
        }
    }
}

@Composable
private fun TimeRuler(totalMs: Long, pps: Float, onSeek: (Long) -> Unit) {
    val seconds = ((totalMs / 1000) + 6).toInt().coerceAtLeast(10)
    Row(
        Modifier
            .height(22.dp)
            .pointerInput(pps) {
                detectTapGestures { off -> onSeek((off.x / pps * 1000).toLong()) }
            }
    ) {
        repeat(seconds) { s ->
            Box(Modifier.width(pps.dp), contentAlignment = Alignment.CenterStart) {
                if (s % 5 == 0) {
                    Text("${s}s", color = TextLo, fontSize = 9.sp)
                } else {
                    Box(Modifier.size(1.dp, 6.dp).background(Stroke))
                }
            }
        }
    }
}

@Composable
private fun ClipChip(
    clip: Clip,
    widthDp: androidx.compose.ui.unit.Dp,
    selected: Boolean,
    onClick: () -> Unit,
    onTrimDelta: (Float, Float) -> Unit
) {
    val context = LocalContext.current
    Box(
        Modifier
            .width(widthDp.coerceAtLeast(36.dp))
            .fillMaxHeight()
            .clip(RoundedCornerShape(8.dp))
            .background(Surface2)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) Purple else Stroke,
                shape = RoundedCornerShape(8.dp)
            )
            .pointerInput(clip.id) { detectTapGestures { onClick() } }
    ) {
        if (clip.media.kind == MediaKind.IMAGE || clip.media.kind == MediaKind.VIDEO) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(clip.media.uri)
                    .decoderFactory { r, o, _ -> VideoFrameDecoder(r.source, o) }
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(Icons.Filled.Image, null, tint = Stroke, modifier = Modifier.align(Alignment.Center))
        }

        if (clip.speed != 1f) {
            Box(Modifier.align(Alignment.TopStart).padding(3.dp)) {
                Text("${clip.speed}x", color = Accent, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
        }
        if (clip.filter != FilterPreset.NONE) {
            Box(Modifier.align(Alignment.BottomStart).padding(3.dp)) {
                Text(clip.filter.label, color = Cyan, fontSize = 8.sp)
            }
        }

        if (selected) {
            TrimHandle(Modifier.align(Alignment.CenterStart)) { dx -> onTrimDelta(dx, 0f) }
            TrimHandle(Modifier.align(Alignment.CenterEnd)) { dx -> onTrimDelta(0f, dx) }
        }
    }
}

@Composable
private fun TrimHandle(modifier: Modifier, onDrag: (Float) -> Unit) {
    Box(
        modifier
            .width(14.dp)
            .fillMaxHeight()
            .background(Purple, RoundedCornerShape(4.dp))
            .pointerInput(Unit) {
                detectHorizontalDragGestures { _, dragAmount -> onDrag(dragAmount) }
            },
        contentAlignment = Alignment.Center
    ) {
        Box(Modifier.size(2.dp, 18.dp).background(Color.White, RoundedCornerShape(1.dp)))
    }
}

@Composable
private fun AudioLane(project: Project, pps: Float) {
    val widthDp = ((project.totalDurationMs / 1000f) * pps).dp.coerceAtLeast(120.dp)
    Box(
        Modifier
            .width(widthDp)
            .height(34.dp)
            .padding(horizontal = 2.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(Surface1)
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val mid = size.height / 2
            val step = 4f
            var x = 0f
            var seed = 7
            while (x < size.width) {
                seed = (seed * 1103515245 + 12345) and 0x7fffffff
                val h = ((seed % 100) / 100f) * (size.height * 0.42f) + 2f
                drawLine(Cyan.copy(alpha = 0.85f), Offset(x, mid - h), Offset(x, mid + h), strokeWidth = 2f)
                x += step
            }
        }
        if (project.audio.isEmpty()) {
            Text("Original audio", color = TextLo, fontSize = 9.sp,
                modifier = Modifier.align(Alignment.CenterStart).padding(start = 6.dp))
        }
    }
}

@Composable
private fun TextLane(project: Project, pps: Float) {
    val widthDp = ((project.totalDurationMs / 1000f) * pps).dp.coerceAtLeast(120.dp)
    Box(Modifier.width(widthDp).height(26.dp).padding(horizontal = 2.dp)) {
        project.texts.forEach { t ->
            val startDp = (t.startMs / 1000f * pps).dp
            val wDp = ((t.endMs - t.startMs) / 1000f * pps).dp
            Box(
                Modifier
                    .padding(start = startDp)
                    .width(wDp.coerceAtLeast(40.dp))
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(6.dp))
                    .background(Accent.copy(alpha = 0.25f))
                    .border(1.dp, Accent, RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(t.text, color = Accent, fontSize = 9.sp, maxLines = 1,
                    modifier = Modifier.padding(horizontal = 5.dp))
            }
        }
        if (project.texts.isEmpty()) {
            Box(Modifier.fillMaxSize().clip(RoundedCornerShape(6.dp)).background(Surface1),
                contentAlignment = Alignment.CenterStart) {
                Text("Text / stickers", color = TextLo, fontSize = 9.sp,
                    modifier = Modifier.padding(start = 6.dp))
            }
        }
    }
}
