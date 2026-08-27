package com.ledgerhub.data.repository

import com.ledgerhub.data.remote.createPlatformHttpClient
import com.ledgerhub.data.remote.dto.InvoiceDto
import com.ledgerhub.data.remote.dto.toDomain
import com.ledgerhub.data.remote.ledgerApiBaseUrl
import com.ledgerhub.domain.invoice.Invoice
import com.ledgerhub.domain.repository.LedgerRepository
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess

/**
 * Implémentation Ktor de [LedgerRepository] — cible le backend local de dev (voir
 * [ledgerApiBaseUrl]), jamais un service cloud (pivot 100% local, voir App.kt). Structure
 * symétrique à [com.ledgerhub.data.auth.KtorAuthRepository].
 */
class LedgerRepositoryImpl(
    private val httpClient: HttpClient = createPlatformHttpClient(),
    private val baseUrl: String = ledgerApiBaseUrl,
) : LedgerRepository {

    override suspend fun fetchInvoices(): Result<List<Invoice>> = runCatching {
        val response = httpClient.get("$baseUrl/api/invoices")
        check(response.status.isSuccess()) {
            "Échec du chargement des factures (code ${response.status.value}) — vérifiez que le serveur local tourne sur $baseUrl"
        }
        response.body<List<InvoiceDto>>().map { it.toDomain() }
    }

    override suspend fun getInvoiceDetail(number: String): Result<Invoice?> = runCatching {
        val response = httpClient.get("$baseUrl/api/invoices/$number")
        when {
            response.status == HttpStatusCode.NotFound -> null
            response.status.isSuccess() -> response.body<InvoiceDto>().toDomain()
            else -> error("Échec du chargement de la facture $number (code ${response.status.value})")
        }
    }

    override suspend fun healthCheck(): Result<Unit> = runCatching {
        val response = httpClient.get("$baseUrl/api/health")
        check(response.status.isSuccess()) {
            "Le serveur local ne répond pas (code ${response.status.value}) sur $baseUrl"
        }
    }
}
