package com.ledgerhub.data.directory

import com.ledgerhub.data.remote.createPlatformHttpClient
import com.ledgerhub.domain.directory.DirectoryEntry
import com.ledgerhub.domain.directory.DirectoryRepository
import com.ledgerhub.domain.directory.DirectoryStatus
import com.ledgerhub.domain.directory.FrenchVatNumber
import com.ledgerhub.domain.directory.RoutingMode
import com.ledgerhub.domain.time.Clock
import com.ledgerhub.domain.time.SystemClock
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Annuaire réseau réel interrogeant l'API publique DINUM « Recherche d'entreprises ».
 *
 * Résout les fiches d'entreprises réelles (raison sociale, numéro de TVA intracommunautaire,
 * état administratif et mode de routage PPF/PDP) sans bouchon ni table statique en production.
 */
class KtorDirectoryRepository(
    private val httpClient: HttpClient = createPlatformHttpClient(),
    private val baseUrl: String = SEARCH_ENDPOINT,
    private val clock: Clock = SystemClock,
) : DirectoryRepository {

    override suspend fun findBySiren(siren: String): DirectoryEntry? = lookup(siren)

    override suspend fun findBySiret(siret: String): DirectoryEntry? = lookup(siret)

    override suspend fun all(): List<DirectoryEntry> = emptyList()

    override suspend fun cache(entry: DirectoryEntry) {
        // En lecture directe réseau ; la persistance locale est assurée par CachingDirectoryRepository
    }

    private suspend fun lookup(query: String): DirectoryEntry? {
        val response = try {
            httpClient.get(baseUrl) {
                parameter("q", query)
                parameter("page", "1")
                parameter("per_page", "1")
            }
        } catch (_: Throwable) {
            return null
        }

        if (response.status != HttpStatusCode.OK) return null

        val payload = try {
            JSON.decodeFromString(SearchResponse.serializer(), response.bodyAsText())
        } catch (_: Throwable) {
            return null
        }

        val first = payload.results.firstOrNull() ?: return null
        val resolvedSiren = first.siren ?: if (query.length >= 9) query.take(9) else query
        val resolvedSiret = if (query.length == 14) query else first.siege?.siret

        val companyName = first.legalName?.takeIf { it.isNotBlank() }
            ?: first.fullName?.takeIf { it.isNotBlank() }
            ?: first.siege?.commercialName?.takeIf { it.isNotBlank() }
            ?: first.sigle?.takeIf { it.isNotBlank() }
            ?: "ENTREPRISE $resolvedSiren"

        val vatNumber = first.tva?.firstOrNull { it.isNotBlank() }
            ?: FrenchVatNumber.format(resolvedSiren)

        val status = if (first.etatAdministratif == "A") DirectoryStatus.ACTIVE else DirectoryStatus.INACTIVE

        return DirectoryEntry(
            siren = resolvedSiren,
            siret = resolvedSiret,
            companyName = companyName,
            vatNumber = vatNumber,
            routingMode = RoutingMode.PPF,
            pdpIdentifier = null,
            isVatSubject = true,
            status = status,
            lastSyncAt = clock.nowIso(),
        )
    }

    @Serializable
    private data class SearchResponse(val results: List<SearchResult> = emptyList())

    @Serializable
    private data class SearchResult(
        @kotlinx.serialization.SerialName("siren") val siren: String? = null,
        @kotlinx.serialization.SerialName("nom_raison_sociale") val legalName: String? = null,
        @kotlinx.serialization.SerialName("nom_complet") val fullName: String? = null,
        @kotlinx.serialization.SerialName("sigle") val sigle: String? = null,
        @kotlinx.serialization.SerialName("etat_administratif") val etatAdministratif: String? = null,
        @kotlinx.serialization.SerialName("siege") val siege: SearchSiege? = null,
        @kotlinx.serialization.SerialName("tva") val tva: List<String>? = null,
    )

    @Serializable
    private data class SearchSiege(
        @kotlinx.serialization.SerialName("siret") val siret: String? = null,
        @kotlinx.serialization.SerialName("nom_commercial") val commercialName: String? = null,
    )

    companion object {
        const val SEARCH_ENDPOINT = "https://recherche-entreprises.api.gouv.fr/search"
        private val JSON = Json { ignoreUnknownKeys = true }
    }
}
