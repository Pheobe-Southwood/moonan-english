package io.github.pheobesouthwood.moonanenglish

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import org.junit.Rule
import org.junit.Test

class AppLaunchTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun threePrimaryDestinationsAreVisible() {
        // “练习”同时是页面标题和底栏标签，因此按集合断言一个可见节点。
        compose.onAllNodesWithText("练习")[0].assertIsDisplayed()
        compose.onAllNodesWithText("历史")[0].assertIsDisplayed()
        compose.onAllNodesWithText("设置")[0].assertIsDisplayed()
    }
}
