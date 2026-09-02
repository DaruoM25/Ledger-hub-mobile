package com.ledgerhub.presentation.export

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import com.ledgerhub.domain.export.AccountingArchive
import com.ledgerhub.domain.export.ExportFormat
import com.ledgerhub.domain.export.ExportPeriod
import com.ledgerhub.domain.i18n.AppLanguage
import com.ledgerhub.presentation.i18n.LocalAppLanguage
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Niveau 3a (US-22) — **géométrie** de la feuille d'export, au gabarit exact d'un Pixel 5.
 *
 * ## Pourquoi ce test existe
 *
 * Le bouton du bas de la feuille — « Générer l'archive » au repos, « Télécharger l'archive (.zip) »
 * à l'arrivée — a été livré rogné sous le bas de l'écran de l'appareil cible **trois passes QA de
 * suite**, sans qu'aucun test JVM ne bronche. À chaque fois le défaut n'a été vu qu'après avoir
 * allumé un émulateur, et à chaque fois il s'est manifesté par un `assertIsDisplayed` en échec qui
 * ne disait rien de sa cause.
 *
 * ## Ce qu'il éprouve vraiment
 *
 * Pas une hauteur — **une joignabilité**. La cause racine n'était pas que la feuille fût trop
 * haute : mesurée sur l'appareil, elle faisait 536 dp sur un écran de 851. Elle était *ancrée en
 * bas d'une fenêtre qui déborde derrière les barres système*, et son contenu tenait dans son
 * conteneur défilant — or un `verticalScroll` dont le contenu tient a un `maxValue` de zéro :
 * Compose y désactive le défilement. Le bouton était donc sous la barre système, et **rien** ne
 * pouvait l'en sortir, ni `performScrollTo()` ni le doigt de l'utilisateur.
 *
 * D'où la forme de ces tests : ils font défiler jusqu'au bouton et exigent qu'il s'affiche. C'est
 * exactement le geste qui échouait sur l'appareil, et il échoue désormais sur la JVM.
 *
 * ## Ce que ce niveau ne prouve pas
 *
 * Robolectric ne mesure pas le texte fidèlement — ses métriques de police sont simulées, et toute
 * ligne y occupe 36 px quelle que soit sa taille de style. La feuille y est donc plus haute que sur
 * l'appareil, ce qui rend ces tests **conservateurs** : ce qui reste joignable sous des lignes de
 * 36 px l'est a fortiori sous des lignes réelles. Le rendu de l'appareil, lui, relève du niveau 3b.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w393dp-h851dp")
@OptIn(ExperimentalTestApi::class)
class ExportModalGeometryRobolectricTest {

    /**
     * Hauteur maximale admise, en pixels Robolectric (densité 1 : 1 px = 1 dp).
     *
     * Le plafond posé sur la feuille (`SheetMaxHeight`, 560 dp), plus la tolérance d'un pixel
     * d'arrondi. Une feuille qui le dépasserait aurait perdu son plafond — et avec lui son
     * défilement, donc la joignabilité de son bouton du bas.
     */
    private val heightBudget = 561f

    private val period = ExportPeriod(from = "2026-01-01", to = "2026-09-02")

    private fun readyState() = ExportUiState(
        period = period,
        stage = ExportStage.READY,
        progress = 1f,
        archive = AccountingArchive(
            fileName = "820329331FEC20260902.txt",
            mimeType = "text/plain",
            content = "JournalCode",
            format = ExportFormat.FEC_OFFICIAL,
            documentCount = 2,
        ),
    )

    private fun idleState() = ExportUiState(period = period)

    // ── Joignabilité : le geste même qui échouait sur l'appareil ────────────

    /**
     * L'état `READY` est le plus haut des trois : la confirmation d'archive s'y **ajoute** au
     * bouton de téléchargement au lieu de le remplacer. C'est celui qui débordait.
     */
    @Test
    fun theDownloadButton_isReachableOnAPixel5() = runComposeUiTest {
        setContent {
            CompositionLocalProvider(LocalAppLanguage provides AppLanguage.FR) {
                ExportModalContent(uiState = readyState(), onIntent = {}, onDismiss = {})
            }
        }

        onNodeWithTag(ExportModalTags.DOWNLOAD_BTN).performScrollTo().assertIsDisplayed()
    }

    /** L'anglais est plus long sur plusieurs libellés : un gabarit tenu dans une langue ne dit rien de l'autre. */
    @Test
    fun theDownloadButton_isReachableInEnglishToo() = runComposeUiTest {
        setContent {
            CompositionLocalProvider(LocalAppLanguage provides AppLanguage.EN) {
                ExportModalContent(uiState = readyState(), onIntent = {}, onDismiss = {})
            }
        }

        onNodeWithTag(ExportModalTags.DOWNLOAD_BTN).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun theGenerateButton_isReachableAtRest() = runComposeUiTest {
        setContent {
            CompositionLocalProvider(LocalAppLanguage provides AppLanguage.FR) {
                ExportModalContent(uiState = idleState(), onIntent = {}, onDismiss = {})
            }
        }

        onNodeWithTag(ExportModalTags.GENERATE_BTN).performScrollTo().assertIsDisplayed()
    }

    /** Les trois cartes de format doivent l'être aussi : c'est par elles que passe le choix. */
    @Test
    fun everyFormatCard_isReachableAtRest() = runComposeUiTest {
        setContent {
            CompositionLocalProvider(LocalAppLanguage provides AppLanguage.FR) {
                ExportModalContent(uiState = idleState(), onIntent = {}, onDismiss = {})
            }
        }

        ExportFormat.entries.forEach { format ->
            onNodeWithTag(ExportModalTags.format(format)).performScrollTo().assertIsDisplayed()
        }
    }

    // ── Plafond : ce qui rend le défilement possible ────────────────────────

    /**
     * Le plafond n'est pas cosmétique : sans lui, le contenu tient dans son conteneur, le
     * `verticalScroll` a un `maxValue` de zéro, et plus rien ne défile. Le vérifier, c'est
     * vérifier que la joignabilité ci-dessus repose sur autre chose que de la chance.
     */
    @Test
    fun theSheet_staysUnderItsHeightCap() = runComposeUiTest {
        setContent {
            CompositionLocalProvider(LocalAppLanguage provides AppLanguage.FR) {
                ExportModalContent(uiState = readyState(), onIntent = {}, onDismiss = {})
            }
        }

        val height = onNodeWithTag(ExportModalTags.DIALOG).fetchSemanticsNode().size.height

        assertTrue(
            height <= heightBudget,
            "Feuille au-delà de son plafond : $height dp (max $heightBudget dp). " +
                "Sans plafond, le défilement s'éteint et le bouton du bas devient injoignable.",
        )
    }
}
