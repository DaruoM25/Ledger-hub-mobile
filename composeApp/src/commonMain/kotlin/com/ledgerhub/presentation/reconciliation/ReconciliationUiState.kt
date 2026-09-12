package com.ledgerhub.presentation.reconciliation

import com.ledgerhub.domain.invoice.Invoice
import com.ledgerhub.domain.invoice.InvoiceStatus
import com.ledgerhub.domain.invoice.Money
import com.ledgerhub.domain.reconciliation.AmountDelta
import com.ledgerhub.domain.reconciliation.BankTransaction
import com.ledgerhub.domain.reconciliation.ReconciliationMatch

/**
 * État immuable de l'écran de Rapprochement Bancaire (US-18).
 *
 * Ne contient aucune donnée dérivable : l'état « rapprochée » d'une ligne, l'écart courant et la
 * possibilité de lettrer se **calculent** ici à partir des trois listes et des deux sélections.
 * Les stocker en plus les exposerait à diverger d'elles.
 *
 * @param invoices factures présentées au lettrage. Le filtrage réglementaire (voir
 *   [PAYABLE_STATUSES]) est appliqué à la source par le ViewModel, pas à l'affichage : une facture
 *   qu'on ne peut pas solder n'a rien à faire dans une liste intitulée « en attente de paiement ».
 */
data class ReconciliationUiState(
    val isLoading: Boolean = true,
    val transactions: List<BankTransaction> = emptyList(),
    val invoices: List<Invoice> = emptyList(),
    val matches: List<ReconciliationMatch> = emptyList(),
    val selectedTransactionId: String? = null,
    val selectedInvoiceNumber: String? = null,
    val isReconciling: Boolean = false,
    val errorMessage: String? = null,
    /** Bannière réglementaire Material 3 : transmission e-Reporting de paiement prête (US-28). */
    val showEreportingBanner: Boolean = false,
    /** Onglet visible en agencement compact — sans effet au-delà du seuil « expanded ». */
    val compactTab: ReconciliationTab = ReconciliationTab.TRANSACTIONS,
) {
    val selectedTransaction: BankTransaction?
        get() = transactions.firstOrNull { it.id == selectedTransactionId }

    val selectedInvoice: Invoice?
        get() = invoices.firstOrNull { it.number == selectedInvoiceNumber }

    /**
     * Le bouton de lettrage n'apparaît qu'une fois les deux côtés désignés — c'est ce qui rend
     * l'action compréhensible : on ne propose pas d'associer avant d'avoir quoi associer.
     */
    val canReconcile: Boolean
        get() = !isReconciling && selectedTransaction != null && selectedInvoice != null

    /** Écart du couple en cours de sélection, `null` tant que la sélection est incomplète. */
    val pendingDelta: Money?
        get() {
            val transaction = selectedTransaction ?: return null
            val invoice = selectedInvoice ?: return null
            return AmountDelta.between(transaction, invoice)
        }

    /** `true` si le couple sélectionné ne se solde pas exactement — pilote le badge d'écart. */
    val hasPendingMismatch: Boolean
        get() = pendingDelta?.let { it != Money.ZERO } == true

    fun isTransactionReconciled(transactionId: String): Boolean =
        matches.any { it.transactionId == transactionId }

    fun isInvoiceReconciled(invoiceNumber: String): Boolean =
        matches.any { it.invoiceNumber == invoiceNumber }

    fun matchFor(transactionId: String): ReconciliationMatch? =
        matches.firstOrNull { it.transactionId == transactionId }

    companion object {
        /**
         * Statuts qu'un encaissement peut solder.
         *
         * Dérivé de la machine d'états, jamais réénuméré à la main : `DRAFT → PAID` est interdit
         * par [InvoiceStatusTransition][com.ledgerhub.domain.invoice.InvoiceStatusTransition], un
         * brouillon n'étant pas entré dans le circuit légal. Proposer une facture que le lettrage
         * refusera ensuite serait une impasse offerte à l'utilisateur.
         */
        val PAYABLE_STATUSES: Set<InvoiceStatus> =
            InvoiceStatus.entries
                .filter { status ->
                    com.ledgerhub.domain.invoice.InvoiceStatusTransition
                        .isAllowed(status, InvoiceStatus.PAID)
                }
                .toSet()
    }
}

/** Les deux faces du rapprochement, présentées en onglets sous le seuil « expanded ». */
enum class ReconciliationTab { TRANSACTIONS, INVOICES }
