package com.ledgerhub.data.creditnote

import com.ledgerhub.db.LedgerHubDatabase
import com.ledgerhub.db.CreditNote as CreditNoteRow
import com.ledgerhub.domain.creditnote.CreditNote
import com.ledgerhub.domain.creditnote.CreditNoteRepository
import com.ledgerhub.domain.invoice.InvoiceStatus
import com.ledgerhub.domain.invoice.Money
import com.ledgerhub.domain.invoice.Party

/**
 * Persistance réelle des avoirs via SQLDelight (voir CreditNote.sq). La création d'un avoir
 * fait passer, dans la même transaction, la facture d'origine au statut [InvoiceStatus.CANCELLED]
 * — l'avoir et l'annulation de la facture sont un seul geste métier atomique, jamais deux écritures
 * indépendantes qui pourraient diverger.
 *
 * @param userEmail compte propriétaire des avoirs lus/écrits par ce repository —
 *   scoping multi-utilisateurs (voir CreditNote.sq:selectByUserEmail).
 */
class SqlDelightCreditNoteRepository(
    private val database: LedgerHubDatabase,
    private val userEmail: String,
) : CreditNoteRepository {

    override suspend fun submitCreditNote(creditNote: CreditNote): Result<Unit> = runCatching {
        database.transaction {
            database.customerQueries.insertOrReplace(
                siret = creditNote.recipient.siret,
                siren = creditNote.recipient.siren,
                name = creditNote.recipient.name,
            )
            database.creditNoteQueries.insertOrReplace(
                number = creditNote.number,
                issueDate = creditNote.issueDate,
                invoiceNumber = creditNote.invoiceId,
                reason = creditNote.reason,
                userEmail = userEmail,
                issuerName = creditNote.issuer.name,
                issuerSiren = creditNote.issuer.siren,
                issuerSiret = creditNote.issuer.siret,
                recipientSiret = creditNote.recipient.siret,
                totalHtCents = creditNote.totalHt.cents,
                totalVatCents = creditNote.totalVat.cents,
                totalTtcCents = creditNote.totalTtc.cents,
            )
            database.invoiceQueries.updateStatus(InvoiceStatus.CANCELLED.name, creditNote.invoiceId)
        }
    }

    override suspend fun fetchCreditNotes(): Result<List<CreditNote>> = runCatching {
        database.creditNoteQueries.selectByUserEmail(userEmail).executeAsList().map { it.toDomain() }
    }

    private fun CreditNoteRow.toDomain(): CreditNote {
        val recipient = database.customerQueries.selectBySiret(recipientSiret).executeAsOneOrNull()
            ?.let { Party(name = it.name, siren = it.siren, siret = it.siret) }
            ?: Party(name = "", siren = "", siret = recipientSiret)
        return CreditNote(
            number = number,
            issueDate = issueDate,
            invoiceId = invoiceNumber,
            reason = reason,
            issuer = Party(name = issuerName, siren = issuerSiren, siret = issuerSiret),
            recipient = recipient,
            totalHt = Money(totalHtCents),
            totalVat = Money(totalVatCents),
            totalTtc = Money(totalTtcCents),
        )
    }
}
