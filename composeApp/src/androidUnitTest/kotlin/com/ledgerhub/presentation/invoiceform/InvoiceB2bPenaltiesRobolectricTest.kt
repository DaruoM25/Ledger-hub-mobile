package com.ledgerhub.presentation.invoiceform

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.runComposeUiTest
import com.ledgerhub.domain.i18n.AppLanguage
import com.ledgerhub.domain.i18n.AppTranslations
import com.ledgerhub.domain.i18n.StringKey
import com.ledgerhub.presentation.i18n.LocalAppLanguage
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test

/**
 * Niveau 3a (US-16) — validation sémantique de la case « pénalités B2B » et de l'alternance du
 * pied de page légal, sous Robolectric (JVM, exécutable en CI sans émulateur).
 *
 * La bascule passe par `performSemanticsAction(OnClick)` : c'est le chemin qu'emprunte un service
 * d'accessibilité, et le seul fiable ici — l'US-15 a montré que l'injection tactile synthétique
 * n'atteint pas toujours le `Modifier.clickable` sous Robolectric. Le geste réel du doigt est
 * couvert sur Pixel 5 par `InvoiceB2bPenaltiesInstrumentedTest`.
 *
 * Assertions en [assertIsDisplayed] uniquement, jamais `assertExists`.
 *
 * Les textes attendus sont **résolus depuis `AppTranslations`**, jamais réécrits en dur : la
 * formulation réglementaire est verrouillée une seule fois, par la sentinelle N1.
 */
@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalTestApi::class)
class InvoiceB2bPenaltiesRobolectricTest {

    private fun legalMention(language: AppLanguage = AppLanguage.FR) =
        AppTranslations.get(StringKey.B2B_LEGAL_MENTION, language)

    private fun courtesyMention(language: AppLanguage = AppLanguage.FR) =
        AppTranslations.get(StringKey.B2B_COURTESY_MENTION, language)

    // ── Mode Formulaire ─────────────────────────────────────────────────────

    @Test
    fun onOpening_theBoxIsCheckedAndTheLegalMentionIsShown() = runComposeUiTest {
        setContent { InvoiceFormScreen(viewModel = InvoiceFormViewModel()) }

        onNodeWithTag(InvoiceFormTags.B2B_PENALTIES_CHECKBOX).performScrollTo().assertIsDisplayed()
        onNodeWithTag(InvoiceFormTags.B2B_PENALTIES_CHECKBOX).assertIsOn()
        onNodeWithTag(InvoiceFormTags.LEGAL_FOOTER).performScrollTo()
            .assertTextEquals(legalMention())
    }

    /**
     * Le cœur de l'US-16 : un clic commute **immédiatement** le pied de page entre la mention de
     * l'article L.441-10 et la formule de courtoisie, sans recharger ni revalider quoi que ce soit.
     */
    @Test
    fun clickingTheBox_alternatesBetweenTheLegalMentionAndTheCourtesyLine() = runComposeUiTest {
        setContent { InvoiceFormScreen(viewModel = InvoiceFormViewModel()) }

        onNodeWithTag(InvoiceFormTags.LEGAL_FOOTER).performScrollTo().assertTextEquals(legalMention())

        onNodeWithTag(InvoiceFormTags.B2B_PENALTIES_CHECKBOX).performScrollTo().toggleBox()

        onNodeWithTag(InvoiceFormTags.B2B_PENALTIES_CHECKBOX).assertIsOff()
        onNodeWithTag(InvoiceFormTags.LEGAL_FOOTER).performScrollTo().assertTextEquals(courtesyMention())

        onNodeWithTag(InvoiceFormTags.B2B_PENALTIES_CHECKBOX).performScrollTo().toggleBox()

        onNodeWithTag(InvoiceFormTags.B2B_PENALTIES_CHECKBOX).assertIsOn()
        onNodeWithTag(InvoiceFormTags.LEGAL_FOOTER).performScrollTo().assertTextEquals(legalMention())
    }

    /** Le pied de page n'est jamais vide : il porte toujours l'un des deux textes. */
    @Test
    fun theLegalFooter_isNeverEmpty_inEitherState() = runComposeUiTest {
        setContent { InvoiceFormScreen(viewModel = InvoiceFormViewModel()) }

        onNodeWithTag(InvoiceFormTags.LEGAL_FOOTER).performScrollTo().assertIsDisplayed()

        onNodeWithTag(InvoiceFormTags.B2B_PENALTIES_CHECKBOX).performScrollTo().toggleBox()

        onNodeWithTag(InvoiceFormTags.LEGAL_FOOTER).performScrollTo().assertIsDisplayed()
    }

    // ── Mode Page Blanche ───────────────────────────────────────────────────

    /**
     * La feuille blanche porte le même contrôle et le même pied : les deux modes sont deux vues
     * d'un seul état, jamais deux implémentations.
     */
    @Test
    fun onTheBlankPage_theSameBoxDrivesTheSameFooter() = runComposeUiTest {
        setContent { InvoiceFormScreen(viewModel = InvoiceFormViewModel()) }

        onNodeWithTag(InvoiceFormTags.modeSegmentTag(InvoiceFormMode.BLANK_PAGE)).toggleBox()
        onNodeWithTag(InvoicePaperCanvasTags.CANVAS).assertIsDisplayed()

        onNodeWithTag(InvoiceFormTags.LEGAL_FOOTER).performScrollTo().assertTextEquals(legalMention())
        onNodeWithTag(InvoiceFormTags.B2B_PENALTIES_CHECKBOX).performScrollTo().assertIsOn()

        onNodeWithTag(InvoiceFormTags.B2B_PENALTIES_CHECKBOX).performScrollTo().toggleBox()

        onNodeWithTag(InvoiceFormTags.LEGAL_FOOTER).performScrollTo().assertTextEquals(courtesyMention())
    }

    /**
     * Le choix survit à la bascule de mode : décoché sur la feuille, il reste décoché au retour
     * dans le formulaire. Le drapeau vit dans l'état, pas dans la vue.
     */
    @Test
    fun theChoice_survivesSwitchingBetweenModes() = runComposeUiTest {
        setContent { InvoiceFormScreen(viewModel = InvoiceFormViewModel()) }

        onNodeWithTag(InvoiceFormTags.B2B_PENALTIES_CHECKBOX).performScrollTo().toggleBox()

        onNodeWithTag(InvoiceFormTags.modeSegmentTag(InvoiceFormMode.BLANK_PAGE)).toggleBox()
        onNodeWithTag(InvoiceFormTags.B2B_PENALTIES_CHECKBOX).performScrollTo().assertIsOff()
        onNodeWithTag(InvoiceFormTags.LEGAL_FOOTER).performScrollTo().assertTextEquals(courtesyMention())

        onNodeWithTag(InvoiceFormTags.modeSegmentTag(InvoiceFormMode.CLASSIC)).toggleBox()
        onNodeWithTag(InvoiceFormTags.B2B_PENALTIES_CHECKBOX).performScrollTo().assertIsOff()
    }

    // ── Immuabilité fiscale ─────────────────────────────────────────────────

    /**
     * Pendant une écriture, le formulaire est verrouillé ([InvoiceFormUiState.isFormEnabled]) :
     * les mentions légales le sont aussi. On ne retouche pas l'article L.441-10 d'une facture en
     * cours de dépôt.
     */
    @Test
    fun whileTheFormIsLocked_theBoxIsDisabled() = runComposeUiTest {
        setContent {
            InvoiceFormContent(
                uiState = InvoiceFormUiState(submissionStatus = SubmissionStatus.Loading),
                onIntent = {},
            )
        }

        onNodeWithTag(InvoiceFormTags.B2B_PENALTIES_CHECKBOX).performScrollTo().assertIsNotEnabled()
    }

    // ── Internationalisation ────────────────────────────────────────────────

    @Test
    fun inEnglish_bothMentionsAreTranslated() = runComposeUiTest {
        setContent {
            CompositionLocalProvider(LocalAppLanguage provides AppLanguage.EN) {
                InvoiceFormScreen(viewModel = InvoiceFormViewModel())
            }
        }

        onNodeWithTag(InvoiceFormTags.LEGAL_FOOTER).performScrollTo()
            .assertTextEquals(legalMention(AppLanguage.EN))

        onNodeWithTag(InvoiceFormTags.B2B_PENALTIES_CHECKBOX).performScrollTo().toggleBox()

        onNodeWithTag(InvoiceFormTags.LEGAL_FOOTER).performScrollTo()
            .assertTextEquals(courtesyMention(AppLanguage.EN))
    }
}

/**
 * Actionne un nœud cochable ou sélectionnable par son action sémantique `OnClick`.
 *
 * `performClick()` — injection tactile synthétique — s'est révélé sans effet sur certains
 * composants Material 3 sous Robolectric (voir US-15). L'action sémantique est déterministe et
 * correspond à ce que déclenche un service d'accessibilité.
 */
private fun SemanticsNodeInteraction.toggleBox(): SemanticsNodeInteraction =
    performSemanticsAction(SemanticsActions.OnClick)
