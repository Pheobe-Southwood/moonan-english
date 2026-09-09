package io.github.pheobesouthwood.moonanenglish.domain

import kotlinx.serialization.Serializable

/**
 * Wire protocols understood by the local BYOK gateway.
 *
 * The names intentionally describe the wire protocol rather than a vendor.  A
 * user can therefore point an OpenAI-compatible provider at any HTTPS gateway.
 */
@Serializable
enum class AiProtocol {
    OPENAI_RESPONSES,
    OPENAI_CHAT_COMPLETIONS,
    ANTHROPIC_MESSAGES,
    GEMINI_GENERATE_CONTENT,
    BAIDU_OCR,
}

/** Stages which can be sent to an AI provider. */
@Serializable
enum class AiStage {
    VISION_RECOGNITION,
    TRANSLATION_GRADING,
    TRANSLATION_REVIEW,
    SHORT_ESSAY_GRADING,
    SHORT_ESSAY_REVIEW,
    LONG_ESSAY_GRADING,
    LONG_ESSAY_REVIEW,
}

/** A provider profile is local configuration and is never uploaded by the app. */
@Serializable
data class ProviderProfile(
    val id: String,
    val name: String,
    val protocol: AiProtocol,
    val baseUrl: String,
    val apiKey: String = "",
    val apiSecret: String = "",
    val extraHeaders: Map<String, String> = emptyMap(),
    val modelIds: List<String> = emptyList(),
    val supportsVision: Boolean = false,
    val enabled: Boolean = true,
)

/** A base64 image payload.  [base64] must not include a data URL prefix. */
@Serializable
data class AiImage(
    val base64: String,
    val mimeType: String = "image/jpeg",
    val fileName: String? = null,
)

@Serializable
enum class ResponseFormat {
    TEXT,
    JSON_OBJECT,
}

/**
 * A protocol-neutral completion request.  Prompt editing happens above this
 * layer; adapters only serialize the already-rendered prompts.
 */
@Serializable
data class AiRequest(
    val stage: AiStage? = null,
    val model: String,
    val systemPrompt: String? = null,
    val userPrompt: String,
    val images: List<AiImage> = emptyList(),
    val temperature: Double? = null,
    val maxOutputTokens: Int? = null,
    val responseFormat: ResponseFormat = ResponseFormat.JSON_OBJECT,
    /** A fixed, read-only schema supplied by the prompt layer, when desired. */
    val responseJsonSchema: String? = null,
)

@Serializable
data class AiUsage(
    val inputTokens: Long? = null,
    val outputTokens: Long? = null,
    val totalTokens: Long? = null,
)

/** A normalized response plus the untouched provider response for diagnosis. */
@Serializable
data class AiResponse(
    val text: String,
    val rawBody: String,
    val providerId: String,
    val model: String,
    val usage: AiUsage? = null,
    val structuredJson: String? = null,
)

@Serializable
data class ModelInfo(
    val id: String,
    val displayName: String? = null,
    val ownedBy: String? = null,
    val supportsVision: Boolean = false,
)

@Serializable
data class OcrRequest(
    val images: List<AiImage>,
    val languageType: String = "ENG",
    val detectDirection: Boolean = true,
    val paragraph: Boolean = true,
)

@Serializable
data class OcrResponse(
    val text: String,
    val rawBody: String,
    val providerId: String,
)

@Serializable
enum class AiErrorKind {
    AUTHENTICATION,
    AUTHORIZATION,
    RATE_LIMIT,
    INSUFFICIENT_CREDITS,
    INVALID_REQUEST,
    UNSUPPORTED,
    TIMEOUT,
    NETWORK,
    SERVER,
    INVALID_RESPONSE,
    CANCELLED,
    UNKNOWN,
}

/** A stable, UI-safe error category with an optional provider diagnostic. */
@Serializable
data class AiFailure(
    val kind: AiErrorKind,
    val message: String,
    val httpStatus: Int? = null,
    val retryAfterSeconds: Long? = null,
    val providerId: String? = null,
    val rawBody: String? = null,
)

class AiProviderException(
    val failure: AiFailure,
    cause: Throwable? = null,
) : java.io.IOException(failure.message, cause)

/**
 * Adapters throw [AiProviderException] for all expected provider failures.
 * [AiGateway] turns those exceptions into Result values for simple UI callers.
 */
interface AiProviderAdapter {
    suspend fun complete(request: AiRequest): AiResponse

    suspend fun listModels(): List<ModelInfo>
}

interface OcrProviderAdapter {
    suspend fun recognize(request: OcrRequest): OcrResponse
}

/** One-provider facade used by screens and background work. */
class AiGateway(
    private val adapter: AiProviderAdapter,
) {
    suspend fun complete(request: AiRequest): Result<AiResponse> =
        runCatching { adapter.complete(request) }

    suspend fun listModels(): Result<List<ModelInfo>> =
        runCatching { adapter.listModels() }
}
