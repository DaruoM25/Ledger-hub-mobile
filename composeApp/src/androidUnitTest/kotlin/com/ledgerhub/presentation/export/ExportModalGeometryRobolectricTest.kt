package com.ledgerhub.presentation.export

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
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
 * Niveau 3a (US-22) — **budget de hauteur** de la feuille d'export, au gabarit exact d'un Pixel 5.
 *
 * ## Pourquoi ce test existe
 *
 * La feuille a débordé par le bas de l'écran de l'appareil cible à deux reprises, et les deux fois
 * le défaut n'a été vu qu'après une passe QA sur émulateur : le bouton du bas — « Générer
 * l'archive » au repos, « Télécharger l'archive (.zip) » à l'arrivée — s'y trouvait rogné, et trois
 * tests instrumentés tombaient sur un `assertIsDisplayed` sans rien dire de la cause.
 *
 * Une hauteur se mesure ; elle n'a pas à s'estimer. Ce test la mesure **sur la JVM**, au gabarit
 * déclaré d'un Pixel 5 (393 × 851 dp), et échoue avant l'émulateur. Il vise délibérément l'état
 * `READY`, le plus haut des trois : la confirmation d'archive s'y **ajoute** au bouton de
 * téléchargement au lieu de le remplacer, ce qui est précisément ce que les deux régressions
 * avaient manqué.
 *
 * ## Ce que ce test vaut, et ce qu'il ne vaut pas
 *
 * Robolectric **ne mesure pas le texte fidèlement** : ses métriques de police sont simulées, et
 * toute ligne y occupe 36 px quelle que soit sa taille de style. La feuille y est donc
 * systématiquement plus haute que sur l'appareil — mesurée à 733 px ici contre une feuille qui,
 * sur un Pixel 5, tient dans le même écran.
 *
 * C'est ce qui rend le test utile plutôt que trompeur : il est **conservateur par construction**.
 * Ce qui tient sous des lignes de 36 px tient a fortiori sous des lignes réelles. Il ne prouve pas
 * le rendu de l'appareil — c'est le niveau 3b qui s'en charge — mais il attrape sur la JVM toute
 * section ajoutée qui repousserait le bouton du bas hors de l'écran, ce que les deux régressions
 * précédentes n'ont été vues qu'après une passe QA sur émulateur.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w393dp-h851dp")
@OptIn(ExperimentalTestApi::class)
class ExportModalGeometryRobolectricTest {

    /**
     * Hauteur maximale admise, en pixels Robolectric (densité 1 : 1 px = 1 dp).
     *
     * Les 851 dp de l'écran d'un Pixel 5, moins la barre de statut (~24 dp) et la barre de gestes
     * (~48 dp) que Robolectric ne modèle pas. Le budget porte donc sur la zone que la feuille a
     * réellement le droit d'occuper — celle à laquelle `safeDrawingPadding()` la borne.
     */
    private val heightBudget = 779f

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

    private fun measure(state: ExportUiState, language: AppLanguage): Float {
        var height = 0f
        runComposeUiTest {
            setContent {
                CompositionLocalProvider(LocalAppLanguage provides language) {
                    ExportModalContent(uiState = state, onIntent = {}, onDismiss = {})
                }
            }
            height = onNodeWithTag(ExportModalTags.DIALOG).fetchSemanticsNode().size.height.toFloat()
        }
        return height
    }

    @Test
    fun theSheet_fitsAPixel5_inItsTallestState() {
        val height = measure(readyState(), AppLanguage.FR)

        assertTrue(
            height <= heightBudget,
            "Feuille trop haute pour un Pixel 5 : $height dp (budget $heightBudget dp). " +
                "Le bouton du bas sera rogné sur l'appareil.",
        )
    }

    /**
     * L'anglais est plus long que le français sur plusieurs libellés de cette feuille. Un budget
     * tenu dans une seule langue ne prouve rien pour l'autre.
     */
    @Test
    fun theSheet_fitsAPixel5_inEnglishToo() {
        val height = measure(readyState(), AppLanguage.EN)

        assertTrue(
            height <= heightBudget,
            "Feuille trop haute en anglais : $height dp (budget $heightBudget dp).",
        )
    }

    /** L'état de repos est nécessairement plus court que `READY` — la propriété est vérifiée, pas supposée. */
    @Test
    fun theIdleSheet_isShorterThanTheReadyOne() {
        val idle = measure(ExportUiState(period = period), AppLanguage.FR)
        val ready = measure(readyState(), AppLanguage.FR)

        assertTrue(
            idle < ready,
            "L'état de repos ($idle dp) devrait être plus court que l'état prêt ($ready dp)",
        )
        assertTrue(idle <= heightBudget, "Feuille au repos trop haute : $idle dp")
    }
}
