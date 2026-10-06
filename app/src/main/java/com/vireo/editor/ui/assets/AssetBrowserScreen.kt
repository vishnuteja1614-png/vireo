package com.vireo.editor.ui.assets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.vireo.editor.data.StockAssets
import com.vireo.editor.ui.theme.Bg
import com.vireo.editor.ui.theme.BrandGradient
import com.vireo.editor.ui.theme.Cyan
import com.vireo.editor.ui.theme.Stroke
import com.vireo.editor.ui.theme.Surface1
import com.vireo.editor.ui.theme.Surface2
import com.vireo.editor.ui.theme.TextHi
import com.vireo.editor.ui.theme.TextLo
import kotlinx.coroutines.launch
import java.io.File

/**
 * Live search over Openverse for royalty-free music and images.
 *
 * Every result shown here is already filtered to licences that permit
 * commercial use and modification, so anything the user drops on the timeline
 * is safe to ship in a paid app.
 */
@Composable
fun AssetBrowserScreen(
    onBack: () -> Unit,
    onUseAudio: (File, String) -> Unit,
    onUseImage: (File, String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var kind by remember { mutableStateOf(StockAssets.Kind.AUDIO) }
    var query by remember { mutableStateOf("cinematic") }
    var loading by remember { mutableStateOf(false) }
    var busyId by remember { mutableStateOf<String?>(null) }
    var results by remember { mutableStateOf<List<StockAssets.Asset>>(emptyList()) }
    var message by remember { mutableStateOf<String?>(null) }

    fun run() {
        loading = true
        message = null
        scope.launch {
            val r = StockAssets.search(query, kind)
            results = r
            loading = false
            if (r.isEmpty()) message = "No results. Try another word."
        }
    }

    // First open, and every tab switch, fetches a fresh page.
    LaunchedEffect(kind) { run() }

    Column(Modifier.fillMaxSize().background(Bg)) {

        Row(
            Modifier.fillMaxWidth().background(Surface1).padding(horizontal = 6.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, "Back", tint = TextHi)
            }
            Column(Modifier.weight(1f)) {
                Text("Free Library", color = TextHi, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text("Royalty-free · safe for commercial use", color = TextLo, fontSize = 11.sp)
            }
        }

        // ---------- audio / image switch ----------
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TabChip("Music", Icons.Filled.MusicNote, kind == StockAssets.Kind.AUDIO) {
                kind = StockAssets.Kind.AUDIO
            }
            TabChip("Images", Icons.Filled.Image, kind == StockAssets.Kind.IMAGE) {
                kind = StockAssets.Kind.IMAGE
            }
        }

        TextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            placeholder = { Text("Search sound or photo", color = TextLo, fontSize = 13.sp) },
            trailingIcon = {
                IconButton(onClick = { run() }) { Icon(Icons.Filled.Search, "Search", tint = Cyan) }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { run() }),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Surface2,
                unfocusedContainerColor = Surface2,
                focusedTextColor = TextHi,
                unfocusedTextColor = TextHi,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent
            ),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
                .clip(RoundedCornerShape(10.dp))
        )

        Spacer(Modifier.height(8.dp))

        if (loading) {
            Box(Modifier.fillMaxWidth().padding(28.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Cyan, strokeWidth = 2.dp)
            }
        }
        message?.let {
            Text(it, color = TextLo, fontSize = 12.sp, modifier = Modifier.padding(16.dp))
        }

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(results, key = { it.id }) { asset ->
                AssetRow(
                    asset = asset,
                    busy = busyId == asset.id,
                    onAdd = {
                        busyId = asset.id
                        scope.launch {
                            val f = StockAssets.download(context, asset)
                            busyId = null
                            if (f == null) {
                                message = "Download failed. Check the connection."
                            } else if (asset.kind == StockAssets.Kind.AUDIO) {
                                onUseAudio(f, asset.title)
                            } else {
                                onUseImage(f, asset.title)
                            }
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun TabChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    active: Boolean,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .clip(RoundedCornerShape(18.dp))
            .then(if (active) Modifier.background(BrandGradient) else Modifier.background(Surface2))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(icon, null, tint = if (active) Color.White else TextLo, modifier = Modifier.size(16.dp))
        Text(
            label,
            color = if (active) Color.White else TextLo,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun AssetRow(
    asset: StockAssets.Asset,
    busy: Boolean,
    onAdd: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Surface1)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(54.dp).clip(RoundedCornerShape(8.dp)).background(Surface2),
            contentAlignment = Alignment.Center
        ) {
            if (asset.thumb != null) {
                AsyncImage(
                    model = asset.thumb,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(Icons.Filled.MusicNote, null, tint = Stroke, modifier = Modifier.size(22.dp))
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(asset.title, color = TextHi, fontSize = 13.sp, maxLines = 1,
                fontWeight = FontWeight.Medium)
            Text(asset.creator, color = TextLo, fontSize = 11.sp, maxLines = 1)
            Text(
                if (asset.durationMs > 0)
                    "${asset.credit} · ${asset.durationMs / 1000}s" else asset.credit,
                color = Cyan, fontSize = 10.sp, maxLines = 1
            )
        }
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier.size(36.dp).clip(RoundedCornerShape(18.dp)).background(BrandGradient)
                .clickable(enabled = !busy, onClick = onAdd),
            contentAlignment = Alignment.Center
        ) {
            if (busy) {
                CircularProgressIndicator(
                    color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(16.dp)
                )
            } else {
                Icon(Icons.Filled.Add, "Add", tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }
    }
}
