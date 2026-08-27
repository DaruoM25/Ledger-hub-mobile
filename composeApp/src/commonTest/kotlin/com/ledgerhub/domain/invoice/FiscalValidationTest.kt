package com.ledgerhub.domain.invoice

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class FiscalValidationTest {

    private val issuer = Party("Vendeur SARL", "123456789", "12345678900012")
    private val recipient = Party("Client SAS", "987654321", "98765432100045")

    private fun invoice(status: InvoiceStatus) = Invoice(
        number = "F-2026-042",
        issueDate = "2026-08-01",
        issuer = issuer,
        recipient = recipient,
        lines = listOf(InvoiceLine("Conseil", quantity = 1, unitPriceHt = Money(10000), vatRate = VatRate.TAUX_NORMAL)),
        status = status,
    )

    // ── Immutabilité fiscale (US-01) ─────────────────────────────────────────

    @Test
    fun validateEditable_draftInvoice_isValid() {
        assertIs<ValidationResult.Valid>(FiscalValidation.validateEditable(invoice(InvoiceStatus.DRAFT)))
    }

    @Test
    fun validateEditable_validatedInvoice_isInvalid() {
        assertIs<ValidationResult.Invalid>(FiscalValidation.validateEditable(invoice(InvoiceStatus.VALIDATED)))
    }

    @Test
    fun validateEditable_sentInvoice_isInvalid() {
        assertIs<ValidationResult.Invalid>(FiscalValidation.validateEditable(invoice(InvoiceStatus.SENT)))
    }

    @Test
    fun validateEditable_paidInvoice_isInvalid() {
        assertIs<ValidationResult.Invalid>(FiscalValidation.validateEditable(invoice(InvoiceStatus.PAID)))
    }

    @Test
    fun validateEditable_cancelledInvoice_isInvalid() {
        assertIs<ValidationResult.Invalid>(FiscalValidation.validateEditable(invoice(InvoiceStatus.CANCELLED)))
    }

    // ── Non-régression centime-strict (US-01) — jamais de flottant sur les montants ──

    @Test
    fun totalTtc_neverUsesFloatingPointRounding_evenOnRepeatingDecimals() {
        // 33.33 HT * 3 lignes à 20% de TVA : un calcul en Double introduirait une dérive
        // d'arrondi typique (33.33 * 1.20 = 39.996 en flottant) ; en centimes entiers, exact.
        val line = InvoiceLine("Conseil", quantity = 1, unitPriceHt = Money(3333), vatRate = VatRate.TAUX_NORMAL)
        val invoice = Invoice(
            number = "F-2026-100",
            issueDate = "2026-08-01",
            issuer = issuer,
            recipient = recipient,
            lines = List(3) { line },
        )
        // HT = 99.99, TVA 20% = 20.00 (round half up sur 19.998), TTC = 119.99.
        assertEquals(Money(9999), invoice.totalHt)
        assertEquals(Money(2000), invoice.totalVat)
        assertEquals(Money(11999), invoice.totalTtc)
    }

    @Test
    fun totalTtc_ofMixedVatRates_sumsExactlyToLineTotals() {
        val invoice = Invoice(
            number = "F-2026-101",
            issueDate = "2026-08-01",
            issuer = issuer,
            recipient = recipient,
            lines = listOf(
                InvoiceLine("Conseil", quantity = 1, unitPriceHt = Money(10000), vatRate = VatRate.TAUX_NORMAL), // 120.00
                InvoiceLine("Livre", quantity = 1, unitPriceHt = Money(2000), vatRate = VatRate.TAUX_REDUIT),     // 21.10
            ),
        )
        assertEquals(Money(12000 + 2110), invoice.totalTtc)
    }

    @Test
    fun validateSiren_nineDigits_isValid() {
        assertIs<ValidationResult.Valid>(FiscalValidation.validateSiren("123456789"))
    }

    @Test
    fun validateSiren_tooShort_isInvalid() {
        assertIs<ValidationResult.Invalid>(FiscalValidation.validateSiren("12345678"))
    }

    @Test
    fun validateSiren_tooLong_isInvalid() {
        assertIs<ValidationResult.Invalid>(FiscalValidation.validateSiren("1234567890"))
    }

    @Test
    fun validateSiren_containsLetters_isInvalid() {
        assertIs<ValidationResult.Invalid>(FiscalValidation.validateSiren("12345678A"))
    }

    @Test
    fun validateSiret_fourteenDigits_isValid() {
        assertIs<ValidationResult.Valid>(FiscalValidation.validateSiret("12345678900012"))
    }

    @Test
    fun validateSiret_wrongLength_isInvalid() {
        assertIs<ValidationResult.Invalid>(FiscalValidation.validateSiret("123456789"))
    }

    @Test
    fun validateSiret_containsSpaces_isInvalid() {
        assertIs<ValidationResult.Invalid>(FiscalValidation.validateSiret("1234 5678 9000 12"))
    }
}
