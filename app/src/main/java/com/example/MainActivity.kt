package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.notifications.SprNotificationHelper
import com.example.ui.SprZoneApp
import com.example.ui.viewmodel.SprZoneViewModel

class MainActivity : ComponentActivity() {
    private var openNotificationsNonce by mutableIntStateOf(0)
    private var deepLinkProductId by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        SprNotificationHelper.ensureChannel(this)
        handleNotificationIntent(intent)
        setContent {
            val sprViewModel: SprZoneViewModel = viewModel(factory = SprZoneViewModel.Factory)
            SprZoneApp(
                viewModel = sprViewModel,
                openNotificationsNonce = openNotificationsNonce,
                deepLinkProductId = deepLinkProductId,
                onDeepLinkConsumed = { deepLinkProductId = null }
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNotificationIntent(intent)
    }

    private fun handleNotificationIntent(intent: Intent?) {
        if (intent == null) return
        val prodId = intent.getStringExtra(SprNotificationHelper.EXTRA_PRODUCT_ID)
        if (!prodId.isNullOrBlank()) {
            deepLinkProductId = prodId
        } else if (intent.getBooleanExtra(SprNotificationHelper.EXTRA_OPEN_NOTIFICATIONS, false)) {
            openNotificationsNonce++
        }
    }
}
