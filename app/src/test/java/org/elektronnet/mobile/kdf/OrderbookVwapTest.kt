package org.elektronnet.mobile.kdf

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OrderbookVwapTest {

    private fun entry(price: String, base: String, rel: String? = null): OrderEntry {
        val p = BigDecimal(price)
        val b = BigDecimal(base)
        return OrderEntry(
            price = p,
            maxVolume = b,
            minVolume = BigDecimal.ZERO,
            relVolume = rel?.let(::BigDecimal) ?: (p * b),
        )
    }

    @Test
    fun testBidVolumeIsNormalisedToBase() {
        // 0.000108 tBTC at 0.000000054 is 2,000 tELEK, not 0.000108 tELEK.
        val book = orderbookVwap(
            asks = emptyList(),
            bids = listOf(entry(price = "0.000000054", base = "2000", rel = "0.000108")),
        )
        assertEquals(0, book!!.price.compareTo(BigDecimal("0.000000054")))
        assertEquals(0, book.baseVolume.compareTo(BigDecimal("2000")))
    }

    @Test
    fun testBothSidesAreWeightedInBaseUnits() {
        // Ask: 100 base for 100 rel. Bid: 200 base for 100 rel.
        // Notional 200 over 300 base, not the unweighted (1.0 + 0.5) / 2.
        val book = orderbookVwap(
            asks = listOf(entry(price = "1", base = "100")),
            bids = listOf(entry(price = "0.5", base = "200")),
        )
        assertEquals(0, book!!.baseVolume.compareTo(BigDecimal("300")))
        assertEquals(0.666666666667, book.price.toDouble(), 1e-12)
    }

    @Test
    fun testRowsWithoutPriceOrVolumeAreSkipped() {
        val book = orderbookVwap(
            asks = listOf(entry(price = "0", base = "100"), entry(price = "2", base = "0")),
            bids = listOf(entry(price = "1", base = "10")),
        )
        assertEquals(0, book!!.baseVolume.compareTo(BigDecimal("10")))
        assertEquals(0, book.price.compareTo(BigDecimal("1")))
    }

    @Test
    fun testEmptyBookHasNoVwap() {
        assertNull(orderbookVwap(emptyList(), emptyList()))
    }
}
