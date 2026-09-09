package io.github.pheobesouthwood.moonanenglish.data.ai

import io.github.pheobesouthwood.moonanenglish.domain.AiErrorKind
import io.github.pheobesouthwood.moonanenglish.domain.AiFailure
import io.github.pheobesouthwood.moonanenglish.domain.AiProviderException
import org.json.JSONObject
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.CancellationException

/** Maps heterogeneous provider errors to a small set of actionable UI states. */
object AiErrorMapper {
    fun fromHttp(
        statusCode: Int,
        body: String?,
        providerId: String? = null,
        retryAfterSeconds: Long? = null,
    ): AiProviderException {
        val diagnostic = parseDiagnostic(body)
        val lower = (diagnostic + " " + body.orEmpty()).lowercase()
        val kind = when {
            statusCode == 401 -> AiErrorKind.AUTHENTICATION
            statusCode == 403 -> AiErrorKind.AUTHORIZATION
            statusCode == 402 || lower.contains("insufficient_quota") ||
                lower.contains("insufficient_credits") || lower.contains("insufficient balance") ||
                lower.contains("余额不足") -> AiErrorKind.INSUFFICIENT_CREDITS
            statusCode == 408 -> AiErrorKind.TIMEOUT
            statusCode == 409 || statusCode == 429 -> AiErrorKind.RATE_LIMIT
            statusCode == 400 || statusCode == 404 || statusCode == 422 -> AiErrorKind.INVALID_REQUEST
            statusCode in 500..599 -> AiErrorKind.SERVER
            else -> AiErrorKind.UNKNOWN
        }
        val message = when (kind) {
            AiErrorKind.AUTHENTICATION -> "API 密钥无效或未提供"
            AiErrorKind.AUTHORIZATION -> "API 密钥没有访问该模型或接口的权限"
            AiErrorKind.RATE_LIMIT -> "请求过于频繁，请稍后手动重试"
            AiErrorKind.INSUFFICIENT_CREDITS -> "AI 提供商余额或配额不足"
            AiErrorKind.INVALID_REQUEST -> "AI 请求参数或模型配置无效"
            AiErrorKind.TIMEOUT -> "AI 请求超时，请检查网络后手动重试"
            AiErrorKind.SERVER -> "AI 提供商服务暂时不可用"
            else -> "AI 提供商返回了 HTTP $statusCode 错误"
        }
        return AiProviderException(
            AiFailure(
                kind = kind,
                message = "$message${diagnostic.takeIf { it.isNotBlank() }?.let { "：$it" }.orEmpty()}",
                httpStatus = statusCode,
                retryAfterSeconds = retryAfterSeconds,
                providerId = providerId,
                rawBody = body,
            ),
        )
    }

    fun fromThrowable(throwable: Throwable, providerId: String? = null): AiProviderException {
        if (throwable is AiProviderException) return throwable
        val kind = when (throwable) {
            is CancellationException -> AiErrorKind.CANCELLED
            is SocketTimeoutException -> AiErrorKind.TIMEOUT
            is UnknownHostException -> AiErrorKind.NETWORK
            is IOException -> AiErrorKind.NETWORK
            is org.json.JSONException,
            is IllegalArgumentException -> AiErrorKind.INVALID_RESPONSE
            else -> AiErrorKind.UNKNOWN
        }
        val message = when (kind) {
            AiErrorKind.CANCELLED -> "操作已取消"
            AiErrorKind.TIMEOUT -> "网络请求超时，请稍后手动重试"
            AiErrorKind.NETWORK -> "无法连接 AI 提供商，请检查网络和 Base URL"
            AiErrorKind.INVALID_RESPONSE -> "AI 返回内容无法解析，请检查模型或提示词"
            else -> throwable.message ?: "AI 请求失败"
        }
        return AiProviderException(
            AiFailure(kind = kind, message = message, providerId = providerId),
            throwable,
        )
    }

    private fun parseDiagnostic(body: String?): String {
        if (body.isNullOrBlank()) return ""
        return try {
            val root = JSONObject(body)
            val error = root.opt("error")
            when (error) {
                is JSONObject -> error.optStringOrNull("message")
                    ?: error.optStringOrNull("detail")
                    ?: error.optStringOrNull("code")
                else -> root.optStringOrNull("message")
                    ?: root.optStringOrNull("error_description")
                    ?: root.optStringOrNull("error_msg")
                    ?: root.optStringOrNull("error_code")
            }.orEmpty()
        } catch (_: Exception) {
            body.trim().take(240)
        }
    }
}

