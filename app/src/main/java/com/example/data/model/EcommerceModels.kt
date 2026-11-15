package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlin.math.roundToInt

private val VARIANT_PRICE_ADAPTER by lazy {
    val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    val mapType = Types.newParameterizedType(
        Map::class.java,
        String::class.java,
        Any::class.java
    )
    moshi.adapter<Map<String, Any?>>(mapType)
}

@Entity(tableName = "products")
data class ProductEntity(
    @PrimaryKey val id: String,
    val name: String,
    val category: String,
    val brand: String,
    val sellerName: String,
    val priceBdt: Long,
    val originalPriceBdt: Long,
    val discountPercent: Int,
    val rating: Double,
    val reviewCount: Int,
    val stock: Int,
    val isFlashSale: Boolean,
    val isFeatured: Boolean,
    val imageKey: String,
    val imageUrl: String = "",
    val description: String,
    val specsSummary: String,
    val variantsCsv: String,
    val variantColorsCsv: String = "",
    val variantSizesCsv: String = "",
    val variantPricesJson: String = "{}",
    val freeDeliveryMinQty: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
) {
    val variants: List<String>
        get() = variantsCsv.split(",").map { it.trim() }.filter { it.isNotEmpty() }

    val colors: List<String>
        get() = variantColorsCsv.split(",").map { it.trim() }.filter { it.isNotEmpty() }

    val sizes: List<String>
        get() = variantSizesCsv.split(",").map { it.trim() }.filter { it.isNotEmpty() }

    val variantPricesMap: Map<String, Long>
        get() {
            if (variantPricesJson.isBlank() || variantPricesJson == "{}") return emptyMap()
            return try {
                val parsed = VARIANT_PRICE_ADAPTER.fromJson(variantPricesJson).orEmpty()
                buildMap {
                    parsed.forEach { (k, rawVal) ->
                        val v = when (rawVal) {
                            is Number -> rawVal.toDouble().roundToInt().toLong()
                            is String -> rawVal.toDoubleOrNull()?.roundToInt()?.toLong() ?: 0L
                            else -> 0L
                        }
                        if (k.isNotBlank() && v > 0L) put(k, v)
                    }
                }
            } catch (_: Throwable) {
                emptyMap()
            }
        }

    fun defaultVariantLabel(): String {
        val c = colors.firstOrNull()
        val s = sizes.firstOrNull()
        return when {
            c != null && s != null -> "$c / $s"
            c != null -> c
            s != null -> s
            else -> variants.firstOrNull() ?: "Standard"
        }
    }

    fun priceForVariant(variant: String): Long {
        val map = variantPricesMap
        if (map.isEmpty()) return priceBdt
        val clean = variant.trim()
        if (clean.isNotEmpty()) {
            val sanitized = clean
                .replace(Regex("\\s*/\\s*"), " | ")
                .replace('.', '·')
                .replace(Regex("[#$\\[\\]/]"), "-")
                .take(120)
            listOf(clean, sanitized).distinct().forEach { candidate ->
                map[candidate]?.let { return it }
                map.entries.firstOrNull { it.key.equals(candidate, ignoreCase = true) }?.let { return it.value }
            }
            val parts = clean.split('/', '|').map { it.trim() }.filter { it.isNotEmpty() }
            for (part in parts) {
                val safePart = part
                    .replace('.', '·')
                    .replace(Regex("[#$\\[\\]/]"), "-")
                    .take(120)
                listOf(part, safePart).distinct().forEach { candidate ->
                    map[candidate]?.let { return it }
                    map.entries.firstOrNull { it.key.equals(candidate, ignoreCase = true) }?.let { return it.value }
                }
            }
        }
        return priceBdt
    }

    fun discountPercentForPrice(unitPrice: Long): Int {
        return if (originalPriceBdt > unitPrice && unitPrice > 0L) {
            (((originalPriceBdt - unitPrice) * 100.0) / originalPriceBdt).roundToInt().coerceIn(0, 95)
        } else 0
    }
}

@Entity(tableName = "cart_items")
data class CartItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userEmail: String,
    val productId: String,
    val productName: String,
    val unitPriceBdt: Long,
    val originalPriceBdt: Long,
    val imageKey: String,
    val imageUrl: String = "",
    val selectedVariant: String,
    val quantity: Int,
    val freeDeliveryMinQty: Int = 0,
    val addedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "wishlist_items")
data class WishlistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userEmail: String,
    val productId: String,
    val addedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "user_accounts")
data class UserAccountEntity(
    @PrimaryKey val email: String,
    val fullName: String,
    val phone: String,
    val passwordHash: String,
    val salt: String,
    val bio: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "addresses")
data class AddressEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userEmail: String,
    val label: String,
    val recipientName: String,
    val phone: String,
    val division: String,
    val districtThana: String,
    val streetAddress: String,
    val isDefault: Boolean = false,
    val remoteKey: String = ""
) {
    val formattedAddress: String
        get() = "$streetAddress, $districtThana, $division"
}

@Entity(tableName = "orders")
data class OrderEntity(
    @PrimaryKey val orderId: String,
    val userEmail: String,
    val recipientName: String,
    val phone: String,
    val fullAddress: String,
    val division: String,
    val itemsSummary: String,
    val itemCount: Int,
    val subtotalBdt: Long,
    val discountBdt: Long,
    val deliveryFeeBdt: Long,
    val totalBdt: Long,
    val promoCode: String,
    val paymentMethod: String,
    val senderNumber: String = "",
    val trxId: String = "",
    val paymentAccountMasked: String,
    val transactionId: String,
    val paymentStatus: String,
    val orderStatus: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "reviews")
data class ReviewEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val productId: String,
    val userName: String,
    val userEmail: String,
    val rating: Int,
    val comment: String,
    val verifiedPurchase: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

data class MerchantPaymentSettings(
    val bkashNumber: String = "",
    val nagadNumber: String = "",
    val rocketNumber: String = "",
    val smsApiUrl: String = "",
    val smsApiKey: String = "",
    val autoVerifyEnabled: Boolean = true
)

data class DeliveryConfigSummary(
    val freeDeliveryMinAmountBdt: Long = StoreConstants.FREE_DELIVERY_THRESHOLD_BDT,
    val divisionRates: Map<String, Long> = emptyMap()
) {
    fun feeForDivision(division: String): Long {
        val norm = division.trim().lowercase()
        divisionRates.entries.firstOrNull { it.key.equals(norm, ignoreCase = true) }?.let { return it.value }
        return StoreConstants.deliveryFeeForDivision(division)
    }
}

data class AppNotificationItem(
    val id: String,
    val title: String,
    val message: String,
    val type: String = "promo",
    val productId: String = "",
    val targetEmail: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

enum class PaymentMethod(
    val displayName: String,
    val subtitle: String,
    val requiresMobileTrx: Boolean,
    val isCardGateway: Boolean
) {
    COD("Cash on Delivery", "Pay cash at doorstep", false, false)
}

enum class DeliveryZone(val label: String, val feeBdt: Long, val etaText: String) {
    DHAKA_METRO("Dhaka Metro", 60L, "Same-day / 24h express"),
    DHAKA_SUBURBS("Dhaka Suburbs (Gazipur / Narayanganj / Savar)", 90L, "1–2 business days"),
    OUTSIDE_DHAKA("Outside Dhaka", 120L, "2–4 business days")
}

object StoreConstants {
    const val GUEST_EMAIL = "guest@sprzonebd.local"
    const val FREE_DELIVERY_THRESHOLD_BDT = 3000L
    const val RTDB_BASE_URL = "https://e-commarcebd-default-rtdb.europe-west1.firebasedatabase.app/"
    const val FIREBASE_AUTH_BASE_URL = "https://identitytoolkit.googleapis.com/"
    const val FIREBASE_TOKEN_BASE_URL = "https://securetoken.googleapis.com/"
    const val FIREBASE_WEB_API_KEY = "AIzaSyBmogqpXzirZC-aUHZrFMwutSJ5fmbyDXk"
    const val GOOGLE_WEB_CLIENT_ID = "799252588942-itnqbin8ck1rpda2hhas07k4953rv0k1.apps.googleusercontent.com"

    fun deliveryFeeForDivision(division: String): Long {
        return if (division.trim().equals("Dhaka", ignoreCase = true)) 60L else 120L
    }

    val CATEGORIES = listOf(
        "All",
        "Electronics & Gadgets",
        "Mobile & Accessories",
        "Computer & Accessories",
        "Home & Kitchen",
        "Fashion & Apparel",
        "Health & Beauty",
        "Sports & Outdoors",
        "Toys & Baby Care",
        "Books & Stationery",
        "Groceries & Daily Needs"
    )

    val BD_DIVISIONS = listOf(
        "Dhaka",
        "Chattogram",
        "Sylhet",
        "Rajshahi",
        "Khulna",
        "Barishal",
        "Rangpur",
        "Mymensingh"
    )

    val BD_DISTRICTS_BY_DIVISION: Map<String, List<String>> = mapOf(
        "Dhaka" to listOf(
            "Dhaka", "Faridpur", "Gazipur", "Gopalganj", "Kishoreganj", "Madaripur",
            "Manikganj", "Munshiganj", "Narayanganj", "Narsingdi", "Rajbari", "Shariatpur", "Tangail"
        ),
        "Chattogram" to listOf(
            "Bandarban", "Brahmanbaria", "Chandpur", "Chattogram", "Cox's Bazar", "Cumilla",
            "Feni", "Khagrachhari", "Lakshmipur", "Noakhali", "Rangamati"
        ),
        "Sylhet" to listOf(
            "Habiganj", "Moulvibazar", "Sunamganj", "Sylhet"
        ),
        "Rajshahi" to listOf(
            "Bogura", "Chapainawabganj", "Joypurhat", "Naogaon", "Natore", "Pabna", "Rajshahi", "Sirajganj"
        ),
        "Khulna" to listOf(
            "Bagerhat", "Chuadanga", "Jashore", "Jhenaidah", "Khulna",
            "Kushtia", "Magura", "Meherpur", "Narail", "Satkhira"
        ),
        "Barishal" to listOf(
            "Barguna", "Barishal", "Bhola", "Jhalokati", "Patuakhali", "Pirojpur"
        ),
        "Rangpur" to listOf(
            "Dinajpur", "Gaibandha", "Kurigram", "Lalmonirhat", "Nilphamari", "Panchagarh", "Rangpur", "Thakurgaon"
        ),
        "Mymensingh" to listOf(
            "Jamalpur", "Mymensingh", "Netrokona", "Sherpur"
        )
    )

    fun districtsForDivision(division: String): List<String> {
        val matchKey = BD_DISTRICTS_BY_DIVISION.keys.firstOrNull {
            it.equals(division.trim(), ignoreCase = true)
        }
        return if (matchKey != null) {
            BD_DISTRICTS_BY_DIVISION[matchKey].orEmpty()
        } else {
            BD_DISTRICTS_BY_DIVISION.values.flatten()
        }
    }

    /**
     * Order cancellation is strictly locked once an order reaches Confirmed or any later stage.
     */
    fun canCancelOrder(status: String): Boolean {
        return status.equals("Pending", ignoreCase = true) ||
            status.equals("Placed", ignoreCase = true)
    }

    fun isValidBdPhone(phone: String): Boolean {
        val digits = phone.filter { it.isDigit() }
        val normalized = when {
            digits.startsWith("8801") && digits.length == 13 -> digits.substring(2)
            digits.startsWith("01") && digits.length == 11 -> digits
            else -> return false
        }
        return Regex("^01[3-9][0-9]{8}$").matches(normalized)
    }

    fun normalizeBdPhone(phone: String): String {
        val digits = phone.filter { it.isDigit() }
        return if (digits.startsWith("8801") && digits.length == 13) digits.substring(2) else digits
    }

    fun isValidTrxId(method: PaymentMethod, trxId: String): Boolean {
        return method == PaymentMethod.COD || trxId.trim().length >= 6
    }
}
