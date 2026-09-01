package com.ledgerhub.domain.integrations

import com.ledgerhub.domain.i18n.AppLanguage
import com.ledgerhub.domain.i18n.AppTranslations
import kotlin.test.Test
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Niveau 1 (US-20) — sentinelles i18n du catalogue.
 *
 * L'invariant de parité (`AppTranslationsTest`) garantit qu'une clé existe dans les deux langues.
 * Il ne dit rien de la **traduction effective** : une entrée anglaise recopiée du français passe
 * l'invariant sans broncher. C'est précisément ce que ce test attrape, module par module.
 */
class IntegrationModuleI18nTest {

    @Test
    fun everyModule_isFullyTranslatedInBothLanguages() {
        IntegrationModule.entries.forEach { module ->
            AppLanguage.entries.forEach { language ->
                val title = AppTranslations.get(module.titleKey, language)
                val description = AppTranslations.get(module.descriptionKey, language)
                assertTrue(title.isNotBlank(), "Titre vide pour $module / $language")
                assertTrue(description.isNotBlank(), "Description vide pour $module / $language")
            }
        }
    }

    /**
     * Les descriptions doivent différer d'une langue à l'autre. Les **titres** en sont exclus :
     * « Notifications Slack » se dit presque de la même façon en anglais, et l'exiger différent
     * pousserait à traduire pour traduire.
     */
    @Test
    fun everyDescription_differsBetweenFrenchAndEnglish() {
        IntegrationModule.entries.forEach { module ->
            assertNotEquals(
                AppTranslations.get(module.descriptionKey, AppLanguage.FR),
                AppTranslations.get(module.descriptionKey, AppLanguage.EN),
                "Description non traduite pour $module",
            )
        }
    }

    @Test
    fun everyBadge_isTranslatedInBothLanguages() {
        IntegrationStatus.entries.forEach { status ->
            AppLanguage.entries.forEach { language ->
                assertTrue(
                    AppTranslations.get(status.badgeKey, language).isNotBlank(),
                    "Badge vide pour $status / $language",
                )
            }
            assertNotEquals(
                AppTranslations.get(status.badgeKey, AppLanguage.FR),
                AppTranslations.get(status.badgeKey, AppLanguage.EN),
                "Badge non traduit pour $status",
            )
        }
    }

    /**
     * Les descriptions tiennent en trois lignes sur une carte de 170 dp de large. Au-delà, elles
     * sont tronquées à l'affichage : la limite est donc vérifiée ici plutôt que découverte sur la
     * capture QA.
     */
    @Test
    fun everyDescription_staysShortEnoughForACard() {
        IntegrationModule.entries.forEach { module ->
            AppLanguage.entries.forEach { language ->
                val description = AppTranslations.get(module.descriptionKey, language)
                assertTrue(
                    description.length <= 70,
                    "Description trop longue pour une carte ($module / $language, " +
                        "${description.length} caractères) : $description",
                )
            }
        }
    }
}
