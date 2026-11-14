package com.example.notifications

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.model.AppNotificationItem
import com.example.data.model.StoreConstants
import java.util.Locale

data class AppPermissionStatus(
    val internetGranted: Boolean,
    val isOnline: Boolean,
    val locationGranted: Boolean,
    val notificationGranted: Boolean
)

data class DetectedLocationResult(
    val displayText: String,
    val matchedDivision: String? = null
)

object SprNotificationHelper {
    const val CHANNEL_ID = "spr_zone_announcements"
    const val EXTRA_OPEN_NOTIFICATIONS = "extra_open_notifications"
    const val EXTRA_PRODUCT_ID = "extra_product_id"
    const val EXTRA_NOTIFICATION_ID = "extra_notification_id"

    fun requiredRuntimePermissions(): Array<String> {
        val list = mutableListOf(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            list += Manifest.permission.POST_NOTIFICATIONS
        }
        return list.toTypedArray()
    }

    @Suppress("DEPRECATION")
    fun hasInternetPermission(context: Context): Boolean {
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.INTERNET
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            return true
        }
        return try {
            val pkgInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong())
                )
            } else {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.GET_PERMISSIONS
                )
            }
            pkgInfo.requestedPermissions?.contains(Manifest.permission.INTERNET) == true
        } catch (_: Throwable) {
            false
        }
    }

    fun isOnline(context: Context): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return false
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (_: Throwable) {
            false
        }
    }

    fun hasLocationPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
    }

    fun hasNotificationPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun readPermissionStatus(context: Context): AppPermissionStatus {
        return AppPermissionStatus(
            internetGranted = hasInternetPermission(context),
            isOnline = isOnline(context),
            locationGranted = hasLocationPermission(context),
            notificationGranted = hasNotificationPermission(context)
        )
    }

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.notif_channel_name),
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = context.getString(R.string.notif_channel_desc)
                    enableVibration(true)
                }
                nm.createNotificationChannel(channel)
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun showSystemNotification(context: Context, item: AppNotificationItem) {
        if (!hasNotificationPermission(context)) return
        ensureChannel(context)

        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_NOTIFICATIONS, true)
            putExtra(EXTRA_NOTIFICATION_ID, item.id)
            if (item.productId.isNotBlank()) {
                putExtra(EXTRA_PRODUCT_ID, item.productId)
            }
        }
        val pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val pendingIntent = PendingIntent.getActivity(
            context,
            item.id.hashCode(),
            tapIntent,
            pendingFlags
        )

        val prefix = when (item.type.lowercase()) {
            "order" -> "🚚 "
            "info" -> "📢 "
            else -> "🔥 "
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("$prefix${item.title}")
            .setContentText(item.message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(item.message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(item.id.hashCode(), notification)
        } catch (_: SecurityException) {
            // Permission revoked concurrently
        }
    }

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    fun detectCurrentLocation(context: Context): DetectedLocationResult {
        if (!hasLocationPermission(context)) {
            return DetectedLocationResult("Location permission required")
        }
        return try {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
                ?: return DetectedLocationResult("Location service unavailable")
            val candidates = listOf(
                LocationManager.NETWORK_PROVIDER,
                LocationManager.GPS_PROVIDER,
                LocationManager.PASSIVE_PROVIDER
            )
            var bestLoc: Location? = null
            for (provider in candidates) {
                val loc = try {
                    lm.getLastKnownLocation(provider)
                } catch (_: Throwable) {
                    null
                }
                if (loc != null && (bestLoc == null || loc.time > bestLoc.time)) {
                    bestLoc = loc
                }
            }
            if (bestLoc == null) {
                return DetectedLocationResult("Location enabled (move outdoors or enable GPS/Wi-Fi to refresh fix)")
            }

            val address = try {
                val geocoder = Geocoder(context, Locale.ENGLISH)
                geocoder.getFromLocation(bestLoc.latitude, bestLoc.longitude, 1)?.firstOrNull()
            } catch (_: Throwable) {
                null
            }
            if (address != null) {
                val parts = listOfNotNull(
                    address.subLocality?.takeIf { it.isNotBlank() },
                    address.locality?.takeIf { it.isNotBlank() },
                    address.adminArea?.takeIf { it.isNotBlank() },
                    address.countryName?.takeIf { it.isNotBlank() }
                ).distinct()
                val joined = parts.joinToString(", ").ifBlank {
                    "Lat ${"%.3f".format(Locale.US, bestLoc.latitude)}, Lon ${"%.3f".format(Locale.US, bestLoc.longitude)}"
                }
                val matchedDiv = StoreConstants.BD_DIVISIONS.firstOrNull { div ->
                    joined.contains(div, ignoreCase = true) ||
                        (div == "Chattogram" && joined.contains("Chittagong", ignoreCase = true)) ||
                        (div == "Barishal" && joined.contains("Barisal", ignoreCase = true))
                }
                DetectedLocationResult(joined, matchedDiv)
            } else {
                DetectedLocationResult(
                    "Lat ${"%.3f".format(Locale.US, bestLoc.latitude)}, Lon ${"%.3f".format(Locale.US, bestLoc.longitude)}"
                )
            }
        } catch (_: Throwable) {
            DetectedLocationResult("Location enabled")
        }
    }

    fun openAppPermissionSettings(context: Context) {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null)
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (_: Throwable) {
            // Ignore if settings activity cannot be resolved
        }
    }
}
