package com.ledgerhub.data.reconciliation

import com.ledgerhub.domain.invoice.Money
import com.ledgerhub.domain.reconciliation.BankTransaction
import com.ledgerhub.domain.reconciliation.BankTransactionRepository

/**
 * Source bancaire simulée (US-18) — même parti pris que
 * [MockDirectoryRepository][com.ledgerhub.data.directory.MockDirectoryRepository] pour l'annuaire
 * DGFIP en US-09 : aucun connecteur bancaire n'existe encore, et attendre qu'il existe
 * empêcherait de livrer et d'éprouver l'écran de lettrage.
 *
 * Le jeu couvre volontairement les trois cas que l'interface doit savoir présenter :
 * un encaissement au centime près, un encaissement amputé de frais bancaires (écart négatif),
 * un trop-perçu (écart positif), et un décaissement — que le lettrage doit refuser.
 */
class MockBankTransactionRepository(
    private val transactions: List<BankTransaction> = DEMO_STATEMENT,
) : BankTransactionRepository {

    override suspend fun fetchTransactions(): Result<List<BankTransaction>> =
        Result.success(transactions)

    companion object {
        val DEMO_STATEMENT: List<BankTransaction> = listOf(
            BankTransaction(
                id = "TX-2026-0091",
                label = "VIR SEPA BOULANGERIE MOREAU",
                amount = Money(24_000),
                valueDateIso = "2026-09-01",
                counterparty = "Boulangerie Moreau SARL",
            ),
            // Virement amputé des frais de la banque emettrice : l'ecart est le cas courant,
            // pas l'exception — l'ecran doit le signaler sans interdire le lettrage.
            BankTransaction(
                id = "TX-2026-0092",
                label = "VIR SEPA ATELIER DUPONT",
                amount = Money(11_850),
                valueDateIso = "2026-08-31",
                counterparty = "Atelier Dupont",
            ),
            BankTransaction(
                id = "TX-2026-0093",
                label = "VIR SEPA CABINET LEROY",
                amount = Money(60_500),
                valueDateIso = "2026-08-30",
                counterparty = "Cabinet Leroy",
            ),
            // Decaissement : present pour que le refus du lettrage sur un debit soit observable
            // dans l'application, et pas seulement en test.
            BankTransaction(
                id = "TX-2026-0094",
                label = "PRLV URSSAF",
                amount = Money(-32_400),
                valueDateIso = "2026-08-29",
                counterparty = "URSSAF",
            ),
        )
    }
}
