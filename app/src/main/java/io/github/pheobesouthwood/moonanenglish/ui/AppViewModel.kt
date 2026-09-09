package io.github.pheobesouthwood.moonanenglish.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.pheobesouthwood.moonanenglish.data.AppStore
import io.github.pheobesouthwood.moonanenglish.data.BackupManager
import io.github.pheobesouthwood.moonanenglish.data.BackupPreview
import io.github.pheobesouthwood.moonanenglish.data.ExamRepository
import io.github.pheobesouthwood.moonanenglish.data.ImageStore
import io.github.pheobesouthwood.moonanenglish.data.ResultParser
import io.github.pheobesouthwood.moonanenglish.data.ai.AiProviderFactory
import io.github.pheobesouthwood.moonanenglish.domain.AiImage
import io.github.pheobesouthwood.moonanenglish.domain.AiProtocol
import io.github.pheobesouthwood.moonanenglish.domain.AiRequest
import io.github.pheobesouthwood.moonanenglish.domain.AiStage
import io.github.pheobesouthwood.moonanenglish.domain.AppearanceMode
import io.github.pheobesouthwood.moonanenglish.domain.ExamQuestion
import io.github.pheobesouthwood.moonanenglish.domain.GradingJsonSchemas
import io.github.pheobesouthwood.moonanenglish.domain.GradingRevision
import io.github.pheobesouthwood.moonanenglish.domain.InputMode
import io.github.pheobesouthwood.moonanenglish.domain.PracticeSession
import io.github.pheobesouthwood.moonanenglish.domain.PromptDefaults
import io.github.pheobesouthwood.moonanenglish.domain.PromptTemplate
import io.github.pheobesouthwood.moonanenglish.domain.PromptVariableValidator
import io.github.pheobesouthwood.moonanenglish.domain.PromptVariables
import io.github.pheobesouthwood.moonanenglish.domain.ProviderDefaults
import io.github.pheobesouthwood.moonanenglish.domain.ProviderProfile
import io.github.pheobesouthwood.moonanenglish.domain.QuestionType
import io.github.pheobesouthwood.moonanenglish.domain.StageAssignment
import io.github.pheobesouthwood.moonanenglish.domain.StageStatus
import io.github.pheobesouthwood.moonanenglish.domain.StoredAppState
import io.github.pheobesouthwood.moonanenglish.domain.StoredImage
import io.github.pheobesouthwood.moonanenglish.domain.UserSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Base64
import java.util.UUID
import java.util.concurrent.TimeUnit
import org.json.JSONObject
import okhttp3.OkHttpClient

data class AppUiState(
    val ready: Boolean = false,
    val stored: StoredAppState = StoredAppState(
        providers = ProviderDefaults.profiles,
        prompts = PromptDefaults.allDefaults().values.toList(),
    ),
    val years: List<Int> = emptyList(),
    val selectedYear: Int = 2026,
    val selectedType: QuestionType = QuestionType.TRANSLATION,
    val answer: String = "",
    val draftImages: List<StoredImage> = emptyList(),
    val busyMessage: String? = null,
    val notice: String? = null,
    val error: String? = null,
    val backupPreview: BackupPreview? = null,
    val activeSessionId: String? = null,
    val latestRevision: GradingRevision? = null,
    val promptPreview: String? = null,
)

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val exams = ExamRepository(application)
    private val store = AppStore(application)
    private val images = ImageStore(application)
    private val backups = BackupManager(application)
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .callTimeout(150, TimeUnit.SECONDS)
        .build()
    private var activeAiJob: Job? = null
    private val _ui = MutableStateFlow(AppUiState())
    val ui: StateFlow<AppUiState> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            val years = runCatching { exams.years() }.getOrElse {
                _ui.update { state -> state.copy(error = "题库读取失败：${it.message}") }
                emptyList()
            }
            val state = store.load()
            _ui.update { it.copy(ready = true, years = years, selectedYear = years.firstOrNull() ?: 2026, stored = state) }
        }
    }

    fun question(): ExamQuestion? = exams.find(_ui.value.selectedYear, _ui.value.selectedType)
    fun availableTypes(): List<QuestionType> = exams.forYear(_ui.value.selectedYear).map { it.questionType }

    fun selectYear(year: Int) {
        discardUnstoredDraftImages()
        val types = exams.forYear(year).map { it.questionType }
        _ui.update { it.copy(selectedYear = year, selectedType = it.selectedType.takeIf(types::contains) ?: types.first(), answer = "", draftImages = emptyList(), activeSessionId = null, latestRevision = null) }
    }

    fun selectType(type: QuestionType) {
        discardUnstoredDraftImages()
        _ui.update { it.copy(selectedType = type, answer = "", draftImages = emptyList(), activeSessionId = null, latestRevision = null) }
    }
    fun setAnswer(value: String) = _ui.update { it.copy(answer = value) }
    fun clearMessage() = _ui.update { it.copy(error = null, notice = null) }

    fun importImages(uris: List<Uri>) = viewModelScope.launch {
        if (_ui.value.draftImages.size + uris.size > 6) {
            _ui.update { it.copy(error = "每次最多选择 6 张图片") }; return@launch
        }
        runBusy("正在导入图片…") {
            val imported = withContext(Dispatchers.IO) { uris.map(images::import) }
            _ui.update { it.copy(draftImages = it.draftImages + imported, notice = "已导入 ${imported.size} 张图片") }
        }
    }

    fun addCaptured(file: File) = viewModelScope.launch {
        if (_ui.value.draftImages.size >= 6) { file.delete(); _ui.update { it.copy(error = "每次最多选择 6 张图片") }; return@launch }
        runCatching { images.fromCaptured(file) }
            .onSuccess { captured -> _ui.update { it.copy(draftImages = it.draftImages + captured) } }
            .onFailure { failure -> _ui.update { it.copy(error = failure.message) } }
    }

    fun newCameraTarget() = images.newCameraUri()
    fun imageFile(image: StoredImage): File = images.resolve(image)

    fun moveImage(index: Int, direction: Int) {
        val target = index + direction
        val list = _ui.value.draftImages.toMutableList()
        if (index !in list.indices || target !in list.indices) return
        val item = list.removeAt(index); list.add(target, item)
        _ui.update { it.copy(draftImages = list) }
    }

    fun rotateImage(index: Int) = transformImage(index) { it.copy(rotationDegrees = (it.rotationDegrees + 90) % 360) }
    fun cropImage(index: Int) = transformImage(index) { it.copy(cropInsetPercent = (it.cropInsetPercent + 5) % 25) }
    fun removeImage(index: Int) {
        val item = _ui.value.draftImages.getOrNull(index) ?: return
        images.delete(item)
        _ui.update { it.copy(draftImages = it.draftImages.filterIndexed { i, _ -> i != index }) }
    }

    private fun transformImage(index: Int, transform: (StoredImage) -> StoredImage) {
        _ui.update { state -> state.copy(draftImages = state.draftImages.mapIndexed { i, image -> if (i == index) transform(image) else image }) }
    }

    private fun discardUnstoredDraftImages() {
        if (_ui.value.activeSessionId == null) _ui.value.draftImages.forEach(images::delete)
    }

    fun saveProvider(profile: ProviderProfile) = mutateStored { state ->
        state.copy(providers = state.providers.filterNot { it.id == profile.id } + profile)
    }

    fun deleteProvider(id: String) = mutateStored { state ->
        state.copy(
            providers = state.providers.filterNot { it.id == id },
            assignments = state.assignments.filterNot { it.providerId == id },
        )
    }

    fun savePrompt(template: PromptTemplate) = mutateStored { state ->
        state.copy(prompts = state.prompts.filterNot { it.stage == template.stage } + template.copy(revision = template.revision + 1))
    }

    fun resetPrompt(stage: AiStage) = savePrompt(PromptDefaults.defaultFor(stage).copy(revision = 0))
    fun resetAllPrompts() = mutateStored { it.copy(prompts = PromptDefaults.allDefaults().values.toList()) }

    fun saveAssignment(assignment: StageAssignment) = mutateStored { state ->
        state.copy(assignments = state.assignments.filterNot { it.stage == assignment.stage } + assignment)
    }

    fun saveSettings(settings: UserSettings) = mutateStored { it.copy(settings = settings) }

    fun previewPrompt(template: PromptTemplate) {
        val sample = mapOf(
            PromptVariables.YEAR to _ui.value.selectedYear.toString(),
            PromptVariables.QUESTION to (question()?.contentMarkdown ?: "示例题目"),
            PromptVariables.QUESTION_TYPE to _ui.value.selectedType.label,
            PromptVariables.MAX_SCORE to (question()?.maxScore ?: 10).toString(),
            PromptVariables.ANSWER to _ui.value.answer.ifBlank { "示例答案" },
            PromptVariables.RUBRIC to template.rubric.ifBlank { "示例评分规则" },
            PromptVariables.GRADING_RESULT to "示例评分结果",
            PromptVariables.IMAGES to "1 张图片",
            PromptVariables.LANGUAGE to "简体中文",
        )
        runCatching { PromptVariableValidator.render(template, sample) }
            .onSuccess { rendered -> _ui.update { it.copy(promptPreview = "SYSTEM\n${rendered.systemPrompt}\n\nUSER\n${withSchema(rendered.userPrompt, PromptDefaults.schemaFor(template.stage))}") } }
            .onFailure { failure -> _ui.update { it.copy(error = failure.message) } }
    }

    fun dismissPromptPreview() = _ui.update { it.copy(promptPreview = null) }

    fun fetchModels(providerId: String) = viewModelScope.launch {
        val provider = _ui.value.stored.providers.firstOrNull { it.id == providerId } ?: return@launch
        runBusy("正在拉取模型列表…") {
            val models = withContext(Dispatchers.IO) { AiProviderFactory.create(provider, httpClient).listModels() }
            saveProvider(provider.copy(modelIds = models.map { it.id }.distinct()))
            _ui.update { it.copy(notice = if (models.isEmpty()) "接口未返回模型；仍可手动填写" else "已获取 ${models.size} 个模型") }
        }
    }

    fun testProvider(providerId: String) = viewModelScope.launch {
        val provider = _ui.value.stored.providers.firstOrNull { it.id == providerId } ?: return@launch
        runBusy("正在测试连接…") {
            val models = withContext(Dispatchers.IO) { AiProviderFactory.create(provider, httpClient).listModels() }
            _ui.update { it.copy(notice = "连接成功${if (models.isNotEmpty()) "，发现 ${models.size} 个模型" else ""}") }
        }
    }

    fun recognizeImages(providerId: String, modelOverride: String? = null) {
        activeAiJob?.cancel()
        activeAiJob = viewModelScope.launch {
            val provider = _ui.value.stored.providers.firstOrNull { it.id == providerId }
            if (provider == null || !provider.enabled) { _ui.update { it.copy(error = "识别提供商未启用") }; return@launch }
            runBusy("正在识别 ${_ui.value.draftImages.size} 张图片…") {
                val payloads = withContext(Dispatchers.IO) {
                    _ui.value.draftImages.map { image ->
                        AiImage(Base64.getEncoder().encodeToString(images.prepareForUpload(image)), "image/jpeg", image.id)
                    }
                }
                val assignment = _ui.value.stored.assignments.firstOrNull { it.stage == AiStage.VISION_RECOGNITION }
                val model = modelOverride?.takeIf(String::isNotBlank) ?: assignment?.takeIf { it.providerId == providerId }?.modelId
                    ?: provider.modelIds.firstOrNull().orEmpty()
                val template = _ui.value.stored.prompts.first { it.stage == AiStage.VISION_RECOGNITION }
                val rendered = PromptVariableValidator.render(template, mapOf(PromptVariables.IMAGES to "${payloads.size} 张图片"))
                val response = withContext(Dispatchers.IO) {
                    AiProviderFactory.create(provider, httpClient).complete(
                        AiRequest(AiStage.VISION_RECOGNITION, model, rendered.systemPrompt, withSchema(rendered.userPrompt, GradingJsonSchemas.OCR), payloads, responseJsonSchema = GradingJsonSchemas.OCR)
                    )
                }
                val text = response.structuredJson?.let { JSONObject(it).optString("text") }.orEmpty().ifBlank { response.text }
                _ui.update { it.copy(answer = text, notice = "识别完成，请检查识别稿") }
            }
            if (_ui.value.stored.settings.autoGradeAfterRecognition && _ui.value.answer.isNotBlank()) startGrading()
        }
    }

    fun startGrading(
        gradingProviderOverride: String? = null,
        gradingModelOverride: String? = null,
        reviewProviderOverride: String? = null,
        reviewModelOverride: String? = null,
    ) {
        activeAiJob?.cancel()
        activeAiJob = viewModelScope.launch {
            try {
                performGrading(gradingProviderOverride, gradingModelOverride, reviewProviderOverride, reviewModelOverride)
            } catch (_: CancellationException) {
                _ui.update { it.copy(busyMessage = null, notice = "已取消当前阶段") }
            } catch (failure: Throwable) {
                _ui.update { it.copy(busyMessage = null, error = failure.message ?: "评分失败") }
            }
        }
    }

    fun cancelActive() { httpClient.dispatcher.cancelAll(); activeAiJob?.cancel() }

    fun retryLatestReview(providerOverride: String? = null, modelOverride: String? = null) {
        val revision = _ui.value.latestRevision ?: return
        val session = _ui.value.stored.sessions.firstOrNull { it.id == revision.sessionId } ?: return
        val question = exams.byId(session.questionId) ?: return
        val stage = reviewStage(question.questionType)
        activeAiJob?.cancel()
        activeAiJob = viewModelScope.launch {
            try {
                val selection = resolveSelection(stage, providerOverride, modelOverride)
                val template = _ui.value.stored.prompts.first { it.stage == stage }
                val gradingText = revision.gradingRawResponse
                require(gradingText.isNotBlank()) { "没有可用于复习指导的评分结果" }
                val rendered = PromptVariableValidator.render(template, promptValues(question, revision.answerSnapshot, template, gradingText))
                var running = revision.copy(reviewStatus = StageStatus.RUNNING, errorMessage = null)
                persistRevision(session, running)
                _ui.update { it.copy(latestRevision = running, busyMessage = "正在重试复习指导…") }
                val response = withContext(Dispatchers.IO) {
                    AiProviderFactory.create(selection.first, httpClient).complete(
                    AiRequest(stage, selection.second, rendered.systemPrompt, withSchema(rendered.userPrompt, GradingJsonSchemas.REVIEW), responseJsonSchema = GradingJsonSchemas.REVIEW)
                    )
                }
                val parsed = ResultParser.review(response.structuredJson, response.text)
                running = running.copy(
                    reviewStatus = if (parsed == null) StageStatus.RAW_ONLY else StageStatus.SUCCEEDED,
                    review = parsed,
                    reviewRawResponse = response.text,
                    modelSnapshot = running.modelSnapshot.substringBefore(" → ") + " → ${selection.first.name}/${selection.second}",
                )
                persistRevision(session, running)
                _ui.update { it.copy(latestRevision = running, busyMessage = null, notice = "复习指导已完成") }
            } catch (_: CancellationException) {
                _ui.update { it.copy(busyMessage = null, notice = "已取消复习指导") }
            } catch (failure: Throwable) {
                val failed = revision.copy(reviewStatus = StageStatus.FAILED, errorMessage = "复习指导失败：${failure.message}")
                persistRevision(session, failed)
                _ui.update { it.copy(latestRevision = failed, busyMessage = null, error = failed.errorMessage) }
            }
        }
    }

    private suspend fun performGrading(
        gradingProviderOverride: String?, gradingModelOverride: String?, reviewProviderOverride: String?, reviewModelOverride: String?,
    ) {
        val snapshot = _ui.value
        val question = question() ?: error("当前题目不可用")
        require(snapshot.answer.isNotBlank()) { "请先填写答案" }
        val gradingStage = gradingStage(question.questionType)
        val reviewStage = reviewStage(question.questionType)
        val gradeSelection = resolveSelection(gradingStage, gradingProviderOverride, gradingModelOverride)
        val reviewSelection = resolveSelection(reviewStage, reviewProviderOverride, reviewModelOverride)
        val sessionId = snapshot.activeSessionId ?: UUID.randomUUID().toString()
        val revisionId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val session = snapshot.stored.sessions.firstOrNull { it.id == sessionId } ?: PracticeSession(
            id = sessionId,
            questionId = question.id,
            year = question.year,
            questionType = question.questionType,
            inputMode = if (snapshot.draftImages.isEmpty()) InputMode.TEXT else InputMode.IMAGES,
            images = snapshot.draftImages,
            recognizedText = if (snapshot.draftImages.isEmpty()) "" else snapshot.answer,
            currentAnswer = snapshot.answer,
            createdAtEpochMs = now,
        )
        val gradeTemplate = snapshot.stored.prompts.first { it.stage == gradingStage }
        val values = promptValues(question, snapshot.answer, gradeTemplate, null)
        val rendered = PromptVariableValidator.render(gradeTemplate, values)
        var revision = GradingRevision(
            id = revisionId, sessionId = sessionId, answerSnapshot = snapshot.answer,
            questionSnapshot = buildString {
                append(question.contentMarkdown)
                if (question.translationSegments.isNotEmpty()) append("\n\n待译句：\n${question.translationSegments.joinToString("\n")}")
                question.imageDescription?.let { append("\n\n图表/图画描述：\n$it") }
            },
            promptSnapshot = "${rendered.systemPrompt}\n\n${withSchema(rendered.userPrompt, GradingJsonSchemas.GRADING)}",
            modelSnapshot = "${gradeSelection.first.name}/${gradeSelection.second}", gradingStatus = StageStatus.RUNNING,
            createdAtEpochMs = now,
        )
        persistRevision(session, revision)
        _ui.update { it.copy(activeSessionId = sessionId, latestRevision = revision, busyMessage = "正在严格评分…") }

        val gradeResponse = try {
            withContext(Dispatchers.IO) {
                AiProviderFactory.create(gradeSelection.first, httpClient).complete(
                    AiRequest(gradingStage, gradeSelection.second, rendered.systemPrompt, withSchema(rendered.userPrompt, GradingJsonSchemas.GRADING), responseJsonSchema = GradingJsonSchemas.GRADING)
                )
            }
        } catch (failure: Throwable) {
            revision = revision.copy(gradingStatus = StageStatus.FAILED, errorMessage = failure.message)
            persistRevision(session, revision); _ui.update { it.copy(latestRevision = revision) }; throw failure
        }
        val grade = ResultParser.grading(gradeResponse.structuredJson, gradeResponse.text, question.maxScore)
        revision = revision.copy(
            gradingStatus = if (grade == null) StageStatus.RAW_ONLY else StageStatus.SUCCEEDED,
            grading = grade, gradingRawResponse = gradeResponse.text,
        )
        persistRevision(session, revision)
        _ui.update { it.copy(latestRevision = revision, busyMessage = "正在生成复习指导…") }

        val reviewTemplate = snapshot.stored.prompts.first { it.stage == reviewStage }
        val reviewValues = promptValues(question, snapshot.answer, reviewTemplate, gradeResponse.text)
        val reviewPrompt = PromptVariableValidator.render(reviewTemplate, reviewValues)
        revision = revision.copy(reviewStatus = StageStatus.RUNNING)
        persistRevision(session, revision)
        try {
            val reviewResponse = withContext(Dispatchers.IO) {
                AiProviderFactory.create(reviewSelection.first, httpClient).complete(
                    AiRequest(reviewStage, reviewSelection.second, reviewPrompt.systemPrompt, withSchema(reviewPrompt.userPrompt, GradingJsonSchemas.REVIEW), responseJsonSchema = GradingJsonSchemas.REVIEW)
                )
            }
            val review = ResultParser.review(reviewResponse.structuredJson, reviewResponse.text)
            revision = revision.copy(
                reviewStatus = if (review == null) StageStatus.RAW_ONLY else StageStatus.SUCCEEDED,
                review = review, reviewRawResponse = reviewResponse.text,
                promptSnapshot = revision.promptSnapshot + "\n\n--- REVIEW ---\n${reviewPrompt.systemPrompt}\n\n${withSchema(reviewPrompt.userPrompt, GradingJsonSchemas.REVIEW)}",
                modelSnapshot = revision.modelSnapshot + " → ${reviewSelection.first.name}/${reviewSelection.second}",
            )
            persistRevision(session, revision)
            _ui.update { it.copy(latestRevision = revision, busyMessage = null, notice = "评分与复习指导已完成") }
        } catch (failure: Throwable) {
            revision = revision.copy(reviewStatus = StageStatus.FAILED, errorMessage = "复习指导失败：${failure.message}")
            persistRevision(session, revision)
            _ui.update { it.copy(latestRevision = revision, busyMessage = null, error = revision.errorMessage) }
        }
    }

    private fun resolveSelection(stage: AiStage, providerOverride: String?, modelOverride: String?): Pair<ProviderProfile, String> {
        val assignment = _ui.value.stored.assignments.firstOrNull { it.stage == stage }
        val providerId = providerOverride?.takeIf(String::isNotBlank) ?: assignment?.providerId
        val provider = _ui.value.stored.providers.firstOrNull { it.id == providerId && it.enabled }
            ?: error("请先为“${stage.name}”绑定并启用 AI 提供商")
        require(provider.protocol != AiProtocol.BAIDU_OCR) { "百度 OCR 不能用于评分" }
        val model = modelOverride?.takeIf(String::isNotBlank) ?: assignment?.modelId ?: provider.modelIds.firstOrNull()
        require(!model.isNullOrBlank()) { "请为 ${provider.name} 填写模型 ID" }
        return provider to model
    }

    private fun promptValues(question: ExamQuestion, answer: String, template: PromptTemplate, grading: String?) = mapOf(
        PromptVariables.YEAR to question.year.toString(), PromptVariables.QUESTION to buildString { append(question.contentMarkdown); question.imageDescription?.let { append("\n\n$it") } },
        PromptVariables.QUESTION_TYPE to question.questionType.label, PromptVariables.MAX_SCORE to question.maxScore.toString(),
        PromptVariables.ANSWER to answer, PromptVariables.RUBRIC to template.rubric,
        PromptVariables.GRADING_RESULT to (grading ?: "尚无评分结果"), PromptVariables.IMAGES to "${_ui.value.draftImages.size} 张图片",
        PromptVariables.LANGUAGE to "简体中文",
    )

    private fun gradingStage(type: QuestionType) = when (type) {
        QuestionType.TRANSLATION -> AiStage.TRANSLATION_GRADING
        QuestionType.SMALL_ESSAY -> AiStage.SHORT_ESSAY_GRADING
        QuestionType.LARGE_ESSAY -> AiStage.LONG_ESSAY_GRADING
    }
    private fun reviewStage(type: QuestionType) = when (type) {
        QuestionType.TRANSLATION -> AiStage.TRANSLATION_REVIEW
        QuestionType.SMALL_ESSAY -> AiStage.SHORT_ESSAY_REVIEW
        QuestionType.LARGE_ESSAY -> AiStage.LONG_ESSAY_REVIEW
    }

    private fun withSchema(prompt: String, schema: String): String =
        "$prompt\n\n固定输出 JSON Schema（只读）：\n$schema"

    private suspend fun persistRevision(base: PracticeSession, revision: GradingRevision) {
        val currentState = _ui.value.stored
        val existing = currentState.sessions.firstOrNull { it.id == base.id } ?: base
        val updatedSession = existing.copy(
            currentAnswer = revision.answerSnapshot,
            revisions = existing.revisions.filterNot { it.id == revision.id } + revision,
        )
        val updated = currentState.copy(sessions = currentState.sessions.filterNot { it.id == base.id } + updatedSession)
        store.save(updated)
        _ui.update { it.copy(stored = updated) }
    }

    fun deleteSession(id: String) = mutateStored { state ->
        val session = state.sessions.firstOrNull { it.id == id }
        session?.images?.forEach(images::delete)
        state.copy(sessions = state.sessions.filterNot { it.id == id })
    }

    fun deleteAllSessions() = mutateStored { state -> images.clear(); state.copy(sessions = emptyList()) }

    fun exportBackup(uri: Uri) = viewModelScope.launch {
        runBusy("正在导出明文备份…") {
            backups.export(uri, _ui.value.stored)
            _ui.update { it.copy(notice = "明文备份已导出；请妥善保管其中的 API 密钥") }
        }
    }

    fun inspectBackup(uri: Uri) = viewModelScope.launch {
        runBusy("正在校验备份…") {
            val preview = backups.preview(uri)
            _ui.update { it.copy(backupPreview = preview) }
        }
    }

    fun dismissBackupPreview() = _ui.update { it.copy(backupPreview = null) }
    fun restoreBackup() = viewModelScope.launch {
        val preview = _ui.value.backupPreview ?: return@launch
        runBusy("正在恢复备份…") {
            val restored = backups.restore(preview, store)
            _ui.update { it.copy(stored = restored, backupPreview = null, notice = "备份已恢复") }
        }
    }

    private fun mutateStored(transform: (StoredAppState) -> StoredAppState) {
        val updated = transform(_ui.value.stored)
        _ui.update { it.copy(stored = updated) }
        viewModelScope.launch { runCatching { store.save(updated) }.onFailure { failure -> _ui.update { it.copy(error = "保存失败：${failure.message}") } } }
    }

    private suspend fun runBusy(message: String, block: suspend () -> Unit) {
        _ui.update { it.copy(busyMessage = message, error = null) }
        try { block() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Throwable) { _ui.update { it.copy(error = failure.message ?: "操作失败") } }
        finally { _ui.update { it.copy(busyMessage = null) } }
    }
}
