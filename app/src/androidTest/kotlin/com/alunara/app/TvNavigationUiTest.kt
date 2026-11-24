package com.alunara.app

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test

class TvNavigationUiTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun backRendersHomeFromSettingsAndLibrary() {
        val home = composeRule.onNodeWithContentDescription(composeRule.activity.getString(R.string.nav_home))
        for (destination in listOf(R.string.nav_settings, R.string.nav_library)) {
            val tab = composeRule.onNodeWithContentDescription(composeRule.activity.getString(destination))
            tab.performClick()
            tab.assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, true))
            composeRule.runOnIdle { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
            home.assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, true))
        }
    }
}
