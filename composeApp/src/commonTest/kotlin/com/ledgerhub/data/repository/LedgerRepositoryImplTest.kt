package com.ledgerhub.data.repository

import com.ledgerhub.domain.invoice.InvoiceStatus
import com.ledgerhub.domain.invoice.Money
import com.ledgerhub.domain.invoice.VatRate
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests QA (Skill 2) de [LedgerRepositoryImpl] — [MockEngine] simule les réponses HTTP, aucun
 * accès réseau réel. Voir SqlDelightInvoiceRepositoryTest pour le même principe côté persistance
 * locale (driver JDBC en mémoire plutôt que SQLite réel).
 */
class LedgerRepositoryImplTest {

    private val invoiceJson = """
        {
          "number": "F-2026-001",
          "issueDate": "2026-08-01",
          "status": "PAID",
          "issuer": {"name": "Vendeur SARL", "siren": "123456789", "siret": "12345678900012"},
          "recipient": {"name": "Client SAS", "siren": "987654321", "siret": "98765432100045"},
          "items": [
            {"label": "Conseil", "quantity": 2, "unitPriceHtCents": 5000, "vatRate": "TAUX_NORMAL"}
          ],
          "taxSummary": [
            {"vatRate": "TAUX_NORMAL", "baseHtCents": 10000, "vatAmountCents": 2000}
          ]
        }
    """.trimIndent()

    private fun repositoryWith(engine: MockEngine): LedgerRepositoryImpl {
        val client = HttpClient(engine) {
            install(ContentNegotiation) { json() }
        }
        return LedgerRepositoryImpl(httpClient = client, baseUrl = "http://test.local")
    }

    // ── fetchInvoices ─────────────────────────────────────────────────────────

    @Test
    fun fetchInvoices_onSuccess_mapsDtoToDomain() = runTest {
        val engine = MockEngine { request ->
            assertEquals("http://test.local/api/invoices", request.url.toString())
            respond(
                content = "[$invoiceJson]",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val repository = repositoryWith(engine)

        val invoices = repository.fetchInvoices().getOrThrow()

        assertEquals(1, invoices.size)
        val invoice = invoices.single()
        assertEquals("F-2026-001", invoice.number)
        assertEquals(InvoiceStatus.PAID, invoice.status)
        assertEquals(1, invoice.lines.size)
        assertEquals(VatRate.TAUX_NORMAL, invoice.lines.single().vatRate)
        // Le total est TOUJOURS recalculé côté domaine à partir des lignes, jamais lu depuis
        // taxSummary (voir InvoiceDto.toDomain) : 2 * 50.00 HT + 20% TVA = 120.00 TTC.
        assertEquals(Money(12000), invoice.totalTtc)
    }

    @Test
    fun fetchInvoices_onServerError_returnsFailure() = runTest {
        val engine = MockEngine { respondError(HttpStatusCode.InternalServerError) }
        val repository = repositoryWith(engine)

        val result = repository.fetchInvoices()

        assertTrue(result.isFailure)
    }

    // ── getInvoiceDetail ──────────────────────────────────────────────────────

    @Test
    fun getInvoiceDetail_onSuccess_mapsDtoToDomain() = runTest {
        val engine = MockEngine { request ->
            assertEquals("http://test.local/api/invoices/F-2026-001", request.url.toString())
            respond(
                content = invoiceJson,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val repository = repositoryWith(engine)

        val invoice = repository.getInvoiceDetail("F-2026-001").getOrThrow()

        assertEquals("F-2026-001", invoice?.number)
    }

    @Test
    fun getInvoiceDetail_onNotFound_returnsSuccessWithNull() = runTest {
        val engine = MockEngine { respondError(HttpStatusCode.NotFound) }
        val repository = repositoryWith(engine)

        val result = repository.getInvoiceDetail("F-INCONNUE")

        assertTrue(result.isSuccess)
        assertNull(result.getOrThrow())
    }

    // ── healthCheck ───────────────────────────────────────────────────────────

    @Test
    fun healthCheck_onSuccess_isSuccess() = runTest {
        val engine = MockEngine { respond(content = "", status = HttpStatusCode.OK) }
        val repository = repositoryWith(engine)

        assertTrue(repository.healthCheck().isSuccess)
    }

    @Test
    fun healthCheck_whenServerUnreachable_isFailure() = runTest {
        val engine = MockEngine { respondError(HttpStatusCode.ServiceUnavailable) }
        val repository = repositoryWith(engine)

        assertTrue(repository.healthCheck().isFailure)
    }
}
