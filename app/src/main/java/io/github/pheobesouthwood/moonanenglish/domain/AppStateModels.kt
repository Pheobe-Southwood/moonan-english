package io.github.pheobesouthwood.moonanenglish.domain

import kotlinx.serialization.Serializable

@Serializable
enum class AppearanceMode { SYSTEM, LIGHT, DARK }

@Serializable
data class StageAssignment(
    val stage: AiStage,
    val providerId: String = "",
    val modelId: String = "",
)

@Serializable
data class UserSettings(
    val appearanceMode: AppearanceMode = AppearanceMode.SYSTEM,
    val autoGradeAfterRecognition: Boolean = false,
)

@Serializable
data class StoredAppState(
    val formatVersion: Int = 1,
    val providers: List<ProviderProfile> = emptyList(),
    val prompts: List<PromptTemplate> = emptyList(),
    val assignments: List<StageAssignment> = emptyList(),
    val sessions: List<PracticeSession> = emptyList(),
    val settings: UserSettings = UserSettings(),
)

object ProviderDefaults {
    val profiles = listOf(
        ProviderProfile("openai", "OpenAI", AiProtocol.OPENAI_RESPONSES, "https://api.openai.com/v1", supportsVision = true, enabled = false),
        ProviderProfile("anthropic", "Anthropic", AiProtocol.ANTHROPIC_MESSAGES, "https://api.anthropic.com/v1", supportsVision = true, enabled = false),
        ProviderProfile("gemini", "Gemini", AiProtocol.GEMINI_GENERATE_CONTENT, "https://generativelanguage.googleapis.com/v1beta", supportsVision = true, enabled = false),
        ProviderProfile("deepseek", "DeepSeek", AiProtocol.OPENAI_CHAT_COMPLETIONS, "https://api.deepseek.com/v1", enabled = false),
        ProviderProfile("openrouter", "OpenRouter", AiProtocol.OPENAI_CHAT_COMPLETIONS, "https://openrouter.ai/api/v1", supportsVision = true, enabled = false),
        ProviderProfile("custom", "自定义兼容网关", AiProtocol.OPENAI_CHAT_COMPLETIONS, "https://example.invalid/v1", enabled = false),
        ProviderProfile("baidu-ocr", "百度手写 OCR", AiProtocol.BAIDU_OCR, "https://aip.baidubce.com", enabled = false),
    )
}
