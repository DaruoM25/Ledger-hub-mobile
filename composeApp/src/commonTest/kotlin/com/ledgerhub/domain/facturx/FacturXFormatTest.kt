package com.ledgerhub.domain.facturx

import com.ledgerhub.domain.invoice.Money
import com.ledgerhub.domain.invoice.VatRate
import kotlin.test.Test
import kotlin.test.assertEquals

/** Formats imposés par le profil BASIC : décimal `xs:decimal`, date 102, pourcentage. */
class FacturXFormatTest {

    // ── Montants ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun amount_rendersTwoDecimals_withADot() {
        // Séparateur point quelle que soit la langue : c'est le format du schéma, pas un affichage.
        assertEquals("2200.00", FacturXFormat.amount(Money(220_000)))
        assertEquals("0.05", FacturXFormat.amount(Money(5)))
        assertEquals("0.00", FacturXFormat.amount(Money.ZERO))
    }

    @Test
    fun amount_keepsTheSignOfACreditNote() {
        assertEquals("-2200.00", FacturXFormat.amount(Money(-220_000)))
        assertEquals("-0.01", FacturXFormat.amount(Money(-1)))
    }

    @Test
    fun amount_padsTheCentsToTwoDigits() {
        assertEquals("1.05", FacturXFormat.amount(Money(105)))
        assertEquals("1.50", FacturXFormat.amount(Money(150)))
    }

    @Test
    fun amount_handlesLargeValues_withoutGrouping() {
        // Aucun séparateur de milliers : xs:decimal ne l'admet pas.
        assertEquals("1234567.89", FacturXFormat.amount(Money(123_456_789)))
    }

    // ── Dates ────────────────────────────────────────────────────────────────────────────────

    @Test
    fun date102_stripsTheIsoSeparators() {
        assertEquals("20260712", FacturXFormat.date102("2026-07-12"))
        assertEquals("20260101", FacturXFormat.date102("2026-01-01"))
    }

    @Test
    fun date102_toleratesAnUnexpectedInput_ratherThanThrowing() {
        // Le générateur ne doit jamais lever : la validation XSD signalera un format aberrant.
        assertEquals("", FacturXFormat.date102(""))
        assertEquals("nimportequoi", FacturXFormat.date102("nimportequoi"))
    }

    // ── Taux ─────────────────────────────────────────────────────────────────────────────────

    @Test
    fun percent_rendersEveryStatutoryRate() {
        assertEquals("20.00", FacturXFormat.percent(VatRate.TAUX_NORMAL.basisPoints))
        assertEquals("10.00", FacturXFormat.percent(VatRate.TAUX_INTERMEDIAIRE.basisPoints))
        assertEquals("5.50", FacturXFormat.percent(VatRate.TAUX_REDUIT.basisPoints))
        assertEquals("2.10", FacturXFormat.percent(VatRate.TAUX_PARTICULIER.basisPoints))
        assertEquals("0.00", FacturXFormat.percent(VatRate.EXONERE.basisPoints))
    }

    @Test
    fun currency_isEuro() {
        assertEquals("EUR", FacturXFormat.CURRENCY)
    }
}
