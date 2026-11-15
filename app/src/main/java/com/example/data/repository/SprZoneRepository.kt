package com.example.data.repository

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import com.example.data.local.AddressDao
import com.example.data.local.CartDao
import com.example.data.local.OrderDao
import com.example.data.local.ProductDao
import com.example.data.local.ReviewDao
import com.example.data.local.SessionPreferences
import com.example.data.local.SprZoneDatabase
import com.example.data.local.UserDao
import com.example.data.local.WishlistDao
import com.example.data.model.AddressEntity
import com.example.data.model.AppNotificationItem
import com.example.data.model.CartItemEntity
import com.example.data.model.DeliveryConfigSummary
import com.example.data.model.DeliveryZone
import com.example.data.model.MerchantPaymentSettings
import com.example.data.model.OrderEntity
import com.example.data.model.PaymentMethod
import com.example.data.model.ProductEntity
import com.example.data.model.ReviewEntity
import com.example.data.model.StoreConstants
import com.example.data.model.UserAccountEntity
import com.example.data.model.WishlistEntity
import com.example.data.remote.FirebaseAuthRequest
import com.example.data.remote.FirebaseAuthRestService
import com.example.data.remote.FirebaseDeleteAccountRequest
import com.example.data.remote.FirebaseLookupRequest
import com.example.data.remote.FirebaseSendOobCodeRequest
import com.example.data.remote.FirebaseSignInWithIdpRequest
import com.example.data.remote.FirebaseTokenRefreshService
import com.example.data.remote.FirebaseUpdateAccountRequest
import com.example.data.remote.RemoteCouponDto
import com.example.data.remote.RemoteCustomerAddressDto
import com.example.data.remote.RemoteCustomerDto
import com.example.data.remote.RemoteCustomerProfileDto
import com.example.data.remote.RemoteOrderDto
import com.example.data.remote.RemoteOrderItemDto
import com.example.data.remote.RemoteReviewDto
import com.example.data.remote.SprZoneNetworkClient
import com.example.data.remote.SprZoneNetworkClient.toDomain
import com.example.data.remote.SprZoneRtdbService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

class GooglePendingLinkException(
    val email: String,
    val googleIdToken: String,
    message: String
) : Exception(message)

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
    private val authApi: FirebaseAuthRestService = SprZoneNetworkClient.authService,
    private val tokenApi: FirebaseTokenRefreshService = SprZoneNetworkClient.tokenService,
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

    private val _deliveryConfig = MutableStateFlow(DeliveryConfigSummary())
    val deliveryConfig: StateFlow<DeliveryConfigSummary> = _deliveryConfig.asStateFlow()

    private val _coupons = MutableStateFlow<Map<String, RemoteCouponDto>>(emptyMap())
    val coupons: StateFlow<Map<String, RemoteCouponDto>> = _coupons.asStateFlow()

    private val _allNotifications = MutableStateFlow<List<AppNotificationItem>>(emptyList())
    val allNotifications: StateFlow<List<AppNotificationItem>> = _allNotifications.asStateFlow()

    val loggedInEmailFlow: Flow<String?> = prefs.loggedInEmail
    val deliveryZoneFlow: Flow<DeliveryZone> = prefs.deliveryZone
    val isDarkModeFlow: Flow<Boolean> = prefs.isDarkMode
    val readNotificationIdsFlow: Flow<Set<String>> = prefs.readNotificationIds

    suspend fun ensureSeededAndSync() {
        syncRemoteCatalogAndSettings()
    }

    suspend fun syncRemoteCatalogAndSettings() = withContext(Dispatchers.IO) {
        try {
            val delResp = rtdb.getDeliveryConfig()
            if (delResp.isSuccessful) {
                delResp.body()?.let { dto ->
                    val minAmt = (dto.freeDeliveryMinAmount ?: StoreConstants.FREE_DELIVERY_THRESHOLD_BDT.toDouble())
                        .roundToInt().toLong().coerceAtLeast(0L)
                    val rates = dto.zones.orEmpty().values
                        .mapNotNull { z ->
                            val zName = z.name?.trim()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                            zName to (z.rate ?: 120.0).roundToInt().toLong().coerceAtLeast(0L)
                        }.toMap()
                    _deliveryConfig.value = DeliveryConfigSummary(
                        freeDeliveryMinAmountBdt = minAmt,
                        divisionRates = rates
                    )
                }
            }
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
        }

        try {
            val settingsResp = rtdb.getPaymentSettings()
            if (settingsResp.isSuccessful) {
                settingsResp.body()?.let { dto ->
                    _paymentSettings.value = dto.toDomain()
                }
            }
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
        }

        try {
            val couponsResp = rtdb.getCoupons()
            if (couponsResp.isSuccessful) {
                _coupons.value = couponsResp.body().orEmpty()
            }
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
        }

        val reviewStatsByProduct = syncReviewsInternal()

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
                        val variantPricesStr = serializeVariantPrices(dto.variantPrices)
                        val rStats = reviewStatsByProduct[safeId]
                        ProductEntity(
                            id = safeId,
                            name = name,
                            category = cat,
                            brand = dto.brand?.trim()?.takeIf { it.isNotBlank() }?.take(120) ?: "SPR Official",
                            sellerName = dto.sellerName?.trim()?.takeIf { it.isNotBlank() }?.take(120) ?: "SPR Official Store",
                            priceBdt = price,
                            originalPriceBdt = if (oldPrice > price) oldPrice else price,
                            discountPercent = discount,
                            rating = rStats?.first ?: 0.0,
                            reviewCount = rStats?.second ?: 0,
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
                            variantColorsCsv = dto.variantColors?.trim()?.take(400).orEmpty(),
                            variantSizesCsv = dto.variantSizes?.trim()?.take(400).orEmpty(),
                            variantPricesJson = variantPricesStr,
                            freeDeliveryMinQty = (dto.freeDeliveryMinQty ?: 0).coerceAtLeast(0),
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
        }

        // Sync currently signed-in customer profile & addresses from Firebase RTDB
        syncActiveCustomerSessionFromRemote()
    }

    suspend fun syncReviewsAndRatings() = withContext(Dispatchers.IO) {
        val statsByProd = syncReviewsInternal()
        val products = productDao.observeAllProducts().first()
        products.forEach { prod ->
            val st = statsByProd[prod.id]
            val newRating = st?.first ?: 0.0
            val newCount = st?.second ?: 0
            if (prod.rating != newRating || prod.reviewCount != newCount) {
                productDao.updateRatingSummary(prod.id, newRating, newCount)
            }
        }
    }

    private suspend fun syncReviewsInternal(): Map<String, Pair<Double, Int>> {
        try {
            val revResp = rtdb.getReviews()
            if (revResp.isSuccessful) {
                val raw = revResp.body().orEmpty()
                val entities = mutableListOf<ReviewEntity>()
                val stats = mutableMapOf<String, Pair<Double, Int>>()
                raw.forEach { (productId, prodRevs) ->
                    val validRatings = mutableListOf<Int>()
                    prodRevs.forEach { (_, dto) ->
                        val stars = (dto.rating ?: 0).coerceIn(0, 5)
                        val comment = dto.comment?.trim().orEmpty()
                        if (stars in 1..5 && comment.isNotBlank()) {
                            validRatings.add(stars)
                            entities.add(
                                ReviewEntity(
                                    productId = productId.take(120),
                                    userName = dto.userName?.trim()?.takeIf { it.isNotBlank() }?.take(120) ?: "Customer",
                                    userEmail = "",
                                    rating = stars,
                                    comment = comment.take(1500),
                                    verifiedPurchase = dto.verifiedPurchase != false,
                                    createdAt = dto.createdAt ?: System.currentTimeMillis()
                                )
                            )
                        }
                    }
                    if (validRatings.isNotEmpty()) {
                        val avg = validRatings.sum().toDouble() / validRatings.size
                        val rounded = (avg * 10.0).roundToInt() / 10.0
                        stats[productId] = rounded to validRatings.size
                    }
                }
                reviewDao.clearAll()
                if (entities.isNotEmpty()) {
                    reviewDao.insertAll(entities)
                }
                return stats
            }
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
        }

        // Fallback to already cached local reviews so offline stats remain consistent
        val cached = reviewDao.getAllReviews().groupBy { it.productId }
        return cached.mapValues { (_, list) ->
            if (list.isEmpty()) 0.0 to 0
            else {
                val avg = list.sumOf { it.rating }.toDouble() / list.size
                ((avg * 10.0).roundToInt() / 10.0) to list.size
            }
        }
    }

    fun observeRealtimeNodeChanges(nodePath: String): Flow<Unit> = flow {
        val url = "${StoreConstants.RTDB_BASE_URL}$nodePath"
        var backoffMs = 2_000L
        while (currentCoroutineContext().isActive) {
            var gotEvent = false
            var activeCall: Call? = null
            try {
                val req = Request.Builder()
                    .url(url)
                    .header("Accept", "text/event-stream")
                    .header("Cache-Control", "no-cache")
                    .build()
                activeCall = SprZoneNetworkClient.sseHttpClient.newCall(req)
                activeCall.execute().use { resp ->
                    if (resp.isSuccessful) {
                        backoffMs = 2_000L
                        val body = resp.body
                        val reader = body?.charStream()?.buffered()
                        var currentEvent = ""
                        while (currentCoroutineContext().isActive && reader != null) {
                            val line = reader.readLine() ?: break
                            if (line.startsWith("event:")) {
                                currentEvent = line.removePrefix("event:").trim()
                            } else if (line.startsWith("data:")) {
                                if (currentEvent == "put" || currentEvent == "patch") {
                                    if (gotEvent) {
                                        emit(Unit)
                                    } else {
                                        // Skip emitting on very first SSE snapshot right at connect; emit on live updates
                                        gotEvent = true
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
            } finally {
                activeCall?.cancel()
            }
            delay(backoffMs)
            backoffMs = (backoffMs * 2).coerceAtMost(30_000L)
        }
    }.flowOn(Dispatchers.IO)

    suspend fun syncAppNotifications(): List<AppNotificationItem> = withContext(Dispatchers.IO) {
        return@withContext try {
            val resp = rtdb.getAppNotifications()
            if (!resp.isSuccessful) return@withContext emptyList()
            val items = resp.body().orEmpty()
                .entries
                .mapNotNull { (id, dto) ->
                    if (dto.active == false) return@mapNotNull null
                    val title = dto.title?.trim()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    val msg = dto.message?.trim()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    AppNotificationItem(
                        id = id,
                        title = title.take(180),
                        message = msg.take(1000),
                        type = dto.type?.trim()?.lowercase()?.ifBlank { "promo" } ?: "promo",
                        productId = dto.productId?.trim().orEmpty(),
                        targetEmail = dto.targetEmail?.trim()?.lowercase().orEmpty(),
                        createdAt = dto.createdAt ?: dto.updatedAt ?: System.currentTimeMillis()
                    )
                }
                .sortedByDescending { it.createdAt }
            _allNotifications.value = items

            val activeEmail = prefs.loggedInEmail.first()?.trim()?.lowercase()
            val eligible = items.filter { n ->
                n.targetEmail.isBlank() || (!activeEmail.isNullOrBlank() && n.targetEmail == activeEmail)
            }
            val seen = prefs.seenNotificationIds.first()
            val unseen = eligible.filter { it.id !in seen }
            if (unseen.isNotEmpty()) {
                prefs.markNotificationsSeen(unseen.map { it.id })
            }
            unseen
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            emptyList()
        }
    }

    suspend fun markNotificationsRead(ids: Collection<String>) {
        prefs.markNotificationsRead(ids)
    }

    suspend fun syncUserOrdersFromRemote(email: String) = withContext(Dispatchers.IO) {
        val uid = prefs.firebaseUid.first()?.takeIf { it.isNotBlank() } ?: return@withContext
        var token = resolveValidIdToken(forceRefresh = false) ?: return@withContext
        try {
            var resp = rtdb.getOrdersByUid(equalTo = "\"$uid\"", authToken = token)
            if (resp.code() == 401) {
                token = resolveValidIdToken(forceRefresh = true) ?: return@withContext
                resp = rtdb.getOrdersByUid(equalTo = "\"$uid\"", authToken = token)
            }
            if (resp.isSuccessful) {
                val map = resp.body() ?: return@withContext
                val normalizedEmail = email.trim().lowercase()
                map.forEach { (key, dto) ->
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
                                userEmail = (dto.customerEmail?.takeIf { it.isNotBlank() } ?: normalizedEmail).trim().lowercase().take(160),
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
                                paymentAccountMasked = dto.senderNumber?.takeLast(4)?.let { "•••• $it" } ?: "Cash on Delivery",
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
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
        }
    }

    suspend fun trackOrderById(orderId: String): Result<OrderEntity> = withContext(Dispatchers.IO) {
        val cleanId = orderId.trim()
        if (cleanId.isBlank()) return@withContext Result.failure(IllegalArgumentException("Enter an Order ID"))
        val local = orderDao.getOrderById(cleanId)
        try {
            val token = resolveValidIdToken(forceRefresh = false)
            var remote = rtdb.getOrder(cleanId, token)
            if (remote.code() == 401) {
                val refreshed = resolveValidIdToken(forceRefresh = true)
                if (!refreshed.isNullOrBlank()) {
                    remote = rtdb.getOrder(cleanId, refreshed)
                }
            }
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
                    paymentMethod = (dto.paymentMethod ?: local?.paymentMethod ?: "Cash on Delivery").take(60),
                    senderNumber = (dto.senderNumber ?: local?.senderNumber ?: "").take(40),
                    trxId = (dto.trxId ?: local?.trxId ?: "").take(120),
                    paymentAccountMasked = local?.paymentAccountMasked ?: "Cash on Delivery",
                    transactionId = dto.trxId?.takeIf { it.isNotBlank() }?.take(120) ?: local?.transactionId ?: cleanId.take(120),
                    paymentStatus = (dto.paymentStatus ?: local?.paymentStatus ?: "Pay on Delivery").take(80),
                    orderStatus = (dto.status ?: local?.orderStatus ?: "Pending").take(60),
                    createdAt = dto.createdAt ?: local?.createdAt ?: System.currentTimeMillis(),
                    updatedAt = dto.updatedAt ?: System.currentTimeMillis()
                )
                orderDao.insertOrder(entity)
                return@withContext Result.success(entity)
            }
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
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

    // --- Customer Authentication & Firebase RTDB Synchronization ---

    suspend fun registerUser(
        fullName: String,
        email: String,
        phone: String,
        password: String
    ): Result<UserAccountEntity> = withContext(Dispatchers.IO) {
        val normalizedEmail = email.trim().lowercase()
        val trimmedName = fullName.trim()
        if (trimmedName.isBlank()) return@withContext Result.failure(IllegalArgumentException("Full name is required"))
        if (!normalizedEmail.contains("@") || !normalizedEmail.contains(".")) {
            return@withContext Result.failure(IllegalArgumentException("Enter a valid email address"))
        }
        if (password.length < 6) {
            return@withContext Result.failure(IllegalArgumentException("Password must be at least 6 characters"))
        }
        val cleanPhone = phone.trim()
        if (cleanPhone.isNotBlank() && !StoreConstants.isValidBdPhone(cleanPhone)) {
            return@withContext Result.failure(IllegalArgumentException("Enter a valid 11-digit BD mobile number (01XXXXXXXXX)."))
        }
        val normPhone = if (cleanPhone.isNotBlank()) StoreConstants.normalizeBdPhone(cleanPhone) else ""

        val authResp = try {
            authApi.signUp(body = FirebaseAuthRequest(email = normalizedEmail, password = password))
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            return@withContext Result.failure(IllegalStateException("Network error while creating account: ${t.message ?: "Check internet connection"}"))
        }

        if (!authResp.isSuccessful || authResp.body()?.localId.isNullOrBlank() || authResp.body()?.idToken.isNullOrBlank()) {
            val rawErr = authResp.errorBody()?.string().orEmpty()
            return@withContext Result.failure(IllegalStateException(parseFirebaseAuthError(rawErr, "Could not create account")))
        }

        val authBody = authResp.body()!!
        val uid = authBody.localId!!
        val idToken = authBody.idToken!!
        val refreshToken = authBody.refreshToken.orEmpty()
        val now = System.currentTimeMillis()

        val remoteProfile = RemoteCustomerProfileDto(
            name = trimmedName.take(120),
            email = normalizedEmail.take(160),
            phone = normPhone,
            bio = "",
            createdAt = now,
            updatedAt = now
        )
        val saveProfileResp = try {
            rtdb.putCustomerProfile(uid, idToken, remoteProfile)
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            null
        }
        if (saveProfileResp == null || !saveProfileResp.isSuccessful) {
            return@withContext Result.failure(IllegalStateException("Account created in Auth, but database sync failed. Please try signing in."))
        }

        val salt = generateSalt()
        val hash = hashPassword(password, salt)
        val user = UserAccountEntity(
            email = normalizedEmail,
            fullName = trimmedName.take(120),
            phone = normPhone,
            passwordHash = hash,
            salt = salt,
            createdAt = now
        )
        userDao.upsertUser(user)
        prefs.setFirebaseSession(uid, idToken, refreshToken)
        migrateGuestCart(normalizedEmail)
        prefs.setLoggedInEmail(normalizedEmail)
        Result.success(user)
    }

    suspend fun loginUser(email: String, password: String): Result<UserAccountEntity> = withContext(Dispatchers.IO) {
        val normalizedEmail = email.trim().lowercase()
        if (!normalizedEmail.contains("@") || !normalizedEmail.contains(".")) {
            return@withContext Result.failure(IllegalArgumentException("Enter a valid email address"))
        }
        if (password.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Enter your password"))
        }

        val authResp = try {
            authApi.signInWithPassword(body = FirebaseAuthRequest(email = normalizedEmail, password = password))
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            return@withContext Result.failure(IllegalStateException("Network error during sign in: ${t.message ?: "Check internet connection"}"))
        }

        if (!authResp.isSuccessful || authResp.body()?.localId.isNullOrBlank() || authResp.body()?.idToken.isNullOrBlank()) {
            val rawErr = authResp.errorBody()?.string().orEmpty()
            return@withContext Result.failure(IllegalArgumentException(parseFirebaseAuthError(rawErr, "Invalid email or password")))
        }

        val authBody = authResp.body()!!
        val uid = authBody.localId!!
        val idToken = authBody.idToken!!
        val refreshToken = authBody.refreshToken.orEmpty()

        prefs.setFirebaseSession(uid, idToken, refreshToken)
        val user = pullAndCacheRemoteCustomer(
            uid = uid,
            idToken = idToken,
            email = normalizedEmail,
            fallbackName = authBody.displayName?.takeIf { it.isNotBlank() }
                ?: normalizedEmail.substringBefore("@"),
            passwordForLocalCache = password
        )

        migrateGuestCart(normalizedEmail)
        prefs.setLoggedInEmail(normalizedEmail)
        syncUserOrdersFromRemote(normalizedEmail)
        Result.success(user)
    }

    suspend fun sendPasswordResetEmail(email: String): Result<String> = withContext(Dispatchers.IO) {
        val normalizedEmail = email.trim().lowercase()
        if (!normalizedEmail.contains("@") || !normalizedEmail.contains(".")) {
            return@withContext Result.failure(IllegalArgumentException("Enter a valid email address"))
        }
        val resp = try {
            authApi.sendOobCode(body = FirebaseSendOobCodeRequest(email = normalizedEmail))
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            return@withContext Result.failure(IllegalStateException("Network error sending reset email: ${t.message ?: "Check internet connection"}"))
        }
        if (!resp.isSuccessful) {
            val rawErr = resp.errorBody()?.string().orEmpty()
            return@withContext Result.failure(IllegalArgumentException(parseFirebaseAuthError(rawErr, "Could not send password reset email")))
        }
        Result.success("Password reset link sent to $normalizedEmail. Check your inbox and spam folder.")
    }

    suspend fun signInWithGoogleIdToken(googleIdToken: String): Result<UserAccountEntity> = withContext(Dispatchers.IO) {
        val cleanGoogleToken = googleIdToken.trim()
        if (cleanGoogleToken.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Google ID token was empty."))
        }
        val encodedToken = URLEncoder.encode(cleanGoogleToken, "UTF-8")
        val idpResp = try {
            authApi.signInWithIdp(
                body = FirebaseSignInWithIdpRequest(
                    postBody = "id_token=$encodedToken&providerId=google.com",
                    returnIdpCredential = true,
                    returnSecureToken = true
                )
            )
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            return@withContext Result.failure(IllegalStateException("Network error during Google Sign-In: ${t.message ?: "Check internet connection"}"))
        }

        if (!idpResp.isSuccessful) {
            val rawErr = idpResp.errorBody()?.string().orEmpty()
            return@withContext Result.failure(IllegalStateException(parseFirebaseAuthError(rawErr, "Google Sign-In failed")))
        }

        val body = idpResp.body()
        if (body?.needConfirmation == true) {
            val conflictEmail = body.email?.trim()?.lowercase().orEmpty()
            return@withContext Result.failure(
                GooglePendingLinkException(
                    email = conflictEmail,
                    googleIdToken = cleanGoogleToken,
                    message = "An account already exists for $conflictEmail. Enter your password once to link Google Sign-In."
                )
            )
        }

        val uid = body?.localId?.takeIf { it.isNotBlank() }
        val idToken = body?.idToken?.takeIf { it.isNotBlank() }
        val resolvedEmail = body?.email?.trim()?.lowercase()?.takeIf { it.isNotBlank() }
        if (uid == null || idToken == null || resolvedEmail == null) {
            return@withContext Result.failure(IllegalStateException("Google Sign-In did not return a complete Firebase session."))
        }

        prefs.setFirebaseSession(uid, idToken, body.refreshToken.orEmpty())
        val user = pullAndCacheRemoteCustomer(
            uid = uid,
            idToken = idToken,
            email = resolvedEmail,
            fallbackName = body.displayName?.takeIf { it.isNotBlank() }
                ?: resolvedEmail.substringBefore("@"),
            passwordForLocalCache = generateSalt()
        )
        migrateGuestCart(resolvedEmail)
        prefs.setLoggedInEmail(resolvedEmail)
        syncUserOrdersFromRemote(resolvedEmail)
        Result.success(user)
    }

    suspend fun loginAndLinkGoogle(
        email: String,
        password: String,
        googleIdToken: String
    ): Result<UserAccountEntity> = withContext(Dispatchers.IO) {
        val normalizedEmail = email.trim().lowercase()
        if (!normalizedEmail.contains("@") || !normalizedEmail.contains(".")) {
            return@withContext Result.failure(IllegalArgumentException("Enter a valid email address"))
        }
        if (password.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Enter your existing password to link Google"))
        }

        val authResp = try {
            authApi.signInWithPassword(body = FirebaseAuthRequest(email = normalizedEmail, password = password))
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            return@withContext Result.failure(IllegalStateException("Network error during account verification: ${t.message ?: "Check internet connection"}"))
        }
        if (!authResp.isSuccessful || authResp.body()?.localId.isNullOrBlank() || authResp.body()?.idToken.isNullOrBlank()) {
            val rawErr = authResp.errorBody()?.string().orEmpty()
            return@withContext Result.failure(IllegalArgumentException(parseFirebaseAuthError(rawErr, "Invalid email or password")))
        }

        val baseBody = authResp.body()!!
        var finalUid = baseBody.localId!!
        var finalIdToken = baseBody.idToken!!
        var finalRefreshToken = baseBody.refreshToken.orEmpty()
        var resolvedName = baseBody.displayName?.takeIf { it.isNotBlank() }

        val cleanGoogleToken = googleIdToken.trim()
        if (cleanGoogleToken.isNotBlank()) {
            val encodedToken = URLEncoder.encode(cleanGoogleToken, "UTF-8")
            val linkResp = try {
                authApi.signInWithIdp(
                    body = FirebaseSignInWithIdpRequest(
                        postBody = "id_token=$encodedToken&providerId=google.com",
                        returnIdpCredential = true,
                        returnSecureToken = true,
                        idToken = finalIdToken
                    )
                )
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                null
            }
            if (linkResp != null && linkResp.isSuccessful && linkResp.body() != null) {
                val lb = linkResp.body()!!
                if (!lb.localId.isNullOrBlank()) finalUid = lb.localId
                if (!lb.idToken.isNullOrBlank()) finalIdToken = lb.idToken
                if (!lb.refreshToken.isNullOrBlank()) finalRefreshToken = lb.refreshToken
                if (!lb.displayName.isNullOrBlank() && resolvedName.isNullOrBlank()) resolvedName = lb.displayName
            } else if (linkResp != null && !linkResp.isSuccessful) {
                val errRaw = linkResp.errorBody()?.string().orEmpty()
                if (!errRaw.contains("FEDERATED_USER_ID_ALREADY_LINKED", ignoreCase = true)) {
                    return@withContext Result.failure(IllegalStateException(parseFirebaseAuthError(errRaw, "Could not link Google credential")))
                }
            }
        }

        prefs.setFirebaseSession(finalUid, finalIdToken, finalRefreshToken)
        val user = pullAndCacheRemoteCustomer(
            uid = finalUid,
            idToken = finalIdToken,
            email = normalizedEmail,
            fallbackName = resolvedName ?: normalizedEmail.substringBefore("@"),
            passwordForLocalCache = password
        )
        migrateGuestCart(normalizedEmail)
        prefs.setLoggedInEmail(normalizedEmail)
        syncUserOrdersFromRemote(normalizedEmail)
        Result.success(user)
    }

    suspend fun getLinkedProvidersForCurrentUser(): List<String> = withContext(Dispatchers.IO) {
        var token = resolveValidIdToken(forceRefresh = false) ?: return@withContext emptyList()
        return@withContext try {
            var resp = authApi.lookupAccount(body = FirebaseLookupRequest(token))
            if (resp.code() == 401) {
                token = resolveValidIdToken(forceRefresh = true) ?: return@withContext emptyList()
                resp = authApi.lookupAccount(body = FirebaseLookupRequest(token))
            }
            if (!resp.isSuccessful) return@withContext emptyList()
            resp.body()?.users?.firstOrNull()?.providerUserInfo.orEmpty()
                .mapNotNull { it.providerId?.trim()?.takeIf { p -> p.isNotBlank() } }
                .distinct()
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            emptyList()
        }
    }

    suspend fun linkGoogleToCurrentAccount(googleIdToken: String): Result<List<String>> = withContext(Dispatchers.IO) {
        val cleanGoogleToken = googleIdToken.trim()
        if (cleanGoogleToken.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Google credential was empty."))
        }
        var token = resolveValidIdToken(forceRefresh = false)
            ?: return@withContext Result.failure(IllegalStateException("Please sign in first."))
        val encodedToken = URLEncoder.encode(cleanGoogleToken, "UTF-8")
        var resp = try {
            authApi.signInWithIdp(
                body = FirebaseSignInWithIdpRequest(
                    postBody = "id_token=$encodedToken&providerId=google.com",
                    returnIdpCredential = true,
                    returnSecureToken = true,
                    idToken = token
                )
            )
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            return@withContext Result.failure(IllegalStateException("Network error while linking Google: ${t.message ?: "Check internet"}"))
        }
        if (resp.code() == 401) {
            token = resolveValidIdToken(forceRefresh = true) ?: token
            resp = authApi.signInWithIdp(
                body = FirebaseSignInWithIdpRequest(
                    postBody = "id_token=$encodedToken&providerId=google.com",
                    returnIdpCredential = true,
                    returnSecureToken = true,
                    idToken = token
                )
            )
        }
        if (!resp.isSuccessful) {
            val rawErr = resp.errorBody()?.string().orEmpty()
            return@withContext Result.failure(IllegalStateException(parseFirebaseAuthError(rawErr, "Could not link Google account")))
        }
        resp.body()?.let { body ->
            val nextUid = body.localId?.takeIf { it.isNotBlank() } ?: prefs.firebaseUid.first()
            val nextIdToken = body.idToken?.takeIf { it.isNotBlank() } ?: token
            val nextRefresh = body.refreshToken?.takeIf { it.isNotBlank() } ?: prefs.firebaseRefreshToken.first().orEmpty()
            prefs.setFirebaseSession(nextUid, nextIdToken, nextRefresh)
        }
        Result.success(getLinkedProvidersForCurrentUser())
    }

    suspend fun linkPasswordToCurrentAccount(newPassword: String): Result<List<String>> = withContext(Dispatchers.IO) {
        if (newPassword.length < 6) {
            return@withContext Result.failure(IllegalArgumentException("Password must be at least 6 characters"))
        }
        var token = resolveValidIdToken(forceRefresh = false)
            ?: return@withContext Result.failure(IllegalStateException("Please sign in first."))
        var resp = try {
            authApi.updateAccount(body = FirebaseUpdateAccountRequest(idToken = token, password = newPassword))
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            return@withContext Result.failure(IllegalStateException("Network error setting password: ${t.message ?: "Check internet"}"))
        }
        if (resp.code() == 401) {
            token = resolveValidIdToken(forceRefresh = true) ?: token
            resp = authApi.updateAccount(body = FirebaseUpdateAccountRequest(idToken = token, password = newPassword))
        }
        if (!resp.isSuccessful) {
            val rawErr = resp.errorBody()?.string().orEmpty()
            return@withContext Result.failure(IllegalStateException(parseFirebaseAuthError(rawErr, "Could not set email password")))
        }
        resp.body()?.let { body ->
            val nextUid = body.localId?.takeIf { it.isNotBlank() } ?: prefs.firebaseUid.first()
            val nextIdToken = body.idToken?.takeIf { it.isNotBlank() } ?: token
            val nextRefresh = body.refreshToken?.takeIf { it.isNotBlank() } ?: prefs.firebaseRefreshToken.first().orEmpty()
            prefs.setFirebaseSession(nextUid, nextIdToken, nextRefresh)
        }
        val email = prefs.loggedInEmail.first()?.trim()?.lowercase()
        if (!email.isNullOrBlank()) {
            userDao.getUserByEmail(email)?.let { existing ->
                val nextSalt = generateSalt()
                userDao.upsertUser(existing.copy(salt = nextSalt, passwordHash = hashPassword(newPassword, nextSalt)))
            }
        }
        Result.success(getLinkedProvidersForCurrentUser())
    }

    private suspend fun syncActiveCustomerSessionFromRemote() {
        val email = prefs.loggedInEmail.first()?.trim()?.lowercase() ?: return
        val uid = prefs.firebaseUid.first() ?: return
        val token = resolveValidIdToken(forceRefresh = false) ?: return
        try {
            val resp = rtdb.getCustomerProfile(uid, token)
            if (resp.code() == 401) {
                val refreshed = resolveValidIdToken(forceRefresh = true) ?: return
                val retry = rtdb.getCustomerProfile(uid, refreshed)
                if (retry.isSuccessful) {
                    applyRemoteProfileSnapshot(email, uid, refreshed, retry.body())
                }
            } else if (resp.isSuccessful) {
                applyRemoteProfileSnapshot(email, uid, token, resp.body())
            }
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
        }
    }

    private suspend fun pullAndCacheRemoteCustomer(
        uid: String,
        idToken: String,
        email: String,
        fallbackName: String,
        passwordForLocalCache: String
    ): UserAccountEntity {
        val remote = try {
            val resp = rtdb.getCustomerProfile(uid, idToken)
            if (resp.isSuccessful) resp.body() else null
        } catch (_: Throwable) {
            null
        }

        val now = System.currentTimeMillis()
        val finalProfile = if (remote == null) {
            val created = RemoteCustomerProfileDto(
                name = fallbackName.take(120),
                email = email.take(160),
                phone = "",
                bio = "",
                createdAt = now,
                updatedAt = now
            )
            try { rtdb.putCustomerProfile(uid, idToken, created) } catch (_: Throwable) {}
            created
        } else {
            remote
        }

        val existingLocal = userDao.getUserByEmail(email)
        val salt = existingLocal?.salt ?: generateSalt()
        val hash = hashPassword(passwordForLocalCache, salt)
        val user = UserAccountEntity(
            email = email,
            fullName = (finalProfile.name?.takeIf { it.isNotBlank() } ?: fallbackName).take(120),
            phone = finalProfile.phone.orEmpty(),
            passwordHash = hash,
            salt = salt,
            bio = finalProfile.bio.orEmpty(),
            createdAt = finalProfile.createdAt ?: existingLocal?.createdAt ?: now
        )
        userDao.upsertUser(user)
        syncRemoteAddressesToLocal(email, finalProfile.addresses)
        return user
    }

    private suspend fun applyRemoteProfileSnapshot(
        email: String,
        uid: String,
        idToken: String,
        remote: RemoteCustomerProfileDto?
    ) {
        val existingLocal = userDao.getUserByEmail(email)
        if (remote == null) {
            if (existingLocal != null) {
                try {
                    rtdb.putCustomerProfile(
                        uid,
                        idToken,
                        RemoteCustomerProfileDto(
                            name = existingLocal.fullName,
                            email = existingLocal.email,
                            phone = existingLocal.phone,
                            bio = existingLocal.bio,
                            createdAt = existingLocal.createdAt,
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                } catch (_: Throwable) {}
            }
            return
        }

        if (existingLocal != null) {
            val updated = existingLocal.copy(
                fullName = remote.name?.takeIf { it.isNotBlank() } ?: existingLocal.fullName,
                phone = remote.phone ?: existingLocal.phone,
                bio = remote.bio ?: existingLocal.bio
            )
            userDao.upsertUser(updated)
        }
        syncRemoteAddressesToLocal(email, remote.addresses)
    }

    private suspend fun syncRemoteAddressesToLocal(
        email: String,
        remoteMap: Map<String, RemoteCustomerAddressDto>?
    ) {
        if (remoteMap == null) return
        val localList = addressDao.getAddressesForUser(email)
        val localByRemote = localList.associateBy { it.remoteKey }
        val remoteKeys = remoteMap.keys

        // Remove locally cached remote addresses that were deleted on server
        for (local in localList) {
            if (local.remoteKey.isNotBlank() && local.remoteKey !in remoteKeys) {
                addressDao.deleteAddress(local.id)
            }
        }

        // Upsert server addresses
        remoteMap.forEach { (rKey, dto) ->
            val districtPart = dto.district?.trim().orEmpty()
            val thanaPart = dto.thana?.trim().orEmpty()
            val combinedDistThana = listOf(districtPart, thanaPart).filter { it.isNotBlank() }.distinct().joinToString(", ")
                .ifBlank { "Dhaka" }
            val existing = localByRemote[rKey]
            val entity = AddressEntity(
                id = existing?.id ?: 0L,
                userEmail = email,
                label = dto.label?.takeIf { it.isNotBlank() } ?: "Home",
                recipientName = dto.recipientName.orEmpty(),
                phone = dto.phone.orEmpty(),
                division = dto.division?.takeIf { it.isNotBlank() } ?: "Dhaka",
                districtThana = combinedDistThana,
                streetAddress = dto.street.orEmpty(),
                isDefault = dto.isDefault == true,
                remoteKey = rKey
            )
            if (existing != null) {
                addressDao.updateAddress(entity)
            } else {
                addressDao.insertAddress(entity)
            }
        }
    }

    private suspend fun resolveValidIdToken(forceRefresh: Boolean): String? {
        val currentToken = prefs.firebaseIdToken.first()
        if (!forceRefresh && !currentToken.isNullOrBlank()) return currentToken
        val refreshTok = prefs.firebaseRefreshToken.first()?.takeIf { it.isNotBlank() } ?: return currentToken
        return try {
            val resp = tokenApi.refreshToken(refreshToken = refreshTok)
            val body = resp.body()
            if (resp.isSuccessful && !body?.id_token.isNullOrBlank()) {
                val newIdToken = body!!.id_token!!
                val newRefresh = body.refresh_token?.takeIf { it.isNotBlank() } ?: refreshTok
                val uid = body.user_id?.takeIf { it.isNotBlank() } ?: prefs.firebaseUid.first()
                prefs.setFirebaseSession(uid, newIdToken, newRefresh)
                newIdToken
            } else {
                currentToken
            }
        } catch (_: Throwable) {
            currentToken
        }
    }

    suspend fun updateProfile(
        email: String,
        fullName: String,
        phone: String,
        bio: String
    ): Result<UserAccountEntity> = withContext(Dispatchers.IO) {
        val normalizedEmail = email.trim().lowercase()
        val existing = userDao.getUserByEmail(normalizedEmail)
            ?: return@withContext Result.failure(IllegalStateException("Account not found."))
        val trimmedName = fullName.trim()
        if (trimmedName.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Full name is required."))
        }
        val cleanPhone = phone.trim()
        if (cleanPhone.isNotBlank() && !StoreConstants.isValidBdPhone(cleanPhone)) {
            return@withContext Result.failure(IllegalArgumentException("Enter a valid 11-digit BD mobile number (01XXXXXXXXX)."))
        }
        val normalizedPhone = if (cleanPhone.isNotBlank()) StoreConstants.normalizeBdPhone(cleanPhone) else ""
        val cleanBio = bio.trim().take(240)
        val updated = existing.copy(
            fullName = trimmedName.take(120),
            phone = normalizedPhone,
            bio = cleanBio
        )
        userDao.upsertUser(updated)

        val uid = prefs.firebaseUid.first()
        val token = resolveValidIdToken(forceRefresh = false)
        if (!uid.isNullOrBlank() && !token.isNullOrBlank()) {
            val patch = mapOf<String, Any>(
                "name" to updated.fullName,
                "email" to normalizedEmail,
                "phone" to normalizedPhone,
                "bio" to cleanBio,
                "updatedAt" to System.currentTimeMillis()
            )
            try {
                val resp = rtdb.patchCustomerProfile(uid, token, patch)
                if (resp.code() == 401) {
                    resolveValidIdToken(forceRefresh = true)?.let { fresh ->
                        rtdb.patchCustomerProfile(uid, fresh, patch)
                    }
                }
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
            }
        }
        Result.success(updated)
    }

    suspend fun deleteProfile(email: String): Result<Unit> = withContext(Dispatchers.IO) {
        val normalizedEmail = email.trim().lowercase()
        val uid = prefs.firebaseUid.first()
        val token = resolveValidIdToken(forceRefresh = false)
        if (!uid.isNullOrBlank() && !token.isNullOrBlank()) {
            try {
                rtdb.deleteCustomerProfile(uid, token)
                authApi.deleteAccount(body = FirebaseDeleteAccountRequest(token))
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
            }
        }
        addressDao.deleteAllForUser(normalizedEmail)
        cartDao.clearCart(normalizedEmail)
        wishlistDao.clearWishlist(normalizedEmail)
        userDao.deleteUserByEmail(normalizedEmail)
        prefs.setFirebaseSession(null, null, null)
        prefs.setLoggedInEmail(null)
        Result.success(Unit)
    }

    suspend fun signOut() {
        prefs.setFirebaseSession(null, null, null)
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
        quantity: Int = 1,
        unitPriceOverride: Long? = null
    ): Result<Unit> {
        if (product.stock <= 0) {
            return Result.failure(IllegalStateException("Sorry, ${product.name} is currently out of stock."))
        }
        val chosenVariant = variant.ifBlank { product.defaultVariantLabel() }
        val unitPrice = unitPriceOverride ?: product.priceForVariant(chosenVariant)
        val existing = cartDao.findCartItem(userEmail, product.id, chosenVariant)
        if (existing != null) {
            val nextQty = (existing.quantity + quantity).coerceAtMost(product.stock)
            cartDao.updateCartItem(
                existing.copy(
                    unitPriceBdt = unitPrice,
                    freeDeliveryMinQty = product.freeDeliveryMinQty,
                    quantity = nextQty
                )
            )
        } else {
            cartDao.insertCartItem(
                CartItemEntity(
                    userEmail = userEmail,
                    productId = product.id,
                    productName = product.name,
                    unitPriceBdt = unitPrice,
                    originalPriceBdt = product.originalPriceBdt,
                    imageKey = product.imageKey,
                    imageUrl = product.imageUrl,
                    selectedVariant = chosenVariant,
                    quantity = quantity.coerceAtMost(product.stock),
                    freeDeliveryMinQty = product.freeDeliveryMinQty
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

    // --- Addresses (Local Room + Remote Firebase RTDB) ---

    suspend fun saveAddress(
        userEmail: String,
        label: String,
        recipientName: String,
        phone: String,
        division: String,
        districtThana: String,
        streetAddress: String,
        makeDefault: Boolean
    ): Result<Long> = withContext(Dispatchers.IO) {
        val normalizedEmail = userEmail.trim().lowercase()
        if (recipientName.isBlank() || phone.isBlank() || districtThana.isBlank() || streetAddress.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Please fill in all shipping address fields."))
        }
        val shouldDefault = makeDefault || addressDao.countAddresses(normalizedEmail) == 0
        if (shouldDefault) {
            addressDao.clearDefaultAddresses(normalizedEmail)
        }
        val remoteKey = "addr_${System.currentTimeMillis()}_${(100..999).random()}"
        val id = addressDao.insertAddress(
            AddressEntity(
                userEmail = normalizedEmail,
                label = label.ifBlank { "Home" },
                recipientName = recipientName.trim(),
                phone = phone.trim(),
                division = division,
                districtThana = districtThana.trim(),
                streetAddress = streetAddress.trim(),
                isDefault = shouldDefault,
                remoteKey = remoteKey
            )
        )
        pushAddressToRemote(
            userEmail = normalizedEmail,
            remoteKey = remoteKey,
            label = label.ifBlank { "Home" },
            recipientName = recipientName.trim(),
            phone = phone.trim(),
            division = division,
            districtThana = districtThana.trim(),
            streetAddress = streetAddress.trim(),
            isDefault = shouldDefault
        )
        Result.success(id)
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
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val normalizedEmail = userEmail.trim().lowercase()
        if (recipientName.isBlank() || phone.isBlank() || districtThana.isBlank() || streetAddress.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Please fill in all shipping address fields."))
        }
        val existing = addressDao.getAddressById(addressId, normalizedEmail)
            ?: return@withContext Result.failure(IllegalArgumentException("Address not found."))
        if (makeDefault) {
            addressDao.clearDefaultAddresses(normalizedEmail)
        }
        val finalKey = existing.remoteKey.ifBlank { "addr_${System.currentTimeMillis()}_${(100..999).random()}" }
        val finalDefault = makeDefault || existing.isDefault
        addressDao.updateAddress(
            existing.copy(
                label = label.ifBlank { "Home" }.trim(),
                recipientName = recipientName.trim(),
                phone = phone.trim(),
                division = division,
                districtThana = districtThana.trim(),
                streetAddress = streetAddress.trim(),
                isDefault = finalDefault,
                remoteKey = finalKey
            )
        )
        pushAddressToRemote(
            userEmail = normalizedEmail,
            remoteKey = finalKey,
            label = label.ifBlank { "Home" }.trim(),
            recipientName = recipientName.trim(),
            phone = phone.trim(),
            division = division,
            districtThana = districtThana.trim(),
            streetAddress = streetAddress.trim(),
            isDefault = finalDefault
        )
        Result.success(Unit)
    }

    suspend fun setDefaultAddress(userEmail: String, addressId: Long) = withContext(Dispatchers.IO) {
        val normalizedEmail = userEmail.trim().lowercase()
        addressDao.clearDefaultAddresses(normalizedEmail)
        addressDao.setDefaultAddress(addressId, normalizedEmail)
        val all = addressDao.getAddressesForUser(normalizedEmail)
        val uid = prefs.firebaseUid.first() ?: return@withContext
        val token = resolveValidIdToken(forceRefresh = false) ?: return@withContext
        val patch = buildMap<String, Any> {
            all.forEach { a ->
                if (a.remoteKey.isNotBlank()) {
                    put("${a.remoteKey}/isDefault", a.id == addressId)
                }
            }
        }
        if (patch.isNotEmpty()) {
            try {
                rtdb.patchCustomerAddresses(uid, token, patch)
            } catch (_: Throwable) {}
        }
    }

    suspend fun deleteAddress(id: Long) = withContext(Dispatchers.IO) {
        val email = prefs.loggedInEmail.first()?.trim()?.lowercase()
        val target = if (!email.isNullOrBlank()) addressDao.getAddressById(id, email) else null
        addressDao.deleteAddress(id)
        val uid = prefs.firebaseUid.first()
        val token = resolveValidIdToken(forceRefresh = false)
        if (!uid.isNullOrBlank() && !token.isNullOrBlank() && target != null && target.remoteKey.isNotBlank()) {
            try {
                rtdb.deleteCustomerAddress(uid, target.remoteKey, token)
            } catch (_: Throwable) {}
        }
    }

    private suspend fun pushAddressToRemote(
        userEmail: String,
        remoteKey: String,
        label: String,
        recipientName: String,
        phone: String,
        division: String,
        districtThana: String,
        streetAddress: String,
        isDefault: Boolean
    ) {
        val uid = prefs.firebaseUid.first() ?: return
        var token = resolveValidIdToken(forceRefresh = false) ?: return

        val parts = districtThana.split(",").map { it.trim() }.filter { it.isNotBlank() }
        val district = parts.firstOrNull().orEmpty()
        val thana = if (parts.size > 1) parts.drop(1).joinToString(", ") else districtThana

        if (isDefault) {
            val others = addressDao.getAddressesForUser(userEmail)
                .filter { it.remoteKey.isNotBlank() && it.remoteKey != remoteKey }
            if (others.isNotEmpty()) {
                val clearPatch = others.associate { "${it.remoteKey}/isDefault" to (false as Any) }
                try {
                    val patchResp = rtdb.patchCustomerAddresses(uid, token, clearPatch)
                    if (patchResp.code() == 401) {
                        token = resolveValidIdToken(forceRefresh = true) ?: token
                        rtdb.patchCustomerAddresses(uid, token, clearPatch)
                    }
                } catch (_: Throwable) {}
            }
        }

        val dto = RemoteCustomerAddressDto(
            label = label,
            recipientName = recipientName,
            phone = phone,
            division = division,
            district = district,
            thana = thana,
            street = streetAddress,
            isDefault = isDefault
        )
        try {
            val resp = rtdb.putCustomerAddress(uid, remoteKey, token, dto)
            if (resp.code() == 401) {
                resolveValidIdToken(forceRefresh = true)?.let { fresh ->
                    rtdb.putCustomerAddress(uid, remoteKey, fresh, dto)
                }
            }
        } catch (_: Throwable) {}
    }

    // --- Promo & Checkout ---

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

    fun computeEffectiveDeliveryFee(
        cartItems: List<CartItemEntity>,
        subtotalBdt: Long,
        division: String,
        promo: PromoResult?
    ): Long {
        if (cartItems.isEmpty() || subtotalBdt <= 0L) return 0L
        val config = _deliveryConfig.value
        val qtyFree = cartItems.any { it.freeDeliveryMinQty > 0 && it.quantity >= it.freeDeliveryMinQty }
        val amtFree = config.freeDeliveryMinAmountBdt > 0L && subtotalBdt >= config.freeDeliveryMinAmountBdt
        if (qtyFree || amtFree || promo?.freeShipping == true) return 0L
        return config.feeForDivision(division)
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
        val rawDeliveryFee = computeEffectiveDeliveryFee(cartItems, subtotal, address.division, promo)
        val total = (subtotal - discount + rawDeliveryFee).coerceAtLeast(0L)

        val normalizedSender = ""
        val normalizedTrx = "COD${System.currentTimeMillis().toString().takeLast(6)}"
        val paymentStatus = "Pay on Delivery"
        val initialOrderStatus = "Pending"

        val datePart = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
        val suffix = (1000..9999).random()
        val orderId = "SPR-BD-$datePart-$suffix"

        val maskedAccount = "Cash on Delivery"

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

        try {
            val uid = prefs.firebaseUid.first() ?: userEmail.replace(".", "_")
            val district = address.districtThana.split(",").firstOrNull()?.trim().orEmpty()
            val remoteDto = RemoteOrderDto(
                orderId = orderId,
                uid = uid,
                customerEmail = userEmail,
                customer = RemoteCustomerDto(
                    name = address.recipientName,
                    phone = address.phone,
                    address = address.formattedAddress,
                    district = district,
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
        } catch (_: Exception) {
            // Order remains safely stored locally in Room if offline
        }

        Result.success(order)
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
            val token = resolveValidIdToken(forceRefresh = false)
            val resp = rtdb.patchOrderStatus(orderId, mapOf("status" to "Cancelled", "updatedAt" to now), token)
            if (resp.code() == 401) {
                resolveValidIdToken(forceRefresh = true)?.let { fresh ->
                    rtdb.patchOrderStatus(orderId, mapOf("status" to "Cancelled", "updatedAt" to now), fresh)
                }
            }
        } catch (_: Exception) {
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
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (comment.isBlank()) return@withContext Result.failure(IllegalArgumentException("Please write a short review comment."))
        val safeRating = rating.coerceIn(1, 5)
        val trimmedComment = comment.trim()
        val now = System.currentTimeMillis()
        val uid = prefs.firebaseUid.first()
        var token = resolveValidIdToken(forceRefresh = false)

        if (!uid.isNullOrBlank() && !token.isNullOrBlank()) {
            val reviewKey = "rev_${now}_${(100..999).random()}"
            val remoteDto = RemoteReviewDto(
                uid = uid,
                userName = userName.trim().ifBlank { "Customer" },
                rating = safeRating,
                comment = trimmedComment,
                verifiedPurchase = true,
                createdAt = now
            )
            try {
                var resp = rtdb.putReview(productId, reviewKey, token, remoteDto)
                if (resp.code() == 401) {
                    token = resolveValidIdToken(forceRefresh = true) ?: token
                    resp = rtdb.putReview(productId, reviewKey, token, remoteDto)
                }
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
            }
        }

        reviewDao.insertReview(
            ReviewEntity(
                productId = productId,
                userName = userName.trim().ifBlank { "Customer" },
                userEmail = userEmail,
                rating = safeRating,
                comment = trimmedComment,
                verifiedPurchase = true,
                createdAt = now
            )
        )
        val allReviews = reviewDao.getReviewsForProduct(productId)
        if (allReviews.isNotEmpty()) {
            val avg = (allReviews.sumOf { it.rating }.toDouble() / allReviews.size)
            val rounded = (avg * 10.0).roundToInt() / 10.0
            productDao.updateRatingSummary(productId, rounded, allReviews.size)
        }
        Result.success(Unit)
    }

    // --- Helpers ---

    private fun serializeVariantPrices(map: Map<String, Double>?): String {
        if (map.isNullOrEmpty()) return "{}"
        return try {
            val obj = JSONObject()
            map.forEach { (k, v) ->
                if (k.isNotBlank() && v > 0.0) obj.put(k, v)
            }
            obj.toString()
        } catch (_: Throwable) {
            "{}"
        }
    }

    private fun parseFirebaseAuthError(rawJson: String, fallback: String): String {
        return try {
            val msg = JSONObject(rawJson).optJSONObject("error")?.optString("message").orEmpty()
            when {
                msg.contains("EMAIL_EXISTS", ignoreCase = true) ->
                    "An account already exists with this email. Sign in with Password, Continue with Google, or reset your password."
                msg.contains("INVALID_LOGIN_CREDENTIALS", ignoreCase = true) ||
                    msg.contains("INVALID_PASSWORD", ignoreCase = true) ||
                    msg.contains("EMAIL_NOT_FOUND", ignoreCase = true) ->
                    "Invalid email or password. If you signed up with Google, tap Continue with Google or use Forgot Password."
                msg.contains("CREDENTIAL_TOO_OLD_LOGIN_AGAIN", ignoreCase = true) ->
                    "Please sign out and sign in again to complete this security action."
                msg.contains("FEDERATED_USER_ID_ALREADY_LINKED", ignoreCase = true) ->
                    "This Google account is already linked to another SPR Zone BD account."
                msg.contains("OPERATION_NOT_ALLOWED", ignoreCase = true) ->
                    "Google Sign-In is not yet enabled in Firebase Authentication Console."
                msg.contains("USER_DISABLED", ignoreCase = true) ->
                    "This customer account has been disabled."
                msg.contains("TOO_MANY_ATTEMPTS", ignoreCase = true) ->
                    "Too many attempts. Please wait a moment and try again."
                msg.contains("WEAK_PASSWORD", ignoreCase = true) ->
                    "Password must be at least 6 characters."
                msg.isNotBlank() -> msg
                else -> fallback
            }
        } catch (_: Throwable) {
            fallback
        }
    }

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
