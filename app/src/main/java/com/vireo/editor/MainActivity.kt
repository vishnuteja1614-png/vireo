package com.vireo.editor

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.util.UnstableApi
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.vireo.editor.data.MediaKind
import com.vireo.editor.data.Project
import com.vireo.editor.ui.editor.EditorScreen
import com.vireo.editor.ui.editor.EditorViewModel
import com.vireo.editor.ui.export.ExportScreen
import com.vireo.editor.ui.ai.AiStudioScreen
import com.vireo.editor.ui.ai.AiViewModel
import com.vireo.editor.ui.editor.CaptionStylePickerScreen
import com.vireo.editor.ui.editor.TransitionPickerScreen
import com.vireo.editor.ui.home.HomeScreen
import com.vireo.editor.ui.settings.SettingsScreen
import com.vireo.editor.ui.picker.MediaPickerScreen
import com.vireo.editor.ui.theme.Bg
import com.vireo.editor.ui.theme.VireoTheme

@UnstableApi
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)

        setContent {
            VireoTheme {
                Surface(Modifier.fillMaxSize().background(Bg)) {
                    VireoApp()
                }
            }
        }
    }
}

@UnstableApi
@Composable
fun VireoApp() {
    val nav = rememberNavController()
    val vm: EditorViewModel = viewModel()
    val aiVm: AiViewModel = viewModel()
    val context = androidx.compose.ui.platform.LocalContext.current

    val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        arrayOf(Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_AUDIO)
    else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { vm.loadGallery(MediaKind.VIDEO) }

    LaunchedEffect(Unit) { launcher.launch(permissions) }

    val gallery by vm.gallery.collectAsState()
    val picked by vm.picked.collectAsState()
    val project by vm.project.collectAsState()
    // Projects are persisted to disk, so "Recent" survives the app closing.
    val store = remember { com.vireo.editor.data.ProjectStore(context) }
    var recent by remember { mutableStateOf(store.load()) }

    fun rememberProject(p: Project) {
        if (p.clips.isEmpty()) return
        store.save(p)
        recent = store.load()
    }

    NavHost(nav, startDestination = "home") {
        composable("home") {
            HomeScreen(
                recent = recent,
                onNewProject = {
                    vm.newProject()
                    vm.loadGallery(MediaKind.VIDEO)
                    nav.navigate("picker")
                },
                onOpenProject = { p -> vm.openProject(p); nav.navigate("editor") },
                onQuickTool = { tool ->
                    // Each quick tool now has real behaviour; previously trim,
                    // merge and compress all fell through to the same branch.
                    when (tool) {
                        "ai" -> nav.navigate("ai")
                        "settings" -> nav.navigate("settings")
                        "captions" -> nav.navigate("captions")
                        "compress" -> {
                            if (project.clips.isEmpty()) {
                                vm.newProject(); vm.loadGallery(MediaKind.VIDEO); nav.navigate("picker")
                            } else {
                                vm.applyCompressPreset()
                                nav.navigate("export")
                            }
                        }
                        "merge" -> {
                            vm.newProject()
                            vm.loadGallery(MediaKind.VIDEO)
                            nav.navigate("picker")
                        }
                        else -> { // trim
                            vm.newProject()
                            vm.loadGallery(MediaKind.VIDEO)
                            nav.navigate("picker")
                        }
                    }
                }
            )
        }
        composable("picker") {
            MediaPickerScreen(
                gallery = gallery,
                picked = picked,
                onTabChange = { vm.loadGallery(it) },
                onToggle = { vm.togglePick(it) },
                onBack = { nav.popBackStack() },
                onAdd = {
                    vm.addPickedToTimeline()
                    nav.navigate("editor") { popUpTo("home") }
                }
            )
        }
        composable("editor") {
            EditorScreen(
                vm = vm,
                onBack = {
                    rememberProject(project)
                    nav.popBackStack("home", inclusive = false)
                },
                onExport = { nav.navigate("export") },
                onAddMedia = { vm.loadGallery(MediaKind.VIDEO); nav.navigate("picker") },
                onOpenAi = { nav.navigate("ai") },
                onOpenTransitions = { nav.navigate("transitions") },
                onOpenCaptions = { nav.navigate("captions") },
                onOpenAssets = { nav.navigate("assets") }
            )
        }
        composable("assets") {
            com.vireo.editor.ui.assets.AssetBrowserScreen(
                onBack = { nav.popBackStack() },
                onUseAudio = { file, title ->
                    vm.addStockAudio(file, title)
                    nav.popBackStack()
                },
                onUseImage = { file, title ->
                    vm.addStockImage(file, title)
                    nav.popBackStack()
                }
            )
        }
        composable("ai") {
            AiStudioScreen(
                vm = aiVm,
                onBack = { nav.popBackStack() },
                onOpenSettings = { nav.navigate("settings") },
                onUseVoiceover = { file ->
                    vm.addVoiceoverFile(file)
                    nav.popBackStack()
                }
            )
        }
        composable("settings") {
            SettingsScreen(vm = aiVm, onBack = { nav.popBackStack() })
        }
        composable("transitions") {
            val selected = project.clips.firstOrNull { it.id == vm.selectedClipId.value }
            TransitionPickerScreen(
                selectedId = selected?.transitionId ?: "none",
                onPick = { def ->
                    vm.selectedClipId.value?.let { vm.setTransitionPreset(it, def) }
                    nav.popBackStack()
                },
                onBack = { nav.popBackStack() }
            )
        }
        composable("captions") {
            CaptionStylePickerScreen(
                selectedId = project.captionStyleId,
                onPick = { st -> vm.setCaptionStyle(st.id); nav.popBackStack() },
                onBack = { nav.popBackStack() },
                onImportSubtitles = { raw ->
                    val n = vm.importSubtitles(raw)
                    if (n > 0) nav.popBackStack()
                }
            )
        }
        composable("export") {
            ExportScreen(
                vm = vm,
                onClose = { rememberProject(project); vm.resetExport(); nav.popBackStack() },
                onShare = { uri -> shareVideo(context, uri) }
            )
        }
    }
}

private fun shareVideo(context: android.content.Context, uri: Uri) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "video/*"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Share video"))
}
