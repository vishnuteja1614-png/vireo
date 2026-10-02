package com.vireo.editor.ui.home

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vireo.editor.data.Project
import com.vireo.editor.data.asTimecode
import com.vireo.editor.ui.ToolButton
import com.vireo.editor.ui.theme.*

@Composable
fun HomeScreen(
    recent: List<Project>,
    onNewProject: () -> Unit,
    onOpenProject: (Project) -> Unit,
    onQuickTool: (String) -> Unit
) {
    LazyColumn(
        Modifier.fillMaxSize().background(Bg),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(BrandGradient),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(20.dp)) }
                Spacer(Modifier.width(10.dp))
                Text("Vireo", color = TextHi, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { onQuickTool("settings") }) {
                    Icon(Icons.Outlined.Settings, "Settings", tint = TextLo)
                }
            }
        }

        item { Text("Create", color = TextHi, fontSize = 34.sp, fontWeight = FontWeight.Bold) }

        item { NewProjectCard(onNewProject) }

        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                ToolButton(Icons.Filled.ContentCut, "Trim") { onQuickTool("trim") }
                ToolButton(Icons.Filled.Merge, "Merge") { onQuickTool("merge") }
                ToolButton(Icons.Filled.Compress, "Compress") { onQuickTool("compress") }
                ToolButton(Icons.Filled.AutoAwesome, "AI") { onQuickTool("ai") }
                ToolButton(Icons.Filled.ClosedCaption, "Captions") { onQuickTool("captions") }
            }
        }

        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Recent Projects", color = TextHi, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                Text("See all", color = Purple, fontSize = 13.sp,
                    modifier = Modifier.clickable { onQuickTool("all") })
            }
        }

        if (recent.isEmpty()) {
            item { EmptyState(onNewProject) }
        } else {
            items(recent.chunked(2)) { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    pair.forEach { p ->
                        Box(Modifier.weight(1f)) { ProjectCard(p) { onOpenProject(p) } }
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun NewProjectCard(onClick: () -> Unit) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, spring(Spring.DampingRatioMediumBouncy), label = "s")
    Box(
        Modifier
            .fillMaxWidth()
            .height(130.dp)
            .scale(scale)
            .clip(RoundedCornerShape(22.dp))
            .background(BrandGradient)
            .clickable {
                pressed = true
                onClick()
            }
            .padding(22.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(54.dp).clip(RoundedCornerShape(18.dp))
                    .background(Color.White.copy(alpha = 0.22f)),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Filled.Add, null, tint = Color.White, modifier = Modifier.size(30.dp)) }
            Spacer(Modifier.width(16.dp))
            Column {
                Text("New Project", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text("Import video, photos or record",
                    color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp)
            }
        }
    }
    LaunchedEffect(pressed) { if (pressed) { kotlinx.coroutines.delay(120); pressed = false } }
}

@Composable
private fun ProjectCard(project: Project, onClick: () -> Unit) {
    Column(Modifier.clickable(onClick = onClick)) {
        Box(
            Modifier.fillMaxWidth().height(110.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Surface2)
                .border(1.dp, Stroke, RoundedCornerShape(16.dp))
        ) {
            Icon(Icons.Filled.Movie, null, tint = Stroke,
                modifier = Modifier.align(Alignment.Center).size(34.dp))
            Box(Modifier.align(Alignment.BottomEnd).padding(8.dp)) {
                Surface(color = Color.Black.copy(alpha = 0.7f), shape = RoundedCornerShape(7.dp)) {
                    Text(project.totalDurationMs.asTimecode(), color = Color.White, fontSize = 10.sp,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(project.name, color = TextHi, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1)
        Text("${project.clips.size} clips", color = TextLo, fontSize = 12.sp)
    }
}

@Composable
private fun EmptyState(onNewProject: () -> Unit) {
    Surface(
        color = Surface1, shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, Stroke), modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            Modifier.padding(30.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Outlined.VideoLibrary, null, tint = Stroke, modifier = Modifier.size(42.dp))
            Spacer(Modifier.height(12.dp))
            Text("No projects yet", color = TextHi, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(4.dp))
            Text("Tap New Project to start editing", color = TextLo, fontSize = 13.sp)
        }
    }
}
