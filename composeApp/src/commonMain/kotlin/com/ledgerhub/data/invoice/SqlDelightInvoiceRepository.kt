package com.ledgerhub.data.invoice

import com.ledgerhub.db.LedgerHubDatabase
import com.ledgerhub.db.Invoice as InvoiceRow
import com.ledgerhub.db.InvoiceLine as InvoiceLineRow
import com.ledgerhub.domain.invoice.Invoice
import com.ledgerhub.domain.invoice.InvoiceLine
import com.ledgerhub.domain.invoice.InvoiceRepository
import com.ledgerhub.domain.invoice.InvoiceStatus
import com.ledgerhub.domain.invoice.Money
import com.ledgerhub.domain.invoice.Party
import com.ledgerhub.domain.invoice.VatRate

/**
 * Persistance réelle des factures via SQLDelight (voir Invoice.sq / InvoiceLine.sq /
 * Customer.sq, commonMain/sqldelight). Le destinataire est normalisé vers la table Customer
 * (SIRET unique, réutilisable d'une facture à l'autre) ; l'émetteur reste embarqué sur la
 * ligne Invoice (toujours la même entreprise, celle de [userEmail]).
 *
 * @param userEmail compte propriétaire des factures lues/écrites par ce repository —
 *   scoping multi-utilisateurs (voir Invoice.sq:selectByUserEmail).
 */
class SqlDelightInvoiceRepository(
    private val database: LedgerHubDatabase,
    private val userEmail: String,
) : InvoiceRepository {

    override suspend fun submitInvoice(invoice: Invoice): Result<Unit> = runCatching {
        database.transaction {
            database.customerQueries.insertOrReplace(
                siret = invoice.recipient.siret,
                siren = invoice.recipient.siren,
                name = invoice.recipient.name,
            )
            database.invoiceQueries.insertOrReplace(
                number = invoice.number,
                issueDate = invoice.issueDate,
                status = invoice.status.name,
                sourceQuoteId = invoice.sourceQuoteId,
                userEmail = userEmail,
                issuerName = invoice.issuer.name,
                issuerSiren = invoice.issuer.siren,
                issuerSiret = invoice.issuer.siret,
                recipientSiret = invoice.recipient.siret,
            )
            // Remplacement intégral des lignes — plus simple et moins sujet aux bugs qu'un diff
            // ligne à ligne, pour un volume de lignes par facture qui reste faible en pratique.
            database.invoiceLineQueries.deleteByInvoiceNumber(invoice.number)
            invoice.lines.forEach { line ->
                database.invoiceLineQueries.insert(
                    invoiceNumber = invoice.number,
                    label = line.label,
                    quantity = line.quantity.toLong(),
                    unitPriceHtCents = line.unitPriceHt.cents,
                    vatRate = line.vatRate.name,
                )
            }
        }
    }

    override suspend fun fetchInvoices(): Result<List<Invoice>> = runCatching {
        database.invoiceQueries.selectByUserEmail(userEmail).executeAsList().map { it.toDomain() }
    }

    private fun InvoiceRow.toDomain(): Invoice {
        val recipient = database.customerQueries.selectBySiret(recipientSiret).executeAsOneOrNull()
            ?.let { Party(name = it.name, siren = it.siren, siret = it.siret) }
            ?: Party(name = "", siren = "", siret = recipientSiret)
        val lines = database.invoiceLineQueries.selectByInvoiceNumber(number).executeAsList().map { it.toDomain() }
        return Invoice(
            number = number,
            issueDate = issueDate,
            issuer = Party(name = issuerName, siren = issuerSiren, siret = issuerSiret),
            recipient = recipient,
            lines = lines,
            status = InvoiceStatus.valueOf(status),
            sourceQuoteId = sourceQuoteId,
        )
    }

    private fun InvoiceLineRow.toDomain(): InvoiceLine = InvoiceLine(
        label = label,
        quantity = quantity.toInt(),
        unitPriceHt = Money(unitPriceHtCents),
        vatRate = VatRate.valueOf(vatRate),
    )
}
