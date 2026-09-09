package io.github.pheobesouthwood.moonanenglish.data.ai

import io.github.pheobesouthwood.moonanenglish.domain.AiImage
import io.github.pheobesouthwood.moonanenglish.domain.AiRequest
import io.github.pheobesouthwood.moonanenglish.domain.AiResponse
import io.github.pheobesouthwood.moonanenglish.domain.ModelInfo
import io.github.pheobesouthwood.moonanenglish.domain.ProviderProfile
import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONObject

/** Adapter for Anthropic's /v1/messages protocol. */
class AnthropicMessagesAdapter(
    provider: ProviderProfile,
    client: OkHttpClient = OkHttpClient(),
) : BaseAiProvider(provider, client) {

    override suspend fun complete(request: AiRequest): AiResponse {
        val model = requireModel(request.model)
        val body = JSONObject()
            .put("model", model)
            .put("max_tokens", request.maxOutputTokens ?: 4096)
            .put("messages", messages(request))
        request.systemPrompt?.takeIf { it.isNotBlank() }?.let { body.put("system", it) }
        request.temperature?.let { body.put("temperature", it) }
        val raw = execute(
            jsonRequest(endpoint("messages"), body)
                .newBuilder()
                .header("x-api-key", provider.apiKey)
                .header("anthropic-version", provider.extraHeaders["anthropic-version"] ?: "2023-06-01")
                .header("Accept", "application/json")
                .build(),
        )
        return try {
            AiJsonParser.parseAnthropicResponse(provider.id, model, raw)
        } catch (error: Throwable) {
            throw AiErrorMapper.fromThrowable(error, provider.id)
        }
    }

    override suspend fun listModels(): List<ModelInfo> {
        val request = requestBuilder(endpoint("models"))
            .header("x-api-key", provider.apiKey)
            .header("anthropic-version", provider.extraHeaders["anthropic-version"] ?: "2023-06-01")
            .header("Accept", "application/json")
            .build()
        return parseAnthropicModels(execute(request))
    }

    private fun messages(request: AiRequest): JSONArray {
        val content = JSONArray()
            .put(JSONObject().put("type", "text").put("text", request.userPrompt))
        request.images.forEach { image -> content.put(imagePart(image)) }
        return JSONArray().put(
            JSONObject()
                .put("role", "user")
                .put("content", content),
        )
    }

    private fun imagePart(image: AiImage): JSONObject =
        JSONObject()
            .put("type", "image")
            .put(
                "source",
                JSONObject()
                    .put("type", "base64")
                    .put("media_type", image.mimeType.ifBlank { "image/jpeg" })
                    .put("data", image.base64),
            )
}

