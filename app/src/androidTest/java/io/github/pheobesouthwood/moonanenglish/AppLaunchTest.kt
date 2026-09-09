package io.github.pheobesouthwood.moonanenglish

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

class AppLaunchTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun threePrimaryDestinationsAreVisible() {
        compose.onNodeWithText("练习").assertIsDisplayed()
        compose.onNodeWithText("历史").assertIsDisplayed()
        compose.onNodeWithText("设置").assertIsDisplayed()
    }
}
