package com.ledgerhub.presentation.reconciliation

/** Intentions de l'écran de Rapprochement Bancaire (UDF/MVI) — US-18. */
sealed interface ReconciliationIntent {
    /** L'utilisateur désigne une écriture bancaire. Re-sélectionner la même la désélectionne. */
    data class SelectTransaction(val transactionId: String) : ReconciliationIntent

    /** L'utilisateur désigne une facture à solder. Re-sélectionner la même la désélectionne. */
    data class SelectInvoice(val invoiceNumber: String) : ReconciliationIntent

    /** Lettrage du couple sélectionné. Sans effet tant que les deux côtés ne sont pas choisis. */
    data object PerformMatch : ReconciliationIntent

    /** Abandon de la sélection en cours, sans rien écrire. */
    data object ClearSelection : ReconciliationIntent

    /** L'UI a consommé le message courant (erreur ou confirmation). */
    data object MessageShown : ReconciliationIntent
}
