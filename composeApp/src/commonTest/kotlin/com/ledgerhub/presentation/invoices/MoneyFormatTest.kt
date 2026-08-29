package com.ledgerhub.presentation.invoices

import com.ledgerhub.domain.i18n.AppLanguage
import com.ledgerhub.domain.invoice.Money
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Tests QA (Skill 2) du formateur monétaire partagé. Le séparateur de milliers attendu est
 * l'espace insécable U+00A0 ([NON_BREAKING_SPACE]) — référencé via la constante partagée
 * plutôt que par un littéral, pour éviter toute ambiguïté d'encodage dans ce fichier.
 */
class MoneyFormatTest {

    private val sp = NON_BREAKING_SPACE

    @Test
    fun nonBreakingSpaceConstant_isCodePointA0() {
        assertEquals(0xA0, sp.single().code)
    }

    @Test
    fun zero_isFormattedWithTwoDecimals() {
        assertEquals("0,00$sp€", Money(0).formatEuros())
    }

    @Test
    fun amountUnderOneEuro_keepsLeadingZero() {
        assertEquals("0,07$sp€", Money(7).formatEuros())
    }

    @Test
    fun simpleAmount_usesCommaDecimalSeparator() {
        assertEquals("12,50$sp€", Money(1_250).formatEuros())
    }

    @Test
    fun thousands_areGroupedWithNonBreakingSpace() {
        assertEquals("1${sp}234,56$sp€", Money(123_456).formatEuros())
    }

    @Test
    fun millions_areGroupedEveryThreeDigits() {
        assertEquals("1${sp}234${sp}567,89$sp€", Money(123_456_789).formatEuros())
    }

    @Test
    fun negativeAmount_keepsLeadingSign() {
        assertEquals("-1${sp}000,00$sp€", Money(-100_000).formatEuros())
    }

    // ── Anglais : symbole € en tête, virgule milliers, point décimal ─────────────

    @Test
    fun english_zero_isFormattedWithTwoDecimals() {
        assertEquals("€0.00", formatMoney(0, AppLanguage.EN))
    }

    @Test
    fun english_amountUnderOneEuro_keepsLeadingZero() {
        assertEquals("€0.07", formatMoney(7, AppLanguage.EN))
    }

    @Test
    fun english_thousands_areGroupedWithComma_dotDecimal() {
        assertEquals("€1,234.56", formatMoney(123_456, AppLanguage.EN))
        assertEquals("€1,234.56", Money(123_456).format(AppLanguage.EN))
    }

    @Test
    fun english_millions_areGroupedEveryThreeDigits() {
        assertEquals("€1,234,567.89", formatMoney(123_456_789, AppLanguage.EN))
    }

    @Test
    fun english_negativeAmount_signBeforeSymbol() {
        assertEquals("-€1,000.00", formatMoney(-100_000, AppLanguage.EN))
    }

    @Test
    fun sameCentsValue_rendersDifferentlyPerLanguage_withoutFloatDrift() {
        val cents = 123_456L
        assertEquals("1${sp}234,56$sp€", formatMoney(cents, AppLanguage.FR))
        assertEquals("€1,234.56", formatMoney(cents, AppLanguage.EN))
        // Preuve d'arithmétique entière : la partie décimale est exactement `cents % 100`.
        assertEquals(56L, cents % 100)
    }
}
