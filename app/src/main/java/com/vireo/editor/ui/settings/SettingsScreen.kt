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
import com.vireo.editor.ai.AiConfig
import com.vireo.editor.ui.GradientButton
import com.vireo.editor.ui.SectionCard
import com.vireo.editor.ui.ai.AiViewModel
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

    Column(Modifier.fillMaxSize().background(Bg).verticalScroll(rememberScrollState())) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Back", tint = TextHi) }
            Text("AI Keys & Models", color = TextHi, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
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
                    Text("Keys are stored only on this phone and sent directly to the provider you choose. Vireo has no server.",
                        color = TextLo, fontSize = 12.sp)
                }
            }

            Spacer(Modifier.height(16.dp))

            SectionCard("OpenRouter (recommended — one key, 300+ models)") {
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
