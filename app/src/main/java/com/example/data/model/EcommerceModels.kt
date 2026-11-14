package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

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
    val createdAt: Long = System.currentTimeMillis()
) {
    val variants: List<String>
        get() = variantsCsv.split(",").map { it.trim() }.filter { it.isNotEmpty() }
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
    val isDefault: Boolean = false
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

enum class PaymentMethod(
    val displayName: String,
    val subtitle: String,
    val requiresMobileTrx: Boolean,
    val isCardGateway: Boolean
) {
    BKASH("bKash", "Send Money / Payment + Auto TrxID Verify", true, false),
    NAGAD("Nagad", "Send Money / Payment + Auto TrxID Verify", true, false),
    ROCKET("Rocket (DBBL)", "Mobile Banking + Auto TrxID Verify", true, false),
    SSLCOMMERZ("Debit / Credit Card", "Visa, Mastercard & NexusPay", false, true),
    COD("Cash on Delivery", "Pay cash at doorstep (All 64 Districts)", false, false)
}

enum class DeliveryZone(val label: String, val feeBdt: Long, val etaText: String) {
    DHAKA_METRO("Dhaka Metro", 60L, "Same-day / 24h express"),
    DHAKA_SUBURBS("Dhaka Suburbs (Gazipur / Narayanganj / Savar)", 90L, "1–2 business days"),
    OUTSIDE_DHAKA("All Bangladesh (64 Districts)", 120L, "2–4 business days")
}

object StoreConstants {
    const val GUEST_EMAIL = "guest@sprzonebd.local"
    const val FREE_DELIVERY_THRESHOLD_BDT = 3000L
    const val RTDB_BASE_URL = "https://e-commarcebd-default-rtdb.europe-west1.firebasedatabase.app/"

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
        val clean = trxId.trim().uppercase()
        return when (method) {
            PaymentMethod.BKASH -> Regex("^[A-Z0-9]{10}$").matches(clean)
            PaymentMethod.NAGAD -> Regex("^[A-Z0-9]{8,12}$").matches(clean)
            PaymentMethod.ROCKET -> Regex("^[A-Z0-9]{8,14}$").matches(clean)
            else -> true
        }
    }
}
