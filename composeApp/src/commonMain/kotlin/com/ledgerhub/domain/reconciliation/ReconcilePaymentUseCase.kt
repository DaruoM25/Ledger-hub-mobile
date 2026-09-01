package com.ledgerhub.domain.reconciliation

import com.ledgerhub.domain.invoice.Invoice
import com.ledgerhub.domain.invoice.InvoiceStatus
import com.ledgerhub.domain.invoice.InvoiceStatusTransition
import com.ledgerhub.domain.invoice.InvalidStatusTransitionException
import com.ledgerhub.domain.time.Clock
import com.ledgerhub.domain.time.SystemClock

/**
 * Point d'entrée **unique** du lettrage (US-18).
 *
 * Le rapprochement n'est pas qu'un lien d'affichage : il constate qu'une facture est payée. Il
 * emporte donc le passage à [InvoiceStatus.PAID] et la trace correspondante dans la Piste d'Audit
 * Fiable — un encaissement rapproché sans transition laisserait la facture éternellement « en
 * attente de paiement », et une transition sans trace serait invisible en contrôle.
 *
 * ## Ce qui est validé ici, et pourquoi ici
 *
 * La validation précède tout accès à la base : une opération interdite ne doit pas l'atteindre,
 * même pour y être rejetée. Trois refus :
 *
 * 1. **Transition de statut** — arbitrée par [InvoiceStatusTransition], la même table que
 *    [ChangeInvoiceStatusUseCase][com.ledgerhub.domain.invoice.ChangeInvoiceStatusUseCase]. Un
 *    brouillon ne peut pas être payé : il n'est pas entré dans le circuit légal, et l'écran ne
 *    doit pas proposer de le solder.
 * 2. **Double lettrage** — une écriture bancaire solde une facture et une seule, une facture est
 *    soldée par un encaissement et un seul. Sans cette règle, un même virement pourrait éteindre
 *    plusieurs créances.
 * 3. **Sens de l'écriture** — un décaissement ne solde pas une facture client.
 *
 * L'écriture, elle, est confiée d'un bloc à [ReconciliationRepository.reconcile] : lettrage,
 * statut et trace doivent être écrits ensemble ou pas du tout.
 */
class ReconcilePaymentUseCase(
    private val repository: ReconciliationRepository,
    private val clock: Clock = SystemClock,
) {

    suspend operator fun invoke(
        transaction: BankTransaction,
        invoice: Invoice,
    ): Result<ReconciliationMatch> {
        if (!transaction.isCredit) {
            return Result.failure(
                NotACreditTransactionException(transaction.id),
            )
        }
        if (!InvoiceStatusTransition.isAllowed(invoice.status, InvoiceStatus.PAID)) {
            return Result.failure(InvalidStatusTransitionException(invoice.status, InvoiceStatus.PAID))
        }

        val existing = repository.fetchMatches().getOrElse { return Result.failure(it) }
        existing.firstOrNull { it.transactionId == transaction.id }?.let {
            return Result.failure(AlreadyReconciledException.transaction(transaction.id, it.invoiceNumber))
        }
        existing.firstOrNull { it.invoiceNumber == invoice.number }?.let {
            return Result.failure(AlreadyReconciledException.invoice(invoice.number, it.transactionId))
        }

        val match = ReconciliationMatch(
            transactionId = transaction.id,
            invoiceNumber = invoice.number,
            matchedAtIso = clock.nowIso(),
            deltaCents = AmountDelta.between(transaction, invoice).cents,
        )
        return repository.reconcile(match, fromStatus = invoice.status.name).map { match }
    }
}

/** Lettrage refusé : l'un des deux éléments est déjà rapproché. */
class AlreadyReconciledException private constructor(message: String) : Exception(message) {
    companion object {
        fun transaction(transactionId: String, boundTo: String) = AlreadyReconciledException(
            "La transaction $transactionId est déjà rapprochée avec la facture $boundTo",
        )

        fun invoice(invoiceNumber: String, boundTo: String) = AlreadyReconciledException(
            "La facture $invoiceNumber est déjà rapprochée avec la transaction $boundTo",
        )
    }
}

/** Lettrage refusé : un décaissement ne solde pas une facture client. */
class NotACreditTransactionException(transactionId: String) :
    Exception("La transaction $transactionId est un décaissement : elle ne peut pas solder une facture")
