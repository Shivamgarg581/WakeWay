package com.wakeway.app

import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test

class MainActivityTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun homeShowsCoreDestinationAction() {
        rule.onNodeWithText("SET DESTINATION", useUnmergedTree = true).assertExists()
    }

    @Test
    fun destinationFlowOpensWithoutCrashing() {
        rule.onNodeWithText("SET DESTINATION", useUnmergedTree = true).performClick()
        rule.onNodeWithText("Set your destination", useUnmergedTree = true).assertExists()
    }
}
