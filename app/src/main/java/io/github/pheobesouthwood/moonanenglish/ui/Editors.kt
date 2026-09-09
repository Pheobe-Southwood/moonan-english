package io.github.pheobesouthwood.moonanenglish.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import io.github.pheobesouthwood.moonanenglish.domain.AiProtocol
import io.github.pheobesouthwood.moonanenglish.domain.AiStage
import io.github.pheobesouthwood.moonanenglish.domain.GradingJsonSchemas
import io.github.pheobesouthwood.moonanenglish.domain.PromptDefaults
import io.github.pheobesouthwood.moonanenglish.domain.PromptTemplate
import io.github.pheobesouthwood.moonanenglish.domain.PromptVariableValidator
import io.github.pheobesouthwood.moonanenglish.domain.PromptVariables
import io.github.pheobesouthwood.moonanenglish.domain.ProviderProfile
import io.github.pheobesouthwood.moonanenglish.domain.StageAssignment
import io.github.pheobesouthwood.moonanenglish.data.ai.ProviderProfileValidation
import java.util.UUID

private fun AiStage.zh(): String = when (this) {
    AiStage.VISION_RECOGNITION -> "视觉文字识别"
    AiStage.TRANSLATION_GRADING -> "翻译·严格评分"
    AiStage.TRANSLATION_REVIEW -> "翻译·复习指导"
    AiStage.SHORT_ESSAY_GRADING -> "小作文·严格评分"
    AiStage.SHORT_ESSAY_REVIEW -> "小作文·复习指导"
    AiStage.LONG_ESSAY_GRADING -> "大作文·严格评分"
    AiStage.LONG_ESSAY_REVIEW -> "大作文·复习指导"
}

private fun AiProtocol.zh(): String = when (this) {
    AiProtocol.OPENAI_RESPONSES -> "OpenAI Responses"
    AiProtocol.OPENAI_CHAT_COMPLETIONS -> "OpenAI 兼容 Chat"
    AiProtocol.ANTHROPIC_MESSAGES -> "Anthropic Messages"
    AiProtocol.GEMINI_GENERATE_CONTENT -> "Gemini generateContent"
    AiProtocol.BAIDU_OCR -> "百度 OCR"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderScreen(padding: PaddingValues, ui: AppUiState, vm: AppViewModel, back: () -> Unit) {
    var editing by remember { mutableStateOf<ProviderProfile?>(null) }
    var assigning by remember { mutableStateOf<AiStage?>(null) }
    Scaffold(
        modifier = Modifier.padding(padding),
        topBar = { TopAppBar(title = { Text("AI 提供商") }, navigationIcon = { IconButton(back) { Icon(Icons.Default.ArrowBack, "返回") } }) },
        floatingActionButton = {
            Button(onClick = { editing = ProviderProfile(UUID.randomUUID().toString(), "新提供商", AiProtocol.OPENAI_CHAT_COMPLETIONS, "https://") }) {
                Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text("新增")
            }
        },
    ) { inner ->
        LazyColumn(Modifier.fillMaxSize().padding(inner), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 90.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Text("密钥仅保存在应用沙箱；明文备份会包含这些密钥。", color = MaterialTheme.colorScheme.error) }
            items(ui.stored.providers.sortedBy { it.name }, key = { it.id }) { provider ->
                GroupCard {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(provider.name, style = MaterialTheme.typography.titleMedium)
                            Text(provider.protocol.zh(), color = MaterialTheme.colorScheme.primary)
                            Text(provider.baseUrl, style = MaterialTheme.typography.bodySmall)
                            Text("模型：${provider.modelIds.joinToString().ifBlank { "尚未填写" }}", style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(provider.enabled, { vm.saveProvider(provider.copy(enabled = it)) })
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton({ editing = provider }) { Text("编辑") }
                        OutlinedButton({ vm.fetchModels(provider.id) }) { Text("拉取模型") }
                        OutlinedButton({ vm.testProvider(provider.id) }) { Text("测试连接") }
                    }
                }
            }
            item { Text("阶段绑定", style = MaterialTheme.typography.titleLarge) }
            items(AiStage.entries) { stage ->
                val assignment = ui.stored.assignments.firstOrNull { it.stage == stage }
                GroupCard {
                    Text(stage.zh())
                    Text(if (assignment == null || assignment.providerId.isBlank()) "未配置" else "${ui.stored.providers.firstOrNull { it.id == assignment.providerId }?.name ?: "提供商已删除"} · ${assignment.modelId}", style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { assigning = stage }) { Text("选择提供商与模型") }
                }
            }
        }
    }
    editing?.let { profile -> ProviderDialog(profile, dismiss = { editing = null }, save = { vm.saveProvider(it); editing = null }, delete = { vm.deleteProvider(profile.id); editing = null }) }
    assigning?.let { stage -> AssignmentDialog(stage, ui, dismiss = { assigning = null }, save = { vm.saveAssignment(it); assigning = null }) }
}

@Composable
private fun ProviderDialog(profile: ProviderProfile, dismiss: () -> Unit, save: (ProviderProfile) -> Unit, delete: () -> Unit) {
    var draft by remember(profile) { mutableStateOf(profile) }
    var protocolMenu by remember { mutableStateOf(false) }
    var headers by remember(profile) { mutableStateOf(profile.extraHeaders.entries.joinToString("\n") { "${it.key}: ${it.value}" }) }
    var models by remember(profile) { mutableStateOf(profile.modelIds.joinToString(", ")) }
    val profileErrors = ProviderProfileValidation.validate(draft)
    val valid = profileErrors.isEmpty()
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text("提供商配置") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(draft.name, { draft = draft.copy(name = it) }, label = { Text("名称") }, modifier = Modifier.fillMaxWidth())
                FilledTonalButton({ protocolMenu = true }, Modifier.fillMaxWidth()) { Text(draft.protocol.zh()) }
                DropdownMenu(protocolMenu, { protocolMenu = false }) {
                    AiProtocol.entries.forEach { protocol -> DropdownMenuItem({ Text(protocol.zh()) }, { draft = draft.copy(protocol = protocol); protocolMenu = false }) }
                }
                OutlinedTextField(draft.baseUrl, { draft = draft.copy(baseUrl = it) }, label = { Text("HTTPS Base URL") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(draft.apiKey, { draft = draft.copy(apiKey = it) }, label = { Text(if (draft.protocol == AiProtocol.BAIDU_OCR) "API Key" else "API Key") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                if (draft.protocol == AiProtocol.BAIDU_OCR) OutlinedTextField(draft.apiSecret, { draft = draft.copy(apiSecret = it) }, label = { Text("Secret Key") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(models, { models = it }, label = { Text("模型 ID（逗号分隔）") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(headers, { headers = it }, label = { Text("额外请求头（每行 名称: 值）") }, modifier = Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) { Text("支持图片输入"); Switch(draft.supportsVision, { draft = draft.copy(supportsVision = it) }) }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) { Text("启用"); Switch(draft.enabled, { draft = draft.copy(enabled = it) }) }
                if (!valid) Text(profileErrors.joinToString("\n"), color = MaterialTheme.colorScheme.error)
                TextButton(onClick = delete) { Icon(Icons.Default.Delete, null); Text("删除提供商", color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = {
                val parsedHeaders = headers.lines().mapNotNull { line -> line.indexOf(':').takeIf { it > 0 }?.let { line.substring(0, it).trim() to line.substring(it + 1).trim() } }.toMap()
                save(draft.copy(modelIds = models.split(',').map(String::trim).filter(String::isNotBlank), extraHeaders = parsedHeaders))
            }) { Text("保存") }
        },
        dismissButton = { TextButton(dismiss) { Text("取消") } },
    )
}

@Composable
private fun AssignmentDialog(stage: AiStage, ui: AppUiState, dismiss: () -> Unit, save: (StageAssignment) -> Unit) {
    val providers = ui.stored.providers.filter { it.enabled && it.protocol != AiProtocol.BAIDU_OCR }
    var providerId by remember { mutableStateOf(ui.stored.assignments.firstOrNull { it.stage == stage }?.providerId ?: providers.firstOrNull()?.id.orEmpty()) }
    var model by remember { mutableStateOf(ui.stored.assignments.firstOrNull { it.stage == stage }?.modelId.orEmpty()) }
    val selected = providers.firstOrNull { it.id == providerId }
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text(stage.zh()) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            providers.forEach { provider -> FilterChip(provider.id == providerId, { providerId = provider.id; model = provider.modelIds.firstOrNull().orEmpty() }, { Text(provider.name) }) }
            OutlinedTextField(model, { model = it }, label = { Text("模型 ID") }, modifier = Modifier.fillMaxWidth())
            if (providers.isEmpty()) Text("请先启用一个 AI 提供商。")
        } },
        confirmButton = { TextButton(enabled = selected != null && model.isNotBlank(), onClick = { save(StageAssignment(stage, providerId, model)) }) { Text("保存") } },
        dismissButton = { TextButton(dismiss) { Text("取消") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PromptEditorScreen(padding: PaddingValues, ui: AppUiState, vm: AppViewModel, back: () -> Unit) {
    var selected by remember { mutableStateOf(AiStage.VISION_RECOGNITION) }
    var stageMenu by remember { mutableStateOf(false) }
    var resetAll by remember { mutableStateOf(false) }
    val stored = ui.stored.prompts.firstOrNull { it.stage == selected } ?: PromptDefaults.defaultFor(selected)
    var draft by remember(stored) { mutableStateOf(stored) }
    Scaffold(modifier = Modifier.padding(padding), topBar = { TopAppBar(title = { Text("提示词") }, navigationIcon = { IconButton(back) { Icon(Icons.Default.ArrowBack, "返回") } }) }) { inner ->
        Column(Modifier.fillMaxSize().padding(inner).padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton({ stageMenu = true }, Modifier.fillMaxWidth()) { Text("当前：${selected.zh()}") }
            DropdownMenu(stageMenu, { stageMenu = false }) {
                AiStage.entries.forEach { stage -> DropdownMenuItem({ Text(stage.zh()) }, { selected = stage; stageMenu = false }) }
            }
            Text(selected.zh(), style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(draft.systemPrompt, { draft = draft.copy(systemPrompt = it) }, label = { Text("System prompt") }, modifier = Modifier.fillMaxWidth().height(180.dp))
            OutlinedTextField(draft.userTemplate, { draft = draft.copy(userTemplate = it) }, label = { Text("User template") }, modifier = Modifier.fillMaxWidth().height(220.dp))
            OutlinedTextField(draft.rubric, { draft = draft.copy(rubric = it) }, label = { Text("项目自定义、非官方六档评分规则") }, modifier = Modifier.fillMaxWidth().height(180.dp))
            Text("可用变量：${PromptVariables.all.joinToString { "{{$it}}" }}", style = MaterialTheme.typography.bodySmall)
            val validation = PromptVariableValidator.validateForSave(draft)
            if (!validation.valid) Text(validation.errors.joinToString("\n"), color = MaterialTheme.colorScheme.error)
            GroupCard { Text("固定、只读 JSON Schema"); Text(PromptDefaults.schemaFor(selected), style = MaterialTheme.typography.bodySmall) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button({ vm.savePrompt(draft) }, enabled = validation.valid, modifier = Modifier.weight(1f)) { Text("保存") }
                OutlinedButton({ vm.resetPrompt(selected) }, Modifier.weight(1f)) { Icon(Icons.Default.Refresh, null); Text("恢复本项") }
            }
            OutlinedButton({ vm.previewPrompt(draft) }, Modifier.fillMaxWidth()) { Text("预览最终请求") }
            TextButton({ resetAll = true }, Modifier.align(Alignment.CenterHorizontally)) { Text("恢复全部默认提示词", color = MaterialTheme.colorScheme.error) }
        }
    }
    if (resetAll) AlertDialog(onDismissRequest = { resetAll = false }, title = { Text("恢复全部默认值？") }, text = { Text("七组提示词和三份评分规则都会被覆盖。") }, confirmButton = { TextButton({ vm.resetAllPrompts(); resetAll = false }) { Text("恢复") } }, dismissButton = { TextButton({ resetAll = false }) { Text("取消") } })
    ui.promptPreview?.let { preview ->
        AlertDialog(
            onDismissRequest = vm::dismissPromptPreview,
            title = { Text("最终请求预览") },
            text = { Column(Modifier.height(430.dp).verticalScroll(rememberScrollState())) { Text(preview) } },
            confirmButton = { TextButton(vm::dismissPromptPreview) { Text("关闭") } },
        )
    }
}
