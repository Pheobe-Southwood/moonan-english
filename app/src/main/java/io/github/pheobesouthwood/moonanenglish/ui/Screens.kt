package io.github.pheobesouthwood.moonanenglish.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.TextSnippet
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.pheobesouthwood.moonanenglish.domain.AppearanceMode
import io.github.pheobesouthwood.moonanenglish.domain.AiStage
import io.github.pheobesouthwood.moonanenglish.domain.PracticeSession
import io.github.pheobesouthwood.moonanenglish.domain.QuestionType
import io.github.pheobesouthwood.moonanenglish.domain.StageStatus
import java.text.SimpleDateFormat
import android.graphics.BitmapFactory
import java.util.Date
import java.util.Locale
import java.io.File
import android.graphics.Bitmap

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PracticeScreen(
    padding: PaddingValues,
    ui: AppUiState,
    vm: AppViewModel,
    pickImages: () -> Unit,
    takePhoto: () -> Unit,
) {
    var yearMenu by remember { mutableStateOf(false) }
    var showAiChoice by remember { mutableStateOf(false) }
    val question = vm.question()
    val gradeStage = when (ui.selectedType) {
        QuestionType.TRANSLATION -> AiStage.TRANSLATION_GRADING
        QuestionType.SMALL_ESSAY -> AiStage.SHORT_ESSAY_GRADING
        QuestionType.LARGE_ESSAY -> AiStage.LONG_ESSAY_GRADING
    }
    val reviewStage = when (ui.selectedType) {
        QuestionType.TRANSLATION -> AiStage.TRANSLATION_REVIEW
        QuestionType.SMALL_ESSAY -> AiStage.SHORT_ESSAY_REVIEW
        QuestionType.LARGE_ESSAY -> AiStage.LONG_ESSAY_REVIEW
    }
    val gradeAssignment = ui.stored.assignments.firstOrNull { it.stage == gradeStage }
    val reviewAssignment = ui.stored.assignments.firstOrNull { it.stage == reviewStage }
    var gradeProvider by remember(question?.id, gradeAssignment?.providerId) { mutableStateOf(gradeAssignment?.providerId.orEmpty()) }
    var gradeModel by remember(question?.id, gradeAssignment?.modelId) { mutableStateOf(gradeAssignment?.modelId.orEmpty()) }
    var reviewProvider by remember(question?.id, reviewAssignment?.providerId) { mutableStateOf(reviewAssignment?.providerId.orEmpty()) }
    var reviewModel by remember(question?.id, reviewAssignment?.modelId) { mutableStateOf(reviewAssignment?.modelId.orEmpty()) }
    LazyColumn(
        Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { Text("练习", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold) }
        item {
            GroupCard {
                Text("年份", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                TextButton(onClick = { yearMenu = true }) { Text("${ui.selectedYear} 年") }
                DropdownMenu(expanded = yearMenu, onDismissRequest = { yearMenu = false }) {
                    ui.years.forEach { year -> DropdownMenuItem(text = { Text("$year 年") }, onClick = { vm.selectYear(year); yearMenu = false }) }
                }
                HorizontalDivider()
                Text("题型", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    vm.availableTypes().forEach { type ->
                        FilterChip(selected = ui.selectedType == type, onClick = { vm.selectType(type) }, label = { Text(type.label) })
                    }
                }
            }
        }
        item {
            GroupCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${ui.selectedYear} · ${ui.selectedType.label}", fontWeight = FontWeight.SemiBold)
                    Text("${question?.maxScore ?: 0} 分", color = MaterialTheme.colorScheme.secondary)
                }
                Spacer(Modifier.height(10.dp))
                Text(question?.contentMarkdown ?: "该题型不可用", style = MaterialTheme.typography.bodyLarge)
                question?.translationSegments?.takeIf { it.isNotEmpty() }?.let { segments ->
                    Spacer(Modifier.height(12.dp))
                    Text("待翻译的 5 个句段", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                    segments.forEachIndexed { index, segment -> Text("${index + 1}. $segment") }
                }
                question?.imageDescription?.takeIf { it.isNotBlank() }?.let {
                    Spacer(Modifier.height(12.dp)); Text("图表/图画描述", fontWeight = FontWeight.SemiBold); Text(it)
                }
            }
        }
        item {
            GroupCard {
                Text("我的答案", fontWeight = FontWeight.SemiBold)
                OutlinedTextField(
                    value = ui.answer,
                    onValueChange = vm::setAnswer,
                    modifier = Modifier.fillMaxWidth().height(220.dp),
                    placeholder = { Text("在这里输入，或导入答题卡后识别…") },
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = takePhoto, modifier = Modifier.weight(1f)) { Icon(Icons.Default.AddAPhoto, null); Spacer(Modifier.width(6.dp)); Text("拍照") }
                    OutlinedButton(onClick = pickImages, modifier = Modifier.weight(1f)) { Icon(Icons.Default.PhotoLibrary, null); Spacer(Modifier.width(6.dp)); Text("相册") }
                }
            }
        }
        if (ui.draftImages.isNotEmpty()) {
            item { Text("答题图片 ${ui.draftImages.size}/6", fontWeight = FontWeight.SemiBold) }
            itemsIndexed(ui.draftImages, key = { _, image -> image.id }) { index, image ->
                GroupCard {
                    remember(image.id, image.rotationDegrees, image.cropInsetPercent) { loadThumbnail(vm.imageFile(image)) }?.let { bitmap ->
                        Image(
                            bitmap.asImageBitmap(),
                            contentDescription = "第 ${index + 1} 张答题图片",
                            modifier = Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(10.dp)).rotate(image.rotationDegrees.toFloat()),
                            contentScale = ContentScale.Fit,
                        )
                    }
                    Text("第 ${index + 1} 张 · 旋转 ${image.rotationDegrees}° · 裁边 ${image.cropInsetPercent}%")
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        IconButton({ vm.moveImage(index, -1) }, enabled = index > 0) { Icon(Icons.Default.ArrowUpward, "上移") }
                        IconButton({ vm.moveImage(index, 1) }, enabled = index < ui.draftImages.lastIndex) { Icon(Icons.Default.ArrowDownward, "下移") }
                        IconButton({ vm.rotateImage(index) }) { Icon(Icons.Default.RotateRight, "旋转") }
                        IconButton({ vm.cropImage(index) }) { Icon(Icons.Default.Crop, "裁剪边缘") }
                        IconButton({ vm.removeImage(index) }) { Icon(Icons.Default.Delete, "删除", tint = MaterialTheme.colorScheme.error) }
                    }
                }
            }
            item {
                OutlinedButton(onClick = { showAiChoice = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.TextSnippet, null); Spacer(Modifier.width(8.dp)); Text("选择方式并识别图片")
                }
            }
        }
        item {
            GroupCard {
                Text("本次使用的模型", fontWeight = FontWeight.SemiBold)
                AttemptModelRow("严格评分", gradeProvider, gradeModel, ui, onProvider = { id -> gradeProvider = id; gradeModel = ui.stored.providers.firstOrNull { it.id == id }?.modelIds?.firstOrNull().orEmpty() }, onModel = { gradeModel = it })
                HorizontalDivider()
                AttemptModelRow("复习指导", reviewProvider, reviewModel, ui, onProvider = { id -> reviewProvider = id; reviewModel = ui.stored.providers.firstOrNull { it.id == id }?.modelIds?.firstOrNull().orEmpty() }, onModel = { reviewModel = it })
                Text("这里只覆盖本次练习，不会改动阶段默认绑定。", style = MaterialTheme.typography.bodySmall)
            }
        }
        item {
            Button(onClick = { vm.startGrading(gradeProvider, gradeModel, reviewProvider, reviewModel) }, enabled = question != null && ui.answer.isNotBlank(), modifier = Modifier.fillMaxWidth().height(50.dp)) {
                Icon(Icons.Default.AutoAwesome, null); Spacer(Modifier.width(8.dp)); Text("开始严格评分")
            }
        }
        ui.latestRevision?.let { revision ->
            item {
                GroupCard {
                    Text("最新评分版本", style = MaterialTheme.typography.titleMedium)
                    Text("评分状态：${revision.gradingStatus} · 指导状态：${revision.reviewStatus}")
                    revision.grading?.let { result ->
                        Text("${result.score}/${result.maxScore}", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
                        Text(result.summary)
                        result.deductions.forEach { Text("${it.category}：-${it.pointsDeducted}，${it.explanation}") }
                        if (result.revisedAnswer.isNotBlank()) { Text("参考修改", fontWeight = FontWeight.SemiBold); Text(result.revisedAnswer) }
                    }
                    revision.review?.let { review ->
                        Text("复习指导", fontWeight = FontWeight.SemiBold)
                        review.studyAdvice.forEach { Text("• $it") }
                        review.grammarIssues.forEach { Text("${it.original} → ${it.correction}\n${it.explanation}") }
                        review.sentenceRevisions.forEach { Text("${it.original} → ${it.improved}") }
                    }
                    if (revision.grading == null && revision.gradingRawResponse.isNotBlank()) Text("结构化解析失败，原始响应：\n${revision.gradingRawResponse}")
                    if (revision.review == null && revision.reviewRawResponse.isNotBlank()) Text("指导原始响应：\n${revision.reviewRawResponse}")
                    revision.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    if (revision.gradingStatus == StageStatus.FAILED || revision.gradingStatus == StageStatus.RAW_ONLY) {
                        OutlinedButton({ vm.startGrading(gradeProvider, gradeModel, reviewProvider, reviewModel) }, Modifier.fillMaxWidth()) { Text("重试严格评分（创建新版本）") }
                    } else if (revision.reviewStatus == StageStatus.FAILED || revision.reviewStatus == StageStatus.RAW_ONLY) {
                        OutlinedButton({ vm.retryLatestReview(reviewProvider, reviewModel) }, Modifier.fillMaxWidth()) { Text("单独重试复习指导") }
                    }
                }
            }
        }
    }

    if (showAiChoice) RecognitionChoiceDialog(ui, vm, onDismiss = { showAiChoice = false })
}

@Composable
private fun AttemptModelRow(
    label: String,
    providerId: String,
    model: String,
    ui: AppUiState,
    onProvider: (String) -> Unit,
    onModel: (String) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val providers = ui.stored.providers.filter { it.enabled && it.protocol.name != "BAIDU_OCR" }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        OutlinedButton({ menu = true }, Modifier.fillMaxWidth()) { Text(providers.firstOrNull { it.id == providerId }?.name ?: "选择提供商") }
        DropdownMenu(menu, { menu = false }) {
            providers.forEach { provider -> DropdownMenuItem({ Text(provider.name) }, { onProvider(provider.id); menu = false }) }
        }
        OutlinedTextField(model, onModel, label = { Text("模型 ID") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun RecognitionChoiceDialog(ui: AppUiState, vm: AppViewModel, onDismiss: () -> Unit) {
    val candidates = ui.stored.providers.filter { it.enabled && (it.protocol.name == "BAIDU_OCR" || it.supportsVision) }
    var providerId by remember(candidates) { mutableStateOf(candidates.firstOrNull()?.id.orEmpty()) }
    var model by remember(candidates) { mutableStateOf(candidates.firstOrNull()?.modelIds?.firstOrNull().orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择图片识别方式") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (candidates.isEmpty()) Text("请先在“设置 → AI 提供商”启用百度 OCR 或支持图片的视觉模型。")
                candidates.forEach { provider -> FilterChip(providerId == provider.id, { providerId = provider.id; model = provider.modelIds.firstOrNull().orEmpty() }, { Text(provider.name) }) }
                if (candidates.firstOrNull { it.id == providerId }?.protocol?.name != "BAIDU_OCR") {
                    OutlinedTextField(model, { model = it }, label = { Text("视觉模型 ID") }, modifier = Modifier.fillMaxWidth())
                }
                Text("识别稿会先写入答案框供你修改；是否自动评分由设置决定。", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(enabled = providerId.isNotBlank(), onClick = { onDismiss(); vm.recognizeImages(providerId, model) }) { Text("开始识别") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
fun HistoryScreen(padding: PaddingValues, ui: AppUiState, vm: AppViewModel) {
    var deleteId by remember { mutableStateOf<String?>(null) }
    var deleteAll by remember { mutableStateOf(false) }
    var yearFilter by remember { mutableStateOf<Int?>(null) }
    var typeFilter by remember { mutableStateOf<QuestionType?>(null) }
    var daysFilter by remember { mutableStateOf<Int?>(null) }
    var yearMenu by remember { mutableStateOf(false) }
    var typeMenu by remember { mutableStateOf(false) }
    val cutoff = daysFilter?.let { System.currentTimeMillis() - it * 86_400_000L }
    val filtered = ui.stored.sessions.filter { session ->
        (yearFilter == null || session.year == yearFilter) &&
            (typeFilter == null || session.questionType == typeFilter) &&
            (cutoff == null || session.createdAtEpochMs >= cutoff)
    }.sortedByDescending { it.createdAtEpochMs }
    LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("历史", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                if (ui.stored.sessions.isNotEmpty()) TextButton(onClick = { deleteAll = true }) { Text("全部删除", color = MaterialTheme.colorScheme.error) }
            }
        }
        item {
            GroupCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton({ yearMenu = true }, Modifier.weight(1f)) { Text(yearFilter?.toString() ?: "全部年份") }
                    DropdownMenu(yearMenu, { yearMenu = false }) {
                        DropdownMenuItem({ Text("全部年份") }, { yearFilter = null; yearMenu = false })
                        ui.stored.sessions.map { it.year }.distinct().sortedDescending().forEach { year -> DropdownMenuItem({ Text(year.toString()) }, { yearFilter = year; yearMenu = false }) }
                    }
                    OutlinedButton({ typeMenu = true }, Modifier.weight(1f)) { Text(typeFilter?.label ?: "全部题型") }
                    DropdownMenu(typeMenu, { typeMenu = false }) {
                        DropdownMenuItem({ Text("全部题型") }, { typeFilter = null; typeMenu = false })
                        QuestionType.entries.forEach { type -> DropdownMenuItem({ Text(type.label) }, { typeFilter = type; typeMenu = false }) }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(null to "全部日期", 7 to "7天", 30 to "30天").forEach { (days, label) -> FilterChip(daysFilter == days, { daysFilter = days }, { Text(label) }) }
                }
            }
        }
        if (ui.stored.sessions.isEmpty()) item { EmptyCard("还没有评分记录。完成一次严格评分后，所有版本都会保存在这里。") }
        else if (filtered.isEmpty()) item { EmptyCard("没有符合筛选条件的记录。") }
        items(filtered, key = { it.id }) { session ->
            HistoryCard(session, vm, onDelete = { deleteId = session.id })
        }
    }
    deleteId?.let { id -> ConfirmDelete("永久删除这次练习及原图？", { deleteId = null }, { vm.deleteSession(id); deleteId = null }) }
    if (deleteAll) ConfirmDelete("永久删除全部练习历史和原图？", { deleteAll = false }, { vm.deleteAllSessions(); deleteAll = false })
}

@Composable
private fun HistoryCard(session: PracticeSession, vm: AppViewModel, onDelete: () -> Unit) {
    var expanded by remember(session.id) { mutableStateOf(false) }
    GroupCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("${session.year} · ${session.questionType.label}", fontWeight = FontWeight.SemiBold)
                Text(SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date(session.createdAtEpochMs)), style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onDelete) { Icon(Icons.Default.Delete, "永久删除", tint = MaterialTheme.colorScheme.error) }
        }
        Text("${session.revisions.size} 个评分版本 · ${session.images.size} 张原图")
        TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "收起完整快照" else "查看答案、题目、提示词与原始响应") }
        if (expanded && session.images.isNotEmpty()) {
            session.images.forEachIndexed { index, stored ->
                remember(stored.id) { loadThumbnail(vm.imageFile(stored)) }?.let { bitmap ->
                    Image(bitmap.asImageBitmap(), "历史原图 ${index + 1}", Modifier.fillMaxWidth().height(130.dp).clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.Fit)
                }
            }
        }
        session.revisions.sortedByDescending { it.createdAtEpochMs }.forEachIndexed { index, revision ->
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text("版本 ${session.revisions.size - index} · ${revision.modelSnapshot.ifBlank { "未指定模型" }}", fontWeight = FontWeight.Medium)
            revision.grading?.let { Text("${it.score}/${it.maxScore} · ${it.summary}") }
            revision.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (revision.gradingRawResponse.isNotBlank()) Text("原始响应：${revision.gradingRawResponse}", style = MaterialTheme.typography.bodySmall)
            if (expanded) {
                Text("答案快照", fontWeight = FontWeight.SemiBold); Text(revision.answerSnapshot)
                Text("题目快照", fontWeight = FontWeight.SemiBold); Text(revision.questionSnapshot, style = MaterialTheme.typography.bodySmall)
                Text("模型快照", fontWeight = FontWeight.SemiBold); Text(revision.modelSnapshot)
                Text("提示词快照", fontWeight = FontWeight.SemiBold); Text(revision.promptSnapshot, style = MaterialTheme.typography.bodySmall)
                if (revision.reviewRawResponse.isNotBlank()) { Text("指导原始响应", fontWeight = FontWeight.SemiBold); Text(revision.reviewRawResponse, style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}

private fun loadThumbnail(file: File): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    var sample = 1
    while (bounds.outWidth / sample > 1200 || bounds.outHeight / sample > 1200) sample *= 2
    return BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
}

@Composable
fun SettingsScreen(
    padding: PaddingValues,
    ui: AppUiState,
    vm: AppViewModel,
    openPrompts: () -> Unit,
    openProviders: () -> Unit,
    exportBackup: () -> Unit,
    importBackup: () -> Unit,
) {
    val settings = ui.stored.settings
    LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Text("设置", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold) }
        item { SettingsLink(Icons.Default.TextSnippet, "提示词", "编辑七组完整提示词与评分规则", openPrompts) }
        item { SettingsLink(Icons.Default.Key, "AI 提供商", "密钥、模型、阶段绑定与连接测试", openProviders) }
        item {
            GroupCard {
                Text("识别后自动评分", fontWeight = FontWeight.SemiBold)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("关闭时先检查并修改识别稿", modifier = Modifier.weight(1f))
                    Switch(settings.autoGradeAfterRecognition, { vm.saveSettings(settings.copy(autoGradeAfterRecognition = it)) })
                }
                HorizontalDivider(Modifier.padding(vertical = 10.dp))
                Text("外观", fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppearanceMode.entries.forEach { mode ->
                        FilterChip(settings.appearanceMode == mode, { vm.saveSettings(settings.copy(appearanceMode = mode)) }, { Text(when (mode) { AppearanceMode.SYSTEM -> "跟随系统"; AppearanceMode.LIGHT -> "浅色"; AppearanceMode.DARK -> "深色" }) })
                    }
                }
            }
        }
        item {
            GroupCard {
                Text("全量明文备份", fontWeight = FontWeight.SemiBold)
                Text("包含 API 密钥、图片、提示词和所有历史，不进行任何加密。", color = MaterialTheme.colorScheme.error)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(exportBackup, Modifier.weight(1f)) { Icon(Icons.Default.FileUpload, null); Text("导出") }
                    OutlinedButton(importBackup, Modifier.weight(1f)) { Icon(Icons.Default.FileDownload, null); Text("恢复") }
                }
            }
        }
        item { EmptyCard("版本 0.0.1 · Android 12+\n本机 BYOK，无账号、服务器、云同步或自动重试。") }
    }
}

@Composable
private fun SettingsLink(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.SemiBold); Text(subtitle, style = MaterialTheme.typography.bodySmall) }
            Icon(Icons.Default.ChevronRight, null)
        }
    }
}

@Composable
fun GroupCard(content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

@Composable private fun EmptyCard(message: String) = GroupCard { Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant) }

@Composable
private fun ConfirmDelete(message: String, dismiss: () -> Unit, confirm: () -> Unit) = AlertDialog(
    onDismissRequest = dismiss,
    title = { Text("永久删除") },
    text = { Text(message) },
    confirmButton = { TextButton(onClick = confirm) { Text("删除", color = MaterialTheme.colorScheme.error) } },
    dismissButton = { TextButton(onClick = dismiss) { Text("取消") } },
)
