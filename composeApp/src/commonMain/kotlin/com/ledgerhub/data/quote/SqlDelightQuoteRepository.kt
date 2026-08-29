package com.ledgerhub.data.quote

import com.ledgerhub.db.LedgerHubDatabase
import com.ledgerhub.db.Quote as QuoteRow
import com.ledgerhub.db.QuoteLine as QuoteLineRow
import com.ledgerhub.domain.invoice.Money
import com.ledgerhub.domain.invoice.Party
import com.ledgerhub.domain.invoice.VatRate
import com.ledgerhub.domain.quote.Quote
import com.ledgerhub.domain.quote.QuoteLine
import com.ledgerhub.domain.quote.QuoteRepository
import com.ledgerhub.domain.quote.QuoteStatus

/**
 * Persistance réelle des devis via SQLDelight — structure symétrique à
 * [com.ledgerhub.data.invoice.SqlDelightInvoiceRepository] (voir Quote.sq / QuoteLine.sq).
 *
 * @param userEmail compte propriétaire des devis lus/écrits par ce repository —
 *   scoping multi-utilisateurs (voir Quote.sq:selectByUserEmail).
 */
class SqlDelightQuoteRepository(
    private val database: LedgerHubDatabase,
    private val userEmail: String,
) : QuoteRepository {

    override suspend fun submitQuote(quote: Quote): Result<Unit> = runCatching {
        database.transaction {
            database.customerQueries.insertIfAbsent(
                siret = quote.recipient.siret,
                siren = quote.recipient.siren,
                name = quote.recipient.name,
                email = quote.recipient.email,
            )
            database.quoteQueries.insertOrReplace(
                number = quote.number,
                issueDate = quote.issueDate,
                validityDate = quote.validityDate,
                status = quote.status.name,
                userEmail = userEmail,
                issuerName = quote.issuer.name,
                issuerSiren = quote.issuer.siren,
                issuerSiret = quote.issuer.siret,
                recipientSiret = quote.recipient.siret,
            )
            database.quoteLineQueries.deleteByQuoteNumber(quote.number)
            quote.lines.forEach { line ->
                database.quoteLineQueries.insert(
                    quoteNumber = quote.number,
                    label = line.label,
                    quantity = line.quantity.toLong(),
                    unitPriceHtCents = line.unitPriceHt.cents,
                    vatRate = line.vatRate.name,
                )
            }
        }
    }

    override suspend fun fetchQuotes(): Result<List<Quote>> = runCatching {
        database.quoteQueries.selectByUserEmail(userEmail).executeAsList().map { it.toDomain() }
    }

    private fun QuoteRow.toDomain(): Quote {
        val recipient = database.customerQueries.selectBySiret(recipientSiret).executeAsOneOrNull()
            ?.let { Party(name = it.name, siren = it.siren, siret = it.siret, email = it.email) }
            ?: Party(name = "", siren = "", siret = recipientSiret)
        val lines = database.quoteLineQueries.selectByQuoteNumber(number).executeAsList().map { it.toDomain() }
        return Quote(
            number = number,
            issueDate = issueDate,
            validityDate = validityDate,
            issuer = Party(name = issuerName, siren = issuerSiren, siret = issuerSiret),
            recipient = recipient,
            lines = lines,
            status = QuoteStatus.valueOf(status),
        )
    }

    private fun QuoteLineRow.toDomain(): QuoteLine = QuoteLine(
        label = label,
        quantity = quantity.toInt(),
        unitPriceHt = Money(unitPriceHtCents),
        vatRate = VatRate.valueOf(vatRate),
    )
}
