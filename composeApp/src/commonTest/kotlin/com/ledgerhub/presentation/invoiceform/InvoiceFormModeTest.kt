package com.ledgerhub.presentation.invoiceform

import com.ledgerhub.domain.i18n.AppLanguage
import com.ledgerhub.domain.i18n.AppTranslations
import com.ledgerhub.domain.i18n.StringKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Niveau 1 (US-15) — contrat du sélecteur de mode de saisie.
 *
 * Le mode est un **type**, pas un booléen, et surtout pas un champ de [InvoiceFormUiState] :
 * ces tests verrouillent son ordre d'affichage, son défaut et son étiquetage bilingue, seuls
 * éléments dont dépendent l'écran et les tests d'interaction (N3a/N3b).
 */
class InvoiceFormModeTest {

    @Test
    fun theSelectorOffersExactlyTwoModes_formFirst() {
        // L'ordre est celui des segments à l'écran : le formulaire classique reste l'entrée
        // par défaut, la page blanche est le mode que l'on choisit délibérément.
        assertEquals(
            listOf(InvoiceFormMode.CLASSIC, InvoiceFormMode.BLANK_PAGE),
            InvoiceFormMode.entries,
        )
        assertEquals(InvoiceFormMode.CLASSIC, InvoiceFormMode.entries.first())
    }

    @Test
    fun eachMode_carriesItsOwnTranslationKey() {
        val keys = InvoiceFormMode.entries.map { it.labelKey }
        assertEquals(keys.size, keys.toSet().size, "Deux modes ne peuvent pas partager le même libellé")
    }

    /**
     * Libellés imposés par la spécification US-15 : figés par sentinelle plutôt que laissés à
     * l'appréciation d'une relecture, exactement comme les statuts réglementaires de l'US-13.
     */
    @Test
    fun modeLabels_useTheSpecifiedWording_inBothLanguages() {
        assertEquals(
            "Mode Formulaire",
            AppTranslations.get(InvoiceFormMode.CLASSIC.labelKey, AppLanguage.FR),
        )
        assertEquals(
            "Form Mode",
            AppTranslations.get(InvoiceFormMode.CLASSIC.labelKey, AppLanguage.EN),
        )
        assertEquals(
            "Mode Page Blanche",
            AppTranslations.get(InvoiceFormMode.BLANK_PAGE.labelKey, AppLanguage.FR),
        )
        assertEquals(
            "Blank Page Mode",
            AppTranslations.get(InvoiceFormMode.BLANK_PAGE.labelKey, AppLanguage.EN),
        )
    }

    /**
     * Parité stricte FR/EN sur le périmètre US-15 — l'invariant global est vérifié par
     * `AppTranslationsTest` ; ce test cible les clés introduites par cette US, y compris celles
     * de la feuille blanche, qui vient de sortir du « hors périmètre i18n ».
     */
    @Test
    fun everyUs15Key_isTranslatedAndDistinct_inBothLanguages() {
        val us15Keys = listOf(
            StringKey.FORM_MODE_SELECTOR_LABEL,
            StringKey.FORM_MODE_CLASSIC,
            StringKey.FORM_MODE_BLANK_PAGE,
            StringKey.PAPER_VAT_LABEL,
            StringKey.PAPER_VAT_BASE_ON,
        )

        us15Keys.forEach { key ->
            AppLanguage.entries.forEach { language ->
                assertTrue(
                    AppTranslations.get(key, language).isNotBlank(),
                    "Traduction manquante ou vide pour $key / $language",
                )
            }
        }

        // Les deux modes doivent rester distinguables à l'écran dans chaque langue : deux
        // segments portant le même texte rendraient le sélecteur inutilisable.
        AppLanguage.entries.forEach { language ->
            assertTrue(
                AppTranslations.get(StringKey.FORM_MODE_CLASSIC, language) !=
                    AppTranslations.get(StringKey.FORM_MODE_BLANK_PAGE, language),
                "Libellés de mode identiques en $language",
            )
        }
    }
}
