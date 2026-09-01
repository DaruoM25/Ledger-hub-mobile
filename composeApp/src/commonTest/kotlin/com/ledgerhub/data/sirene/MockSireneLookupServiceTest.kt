package com.ledgerhub.data.sirene

import com.ledgerhub.domain.sirene.SireneLookupResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Niveau 1 (US-21) — répertoire SIRENE simulé.
 *
 * Le temps est **virtuel** (`runTest`) : la suite ne paie pas la seconde de vérification, mais
 * `testScheduler.currentTime` permet malgré tout de prouver qu'elle est bien écoulée. C'est ce qui garantit à
 * l'indicateur de chargement un intervalle où exister — sans quoi il ne serait qu'un ornement
 * jamais rendu.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MockSireneLookupServiceTest {

    @Test
    fun aKnownSiret_resolvesToTheDemonstrationCompany() = runTest {
        val result = MockSireneLookupService(simulatedDelayMillis = 0L)
            .lookup(MockSireneLookupService.DEMO_SIRET)

        val verified = assertIs<SireneLookupResult.Verified>(result)
        assertEquals("Youssoufi DevOps & Cloud EURL", verified.company.companyName)
        assertEquals(MockSireneLookupService.DEMO_SIRET, verified.company.siret)
        assertEquals("EURL", verified.company.legalForm)
    }

    /** Toute recette doit aboutir, quel que soit le numéro tapé sur le clavier de l'émulateur. */
    @Test
    fun anUnseededSiret_stillResolvesToTheDefaultCompany() = runTest {
        val result = MockSireneLookupService(simulatedDelayMillis = 0L).lookup("48301002000015")

        val verified = assertIs<SireneLookupResult.Verified>(result)
        assertEquals(MockSireneLookupService.DEFAULT_COMPANY_NAME, verified.company.companyName)
        assertEquals("48301002000015", verified.company.siret)
    }

    @Test
    fun aSecondSeededSiret_resolvesToItsOwnCompany() = runTest {
        val result = MockSireneLookupService(simulatedDelayMillis = 0L).lookup("73282932000074")

        val verified = assertIs<SireneLookupResult.Verified>(result)
        assertEquals("RENAULT SAS", verified.company.companyName)
    }

    @Test
    fun theReservedSiret_isReportedAsNotFound() = runTest {
        val result = MockSireneLookupService(simulatedDelayMillis = 0L)
            .lookup(MockSireneLookupService.UNKNOWN_SIRET)

        assertEquals(SireneLookupResult.NotFound, result)
    }

    // ── Durée de la vérification ────────────────────────────────────────────

    @Test
    fun theLookup_takesTheAnnouncedSecond() = runTest {
        val service = MockSireneLookupService()
        val startedAt = testScheduler.currentTime

        service.lookup(MockSireneLookupService.DEMO_SIRET)

        assertEquals(
            MockSireneLookupService.DEFAULT_DELAY_MILLIS,
            testScheduler.currentTime - startedAt,
            "La vérification doit durer la seconde annoncée par l'US-21",
        )
        assertEquals(1_000L, MockSireneLookupService.DEFAULT_DELAY_MILLIS)
    }

    @Test
    fun theDelay_isInjectable_soTestsDoNotPayForIt() = runTest {
        val startedAt = testScheduler.currentTime

        MockSireneLookupService(simulatedDelayMillis = 0L).lookup(MockSireneLookupService.DEMO_SIRET)

        assertEquals(0L, testScheduler.currentTime - startedAt)
    }

    /** Le SIRET de démonstration respecte la clé de Luhn, bien que rien ne l'y oblige. */
    @Test
    fun theDemonstrationSiret_isFourteenDigits() {
        assertEquals(14, MockSireneLookupService.DEMO_SIRET.length)
        assertTrue(MockSireneLookupService.DEMO_SIRET.all { it in '0'..'9' })
    }
}
