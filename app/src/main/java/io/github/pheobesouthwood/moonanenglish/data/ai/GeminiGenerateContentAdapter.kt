package io.github.pheobesouthwood.moonanenglish.data.ai

import io.github.pheobesouthwood.moonanenglish.domain.AiImage
import io.github.pheobesouthwood.moonanenglish.domain.AiRequest
import io.github.pheobesouthwood.moonanenglish.domain.AiResponse
import io.github.pheobesouthwood.moonanenglish.domain.ModelInfo
import io.github.pheobesouthwood.moonanenglish.domain.ProviderProfile
import io.github.pheobesouthwood.moonanenglish.domain.ResponseFormat
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONObject

/** Adapter for Gemini's v1beta models/{model}:generateContent endpoint. */
class GeminiGenerateContentAdapter(
    provider: ProviderProfile,
    client: OkHttpClient = OkHttpClient(),
) : BaseAiProvider(provider, client) {

    override suspend fun complete(request: AiRequest): AiResponse {
        val model = requireModel(request.model).removePrefix("models/")
        val body = JSONObject()
            .put("contents", contents(request))
        request.systemPrompt?.takeIf { it.isNotBlank() }?.let {
            body.put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", it))))
        }
        val generation = JSONObject()
        request.temperature?.let { generation.put("temperature", it) }
        request.maxOutputTokens?.let { generation.put("maxOutputTokens", it) }
        if (request.responseFormat == ResponseFormat.JSON_OBJECT) {
            generation.put("responseMimeType", "application/json")
        }
        if (generation.length() > 0) body.put("generationConfig", generation)
        val raw = execute(
            jsonRequest(generateUrl(model), body)
                .newBuilder()
                .header("Accept", "application/json")
                .build(),
        )
        return try {
            AiJsonParser.parseGeminiResponse(provider.id, model, raw)
        } catch (error: Throwable) {
            throw AiErrorMapper.fromThrowable(error, provider.id)
        }
    }

    override suspend fun listModels(): List<ModelInfo> {
        val url = provider.baseUrl.trimEnd('/').toHttpUrl().newBuilder()
            .addPathSegment("models")
            .apply { if (provider.apiKey.isNotBlank()) addQueryParameter("key", provider.apiKey) }
            .build()
        val request = requestBuilder(url.toString())
            .header("Accept", "application/json")
            .build()
        val root = parseJsonOrError(execute(request))
        val models = root.optArrayOrNull("models") ?: JSONArray()
        return (0 until models.length()).mapNotNull { index ->
            val model = models.opt(index) as? JSONObject ?: return@mapNotNull null
            val name = model.optStringOrNull("name") ?: return@mapNotNull null
            val methods = model.optArrayOrNull("supportedGenerationMethods")
            val canGenerate = methods == null || (0 until methods.length()).any {
                methods.optString(it) == "generateContent"
            }
            if (!canGenerate) return@mapNotNull null
            ModelInfo(
                id = name.removePrefix("models/"),
                displayName = model.optStringOrNull("displayName"),
                supportsVision = true,
            )
        }
    }

    private fun generateUrl(model: String): String {
        val url = provider.baseUrl.trimEnd('/').toHttpUrl().newBuilder()
            .addPathSegment("models")
            .addPathSegment("$model:generateContent")
        if (provider.apiKey.isNotBlank()) url.addQueryParameter("key", provider.apiKey)
        return url.build().toString()
    }

    private fun contents(request: AiRequest): JSONArray {
        val parts = JSONArray()
            .put(JSONObject().put("text", request.userPrompt))
        request.images.forEach { image ->
            parts.put(
                JSONObject().put(
                    "inlineData",
                    JSONObject()
                        .put("mimeType", image.mimeType.ifBlank { "image/jpeg" })
                        .put("data", image.base64),
                ),
            )
        }
        return JSONArray().put(JSONObject().put("role", "user").put("parts", parts))
    }
}

typealias GeminiAdapter = GeminiGenerateContentAdapter
