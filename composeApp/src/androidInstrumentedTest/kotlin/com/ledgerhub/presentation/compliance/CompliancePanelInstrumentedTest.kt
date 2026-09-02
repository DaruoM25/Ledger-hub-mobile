package com.ledgerhub.presentation.compliance

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.asAndroidBitmap
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
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ledgerhub.domain.compliance.ComplianceCheck
import com.ledgerhub.domain.directory.FrenchVatNumber
import com.ledgerhub.domain.i18n.AppLanguage
import com.ledgerhub.domain.i18n.AppTranslations
import com.ledgerhub.domain.i18n.StringKey
import com.ledgerhub.domain.invoice.Party
import com.ledgerhub.domain.invoice.VatRate
import com.ledgerhub.presentation.invoiceform.InvoiceFormIntent
import com.ledgerhub.presentation.invoiceform.InvoiceFormScreen
import com.ledgerhub.presentation.invoiceform.InvoiceFormViewModel
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.assertTrue

/**
 * Niveau 3b (US-24) — le panneau d'audit au doigt sur émulateur Pixel 5 API 35
 * (`connectedDebugAndroidTest`).
 *
 * Ce que Robolectric ne peut pas prouver : que le bouton de scan est **joignable** au bas d'un
 * formulaire long, qu'il offre une cible tactile décente, et que l'appui du doigt — et non un
 * `performClick` synthétique — déclenche bien l'audit. S'y ajoute l'export de la capture QA.
 *
 * `performScrollTo()` avant toute interaction : le panneau vit après les lignes, le récapitulatif
 * et les mentions légales. Exiger qu'il soit visible d'emblée serait une promesse que la taille
 * de police système suffit à briser — leçon des trois passes QA de l'US-22.
 */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class CompliancePanelInstrumentedTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val screenshotName = "US24_mobile_compliance_panel_sdk_gphone64_x86_64.png"

    private val issuer = Party(
        name = "Cabinet LedgerHub",
        siren = "552100554",
        siret = "55210055400013",
        email = "facturation@ledgerhub.app",
    )

    private val issuerVat = "FR" + FrenchVatNumber.computeKey(issuer.siren) + issuer.siren

    private fun tr(key: StringKey) = AppTranslations.get(key, AppLanguage.FR)

    /**
     * Facture de démonstration **volontairement imparfaite** : SIRET client à la clé de Luhn
     * fausse et mentions B2B décochées.
     *
     * C'est ce que la capture officielle doit montrer — une checklist qui *dit quelque chose*.
     * Une facture parfaite n'afficherait que quatre coches vertes et ne prouverait ni les
     * gravités, ni le bandeau, c'est-à-dire l'essentiel de l'US.
     */
    private fun renderAuditedInvoice(): InvoiceFormViewModel {
        val viewModel = InvoiceFormViewModel(issuer = issuer, issuerVatNumber = issuerVat)

        composeRule.setContent { InvoiceFormScreen(viewModel = viewModel) }
        composeRule.waitForIdle()

        viewModel.processIntent(InvoiceFormIntent.InvoiceNumberChanged("FAC-2026-0301"))
        viewModel.processIntent(InvoiceFormIntent.IssueDateChanged("2026-09-02"))
        viewModel.processIntent(InvoiceFormIntent.DueDateChanged("2026-10-02"))
        viewModel.processIntent(InvoiceFormIntent.ClientSiretChanged("78410233600021"))
        viewModel.processIntent(InvoiceFormIntent.ClientEmailChanged("compta@moreau.fr"))
        viewModel.processIntent(
            InvoiceFormIntent.UpdateLine(0, "Conseil réglementaire PPF", "2", "100.00", VatRate.TAUX_NORMAL),
        )
        viewModel.processIntent(InvoiceFormIntent.ToggleB2bPenalties(false))
        composeRule.waitForIdle()
        return viewModel
    }

    private fun scan() {
        composeRule.onNodeWithTag(CompliancePanelTags.SCAN_BUTTON)
            .performScrollTo()
            .performTouchInput { click() }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(CompliancePanelTags.CHECKLIST)
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    // ── Joignabilité et cible tactile ───────────────────────────────────────

    @Test
    fun thePanelAndItsScanButton_areReachableOnDevice() {
        renderAuditedInvoice()

        composeRule.onNodeWithTag(CompliancePanelTags.PANEL).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(CompliancePanelTags.SCAN_BUTTON).performScrollTo().assertIsDisplayed()
    }

    /** Un bouton d'action réglementaire doit se viser au doigt, pas au stylet. */
    @Test
    fun theScanButton_meetsTheMinimumTouchTarget() {
        renderAuditedInvoice()

        composeRule.onNodeWithTag(CompliancePanelTags.SCAN_BUTTON)
            .performScrollTo()
            .assertHeightIsAtLeast(48.dp)
    }

    // ── Le geste réel ───────────────────────────────────────────────────────

    /** L'appui du doigt — et non un clic synthétique — produit la checklist. */
    @Test
    fun tappingScan_producesTheChecklistOnDevice() {
        renderAuditedInvoice()

        composeRule.onNodeWithTag(CompliancePanelTags.CHECKLIST).assertDoesNotExist()
        scan()

        composeRule.onNodeWithTag(CompliancePanelTags.CHECKLIST).performScrollTo().assertIsDisplayed()
        ComplianceCheck.ordered().forEach { check ->
            composeRule.onNodeWithTag(CompliancePanelTags.check(check))
                .performScrollTo()
                .assertIsDisplayed()
        }
    }

    /** La facture auditée porte deux avertissements : le bandeau doit être ambre, pas rouge. */
    @Test
    fun theAlert_statesTheWarningsOfTheAuditedInvoice() {
        renderAuditedInvoice()
        scan()

        composeRule.onNodeWithTag(CompliancePanelTags.ALERT)
            .performScrollTo()
            .assertTextContains(tr(StringKey.COMPLIANCE_ALERT_WARNING_TITLE), substring = true)
        composeRule.onNodeWithTag(CompliancePanelTags.check(ComplianceCheck.SIRET))
            .assertTextContains(tr(StringKey.COMPLIANCE_SIRET_LUHN), substring = true)
        composeRule.onNodeWithTag(CompliancePanelTags.check(ComplianceCheck.LEGAL_MENTIONS))
            .assertTextContains(tr(StringKey.COMPLIANCE_LEGAL_MISSING), substring = true)
    }

    /** Corriger la facture périme le rapport : la checklist quitte l'écran de l'appareil. */
    @Test
    fun editingAfterAScan_clearsTheChecklistOnDevice() {
        val viewModel = renderAuditedInvoice()
        scan()

        viewModel.processIntent(InvoiceFormIntent.ToggleB2bPenalties(true))
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(CompliancePanelTags.CHECKLIST)
                .fetchSemanticsNodes().isEmpty()
        }

        composeRule.onNodeWithTag(CompliancePanelTags.CHECKLIST).assertDoesNotExist()
    }

    // ── Preuve QA ───────────────────────────────────────────────────────────

    /**
     * Capture officielle de l'US-24, cadrée sur **`compliance_panel`** : ce que l'US doit
     * démontrer, c'est le panneau — sa checklist des quatre contrôles et son bandeau d'alerte.
     * Le reste du formulaire est du contexte, et l'inclure diluerait la preuve.
     *
     * Écrite **aux deux emplacements** : les fichiers de l'application (traçabilité) et
     * `/sdcard/Download`, d'où `scripts/run-qa.ps1 -ScreenshotPrefix "US24"` la rapatrie sans
     * `adb pull` manuel (même procédé que les US-20 à US-23).
     */
    @Test
    fun exportsTheCompliancePanelScreenshot() {
        renderAuditedInvoice()
        scan()

        composeRule.onNodeWithTag(CompliancePanelTags.PANEL).performScrollTo().assertIsDisplayed()
        composeRule.waitForIdle()

        val bitmap = composeRule.onNodeWithTag(CompliancePanelTags.PANEL)
            .captureToImage()
            .asAndroidBitmap()

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val appFile = File(context.getExternalFilesDir(null), screenshotName)
        appFile.outputStream().use { stream -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream) }

        val sharedFile = File("/sdcard/Download", screenshotName).apply { parentFile?.mkdirs() }
        runCatching {
            sharedFile.outputStream().use { stream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            }
        }

        assertTrue(appFile.exists(), "Capture US-24 non écrite : ${appFile.absolutePath}")
        assertTrue(appFile.length() > 0, "Capture US-24 vide : ${appFile.absolutePath}")
    }
}
