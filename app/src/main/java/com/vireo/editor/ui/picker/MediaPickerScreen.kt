package com.vireo.editor.ui.picker

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import com.vireo.editor.data.MediaItem
import com.vireo.editor.data.MediaKind
import com.vireo.editor.data.asTimecode
import com.vireo.editor.ui.GradientButton
import com.vireo.editor.ui.theme.*

@Composable
fun MediaPickerScreen(
    gallery: List<MediaItem>,
    picked: List<MediaItem>,
    onTabChange: (MediaKind) -> Unit,
    onToggle: (MediaItem) -> Unit,
    onBack: () -> Unit,
    onAdd: () -> Unit
) {
    var tab by remember { mutableStateOf(0) }
    val tabs = listOf("Videos", "Photos", "Audio")
    val totalMs = picked.sumOf { it.durationMs }

    Column(Modifier.fillMaxSize().background(Bg)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Back", tint = TextHi) }
            Text("Select Media", color = TextHi, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            AnimatedVisibility(picked.isNotEmpty(), enter = fadeIn() + scaleIn(), exit = fadeOut()) {
                Text("Next (${picked.size})", color = Purple, fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable { onAdd() }.padding(horizontal = 12.dp))
            }
        }

        TabRow(
            selectedTabIndex = tab,
            containerColor = Bg,
            contentColor = Purple,
            divider = {}
        ) {
            tabs.forEachIndexed { i, label ->
                Tab(
                    selected = tab == i,
                    onClick = {
                        tab = i
                        onTabChange(when (i) { 0 -> MediaKind.VIDEO; 1 -> MediaKind.IMAGE; else -> MediaKind.AUDIO })
                    },
                    text = { Text(label, fontSize = 14.sp,
                        color = if (tab == i) Purple else TextLo,
                        fontWeight = if (tab == i) FontWeight.SemiBold else FontWeight.Normal) }
                )
            }
        }

        if (gallery.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Purple, strokeWidth = 2.dp)
                    Spacer(Modifier.height(14.dp))
                    Text("Loading media…", color = TextLo, fontSize = 13.sp)
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(3.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                items(gallery, key = { it.uri.toString() }) { item ->
                    val index = picked.indexOfFirst { it.uri == item.uri }
                    MediaCell(item, index) { onToggle(item) }
                }
            }
        }

        AnimatedVisibility(
            visible = picked.isNotEmpty(),
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut()
        ) {
            Surface(color = Surface1, shadowElevation = 16.dp) {
                Column(Modifier.padding(14.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("${picked.size} selected", color = TextHi, fontSize = 13.sp,
                            fontWeight = FontWeight.Medium)
                        Spacer(Modifier.weight(1f))
                        Text("Total ${totalMs.asTimecode()}", color = Cyan, fontSize = 13.sp)
                    }
                    Spacer(Modifier.height(10.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(picked, key = { it.uri.toString() }) { m ->
                            Box {
                                Thumb(m, Modifier.size(58.dp).clip(RoundedCornerShape(10.dp)))
                                Box(
                                    Modifier.align(Alignment.TopEnd).padding(2.dp).size(18.dp)
                                        .clip(CircleShape).background(Color.Black.copy(alpha = 0.7f))
                                        .clickable { onToggle(m) },
                                    contentAlignment = Alignment.Center
                                ) { Icon(Icons.Filled.Close, null, tint = Color.White, modifier = Modifier.size(12.dp)) }
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    GradientButton("Add to Timeline", Modifier.fillMaxWidth(), icon = Icons.Filled.Add) { onAdd() }
                }
            }
        }
    }
}

@Composable
private fun MediaCell(item: MediaItem, selectedIndex: Int, onClick: () -> Unit) {
    val selected = selectedIndex >= 0
    val scale by animateFloatAsState(if (selected) 0.92f else 1f,
        spring(Spring.DampingRatioMediumBouncy), label = "mc")
    Box(
        Modifier
            .aspectRatio(1f)
            .scale(scale)
            .clip(RoundedCornerShape(10.dp))
            .background(Surface2)
            .clickable(onClick = onClick)
            .then(if (selected) Modifier.border(2.dp, Purple, RoundedCornerShape(10.dp)) else Modifier)
    ) {
        Thumb(item, Modifier.fillMaxSize())
        if (item.kind == MediaKind.VIDEO && item.durationMs > 0) {
            Box(Modifier.align(Alignment.BottomStart).padding(5.dp)) {
                Surface(color = Color.Black.copy(alpha = 0.65f), shape = RoundedCornerShape(6.dp)) {
                    Text(item.durationMs.asTimecode(), color = Color.White, fontSize = 9.sp,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp))
                }
            }
        }
        AnimatedVisibility(selected, enter = scaleIn(), exit = scaleOut(),
            modifier = Modifier.align(Alignment.TopEnd).padding(5.dp)) {
            Box(
                Modifier.size(22.dp).clip(CircleShape).background(BrandGradient),
                contentAlignment = Alignment.Center
            ) {
                Text("${selectedIndex + 1}", color = Color.White, fontSize = 11.sp,
                    fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun Thumb(item: MediaItem, modifier: Modifier) {
    val context = LocalContext.current
    AsyncImage(
        model = ImageRequest.Builder(context)
            .data(item.uri)
            .decoderFactory { result, options, _ -> VideoFrameDecoder(result.source, options) }
            .crossfade(true)
            .build(),
        contentDescription = item.name,
        contentScale = ContentScale.Crop,
        modifier = modifier
    )
}
