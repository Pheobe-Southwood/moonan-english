package io.github.pheobesouthwood.moonanenglish.data

import io.github.pheobesouthwood.moonanenglish.domain.Deduction
import io.github.pheobesouthwood.moonanenglish.domain.GradingResult
import io.github.pheobesouthwood.moonanenglish.domain.GrammarIssue
import io.github.pheobesouthwood.moonanenglish.domain.ReviewResult
import io.github.pheobesouthwood.moonanenglish.domain.SentenceRevision
import org.json.JSONArray
import org.json.JSONObject

object ResultParser {
    fun grading(json: String?, raw: String, expectedMax: Int): GradingResult? = runCatching {
        val root = JSONObject(json ?: return null)
        val score = root.getDouble("score")
        val max = root.optDouble("maxScore", expectedMax.toDouble())
        require(score in 0.0..expectedMax.toDouble() && max == expectedMax.toDouble())
        GradingResult(
            score = score,
            maxScore = max,
            summary = root.optString("summary"),
            deductions = root.optJSONArray("deductions").objects().map { item ->
                Deduction(
                    category = item.optString("category"),
                    evidence = item.optString("evidence", item.optString("original")),
                    pointsDeducted = item.optDouble("pointsDeducted", item.optDouble("points", 0.0)),
                    explanation = item.optString("explanation", item.optString("suggestion")),
                )
            },
            revisedAnswer = root.optString("revisedAnswer"),
            rawResponse = raw,
        )
    }.getOrNull()

    fun review(json: String?, raw: String): ReviewResult? = runCatching {
        val root = JSONObject(json ?: return null)
        ReviewResult(
            vocabulary = root.optJSONArray("vocabulary").stringsOrObjects(),
            grammarIssues = root.optJSONArray("grammarIssues").objects().map { item ->
                GrammarIssue(item.optString("original"), item.optString("correction"), item.optString("explanation"))
            },
            sentenceRevisions = root.optJSONArray("sentenceRevisions").objects().map { item ->
                SentenceRevision(item.optString("original"), item.optString("improved", item.optString("revised")))
            },
            studyAdvice = root.optJSONArray("studyAdvice").stringsOrObjects(),
            rawResponse = raw,
        )
    }.getOrNull()

    private fun JSONArray?.objects(): List<JSONObject> = if (this == null) emptyList() else buildList {
        for (index in 0 until length()) (opt(index) as? JSONObject)?.let(::add)
    }

    private fun JSONArray?.stringsOrObjects(): List<String> = if (this == null) emptyList() else buildList {
        for (index in 0 until length()) when (val item = opt(index)) {
            is String -> add(item)
            is JSONObject -> add(
                listOf(item.optString("original"), item.optString("suggestion"), item.optString("explanation"))
                    .filter(String::isNotBlank).joinToString(" → ")
            )
        }
    }
}
