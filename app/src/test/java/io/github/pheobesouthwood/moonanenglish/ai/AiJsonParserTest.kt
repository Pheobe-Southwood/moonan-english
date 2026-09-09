package io.github.pheobesouthwood.moonanenglish.ai

import io.github.pheobesouthwood.moonanenglish.data.ai.AiJsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiJsonParserTest {
    @Test
    fun extractsJsonFromMarkdownFenceAndPreamble() {
        val parsed = AiJsonParser.parseStructuredJson(
            "结果如下：\n```json\n{\"score\":7,\"summary\":\"ok\"}\n```",
        )
        assertNotNull(parsed)
        assertEquals(7, parsed!!.getInt("score"))
    }

    @Test
    fun parsesOpenAiResponsesTextAndUsage() {
        val response = AiJsonParser.parseOpenAiResponse(
            providerId = "test",
            model = "gpt-test",
            rawBody = """
                {"output":[{"type":"message","content":[{"type":"output_text","text":"{\"score\":8}"}]}],"usage":{"input_tokens":12,"output_tokens":4,"total_tokens":16}}
            """.trimIndent(),
        )
        assertEquals("{\"score\":8}", response.text)
        assertEquals(16L, response.usage?.totalTokens)
        assertTrue(response.structuredJson!!.contains("score"))
    }

    @Test
    fun parsesChatAnthropicAndGeminiShapes() {
        val chat = AiJsonParser.parseOpenAiResponse(
            "test",
            "chat",
            """{"choices":[{"message":{"content":[{"type":"text","text":"{\"a\":1}"}]}}]}""",
        )
        val anthropic = AiJsonParser.parseAnthropicResponse(
            "test",
            "claude",
            """{"content":[{"type":"text","text":"{\"a\":2}"}]}""",
        )
        val gemini = AiJsonParser.parseGeminiResponse(
            "test",
            "gemini",
            """{"candidates":[{"content":{"parts":[{"text":"{\"a\":3}"}]}}]}""",
        )
        assertEquals("{\"a\":1}", chat.text)
        assertEquals("{\"a\":2}", anthropic.text)
        assertEquals("{\"a\":3}", gemini.text)
    }
}

