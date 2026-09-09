package io.github.pheobesouthwood.moonanenglish.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ResultParserTest {
    @Test
    fun parsesStructuredGradingAndKeepsRawText() {
        val raw = """{"score":8.5,"maxScore":10,"summary":"总体准确","deductions":[{"category":"语法","points":1.5,"original":"x","explanation":"说明"}],"revisedAnswer":"better"}"""
        val result = ResultParser.grading(raw, raw, 10)!!
        assertEquals(8.5, result.score, 0.0)
        assertEquals("x", result.deductions.single().evidence)
        assertEquals(raw, result.rawResponse)
    }

    @Test
    fun rejectsOutOfRangeScoreForRawFallback() {
        assertNull(ResultParser.grading("""{"score":12,"maxScore":10,"summary":"","deductions":[],"revisedAnswer":""}""", "raw", 10))
    }
}
