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
import com.example.data.remote.FirebaseTokenRefreshResponse
import com.example.data.remote.FirebaseTokenRefreshService
import com.example.data.remote.RemoteAppNotificationDto
import com.example.data.remote.RemoteCouponDto
import com.example.data.remote.RemoteCustomerAddressDto
import com.example.data.remote.RemoteCustomerProfileDto
import com.example.data.remote.RemoteDeliveryConfigDto
import com.example.data.remote.RemoteDeliveryZoneDto
import com.example.data.remote.RemoteOrderDto
import com.example.data.remote.RemotePaymentSettingsDto
import com.example.data.remote.RemoteProductDto
import com.example.data.remote.RemoteSmsRecordDto
import com.example.data.remote.SmsVerifyApiResponse
import com.example.data.remote.SprZoneRtdbService
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
}

private class InMemoryFirebaseAuthService : FirebaseAuthRestService {
    private data class StoredAuthUser(val uid: String, val email: String, val password: String)
    private val usersByEmail = mutableMapOf<String, StoredAuthUser>()
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
        usersByEmail[email] = StoredAuthUser(uid, email, body.password)
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
        if (found == null || found.password != body.password) {
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
                localId = found.uid
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
    var deliveryConfig: RemoteDeliveryConfigDto? = RemoteDeliveryConfigDto()

    override suspend fun getProducts(): Response<Map<String, RemoteProductDto>?> = Response.success(emptyMap())
    override suspend fun getDeliveryConfig(): Response<RemoteDeliveryConfigDto?> = Response.success(deliveryConfig)
    override suspend fun getPaymentSettings(): Response<RemotePaymentSettingsDto?> = Response.success(RemotePaymentSettingsDto())
    override suspend fun getCoupons(): Response<Map<String, RemoteCouponDto>?> = Response.success(emptyMap())
    override suspend fun getAppNotifications(): Response<Map<String, RemoteAppNotificationDto>?> = Response.success(emptyMap())

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
    override suspend fun putOrder(orderId: String, order: RemoteOrderDto): Response<RemoteOrderDto?> = Response.success(order)
    override suspend fun getOrder(orderId: String): Response<RemoteOrderDto?> = Response.success(null)
    override suspend fun getAllOrders(): Response<Map<String, RemoteOrderDto>?> = Response.success(emptyMap())
    override suspend fun patchOrderStatus(orderId: String, patch: Map<String, Any>): Response<Map<String, Any>?> = Response.success(patch)
    override suspend fun verifyViaExternalSmsApi(
        url: String,
        sender: String,
        trxId: String,
        amount: Long,
        provider: String,
        apiKey: String
    ): Response<SmsVerifyApiResponse?> = Response.success(null)
}
