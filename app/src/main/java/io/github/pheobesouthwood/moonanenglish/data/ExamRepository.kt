package io.github.pheobesouthwood.moonanenglish.data

import android.content.Context
import io.github.pheobesouthwood.moonanenglish.domain.ExamQuestion
import io.github.pheobesouthwood.moonanenglish.domain.QuestionType
import kotlinx.serialization.json.Json

class ExamRepository(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }
    private val questions: List<ExamQuestion> by lazy {
        context.assets.open("exams.json").bufferedReader().use {
            json.decodeFromString<List<ExamQuestion>>(it.readText())
        }.sortedWith(compareBy<ExamQuestion> { it.year }.thenBy { it.questionType.ordinal })
    }

    fun all(): List<ExamQuestion> = questions
    fun years(): List<Int> = questions.map { it.year }.distinct().sortedDescending()
    fun forYear(year: Int): List<ExamQuestion> = questions.filter { it.year == year }
    fun find(year: Int, type: QuestionType): ExamQuestion? =
        questions.firstOrNull { it.year == year && it.questionType == type }
    fun byId(id: String): ExamQuestion? = questions.firstOrNull { it.id == id }
}
