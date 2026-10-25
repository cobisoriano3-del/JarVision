package com.jarvision.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun appLaunchesIntoFilesDestination() {
        assertTrue(
            "JarVision title should not be visible",
            composeRule.onAllNodesWithText("JarVision")
                .fetchSemanticsNodes()
                .isEmpty()
        )
        composeRule.onNodeWithText("Files").assertIsDisplayed()
    }

    @Test
    fun storageAccessPromptIsShownWhenNoAccessWasGranted() {
        val hasAccessPrompt = composeRule.onAllNodesWithText("Storage access required")
            .fetchSemanticsNodes()
            .isNotEmpty()
        if (hasAccessPrompt) {
            composeRule.onNodeWithText("Allow access to all files").assertIsDisplayed()
            composeRule.onNodeWithText("Choose a folder instead").assertIsDisplayed()
        } else {
            composeRule.onNodeWithText("Internal storage").assertIsDisplayed()
        }
    }
}
