package com.ledgerhub.presentation.invoices

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ledgerhub.domain.audit.AuditEntry
import com.ledgerhub.domain.audit.AuditMilestoneId
import com.ledgerhub.domain.audit.buildAuditTimeline
import com.ledgerhub.domain.audit.fingerprintSha256
import com.ledgerhub.domain.i18n.AppLanguage
import com.ledgerhub.domain.i18n.AppTranslations
import com.ledgerhub.domain.i18n.StringKey
import com.ledgerhub.domain.invoice.Invoice
import com.ledgerhub.domain.invoice.InvoiceLine
import com.ledgerhub.domain.invoice.InvoiceStatus
import com.ledgerhub.domain.invoice.Money
import com.ledgerhub.domain.invoice.Party
import com.ledgerhub.domain.invoice.VatRate
import com.ledgerhub.presentation.invoices.components.AuditTrailTags
import com.ledgerhub.presentation.invoices.components.AuditTrailTimeline
import com.ledgerhub.presentation.invoices.components.InvoicePreviewContent
import com.ledgerhub.presentation.invoices.components.InvoicePreviewDialog
import com.ledgerhub.presentation.invoices.components.InvoicePreviewTags
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.assertTrue

/**
 * Niveau 3b (US-17) — audit tactile et visuel du panneau de traçabilité sur émulateur Pixel 5
 * API 35 (`connectedDebugAndroidTest`).
 *
 * Ce que Robolectric ne peut pas prouver : que le panneau se laisse réellement atteindre au doigt
 * dans l'aperçu, que ses jalons offrent une cible tactile décente, et que les **deux agencements**
 * responsive se comportent comme prévu de part et d'autre du seuil de 840 dp. S'y ajoute l'export
 * de la capture d'écran servant de preuve QA.
 *
 * Assertions en [assertIsDisplayed] uniquement, jamais `assertExists`.
 */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class AuditTrailTimelineInstrumentedTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val screenshotName = "US17_mobile_invoice_audit_trail_sdk_gphone64_x86_64.png"

    private val issuer = Party("Cabinet LedgerHub", "820329331", "82032933100027")
    private val recipient = Party("Boulangerie Moreau SARL", "784102336", "78410233600021")

    /** Facture approuvée : les quatre jalons sont franchis, ce que la capture doit démontrer. */
    private val approvedInvoice = Invoice(
        number = "FAC-2026-0217",
        issueDate = "2026-08-31",
        issuer = issuer,
        recipient = recipient,
        lines = listOf(
            InvoiceLine("Conseil réglementaire PPF", 2, Money(10_000), VatRate.TAUX_NORMAL),
            InvoiceLine("Ouvrage documentaire", 3, Money(1_990), VatRate.TAUX_REDUIT),
        ),
        status = InvoiceStatus.APPROVED,
        dueDate = "2026-09-30",
    )

    private val auditTrail = listOf(
        AuditEntry("a1", "FAC-2026-0217", null, InvoiceStatus.DRAFT, null, "2026-08-30T07:45:00Z"),
        AuditEntry("a2", "FAC-2026-0217", InvoiceStatus.DRAFT, InvoiceStatus.DEPOSITED, null, "2026-08-31T09:00:00Z"),
        AuditEntry("a3", "FAC-2026-0217", InvoiceStatus.DEPOSITED, InvoiceStatus.APPROVED, null, "2026-09-01T10:30:00Z"),
    )

    private fun timeline() = buildAuditTimeline(approvedInvoice, auditTrail)

    /**
     * Le panneau est posé dans une colonne défilante, comme l'écran de détail le fait : composé nu
     * il n'aurait aucun ancêtre scrollable et [performScrollTo] échouerait avant toute assertion.
     */
    private fun renderPanel() {
        composeRule.setContent {
            Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                AuditTrailTimeline(milestones = timeline())
            }
        }
        composeRule.waitForIdle()
    }

    // ── Panneau seul ────────────────────────────────────────────────────────

    @Test
    fun thePanel_showsTheFourMilestonesOnDevice() {
        renderPanel()

        composeRule.onNodeWithTag(AuditTrailTags.PANEL).assertIsDisplayed()
        AuditMilestoneId.entries.forEach { id ->
            composeRule.onNodeWithTag(AuditTrailTags.stepTag(id))
                .performScrollTo()
                .assertIsDisplayed()
        }
        composeRule.onNodeWithTag(AuditTrailTags.SHA256, useUnmergedTree = true)
            .performScrollTo()
            .assertTextContains(approvedInvoice.fingerprintSha256().take(16), substring = true)
    }

    /**
     * Accessibilité tactile : chaque jalon reste une zone lisible et atteignable au doigt. La
     * pastille seule ne fait que 22 dp — c'est la ligne entière qui doit tenir la cible.
     */
    @Test
    fun everyMilestoneRow_meetsTheMinimumTouchTargetHeight() {
        renderPanel()

        AuditMilestoneId.entries.forEach { id ->
            composeRule.onNodeWithTag(AuditTrailTags.stepTag(id))
                .performScrollTo()
                .assertIsDisplayed()
                .assertHeightIsAtLeast(48.dp)
        }
    }

    // ── Agencement responsive ───────────────────────────────────────────────

    /**
     * Sous le seuil « expanded » (téléphone en portrait), feuille et panneau s'empilent : le
     * panneau reste atteignable en faisant défiler la feuille.
     */
    @Test
    fun onACompactWidth_thePanelIsStackedUnderTheSheet_andRemainsReachable() {
        composeRule.setContent {
            Viewport(width = 400.dp, height = 800.dp) { InvoicePreviewDialogContent() }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(InvoicePreviewTags.SHEET).assertIsDisplayed()
        composeRule.onNodeWithTag(AuditTrailTags.PANEL).performScrollTo().assertIsDisplayed()
    }

    /**
     * Au-delà du seuil (tablette, ou téléphone en paysage), les deux tiennent côte à côte : le
     * panneau est visible **sans défilement**, ce qui est tout l'intérêt de l'agencement en
     * colonnes.
     */
    @Test
    fun onAnExpandedWidth_thePanelSitsBesideTheSheet_withoutScrolling() {
        composeRule.setContent {
            Viewport(width = 1_000.dp, height = 800.dp) { InvoicePreviewDialogContent() }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(InvoicePreviewTags.SHEET).assertIsDisplayed()
        composeRule.onNodeWithTag(AuditTrailTags.PANEL).assertIsDisplayed()
        composeRule.onNodeWithTag(AuditTrailTags.stepTag(AuditMilestoneId.CREATED)).assertIsDisplayed()
    }

    /** L'aperçu s'ouvre et se ferme au doigt, panneau compris. */
    @Test
    fun tappingClose_dismissesThePreviewWithItsPanel() {
        // `mutableStateOf`, et non un simple `var` : sans etat observable, `onDismiss` modifie une
        // variable que la composition ne surveille pas. Rien ne recompose, le dialogue reste a
        // l'ecran, et l'echec se lit a tort comme un probleme d'animation alors que le test ne
        // reproduisait tout simplement pas le comportement de l'appelant reel, qui remonte la
        // fermeture dans son etat.
        var dismissed by mutableStateOf(false)
        composeRule.setContent {
            if (!dismissed) {
                InvoicePreviewDialog(
                    invoice = approvedInvoice,
                    onDismiss = { dismissed = true },
                    auditTimeline = timeline(),
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(AuditTrailTags.PANEL).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(InvoicePreviewTags.CLOSE_BUTTON).performTouchInput { click() }

        // Un `Dialog` vit dans sa propre fenetre : sa fermeture passe par le gestionnaire de
        // fenetres et peut survivre d'une image a la recomposition. On attend donc la disparition
        // du noeud plutot que de la supposer acquise apres `waitForIdle`.
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(InvoicePreviewTags.DIALOG).fetchSemanticsNodes().isEmpty()
        }
        composeRule.onNodeWithTag(InvoicePreviewTags.DIALOG).assertDoesNotExist()
    }

    // ── Preuve QA ───────────────────────────────────────────────────────────

    /**
     * Capture officielle de l'US-17 : facture approuvée, les quatre jalons franchis et l'empreinte
     * SHA-256 visible — exactement ce que le panneau doit démontrer.
     *
     * Export sous `US17_mobile_invoice_audit_trail_sdk_gphone64_x86_64.png`, à rapatrier dans
     * `screenshots/` aux côtés des captures US-10 à US-16.
     */
    @Test
    fun exportsTheAuditTrailScreenshot() {
        renderPanel()

        composeRule.onNodeWithTag(AuditTrailTags.PANEL).assertIsDisplayed()
        composeRule.onNodeWithTag(AuditTrailTags.SHA256, useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(AuditTrailTags.stepTag(AuditMilestoneId.STATUS))
            .performScrollTo()
            .assertTextContains(
                AppTranslations.get(StringKey.STATUS_APPROVED, AppLanguage.FR),
                substring = true,
            )
        composeRule.waitForIdle()

        val bitmap = composeRule.onNodeWithTag(AuditTrailTags.PANEL).captureToImage().asAndroidBitmap()

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val output = File(context.getExternalFilesDir(null), screenshotName)
        output.outputStream().use { stream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        }

        assertTrue(output.exists(), "Capture US-17 non écrite : ${output.absolutePath}")
        assertTrue(output.length() > 0, "Capture US-17 vide : ${output.absolutePath}")
    }

    /**
     * Contenu de l'aperçu hors `Dialog` : un dialogue impose sa propre fenêtre plein écran, ce qui
     * empêcherait de contraindre la largeur pour éprouver les deux agencements.
     */
    /**
     * Fenetre de test d'une largeur donnee, **en dp reellement disponibles**.
     *
     * `Modifier.size` seul ne suffit pas : il ramene la taille demandee dans les contraintes
     * recues, donc aux 393 dp de large du Pixel 5. Un `Box(Modifier.size(1000.dp, ...))` y mesurait
     * 393 dp, `InvoicePreviewContent` basculait sur l'agencement empile, et le panneau se
     * retrouvait sous la ligne de flottaison — d'ou l'echec sur un test cense prouver l'inverse.
     * `requiredSize` ne reglerait rien : la colonne de droite sortirait alors de l'ecran physique
     * et resterait invisible.
     *
     * La densite est donc reduite a 1 : la meme dalle de 1080 px expose 1080 dp, de quoi loger
     * 1000 dp sans rien rogner. C'est exactement ce qu'est un grand ecran pour un seuil Material
     * exprime en dp — davantage de dp disponibles — et cela rend le test independant de la
     * definition de l'appareil, la seule contrainte etant une dalle d'au moins [width] pixels.
     */
    @Composable
    private fun Viewport(
        width: androidx.compose.ui.unit.Dp,
        height: androidx.compose.ui.unit.Dp,
        content: @Composable () -> Unit,
    ) {
        CompositionLocalProvider(LocalDensity provides Density(density = 1f, fontScale = 1f)) {
            Box(modifier = Modifier.size(width = width, height = height)) { content() }
        }
    }

    @Composable
    private fun InvoicePreviewDialogContent() {
        InvoicePreviewContent(invoice = approvedInvoice, auditTimeline = timeline())
    }
}
