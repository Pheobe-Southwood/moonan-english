package io.github.pheobesouthwood.moonanenglish.data.ai

import io.github.pheobesouthwood.moonanenglish.domain.AiResponse
import io.github.pheobesouthwood.moonanenglish.domain.AiUsage
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

internal fun JSONObject.optStringOrNull(key: String): String? =
    if (!has(key) || isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

internal fun JSONObject.optLongOrNull(key: String): Long? =
    if (!has(key) || isNull(key)) null else optLong(key)

internal fun JSONObject.optIntOrNull(key: String): Int? =
    if (!has(key) || isNull(key)) null else optInt(key)

internal fun JSONObject.optObjectOrNull(key: String): JSONObject? =
    opt(key) as? JSONObject

internal fun JSONObject.optArrayOrNull(key: String): JSONArray? =
    opt(key) as? JSONArray

internal fun JSONArray.objects(): Sequence<JSONObject> = sequence {
    for (index in 0 until length()) {
        (opt(index) as? JSONObject)?.let { yield(it) }
    }
}

internal fun JSONObject.putIfNotNull(key: String, value: Any?) {
    if (value != null) put(key, value)
}

internal fun imageDataUrl(mimeType: String, base64: String): String =
    "data:${mimeType.ifBlank { "image/jpeg" }};base64,$base64"

internal fun parseJsonObjectOrNull(value: String?): JSONObject? {
    if (value.isNullOrBlank()) return null
    return try {
        JSONObject(value)
    } catch (_: JSONException) {
        null
    }
}

/**
 * Extracts the first balanced JSON object from a model response.  Models often
 * add a short sentence or a ```json fence despite being asked for JSON.
 */
object AiJsonParser {
    fun extractJsonObject(text: String): String? {
        val trimmed = text.trim()
        val candidates = sequenceOf(
            trimmed.removePrefix("```json").removePrefix("```JSON").removeSuffix("```").trim(),
            trimmed,
        ).distinct()
        for (candidate in candidates) {
            if (parseJsonObjectOrNull(candidate) != null) return candidate
            val extracted = extractBalanced(candidate, '{', '}')
            if (extracted != null && parseJsonObjectOrNull(extracted) != null) return extracted
        }
        return null
    }

    fun parseStructuredJson(text: String): JSONObject? =
        extractJsonObject(text)?.let(::parseJsonObjectOrNull)

    fun response(
        providerId: String,
        model: String,
        text: String,
        rawBody: String,
        usage: AiUsage? = null,
    ): AiResponse {
        if (text.isBlank()) {
            throw IllegalArgumentException("AI 响应没有可显示的文本")
        }
        return AiResponse(
            text = text,
            rawBody = rawBody,
            providerId = providerId,
            model = model,
            usage = usage,
            structuredJson = extractJsonObject(text),
        )
    }

    fun usageFromOpenAi(root: JSONObject): AiUsage? =
        root.optObjectOrNull("usage")?.let {
            AiUsage(
                inputTokens = it.optLongOrNull("input_tokens") ?: it.optLongOrNull("prompt_tokens"),
                outputTokens = it.optLongOrNull("output_tokens") ?: it.optLongOrNull("completion_tokens"),
                totalTokens = it.optLongOrNull("total_tokens"),
            )
        }

    fun usageFromAnthropic(root: JSONObject): AiUsage? =
        root.optObjectOrNull("usage")?.let {
            AiUsage(
                inputTokens = it.optLongOrNull("input_tokens"),
                outputTokens = it.optLongOrNull("output_tokens"),
                totalTokens = null,
            )
        }

    fun usageFromGemini(root: JSONObject): AiUsage? =
        root.optObjectOrNull("usageMetadata")?.let {
            AiUsage(
                inputTokens = it.optLongOrNull("promptTokenCount"),
                outputTokens = it.optLongOrNull("candidatesTokenCount"),
                totalTokens = it.optLongOrNull("totalTokenCount"),
            )
        }

    /** Text field extraction for OpenAI Chat, Responses, Anthropic and Gemini. */
    fun textIn(value: Any?): String {
        when (value) {
            is String -> return value
            is JSONObject -> {
                value.optStringOrNull("text")?.let { return it }
                value.optStringOrNull("output_text")?.let { return it }
                value.optObjectOrNull("content")?.let { return textIn(it) }
                value.optArrayOrNull("content")?.let { return textIn(it) }
                value.optArrayOrNull("parts")?.let { return textIn(it) }
                value.optObjectOrNull("message")?.let { return textIn(it) }
            }
            is JSONArray -> {
                val pieces = buildList {
                    for (index in 0 until value.length()) {
                        val part = textIn(value.opt(index))
                        if (part.isNotBlank()) add(part)
                    }
                }
                return pieces.joinToString("\n")
            }
        }
        return ""
    }

    fun parseOpenAiResponse(providerId: String, model: String, rawBody: String): AiResponse {
        val root = JSONObject(rawBody)
        val text = root.optStringOrNull("output_text")
            ?: textIn(root.optArrayOrNull("output"))
            .ifBlank { textIn(root.optArrayOrNull("choices")) }
        return response(providerId, model, text, rawBody, usageFromOpenAi(root))
    }

    fun parseAnthropicResponse(providerId: String, model: String, rawBody: String): AiResponse {
        val root = JSONObject(rawBody)
        val text = textIn(root.optArrayOrNull("content"))
        return response(providerId, model, text, rawBody, usageFromAnthropic(root))
    }

    fun parseGeminiResponse(providerId: String, model: String, rawBody: String): AiResponse {
        val root = JSONObject(rawBody)
        val text = textIn(root.optArrayOrNull("candidates"))
        return response(providerId, model, text, rawBody, usageFromGemini(root))
    }

    private fun extractBalanced(text: String, open: Char, close: Char): String? {
        var depth = 0
        var start = -1
        var inString = false
        var escaped = false
        for (index in text.indices) {
            val char = text[index]
            if (inString) {
                if (escaped) escaped = false
                else if (char == '\\') escaped = true
                else if (char == '"') inString = false
                continue
            }
            if (char == '"') {
                inString = true
                continue
            }
            if (char == open) {
                if (depth == 0) start = index
                depth++
            } else if (char == close && depth > 0) {
                depth--
                if (depth == 0 && start >= 0) return text.substring(start, index + 1)
            }
        }
        return null
    }
}
