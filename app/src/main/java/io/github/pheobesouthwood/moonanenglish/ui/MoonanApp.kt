package io.github.pheobesouthwood.moonanenglish.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.io.File

private enum class Tab(val label: String) { PRACTICE("练习"), HISTORY("历史"), SETTINGS("设置") }
enum class SettingsDestination { ROOT, PROMPTS, PROVIDERS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoonanApp(viewModel: AppViewModel = viewModel()) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(Tab.PRACTICE) }
    var settingsDestination by rememberSaveable { mutableStateOf(SettingsDestination.ROOT) }
    var showBackupWarning by remember { mutableStateOf(false) }
    var pendingCameraFile by remember { mutableStateOf<File?>(null) }
    val snackbar = remember { SnackbarHostState() }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) {
        if (it.isNotEmpty()) viewModel.importImages(it)
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val file = pendingCameraFile
        if (success && file != null) viewModel.addCaptured(file) else file?.delete()
        pendingCameraFile = null
    }
    val exportBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        uri?.let(viewModel::exportBackup)
    }
    val importBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::inspectBackup)
    }

    LaunchedEffect(ui.notice, ui.error) {
        val message = ui.error ?: ui.notice
        if (message != null) { snackbar.showSnackbar(message); viewModel.clearMessage() }
    }

    MoonanTheme(ui.stored.settings.appearanceMode) {
        if (!ui.ready) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@MoonanTheme
        }
        BackHandler(enabled = settingsDestination != SettingsDestination.ROOT) { settingsDestination = SettingsDestination.ROOT }
        Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                if (settingsDestination == SettingsDestination.ROOT) NavigationBar {
                    NavigationBarItem(tab == Tab.PRACTICE, { tab = Tab.PRACTICE }, { Icon(Icons.Default.Create, null) }, label = { Text("练习") })
                    NavigationBarItem(tab == Tab.HISTORY, { tab = Tab.HISTORY }, { Icon(Icons.Default.History, null) }, label = { Text("历史") })
                    NavigationBarItem(tab == Tab.SETTINGS, { tab = Tab.SETTINGS }, { Icon(Icons.Default.Settings, null) }, label = { Text("设置") })
                }
            },
        ) { padding ->
            when {
                settingsDestination == SettingsDestination.PROMPTS -> PromptEditorScreen(padding, ui, viewModel) { settingsDestination = SettingsDestination.ROOT }
                settingsDestination == SettingsDestination.PROVIDERS -> ProviderScreen(padding, ui, viewModel) { settingsDestination = SettingsDestination.ROOT }
                tab == Tab.PRACTICE -> PracticeScreen(
                    padding, ui, viewModel,
                    pickImages = { imagePicker.launch("image/*") },
                    takePhoto = {
                        val (uri, file) = viewModel.newCameraTarget(); pendingCameraFile = file; camera.launch(uri)
                    },
                )
                tab == Tab.HISTORY -> HistoryScreen(padding, ui, viewModel)
                else -> SettingsScreen(
                    padding, ui, viewModel,
                    openPrompts = { settingsDestination = SettingsDestination.PROMPTS },
                    openProviders = { settingsDestination = SettingsDestination.PROVIDERS },
                    exportBackup = { showBackupWarning = true },
                    importBackup = { importBackup.launch(arrayOf("application/zip", "application/octet-stream")) },
                )
            }
        }

        if (showBackupWarning) AlertDialog(
            onDismissRequest = { showBackupWarning = false },
            title = { Text("导出明文备份") },
            text = { Text("备份将直接包含 API 密钥、答题图片、提示词和完整历史。任何获得该文件的人都可以读取并使用你的密钥。") },
            confirmButton = { TextButton(onClick = { showBackupWarning = false; exportBackup.launch("moonan-english-backup.zip") }) { Text("我已了解，继续") } },
            dismissButton = { TextButton(onClick = { showBackupWarning = false }) { Text("取消") } },
        )
        ui.backupPreview?.let { preview ->
            AlertDialog(
                onDismissRequest = viewModel::dismissBackupPreview,
                title = { Text("替换全部本地数据？") },
                text = { Text("备份包含 ${preview.manifest.sessionCount} 次练习、${preview.manifest.providerCount} 个提供商配置和 ${preview.manifest.imageCount} 张图片。继续后将整体替换当前数据。") },
                confirmButton = { TextButton(onClick = viewModel::restoreBackup) { Text("确认替换") } },
                dismissButton = { TextButton(onClick = viewModel::dismissBackupPreview) { Text("取消") } },
            )
        }
        ui.busyMessage?.let { BusyOverlay(it, viewModel::cancelActive) }
    }
}

@Composable
private fun BusyOverlay(message: String, cancel: () -> Unit) {
    androidx.compose.material3.Surface(modifier = Modifier.fillMaxSize(), color = androidx.compose.ui.graphics.Color.Black.copy(alpha = .28f)) {
        Box(contentAlignment = Alignment.Center) {
            androidx.compose.material3.Surface(shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp), tonalElevation = 4.dp) {
                androidx.compose.foundation.layout.Column(
                    Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator()
                    androidx.compose.foundation.layout.Spacer(Modifier.height(14.dp))
                    Text(message)
                    if (message.contains("识别") || message.contains("评分") || message.contains("指导")) {
                        TextButton(onClick = cancel) { Text("取消当前阶段") }
                    }
                }
            }
        }
    }
}
