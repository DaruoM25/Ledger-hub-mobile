package com.ledgerhub.data.sirene

import com.ledgerhub.data.remote.createPlatformHttpClient
import com.ledgerhub.domain.sirene.SireneCompany
import com.ledgerhub.domain.sirene.SireneLookupResult
import com.ledgerhub.domain.sirene.SireneLookupService
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Répertoire SIRENE **réel** (US-26) — API publique « Recherche d'entreprises » de la DINUM,
 * qui expose les données SIRENE de l'INSEE sans clé d'API ni quota déclaré.
 *
 * Remplace [MockSireneLookupService] dans l'application ; la simulation reste en place pour les
 * tests et la démonstration hors ligne. C'est exactement le découplage annoncé par le KDoc de
 * [SireneLookupService] en US-21 : ni le ViewModel ni l'écran ne bougent.
 *
 * ## Ce que « trouvé » veut dire ici
 *
 * L'API répond `200` avec une liste éventuellement **vide** : un SIRET inconnu n'est pas un `404`,
 * c'est un `results: []`. Les deux mènent néanmoins à [SireneLookupResult.NotFound] — du point de
 * vue de l'inscription, « l'API dit qu'elle ne connaît pas ce numéro » et « l'API n'a pas de route
 * pour ce numéro » sont la même réponse.
 *
 * Tout le reste — réseau coupé, DNS, 5xx, JSON illisible — **remonte en exception**, jamais en
 * `NotFound`. C'est le contrat posé en US-21 et il compte : un utilisateur dont le SIRET est
 * correct mais qui est dans le métro ne doit pas lire « SIRET introuvable au répertoire SIRENE ».
 * Le ViewModel traduit cette exception en `UNAVAILABLE`, et l'écran en « réessayez ».
 *
 * ## Pourquoi le JSON est lu à la main plutôt que par ContentNegotiation
 *
 * Le client partagé ([createPlatformHttpClient]) installe `json()` avec sa configuration par
 * défaut, donc `ignoreUnknownKeys = false`. Or cette API renvoie plusieurs dizaines de champs par
 * établissement (dirigeants, finances, complements, matching_etablissements…), et en ajoute au fil
 * de ses versions. Désérialiser avec la configuration par défaut ferait échouer la lecture au
 * premier champ nouveau — c'est-à-dire transformer une évolution de l'API en « répertoire
 * indisponible ». La réponse est donc lue en texte puis décodée par le [Json] tolérant de cette
 * classe, sans toucher à la configuration du client partagé, dont dépend aussi l'authentification.
 */
class KtorSireneLookupService(
    private val httpClient: HttpClient = createPlatformHttpClient(),
    private val baseUrl: String = SEARCH_ENDPOINT,
    private val fallbackOnOffline: Boolean = false,
) : SireneLookupService {

    override suspend fun lookup(siret: String): SireneLookupResult {
        val response = try {
            httpClient.get(baseUrl) {
                parameter("q", siret)
                parameter("page", "1")
                parameter("per_page", "1")
            }
        } catch (t: Throwable) {
            if (fallbackOnOffline) {
                return MockSireneLookupService(simulatedDelayMillis = 200L).lookup(siret)
            }
            throw t
        }

        if (response.status == HttpStatusCode.NotFound) return SireneLookupResult.NotFound

        if (response.status.value !in 200..299) {
            if (fallbackOnOffline) {
                return MockSireneLookupService(simulatedDelayMillis = 200L).lookup(siret)
            }
            error("Le répertoire SIRENE a répondu avec le code HTTP ${response.status.value}.")
        }


        val payload = JSON.decodeFromString(SearchResponse.serializer(), response.bodyAsText())
        val first = payload.results.firstOrNull() ?: return SireneLookupResult.NotFound

        // `nom_complet` est le libellé d'usage (raison sociale des personnes morales, nom et prénom
        // des entrepreneurs individuels). `nom_raison_sociale` sert de repli : il est absent des
        // fiches de personnes physiques, où il vaut `null`.
        val name = first.fullName?.takeIf { it.isNotBlank() }
            ?: first.legalName?.takeIf { it.isNotBlank() }
            ?: return SireneLookupResult.NotFound

        return SireneLookupResult.Verified(
            SireneCompany(
                siret = siret,
                companyName = name,
                // Volontairement `null` : l'API expose `nature_juridique` sous forme de **code**
                // INSEE (« 6540 »), pas de libellé. Or [SireneCompany.legalForm] est documenté
                // comme un champ d'affichage de confort — y verser un code afficherait « 6540 »
                // à l'utilisateur le jour où quelqu'un le branche. Le traduire supposerait la
                // table des catégories juridiques, que personne ne consomme aujourd'hui.
                legalForm = null,
            ),
        )
    }

    @Serializable
    private data class SearchResponse(val results: List<SearchResult> = emptyList())

    @Serializable
    private data class SearchResult(
        @kotlinx.serialization.SerialName("nom_complet") val fullName: String? = null,
        @kotlinx.serialization.SerialName("nom_raison_sociale") val legalName: String? = null,
    )

    companion object {
        /** API « Recherche d'entreprises » de la DINUM — publique, sans authentification. */
        const val SEARCH_ENDPOINT = "https://recherche-entreprises.api.gouv.fr/search"

        /**
         * Tolérant aux champs inconnus **par nécessité**, pas par confort : voir le KDoc de la
         * classe. `isLenient` reste à sa valeur par défaut — on accepte des champs en plus, pas du
         * JSON malformé.
         */
        private val JSON = Json { ignoreUnknownKeys = true }
    }
}
