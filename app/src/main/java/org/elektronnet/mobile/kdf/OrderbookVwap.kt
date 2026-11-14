package org.elektronnet.mobile.kdf

import java.math.BigDecimal
import java.math.RoundingMode

/** Volume-weighted price and the base volume that produced it. */
data class BookVwap(val price: BigDecimal, val baseVolume: BigDecimal)

private val ZERO = BigDecimal.ZERO

/**
 * Volume-weighted average price across both sides of the book.
 *
 * The volume is normalised to the base coin first. The daemon reports a bid
 * row's `maxvolume` in the rel coin, so a naive sum of the two sides would add
 * BTC to ELEK and return a meaningless price. Rows without a volume, or with a
 * non-positive price, do not contribute to the depth.
 */
fun orderbookVwap(asks: List<OrderEntry>, bids: List<OrderEntry>, scale: Int = 12): BookVwap? {
    var baseVolume = ZERO
    var notional = ZERO
    for (entry in asks.asSequence() + bids.asSequence()) {
        if (entry.price <= ZERO || entry.maxVolume <= ZERO) continue
        val rel = entry.relVolume.takeIf { it > ZERO } ?: (entry.price * entry.maxVolume)
        if (rel <= ZERO) continue
        baseVolume += entry.maxVolume
        notional += rel
    }
    if (baseVolume <= ZERO || notional <= ZERO) return null
    return BookVwap(
        price = notional.divide(baseVolume, scale, RoundingMode.HALF_UP).stripTrailingZeros(),
        baseVolume = baseVolume.stripTrailingZeros(),
    )
}
