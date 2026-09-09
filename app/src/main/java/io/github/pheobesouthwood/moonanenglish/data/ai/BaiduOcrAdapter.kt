package io.github.pheobesouthwood.moonanenglish.data.ai

import io.github.pheobesouthwood.moonanenglish.domain.AiErrorKind
import io.github.pheobesouthwood.moonanenglish.domain.AiFailure
import io.github.pheobesouthwood.moonanenglish.domain.AiImage
import io.github.pheobesouthwood.moonanenglish.domain.AiProviderException
import io.github.pheobesouthwood.moonanenglish.domain.AiRequest
import io.github.pheobesouthwood.moonanenglish.domain.AiResponse
import io.github.pheobesouthwood.moonanenglish.domain.ModelInfo
import io.github.pheobesouthwood.moonanenglish.domain.OcrProviderAdapter
import io.github.pheobesouthwood.moonanenglish.domain.OcrRequest
import io.github.pheobesouthwood.moonanenglish.domain.OcrResponse
import io.github.pheobesouthwood.moonanenglish.domain.ProviderProfile
import okhttp3.FormBody
import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Baidu handwriting OCR adapter; it is deliberately separate from vision LLMs. */
class BaiduOcrAdapter(
    provider: ProviderProfile,
    client: OkHttpClient = OkHttpClient(),
) : BaseAiProvider(provider, client), OcrProviderAdapter {

    @Volatile
    private var cachedToken: Token? = null

    override suspend fun complete(request: AiRequest): AiResponse {
        if (request.images.isEmpty()) {
            throw AiProviderException(
                AiFailure(
                    kind = AiErrorKind.INVALID_REQUEST,
                    message = "百度 OCR 至少需要一张图片",
                    providerId = provider.id,
                ),
            )
        }
        val ocr = recognize(
            OcrRequest(
                images = request.images,
                languageType = "ENG",
            ),
        )
        val textJson = JSONObject().put("text", ocr.text).toString()
        return AiJsonParser.response(
            providerId = provider.id,
            model = request.model.ifBlank { "handwriting" },
            text = textJson,
            rawBody = ocr.rawBody,
        )
    }

    override suspend fun listModels(): List<ModelInfo> {
        accessToken()
        return listOf(ModelInfo(id = "handwriting", displayName = "手写文字识别"))
    }

    override suspend fun recognize(request: OcrRequest): OcrResponse {
        if (request.images.isEmpty()) {
            throw AiProviderException(
                AiFailure(
                    kind = AiErrorKind.INVALID_REQUEST,
                    message = "百度 OCR 至少需要一张图片",
                    providerId = provider.id,
                ),
            )
        }
        val texts = request.images.map { recognizeOne(it, request) }
        return OcrResponse(
            text = texts.joinToString("\n\n") { it.text },
            rawBody = texts.joinToString("\n") { it.rawBody },
            providerId = provider.id,
        )
    }

    private fun recognizeOne(image: AiImage, request: OcrRequest): OcrPage {
        val token = accessToken()
        val form = FormBody.Builder()
            .add("image", image.base64)
            .add("language_type", request.languageType)
            .add("detect_direction", request.detectDirection.toString())
            .add("eng_granularity", "word")
            .add("detect_alteration", "true")
            .build()
        val httpRequest = requestBuilder(ocrUrl(token))
            .header("Accept", "application/json")
            .post(form)
            .build()
        val raw = execute(httpRequest)
        val root = parseJsonOrError(raw)
        val errorCode = root.optIntOrNull("error_code")
        if (errorCode != null && errorCode != 0) {
            val errorMessage = root.optStringOrNull("error_msg") ?: "百度 OCR 返回错误"
            throw AiErrorMapper.fromHttp(
                statusCode = if (errorCode == 110 || errorCode == 111) 401 else 400,
                body = raw,
                providerId = provider.id,
            ).let { mapped ->
                AiProviderException(
                    mapped.failure.copy(message = "百度 OCR：$errorMessage"),
                    mapped,
                )
            }
        }
        val result = root.optArrayOrNull("words_result") ?: JSONArray()
        val text = (0 until result.length()).mapNotNull { index ->
            (result.opt(index) as? JSONObject)?.optStringOrNull("words")
        }.joinToString("\n")
        return OcrPage(text, raw)
    }

    private fun accessToken(): String {
        val current = cachedToken
        if (current != null && current.expiresAtMillis > System.currentTimeMillis() + TOKEN_SAFETY_WINDOW_MS) {
            return current.value
        }
        synchronized(this) {
            val secondCheck = cachedToken
            if (secondCheck != null && secondCheck.expiresAtMillis > System.currentTimeMillis() + TOKEN_SAFETY_WINDOW_MS) {
                return secondCheck.value
            }
            if (provider.apiKey.isBlank() || provider.apiSecret.isBlank()) {
                throw AiProviderException(
                    AiFailure(
                        kind = AiErrorKind.AUTHENTICATION,
                        message = "百度 OCR 需要 API Key 和 Secret Key",
                        providerId = provider.id,
                    ),
                )
            }
            val form = FormBody.Builder()
                .add("grant_type", "client_credentials")
                .add("client_id", provider.apiKey)
                .add("client_secret", provider.apiSecret)
                .build()
            val request = requestBuilder(tokenUrl())
                .header("Accept", "application/json")
                .post(form)
                .build()
            val raw = execute(request)
            val root = parseJsonOrError(raw)
            val access = root.optStringOrNull("access_token")
                ?: throw AiProviderException(
                    AiFailure(
                        kind = AiErrorKind.AUTHENTICATION,
                        message = root.optStringOrNull("error_description") ?: "百度 OCR Token 获取失败",
                        providerId = provider.id,
                        rawBody = raw,
                    ),
                )
            val expiresIn = root.optLongOrNull("expires_in") ?: DEFAULT_TOKEN_TTL_SECONDS
            return access.also {
                cachedToken = Token(
                    value = it,
                    expiresAtMillis = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(expiresIn),
                )
            }
        }
    }

    private fun tokenUrl(): String = endpoint("oauth/2.0/token")

    private fun ocrUrl(token: String): String =
        endpoint("rest/2.0/ocr/v1/handwriting") + "?access_token=" +
            java.net.URLEncoder.encode(token, Charsets.UTF_8.name())

    private data class Token(val value: String, val expiresAtMillis: Long)

    private data class OcrPage(val text: String, val rawBody: String)

    private companion object {
        const val DEFAULT_TOKEN_TTL_SECONDS = 2_592_000L
        const val TOKEN_SAFETY_WINDOW_MS = 60_000L
    }
}

typealias BaiduOCRAdapter = BaiduOcrAdapter
