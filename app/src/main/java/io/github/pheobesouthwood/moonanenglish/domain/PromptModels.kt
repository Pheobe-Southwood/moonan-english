package io.github.pheobesouthwood.moonanenglish.domain

import kotlinx.serialization.Serializable

/** Compatibility name used by the prompt editor and the AI layer. */
typealias PromptStage = AiStage

@Serializable
data class PromptTemplate(
    val stage: PromptStage,
    val systemPrompt: String,
    val userTemplate: String,
    /** One of the three user-editable, project-defined scoring rubrics. */
    val rubric: String = "",
    val revision: Int = 1,
)

@Serializable
data class RenderedPrompt(
    val systemPrompt: String,
    val userPrompt: String,
    val rubric: String,
    val variables: Map<String, String>,
)

@Serializable
data class PromptValidation(
    val valid: Boolean,
    val errors: List<String> = emptyList(),
    val missingVariables: Set<String> = emptySet(),
) {
    companion object {
        fun ok() = PromptValidation(valid = true)
    }
}

class PromptValidationException(
    val validation: PromptValidation,
) : IllegalArgumentException(validation.errors.joinToString("；"))

/**
 * Prompt variables are deliberately small and explicit.  This prevents an
 * edited prompt from silently interpolating arbitrary local configuration.
 */
object PromptVariables {
    const val YEAR = "year"
    const val QUESTION = "question"
    const val QUESTION_TYPE = "questionType"
    const val MAX_SCORE = "maxScore"
    const val ANSWER = "answer"
    const val RUBRIC = "rubric"
    const val GRADING_RESULT = "gradingResult"
    const val IMAGES = "images"
    const val LANGUAGE = "language"

    val all: Set<String> = setOf(
        YEAR,
        QUESTION,
        QUESTION_TYPE,
        MAX_SCORE,
        ANSWER,
        RUBRIC,
        GRADING_RESULT,
        IMAGES,
        LANGUAGE,
    )
}

object PromptVariableValidator {
    private val placeholder = Regex("\\{\\{\\s*([A-Za-z][A-Za-z0-9_]*)\\s*\\}\\}")

    fun variables(text: String): Set<String> =
        placeholder.findAll(text).map { it.groupValues[1] }.toSet()

    fun requiredVariables(stage: PromptStage): Set<String> = when (stage) {
        PromptStage.VISION_RECOGNITION -> setOf(PromptVariables.IMAGES)
        PromptStage.TRANSLATION_GRADING,
        PromptStage.SHORT_ESSAY_GRADING,
        PromptStage.LONG_ESSAY_GRADING -> setOf(
            PromptVariables.YEAR,
            PromptVariables.QUESTION,
            PromptVariables.ANSWER,
            PromptVariables.MAX_SCORE,
            PromptVariables.RUBRIC,
        )
        PromptStage.TRANSLATION_REVIEW,
        PromptStage.SHORT_ESSAY_REVIEW,
        PromptStage.LONG_ESSAY_REVIEW -> setOf(
            PromptVariables.YEAR,
            PromptVariables.QUESTION,
            PromptVariables.ANSWER,
            PromptVariables.MAX_SCORE,
            PromptVariables.RUBRIC,
            PromptVariables.GRADING_RESULT,
        )
    }

    /** Save-time validation checks syntax and unknown variables only. */
    fun validateForSave(template: PromptTemplate): PromptValidation {
        val errors = mutableListOf<String>()
        val allText = template.systemPrompt + "\n" + template.userTemplate + "\n" + template.rubric
        val unmatchedOpen = allText.count { it == '{' } - allText.count { it == '}' }
        if (unmatchedOpen != 0 || hasMalformedPlaceholder(allText)) {
            errors += "提示词包含未闭合或格式错误的变量占位符（使用 {{variable}}）"
        }
        val unknown = variables(allText) - PromptVariables.all
        if (unknown.isNotEmpty()) {
            errors += "未知提示词变量：${unknown.sorted().joinToString(", ")}"
        }
        return if (errors.isEmpty()) PromptValidation.ok() else PromptValidation(false, errors)
    }

    /** Invocation-time validation additionally requires all stage inputs. */
    fun validateForInvocation(
        template: PromptTemplate,
        values: Map<String, String>,
    ): PromptValidation {
        val saveValidation = validateForSave(template)
        val templateVariables = variables(template.systemPrompt + "\n" + template.userTemplate + "\n" + template.rubric)
        val missingPlaceholders = requiredVariables(template.stage) - templateVariables
        val missingValues = requiredVariables(template.stage)
            .filter { values[it].isNullOrBlank() }
            .toSet()
        val missing = missingPlaceholders + missingValues
        val errors = saveValidation.errors.toMutableList()
        if (missingPlaceholders.isNotEmpty()) {
            errors += "提示词缺少必需变量：${missingPlaceholders.sorted().joinToString(", ")}"
        }
        if (missingValues.isNotEmpty()) {
            errors += "缺少调用所需变量值：${missingValues.sorted().joinToString(", ")}"
        }
        return if (errors.isEmpty()) PromptValidation.ok()
        else PromptValidation(false, errors, missing)
    }

    fun render(
        template: PromptTemplate,
        values: Map<String, String>,
    ): RenderedPrompt {
        val validation = validateForInvocation(template, values)
        if (!validation.valid) throw PromptValidationException(validation)
        val renderedRubric = interpolate(template.rubric, values)
        val enrichedValues = values + (PromptVariables.RUBRIC to renderedRubric)
        return RenderedPrompt(
            systemPrompt = interpolate(template.systemPrompt, enrichedValues),
            userPrompt = interpolate(template.userTemplate, enrichedValues),
            rubric = renderedRubric,
            variables = enrichedValues,
        )
    }

    private fun interpolate(text: String, values: Map<String, String>): String =
        placeholder.replace(text) { match -> values[match.groupValues[1]].orEmpty() }

    private fun hasMalformedPlaceholder(text: String): Boolean {
        // Remove valid placeholders; any remaining brace pair beginning with
        // "{{" is malformed.  Ordinary JSON braces are allowed in prompts.
        val withoutValid = placeholder.replace(text, "")
        return withoutValid.contains("{{") || withoutValid.contains("}}").also {
            // A single unmatched brace is handled by the count check above.
        }
    }
}

/** The fixed response contracts shown in the prompt editor. */
object GradingJsonSchemas {
    const val GRADING = """
        {
          "type":"object",
          "required":["score","maxScore","summary","deductions","revisedAnswer"],
          "properties":{
            "score":{"type":"number"},
            "maxScore":{"type":"number"},
            "summary":{"type":"string"},
            "deductions":{"type":"array","items":{"type":"object","required":["category","points","explanation"],"properties":{"category":{"type":"string"},"points":{"type":"number"},"explanation":{"type":"string"},"original":{"type":"string"},"suggestion":{"type":"string"}}}},
            "revisedAnswer":{"type":"string"}
          }
        }
    """

    const val REVIEW = """
        {
          "type":"object",
          "required":["vocabulary","grammarIssues","sentenceRevisions","studyAdvice"],
          "properties":{
            "vocabulary":{"type":"array","items":{"type":"object","required":["original","suggestion","explanation"],"properties":{"original":{"type":"string"},"suggestion":{"type":"string"},"explanation":{"type":"string"}}}},
            "grammarIssues":{"type":"array","items":{"type":"object","required":["original","correction","explanation"],"properties":{"original":{"type":"string"},"correction":{"type":"string"},"explanation":{"type":"string"}}}},
            "sentenceRevisions":{"type":"array","items":{"type":"object","required":["original","revised","reason"],"properties":{"original":{"type":"string"},"revised":{"type":"string"},"reason":{"type":"string"}}}},
            "studyAdvice":{"type":"array","items":{"type":"string"}}
          }
        }
    """

    const val OCR = """
        {
          "type":"object",
          "required":["text"],
          "properties":{"text":{"type":"string"}}
        }
    """
}

/**
 * Built-in Chinese templates.  They are ordinary editable data, not hidden
 * instructions; the settings screen can replace every field or restore one
 * stage to these values.
 */
object PromptDefaults {
    private const val COMMON_SYSTEM = "你是考研英语主观题批改助手。请严格依据用户提供的题目、答案和评分细则工作，不要臆造题目内容。默认用中文解释；英语原句、改写和例句保持英文。只输出约定 JSON，不要输出 Markdown 代码围栏。"
    private const val PROJECT_RUBRIC_NOTICE = "本评分细则是项目自定义的复习参考规则，不是官方六档评分规则；请给出可解释、可复核的扣分依据。"

    private val translationRubric = """
        $PROJECT_RUBRIC_NOTICE
        翻译满分以 {{maxScore}} 为上限。逐句检查信息完整、逻辑关系、术语和语法；只对明确错误或明显失真扣分，保留合理的同义表达。扣分合计不得超过满分。
    """.trimIndent()

    private val shortRubric = """
        $PROJECT_RUBRIC_NOTICE
        小作文按任务完成、内容要点、语言准确性、格式与连贯性综合评价，分数范围为 0 到 {{maxScore}}。先指出影响得分的主要问题，再给出可执行的英文修改。
    """.trimIndent()

    private val longRubric = """
        $PROJECT_RUBRIC_NOTICE
        大作文按内容立意、图表/图画描述、结构衔接、词汇语法和书写表达综合评价，分数范围为 0 到 {{maxScore}}。不得因为偏好某个表达而无依据扣分。
    """.trimIndent()

    val templates: Map<PromptStage, PromptTemplate> = mapOf(
        PromptStage.VISION_RECOGNITION to PromptTemplate(
            stage = PromptStage.VISION_RECOGNITION,
            systemPrompt = "你是英文答题图片文字识别助手。按原图阅读顺序转写，保留英文大小写、标点、段落和不确定位置；看不清时使用 [illegible]，不要猜测。只输出 JSON。",
            userTemplate = "请识别以下答题图片中的英文手写内容。图片数据由客户端作为视觉输入发送：{{images}}。输出完整转写文本。",
            rubric = "识别结果只陈述看见的文字，不进行评分或纠错。",
        ),
        PromptStage.TRANSLATION_GRADING to PromptTemplate(
            stage = PromptStage.TRANSLATION_GRADING,
            systemPrompt = COMMON_SYSTEM,
            userTemplate = "年份：{{year}}\n题型：翻译\n题目：{{question}}\n用户答案：{{answer}}\n请使用以下评分细则：{{rubric}}\n按固定评分 JSON 结构输出。",
            rubric = translationRubric,
        ),
        PromptStage.TRANSLATION_REVIEW to PromptTemplate(
            stage = PromptStage.TRANSLATION_REVIEW,
            systemPrompt = COMMON_SYSTEM,
            userTemplate = "年份：{{year}}\n题型：翻译\n题目：{{question}}\n用户答案：{{answer}}\n评分结果：{{gradingResult}}\n请使用以下评分细则：{{rubric}}\n给出词汇、语法、句子修改和复习建议，按固定复习 JSON 结构输出。",
            rubric = translationRubric,
        ),
        PromptStage.SHORT_ESSAY_GRADING to PromptTemplate(
            stage = PromptStage.SHORT_ESSAY_GRADING,
            systemPrompt = COMMON_SYSTEM,
            userTemplate = "年份：{{year}}\n题型：小作文\n题目：{{question}}\n用户答案：{{answer}}\n请使用以下评分细则：{{rubric}}\n按固定评分 JSON 结构输出。",
            rubric = shortRubric,
        ),
        PromptStage.SHORT_ESSAY_REVIEW to PromptTemplate(
            stage = PromptStage.SHORT_ESSAY_REVIEW,
            systemPrompt = COMMON_SYSTEM,
            userTemplate = "年份：{{year}}\n题型：小作文\n题目：{{question}}\n用户答案：{{answer}}\n评分结果：{{gradingResult}}\n请使用以下评分细则：{{rubric}}\n按固定复习 JSON 结构输出。",
            rubric = shortRubric,
        ),
        PromptStage.LONG_ESSAY_GRADING to PromptTemplate(
            stage = PromptStage.LONG_ESSAY_GRADING,
            systemPrompt = COMMON_SYSTEM,
            userTemplate = "年份：{{year}}\n题型：大作文\n题目：{{question}}\n用户答案：{{answer}}\n请使用以下评分细则：{{rubric}}\n按固定评分 JSON 结构输出。",
            rubric = longRubric,
        ),
        PromptStage.LONG_ESSAY_REVIEW to PromptTemplate(
            stage = PromptStage.LONG_ESSAY_REVIEW,
            systemPrompt = COMMON_SYSTEM,
            userTemplate = "年份：{{year}}\n题型：大作文\n题目：{{question}}\n用户答案：{{answer}}\n评分结果：{{gradingResult}}\n请使用以下评分细则：{{rubric}}\n按固定复习 JSON 结构输出。",
            rubric = longRubric,
        ),
    )

    fun defaultFor(stage: PromptStage): PromptTemplate = templates.getValue(stage).copy()

    fun allDefaults(): Map<PromptStage, PromptTemplate> = templates.mapValues { it.value.copy() }

    fun schemaFor(stage: PromptStage): String = when (stage) {
        PromptStage.VISION_RECOGNITION -> GradingJsonSchemas.OCR
        PromptStage.TRANSLATION_GRADING,
        PromptStage.SHORT_ESSAY_GRADING,
        PromptStage.LONG_ESSAY_GRADING -> GradingJsonSchemas.GRADING
        PromptStage.TRANSLATION_REVIEW,
        PromptStage.SHORT_ESSAY_REVIEW,
        PromptStage.LONG_ESSAY_REVIEW -> GradingJsonSchemas.REVIEW
    }
}
