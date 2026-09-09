package io.github.pheobesouthwood.moonanenglish.data.ai

import io.github.pheobesouthwood.moonanenglish.domain.AiImage
import io.github.pheobesouthwood.moonanenglish.domain.AiProviderException
import io.github.pheobesouthwood.moonanenglish.domain.AiRequest
import io.github.pheobesouthwood.moonanenglish.domain.AiResponse
import io.github.pheobesouthwood.moonanenglish.domain.ResponseFormat
import io.github.pheobesouthwood.moonanenglish.domain.ModelInfo
import io.github.pheobesouthwood.moonanenglish.domain.ProviderProfile
import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONObject

/** Adapter for OpenAI's newer /v1/responses protocol. */
class OpenAiResponsesAdapter(
    provider: ProviderProfile,
    client: OkHttpClient = OkHttpClient(),
) : BaseAiProvider(provider, client) {

    override suspend fun complete(request: AiRequest): AiResponse {
        val model = requireModel(request.model)
        val body = JSONObject()
            .put("model", model)
            .put("input", inputMessages(request))
        request.temperature?.let { body.put("temperature", it) }
        request.maxOutputTokens?.let { body.put("max_output_tokens", it) }
        if (request.responseFormat == ResponseFormat.JSON_OBJECT) {
            body.put("text", responseTextConfig(request))
        }
        val builder = jsonRequest(
                url = endpoint("responses"),
                body = body,
            ).newBuilder().header("Accept", "application/json")
        if (provider.apiKey.isNotBlank()) builder.header("Authorization", "Bearer ${provider.apiKey}")
        val raw = execute(builder.build())
        return try {
            AiJsonParser.parseOpenAiResponse(provider.id, model, raw)
        } catch (error: AiProviderException) {
            throw error
        } catch (error: Throwable) {
            throw AiErrorMapper.fromThrowable(error, provider.id)
        }
    }

    override suspend fun listModels(): List<ModelInfo> {
        val builder = requestBuilder(endpoint("models")).header("Accept", "application/json")
        if (provider.apiKey.isNotBlank()) builder.header("Authorization", "Bearer ${provider.apiKey}")
        return parseOpenAiModels(execute(builder.build()))
    }

    private fun inputMessages(request: AiRequest): JSONArray {
        val messages = JSONArray()
        request.systemPrompt?.takeIf { it.isNotBlank() }?.let {
            messages.put(message("system", arrayOf(textPart("input_text", it))))
        }
        val userParts = mutableListOf<JSONObject>()
        userParts += textPart("input_text", request.userPrompt)
        request.images.forEach { image -> userParts += imagePart(image) }
        messages.put(message("user", userParts.toTypedArray()))
        return messages
    }

    private fun message(role: String, parts: Array<JSONObject>): JSONObject {
        val content = JSONArray()
        parts.forEach { content.put(it) }
        return JSONObject().put("role", role).put("content", content)
    }

    private fun textPart(type: String, text: String): JSONObject =
        JSONObject().put("type", type).put("text", text)

    private fun imagePart(image: AiImage): JSONObject =
        JSONObject()
            .put("type", "input_image")
            .put("image_url", imageDataUrl(image.mimeType, image.base64))

    private fun responseTextConfig(request: AiRequest): JSONObject {
        val format = JSONObject()
        val schema = request.responseJsonSchema?.let(::parseJsonObjectOrNull)
        if (schema != null) {
            format
                .put("type", "json_schema")
                .put("name", "moonan_english_result")
                // The editable project schemas intentionally contain optional
                // evidence fields, so strict mode would reject them.
                .put("strict", false)
                .put("schema", schema)
        } else {
            format.put("type", "json_object")
        }
        return JSONObject().put("format", format)
    }
}

/** Upper-case spelling retained for callers which use the protocol name. */
typealias OpenAIResponsesAdapter = OpenAiResponsesAdapter
