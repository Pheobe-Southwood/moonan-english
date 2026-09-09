package io.github.pheobesouthwood.moonanenglish.ai

import io.github.pheobesouthwood.moonanenglish.domain.PromptDefaults
import io.github.pheobesouthwood.moonanenglish.domain.PromptStage
import io.github.pheobesouthwood.moonanenglish.domain.PromptValidationException
import io.github.pheobesouthwood.moonanenglish.domain.PromptVariableValidator
import io.github.pheobesouthwood.moonanenglish.domain.PromptVariables
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptModelsTest {
    @Test
    fun shipsExactlySevenEditableDefaults() {
        assertEquals(7, PromptDefaults.allDefaults().size)
        PromptDefaults.allDefaults().values.forEach {
            assertTrue(PromptVariableValidator.validateForSave(it).valid)
        }
    }

    @Test
    fun saveAllowsMissingRuntimeValuesButInvocationDoesNot() {
        val template = PromptDefaults.defaultFor(PromptStage.TRANSLATION_GRADING)
        assertTrue(PromptVariableValidator.validateForSave(template).valid)
        val missing = PromptVariableValidator.validateForInvocation(template, emptyMap())
        assertFalse(missing.valid)
        assertTrue(PromptVariables.YEAR in missing.missingVariables)
    }

    @Test
    fun invocationRejectsAnEditedTemplateThatRemovedARequiredPlaceholder() {
        val template = PromptDefaults.defaultFor(PromptStage.TRANSLATION_GRADING)
            .copy(userTemplate = "只根据 {{question}} 评分")
        assertTrue(PromptVariableValidator.validateForSave(template).valid)
        val validation = PromptVariableValidator.validateForInvocation(
            template,
            mapOf("year" to "2024", "question" to "Q", "answer" to "A", "rubric" to "R"),
        )
        assertFalse(validation.valid)
        assertTrue(PromptVariables.ANSWER in validation.missingVariables)
    }

    @Test
    fun renderReplacesVariablesAndRejectsUnknownOnSave() {
        val template = PromptDefaults.defaultFor(PromptStage.TRANSLATION_GRADING)
        val rendered = PromptVariableValidator.render(
            template,
            mapOf(
                "year" to "2024",
                "question" to "Question",
                "answer" to "Answer",
                "maxScore" to "10",
                "rubric" to "Rubric",
            ),
        )
        assertTrue("2024" in rendered.userPrompt)
        assertTrue("Answer" in rendered.userPrompt)

        val bad = template.copy(userTemplate = "{{notAllowed}}")
        assertFalse(PromptVariableValidator.validateForSave(bad).valid)
    }

    @Test(expected = PromptValidationException::class)
    fun renderRequiresStageInputs() {
        PromptVariableValidator.render(
            PromptDefaults.defaultFor(PromptStage.VISION_RECOGNITION),
            emptyMap(),
        )
    }
}
