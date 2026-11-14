package com.example

import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.notifications.SprNotificationHelper
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SprNotificationAndPermissionTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun internetPermissionDeclaredAndNotificationChannelCreated() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val status = SprNotificationHelper.readPermissionStatus(context)
        assertTrue(status.internetGranted)

        SprNotificationHelper.ensureChannel(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = nm.getNotificationChannel(SprNotificationHelper.CHANNEL_ID)
            assertNotNull(channel)
        }
    }

    @Test
    fun notificationsInboxAndPermissionsCardAccessible() {
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("home_notifications_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("App Notifications").assertExists()
        composeRule.onNodeWithText("Close").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("nav_account").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("app_permissions_card").assertExists()
    }
}
