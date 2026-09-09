package io.github.pheobesouthwood.moonanenglish.domain

import kotlinx.serialization.Serializable

@Serializable
enum class PaperFormat { OLD_SINGLE_ESSAY, MODERN_PART_A_B }

@Serializable
enum class QuestionType(val label: String, val defaultMaxScore: Int) {
    TRANSLATION("翻译题", 10),
    SMALL_ESSAY("小作文", 10),
    LARGE_ESSAY("大作文", 20),
}

@Serializable
data class ExamQuestion(
    val id: String,
    val year: Int,
    val paperFormat: PaperFormat,
    val questionType: QuestionType,
    val maxScore: Int,
    val contentMarkdown: String,
    val translationSegments: List<String> = emptyList(),
    val imageDescription: String? = null,
)

@Serializable
enum class InputMode { TEXT, IMAGES }

@Serializable
enum class StageStatus { DRAFT, RUNNING, SUCCEEDED, FAILED, CANCELLED, RAW_ONLY }

@Serializable
data class StoredImage(
    val id: String,
    val relativePath: String,
    val rotationDegrees: Int = 0,
    val cropInsetPercent: Int = 0,
)

@Serializable
data class Deduction(
    val category: String,
    val evidence: String,
    val pointsDeducted: Double,
    val explanation: String,
)

@Serializable
data class GradingResult(
    val score: Double,
    val maxScore: Double,
    val summary: String,
    val deductions: List<Deduction> = emptyList(),
    val revisedAnswer: String = "",
    val rawResponse: String = "",
)

@Serializable
data class GrammarIssue(
    val original: String,
    val correction: String,
    val explanation: String,
)

@Serializable
data class SentenceRevision(
    val original: String,
    val improved: String,
)

@Serializable
data class ReviewResult(
    val vocabulary: List<String> = emptyList(),
    val grammarIssues: List<GrammarIssue> = emptyList(),
    val sentenceRevisions: List<SentenceRevision> = emptyList(),
    val studyAdvice: List<String> = emptyList(),
    val rawResponse: String = "",
)

@Serializable
data class GradingRevision(
    val id: String,
    val sessionId: String,
    val answerSnapshot: String,
    val questionSnapshot: String,
    val promptSnapshot: String,
    val modelSnapshot: String,
    val gradingStatus: StageStatus = StageStatus.DRAFT,
    val reviewStatus: StageStatus = StageStatus.DRAFT,
    val grading: GradingResult? = null,
    val review: ReviewResult? = null,
    val gradingRawResponse: String = "",
    val reviewRawResponse: String = "",
    val errorMessage: String? = null,
    val createdAtEpochMs: Long,
)

@Serializable
data class PracticeSession(
    val id: String,
    val questionId: String,
    val year: Int,
    val questionType: QuestionType,
    val inputMode: InputMode,
    val images: List<StoredImage> = emptyList(),
    val recognizedText: String = "",
    val currentAnswer: String = "",
    val createdAtEpochMs: Long,
    val revisions: List<GradingRevision> = emptyList(),
)
