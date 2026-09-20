package com.ledgerhub.data.sirene

import com.ledgerhub.domain.sirene.SireneLookupResult
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Niveau 2 (US-26) — [KtorSireneLookupService] face à l'API « Recherche d'entreprises ».
 *
 * [MockEngine] rejoue les réponses HTTP : aucun accès réseau réel, donc une suite déterministe qui
 * tourne hors ligne. Ce qui est éprouvé ici, c'est la **traduction** des réponses de l'API en
 * décisions du domaine — et surtout la frontière entre « le répertoire dit non » et « le répertoire
 * n'a pas répondu », que l'US-21 avait posée et que l'écran restitue par deux messages distincts.
 */
class KtorSireneLookupServiceTest {

    private val siret = "90123456700013"

    /**
     * Charge utile réduite d'une réponse réelle de l'API — champs inconnus **compris**.
     *
     * `siege`, `complements` et `matching_etablissements` ne sont pas modélisés par le service :
     * leur présence ici est le cœur du test de tolérance ci-dessous, pas du remplissage.
     */
    private val payload = """
        {
          "results": [
            {
              "siren": "901234567",
              "nom_complet": "Youssoufi DevOps & Cloud EURL",
              "nom_raison_sociale": "YOUSSOUFI DEVOPS & CLOUD",
              "nature_juridique": "5498",
              "siege": {"siret": "90123456700013", "code_postal": "75002"},
              "complements": {"est_association": false},
              "matching_etablissements": [{"siret": "90123456700013"}]
            }
          ],
          "total_results": 1,
          "page": 1
        }
    """.trimIndent()

    private fun serviceWith(engine: MockEngine) =
        KtorSireneLookupService(httpClient = HttpClient(engine), baseUrl = "https://sirene.test/search")

    private fun jsonEngine(body: String, status: HttpStatusCode = HttpStatusCode.OK) = MockEngine {
        respond(
            content = body,
            status = status,
            headers = headersOf(HttpHeaders.ContentType, "application/json"),
        )
    }

    // ── Le chemin nominal ───────────────────────────────────────────────────

    @Test
    fun aKnownSiret_yieldsTheCompanyName() = runTest {
        val result = serviceWith(jsonEngine(payload)).lookup(siret)

        val verified = assertIs<SireneLookupResult.Verified>(result)
        assertEquals("YOUSSOUFI DEVOPS & CLOUD", verified.company.companyName)
        assertEquals(siret, verified.company.siret)
    }

    /**
     * Le SIRET interrogé est celui du formulaire, pas celui que l'API renvoie : c'est lui qui
     * identifie l'établissement saisi, quand la fiche rendue décrit l'unité légale.
     */
    @Test
    fun theQuerySendsTheSiret() = runTest {
        val engine = jsonEngine(payload)
        serviceWith(engine).lookup(siret)

        val request = engine.requestHistory.single()
        assertEquals(siret, request.url.parameters["q"])
        assertTrue(request.url.toString().startsWith("https://sirene.test/search"))
    }

    /**
     * Tolérance aux champs inconnus — ce test est la raison d'être du [kotlinx.serialization.json.Json]
     * dédié du service. L'API ajoute des champs au fil de ses versions ; sans cette tolérance,
     * chacune de ses évolutions ferait passer l'inscription pour un répertoire en panne.
     */
    @Test
    fun unknownFields_doNotBreakTheParsing() = runTest {
        val enriched = """
            {"results":[{"nom_complet":"ACME SAS","champ_invente_en_2027":{"a":1},"autre":[1,2,3]}]}
        """.trimIndent()

        val verified = assertIs<SireneLookupResult.Verified>(serviceWith(jsonEngine(enriched)).lookup(siret))
        assertEquals("ACME SAS", verified.company.companyName)
    }

    /** Entrepreneur individuel : `nom_raison_sociale` est nul, `nom_complet` porte l'identité. */
    @Test
    fun aMissingLegalName_fallsBackToTheFullName() = runTest {
        val body = """{"results":[{"nom_complet":"MARTIN Claude","nom_raison_sociale":null}]}"""

        val verified = assertIs<SireneLookupResult.Verified>(serviceWith(jsonEngine(body)).lookup(siret))
        assertEquals("MARTIN Claude", verified.company.companyName)
    }

    @Test
    fun aMissingFullName_fallsBackToTheLegalName() = runTest {
        val body = """{"results":[{"nom_complet":null,"nom_raison_sociale":"ACME SAS"}]}"""

        val verified = assertIs<SireneLookupResult.Verified>(serviceWith(jsonEngine(body)).lookup(siret))
        assertEquals("ACME SAS", verified.company.companyName)
    }

    // ── « Le répertoire dit non » ───────────────────────────────────────────

    /** Un SIRET inconnu ne produit pas d'erreur HTTP : l'API répond 200 avec une liste vide. */
    @Test
    fun anEmptyResultList_isNotFound() = runTest {
        val result = serviceWith(jsonEngine("""{"results":[],"total_results":0}""")).lookup(siret)

        assertEquals(SireneLookupResult.NotFound, result)
    }

    @Test
    fun a404_isNotFound() = runTest {
        val engine = MockEngine { respondError(HttpStatusCode.NotFound) }

        assertEquals(SireneLookupResult.NotFound, serviceWith(engine).lookup(siret))
    }

    /** Une fiche sans libellé explicite se replie sur la désignation générique ENTREPRISE $siren. */
    @Test
    fun aResultWithoutAnyName_fallsBackToGenericName() = runTest {
        val body = """{"results":[{"nom_complet":null,"nom_raison_sociale":"   "}]}"""

        val result = serviceWith(jsonEngine(body)).lookup(siret)
        val verified = assertIs<SireneLookupResult.Verified>(result)
        assertEquals("ENTREPRISE ${siret.take(9)}", verified.company.companyName)
    }

    // ── « Le répertoire n'a pas répondu » ───────────────────────────────────

    /**
     * Une panne serveur doit **lever**, jamais rendre `NotFound` : c'est ce qui permet à l'écran
     * d'afficher « réessayez » au lieu d'accuser à tort le SIRET de l'utilisateur.
     */
    @Test
    fun aServerError_throwsRatherThanReportingAnUnknownSiret() = runTest {
        val engine = MockEngine { respondError(HttpStatusCode.InternalServerError) }

        assertFailsWith<IllegalStateException> { serviceWith(engine).lookup(siret) }
    }

    /** Panne de transport (avion, tunnel, DNS) — l'exception traverse le service intacte. */
    @Test
    fun aNetworkFailure_propagates() = runTest {
        val engine = MockEngine { throw OfflineException() }

        assertFailsWith<OfflineException> { serviceWith(engine).lookup(siret) }
    }

    /** Exception de transport locale au test : `kotlin.io.IOException` n'existe pas en commonTest. */
    private class OfflineException : RuntimeException("réseau injoignable")

    /** Un corps illisible est un incident technique, pas une réponse négative du répertoire. */
    @Test
    fun aMalformedBody_throws() = runTest {
        val engine = jsonEngine("<html>503 Service Unavailable</html>")

        assertFailsWith<Exception> { serviceWith(engine).lookup(siret) }
    }
}
