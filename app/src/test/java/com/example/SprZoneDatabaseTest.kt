package com.example

import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.CatalogSeeder
import com.example.data.local.SessionPreferences
import com.example.data.local.SprZoneDatabase
import com.example.data.model.ProductEntity
import com.example.data.repository.SprZoneRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URI

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SprZoneDatabaseTest {

    private lateinit var context: Context
    private lateinit var db: SprZoneDatabase
    private lateinit var repo: SprZoneRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, SprZoneDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = SprZoneRepository(
            db = db,
            prefs = SessionPreferences(context),
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

        val upd = repo.updateProfile(email, "Rahim Ahmed", "+8801811223344", "Dhaka buyer")
        assertTrue(upd.isSuccess)
        assertEquals("Rahim Ahmed", upd.getOrThrow().fullName)
        assertEquals("01811223344", upd.getOrThrow().phone)
        assertEquals("Dhaka buyer", upd.getOrThrow().bio)

        val addrRes = repo.saveAddress(
            userEmail = email,
            label = "Home",
            recipientName = "Rahim Ahmed",
            phone = "01811223344",
            division = "Dhaka",
            districtThana = "Banani",
            streetAddress = "Road 11, House 4",
            makeDefault = true
        )
        assertTrue(addrRes.isSuccess)
        val addrId = addrRes.getOrThrow()

        val editAddrRes = repo.updateAddress(
            userEmail = email,
            addressId = addrId,
            label = "Office",
            recipientName = "Rahim Office",
            phone = "01811223344",
            division = "Chattogram",
            districtThana = "Agrabad",
            streetAddress = "Suite 302",
            makeDefault = true
        )
        assertTrue(editAddrRes.isSuccess)
        val savedAddr = db.addressDao().observeAddresses(email).first().single()
        assertEquals("Office", savedAddr.label)
        assertEquals("Chattogram", savedAddr.division)

        repo.deleteAddress(addrId)
        assertTrue(db.addressDao().observeAddresses(email).first().isEmpty())

        val delProfile = repo.deleteProfile(email)
        assertTrue(delProfile.isSuccess)
        assertNull(db.userDao().getUserByEmail(email))
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
