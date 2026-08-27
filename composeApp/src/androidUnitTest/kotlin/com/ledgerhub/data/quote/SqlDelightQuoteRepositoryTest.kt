package com.ledgerhub.data.quote

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.ledgerhub.db.LedgerHubDatabase
import com.ledgerhub.domain.invoice.Money
import com.ledgerhub.domain.invoice.Party
import com.ledgerhub.domain.invoice.VatRate
import com.ledgerhub.domain.quote.ConvertQuoteToInvoiceUseCase
import com.ledgerhub.domain.quote.Quote
import com.ledgerhub.domain.quote.QuoteLine
import com.ledgerhub.domain.quote.QuoteStatus
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests QA (Skill 2) de [SqlDelightQuoteRepository], structure symétrique à
 * [com.ledgerhub.data.invoice.SqlDelightInvoiceRepositoryTest]. Pilote JdbcSqliteDriver en
 * mémoire, base neuve par test.
 */
class SqlDelightQuoteRepositoryTest {

    private fun newDatabase(): LedgerHubDatabase {
        // Chargement explicite du driver — robustesse face au classloader sandboxé de Robolectric
        // (utilisé ailleurs dans la même suite), voir SqlDelightCreditNoteRepositoryTest.
        Class.forName("org.sqlite.JDBC")
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        LedgerHubDatabase.Schema.create(driver)
        return LedgerHubDatabase(driver)
    }

    private val issuer = Party("Vendeur SARL", "123456789", "12345678900012")
    private val recipient = Party("Client SAS", "987654321", "98765432100045")

    private fun quote(
        number: String = "DEV-2026-001",
        status: QuoteStatus = QuoteStatus.DRAFT,
        lines: List<QuoteLine> = listOf(
            QuoteLine("Conseil", quantity = 2, unitPriceHt = Money(5000), vatRate = VatRate.TAUX_NORMAL),
        ),
    ) = Quote(
        number = number,
        issueDate = "2026-08-06",
        validityDate = "2026-09-06",
        issuer = issuer,
        recipient = recipient,
        lines = lines,
        status = status,
    )

    @Test
    fun submitQuote_thenFetch_roundTripsAllFields_includingValidityDateAndRecipient() = runTest {
        val repository = SqlDelightQuoteRepository(newDatabase(), userEmail = "qa@ledgerhub.app")
        val original = quote(
            lines = listOf(
                QuoteLine("Conseil", 1, Money(10000), VatRate.TAUX_NORMAL),
                QuoteLine("Livre", 1, Money(2000), VatRate.TAUX_REDUIT),
            ),
        )

        assertTrue(repository.submitQuote(original).isSuccess)

        val fetched = repository.fetchQuotes().getOrThrow()
        assertEquals(1, fetched.size)
        val roundTripped = fetched.single()
        assertEquals(original.number, roundTripped.number)
        assertEquals(original.validityDate, roundTripped.validityDate)
        assertEquals(original.status, roundTripped.status)
        assertEquals(original.recipient, roundTripped.recipient)
        assertEquals(original.lines.size, roundTripped.lines.size)
        assertEquals(original.totalTtc, roundTripped.totalTtc)
    }

    @Test
    fun fetchQuotes_filtersByUserEmail_doesNotLeakOtherUsersQuotes() = runTest {
        val database = newDatabase()
        val repositoryA = SqlDelightQuoteRepository(database, userEmail = "a@ledgerhub.app")
        val repositoryB = SqlDelightQuoteRepository(database, userEmail = "b@ledgerhub.app")

        repositoryA.submitQuote(quote(number = "DEV-A-001"))
        repositoryB.submitQuote(quote(number = "DEV-B-001"))

        assertEquals(listOf("DEV-A-001"), repositoryA.fetchQuotes().getOrThrow().map { it.number })
        assertEquals(listOf("DEV-B-001"), repositoryB.fetchQuotes().getOrThrow().map { it.number })
    }

    @Test
    fun resubmittingSameQuoteNumber_replacesLines_insteadOfAccumulatingThem() = runTest {
        val repository = SqlDelightQuoteRepository(newDatabase(), userEmail = "qa@ledgerhub.app")
        repository.submitQuote(quote(lines = listOf(QuoteLine("Conseil", 1, Money(10000), VatRate.TAUX_NORMAL))))

        repository.submitQuote(
            quote(
                status = QuoteStatus.ACCEPTED,
                lines = listOf(
                    QuoteLine("Conseil", 1, Money(10000), VatRate.TAUX_NORMAL),
                    QuoteLine("Formation", 1, Money(5000), VatRate.TAUX_NORMAL),
                ),
            )
        )

        val fetched = repository.fetchQuotes().getOrThrow()
        assertEquals(1, fetched.size)
        assertEquals(QuoteStatus.ACCEPTED, fetched.single().status)
        assertEquals(2, fetched.single().lines.size)
    }

    // ── Intégration bout en bout avec le "bouton magique" (Skill 1) ──────────

    @Test
    fun acceptedQuote_convertedToInvoice_canBePersistedBySqlDelightInvoiceRepository() = runTest {
        val database = newDatabase()
        val quoteRepository = SqlDelightQuoteRepository(database, userEmail = "qa@ledgerhub.app")
        val invoiceRepository = com.ledgerhub.data.invoice.SqlDelightInvoiceRepository(database, userEmail = "qa@ledgerhub.app")
        val accepted = quote(status = QuoteStatus.ACCEPTED)
        quoteRepository.submitQuote(accepted)

        val conversion = ConvertQuoteToInvoiceUseCase()(accepted, invoiceNumber = "F-2026-001", issueDate = "2026-08-06")
        assertTrue(conversion.isSuccess)

        assertTrue(invoiceRepository.submitInvoice(conversion.getOrThrow()).isSuccess)
        val invoices = invoiceRepository.fetchInvoices().getOrThrow()
        assertEquals(1, invoices.size)
        assertEquals(accepted.number, invoices.single().sourceQuoteId)
    }
}
