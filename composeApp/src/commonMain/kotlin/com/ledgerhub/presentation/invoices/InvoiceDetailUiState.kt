package com.ledgerhub.presentation.invoices

import com.ledgerhub.domain.audit.AuditEntry
import com.ledgerhub.domain.invoice.Invoice
import com.ledgerhub.domain.invoice.InvoiceStatus
import com.ledgerhub.domain.invoice.InvoiceStatusTransition
import com.ledgerhub.domain.invoice.VatBreakdown

/**
 * État immuable de l'écran de détail d'une facture chargée depuis le `LedgerRepository`
 * distant (US-02). Distinct de [com.ledgerhub.presentation.invoicedetail.InvoiceDetailUiState]
 * (module Avoir, données locales).
 *
 * Le verrouillage des actions dérive exclusivement des règles métier portées par [Invoice]
 * ([Invoice.isEditable], [Invoice.isCancellableByCreditNote]) — jamais recalculé ici.
 */
data class InvoiceDetailUiState(
    val isLoading: Boolean = true,
    val invoice: Invoice? = null,
    val errorMessage: String? = null,
    val notFound: Boolean = false,
    /** Numéro de l'avoir qui annule cette facture, s'il en existe un — mention croisée US-05. */
    val creditNoteNumber: String? = null,
    /** Piste d'Audit Fiable de cette facture, de la plus ancienne transition à la plus récente. */
    val auditTrail: List<AuditEntry> = emptyList(),
    /** Transition en cours de saisie : la boîte de motif est ouverte pour ce statut cible. */
    val pendingTransition: InvoiceStatus? = null,
    val transitionReason: String = "",
    val transitionError: String? = null,
    val isTransitioning: Boolean = false,
) {
    /** Ventilation TVA (base HT / TVA par taux) — vide tant que la facture n'est pas chargée. */
    val vatBreakdown: List<VatBreakdown>
        get() = invoice?.vatBreakdown ?: emptyList()

    /** Modification autorisée uniquement sur un brouillon (immutabilité fiscale). */
    val canEdit: Boolean
        get() = invoice?.isEditable == true

    /** Annulation par avoir : facture finalisée (Validée/Envoyée/Payée), pas encore annulée. */
    val canCancelByCreditNote: Boolean
        get() = invoice?.isCancellableByCreditNote == true

    /** Facture annulée → écran en lecture seule, toutes les actions sont neutralisées. */
    val isLocked: Boolean
        get() = invoice?.status == InvoiceStatus.CANCELLED

    /**
     * Facture verrouillée par l'immutabilité fiscale : tout statut hors Brouillon (Validée,
     * Envoyée, Payée, Annulée). Distinct de [isLocked], qui ne vise que l'annulation.
     * Sert l'affordance de verrouillage — cadenas et atténuation — et non la règle métier
     * elle-même, qui reste portée par [Invoice.isEditable].
     */
    val isFiscallyLocked: Boolean
        get() = invoice?.isEditable == false

    /**
     * Transitions proposées à l'écran. Dérivées de la machine d'états, jamais d'une liste codée
     * en dur : ajouter un statut au référentiel suffira à faire apparaître son action.
     * [InvoiceStatus.CANCELLED] en est exclu — il n'appartient qu'au parcours d'avoir.
     */
    val availableTransitions: List<InvoiceStatus>
        get() = invoice
            ?.let { InvoiceStatusTransition.userActionableFrom(it.status).toList() }
            ?.sortedBy { it.ordinal }
            .orEmpty()

    /** Les transitions négatives exigent un motif — voir `ChangeInvoiceStatusUseCase`. */
    val pendingTransitionRequiresReason: Boolean
        get() = pendingTransition == InvoiceStatus.REJECTED || pendingTransition == InvoiceStatus.REFUSED

    val canConfirmTransition: Boolean
        get() = !isTransitioning &&
            (!pendingTransitionRequiresReason || transitionReason.isNotBlank())
}
