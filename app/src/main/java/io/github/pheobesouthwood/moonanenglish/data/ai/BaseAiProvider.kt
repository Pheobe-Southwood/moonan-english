package io.github.pheobesouthwood.moonanenglish.data.ai

import io.github.pheobesouthwood.moonanenglish.domain.AiErrorKind
import io.github.pheobesouthwood.moonanenglish.domain.AiFailure
import io.github.pheobesouthwood.moonanenglish.domain.AiProviderAdapter
import io.github.pheobesouthwood.moonanenglish.domain.AiProviderException
import io.github.pheobesouthwood.moonanenglish.domain.ModelInfo
import io.github.pheobesouthwood.moonanenglish.domain.ProviderProfile
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

internal const val JSON_MEDIA_TYPE = "application/json; charset=utf-8"

/** Shared HTTP and model-list behavior for JSON providers. */
abstract class BaseAiProvider(
    protected val provider: ProviderProfile,
    protected val client: OkHttpClient,
) : AiProviderAdapter {

    protected fun endpoint(path: String): String =
        provider.baseUrl.trimEnd('/') + "/" + path.trimStart('/')

    protected fun jsonRequest(
        url: String,
        body: JSONObject,
        method: String = "POST",
    ): Request {
        val requestBody = body.toString().toRequestBody(JSON_MEDIA_TYPE.toMediaType())
        return requestBuilder(url, method).method(method, requestBody).build()
    }

    protected fun requestBuilder(url: String, method: String = "GET"): Request.Builder {
        val builder = Request.Builder().url(url)
        provider.extraHeaders.forEach { (name, value) -> builder.header(name, value) }
        return builder
    }

    protected fun execute(request: Request): String {
        val response: Response = try {
            client.newCall(request).execute()
        } catch (error: Throwable) {
            throw AiErrorMapper.fromThrowable(error, provider.id)
        }
        response.use { checked ->
            val body = checked.body?.string().orEmpty()
            if (!checked.isSuccessful) {
                throw AiErrorMapper.fromHttp(
                    statusCode = checked.code,
                    body = body,
                    providerId = provider.id,
                    retryAfterSeconds = checked.header("Retry-After")?.toLongOrNull(),
                )
            }
            return body
        }
    }

    protected fun requireModel(model: String): String {
        if (model.isBlank()) {
            throw AiProviderException(
                AiFailure(
                    kind = AiErrorKind.INVALID_REQUEST,
                    message = "尚未配置模型 ID",
                    providerId = provider.id,
                ),
            )
        }
        return model
    }

    protected fun parseOpenAiModels(rawBody: String): List<ModelInfo> {
        return try {
            val root = JSONObject(rawBody)
            val models = root.opt("data") as? JSONArray ?: JSONArray()
            (0 until models.length()).mapNotNull { index ->
                val model = models.opt(index) as? JSONObject ?: return@mapNotNull null
                model.optStringOrNull("id")?.let {
                    ModelInfo(
                        id = it,
                        displayName = model.optStringOrNull("display_name"),
                        ownedBy = model.optStringOrNull("owned_by"),
                    )
                }
            }
        } catch (error: Throwable) {
            throw AiErrorMapper.fromThrowable(error, provider.id)
        }
    }

    protected fun parseAnthropicModels(rawBody: String): List<ModelInfo> {
        return try {
            val root = JSONObject(rawBody)
            val models = root.opt("data") as? JSONArray ?: JSONArray()
            (0 until models.length()).mapNotNull { index ->
                val model = models.opt(index) as? JSONObject ?: return@mapNotNull null
                model.optStringOrNull("id")?.let {
                    ModelInfo(
                        id = it,
                        displayName = model.optStringOrNull("display_name"),
                    )
                }
            }
        } catch (error: Throwable) {
            throw AiErrorMapper.fromThrowable(error, provider.id)
        }
    }

    protected fun jsonError(message: String): Nothing = throw AiProviderException(
        AiFailure(
            kind = AiErrorKind.INVALID_RESPONSE,
            message = message,
            providerId = provider.id,
        ),
    )

    protected fun parseJsonOrError(rawBody: String): JSONObject = try {
        JSONObject(rawBody)
    } catch (error: Exception) {
        throw AiProviderException(
            AiFailure(
                kind = AiErrorKind.INVALID_RESPONSE,
                message = "提供商返回的内容不是有效 JSON",
                providerId = provider.id,
                rawBody = rawBody,
            ),
            error,
        )
    }

    protected fun emptyModelList(): List<ModelInfo> = provider.modelIds.map { ModelInfo(it) }

    protected fun urlHasHttps(url: String): Boolean =
        runCatching { url.toHttpUrl().scheme == "https" }.getOrDefault(false)
}

/** Validation used by the provider editor before saving a profile. */
object ProviderProfileValidation {
    fun validate(profile: ProviderProfile): List<String> {
        val errors = mutableListOf<String>()
        val parsed = runCatching { profile.baseUrl.toHttpUrl() }.getOrNull()
        if (parsed == null || parsed.scheme != "https") {
            errors += "Base URL 必须是 HTTPS 地址"
        }
        if (profile.id.isBlank()) errors += "提供商 ID 不能为空"
        if (profile.name.isBlank()) errors += "提供商名称不能为空"
        if (profile.baseUrl.isBlank()) errors += "Base URL 不能为空"
        if (profile.protocol == io.github.pheobesouthwood.moonanenglish.domain.AiProtocol.BAIDU_OCR &&
            profile.apiKey.isBlank()
        ) {
            errors += "百度 OCR 需要 API Key"
        }
        if (profile.protocol == io.github.pheobesouthwood.moonanenglish.domain.AiProtocol.BAIDU_OCR &&
            profile.apiSecret.isBlank()
        ) {
            errors += "百度 OCR 需要 Secret Key"
        }
        return errors
    }
}
