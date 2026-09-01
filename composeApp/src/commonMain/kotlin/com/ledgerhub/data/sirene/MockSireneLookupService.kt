package com.ledgerhub.data.sirene

import com.ledgerhub.domain.sirene.SireneCompany
import com.ledgerhub.domain.sirene.SireneLookupResult
import com.ledgerhub.domain.sirene.SireneLookupService
import kotlinx.coroutines.delay

/**
 * Répertoire SIRENE simulé (US-21) — aucun appel réseau à ce stade, calqué sur
 * [com.ledgerhub.data.directory.MockDirectoryRepository].
 *
 * Le délai n'est pas de la décoration : c'est lui qui donne au `CircularProgressIndicator` du
 * champ SIRET un intervalle où exister. Sans lui, l'écran passerait de « vide » à « vérifié » sans
 * que rien ne dise à l'utilisateur qu'une interrogation a eu lieu.
 *
 * @param simulatedDelayMillis 1 s par défaut — la durée annoncée par le cahier des charges.
 *   Injectable pour que les tests ne paient pas cette seconde, ni ne courent contre elle.
 */
class MockSireneLookupService(
    private val simulatedDelayMillis: Long = DEFAULT_DELAY_MILLIS,
) : SireneLookupService {

    override suspend fun lookup(siret: String): SireneLookupResult {
        delay(simulatedDelayMillis)

        if (siret == UNKNOWN_SIRET) return SireneLookupResult.NotFound

        val company = SEED[siret] ?: SireneCompany(
            siret = siret,
            companyName = DEFAULT_COMPANY_NAME,
            legalForm = "EURL",
        )
        return SireneLookupResult.Verified(company)
    }

    companion object {
        /** Durée annoncée par l'US-21 pour la vérification. */
        const val DEFAULT_DELAY_MILLIS = 1_000L

        /**
         * SIRET de démonstration de l'inscription. Clé de Luhn valide, bien que le déclenchement
         * ne l'exige pas (voir [com.ledgerhub.domain.sirene.SiretInput]) : la valeur qui sert de
         * vitrine n'a aucune raison d'être bancale.
         */
        const val DEMO_SIRET = "90123456700013"

        /** Raison sociale reportée dans le formulaire — la démonstration de l'US-21. */
        const val DEFAULT_COMPANY_NAME = "Youssoufi DevOps & Cloud EURL"

        /**
         * SIRET réservé au cas « absent du répertoire ». Sans lui, la branche [SireneLookupResult.NotFound]
         * ne serait atteignable qu'à travers une fausse implémentation — donc jamais éprouvée sur
         * le service réellement câblé dans l'application.
         */
        const val UNKNOWN_SIRET = "00000000000000"

        /**
         * Fiches nommées. Tout autre SIRET de 14 chiffres répond [DEFAULT_COMPANY_NAME] : une
         * inscription de démonstration doit aboutir quel que soit le numéro saisi en recette.
         */
        private val SEED: Map<String, SireneCompany> = mapOf(
            DEMO_SIRET to SireneCompany(DEMO_SIRET, DEFAULT_COMPANY_NAME, "EURL"),
            "73282932000074" to SireneCompany("73282932000074", "RENAULT SAS", "SAS"),
        )
    }
}
