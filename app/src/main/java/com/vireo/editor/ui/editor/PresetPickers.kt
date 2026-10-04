package com.vireo.editor.ui.editor

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import com.vireo.editor.data.Easing
import com.vireo.editor.data.TransitionDef
import kotlin.math.pow
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vireo.editor.data.*
import com.vireo.editor.ui.theme.*

@Composable
fun TransitionPickerScreen(
    selectedId: String,
    onPick: (TransitionDef) -> Unit,
    onBack: () -> Unit
) {
    var family by remember { mutableStateOf<TransitionFamily?>(null) }
    val list = remember(family) { family?.let { Transitions.byFamily(it) } ?: Transitions.ALL }

    Column(Modifier.fillMaxSize().background(Bg)) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Back", tint = TextHi) }
            Text("Transitions", color = TextHi, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Text("${Transitions.COUNT} presets", color = Cyan, fontSize = 12.sp,
                modifier = Modifier.padding(end = 12.dp))
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("All", family == null) { family = null }
            Transitions.FAMILIES.forEach { f -> Chip(f.label, family == f) { family = f } }
        }
        Spacer(Modifier.height(10.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(list, key = { it.id }) { t ->
                val sel = t.id == selectedId
                Column(
                    Modifier.clip(RoundedCornerShape(12.dp))
                        .background(if (sel) Purple.copy(alpha = 0.2f) else Surface1)
                        .border(1.dp, if (sel) Purple else Stroke, RoundedCornerShape(12.dp))
                        .clickable { onPick(t) }
                        .padding(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    TransitionThumb(t, Modifier.fillMaxWidth().height(44.dp))
                    Spacer(Modifier.height(6.dp))
                    Text(t.label, color = if (sel) Purple else TextHi, fontSize = 10.sp,
                        maxLines = 1, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal)
                    Text("${t.defaultMs}ms", color = TextLo, fontSize = 9.sp)
                }
            }
        }
    }
}


/**
 * Animated preview tile showing what a transition actually does.
 *
 * The grid previously rendered every preset as the same purple gradient with
 * two letters on it, so "Slide Left" and "Dissolve" were visually identical and
 * the only way to judge a preset was to apply it and export. This runs a small
 * looping A-to-B demo using the same motion vocabulary the render engine uses,
 * so Slide slides, Wipe wipes, Zoom zooms and Dissolve cross-fades.
 */
@Composable
private fun TransitionThumb(def: TransitionDef, modifier: Modifier = Modifier) {
    val loop = rememberInfiniteTransition(label = def.id)
    val raw by loop.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "progress"
    )
    // Hold briefly at each end so the eye can read the result.
    val p = ((raw - 0.15f) / 0.7f).coerceIn(0f, 1f)
    val t = ease(p, def.easing)

    val colorA = Color(0xFF3A3A46)
    val colorB = Purple

    Box(
        modifier.clip(RoundedCornerShape(8.dp)).background(colorA),
        contentAlignment = Alignment.Center
    ) {
        val rad = Math.toRadians((if (def.angle >= 0) def.angle else 0).toDouble())
        val dirX = kotlin.math.cos(rad).toFloat()
        val dirY = -kotlin.math.sin(rad).toFloat()

        when (def.family) {
            TransitionFamily.DISSOLVE, TransitionFamily.BLUR ->
                Box(Modifier.fillMaxSize().alpha(t).background(colorB))

            TransitionFamily.SLIDE, TransitionFamily.WIPE ->
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            translationX = (1f - t) * size.width * dirX
                            translationY = (1f - t) * size.height * dirY
                        }
                        .background(colorB)
                )

            TransitionFamily.ZOOM, TransitionFamily.SHAPE ->
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { scaleX = t; scaleY = t; alpha = t }
                        .background(colorB)
                )

            TransitionFamily.ROTATE, TransitionFamily.THREE_D, TransitionFamily.CREATIVE ->
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            rotationZ = (1f - t) * 180f
                            scaleX = 0.4f + 0.6f * t
                            scaleY = 0.4f + 0.6f * t
                            alpha = t
                        }
                        .background(colorB)
                )

            TransitionFamily.LIGHT ->
                Box(
                    Modifier
                        .fillMaxSize()
                        .alpha(t)
                        .background(colorB)
                ) {
                    // Flash decays away as the incoming frame settles.
                    Box(Modifier.fillMaxSize().alpha((1f - t).pow(2)).background(Color.White))
                }

            TransitionFamily.GLITCH ->
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            translationX = (1f - t) * 14f * kotlin.math.sin(t * 30f)
                            alpha = t
                        }
                        .background(colorB)
                )

            TransitionFamily.DISTORT ->
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = 1f + (1f - t) * 0.9f
                            scaleY = (0.35f + 0.65f * t)
                            alpha = t
                        }
                        .background(colorB)
                )
        }

        Text(
            def.label.take(2).uppercase(),
            color = Color.White.copy(alpha = 0.9f),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

private fun ease(x: Float, e: Easing): Float = when (e) {
    Easing.LINEAR -> x
    Easing.EASE_IN -> x * x * x
    Easing.EASE_OUT -> 1f - (1f - x).pow(3)
    Easing.EASE_IN_OUT -> if (x < 0.5f) 4f * x * x * x else 1f - (-2f * x + 2f).pow(3) / 2f
    Easing.BACK -> {
        val c1 = 1.70158f; val c3 = c1 + 1f
        1f + c3 * (x - 1f).pow(3) + c1 * (x - 1f).pow(2)
    }
    Easing.BOUNCE -> {
        val n1 = 7.5625f; val d1 = 2.75f
        when {
            x < 1f / d1 -> n1 * x * x
            x < 2f / d1 -> { val v = x - 1.5f / d1; n1 * v * v + 0.75f }
            x < 2.5f / d1 -> { val v = x - 2.25f / d1; n1 * v * v + 0.9375f }
            else -> { val v = x - 2.625f / d1; n1 * v * v + 0.984375f }
        }
    }
    Easing.ELASTIC -> if (x == 0f || x == 1f) x else {
        val c4 = (2f * Math.PI.toFloat()) / 3f
        2f.pow(-10f * x) * kotlin.math.sin((x * 10f - 0.75f) * c4) + 1f
    }
}

@Composable
fun CaptionStylePickerScreen(
    selectedId: String,
    onPick: (CaptionStyle) -> Unit,
    onBack: () -> Unit,
    /** Receives the raw text of a chosen .srt/.vtt file. */
    onImportSubtitles: (String) -> Unit = {}
) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val picker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            // Subtitle files are small; reading on the main thread is fine here.
            runCatching {
                ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }.getOrNull()?.let(onImportSubtitles)
        }
    }
    var cat by remember { mutableStateOf<CaptionCategory?>(null) }
    val list = remember(cat) { cat?.let { CaptionStyles.byCategory(it) } ?: CaptionStyles.ALL }

    Column(Modifier.fillMaxSize().background(Bg)) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Back", tint = TextHi) }
            Text("Caption Styles", color = TextHi, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = {
                picker.launch(arrayOf("application/x-subrip", "text/vtt", "text/plain", "*/*"))
            }) { Text("Import SRT", color = Cyan, fontSize = 12.sp) }
            Text("${CaptionStyles.COUNT} styles", color = Cyan, fontSize = 12.sp,
                modifier = Modifier.padding(end = 12.dp))
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("All", cat == null) { cat = null }
            CaptionStyles.CATEGORIES.forEach { c -> Chip(c.label, cat == c) { cat = c } }
        }
        Spacer(Modifier.height(10.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(list, key = { it.id }) { st ->
                val sel = st.id == selectedId
                Column(
                    Modifier.clip(RoundedCornerShape(14.dp))
                        .background(Surface1)
                        .border(if (sel) 2.dp else 1.dp, if (sel) Purple else Stroke, RoundedCornerShape(14.dp))
                        .clickable { onPick(st) }
                ) {
                    Box(Modifier.fillMaxWidth().height(78.dp).background(Color(0xFF17171C)),
                        contentAlignment = Alignment.Center) {
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(st.cornerRadius.dp))
                                .background(Color(st.backgroundColor))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                if (st.allCaps) "SAMPLE TEXT" else "Sample text",
                                color = Color(st.textColor),
                                fontSize = (st.sizeSp / 2.6f).sp,
                                fontWeight = FontWeight(st.fontWeight.coerceIn(100, 900)),
                                letterSpacing = st.letterSpacing.sp
                            )
                        }
                    }
                    Column(Modifier.padding(10.dp)) {
                        Text(st.label, color = if (sel) Purple else TextHi, fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold)
                        Text("${st.category.label} · ${st.anim.name.lowercase().replace('_', ' ')}",
                            color = TextLo, fontSize = 10.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(16.dp))
            .background(if (selected) Purple else Surface2)
            .border(1.dp, if (selected) Purple else Stroke, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 7.dp)
    ) {
        Text(label, color = if (selected) Color.White else TextLo, fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
    }
}
