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
    /**
     * `true` si la facture porte les pénalités de retard légales entre professionnels — mention
     * de l'article L.441-10 du Code de commerce (3 fois le taux d'intérêt légal + indemnité
     * forfaitaire de 40 €). Défaut `true` : la mention est obligatoire en B2B, et son omission
     * est sanctionnable ; une facture à un particulier peut la retirer explicitement.
     *
     * Porté par la facture et **non par [Letterhead]** : c'est un choix par pièce, gelé à
     * l'émission au même titre que [facturX]. Le loger dans l'en-tête permettrait de réimprimer
     * une facture déjà déposée sous d'autres mentions légales.
     */
    val applyB2bPenalties: Boolean = true,
    /** Identifiant SIREN client spécifique (réforme 2026). Défaut tiré du destinataire ou vide. */
    val clientSiren: String = "",
    /** Nature de l'opération (biens, services, mixte) — mention obligatoire 2026. */
    val natureOperation: NatureOperation = NatureOperation.PRESTATION_SERVICES,
    /** Option de paiement de la TVA d'après les débits. */
    val optionTvaDebit: Boolean = false,
    /** Indicateur de transmission en flux e-Reporting (B2C / International). */
    val isEReporting: Boolean = false,
    /** Adresse de livraison spécifique si différente de l'adresse client. */
    val deliveryAddress: DeliveryAddress = DeliveryAddress(),
) {
    init {
        require(lines.isNotEmpty()) { "Une facture doit contenir au moins une ligne de facturation" }
    }

    val vatBreakdown: List<VatBreakdown> get() = computeVatBreakdown(lines)
    val totalHt: Money get() = totalHtOf(lines)
    val totalVat: Money get() = totalVatOf(lines)
    val totalTtc: Money get() = totalTtcOf(lines)

    /**
     * Immutabilité fiscale. Modifiable au brouillon, et de nouveau après un
     * [rejet de plateforme][InvoiceStatus.REJECTED] : la facture n'est alors jamais entrée dans
     * le circuit légal, sa correction puis son redépôt sont la procédure attendue. Un
     * [refus acheteur][InvoiceStatus.REFUSED], lui, porte sur une facture qui a circulé.
     */
    val isEditable: Boolean
        get() = status == InvoiceStatus.DRAFT || status == InvoiceStatus.REJECTED

    /**
     * Annulation comptable par avoir — voir [com.ledgerhub.domain.creditnote.CreditNote].
     * Dérivé de la machine d'états plutôt que réénuméré : une seule table décrit le cycle de vie,
     * et cette propriété ne peut donc pas diverger d'elle.
     */
    val isCancellableByCreditNote: Boolean
        get() = InvoiceStatusTransition.isAllowed(status, InvoiceStatus.CANCELLED)
}

/**
 * Suppression réservée au brouillon. Volontairement plus stricte que [Invoice.isEditable] :
 * une facture rejetée redevient corrigeable, mais la supprimer effacerait son passage — et sa
 * trace d'audit — du dossier.
 */
fun canDelete(invoice: Invoice): Boolean = invoice.status == InvoiceStatus.DRAFT
