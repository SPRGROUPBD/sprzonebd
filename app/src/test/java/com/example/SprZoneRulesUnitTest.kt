package com.example

import com.example.data.model.DeliveryConfigSummary
import com.example.data.model.PaymentMethod
import com.example.data.model.ProductEntity
import com.example.data.model.StoreConstants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SprZoneRulesUnitTest {

    @Test
    fun confirmedAndLaterOrdersCannotBeCancelled() {
        assertTrue(StoreConstants.canCancelOrder("Pending"))
        assertTrue(StoreConstants.canCancelOrder("Placed"))

        assertFalse(StoreConstants.canCancelOrder("Confirmed"))
        assertFalse(StoreConstants.canCancelOrder("Processing"))
        assertFalse(StoreConstants.canCancelOrder("Packed"))
        assertFalse(StoreConstants.canCancelOrder("Out for Delivery"))
        assertFalse(StoreConstants.canCancelOrder("Delivered"))
        assertFalse(StoreConstants.canCancelOrder("Cancelled"))
    }

    @Test
    fun bdMobileNumbersAndCodPaymentValidateProperly() {
        assertTrue(StoreConstants.isValidBdPhone("01712345678"))
        assertTrue(StoreConstants.isValidBdPhone("+8801812345678"))
        assertEquals("01812345678", StoreConstants.normalizeBdPhone("+8801812345678"))
        assertFalse(StoreConstants.isValidBdPhone("01212345678"))
        assertFalse(StoreConstants.isValidBdPhone("0171234"))

        assertEquals(listOf(PaymentMethod.COD), PaymentMethod.entries)
        assertEquals("Cash on Delivery", PaymentMethod.COD.displayName)
    }

    @Test
    fun all64BdDistrictsAreMappedByDivision() {
        assertEquals(8, StoreConstants.BD_DIVISIONS.size)
        val allDistricts = StoreConstants.BD_DISTRICTS_BY_DIVISION.values.flatten()
        assertEquals(64, allDistricts.size)
        assertEquals(64, allDistricts.distinct().size)
        assertEquals(listOf("Habiganj", "Moulvibazar", "Sunamganj", "Sylhet"), StoreConstants.districtsForDivision("Sylhet"))
        assertTrue(StoreConstants.districtsForDivision("Dhaka").contains("Gazipur"))
        assertTrue(StoreConstants.districtsForDivision("Chattogram").contains("Cox's Bazar"))
    }

    @Test
    fun variantWisePriceAndDeliveryZoneConfigResolveCorrectly() {
        val product = ProductEntity(
            id = "shirt_1",
            name = "Cotton Polo",
            category = "Fashion & Apparel",
            brand = "SPR",
            sellerName = "SPR Store",
            priceBdt = 550L,
            originalPriceBdt = 900L,
            discountPercent = 39,
            rating = 4.9,
            reviewCount = 40,
            stock = 20,
            isFlashSale = false,
            isFeatured = true,
            imageKey = "fashion",
            description = "Desc",
            specsSummary = "Specs",
            variantsCsv = "Red / S, Red / XL",
            variantColorsCsv = "Red, Blue",
            variantSizesCsv = "S, XL",
            variantPricesJson = """{"Red / XL":720,"Blue / S":590}""",
            freeDeliveryMinQty = 2
        )

        assertEquals(listOf("Red", "Blue"), product.colors)
        assertEquals(listOf("S", "XL"), product.sizes)
        assertEquals("Red / S", product.defaultVariantLabel())
        assertEquals(720L, product.priceForVariant("Red / XL"))
        assertEquals(590L, product.priceForVariant("Blue / S"))
        assertEquals(550L, product.priceForVariant("Red / S"))
        assertEquals(20, product.discountPercentForPrice(720L))

        val config = DeliveryConfigSummary(
            freeDeliveryMinAmountBdt = 4000L,
            divisionRates = mapOf("Dhaka" to 55L, "Rajshahi" to 115L)
        )
        assertEquals(55L, config.feeForDivision("Dhaka"))
        assertEquals(115L, config.feeForDivision("Rajshahi"))
        assertEquals(120L, config.feeForDivision("Khulna"))
    }
}
