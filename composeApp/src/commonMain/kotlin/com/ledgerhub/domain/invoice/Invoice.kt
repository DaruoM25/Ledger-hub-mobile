package com.ledgerhub.domain.invoice

data class Invoice(
    val number: String,
    val issueDate: String,
    val issuer: Party,
    val recipient: Party,
    val lines: List<InvoiceLine>,
    val status: InvoiceStatus = InvoiceStatus.DRAFT,
    /** Numéro du devis d'origine si cette facture a été générée par conversion — piste d'audit fiscale. */
    val sourceQuoteId: String? = null,
    /**
     * Date d'échéance de paiement (AAAA-MM-JJ). Optionnel (défaut vide) tant que tout le parc de
     * factures héritées n'en porte pas ; le formulaire de saisie la renseigne systématiquement.
     */
    val dueDate: String = "",
    /**
     * `true` si la facture doit être générée/archivée au format légal Factur-X (JSON + PDF unifié).
     * Défaut `true` : la conformité 2026 est la norme, le formulaire permet de la désactiver au cas par cas.
     */
    val facturX: Boolean = true,
) {
    init {
        require(lines.isNotEmpty()) { "Une facture doit contenir au moins une ligne de facturation" }
    }

    val vatBreakdown: List<VatBreakdown> get() = computeVatBreakdown(lines)
    val totalHt: Money get() = totalHtOf(lines)
    val totalVat: Money get() = totalVatOf(lines)
    val totalTtc: Money get() = totalTtcOf(lines)

    /** Immutabilité fiscale : une facture non-brouillon ne peut plus être modifiée ni supprimée. */
    val isEditable: Boolean get() = status == InvoiceStatus.DRAFT

    /**
     * Seule une facture finalisée/verrouillée (ni Brouillon, ni déjà Annulée) peut être
     * annulée comptablement par un avoir — voir [com.ledgerhub.domain.creditnote.CreditNote].
     */
    val isCancellableByCreditNote: Boolean
        get() = status == InvoiceStatus.VALIDATED || status == InvoiceStatus.SENT || status == InvoiceStatus.PAID
}

/** Règle métier explicite — pas de suppression hors statut Brouillon. */
fun canDelete(invoice: Invoice): Boolean = invoice.status == InvoiceStatus.DRAFT
