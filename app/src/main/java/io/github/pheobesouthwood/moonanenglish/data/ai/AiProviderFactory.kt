package io.github.pheobesouthwood.moonanenglish.data.ai

import io.github.pheobesouthwood.moonanenglish.domain.AiProtocol
import io.github.pheobesouthwood.moonanenglish.domain.AiProviderAdapter
import io.github.pheobesouthwood.moonanenglish.domain.ProviderProfile
import okhttp3.OkHttpClient

object AiProviderFactory {
    fun create(
        profile: ProviderProfile,
        client: OkHttpClient = OkHttpClient(),
    ): AiProviderAdapter {
        val errors = ProviderProfileValidation.validate(profile)
        require(errors.isEmpty()) { errors.joinToString("；") }
        return when (profile.protocol) {
            AiProtocol.OPENAI_RESPONSES -> OpenAiResponsesAdapter(profile, client)
            AiProtocol.OPENAI_CHAT_COMPLETIONS -> OpenAiChatCompletionsAdapter(profile, client)
            AiProtocol.ANTHROPIC_MESSAGES -> AnthropicMessagesAdapter(profile, client)
            AiProtocol.GEMINI_GENERATE_CONTENT -> GeminiGenerateContentAdapter(profile, client)
            AiProtocol.BAIDU_OCR -> BaiduOcrAdapter(profile, client)
        }
    }
}

/** Empty-key profiles let the provider editor offer useful presets immediately. */
object ProviderPresets {
    fun openAiResponses() = ProviderProfile(
        id = "openai-responses",
        name = "OpenAI Responses",
        protocol = AiProtocol.OPENAI_RESPONSES,
        baseUrl = "https://api.openai.com/v1",
        supportsVision = true,
    )

    fun openAiChatCompletions() = ProviderProfile(
        id = "openai-compatible",
        name = "OpenAI Compatible",
        protocol = AiProtocol.OPENAI_CHAT_COMPLETIONS,
        baseUrl = "https://api.openai.com/v1",
        supportsVision = true,
    )

    fun anthropic() = ProviderProfile(
        id = "anthropic",
        name = "Anthropic Messages",
        protocol = AiProtocol.ANTHROPIC_MESSAGES,
        baseUrl = "https://api.anthropic.com/v1",
        supportsVision = true,
    )

    fun gemini() = ProviderProfile(
        id = "gemini",
        name = "Gemini generateContent",
        protocol = AiProtocol.GEMINI_GENERATE_CONTENT,
        baseUrl = "https://generativelanguage.googleapis.com/v1beta",
        supportsVision = true,
    )

    fun deepSeek() = ProviderProfile(
        id = "deepseek",
        name = "DeepSeek",
        protocol = AiProtocol.OPENAI_CHAT_COMPLETIONS,
        baseUrl = "https://api.deepseek.com/v1",
        supportsVision = false,
    )

    fun openRouter() = ProviderProfile(
        id = "openrouter",
        name = "OpenRouter",
        protocol = AiProtocol.OPENAI_CHAT_COMPLETIONS,
        baseUrl = "https://openrouter.ai/api/v1",
        supportsVision = true,
    )

    fun customOpenAiCompatible() = ProviderProfile(
        id = "custom-openai-compatible",
        name = "自定义 OpenAI Compatible",
        protocol = AiProtocol.OPENAI_CHAT_COMPLETIONS,
        baseUrl = "https://example.invalid/v1",
        supportsVision = true,
    )

    fun baiduOcr() = ProviderProfile(
        id = "baidu-ocr",
        name = "百度 OCR",
        protocol = AiProtocol.BAIDU_OCR,
        baseUrl = "https://aip.baidubce.com",
        supportsVision = false,
    )

    fun all(): List<ProviderProfile> = listOf(
        openAiResponses(),
        openAiChatCompletions(),
        anthropic(),
        gemini(),
        deepSeek(),
        openRouter(),
        customOpenAiCompatible(),
        baiduOcr(),
    )
}
