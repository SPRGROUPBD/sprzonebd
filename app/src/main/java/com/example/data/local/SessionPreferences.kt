package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.DeliveryZone
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "spr_zone_prefs")

class SessionPreferences(private val context: Context) {
    private object Keys {
        val LOGGED_IN_EMAIL = stringPreferencesKey("logged_in_email")
        val DELIVERY_ZONE = stringPreferencesKey("delivery_zone")
        val DARK_MODE = booleanPreferencesKey("dark_mode")
        val SEEN_NOTIFICATION_IDS = stringSetPreferencesKey("seen_notification_ids")
        val READ_NOTIFICATION_IDS = stringSetPreferencesKey("read_notification_ids")
    }

    val loggedInEmail: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[Keys.LOGGED_IN_EMAIL]?.takeIf { it.isNotBlank() }
    }

    val deliveryZone: Flow<DeliveryZone> = context.dataStore.data.map { prefs ->
        val name = prefs[Keys.DELIVERY_ZONE]
        DeliveryZone.entries.find { it.name == name } ?: DeliveryZone.DHAKA_METRO
    }

    val isDarkMode: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.DARK_MODE] ?: false
    }

    val seenNotificationIds: Flow<Set<String>> = context.dataStore.data.map { prefs ->
        prefs[Keys.SEEN_NOTIFICATION_IDS] ?: emptySet()
    }

    val readNotificationIds: Flow<Set<String>> = context.dataStore.data.map { prefs ->
        prefs[Keys.READ_NOTIFICATION_IDS] ?: emptySet()
    }

    suspend fun setLoggedInEmail(email: String?) {
        context.dataStore.edit { prefs ->
            if (email.isNullOrBlank()) {
                prefs.remove(Keys.LOGGED_IN_EMAIL)
            } else {
                prefs[Keys.LOGGED_IN_EMAIL] = email.trim().lowercase()
            }
        }
    }

    suspend fun setDeliveryZone(zone: DeliveryZone) {
        context.dataStore.edit { prefs ->
            prefs[Keys.DELIVERY_ZONE] = zone.name
        }
    }

    suspend fun setDarkMode(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.DARK_MODE] = enabled
        }
    }

    suspend fun markNotificationsSeen(ids: Collection<String>) {
        if (ids.isEmpty()) return
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.SEEN_NOTIFICATION_IDS] ?: emptySet()
            prefs[Keys.SEEN_NOTIFICATION_IDS] = (current + ids).toList().takeLast(200).toSet()
        }
    }

    suspend fun markNotificationsRead(ids: Collection<String>) {
        if (ids.isEmpty()) return
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.READ_NOTIFICATION_IDS] ?: emptySet()
            prefs[Keys.READ_NOTIFICATION_IDS] = (current + ids).toList().takeLast(200).toSet()
        }
    }
}
