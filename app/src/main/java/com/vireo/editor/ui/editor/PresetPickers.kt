package com.vireo.editor.ui.editor

import androidx.compose.foundation.background
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
                    Box(Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(8.dp))
                        .background(BrandGradient), contentAlignment = Alignment.Center) {
                        Text(t.label.take(2).uppercase(), color = Color.White,
                            fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(t.label, color = if (sel) Purple else TextHi, fontSize = 10.sp,
                        maxLines = 1, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal)
                    Text("${t.defaultMs}ms", color = TextLo, fontSize = 9.sp)
                }
            }
        }
    }
}

@Composable
fun CaptionStylePickerScreen(
    selectedId: String,
    onPick: (CaptionStyle) -> Unit,
    onBack: () -> Unit
) {
    var cat by remember { mutableStateOf<CaptionCategory?>(null) }
    val list = remember(cat) { cat?.let { CaptionStyles.byCategory(it) } ?: CaptionStyles.ALL }

    Column(Modifier.fillMaxSize().background(Bg)) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Back", tint = TextHi) }
            Text("Caption Styles", color = TextHi, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
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
