package com.ledgerhub.domain.creditnote

import com.ledgerhub.domain.invoice.Money
import com.ledgerhub.domain.invoice.Party
import kotlin.test.Test
import kotlin.test.assertFailsWith

class CreditNoteTest {

    private val issuer = Party("Vendeur SARL", "123456789", "12345678900012")
    private val recipient = Party("Client SAS", "987654321", "98765432100045")

    private fun creditNote(
        reason: String = "Erreur de facturation",
        totalHt: Money = Money(-10000),
        totalVat: Money = Money(-2000),
        totalTtc: Money = Money(-12000),
    ) = CreditNote(
        number = "AV-2026-001",
        issueDate = "2026-08-06",
        invoiceId = "F-2026-001",
        reason = reason,
        issuer = issuer,
        recipient = recipient,
        totalHt = totalHt,
        totalVat = totalVat,
        totalTtc = totalTtc,
    )

    // ── Invariant : motif obligatoire ────────────────────────────────────────

    @Test
    fun creditNote_withBlankReason_throws() {
        assertFailsWith<IllegalArgumentException> { creditNote(reason = "") }
    }

    @Test
    fun creditNote_withWhitespaceOnlyReason_throws() {
        assertFailsWith<IllegalArgumentException> { creditNote(reason = "   ") }
    }

    // ── Règle fiscale : montants toujours négatifs ou nuls ───────────────────

    @Test
    fun creditNote_withPositiveTotalHt_throws() {
        assertFailsWith<IllegalArgumentException> { creditNote(totalHt = Money(10000)) }
    }

    @Test
    fun creditNote_withPositiveTotalVat_throws() {
        assertFailsWith<IllegalArgumentException> { creditNote(totalVat = Money(2000)) }
    }

    @Test
    fun creditNote_withPositiveTotalTtc_throws() {
        assertFailsWith<IllegalArgumentException> { creditNote(totalTtc = Money(12000)) }
    }

    @Test
    fun creditNote_withValidNegativeAmounts_andReason_isConstructed() {
        // Ne doit pas lever — sert de contrôle positif face aux tests d'invariant ci-dessus.
        creditNote()
    }
}
