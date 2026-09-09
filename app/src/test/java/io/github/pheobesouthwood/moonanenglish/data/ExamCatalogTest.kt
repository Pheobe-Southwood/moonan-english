package io.github.pheobesouthwood.moonanenglish.data

import io.github.pheobesouthwood.moonanenglish.domain.ExamQuestion
import io.github.pheobesouthwood.moonanenglish.domain.PaperFormat
import io.github.pheobesouthwood.moonanenglish.domain.QuestionType
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ExamCatalogTest {
    private val json = Json { ignoreUnknownKeys = false }

    private fun catalog(): List<ExamQuestion> {
        val candidates = listOf(
            File("src/main/assets/exams.json"),
            File("app/src/main/assets/exams.json"),
        )
        val file = candidates.firstOrNull(File::exists)
        assertNotNull("exams.json must be available to JVM tests", file)
        return json.decodeFromString(file!!.readText())
    }

    @Test
    fun containsEveryYearAndExpectedPaperShape() {
        val questions = catalog()
        assertEquals(72, questions.size)
        assertEquals((2002..2026).toSet(), questions.map { it.year }.toSet())
        assertEquals(questions.size, questions.map { it.id }.toSet().size)

        (2002..2004).forEach { year ->
            val yearQuestions = questions.filter { it.year == year }
            assertEquals(2, yearQuestions.size)
            assertTrue(yearQuestions.all { it.paperFormat == PaperFormat.OLD_SINGLE_ESSAY })
            assertTrue(yearQuestions.none { it.questionType == QuestionType.SMALL_ESSAY })
            assertNotNull(yearQuestions.firstOrNull { it.questionType == QuestionType.LARGE_ESSAY })
        }
        (2005..2026).forEach { year ->
            val yearQuestions = questions.filter { it.year == year }
            assertEquals(3, yearQuestions.size)
            assertTrue(yearQuestions.all { it.paperFormat == PaperFormat.MODERN_PART_A_B })
            assertEquals(setOf(QuestionType.TRANSLATION, QuestionType.SMALL_ESSAY, QuestionType.LARGE_ESSAY), yearQuestions.map { it.questionType }.toSet())
        }
    }

    @Test
    fun everyTranslationHasCompleteArticleAndFiveSegments() {
        val translations = catalog().filter { it.questionType == QuestionType.TRANSLATION }
        assertEquals(25, translations.size)
        translations.forEach { question ->
            assertEquals(10, question.maxScore)
            assertTrue("${question.year} has no article", question.contentMarkdown.length > 500)
            assertEquals("${question.year} must have five marked segments", 5, question.translationSegments.size)
            assertTrue(question.translationSegments.all { it.isNotBlank() })
            question.translationSegments.forEach { segment ->
                assertTrue("${question.year} segment is not in the article", question.contentMarkdown.contains(segment))
            }
        }
    }

    @Test
    fun knownRefTranscriptionErrorsStayFixed() {
        val questions = catalog()
        fun article(year: Int) = questions.first { it.year == year && it.questionType == QuestionType.TRANSLATION }.contentMarkdown
        fun largeImage(year: Int) = questions.first { it.year == year && it.questionType == QuestionType.LARGE_ESSAY }.imageDescription.orEmpty()

        assertTrue(article(2012).contains("theory of everything"))
        assertTrue(article(2012).contains("The second, by Joshua Greenberg"))
        assertFalse(article(2012).contains("In fact, creativity is not the exclusive preserve"))
        assertFalse(article(2013).contains("[reference:"))
        assertTrue(article(2013).contains("irrepressible urge"))
        assertTrue(article(2014).contains("Beethoven’s habit"))
        assertTrue(article(2014).contains("Funeral March"))
        assertTrue(article(2017).contains("a basis for planning to meet the possibilities of what could be a very different operating environment"))
        assertTrue(article(2023).contains("AI can also be used to identify"))
        assertFalse(article(2023).contains("Almost all our major problems involve human behavior"))
        assertTrue(article(2024).contains("odor signature"))
        assertTrue(article(2025).contains("Citizen science"))
        assertTrue(largeImage(2025).contains("95.3"))
        assertTrue(article(2026).contains("scientific literacy"))
        assertTrue(largeImage(2026).contains("39.3"))
    }

    @Test
    fun catalogContainsNoSourceMarkersOrUrls() {
        val serialized = File("app/src/main/assets/exams.json").takeIf(File::exists)?.readText()
            ?: File("src/main/assets/exams.json").readText()
        assertFalse(Regex("https?://", RegexOption.IGNORE_CASE).containsMatchIn(serialized))
        assertFalse(serialized.contains("[reference:", ignoreCase = true))
    }
}
