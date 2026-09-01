package com.ledgerhub.domain.reconciliation

import com.ledgerhub.domain.invoice.Invoice
import com.ledgerhub.domain.invoice.InvoiceLine
import com.ledgerhub.domain.invoice.InvoiceStatus
import com.ledgerhub.domain.invoice.Money
import com.ledgerhub.domain.invoice.Party
import com.ledgerhub.domain.invoice.VatRate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Niveau 1 (US-18) — écart entre un encaissement et la facture qu'il solde.
 *
 * Le calcul tient en une soustraction, mais c'est **la convention de signe** qui compte : elle
 * décide de la lecture d'un écart à l'écran, et un signe inversé se lirait à contresens du langage
 * comptable. Elle est donc figée ici.
 */
class AmountDeltaTest {

    private val issuer = Party("Cabinet LedgerHub", "820329331", "82032933100027")
    private val recipient = Party("Boulangerie Moreau SARL", "784102336", "78410233600021")

    /** Facture à 240,00 € TTC : 2 × 100,00 € HT au taux normal (20 %). */
    private fun invoice(status: InvoiceStatus = InvoiceStatus.DEPOSITED) = Invoice(
        number = "FAC-2026-0301",
        issueDate = "2026-08-31",
        issuer = issuer,
        recipient = recipient,
        lines = listOf(InvoiceLine("Conseil réglementaire", 2, Money(10_000), VatRate.TAUX_NORMAL)),
        status = status,
    )

    private fun credit(cents: Long) = BankTransaction(
        id = "TX-2026-0091",
        label = "VIR SEPA BOULANGERIE MOREAU",
        amount = Money(cents),
        valueDateIso = "2026-09-01",
    )

    @Test
    fun anExactPayment_leavesNoDelta() {
        val subject = invoice()
        assertEquals(Money(24_000), subject.totalTtc, "Hypothèse du test : la facture vaut 240,00 € TTC")

        assertEquals(Money.ZERO, AmountDelta.between(credit(24_000), subject))
        assertFalse(AmountDelta.hasMismatch(credit(24_000), subject))
    }

    /** Virement amputé des frais de la banque émettrice : il reste dû, l'écart est négatif. */
    @Test
    fun anUnderPayment_yieldsANegativeDelta() {
        val delta = AmountDelta.between(credit(23_850), invoice())

        assertEquals(Money(-150), delta)
        assertTrue(AmountDelta.hasMismatch(credit(23_850), invoice()))
    }

    /** Trop-perçu : le client a versé davantage, l'écart est positif. */
    @Test
    fun anOverPayment_yieldsAPositiveDelta() {
        val delta = AmountDelta.between(credit(25_000), invoice())

        assertEquals(Money(1_000), delta)
        assertTrue(AmountDelta.hasMismatch(credit(25_000), invoice()))
    }

    /**
     * La convention est `transaction - facture`, jamais l'inverse : c'est ce qui fait qu'un
     * découvert se lit en négatif. Un test explicite, car une inversion resterait indolore pour
     * tous les autres et fausserait chaque écart affiché.
     */
    @Test
    fun theSignConvention_isTransactionMinusInvoice() {
        val under = AmountDelta.between(credit(20_000), invoice())
        val over = AmountDelta.between(credit(30_000), invoice())

        assertTrue(under < Money.ZERO, "Un paiement insuffisant doit produire un écart négatif")
        assertTrue(over > Money.ZERO, "Un trop-perçu doit produire un écart positif")
    }
}
