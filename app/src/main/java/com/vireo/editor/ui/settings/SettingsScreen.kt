package com.vireo.editor.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vireo.editor.ai.AiBackend
import com.vireo.editor.ai.AiConfig
import com.vireo.editor.ai.PuterClient
import com.vireo.editor.ui.GradientButton
import com.vireo.editor.ui.SectionCard
import com.vireo.editor.ui.ai.AiViewModel
import androidx.compose.ui.viewinterop.AndroidView
import com.vireo.editor.ui.theme.*

@Composable
fun SettingsScreen(vm: AiViewModel, onBack: () -> Unit) {
    var openRouter by remember { mutableStateOf(vm.config.openRouterKey) }
    var openAi by remember { mutableStateOf(vm.config.openAiKey) }
    var gemini by remember { mutableStateOf(vm.config.geminiKey) }
    var groq by remember { mutableStateOf(vm.config.groqKey) }
    var model by remember { mutableStateOf(vm.config.model) }
    var reveal by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }
    val backend by vm.backend.collectAsState()
    val puterUser by vm.puterUser.collectAsState()
    val busy by vm.busy.collectAsState()
    var puterModel by remember { mutableStateOf(vm.config.puterModel) }

    LaunchedEffect(Unit) { vm.refreshPuterUser() }

    Column(Modifier.fillMaxSize().background(Bg).verticalScroll(rememberScrollState())) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Back", tint = TextHi) }
            Text("AI Provider", color = TextHi, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { reveal = !reveal }) {
                Icon(if (reveal) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    "reveal", tint = TextLo)
            }
        }

        Column(Modifier.padding(horizontal = 16.dp)) {

            Surface(color = Purple.copy(alpha = 0.1f), shape = RoundedCornerShape(14.dp)) {
                Row(Modifier.padding(12.dp)) {
                    Icon(Icons.Filled.Lock, null, tint = Purple, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Nothing is sent to us. Vireo has no server: AI goes either through your Puter account or straight to the provider whose key you enter.",
                        color = TextLo, fontSize = 12.sp)
                }
            }

            Spacer(Modifier.height(16.dp))

            // ---------------- backend choice ----------------
            SectionCard("How should AI work?") {
                BackendOption(
                    selected = backend == AiBackend.PUTER,
                    title = "Puter — free, no API key",
                    subtitle = "Sign in once with Puter. Your own Puter allowance covers the AI. Recommended.",
                    badge = "FREE"
                ) { vm.setBackend(AiBackend.PUTER) }
                Spacer(Modifier.height(8.dp))
                BackendOption(
                    selected = backend == AiBackend.KEY,
                    title = "My own API key",
                    subtitle = "Use your OpenRouter / OpenAI / Gemini / Groq key and pay the provider directly.",
                    badge = null
                ) { vm.setBackend(AiBackend.KEY) }
            }

            Spacer(Modifier.height(12.dp))

            if (backend == AiBackend.PUTER) {
                SectionCard("Puter account") {
                    if (puterUser.isNullOrBlank()) {
                        Text("Not signed in. AI features need a free Puter account.",
                            color = TextLo, fontSize = 12.sp)
                        Spacer(Modifier.height(10.dp))
                        GradientButton(
                            if (busy) "Opening Puter…" else "Sign in with Puter",
                            Modifier.fillMaxWidth(), enabled = !busy, icon = Icons.Filled.Login
                        ) { vm.signInToPuter() }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.CheckCircle, null, tint = Cyan, modifier = Modifier.size(22.dp))
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Signed in as ${'$'}{puterUser}", color = TextHi, fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold)
                                Text("AI usage is billed to your Puter allowance",
                                    color = TextLo, fontSize = 11.sp)
                            }
                            Text("Sign out", color = Danger, fontSize = 12.sp,
                                modifier = Modifier.clickable { vm.signOutOfPuter() })
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    // Puter renders its sign-in UI inside this view
                    AndroidView(
                        factory = { ctx ->
                            android.widget.FrameLayout(ctx).also { host ->
                                host.tag = "puter-host"
                            }
                        },
                        update = { host ->
                            val wv = vm.router.puter.view()
                            if (wv != null && wv.parent == null) {
                                host.removeAllViews()
                                host.addView(wv)
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(if (puterUser.isNullOrBlank()) 220.dp else 0.dp)
                    )
                    Spacer(Modifier.height(14.dp))
                    Text("Model", color = TextLo, fontSize = 11.sp)
                    PuterClient.MODELS.forEach { (id, desc) ->
                        val sel = id == puterModel
                        Row(
                            Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (sel) Purple.copy(alpha = 0.16f) else Color.Transparent)
                                .clickable { puterModel = id; vm.setPuterModel(id) }
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = sel, onClick = { puterModel = id; vm.setPuterModel(id) },
                                colors = RadioButtonDefaults.colors(selectedColor = Purple, unselectedColor = Stroke))
                            Column {
                                Text(id, color = if (sel) TextHi else TextLo, fontSize = 12.sp)
                                Text(desc, color = TextLo, fontSize = 10.sp)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            SectionCard(if (backend == AiBackend.KEY) "OpenRouter (one key, 300+ models)" else "OpenRouter (optional fallback)") {
                KeyField(openRouter, "sk-or-v1-…", reveal) { openRouter = it }
                Spacer(Modifier.height(6.dp))
                Text("Get a key at openrouter.ai/keys — free models available",
                    color = TextLo, fontSize = 11.sp)
            }
            Spacer(Modifier.height(12.dp))

            SectionCard("Model") {
                AiConfig.SUGGESTED_MODELS.forEach { (id, desc) ->
                    val sel = id == model
                    Row(
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (sel) Purple.copy(alpha = 0.16f) else Color.Transparent)
                            .clickable { model = id }
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = sel, onClick = { model = id },
                            colors = RadioButtonDefaults.colors(selectedColor = Purple, unselectedColor = Stroke))
                        Column(Modifier.weight(1f)) {
                            Text(id, color = if (sel) TextHi else TextLo, fontSize = 12.sp,
                                fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal)
                            Text(desc, color = TextLo, fontSize = 10.sp)
                        }
                        if (desc.contains("FREE")) {
                            Box(Modifier.clip(RoundedCornerShape(8.dp)).background(Cyan.copy(alpha = 0.2f))
                                .padding(horizontal = 7.dp, vertical = 3.dp)) {
                                Text("FREE", color = Cyan, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = model, onValueChange = { model = it },
                    label = { Text("Or type any model id", color = TextLo, fontSize = 11.sp) },
                    singleLine = true, shape = RoundedCornerShape(12.dp),
                    textStyle = androidx.compose.ui.text.TextStyle(color = TextHi, fontSize = 13.sp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Purple, unfocusedBorderColor = Stroke,
                        focusedContainerColor = Surface2, unfocusedContainerColor = Surface2),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.height(12.dp))
            SectionCard("Other providers (optional)") {
                Text("OpenAI", color = TextLo, fontSize = 11.sp)
                KeyField(openAi, "sk-…", reveal) { openAi = it }
                Spacer(Modifier.height(10.dp))
                Text("Google Gemini", color = TextLo, fontSize = 11.sp)
                KeyField(gemini, "AIza…", reveal) { gemini = it }
                Spacer(Modifier.height(10.dp))
                Text("Groq (fastest)", color = TextLo, fontSize = 11.sp)
                KeyField(groq, "gsk_…", reveal) { groq = it }
            }

            Spacer(Modifier.height(18.dp))
            GradientButton(if (saved) "Saved ✓" else "Save", Modifier.fillMaxWidth(),
                icon = Icons.Filled.Save) {
                vm.saveOpenRouterKey(openRouter)
                vm.saveOpenAiKey(openAi)
                vm.saveGeminiKey(gemini)
                vm.saveGroqKey(groq)
                vm.setModel(model)
                saved = true
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun KeyField(value: String, hint: String, reveal: Boolean, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = onChange,
        placeholder = { Text(hint, color = TextLo, fontSize = 12.sp) },
        singleLine = true,
        visualTransformation = if (reveal) VisualTransformation.None else PasswordVisualTransformation(),
        shape = RoundedCornerShape(12.dp),
        textStyle = androidx.compose.ui.text.TextStyle(color = TextHi, fontSize = 13.sp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Purple, unfocusedBorderColor = Stroke,
            focusedContainerColor = Surface2, unfocusedContainerColor = Surface2,
            cursorColor = Purple),
        modifier = Modifier.fillMaxWidth()
    )
}


@Composable
private fun BackendOption(
    selected: Boolean,
    title: String,
    subtitle: String,
    badge: String?,
    onClick: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) Purple.copy(alpha = 0.16f) else Surface2)
            .border(1.dp, if (selected) Purple else Stroke, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick,
            colors = RadioButtonDefaults.colors(selectedColor = Purple, unselectedColor = Stroke))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, color = TextHi, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                if (badge != null) {
                    Spacer(Modifier.width(6.dp))
                    Box(Modifier.clip(RoundedCornerShape(6.dp)).background(Cyan.copy(alpha = 0.2f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)) {
                        Text(badge, color = Cyan, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(Modifier.height(3.dp))
            Text(subtitle, color = TextLo, fontSize = 11.sp)
        }
    }
}
