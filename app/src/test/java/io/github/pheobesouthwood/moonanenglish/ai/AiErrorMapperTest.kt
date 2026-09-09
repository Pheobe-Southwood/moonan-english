package io.github.pheobesouthwood.moonanenglish.ai

import io.github.pheobesouthwood.moonanenglish.data.ai.AiErrorMapper
import io.github.pheobesouthwood.moonanenglish.domain.AiErrorKind
import io.github.pheobesouthwood.moonanenglish.domain.AiProviderException
import org.junit.Assert.assertEquals
import org.junit.Test

class AiErrorMapperTest {
    @Test
    fun mapsAuthenticationRateLimitAndCredits() {
        assertEquals(
            AiErrorKind.AUTHENTICATION,
            AiErrorMapper.fromHttp(401, "{\"error\":{\"message\":\"bad key\"}}", "p").failure.kind,
        )
        assertEquals(
            AiErrorKind.RATE_LIMIT,
            AiErrorMapper.fromHttp(429, "{}", "p", retryAfterSeconds = 3).failure.kind,
        )
        assertEquals(
            AiErrorKind.INSUFFICIENT_CREDITS,
            AiErrorMapper.fromHttp(400, "{\"error\":{\"code\":\"insufficient_quota\"}}", "p").failure.kind,
        )
    }

    @Test
    fun preservesExistingProviderException() {
        val original = AiErrorMapper.fromHttp(500, "down", "p")
        val mapped = AiErrorMapper.fromThrowable(original, "other")
        assertEquals(original, mapped)
        assertEquals(AiErrorKind.SERVER, (mapped as AiProviderException).failure.kind)
    }
}

