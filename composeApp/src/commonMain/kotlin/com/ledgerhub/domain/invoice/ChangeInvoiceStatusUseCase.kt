package com.ledgerhub.domain.invoice

/**
 * Point d'entrée **unique** de tout changement de statut d'une facture.
 *
 * La validation a lieu ici, avant le moindre accès au dépôt : une transition interdite ne doit
 * pas atteindre la base, même pour y être rejetée. Le dépôt reste néanmoins responsable de
 * l'atomicité — statut et trace d'audit écrits ensemble ou pas du tout.
 *
 * Les transitions exprimant un échec exigent un motif : une facture rejetée ou refusée sans
 * explication rend la piste d'audit inexploitable en contrôle.
 */
class ChangeInvoiceStatusUseCase(
    private val repository: InvoiceStatusRepository,
) {

    suspend operator fun invoke(
        invoice: Invoice,
        target: InvoiceStatus,
        reason: String? = null,
    ): Result<Unit> {
        if (!InvoiceStatusTransition.isAllowed(invoice.status, target)) {
            return Result.failure(InvalidStatusTransitionException(invoice.status, target))
        }
        if (target in REASON_REQUIRED && reason.isNullOrBlank()) {
            return Result.failure(
                IllegalArgumentException("Un motif est requis pour passer la facture ${invoice.number} à $target"),
            )
        }
        return repository.changeStatus(
            invoiceNumber = invoice.number,
            from = invoice.status,
            to = target,
            reason = reason?.trim()?.takeIf { it.isNotEmpty() },
        )
    }

    private companion object {
        /** Transitions négatives : sans motif, la trace ne vaut rien en contrôle. */
        val REASON_REQUIRED = setOf(InvoiceStatus.REJECTED, InvoiceStatus.REFUSED)
    }
}

/**
 * Écriture d'une transition. Séparée d'[InvoiceRepository] pour que le use case ne dépende que
 * de ce qu'il utilise, et pour que les doubles de test restent minces.
 */
interface InvoiceStatusRepository {
    /**
     * Écrit le nouveau statut **et** la trace d'audit correspondante, dans une seule transaction.
     * Une trace sans changement de statut, ou l'inverse, serait pire que pas de trace du tout.
     */
    suspend fun changeStatus(
        invoiceNumber: String,
        from: InvoiceStatus,
        to: InvoiceStatus,
        reason: String?,
    ): Result<Unit>
}
