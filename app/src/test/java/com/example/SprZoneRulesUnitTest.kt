package com.example

import com.example.data.model.PaymentMethod
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
    fun bdMobileNumbersAndTrxIdsValidateProperly() {
        assertTrue(StoreConstants.isValidBdPhone("01712345678"))
        assertTrue(StoreConstants.isValidBdPhone("+8801812345678"))
        assertEquals("01812345678", StoreConstants.normalizeBdPhone("+8801812345678"))
        assertFalse(StoreConstants.isValidBdPhone("01212345678"))
        assertFalse(StoreConstants.isValidBdPhone("0171234"))

        assertTrue(StoreConstants.isValidTrxId(PaymentMethod.BKASH, "BL74K9M2PQ"))
        assertFalse(StoreConstants.isValidTrxId(PaymentMethod.BKASH, "SHORT"))
        assertTrue(StoreConstants.isValidTrxId(PaymentMethod.NAGAD, "73AB92CD"))
        assertTrue(StoreConstants.isValidTrxId(PaymentMethod.ROCKET, "492817364"))
    }
}
