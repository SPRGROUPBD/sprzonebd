package com.example

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MainActivityLaunchTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun mainActivityLaunchesAndNavigatesAllTabsWithoutCrashing() {
        composeRule.waitForIdle()
        composeRule.onNodeWithText("SPR Zone BD").assertExists()

        composeRule.onNodeWithTag("nav_categories").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("nav_cart").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("nav_orders").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("nav_account").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("nav_home").performClick()
        composeRule.waitForIdle()
    }
}
