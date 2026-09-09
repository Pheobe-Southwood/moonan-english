package io.github.pheobesouthwood.moonanenglish.ai

import io.github.pheobesouthwood.moonanenglish.data.ai.AnthropicMessagesAdapter
import io.github.pheobesouthwood.moonanenglish.data.ai.BaiduOcrAdapter
import io.github.pheobesouthwood.moonanenglish.data.ai.GeminiGenerateContentAdapter
import io.github.pheobesouthwood.moonanenglish.data.ai.OpenAiChatCompletionsAdapter
import io.github.pheobesouthwood.moonanenglish.data.ai.OpenAiResponsesAdapter
import io.github.pheobesouthwood.moonanenglish.domain.AiImage
import io.github.pheobesouthwood.moonanenglish.domain.AiProtocol
import io.github.pheobesouthwood.moonanenglish.domain.AiRequest
import io.github.pheobesouthwood.moonanenglish.domain.OcrRequest
import io.github.pheobesouthwood.moonanenglish.domain.ProviderProfile
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AdapterWireFormatTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun openAiResponsesSendsVisionInputAndParsesResponse() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"output_text":"{\"score\":9}"}"""))
        val adapter = OpenAiResponsesAdapter(profile(AiProtocol.OPENAI_RESPONSES, "/v1"), OkHttpClient())
        val response = adapter.complete(request())
        val recorded = server.takeRequest()
        assertEquals("/v1/responses", recorded.requestUrl?.encodedPath)
        assertTrue(recorded.body.readUtf8().contains("input_image"))
        assertEquals("{\"score\":9}", response.text)
    }

    @Test
    fun chatCompletionsUsesCompatibleMessageShape() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"choices":[{"message":{"content":"{\"score\":8}"}}]}"""))
        val adapter = OpenAiChatCompletionsAdapter(profile(AiProtocol.OPENAI_CHAT_COMPLETIONS, "/v1"), OkHttpClient())
        val response = adapter.complete(request())
        val recorded = server.takeRequest()
        assertEquals("/v1/chat/completions", recorded.requestUrl?.encodedPath)
        assertTrue(recorded.body.readUtf8().contains("image_url"))
        assertEquals("{\"score\":8}", response.text)
    }

    @Test
    fun anthropicAndGeminiSendNativeImageParts() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"content":[{"type":"text","text":"{\"score\":7}"}]}"""))
        val anthropic = AnthropicMessagesAdapter(profile(AiProtocol.ANTHROPIC_MESSAGES, "/v1"), OkHttpClient())
        assertEquals("{\"score\":7}", anthropic.complete(request()).text)
        val anthropicRequest = server.takeRequest()
        assertEquals("/v1/messages", anthropicRequest.requestUrl?.encodedPath)
        assertTrue(anthropicRequest.body.readUtf8().contains("\"type\":\"image\""))

        server.enqueue(MockResponse().setBody("""{"candidates":[{"content":{"parts":[{"text":"{\"score\":6}"}]}}]}"""))
        val gemini = GeminiGenerateContentAdapter(profile(AiProtocol.GEMINI_GENERATE_CONTENT, "/v1beta"), OkHttpClient())
        assertEquals("{\"score\":6}", gemini.complete(request()).text)
        val geminiRequest = server.takeRequest()
        assertEquals("/v1beta/models/test-model:generateContent", geminiRequest.requestUrl?.encodedPath)
        assertTrue(geminiRequest.body.readUtf8().contains("inlineData"))
    }

    @Test
    fun baiduFetchesTokenThenRecognizesEachImage() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"access_token":"token","expires_in":3600}"""))
        server.enqueue(MockResponse().setBody("""{"words_result":[{"words":"first line"}]}"""))
        val adapter = BaiduOcrAdapter(
            profile(AiProtocol.BAIDU_OCR, ""),
            OkHttpClient(),
        )
        val response = adapter.recognize(OcrRequest(listOf(AiImage("YWJj"))))
        assertEquals("first line", response.text)
        assertEquals("/oauth/2.0/token", server.takeRequest().requestUrl?.encodedPath)
        val ocrRequest = server.takeRequest()
        assertEquals("/rest/2.0/ocr/v1/handwriting", ocrRequest.requestUrl?.encodedPath)
        val form = ocrRequest.body.readUtf8()
        assertTrue(form.contains("eng_granularity=word"))
        assertTrue(form.contains("detect_alteration=true"))
    }

    private fun request() = AiRequest(
        model = "test-model",
        systemPrompt = "system",
        userPrompt = "user",
        images = listOf(AiImage(base64 = "YWJj", mimeType = "image/png")),
    )

    private fun profile(protocol: AiProtocol, path: String): ProviderProfile = ProviderProfile(
        id = "test",
        name = "Test",
        protocol = protocol,
        baseUrl = server.url(path).toString().trimEnd('/'),
        apiKey = "key",
        apiSecret = "secret",
        supportsVision = true,
    )
}
