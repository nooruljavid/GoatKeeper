package com.goatkeeper.app

import com.goatkeeper.app.util.kiddingDate
import com.goatkeeper.app.util.isKid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test
import java.time.LocalDate

class FarmUtilsTest {
    @Test
    fun testKiddingDateCalculation() {
        // Standard 150 day gestation
        val matingDate = "2024-01-01"
        val expected = "2024-05-30" // Jan(31)+Feb(29)+Mar(31)+Apr(30)+May(29) = 150 days
        assertEquals(expected, kiddingDate(matingDate))
    }

    @Test
    fun testIsKid() {
        val today = LocalDate.now()
        val fiveMonthsAgo = today.minusMonths(5).toString()
        val sevenMonthsAgo = today.minusMonths(7).toString()
        
        assertTrue(isKid(fiveMonthsAgo))
        assertFalse(isKid(sevenMonthsAgo))
    }
}
