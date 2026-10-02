package com.vireo.editor.ui.ai

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vireo.editor.ai.Platform
import com.vireo.editor.ui.GradientButton
import com.vireo.editor.ui.SectionCard
import com.vireo.editor.ui.theme.*

private enum class AiTab(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    PACK("Publish Pack", Icons.Filled.AutoAwesome),
    IDEAS("Ideas", Icons.Filled.Lightbulb),
    SCRIPT("Script + Voice", Icons.Filled.RecordVoiceOver),
    TIMING("Best Time", Icons.Filled.Schedule),
    TRENDS("Trends", Icons.Filled.TrendingUp),
    THUMB("Thumbnail", Icons.Filled.Image),
    MODELS("AI Models", Icons.Filled.Insights)
}

@Composable
fun AiStudioScreen(
    vm: AiViewModel,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onUseVoiceover: (java.io.File) -> Unit
) {
    var tab by remember { mutableStateOf(AiTab.PACK) }
    val platform by vm.platform.collectAsState()
    val busy by vm.busy.collectAsState()
    val error by vm.error.collectAsState()
    val keySaved by vm.keySaved.collectAsState()

    Column(Modifier.fillMaxSize().background(Bg)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Back", tint = TextHi) }
            Box(Modifier.size(28.dp).clip(RoundedCornerShape(9.dp)).background(BrandGradient),
                contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.AutoAwesome, null, tint = Color.White, modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.width(8.dp))
            Text("AI Studio", color = TextHi, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onOpenSettings) { Icon(Icons.Filled.Key, "AI keys", tint = if (keySaved) Cyan else Accent) }
        }

        // platform selector
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Platform.entries.forEach { p ->
                val sel = p == platform
                Box(
                    Modifier.clip(RoundedCornerShape(18.dp))
                        .background(if (sel) Purple else Surface2)
                        .border(1.dp, if (sel) Purple else Stroke, RoundedCornerShape(18.dp))
                        .clickable { vm.setPlatform(p) }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(p.label, color = if (sel) Color.White else TextLo, fontSize = 12.sp,
                        fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal)
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AiTab.entries.forEach { t ->
                val sel = t == tab
                Row(
                    Modifier.clip(RoundedCornerShape(14.dp))
                        .background(if (sel) Purple.copy(alpha = 0.2f) else Color.Transparent)
                        .clickable { tab = t }.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(t.icon, null, tint = if (sel) Purple else TextLo, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(t.label, color = if (sel) Purple else TextLo, fontSize = 12.sp)
                }
            }
        }

        if (!keySaved && tab != AiTab.TIMING) {
            Spacer(Modifier.height(10.dp))
            Surface(color = Accent.copy(alpha = 0.12f), shape = RoundedCornerShape(14.dp),
                modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Info, null, tint = Accent, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Add an OpenRouter key to unlock AI. Best Time works offline.",
                        color = Accent, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    Text("Add", color = Purple, fontWeight = FontWeight.Bold, fontSize = 12.sp,
                        modifier = Modifier.clickable { onOpenSettings() })
                }
            }
        }

        AnimatedVisibility(busy) { LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp), color = Purple, trackColor = Stroke) }

        error?.let {
            Surface(color = Danger.copy(alpha = 0.12f), shape = RoundedCornerShape(12.dp),
                modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                Row(Modifier.padding(12.dp)) {
                    Text(it, color = Danger, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    Icon(Icons.Filled.Close, "dismiss", tint = Danger,
                        modifier = Modifier.size(16.dp).clickable { vm.clearError() })
                }
            }
        }

        Box(Modifier.weight(1f)) {
            when (tab) {
                AiTab.PACK -> PackTab(vm)
                AiTab.IDEAS -> IdeasTab(vm)
                AiTab.SCRIPT -> ScriptTab(vm, onUseVoiceover)
                AiTab.TIMING -> TimingTab(vm)
                AiTab.TRENDS -> TrendsTab(vm)
                AiTab.THUMB -> ThumbnailTab(vm)
                AiTab.MODELS -> ModelsTab()
            }
        }
    }
}

@Composable
private fun PackTab(vm: AiViewModel) {
    var topic by remember { mutableStateOf("") }
    var audience by remember { mutableStateOf("") }
    var language by remember { mutableStateOf("English") }
    val pack by vm.pack.collectAsState()
    val ctx = LocalContext.current

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Field(topic, "What is your video about?", 3) { topic = it }
        Spacer(Modifier.height(8.dp))
        Row {
            Box(Modifier.weight(1f)) { Field(audience, "Audience (optional)", 1) { audience = it } }
            Spacer(Modifier.width(8.dp))
            Box(Modifier.weight(1f)) { Field(language, "Language", 1) { language = it } }
        }
        Spacer(Modifier.height(12.dp))
        GradientButton("Generate Publish Pack", Modifier.fillMaxWidth(),
            enabled = topic.isNotBlank(), icon = Icons.Filled.AutoAwesome) {
            vm.generatePack(topic, audience, language)
        }
        Spacer(Modifier.height(16.dp))

        pack?.let { p ->
            if (p.hook.isNotBlank()) ResultCard("Hook (first 3 seconds)", p.hook, ctx)
            if (p.thumbnailText.isNotBlank()) ResultCard("Thumbnail text", p.thumbnailText, ctx)
            if (p.titles.isNotEmpty()) {
                SectionCard("Titles") {
                    p.titles.forEachIndexed { i, t ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                            Text("${i + 1}", color = Purple, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.width(22.dp))
                            Text(t, color = TextHi, fontSize = 13.sp, modifier = Modifier.weight(1f))
                            Icon(Icons.Filled.ContentCopy, "copy", tint = TextLo,
                                modifier = Modifier.size(15.dp).clickable { copy(ctx, t) })
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
            if (p.description.isNotBlank()) ResultCard("Description", p.description, ctx)
            if (p.hashtags.isNotEmpty()) ChipsCard("Hashtags", p.hashtags, ctx, Cyan)
            if (p.keywords.isNotEmpty()) ChipsCard("SEO Keywords", p.keywords, ctx, Accent)
            if (p.bestTimes.isNotEmpty()) {
                SectionCard("Best times to post") {
                    p.bestTimes.forEach {
                        Row(Modifier.padding(vertical = 4.dp)) {
                            Icon(Icons.Filled.Schedule, null, tint = Purple, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(it, color = TextHi, fontSize = 12.sp)
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
            if (p.tips.isNotEmpty()) {
                SectionCard("Growth tips") {
                    p.tips.forEach { Text("• $it", color = TextLo, fontSize = 12.sp,
                        modifier = Modifier.padding(vertical = 3.dp)) }
                }
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun IdeasTab(vm: AiViewModel) {
    var niche by remember { mutableStateOf("") }
    val ideas by vm.ideas.collectAsState()
    val ctx = LocalContext.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Field(niche, "Your niche (e.g. budget travel in India)", 2) { niche = it }
        Spacer(Modifier.height(12.dp))
        GradientButton("Generate 10 Viral Ideas", Modifier.fillMaxWidth(),
            enabled = niche.isNotBlank(), icon = Icons.Filled.Lightbulb) { vm.generateIdeas(niche) }
        Spacer(Modifier.height(14.dp))
        ideas.forEachIndexed { i, idea ->
            SectionCard {
                Row {
                    Box(Modifier.size(24.dp).clip(CircleShape).background(BrandGradient),
                        contentAlignment = Alignment.Center) {
                        Text("${i + 1}", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(idea.title, color = TextHi, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(5.dp))
                        if (idea.hook.isNotBlank())
                            Text("Hook: ${idea.hook}", color = Cyan, fontSize = 12.sp)
                        if (idea.angle.isNotBlank())
                            Text(idea.angle, color = TextLo, fontSize = 12.sp,
                                modifier = Modifier.padding(top = 4.dp))
                        if (idea.whyItWorks.isNotBlank())
                            Text("Why: ${idea.whyItWorks}", color = TextLo, fontSize = 11.sp,
                                modifier = Modifier.padding(top = 4.dp))
                    }
                    Icon(Icons.Filled.ContentCopy, "copy", tint = TextLo,
                        modifier = Modifier.size(15.dp).clickable { copy(ctx, idea.title) })
                }
            }
            Spacer(Modifier.height(10.dp))
        }
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun ScriptTab(vm: AiViewModel, onUseVoiceover: (java.io.File) -> Unit) {
    var topic by remember { mutableStateOf("") }
    var seconds by remember { mutableFloatStateOf(30f) }
    var tone by remember { mutableStateOf("energetic") }
    val script by vm.script.collectAsState()
    val voice by vm.voiceFile.collectAsState()
    val ctx = LocalContext.current

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Field(topic, "Script topic", 2) { topic = it }
        Spacer(Modifier.height(8.dp))
        Field(tone, "Tone (energetic, calm, funny, expert…)", 1) { tone = it }
        Spacer(Modifier.height(8.dp))
        com.vireo.editor.ui.LabeledSlider(
            label = "Length", value = seconds, range = 15f..180f,
            valueText = "${seconds.toInt()}s", onChange = { seconds = it }
        )
        Spacer(Modifier.height(10.dp))
        GradientButton("Write Script", Modifier.fillMaxWidth(), enabled = topic.isNotBlank(),
            icon = Icons.Filled.EditNote) { vm.generateScript(topic, seconds.toInt(), tone) }

        if (script.isNotBlank()) {
            Spacer(Modifier.height(14.dp))
            ResultCard("Script", script, ctx)
            Spacer(Modifier.height(10.dp))
            GradientButton("Generate Voiceover (offline TTS)", Modifier.fillMaxWidth(),
                icon = Icons.Filled.RecordVoiceOver) { vm.narrate(script) }
        }

        voice?.let { f ->
            Spacer(Modifier.height(12.dp))
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.GraphicEq, null, tint = Cyan, modifier = Modifier.size(26.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Voiceover ready", color = TextHi, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text("${f.length() / 1024} KB · ${f.name}", color = TextLo, fontSize = 11.sp)
                    }
                    GradientButton("Add", Modifier.width(92.dp)) { onUseVoiceover(f) }
                }
            }
        }
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun TimingTab(vm: AiViewModel) {
    val platform by vm.platform.collectAsState()
    val slots = remember(platform) { vm.offlineSlots() }
    val next = remember(platform) { vm.nextSlot() }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        next?.let { n ->
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(BrandGradient).padding(18.dp)) {
                Column {
                    Text("NEXT BEST SLOT", color = Color.White.copy(alpha = 0.8f), fontSize = 10.sp,
                        fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                    Spacer(Modifier.height(6.dp))
                    Text("${n.day} · ${n.window}", color = Color.White, fontSize = 20.sp,
                        fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(n.why, color = Color.White.copy(alpha = 0.9f), fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(14.dp))
        }
        Text("All ${platform.label} windows · your timezone ${vm.region}",
            color = TextLo, fontSize = 12.sp)
        Spacer(Modifier.height(10.dp))
        slots.forEach { s ->
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${s.day} · ${s.window}", color = TextHi, fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold)
                        Text(s.why, color = TextLo, fontSize = 12.sp)
                    }
                    Box(Modifier.size(42.dp).clip(CircleShape).background(Surface2),
                        contentAlignment = Alignment.Center) {
                        Text("${s.score}", color = if (s.score >= 90) Cyan else Purple,
                            fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun TrendsTab(vm: AiViewModel) {
    var topic by remember { mutableStateOf("") }
    val analysis by vm.analysis.collectAsState()
    val ctx = LocalContext.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Field(topic, "Topic to analyse", 2) { topic = it }
        Spacer(Modifier.height(12.dp))
        GradientButton("Deep Trend Analysis", Modifier.fillMaxWidth(), enabled = topic.isNotBlank(),
            icon = Icons.Filled.TrendingUp) { vm.analyseTrend(topic) }
        if (analysis.isNotBlank()) {
            Spacer(Modifier.height(14.dp))
            ResultCard("Analysis", analysis, ctx)
        }
        Spacer(Modifier.height(40.dp))
    }
}

// ---------- shared bits ----------

@Composable
private fun Field(value: String, hint: String, lines: Int, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = onChange,
        placeholder = { Text(hint, color = TextLo, fontSize = 13.sp) },
        minLines = lines, maxLines = lines + 3,
        textStyle = androidx.compose.ui.text.TextStyle(color = TextHi, fontSize = 14.sp),
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Purple, unfocusedBorderColor = Stroke,
            focusedContainerColor = Surface1, unfocusedContainerColor = Surface1,
            cursorColor = Purple
        ),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun ResultCard(title: String, body: String, ctx: Context) {
    SectionCard(title) {
        Text(body, color = TextHi, fontSize = 13.sp, lineHeight = 19.sp)
        Spacer(Modifier.height(10.dp))
        Row(Modifier.clickable { copy(ctx, body) }, verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.ContentCopy, null, tint = Purple, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text("Copy", color = Purple, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
    }
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun ChipsCard(title: String, items: List<String>, ctx: Context, color: Color) {
    SectionCard(title) {
        FlowRowSimple(items, color)
        Spacer(Modifier.height(10.dp))
        Row(Modifier.clickable { copy(ctx, items.joinToString(" ")) },
            verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.ContentCopy, null, tint = Purple, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text("Copy all", color = Purple, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
    }
    Spacer(Modifier.height(10.dp))
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun FlowRowSimple(items: List<String>, color: Color) {
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items.forEach {
            Box(Modifier.clip(RoundedCornerShape(12.dp)).background(Surface2)
                .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                .padding(horizontal = 10.dp, vertical = 5.dp)) {
                Text(it, color = color, fontSize = 11.sp)
            }
        }
    }
}

private fun copy(ctx: Context, text: String) {
    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("vireo", text))
}


@Composable
private fun ThumbnailTab(vm: AiViewModel) {
    var topic by remember { mutableStateOf("") }
    var extra by remember { mutableStateOf("") }
    var preset by remember { mutableStateOf(com.vireo.editor.ai.ThumbnailPrompts.PRESETS.first()) }
    var aspect by remember { mutableStateOf("16:9") }
    val image by vm.image.collectAsState()
    val savedUri by vm.savedImageUri.collectAsState()
    val imageModel by vm.imageModel.collectAsState()

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Field(topic, "What should the thumbnail show?", 2) { topic = it }
        Spacer(Modifier.height(10.dp))

        Text("Style", color = TextLo, fontSize = 12.sp)
        Spacer(Modifier.height(6.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            com.vireo.editor.ai.ThumbnailPrompts.PRESETS.forEach { p ->
                val sel = p.id == preset.id
                Box(Modifier.clip(RoundedCornerShape(16.dp))
                    .background(if (sel) Purple else Surface2)
                    .border(1.dp, if (sel) Purple else Stroke, RoundedCornerShape(16.dp))
                    .clickable { preset = p }.padding(horizontal = 13.dp, vertical = 7.dp)) {
                    Text(p.label, color = if (sel) Color.White else TextLo, fontSize = 12.sp)
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Text("Aspect", color = TextLo, fontSize = 12.sp)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("16:9", "9:16", "1:1", "4:5").forEach { a ->
                val sel = a == aspect
                Box(Modifier.clip(RoundedCornerShape(14.dp))
                    .background(if (sel) Purple else Surface2)
                    .border(1.dp, if (sel) Purple else Stroke, RoundedCornerShape(14.dp))
                    .clickable { aspect = a }.padding(horizontal = 14.dp, vertical = 7.dp)) {
                    Text(a, color = if (sel) Color.White else TextLo, fontSize = 12.sp)
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Field(extra, "Extra details (optional): colours, text, mood", 1) { extra = it }

        Spacer(Modifier.height(10.dp))
        Text("Image model", color = TextLo, fontSize = 12.sp)
        com.vireo.editor.ai.ImageAi.IMAGE_MODELS.forEach { m ->
            Row(Modifier.fillMaxWidth().clickable { vm.setImageModel(m.id) }.padding(vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = m.id == imageModel, onClick = { vm.setImageModel(m.id) },
                    colors = RadioButtonDefaults.colors(selectedColor = Purple, unselectedColor = Stroke))
                Column {
                    Text(m.label, color = TextHi, fontSize = 12.sp)
                    Text(m.note, color = TextLo, fontSize = 10.sp)
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        GradientButton("Generate Thumbnail", Modifier.fillMaxWidth(), enabled = topic.isNotBlank(),
            icon = Icons.Filled.Image) { vm.generateThumbnail(topic, preset, aspect, extra) }

        image?.let { bmp ->
            Spacer(Modifier.height(16.dp))
            androidx.compose.foundation.Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "Generated thumbnail",
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            )
            Spacer(Modifier.height(10.dp))
            GradientButton(if (savedUri != null) "Saved to Pictures/Vireo ✓" else "Save to Gallery",
                Modifier.fillMaxWidth(), icon = Icons.Filled.Download) { vm.saveImage() }
        }
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun ModelsTab() {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("Which AI model should I use?", color = TextHi, fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text("All available with one OpenRouter key", color = TextLo, fontSize = 12.sp)
        Spacer(Modifier.height(14.dp))

        com.vireo.editor.ai.ModelsChart.ROWS.forEach { r ->
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(r.vendor, color = Purple, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            if (r.free) {
                                Spacer(Modifier.width(6.dp))
                                Box(Modifier.clip(RoundedCornerShape(6.dp))
                                    .background(Cyan.copy(alpha = 0.2f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)) {
                                    Text("FREE", color = Cyan, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            if (r.images) {
                                Spacer(Modifier.width(6.dp))
                                Box(Modifier.clip(RoundedCornerShape(6.dp))
                                    .background(Accent.copy(alpha = 0.2f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)) {
                                    Text("IMAGES", color = Accent, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        Spacer(Modifier.height(3.dp))
                        Text(r.model, color = TextHi, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(3.dp))
                        Text(r.bestFor, color = TextLo, fontSize = 11.sp)
                        Spacer(Modifier.height(6.dp))
                        Row {
                            Bars("Speed", r.speed)
                            Spacer(Modifier.width(14.dp))
                            Bars("Quality", r.quality)
                        }
                    }
                    Text(r.costPerM, color = if (r.free) Cyan else TextLo, fontSize = 10.sp)
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        Spacer(Modifier.height(8.dp))
        SectionCard("Notes") {
            com.vireo.editor.ai.ModelsChart.NOTES.forEach {
                Text("• $it", color = TextLo, fontSize = 11.sp, modifier = Modifier.padding(vertical = 3.dp))
            }
        }
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun Bars(label: String, value: Int) {
    Column {
        Text(label, color = TextLo, fontSize = 9.sp)
        Spacer(Modifier.height(3.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            repeat(5) { i ->
                Box(Modifier.size(width = 10.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (i < value) Purple else Stroke))
            }
        }
    }
}
