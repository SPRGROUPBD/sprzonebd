package com.example.data.repository

import com.example.data.local.AddressDao
import com.example.data.local.CartDao
import com.example.data.local.CatalogSeeder
import com.example.data.local.OrderDao
import com.example.data.local.ProductDao
import com.example.data.local.ReviewDao
import com.example.data.local.SessionPreferences
import com.example.data.local.SprZoneDatabase
import com.example.data.local.UserDao
import com.example.data.local.WishlistDao
import com.example.data.model.AddressEntity
import com.example.data.model.CartItemEntity
import com.example.data.model.DeliveryZone
import com.example.data.model.MerchantPaymentSettings
import com.example.data.model.OrderEntity
import com.example.data.model.PaymentMethod
import com.example.data.model.ProductEntity
import com.example.data.model.ReviewEntity
import com.example.data.model.StoreConstants
import com.example.data.model.UserAccountEntity
import com.example.data.model.WishlistEntity
import com.example.data.remote.RemoteCouponDto
import com.example.data.remote.RemoteCustomerDto
import com.example.data.remote.RemoteOrderDto
import com.example.data.remote.RemoteOrderItemDto
import com.example.data.remote.SprZoneNetworkClient
import com.example.data.remote.SprZoneNetworkClient.toDomain
import com.example.data.remote.SprZoneRtdbService
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

data class PromoResult(
    val code: String,
    val discountBdt: Long,
    val freeShipping: Boolean,
    val message: String
)

class SprZoneRepository(
    private val db: SprZoneDatabase,
    private val prefs: SessionPreferences,
    private val rtdb: SprZoneRtdbService = SprZoneNetworkClient.rtdbService,
    private val filesDir: File? = null
) {
    private val productDao: ProductDao = db.productDao()
    private val cartDao: CartDao = db.cartDao()
    private val wishlistDao: WishlistDao = db.wishlistDao()
    private val userDao: UserDao = db.userDao()
    private val addressDao: AddressDao = db.addressDao()
    private val orderDao: OrderDao = db.orderDao()
    private val reviewDao: ReviewDao = db.reviewDao()

    private val _paymentSettings = MutableStateFlow(MerchantPaymentSettings())
    val paymentSettings: StateFlow<MerchantPaymentSettings> = _paymentSettings.asStateFlow()

    private val _coupons = MutableStateFlow<Map<String, RemoteCouponDto>>(emptyMap())
    val coupons: StateFlow<Map<String, RemoteCouponDto>> = _coupons.asStateFlow()

    val loggedInEmailFlow: Flow<String?> = prefs.loggedInEmail
    val deliveryZoneFlow: Flow<DeliveryZone> = prefs.deliveryZone
    val isDarkModeFlow: Flow<Boolean> = prefs.isDarkMode

    suspend fun ensureSeededAndSync() {
        syncRemoteCatalogAndSettings()
    }

    suspend fun syncRemoteCatalogAndSettings() = withContext(Dispatchers.IO) {
        try {
            val settingsResp = rtdb.getPaymentSettings()
            if (settingsResp.isSuccessful) {
                settingsResp.body()?.let { dto ->
                    _paymentSettings.value = dto.toDomain()
                }
            }
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            // Keep previously synced settings when offline
        }

        try {
            val couponsResp = rtdb.getCoupons()
            if (couponsResp.isSuccessful) {
                _coupons.value = couponsResp.body().orEmpty()
            }
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
        }

        try {
            val productsResp = rtdb.getProducts()
            if (productsResp.isSuccessful) {
                val remoteMap = productsResp.body()
                if (remoteMap.isNullOrEmpty()) {
                    productDao.clearAllProducts()
                } else {
                    val mapped = remoteMap.entries.mapNotNull { (id, dto) ->
                        val safeId = id.trim().take(120)
                        if (safeId.isBlank()) return@mapNotNull null
                        val name = dto.name?.trim()?.takeIf { it.isNotBlank() }?.take(300) ?: return@mapNotNull null
                        val price = (dto.price ?: 0.0).toLong().coerceAtLeast(1L)
                        val oldPrice = (dto.oldPrice ?: 0.0).toLong()
                        val discount = if (oldPrice > price) {
                            (((oldPrice - price) * 100.0) / oldPrice).roundToInt().coerceIn(0, 95)
                        } else 0
                        val cat = dto.category?.trim()?.takeIf { it.isNotBlank() }?.take(120) ?: "Electronics & Gadgets"
                        val imgKey = when {
                            cat.contains("Fashion", ignoreCase = true) ||
                                cat.contains("Sport", ignoreCase = true) ||
                                cat.contains("Book", ignoreCase = true) ||
                                cat.contains("Beauty", ignoreCase = true) -> "fashion"
                            cat.contains("Grocer", ignoreCase = true) ||
                                cat.contains("Home", ignoreCase = true) ||
                                cat.contains("Baby", ignoreCase = true) -> "groceries"
                            else -> "electronics"
                        }
                        val safeImageUrl = persistOrSanitizeImageUrl(safeId, dto.image)
                        ProductEntity(
                            id = safeId,
                            name = name,
                            category = cat,
                            brand = dto.brand?.trim()?.takeIf { it.isNotBlank() }?.take(120) ?: "SPR Official",
                            sellerName = dto.sellerName?.trim()?.takeIf { it.isNotBlank() }?.take(120) ?: "SPR Official Store",
                            priceBdt = price,
                            originalPriceBdt = if (oldPrice > price) oldPrice else price,
                            discountPercent = discount,
                            rating = (dto.rating ?: 4.7).coerceIn(1.0, 5.0),
                            reviewCount = dto.reviewCount ?: 25,
                            stock = (dto.stock ?: 10).coerceAtLeast(0),
                            isFlashSale = dto.isFlashDeal ?: (discount >= 15),
                            isFeatured = dto.isFeatured ?: true,
                            imageKey = imgKey,
                            imageUrl = safeImageUrl,
                            description = dto.description?.trim()?.takeIf { it.isNotBlank() }?.take(4000)
                                ?: "Official SPR Zone BD product.",
                            specsSummary = dto.specs?.trim()?.takeIf { it.isNotBlank() }?.take(500)
                                ?: "100% Original • Nationwide BD Delivery",
                            variantsCsv = dto.variants?.trim()?.takeIf { it.isNotBlank() }?.take(500)
                                ?: "Standard",
                            createdAt = dto.updatedAt ?: System.currentTimeMillis()
                        )
                    }
                    productDao.clearAllProducts()
                    if (mapped.isNotEmpty()) {
                        productDao.insertAll(mapped)
                    }
                }
            }
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            // Retain Room offline catalog
        }
    }

    suspend fun syncUserOrdersFromRemote(email: String) = withContext(Dispatchers.IO) {
        try {
            val resp = rtdb.getAllOrders()
            if (resp.isSuccessful) {
                val map = resp.body() ?: return@withContext
                val normalizedEmail = email.trim().lowercase()
                map.forEach { (key, dto) ->
                    if (dto.customerEmail?.trim()?.lowercase() == normalizedEmail) {
                        val orderId = (dto.orderId ?: key).trim().take(120)
                        if (orderId.isBlank()) return@forEach
                        val existing = orderDao.getOrderById(orderId)
                        val newStatus = (dto.status ?: existing?.orderStatus ?: "Pending").take(60)
                        val newPayStatus = (dto.paymentStatus ?: existing?.paymentStatus ?: "Pending").take(80)
                        if (existing != null) {
                            if (existing.orderStatus != newStatus || existing.paymentStatus != newPayStatus) {
                                orderDao.insertOrder(
                                    existing.copy(
                                        orderStatus = newStatus,
                                        paymentStatus = newPayStatus,
                                        updatedAt = dto.updatedAt ?: System.currentTimeMillis()
                                    )
                                )
                            }
                        } else {
                            val summaryText = (dto.itemsSummary ?: dto.items.orEmpty().joinToString("||") {
                                "${it.name.orEmpty().take(120)} x${it.qty ?: 1} @৳${it.price ?: 0}"
                            }).take(4000)
                            orderDao.insertOrder(
                                OrderEntity(
                                    orderId = orderId,
                                    userEmail = normalizedEmail.take(160),
                                    recipientName = (dto.customer?.name ?: "Customer").take(120),
                                    phone = (dto.customer?.phone ?: "").take(40),
                                    fullAddress = (dto.customer?.address ?: "").take(1000),
                                    division = (dto.customer?.division ?: "Dhaka").take(60),
                                    itemsSummary = summaryText,
                                    itemCount = dto.items?.sumOf { it.qty ?: 1 } ?: 1,
                                    subtotalBdt = dto.subtotal ?: dto.total ?: 0L,
                                    discountBdt = dto.discount ?: 0L,
                                    deliveryFeeBdt = dto.deliveryFee ?: 0L,
                                    totalBdt = dto.total ?: 0L,
                                    promoCode = dto.promoCode.orEmpty().take(40),
                                    paymentMethod = (dto.paymentMethod ?: "Cash on Delivery").take(60),
                                    senderNumber = dto.senderNumber.orEmpty().take(40),
                                    trxId = dto.trxId.orEmpty().take(120),
                                    paymentAccountMasked = dto.senderNumber?.takeLast(4)?.let { "•••• $it" } ?: "Verified",
                                    transactionId = dto.trxId?.takeIf { it.isNotBlank() }?.take(120) ?: orderId,
                                    paymentStatus = newPayStatus,
                                    orderStatus = newStatus,
                                    createdAt = dto.createdAt ?: System.currentTimeMillis(),
                                    updatedAt = dto.updatedAt ?: System.currentTimeMillis()
                                )
                            )
                        }
                    }
                }
            }
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            // Keep local Room orders on offline error
        }
    }

    suspend fun trackOrderById(orderId: String): Result<OrderEntity> = withContext(Dispatchers.IO) {
        val cleanId = orderId.trim()
        if (cleanId.isBlank()) return@withContext Result.failure(IllegalArgumentException("Enter an Order ID"))
        val local = orderDao.getOrderById(cleanId)
        try {
            val remote = rtdb.getOrder(cleanId)
            if (remote.isSuccessful && remote.body() != null) {
                val dto = remote.body()!!
                val summaryText = (dto.itemsSummary ?: local?.itemsSummary ?: dto.items.orEmpty().joinToString("||") {
                    "${it.name.orEmpty().take(120)} x${it.qty ?: 1} @৳${it.price ?: 0}"
                }).take(4000)
                val entity = OrderEntity(
                    orderId = (dto.orderId ?: cleanId).take(120),
                    userEmail = dto.customerEmail.orEmpty().take(160),
                    recipientName = (dto.customer?.name ?: local?.recipientName ?: "Customer").take(120),
                    phone = (dto.customer?.phone ?: local?.phone ?: "").take(40),
                    fullAddress = (dto.customer?.address ?: local?.fullAddress ?: "").take(1000),
                    division = (dto.customer?.division ?: local?.division ?: "Dhaka").take(60),
                    itemsSummary = summaryText,
                    itemCount = dto.items?.sumOf { it.qty ?: 1 } ?: local?.itemCount ?: 1,
                    subtotalBdt = dto.subtotal ?: dto.total ?: local?.subtotalBdt ?: 0L,
                    discountBdt = dto.discount ?: local?.discountBdt ?: 0L,
                    deliveryFeeBdt = dto.deliveryFee ?: local?.deliveryFeeBdt ?: 0L,
                    totalBdt = dto.total ?: local?.totalBdt ?: 0L,
                    promoCode = dto.promoCode.orEmpty().take(40),
                    paymentMethod = (dto.paymentMethod ?: local?.paymentMethod ?: "bKash").take(60),
                    senderNumber = (dto.senderNumber ?: local?.senderNumber ?: "").take(40),
                    trxId = (dto.trxId ?: local?.trxId ?: "").take(120),
                    paymentAccountMasked = local?.paymentAccountMasked ?: "Verified",
                    transactionId = dto.trxId?.takeIf { it.isNotBlank() }?.take(120) ?: local?.transactionId ?: cleanId.take(120),
                    paymentStatus = (dto.paymentStatus ?: local?.paymentStatus ?: "Pending").take(80),
                    orderStatus = (dto.status ?: local?.orderStatus ?: "Pending").take(60),
                    createdAt = dto.createdAt ?: local?.createdAt ?: System.currentTimeMillis(),
                    updatedAt = dto.updatedAt ?: System.currentTimeMillis()
                )
                orderDao.insertOrder(entity)
                return@withContext Result.success(entity)
            }
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            // Fall back to local cache
        }
        return@withContext if (local != null) {
            Result.success(local)
        } else {
            Result.failure(IllegalArgumentException("Order $cleanId was not found."))
        }
    }

    fun observeAllProducts(): Flow<List<ProductEntity>> = productDao.observeAllProducts()
    fun observeProductById(id: String): Flow<ProductEntity?> = productDao.observeProductById(id)
    fun observeCart(email: String): Flow<List<CartItemEntity>> = cartDao.observeCart(email)
    fun observeWishlistIds(email: String): Flow<List<String>> = wishlistDao.observeWishlistIds(email)
    fun observeUser(email: String): Flow<UserAccountEntity?> = userDao.observeUser(email)
    fun observeAddresses(email: String): Flow<List<AddressEntity>> = addressDao.observeAddresses(email)
    fun observeUserOrders(email: String): Flow<List<OrderEntity>> = orderDao.observeUserOrders(email)
    fun observeReviews(productId: String): Flow<List<ReviewEntity>> = reviewDao.observeReviewsForProduct(productId)

    suspend fun setDeliveryZone(zone: DeliveryZone) = prefs.setDeliveryZone(zone)
    suspend fun setDarkMode(enabled: Boolean) = prefs.setDarkMode(enabled)

    // --- Customer Authentication (No Admin role on Mobile) ---

    suspend fun registerUser(
        fullName: String,
        email: String,
        phone: String,
        password: String
    ): Result<UserAccountEntity> {
        val normalizedEmail = email.trim().lowercase()
        if (fullName.isBlank()) return Result.failure(IllegalArgumentException("Full name is required"))
        if (!normalizedEmail.contains("@") || !normalizedEmail.contains(".")) {
            return Result.failure(IllegalArgumentException("Enter a valid email address"))
        }
        if (password.length < 6) {
            return Result.failure(IllegalArgumentException("Password must be at least 6 characters"))
        }
        val existing = userDao.getUserByEmail(normalizedEmail)
        if (existing != null) {
            return Result.failure(IllegalStateException("An account already exists with this email. Please sign in."))
        }
        val salt = generateSalt()
        val hash = hashPassword(password, salt)
        val user = UserAccountEntity(
            email = normalizedEmail,
            fullName = fullName.trim(),
            phone = phone.trim(),
            passwordHash = hash,
            salt = salt
        )
        userDao.upsertUser(user)
        migrateGuestCart(normalizedEmail)
        prefs.setLoggedInEmail(normalizedEmail)
        return Result.success(user)
    }

    suspend fun loginUser(email: String, password: String): Result<UserAccountEntity> {
        val normalizedEmail = email.trim().lowercase()
        val user = userDao.getUserByEmail(normalizedEmail)
            ?: return Result.failure(IllegalArgumentException("No account found for $normalizedEmail. Please register first."))
        val expectedHash = hashPassword(password, user.salt)
        if (expectedHash != user.passwordHash) {
            return Result.failure(IllegalArgumentException("Incorrect password. Please try again."))
        }
        migrateGuestCart(normalizedEmail)
        prefs.setLoggedInEmail(normalizedEmail)
        syncUserOrdersFromRemote(normalizedEmail)
        return Result.success(user)
    }

    suspend fun updateProfile(
        email: String,
        fullName: String,
        phone: String,
        bio: String
    ): Result<UserAccountEntity> {
        val normalizedEmail = email.trim().lowercase()
        val existing = userDao.getUserByEmail(normalizedEmail)
            ?: return Result.failure(IllegalStateException("Account not found."))
        val trimmedName = fullName.trim()
        if (trimmedName.isBlank()) {
            return Result.failure(IllegalArgumentException("Full name is required."))
        }
        val cleanPhone = phone.trim()
        if (cleanPhone.isNotBlank() && !StoreConstants.isValidBdPhone(cleanPhone)) {
            return Result.failure(IllegalArgumentException("Enter a valid 11-digit BD mobile number (01XXXXXXXXX)."))
        }
        val normalizedPhone = if (cleanPhone.isNotBlank()) StoreConstants.normalizeBdPhone(cleanPhone) else ""
        val updated = existing.copy(
            fullName = trimmedName.take(120),
            phone = normalizedPhone,
            bio = bio.trim().take(240)
        )
        userDao.upsertUser(updated)
        return Result.success(updated)
    }

    suspend fun deleteProfile(email: String): Result<Unit> {
        val normalizedEmail = email.trim().lowercase()
        addressDao.deleteAllForUser(normalizedEmail)
        cartDao.clearCart(normalizedEmail)
        wishlistDao.clearWishlist(normalizedEmail)
        userDao.deleteUserByEmail(normalizedEmail)
        prefs.setLoggedInEmail(null)
        return Result.success(Unit)
    }

    suspend fun signOut() {
        prefs.setLoggedInEmail(null)
    }

    private suspend fun migrateGuestCart(targetEmail: String) {
        val guestItems = cartDao.getCartItems(StoreConstants.GUEST_EMAIL)
        for (item in guestItems) {
            val existing = cartDao.findCartItem(targetEmail, item.productId, item.selectedVariant)
            if (existing != null) {
                cartDao.updateCartItem(existing.copy(quantity = existing.quantity + item.quantity))
            } else {
                cartDao.insertCartItem(item.copy(id = 0, userEmail = targetEmail))
            }
        }
        if (guestItems.isNotEmpty()) {
            cartDao.clearCart(StoreConstants.GUEST_EMAIL)
        }
    }

    // --- Cart & Wishlist ---

    suspend fun addToCart(
        userEmail: String,
        product: ProductEntity,
        variant: String,
        quantity: Int = 1
    ): Result<Unit> {
        if (product.stock <= 0) {
            return Result.failure(IllegalStateException("Sorry, ${product.name} is currently out of stock."))
        }
        val chosenVariant = variant.ifBlank { product.variants.firstOrNull() ?: "Standard" }
        val existing = cartDao.findCartItem(userEmail, product.id, chosenVariant)
        if (existing != null) {
            val nextQty = (existing.quantity + quantity).coerceAtMost(product.stock)
            cartDao.updateCartItem(existing.copy(quantity = nextQty))
        } else {
            cartDao.insertCartItem(
                CartItemEntity(
                    userEmail = userEmail,
                    productId = product.id,
                    productName = product.name,
                    unitPriceBdt = product.priceBdt,
                    originalPriceBdt = product.originalPriceBdt,
                    imageKey = product.imageKey,
                    imageUrl = product.imageUrl,
                    selectedVariant = chosenVariant,
                    quantity = quantity.coerceAtMost(product.stock)
                )
            )
        }
        return Result.success(Unit)
    }

    suspend fun updateCartQuantity(item: CartItemEntity, newQty: Int) {
        if (newQty <= 0) {
            cartDao.deleteCartItemById(item.id)
        } else {
            val product = productDao.getProductById(item.productId)
            val maxQty = product?.stock ?: newQty
            cartDao.updateCartItem(item.copy(quantity = newQty.coerceAtMost(maxQty.coerceAtLeast(1))))
        }
    }

    suspend fun removeCartItem(id: Long) = cartDao.deleteCartItemById(id)

    suspend fun toggleWishlist(userEmail: String, productId: String): Boolean {
        val existing = wishlistDao.findWishlistItem(userEmail, productId)
        return if (existing != null) {
            wishlistDao.delete(userEmail, productId)
            false
        } else {
            wishlistDao.insert(WishlistEntity(userEmail = userEmail, productId = productId))
            true
        }
    }

    // --- Addresses ---

    suspend fun saveAddress(
        userEmail: String,
        label: String,
        recipientName: String,
        phone: String,
        division: String,
        districtThana: String,
        streetAddress: String,
        makeDefault: Boolean
    ): Result<Long> {
        if (recipientName.isBlank() || phone.isBlank() || districtThana.isBlank() || streetAddress.isBlank()) {
            return Result.failure(IllegalArgumentException("Please fill in all shipping address fields."))
        }
        val shouldDefault = makeDefault || addressDao.countAddresses(userEmail) == 0
        if (shouldDefault) {
            addressDao.clearDefaultAddresses(userEmail)
        }
        val id = addressDao.insertAddress(
            AddressEntity(
                userEmail = userEmail,
                label = label.ifBlank { "Home" },
                recipientName = recipientName.trim(),
                phone = phone.trim(),
                division = division,
                districtThana = districtThana.trim(),
                streetAddress = streetAddress.trim(),
                isDefault = shouldDefault
            )
        )
        return Result.success(id)
    }

    suspend fun updateAddress(
        userEmail: String,
        addressId: Long,
        label: String,
        recipientName: String,
        phone: String,
        division: String,
        districtThana: String,
        streetAddress: String,
        makeDefault: Boolean
    ): Result<Unit> {
        if (recipientName.isBlank() || phone.isBlank() || districtThana.isBlank() || streetAddress.isBlank()) {
            return Result.failure(IllegalArgumentException("Please fill in all shipping address fields."))
        }
        val existing = addressDao.getAddressById(addressId, userEmail)
            ?: return Result.failure(IllegalArgumentException("Address not found."))
        if (makeDefault) {
            addressDao.clearDefaultAddresses(userEmail)
        }
        addressDao.updateAddress(
            existing.copy(
                label = label.ifBlank { "Home" }.trim(),
                recipientName = recipientName.trim(),
                phone = phone.trim(),
                division = division,
                districtThana = districtThana.trim(),
                streetAddress = streetAddress.trim(),
                isDefault = makeDefault || existing.isDefault
            )
        )
        return Result.success(Unit)
    }

    suspend fun setDefaultAddress(userEmail: String, addressId: Long) {
        addressDao.clearDefaultAddresses(userEmail)
        addressDao.setDefaultAddress(addressId, userEmail)
    }

    suspend fun deleteAddress(id: Long) = addressDao.deleteAddress(id)

    // --- Promo & Checkout with Auto TrxID Verification ---

    fun evaluatePromo(codeRaw: String, subtotalBdt: Long): PromoResult? {
        val cleanCode = codeRaw.trim().uppercase()
        if (cleanCode.isBlank()) return null
        val coupon = _coupons.value[cleanCode] ?: _coupons.value.values.firstOrNull {
            it.code.equals(cleanCode, ignoreCase = true)
        } ?: return null

        val minOrder = (coupon.minOrder ?: 0.0).toLong()
        if (subtotalBdt < minOrder) return null

        return when (coupon.type?.lowercase()) {
            "percent" -> {
                val pct = (coupon.value ?: 0.0).coerceIn(0.0, 95.0)
                PromoResult(
                    code = cleanCode,
                    discountBdt = (subtotalBdt * (pct / 100.0)).roundToInt().toLong(),
                    freeShipping = false,
                    message = "${pct.roundToInt()}% discount applied"
                )
            }
            "flat" -> {
                val flat = (coupon.value ?: 0.0).toLong().coerceAtLeast(0L)
                PromoResult(
                    code = cleanCode,
                    discountBdt = flat.coerceAtMost(subtotalBdt),
                    freeShipping = false,
                    message = "৳$flat instant discount applied"
                )
            }
            "freeship" -> PromoResult(
                code = cleanCode,
                discountBdt = 0L,
                freeShipping = true,
                message = "Free nationwide Bangladesh shipping applied"
            )
            else -> null
        }
    }

    suspend fun placeOrder(
        userEmail: String,
        address: AddressEntity,
        cartItems: List<CartItemEntity>,
        promoCode: String,
        paymentMethod: PaymentMethod,
        senderNumberInput: String,
        trxIdInput: String
    ): Result<OrderEntity> = withContext(Dispatchers.IO) {
        if (cartItems.isEmpty()) {
            return@withContext Result.failure(IllegalStateException("Your cart is empty"))
        }

        val subtotal = cartItems.sumOf { it.unitPriceBdt * it.quantity }
        val promo = evaluatePromo(promoCode, subtotal)
        val discount = promo?.discountBdt ?: 0L
        val rawDeliveryFee = if (subtotal >= StoreConstants.FREE_DELIVERY_THRESHOLD_BDT || promo?.freeShipping == true) {
            0L
        } else {
            StoreConstants.deliveryFeeForDivision(address.division)
        }
        val total = (subtotal - discount + rawDeliveryFee).coerceAtLeast(0L)

        var normalizedSender = ""
        var normalizedTrx = ""
        var paymentStatus: String
        var initialOrderStatus: String

        if (paymentMethod.requiresMobileTrx) {
            if (!StoreConstants.isValidBdPhone(senderNumberInput)) {
                return@withContext Result.failure(
                    IllegalArgumentException("Enter a valid 11-digit BD sender mobile number (01XXXXXXXXX).")
                )
            }
            normalizedSender = StoreConstants.normalizeBdPhone(senderNumberInput)
            normalizedTrx = trxIdInput.trim().uppercase()
            if (!StoreConstants.isValidTrxId(paymentMethod, normalizedTrx)) {
                return@withContext Result.failure(
                    IllegalArgumentException("Invalid ${paymentMethod.displayName} Transaction ID format.")
                )
            }

            // Auto-verify against configured Merchant SMS API or RTDB /sms_inbox/{trxId}
            val verified = attemptAutoVerifyTrx(
                provider = paymentMethod.displayName,
                senderNumber = normalizedSender,
                trxId = normalizedTrx,
                amountBdt = total
            )
            if (verified) {
                paymentStatus = "Verified (Auto SMS)"
                initialOrderStatus = "Confirmed"
            } else {
                paymentStatus = "Pending TrxID Check"
                initialOrderStatus = "Pending"
            }
        } else if (paymentMethod == PaymentMethod.SSLCOMMERZ) {
            normalizedSender = senderNumberInput.filter { it.isDigit() }
            normalizedTrx = "SSL${System.currentTimeMillis().toString().takeLast(6)}${(10..99).random()}"
            paymentStatus = "Verified (Card)"
            initialOrderStatus = "Confirmed"
        } else {
            normalizedTrx = "COD${System.currentTimeMillis().toString().takeLast(6)}"
            paymentStatus = "Pay on Delivery"
            initialOrderStatus = "Pending"
        }

        val datePart = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
        val suffix = (1000..9999).random()
        val orderId = "SPR-BD-$datePart-$suffix"

        val maskedAccount = when {
            paymentMethod == PaymentMethod.COD -> "Cash on Delivery"
            normalizedSender.length >= 4 -> "•••• ${normalizedSender.takeLast(4)}"
            else -> "Verified"
        }

        val summary = cartItems.joinToString("||") { item ->
            "${item.productName} x${item.quantity} (${item.selectedVariant}) @৳${item.unitPriceBdt}"
        }

        val order = OrderEntity(
            orderId = orderId,
            userEmail = userEmail,
            recipientName = address.recipientName,
            phone = address.phone,
            fullAddress = address.formattedAddress,
            division = address.division,
            itemsSummary = summary,
            itemCount = cartItems.sumOf { it.quantity },
            subtotalBdt = subtotal,
            discountBdt = discount,
            deliveryFeeBdt = rawDeliveryFee,
            totalBdt = total,
            promoCode = promo?.code ?: "",
            paymentMethod = paymentMethod.displayName,
            senderNumber = normalizedSender,
            trxId = normalizedTrx,
            paymentAccountMasked = maskedAccount,
            transactionId = normalizedTrx,
            paymentStatus = paymentStatus,
            orderStatus = initialOrderStatus
        )

        orderDao.insertOrder(order)
        for (item in cartItems) {
            productDao.decrementStock(item.productId, item.quantity)
        }
        cartDao.clearCart(userEmail)

        // Push order to shared e-commarcebd RTDB so Website Admin sees it immediately
        try {
            val remoteDto = RemoteOrderDto(
                orderId = orderId,
                uid = userEmail.replace(".", "_"),
                customerEmail = userEmail,
                customer = RemoteCustomerDto(
                    name = address.recipientName,
                    phone = address.phone,
                    address = address.formattedAddress,
                    division = address.division
                ),
                items = cartItems.map {
                    RemoteOrderItemDto(
                        id = it.productId,
                        name = it.productName,
                        price = it.unitPriceBdt,
                        qty = it.quantity,
                        variant = it.selectedVariant
                    )
                },
                itemsSummary = summary,
                subtotal = subtotal,
                discount = discount,
                deliveryFee = rawDeliveryFee,
                total = total,
                promoCode = promo?.code ?: "",
                currency = "BDT",
                paymentMethod = paymentMethod.displayName,
                senderNumber = normalizedSender,
                trxId = normalizedTrx,
                paymentStatus = paymentStatus,
                status = initialOrderStatus,
                createdAt = order.createdAt,
                updatedAt = order.updatedAt
            )
            rtdb.putOrder(orderId, remoteDto)
            if (paymentStatus == "Verified (Auto SMS)" && normalizedTrx.isNotBlank()) {
                rtdb.markSmsRecordMatched(normalizedTrx, mapOf("matchedOrderId" to orderId))
            }
        } catch (_: Exception) {
            // Order remains safely stored locally in Room if offline
        }

        Result.success(order)
    }

    private suspend fun attemptAutoVerifyTrx(
        provider: String,
        senderNumber: String,
        trxId: String,
        amountBdt: Long
    ): Boolean {
        val settings = _paymentSettings.value

        // 1. Check merchant's configured SMS/Message API URL if present
        if (settings.smsApiUrl.isNotBlank()) {
            try {
                val resp = rtdb.verifyViaExternalSmsApi(
                    url = settings.smsApiUrl,
                    sender = senderNumber,
                    trxId = trxId,
                    amount = amountBdt,
                    provider = provider,
                    apiKey = settings.smsApiKey
                )
                if (resp.isSuccessful) {
                    val body = resp.body()
                    if (body?.verified == true || body?.status.equals("verified", ignoreCase = true)) {
                        return true
                    }
                }
            } catch (_: Exception) {
                // Fall through to RTDB sms_inbox check
            }
        }

        // 2. Check RTDB /sms_inbox/{trxId} (populated by merchant's SMS forwarder or Website Admin)
        return try {
            val smsResp = rtdb.getSmsInboxRecord(trxId)
            val record = smsResp.body()
            if (smsResp.isSuccessful && record != null) {
                val recSender = StoreConstants.normalizeBdPhone(record.senderNumber.orEmpty())
                val recAmount = (record.amount ?: 0.0).toLong()
                val unmatched = record.matchedOrderId.isNullOrBlank()
                unmatched && recSender == senderNumber && recAmount >= amountBdt
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Strictly enforces: once an order is Confirmed (or later), customer cancellation is blocked.
     */
    suspend fun cancelOrder(orderId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val order = orderDao.getOrderById(orderId)
            ?: return@withContext Result.failure(IllegalArgumentException("Order not found."))
        if (!StoreConstants.canCancelOrder(order.orderStatus)) {
            return@withContext Result.failure(
                IllegalStateException("Order is already ${order.orderStatus}. Confirmed orders cannot be cancelled.")
            )
        }
        val now = System.currentTimeMillis()
        orderDao.updateOrderStatus(orderId, "Cancelled", now)
        try {
            rtdb.patchOrderStatus(orderId, mapOf("status" to "Cancelled", "updatedAt" to now))
        } catch (_: Exception) {
            // Local status updated
        }
        Result.success(Unit)
    }

    // --- Reviews ---

    suspend fun submitReview(
        productId: String,
        userName: String,
        userEmail: String,
        rating: Int,
        comment: String
    ): Result<Unit> {
        if (comment.isBlank()) return Result.failure(IllegalArgumentException("Please write a short review comment."))
        val safeRating = rating.coerceIn(1, 5)
        reviewDao.insertReview(
            ReviewEntity(
                productId = productId,
                userName = userName,
                userEmail = userEmail,
                rating = safeRating,
                comment = comment.trim(),
                verifiedPurchase = true
            )
        )
        val allReviews = reviewDao.getReviewsForProduct(productId)
        if (allReviews.isNotEmpty()) {
            val avg = (allReviews.sumOf { it.rating }.toDouble() / allReviews.size)
            val rounded = (avg * 10.0).roundToInt() / 10.0
            productDao.updateRatingSummary(productId, rounded, allReviews.size)
        }
        return Result.success(Unit)
    }

    // --- Hashing Helpers ---

    private fun generateSalt(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun hashPassword(password: String, salt: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val input = "$salt:$password".toByteArray(Charsets.UTF_8)
        return digest.digest(input).joinToString("") { "%02x".format(it) }
    }

    /**
     * Prevents SQLiteBlobTooBigException (Android CursorWindow 2MB limit) when RTDB products
     * contain multi-megabyte inline data:image/...;base64 payloads. Decodes and downscales inline
     * base64 images to a local file for Coil, returning a short file:// URI (or "" on failure).
     */
    internal fun persistOrSanitizeImageUrl(productId: String, rawImage: String?): String {
        val trimmed = rawImage?.trim().orEmpty()
        if (trimmed.isEmpty()) return ""

        if (trimmed.startsWith("data:image", ignoreCase = true)) {
            val base64Idx = trimmed.indexOf("base64,", ignoreCase = true)
            val dirRoot = filesDir
            if (base64Idx < 0 || dirRoot == null) return ""
            return try {
                val base64Payload = trimmed.substring(base64Idx + 7)
                val bytes = Base64.decode(base64Payload, Base64.DEFAULT)
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return ""

                var sampleSize = 1
                val maxDim = maxOf(bounds.outWidth, bounds.outHeight)
                while (maxDim / sampleSize > 720) {
                    sampleSize *= 2
                }
                val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOpts) ?: return ""

                val cacheFolder = File(dirRoot, "product_images").apply { mkdirs() }
                val fileName = productId.replace(Regex("[^a-zA-Z0-9_-]"), "_") + ".jpg"
                val outFile = File(cacheFolder, fileName)
                FileOutputStream(outFile).use { fos ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 82, fos)
                }
                bitmap.recycle()
                outFile.toURI().toString()
            } catch (_: Throwable) {
                ""
            }
        }

        return if (trimmed.length <= 2048) trimmed else ""
    }
}
