package com.vireo.editor.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vireo.editor.data.TextOverlay
import com.vireo.editor.ui.theme.Cyan
import com.vireo.editor.ui.theme.Purple
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * Makes text on the video canvas directly manipulable with the fingers:
 * **drag to position, pinch to resize, twist to rotate** - the interaction
 * model every phone editor uses and a desktop NLE gives you with a mouse.
 *
 * Before this, position was only reachable through numeric sliders in a panel,
 * which is unusable for placing a title around a subject's head.
 *
 * This layer sits *above* [CaptionPreviewLayer], which does the actual
 * styled drawing. Here we only draw a selection frame and capture gestures,
 * so the on-screen text never renders twice.
 *
 * Gestures write straight to the project without pushing undo steps - a pinch
 * emits dozens of events and would otherwise bury the 50-step history. One
 * undo step is committed when the fingers lift.
 */
@Composable
fun InteractiveTextLayer(
    texts: List<TextOverlay>,
    playheadMs: Long,
    selectedId: String?,
    onSelect: (String?) -> Unit,
    onTransform: (id: String, dxFraction: Float, dyFraction: Float, zoom: Float, rotation: Float) -> Unit,
    onGestureEnd: () -> Unit,
    onRequestEdit: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var canvasW by remember { mutableStateOf(1f) }
    var canvasH by remember { mutableStateOf(1f) }

    // Only cues visible at the playhead can be grabbed, so an off-screen
    // caption never steals a touch from the one the user can actually see.
    val visible = texts.filter { playheadMs in it.startMs..it.endMs }
    val active = visible.firstOrNull { it.id == selectedId } ?: visible.lastOrNull()

    Box(
        modifier
            .fillMaxSize()
            .onSizeChanged { canvasW = it.width.toFloat().coerceAtLeast(1f)
                             canvasH = it.height.toFloat().coerceAtLeast(1f) }
            .pointerInput(visible.map { it.id }, canvasW, canvasH) {
                detectTapGestures(
                    onTap = { pos ->
                        // Pick whichever visible cue is nearest the tap.
                        val hit = visible.minByOrNull { t ->
                            val dx = t.xFraction * canvasW - pos.x
                            val dy = t.yFraction * canvasH - pos.y
                            dx * dx + dy * dy
                        }
                        onSelect(hit?.id)
                    },
                    onDoubleTap = { active?.let { onRequestEdit(it.id) } }
                )
            }
            .pointerInput(active?.id, canvasW, canvasH) {
                detectTransformGestures(
                    panZoomLock = false
                ) { _, pan, zoom, rotation ->
                    val t = active ?: return@detectTransformGestures
                    onTransform(t.id, pan.x / canvasW, pan.y / canvasH, zoom, rotation)
                }
            }
    ) {
        // Selection frame. Drawn as a lightweight box rather than measuring the
        // real glyph bounds, which live in CaptionPreviewLayer's native paint.
        active?.let { t ->
            val density = LocalDensity.current
            val boxW = (t.text.length.coerceAtMost(24) * t.sizeSp * 0.55f)
            val boxH = t.sizeSp * 1.8f
            val xPx = t.xFraction * canvasW
            val yPx = t.yFraction * canvasH

            Box(
                Modifier
                    .offset(
                        x = with(density) { (xPx - boxW / 2f).toDp() },
                        y = with(density) { (yPx - boxH / 2f).toDp() }
                    )
                    .size(
                        width = with(density) { boxW.toDp() },
                        height = with(density) { boxH.toDp() }
                    )
                    .rotate(t.rotationDeg)
                    .border(1.dp, Cyan.copy(alpha = 0.9f), RoundedCornerShape(4.dp))
            ) {
                // Corner grips, purely as an affordance that this can be grabbed.
                listOf(
                    Alignment.TopStart, Alignment.TopEnd,
                    Alignment.BottomStart, Alignment.BottomEnd
                ).forEach { corner ->
                    Box(
                        Modifier
                            .align(corner)
                            .padding(2.dp)
                            .size(7.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Cyan)
                    )
                }
            }

            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 6.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Purple.copy(alpha = 0.85f))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    "Drag to move  ·  pinch to resize  ·  double-tap to edit",
                    color = Color.White, fontSize = 9.sp
                )
            }
        }
    }
}
