package com.example

import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.CatalogSeeder
import com.example.data.local.SessionPreferences
import com.example.data.local.SprZoneDatabase
import com.example.data.model.CartItemEntity
import com.example.data.model.ProductEntity
import com.example.data.remote.FirebaseAuthRequest
import com.example.data.remote.FirebaseAuthResponse
import com.example.data.remote.FirebaseAuthRestService
import com.example.data.remote.FirebaseDeleteAccountRequest
import com.example.data.remote.FirebaseLookupProviderDto
import com.example.data.remote.FirebaseLookupRequest
import com.example.data.remote.FirebaseLookupResponse
import com.example.data.remote.FirebaseLookupUserDto
import com.example.data.remote.FirebaseSendOobCodeRequest
import com.example.data.remote.FirebaseSendOobCodeResponse
import com.example.data.remote.FirebaseSignInWithIdpRequest
import com.example.data.remote.FirebaseSignInWithIdpResponse
import com.example.data.remote.FirebaseTokenRefreshResponse
import com.example.data.remote.FirebaseTokenRefreshService
import com.example.data.remote.FirebaseUpdateAccountRequest
import com.example.data.remote.RemoteAppNotificationDto
import com.example.data.remote.RemoteCouponDto
import com.example.data.remote.RemoteCustomerAddressDto
import com.example.data.remote.RemoteCustomerProfileDto
import com.example.data.remote.RemoteDeliveryConfigDto
import com.example.data.remote.RemoteDeliveryZoneDto
import com.example.data.remote.RemoteOrderDto
import com.example.data.remote.RemotePaymentSettingsDto
import com.example.data.remote.RemoteProductDto
import com.example.data.remote.RemoteReviewDto
import com.example.data.remote.RemoteSmsRecordDto
import com.example.data.remote.SmsVerifyApiResponse
import com.example.data.remote.SprZoneRtdbService
import com.example.data.repository.GooglePendingLinkException
import com.example.data.repository.SprZoneRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.Response
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URI

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SprZoneDatabaseTest {

    private lateinit var context: Context
    private lateinit var db: SprZoneDatabase
    private lateinit var fakeAuth: InMemoryFirebaseAuthService
    private lateinit var fakeRtdb: InMemorySprZoneRtdbService
    private lateinit var repo: SprZoneRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, SprZoneDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        fakeAuth = InMemoryFirebaseAuthService()
        fakeRtdb = InMemorySprZoneRtdbService()
        repo = SprZoneRepository(
            db = db,
            prefs = SessionPreferences(context),
            rtdb = fakeRtdb,
            authApi = fakeAuth,
            tokenApi = InMemoryTokenRefreshService(),
            filesDir = context.filesDir
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun noDemoProductsOrReviewsAreSeeded() {
        assertTrue(CatalogSeeder.initialProducts().isEmpty())
        assertTrue(CatalogSeeder.initialReviews().isEmpty())
    }

    @Test
    fun userCanAddEditAndDeleteProfileAndAddresses() = runTest {
        val email = "customer@example.com"
        val reg = repo.registerUser("Rahim Uddin", email, "01711223344", "secret123")
        assertTrue(reg.isSuccess)
        assertTrue(fakeRtdb.customers.containsKey("uid_1"))

        val upd = repo.updateProfile(email, "Rahim Ahmed", "+8801811223344", "Dhaka buyer")
        assertTrue(upd.isSuccess)
        assertEquals("Rahim Ahmed", upd.getOrThrow().fullName)
        assertEquals("01811223344", upd.getOrThrow().phone)
        assertEquals("Dhaka buyer", upd.getOrThrow().bio)
        assertEquals("Rahim Ahmed", fakeRtdb.customers["uid_1"]?.name)

        val addrRes = repo.saveAddress(
            userEmail = email,
            label = "Home",
            recipientName = "Rahim Ahmed",
            phone = "01811223344",
            division = "Dhaka",
            districtThana = "Dhaka, Banani",
            streetAddress = "Road 11, House 4",
            makeDefault = true
        )
        assertTrue(addrRes.isSuccess)
        val addrId = addrRes.getOrThrow()
        assertEquals(1, fakeRtdb.customers["uid_1"]?.addresses?.size)

        val editAddrRes = repo.updateAddress(
            userEmail = email,
            addressId = addrId,
            label = "Office",
            recipientName = "Rahim Office",
            phone = "01811223344",
            division = "Chattogram",
            districtThana = "Chattogram, Agrabad",
            streetAddress = "Suite 302",
            makeDefault = true
        )
        assertTrue(editAddrRes.isSuccess)
        val savedAddr = db.addressDao().observeAddresses(email).first().single()
        assertEquals("Office", savedAddr.label)
        assertEquals("Chattogram", savedAddr.division)

        // Sign out, clear local Room cache, and log back in — customer & address must restore from RTDB!
        repo.signOut()
        db.addressDao().deleteAllForUser(email)
        db.userDao().deleteUserByEmail(email)
        val relogin = repo.loginUser(email, "secret123")
        assertTrue(relogin.isSuccess)
        assertEquals("Rahim Ahmed", relogin.getOrThrow().fullName)
        val restoredAddresses = db.addressDao().observeAddresses(email).first()
        assertEquals(1, restoredAddresses.size)
        assertEquals("Chattogram", restoredAddresses.single().division)

        repo.deleteAddress(restoredAddresses.single().id)
        assertTrue(db.addressDao().observeAddresses(email).first().isEmpty())
        assertTrue(fakeRtdb.customers["uid_1"]?.addresses.isNullOrEmpty())

        val delProfile = repo.deleteProfile(email)
        assertTrue(delProfile.isSuccess)
        assertNull(db.userDao().getUserByEmail(email))
        assertFalse(fakeRtdb.customers.containsKey("uid_1"))
    }

    @Test
    fun qtyWiseFreeDeliveryAndZoneRatesComputeCorrectly() = runTest {
        fakeRtdb.deliveryConfig = RemoteDeliveryConfigDto(
            freeDeliveryMinAmount = 5000.0,
            zones = mapOf(
                "sylhet" to RemoteDeliveryZoneDto(name = "Sylhet", rate = 130.0)
            )
        )
        repo.syncRemoteCatalogAndSettings()

        val regularItem = CartItemEntity(
            userEmail = "u@test.com",
            productId = "p1",
            productName = "Cotton Tee",
            unitPriceBdt = 650L,
            originalPriceBdt = 800L,
            imageKey = "fashion",
            selectedVariant = "Red / L",
            quantity = 1,
            freeDeliveryMinQty = 2
        )
        assertEquals(130L, repo.computeEffectiveDeliveryFee(listOf(regularItem), 650L, "Sylhet", null))

        // Increasing quantity to freeDeliveryMinQty (2) unlocks 0 delivery fee
        val qtyQualifiedItem = regularItem.copy(quantity = 2)
        assertEquals(0L, repo.computeEffectiveDeliveryFee(listOf(qtyQualifiedItem), 1300L, "Sylhet", null))

        // Subtotal >= freeDeliveryMinAmount (5000) also unlocks 0 delivery fee
        val noQtyLimitItem = regularItem.copy(freeDeliveryMinQty = 0, quantity = 1)
        assertEquals(0L, repo.computeEffectiveDeliveryFee(listOf(noQtyLimitItem), 5000L, "Sylhet", null))
    }

    @Test
    fun persistOrSanitizeImageUrlRejectsOversizedNonDataUriAndKeepsDbSafe() = runTest {
        val hugePayload = "https://example.com/" + "a".repeat(2_500_000)
        val sanitized = repo.persistOrSanitizeImageUrl("prod_huge_url", hugePayload)
        assertEquals("", sanitized)

        db.productDao().insertAll(
            listOf(
                ProductEntity(
                    id = "prod_huge_url",
                    name = "Safe Remote Product",
                    category = "Electronics & Gadgets",
                    brand = "SPR Official",
                    sellerName = "SPR Official Store",
                    priceBdt = 990L,
                    originalPriceBdt = 1200L,
                    discountPercent = 18,
                    rating = 4.8,
                    reviewCount = 12,
                    stock = 10,
                    isFlashSale = false,
                    isFeatured = true,
                    imageKey = "electronics",
                    imageUrl = sanitized,
                    description = "Test",
                    specsSummary = "Specs",
                    variantsCsv = "Standard"
                )
            )
        )

        val rows = db.productDao().observeAllProducts().first()
        assertEquals(1, rows.size)
        assertEquals("", rows.first().imageUrl)
    }

    @Test
    fun persistOrSanitizeImageUrlCachesInlineBase64ToLocalFileUri() {
        val bmp = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
        val baos = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.PNG, 100, baos)
        bmp.recycle()

        val base64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
        val dataUri = "data:image/png;base64,$base64"

        val resultUri = repo.persistOrSanitizeImageUrl("prod_inline_1", dataUri)
        assertTrue("Expected short file URI, got: $resultUri", resultUri.startsWith("file:"))
        assertTrue("Expected URI length <= 2048, got ${resultUri.length}", resultUri.length <= 2048)

        val cachedFile = File(URI(resultUri))
        assertTrue("Expected cached file to exist", cachedFile.exists() && cachedFile.length() > 0L)
    }

    @Test
    fun productsWithoutReviewsDefaultToZeroRatingAndLiveReviewsRecalculate() = runTest {
        fakeRtdb.products["prod_no_rev"] = RemoteProductDto(
            name = "Fresh Gadget",
            category = "Electronics & Gadgets",
            price = 1500.0,
            stock = 5
        )
        repo.syncRemoteCatalogAndSettings()

        val initialProduct = db.productDao().getProductById("prod_no_rev")!!
        assertEquals(0.0, initialProduct.rating, 0.01)
        assertEquals(0, initialProduct.reviewCount)

        val reg = repo.registerUser("Nadia Islam", "nadia@example.com", "01711223355", "secret123")
        assertTrue(reg.isSuccess)

        val revRes = repo.submitReview("prod_no_rev", "Nadia Islam", "nadia@example.com", 4, "Very nice build quality!")
        assertTrue(revRes.isSuccess)
        assertEquals(1, fakeRtdb.reviews["prod_no_rev"]?.size)

        repo.syncReviewsAndRatings()
        val updatedProduct = db.productDao().getProductById("prod_no_rev")!!
        assertEquals(4.0, updatedProduct.rating, 0.01)
        assertEquals(1, updatedProduct.reviewCount)
    }

    @Test
    fun syncUserOrdersUsesScopedUidQuery() = runTest {
        val reg = repo.registerUser("Tariq", "tariq@example.com", "01812345678", "secret123")
        assertTrue(reg.isSuccess)
        fakeRtdb.orders["SPR-BD-OWN"] = RemoteOrderDto(orderId = "SPR-BD-OWN", uid = "uid_1", customerEmail = "tariq@example.com", total = 900L, status = "Confirmed")
        fakeRtdb.orders["SPR-BD-OTHER"] = RemoteOrderDto(orderId = "SPR-BD-OTHER", uid = "uid_999", customerEmail = "other@example.com", total = 500L, status = "Pending")

        repo.syncUserOrdersFromRemote("tariq@example.com")
        val myOrders = db.orderDao().observeUserOrders("tariq@example.com").first()
        assertEquals(1, myOrders.size)
        assertEquals("SPR-BD-OWN", myOrders.single().orderId)
    }

    @Test
    fun forgotPasswordSendsResetAndValidatesEmail() = runTest {
        val email = "resetuser@example.com"
        assertTrue(repo.registerUser("Reset User", email, "01711002233", "secret123").isSuccess)

        val invalidRes = repo.sendPasswordResetEmail("not-an-email")
        assertTrue(invalidRes.isFailure)

        val validRes = repo.sendPasswordResetEmail(email)
        assertTrue(validRes.isSuccess)
        assertEquals(listOf(email), fakeAuth.resetEmailsSent)
    }

    @Test
    fun googleSignInConflictLinksWithPasswordAndSupportsDualLogin() = runTest {
        val email = "dual@example.com"
        assertTrue(repo.registerUser("Dual User", email, "01711998877", "pass1234").isSuccess)
        repo.signOut()

        // First Google sign-in attempt on existing password email triggers GooglePendingLinkException
        val firstGoogle = repo.signInWithGoogleIdToken("gid_tok_dual@example.com")
        assertTrue(firstGoogle.isFailure)
        val pendingEx = firstGoogle.exceptionOrNull() as? GooglePendingLinkException
        assertTrue("Expected GooglePendingLinkException", pendingEx != null)
        assertEquals(email, pendingEx?.email)

        // Linking with password signs user in and binds google.com provider
        val linkedRes = repo.loginAndLinkGoogle(email, "pass1234", pendingEx!!.googleIdToken)
        assertTrue(linkedRes.isSuccess)
        val providers = repo.getLinkedProvidersForCurrentUser()
        assertTrue(providers.contains("password"))
        assertTrue(providers.contains("google.com"))

        // Signing out and logging in purely with Google now succeeds cleanly!
        repo.signOut()
        val secondGoogle = repo.signInWithGoogleIdToken("gid_tok_dual@example.com")
        assertTrue(secondGoogle.isSuccess)
        assertEquals("Dual User", secondGoogle.getOrThrow().fullName)
    }

    @Test
    fun googleUserCanLinkPasswordForEmailSignIn() = runTest {
        val googleLogin = repo.signInWithGoogleIdToken("gid_tok_newgoogle@example.com|Nusrat Jahan")
        assertTrue(googleLogin.isSuccess)
        assertEquals("Nusrat Jahan", googleLogin.getOrThrow().fullName)
        assertEquals(listOf("google.com"), repo.getLinkedProvidersForCurrentUser())

        val linkPwd = repo.linkPasswordToCurrentAccount("newpass99")
        assertTrue(linkPwd.isSuccess)
        val providers = linkPwd.getOrThrow()
        assertTrue(providers.contains("google.com"))
        assertTrue(providers.contains("password"))

        repo.signOut()
        val emailLogin = repo.loginUser("newgoogle@example.com", "newpass99")
        assertTrue(emailLogin.isSuccess)
        assertEquals("Nusrat Jahan", emailLogin.getOrThrow().fullName)
    }

    @Test
    fun variantPricesResolvesFirebaseSafeKeys() {
        val product = ProductEntity(
            id = "p_var",
            name = "Polo Shirt",
            category = "Fashion & Apparel",
            brand = "SPR",
            sellerName = "SPR Official",
            priceBdt = 550L,
            originalPriceBdt = 700L,
            discountPercent = 21,
            rating = 0.0,
            reviewCount = 0,
            stock = 20,
            isFlashSale = false,
            isFeatured = true,
            imageKey = "fashion",
            imageUrl = "",
            description = "Test",
            specsSummary = "Specs",
            variantsCsv = "Red / S, Blue / XL, 1.5L",
            variantColorsCsv = "Red, Blue",
            variantSizesCsv = "S, XL",
            variantPricesJson = """{"Red | S":590,"Blue | XL":650,"1·5L":820}"""
        )
        assertEquals(590L, product.priceForVariant("Red / S"))
        assertEquals(650L, product.priceForVariant("Blue / XL"))
        assertEquals(820L, product.priceForVariant("1.5L"))
        assertEquals(550L, product.priceForVariant("Green / M"))
    }
}

private class InMemoryFirebaseAuthService : FirebaseAuthRestService {
    private data class StoredAuthUser(
        val uid: String,
        val email: String,
        val password: String?,
        val displayName: String = "",
        val providers: MutableSet<String> = mutableSetOf()
    )

    private val usersByEmail = mutableMapOf<String, StoredAuthUser>()
    val resetEmailsSent = mutableListOf<String>()
    private var counter = 0

    override suspend fun signUp(apiKey: String, body: FirebaseAuthRequest): Response<FirebaseAuthResponse?> {
        val email = body.email.trim().lowercase()
        if (usersByEmail.containsKey(email)) {
            return Response.error(
                400,
                """{"error":{"message":"EMAIL_EXISTS"}}""".toResponseBody("application/json".toMediaType())
            )
        }
        counter += 1
        val uid = "uid_$counter"
        usersByEmail[email] = StoredAuthUser(uid, email, body.password, providers = mutableSetOf("password"))
        return Response.success(
            FirebaseAuthResponse(
                idToken = "tok_$uid",
                email = email,
                refreshToken = "ref_$uid",
                localId = uid
            )
        )
    }

    override suspend fun signInWithPassword(apiKey: String, body: FirebaseAuthRequest): Response<FirebaseAuthResponse?> {
        val email = body.email.trim().lowercase()
        val found = usersByEmail[email]
        if (found == null || found.password == null || found.password != body.password) {
            return Response.error(
                400,
                """{"error":{"message":"INVALID_LOGIN_CREDENTIALS"}}""".toResponseBody("application/json".toMediaType())
            )
        }
        return Response.success(
            FirebaseAuthResponse(
                idToken = "tok_${found.uid}",
                email = email,
                refreshToken = "ref_${found.uid}",
                localId = found.uid,
                displayName = found.displayName
            )
        )
    }

    override suspend fun signInWithIdp(
        apiKey: String,
        body: FirebaseSignInWithIdpRequest
    ): Response<FirebaseSignInWithIdpResponse?> {
        val decodedPost = java.net.URLDecoder.decode(body.postBody, "UTF-8")
        val rawToken = decodedPost.substringAfter("id_token=").substringBefore("&")
        val payload = rawToken.removePrefix("gid_tok_")
        val gEmail = payload.substringBefore("|").trim().lowercase().ifBlank { "google@example.com" }
        val gName = payload.substringAfter("|", "").trim().ifBlank { gEmail.substringBefore("@") }

        val linkedUid = body.idToken?.removePrefix("tok_")
        if (!linkedUid.isNullOrBlank()) {
            val currentUser = usersByEmail.values.firstOrNull { it.uid == linkedUid }
                ?: return Response.error(400, """{"error":{"message":"INVALID_ID_TOKEN"}}""".toResponseBody("application/json".toMediaType()))
            currentUser.providers.add("google.com")
            return Response.success(
                FirebaseSignInWithIdpResponse(
                    localId = currentUser.uid,
                    email = currentUser.email,
                    displayName = currentUser.displayName.ifBlank { gName },
                    idToken = "tok_${currentUser.uid}",
                    refreshToken = "ref_${currentUser.uid}",
                    providerId = "google.com",
                    needConfirmation = false
                )
            )
        }

        val existing = usersByEmail[gEmail]
        if (existing != null) {
            if ("google.com" !in existing.providers) {
                return Response.success(
                    FirebaseSignInWithIdpResponse(
                        email = existing.email,
                        needConfirmation = true,
                        verifiedProvider = existing.providers.toList()
                    )
                )
            }
            return Response.success(
                FirebaseSignInWithIdpResponse(
                    localId = existing.uid,
                    email = existing.email,
                    displayName = existing.displayName.ifBlank { gName },
                    idToken = "tok_${existing.uid}",
                    refreshToken = "ref_${existing.uid}",
                    providerId = "google.com",
                    needConfirmation = false
                )
            )
        }

        counter += 1
        val uid = "uid_$counter"
        usersByEmail[gEmail] = StoredAuthUser(
            uid = uid,
            email = gEmail,
            password = null,
            displayName = gName,
            providers = mutableSetOf("google.com")
        )
        return Response.success(
            FirebaseSignInWithIdpResponse(
                localId = uid,
                email = gEmail,
                displayName = gName,
                idToken = "tok_$uid",
                refreshToken = "ref_$uid",
                providerId = "google.com",
                needConfirmation = false
            )
        )
    }

    override suspend fun sendOobCode(
        apiKey: String,
        body: FirebaseSendOobCodeRequest
    ): Response<FirebaseSendOobCodeResponse?> {
        val email = body.email.trim().lowercase()
        resetEmailsSent.add(email)
        return Response.success(FirebaseSendOobCodeResponse(email = email))
    }

    override suspend fun lookupAccount(
        apiKey: String,
        body: FirebaseLookupRequest
    ): Response<FirebaseLookupResponse?> {
        val uid = body.idToken.removePrefix("tok_")
        val found = usersByEmail.values.firstOrNull { it.uid == uid }
            ?: return Response.success(FirebaseLookupResponse(users = emptyList()))
        return Response.success(
            FirebaseLookupResponse(
                users = listOf(
                    FirebaseLookupUserDto(
                        localId = found.uid,
                        email = found.email,
                        displayName = found.displayName,
                        providerUserInfo = found.providers.map { pid ->
                            FirebaseLookupProviderDto(providerId = pid, email = found.email)
                        }
                    )
                )
            )
        )
    }

    override suspend fun updateAccount(
        apiKey: String,
        body: FirebaseUpdateAccountRequest
    ): Response<FirebaseAuthResponse?> {
        val uid = body.idToken.removePrefix("tok_")
        val found = usersByEmail.values.firstOrNull { it.uid == uid }
            ?: return Response.error(400, """{"error":{"message":"INVALID_ID_TOKEN"}}""".toResponseBody("application/json".toMediaType()))
        val updated = found.copy(
            password = body.password ?: found.password,
            displayName = body.displayName ?: found.displayName
        ).apply {
            if (!body.password.isNullOrBlank()) providers.add("password")
        }
        usersByEmail[found.email] = updated
        return Response.success(
            FirebaseAuthResponse(
                idToken = "tok_${updated.uid}",
                email = updated.email,
                refreshToken = "ref_${updated.uid}",
                localId = updated.uid,
                displayName = updated.displayName
            )
        )
    }

    override suspend fun deleteAccount(apiKey: String, body: FirebaseDeleteAccountRequest): Response<Unit> {
        val uid = body.idToken.removePrefix("tok_")
        usersByEmail.entries.removeIf { it.value.uid == uid }
        return Response.success(Unit)
    }
}

private class InMemoryTokenRefreshService : FirebaseTokenRefreshService {
    override suspend fun refreshToken(
        apiKey: String,
        grantType: String,
        refreshToken: String
    ): Response<FirebaseTokenRefreshResponse?> {
        val uid = refreshToken.removePrefix("ref_")
        return Response.success(
            FirebaseTokenRefreshResponse(
                id_token = "tok_$uid",
                refresh_token = refreshToken,
                user_id = uid
            )
        )
    }
}

private class InMemorySprZoneRtdbService : SprZoneRtdbService {
    val customers = mutableMapOf<String, RemoteCustomerProfileDto>()
    val products = mutableMapOf<String, RemoteProductDto>()
    val reviews = mutableMapOf<String, MutableMap<String, RemoteReviewDto>>()
    val orders = mutableMapOf<String, RemoteOrderDto>()
    var deliveryConfig: RemoteDeliveryConfigDto? = RemoteDeliveryConfigDto()

    override suspend fun getProducts(): Response<Map<String, RemoteProductDto>?> = Response.success(products.toMap())
    override suspend fun getDeliveryConfig(): Response<RemoteDeliveryConfigDto?> = Response.success(deliveryConfig)
    override suspend fun getPaymentSettings(): Response<RemotePaymentSettingsDto?> = Response.success(RemotePaymentSettingsDto())
    override suspend fun getCoupons(): Response<Map<String, RemoteCouponDto>?> = Response.success(emptyMap())
    override suspend fun getAppNotifications(): Response<Map<String, RemoteAppNotificationDto>?> = Response.success(emptyMap())

    override suspend fun getReviews(): Response<Map<String, Map<String, RemoteReviewDto>>?> =
        Response.success(reviews.mapValues { it.value.toMap() })

    override suspend fun putReview(
        productId: String,
        reviewId: String,
        authToken: String,
        review: RemoteReviewDto
    ): Response<RemoteReviewDto?> {
        val map = reviews.getOrPut(productId) { mutableMapOf() }
        map[reviewId] = review
        return Response.success(review)
    }

    override suspend fun getCustomerProfile(uid: String, authToken: String): Response<RemoteCustomerProfileDto?> =
        Response.success(customers[uid])

    override suspend fun putCustomerProfile(
        uid: String,
        authToken: String,
        profile: RemoteCustomerProfileDto
    ): Response<RemoteCustomerProfileDto?> {
        val existingAddresses = customers[uid]?.addresses
        val stored = if (profile.addresses == null && existingAddresses != null) {
            profile.copy(addresses = existingAddresses)
        } else {
            profile
        }
        customers[uid] = stored
        return Response.success(stored)
    }

    override suspend fun patchCustomerProfile(
        uid: String,
        authToken: String,
        patch: Map<String, Any>
    ): Response<Map<String, Any>?> {
        val cur = customers[uid] ?: RemoteCustomerProfileDto()
        customers[uid] = cur.copy(
            name = (patch["name"] as? String) ?: cur.name,
            email = (patch["email"] as? String) ?: cur.email,
            phone = (patch["phone"] as? String) ?: cur.phone,
            bio = (patch["bio"] as? String) ?: cur.bio
        )
        return Response.success(patch)
    }

    override suspend fun deleteCustomerProfile(uid: String, authToken: String): Response<Unit> {
        customers.remove(uid)
        return Response.success(Unit)
    }

    override suspend fun putCustomerAddress(
        uid: String,
        addrId: String,
        authToken: String,
        address: RemoteCustomerAddressDto
    ): Response<RemoteCustomerAddressDto?> {
        val cur = customers[uid] ?: RemoteCustomerProfileDto()
        val nextAddresses = cur.addresses.orEmpty().toMutableMap()
        nextAddresses[addrId] = address
        customers[uid] = cur.copy(addresses = nextAddresses)
        return Response.success(address)
    }

    override suspend fun patchCustomerAddresses(
        uid: String,
        authToken: String,
        addressesPatch: Map<String, Any>
    ): Response<Map<String, Any>?> {
        val cur = customers[uid] ?: return Response.success(emptyMap())
        val next = cur.addresses.orEmpty().toMutableMap()
        addressesPatch.forEach { (k, v) ->
            val parts = k.split("/")
            if (parts.size == 2 && parts[1] == "isDefault" && v is Boolean) {
                next[parts[0]]?.let { existing -> next[parts[0]] = existing.copy(isDefault = v) }
            }
        }
        customers[uid] = cur.copy(addresses = next)
        return Response.success(addressesPatch)
    }

    override suspend fun deleteCustomerAddress(uid: String, addrId: String, authToken: String): Response<Unit> {
        val cur = customers[uid] ?: return Response.success(Unit)
        val next = cur.addresses.orEmpty().toMutableMap()
        next.remove(addrId)
        customers[uid] = cur.copy(addresses = next)
        return Response.success(Unit)
    }

    override suspend fun getSmsInboxRecord(trxId: String): Response<RemoteSmsRecordDto?> = Response.success(null)
    override suspend fun markSmsRecordMatched(trxId: String, patch: Map<String, String>): Response<Map<String, String>?> = Response.success(patch)
    override suspend fun putOrder(orderId: String, order: RemoteOrderDto): Response<RemoteOrderDto?> {
        orders[orderId] = order
        return Response.success(order)
    }
    override suspend fun getOrder(orderId: String, authToken: String?): Response<RemoteOrderDto?> =
        Response.success(orders[orderId])
    override suspend fun getOrdersByUid(orderBy: String, equalTo: String, authToken: String): Response<Map<String, RemoteOrderDto>?> {
        val cleanUid = equalTo.trim('"')
        return Response.success(orders.filterValues { it.uid == cleanUid })
    }
    override suspend fun patchOrderStatus(orderId: String, patch: Map<String, Any>, authToken: String?): Response<Map<String, Any>?> =
        Response.success(patch)
    override suspend fun verifyViaExternalSmsApi(
        url: String,
        sender: String,
        trxId: String,
        amount: Long,
        provider: String,
        apiKey: String
    ): Response<SmsVerifyApiResponse?> = Response.success(null)
}
