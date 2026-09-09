package io.github.pheobesouthwood.moonanenglish.data.ai

import io.github.pheobesouthwood.moonanenglish.domain.AiImage
import io.github.pheobesouthwood.moonanenglish.domain.AiRequest
import io.github.pheobesouthwood.moonanenglish.domain.AiResponse
import io.github.pheobesouthwood.moonanenglish.domain.ModelInfo
import io.github.pheobesouthwood.moonanenglish.domain.ProviderProfile
import io.github.pheobesouthwood.moonanenglish.domain.ResponseFormat
import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONObject

/** Adapter for OpenAI-compatible /v1/chat/completions gateways. */
class OpenAiChatCompletionsAdapter(
    provider: ProviderProfile,
    client: OkHttpClient = OkHttpClient(),
) : BaseAiProvider(provider, client) {

    override suspend fun complete(request: AiRequest): AiResponse {
        val model = requireModel(request.model)
        val body = JSONObject()
            .put("model", model)
            .put("messages", messages(request))
        request.temperature?.let { body.put("temperature", it) }
        request.maxOutputTokens?.let { body.put("max_tokens", it) }
        if (request.responseFormat == ResponseFormat.JSON_OBJECT) {
            body.put("response_format", responseFormat(request))
        }
        val builder = jsonRequest(endpoint("chat/completions"), body).newBuilder().header("Accept", "application/json")
        if (provider.apiKey.isNotBlank()) builder.header("Authorization", "Bearer ${provider.apiKey}")
        val raw = execute(builder.build())
        return try {
            AiJsonParser.parseOpenAiResponse(provider.id, model, raw)
        } catch (error: Throwable) {
            throw AiErrorMapper.fromThrowable(error, provider.id)
        }
    }

    override suspend fun listModels(): List<ModelInfo> {
        val builder = requestBuilder(endpoint("models")).header("Accept", "application/json")
        if (provider.apiKey.isNotBlank()) builder.header("Authorization", "Bearer ${provider.apiKey}")
        return parseOpenAiModels(execute(builder.build()))
    }

    private fun messages(request: AiRequest): JSONArray {
        val messages = JSONArray()
        request.systemPrompt?.takeIf { it.isNotBlank() }?.let {
            messages.put(JSONObject().put("role", "system").put("content", it))
        }
        val userContent: Any = if (request.images.isEmpty()) {
            request.userPrompt
        } else {
            JSONArray().put(JSONObject().put("type", "text").put("text", request.userPrompt)).also { content ->
                request.images.forEach { image ->
                    content.put(
                        JSONObject()
                            .put("type", "image_url")
                            .put("image_url", JSONObject().put("url", imageDataUrl(image.mimeType, image.base64))),
                    )
                }
            }
        }
        messages.put(JSONObject().put("role", "user").put("content", userContent))
        return messages
    }

    private fun responseFormat(request: AiRequest): JSONObject {
        // json_object is the widest common denominator across DeepSeek,
        // OpenRouter and custom OpenAI-compatible gateways. The fixed schema
        // is also appended to the visible prompt by the caller.
        return JSONObject().put("type", "json_object")
    }
}

typealias OpenAIChatCompletionsAdapter = OpenAiChatCompletionsAdapter
