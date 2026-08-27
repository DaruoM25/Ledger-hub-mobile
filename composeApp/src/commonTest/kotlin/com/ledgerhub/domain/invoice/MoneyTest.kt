package com.ledgerhub.domain.invoice

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MoneyTest {

    // ── Arrondi TVA (round half up, au centime) ─────────────────────────────

    @Test
    fun vatFor_exactAmount_noRoundingNeeded() {
        // 100.00 € HT * 20% = 20.00 €
        assertEquals(2000L, Money(10000).vatFor(2000).cents)
    }

    @Test
    fun vatFor_roundsHalfUp_atExactlyHalfACent() {
        // 0.125 € de TVA doit s'arrondir à 0.13 € (round half up), pas 0.12 (banker's rounding)
        // 12.50 HT * 1% = 0.125 -> arrondi à 13 centimes
        assertEquals(13L, Money(1250).vatFor(100).cents)
    }

    @Test
    fun vatFor_roundsDown_belowHalfACent() {
        // 10.10 HT * 20% = 2.02 exactement
        assertEquals(202L, Money(1010).vatFor(2000).cents)
    }

    @Test
    fun vatFor_zeroRate_isZero() {
        assertEquals(0L, Money(9999).vatFor(VatRate.EXONERE.basisPoints).cents)
    }

    // ── Parsing de saisie utilisateur ────────────────────────────────────────

    @Test
    fun parseAmountToCents_wholeNumber_parsesCorrectly() {
        assertEquals(1200L, parseAmountToCents("12"))
    }

    @Test
    fun parseAmountToCents_twoDecimals_withDot_parsesCorrectly() {
        assertEquals(1299L, parseAmountToCents("12.99"))
    }

    @Test
    fun parseAmountToCents_twoDecimals_withComma_parsesCorrectly() {
        assertEquals(1299L, parseAmountToCents("12,99"))
    }

    @Test
    fun parseAmountToCents_oneDecimal_padsWithZero() {
        assertEquals(1250L, parseAmountToCents("12.5"))
    }

    @Test
    fun parseAmountToCents_blank_returnsNull() {
        assertNull(parseAmountToCents(""))
    }

    @Test
    fun parseAmountToCents_negative_returnsNull() {
        assertNull(parseAmountToCents("-12.50"))
    }

    @Test
    fun parseAmountToCents_tooManyDecimals_returnsNull() {
        assertNull(parseAmountToCents("12.999"))
    }

    @Test
    fun parseAmountToCents_notANumber_returnsNull() {
        assertNull(parseAmountToCents("abc"))
    }
}
