package com.ledgerhub.data.creditnote

import com.ledgerhub.db.CreditNote as CreditNoteRow
import com.ledgerhub.db.CreditNoteLine as CreditNoteLineRow
import com.ledgerhub.db.LedgerHubDatabase
import com.ledgerhub.domain.creditnote.CreditNote
import com.ledgerhub.domain.creditnote.CreditNoteNumbering
import com.ledgerhub.domain.creditnote.CreditNoteRepository
import com.ledgerhub.domain.creditnote.InvoiceAlreadyCreditedException
import com.ledgerhub.domain.invoice.InvoiceLine
import com.ledgerhub.domain.invoice.InvoiceStatus
import com.ledgerhub.domain.time.Clock
import com.ledgerhub.domain.time.SystemClock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import com.ledgerhub.domain.invoice.Money
import com.ledgerhub.domain.invoice.Party
import com.ledgerhub.domain.invoice.VatRate

/**
 * Persistance réelle des avoirs via SQLDelight (voir CreditNote.sq / CreditNoteLine.sq).
 *
 * L'émission d'un avoir est **un seul geste métier atomique** : contrôle d'unicité, écriture de
 * l'avoir et de ses lignes, puis passage de la facture d'origine au statut
 * [InvoiceStatus.CANCELLED] — le tout dans la même transaction, jamais des écritures
 * indépendantes qui pourraient diverger.
 *
 * @param userEmail compte propriétaire des avoirs lus/écrits — scoping multi-utilisateurs.
 */
@OptIn(ExperimentalUuidApi::class)
class SqlDelightCreditNoteRepository(
    private val database: LedgerHubDatabase,
    private val userEmail: String,
    /** Horodatage de la trace d'audit d'annulation — injecté pour rester déterministe en test. */
    private val clock: Clock = SystemClock,
) : CreditNoteRepository {

    override suspend fun submitCreditNote(creditNote: CreditNote): Result<Unit> = runCatching {
        database.transaction {
            // Une facture ne peut porter qu'un seul avoir total : un second créditerait deux fois
            // la même créance. Le contrôle est dans la transaction, donc insensible à une course.
            val existing = database.creditNoteQueries
                .selectByInvoiceNumber(creditNote.invoiceId)
                .executeAsOneOrNull()
            if (existing != null && existing.number != creditNote.number) {
                throw InvoiceAlreadyCreditedException(creditNote.invoiceId, existing.number)
            }

            database.customerQueries.insertIfAbsent(
                siret = creditNote.recipient.siret,
                siren = creditNote.recipient.siren,
                name = creditNote.recipient.name,
                email = creditNote.recipient.email,
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
                originalInvoiceDate = creditNote.originalInvoiceDate,
            )
            // Remplacement intégral des lignes — même stratégie que SqlDelightInvoiceRepository.
            database.creditNoteLineQueries.deleteByCreditNoteNumber(creditNote.number)
            creditNote.lines.forEach { line ->
                database.creditNoteLineQueries.insert(
                    creditNoteNumber = creditNote.number,
                    label = line.label,
                    quantity = line.quantity.toLong(),
                    unitPriceHtCents = line.unitPriceHt.cents,
                    vatRate = line.vatRate.name,
                )
            }
            // L'annulation est une transition comme une autre : elle doit laisser une trace,
            // dans la même transaction que l'avoir lui-même (PAF, US-07).
            val previousStatus = database.invoiceQueries
                .selectByNumber(creditNote.invoiceId).executeAsOneOrNull()?.status
            database.invoiceQueries.updateStatus(InvoiceStatus.CANCELLED.name, creditNote.invoiceId)
            database.auditLogQueries.insert(
                id = Uuid.random().toString(),
                invoiceNumber = creditNote.invoiceId,
                fromStatus = previousStatus,
                toStatus = InvoiceStatus.CANCELLED.name,
                reason = "Annulée par l'avoir ${creditNote.number} — ${creditNote.reason}",
                createdAt = clock.nowIso(),
            )
        }
    }

    override suspend fun fetchCreditNotes(): Result<List<CreditNote>> = runCatching {
        database.creditNoteQueries.selectByUserEmail(userEmail).executeAsList().map { it.toDomain() }
    }

    override suspend fun findByInvoiceNumber(invoiceNumber: String): Result<CreditNote?> = runCatching {
        database.creditNoteQueries.selectByInvoiceNumber(invoiceNumber).executeAsOneOrNull()?.toDomain()
    }

    override suspend fun nextNumberForYear(year: Int): Result<String> = runCatching {
        val last = database.creditNoteQueries
            .selectLastNumberForPrefix(CreditNoteNumbering.likePatternForYear(year))
            .executeAsOneOrNull()
        CreditNoteNumbering.next(year, last)
    }

    private fun CreditNoteRow.toDomain(): CreditNote {
        val recipient = database.customerQueries.selectBySiret(recipientSiret).executeAsOneOrNull()
            ?.let { Party(name = it.name, siren = it.siren, siret = it.siret, email = it.email) }
            ?: Party(name = "", siren = "", siret = recipientSiret)
        val lines = database.creditNoteLineQueries
            .selectByCreditNoteNumber(number)
            .executeAsList()
            .map { it.toDomain() }
        return CreditNote(
            number = number,
            issueDate = issueDate,
            invoiceId = invoiceNumber,
            originalInvoiceDate = originalInvoiceDate,
            reason = reason,
            issuer = Party(name = issuerName, siren = issuerSiren, siret = issuerSiret),
            recipient = recipient,
            lines = lines,
            totalHt = Money(totalHtCents),
            totalVat = Money(totalVatCents),
            totalTtc = Money(totalTtcCents),
        )
    }

    private fun CreditNoteLineRow.toDomain(): InvoiceLine = InvoiceLine(
        label = label,
        quantity = quantity.toInt(),
        unitPriceHt = Money(unitPriceHtCents),
        vatRate = VatRate.valueOf(vatRate),
    )
}
