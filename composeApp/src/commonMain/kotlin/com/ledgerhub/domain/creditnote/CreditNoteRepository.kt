package com.ledgerhub.domain.creditnote

/** Abstraction de la persistance/backend — la couche présentation ne connaît que ce contrat. */
interface CreditNoteRepository {
    /**
     * Émet l'avoir et annule la facture d'origine, en un seul geste atomique.
     * Échoue avec [InvoiceAlreadyCreditedException] si la facture porte déjà un avoir.
     */
    suspend fun submitCreditNote(creditNote: CreditNote): Result<Unit>

    /** Liste des avoirs existants. */
    suspend fun fetchCreditNotes(): Result<List<CreditNote>>

    /** Avoir déjà émis pour cette facture, s'il existe — alimente le blocage et la mention croisée. */
    suspend fun findByInvoiceNumber(invoiceNumber: String): Result<CreditNote?>

    /**
     * Prochain numéro séquentiel de l'exercice [year], au format `AV-AAAA-NNNN`.
     * Voir [CreditNoteNumbering].
     */
    suspend fun nextNumberForYear(year: Int): Result<String>
}

/**
 * Émission refusée : la facture a déjà été annulée. Une facture ne peut porter qu'un seul avoir
 * total — un second créditerait deux fois la même créance.
 */
class InvoiceAlreadyCreditedException(
    val invoiceNumber: String,
    val creditNoteNumber: String,
) : Exception("Cette facture a déjà été annulée par l'avoir $creditNoteNumber")
