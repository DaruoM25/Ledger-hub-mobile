package com.ledgerhub.presentation.compliance

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import com.ledgerhub.domain.compliance.ComplianceCheck
import com.ledgerhub.domain.directory.FrenchVatNumber
import com.ledgerhub.domain.i18n.AppLanguage
import com.ledgerhub.domain.i18n.AppTranslations
import com.ledgerhub.domain.i18n.StringKey
import com.ledgerhub.domain.invoice.Party
import com.ledgerhub.domain.invoice.VatRate
import com.ledgerhub.presentation.i18n.LocalAppLanguage
import com.ledgerhub.presentation.invoiceform.InvoiceFormIntent
import com.ledgerhub.presentation.invoiceform.InvoiceFormScreen
import com.ledgerhub.presentation.invoiceform.InvoiceFormViewModel
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test

/**
 * Niveau 3a (US-24) — le panneau d'audit rendu dans le formulaire de facture.
 *
 * La logique d'audit est verrouillée au niveau 1 et la machine à états au niveau 2 : ce niveau
 * vérifie que **l'écran les montre** — la checklist sous ses quatre tags imposés, le bandeau dans
 * la bonne gravité, et son absence lorsque la facture est conforme.
 *
 * ## Défilement
 *
 * Le panneau vit tout en bas d'un formulaire long. Toute assertion passe donc par
 * `performScrollTo()` — le geste de l'utilisateur, et la leçon des trois passes QA de l'US-22 :
 * une assertion qui exige qu'un écran entier tienne d'un coup échoue tôt ou tard sur une
 * géométrie, sans rien dire du comportement.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
@OptIn(ExperimentalTestApi::class)
class CompliancePanelRobolectricTest {

    private val issuer = Party(
        name = "Cabinet LedgerHub",
        siren = "552100554",
        siret = "55210055400013",
        email = "facturation@ledgerhub.app",
    )

    private val issuerVat = "FR" + FrenchVatNumber.computeKey(issuer.siren) + issuer.siren

    private fun tr(key: StringKey, language: AppLanguage = AppLanguage.FR) =
        AppTranslations.get(key, language)

    private fun newViewModel(issuerVatNumber: String = issuerVat) =
        InvoiceFormViewModel(issuer = issuer, issuerVatNumber = issuerVatNumber)

    /** Facture complète et conforme — saisie par intentions, comme le ferait l'utilisateur. */
    private fun InvoiceFormViewModel.fillCompliantInvoice() {
        processIntent(InvoiceFormIntent.InvoiceNumberChanged("FAC-2026-0301"))
        processIntent(InvoiceFormIntent.IssueDateChanged("2026-09-02"))
        processIntent(InvoiceFormIntent.DueDateChanged("2026-10-02"))
        processIntent(InvoiceFormIntent.ClientSiretChanged("55210055400013"))
        processIntent(InvoiceFormIntent.ClientEmailChanged("compta@moreau.fr"))
        processIntent(
            InvoiceFormIntent.UpdateLine(0, "Conseil réglementaire", "2", "100.00", VatRate.TAUX_NORMAL),
        )
    }

    // ── Structure du panneau ────────────────────────────────────────────────

    @Test
    fun thePanel_isPartOfTheInvoiceForm() = runComposeUiTest {
        setContent { InvoiceFormScreen(viewModel = newViewModel()) }

        onNodeWithTag("compliance_panel").performScrollTo().assertIsDisplayed()
        onNodeWithText(tr(StringKey.COMPLIANCE_PANEL_TITLE)).assertIsDisplayed()
        onNodeWithTag("compliance_scan_btn").performScrollTo().assertIsDisplayed()
    }

    /** Avant tout scan, il n'y a ni checklist ni bandeau : l'absence de rapport est un état. */
    @Test
    fun beforeAnyScan_thereIsNoChecklistAndNoAlert() = runComposeUiTest {
        setContent { InvoiceFormScreen(viewModel = newViewModel()) }

        onNodeWithTag("compliance_panel").performScrollTo().assertIsDisplayed()
        onNodeWithTag("compliance_checklist").assertDoesNotExist()
        onNodeWithTag("compliance_alert").assertDoesNotExist()
        onNodeWithText(tr(StringKey.COMPLIANCE_NOT_SCANNED)).assertIsDisplayed()
    }

    /** Les huit tags imposés, présents dès qu'un rapport existe. */
    @Test
    fun scanning_revealsTheEightSpecifiedNodes() = runComposeUiTest {
        setContent { InvoiceFormScreen(viewModel = newViewModel()) }

        onNodeWithTag("compliance_scan_btn").performScrollTo().performClick()
        waitForIdle()

        onNodeWithTag("compliance_panel").performScrollTo().assertIsDisplayed()
        onNodeWithTag("compliance_scan_btn").performScrollTo().assertIsDisplayed()
        onNodeWithTag("compliance_checklist").performScrollTo().assertIsDisplayed()
        onNodeWithTag("compliance_check_siret").performScrollTo().assertIsDisplayed()
        onNodeWithTag("compliance_check_vat").performScrollTo().assertIsDisplayed()
        onNodeWithTag("compliance_check_legal").performScrollTo().assertIsDisplayed()
        onNodeWithTag("compliance_check_facturx").performScrollTo().assertIsDisplayed()
        // Le formulaire est vierge : le bandeau existe donc, en gravité bloquante.
        onNodeWithTag("compliance_alert").performScrollTo().assertIsDisplayed()
    }

    /** Chaque ligne annonce son contrôle et son motif — pas seulement une pastille de couleur. */
    @Test
    fun everyChecklistRow_statesItsCheckAndItsReason() = runComposeUiTest {
        setContent { InvoiceFormScreen(viewModel = newViewModel()) }

        onNodeWithTag("compliance_scan_btn").performScrollTo().performClick()
        waitForIdle()

        ComplianceCheck.ordered().forEach { check ->
            onNodeWithTag(CompliancePanelTags.check(check))
                .performScrollTo()
                .assertTextContains(tr(check.titleKey), substring = true)
        }
        // C'est le SIRET **client** qui manque : celui de l'émetteur vient des paramètres
        // fiscaux et est déjà valide. Le contrôle rapporte donc le premier écart réel, et non
        // le premier écart imaginable.
        onNodeWithTag("compliance_check_siret")
            .assertTextContains(tr(StringKey.COMPLIANCE_SIRET_CLIENT_INVALID), substring = true)
    }

    // ── Gravités ────────────────────────────────────────────────────────────

    /** Formulaire vierge : SIRET et structure manquants, donc bandeau **rouge**. */
    @Test
    fun aBlankInvoice_raisesTheBlockingAlert() = runComposeUiTest {
        setContent { InvoiceFormScreen(viewModel = newViewModel()) }

        onNodeWithTag("compliance_scan_btn").performScrollTo().performClick()
        waitForIdle()

        onNodeWithTag("compliance_alert")
            .performScrollTo()
            .assertTextContains(tr(StringKey.COMPLIANCE_ALERT_ERROR_TITLE), substring = true)
    }

    /** Facture complète mais mentions B2B décochées : écart admissible, donc bandeau **ambre**. */
    @Test
    fun aWarningOnlyInvoice_raisesTheAmberAlert() = runComposeUiTest {
        val viewModel = newViewModel()
        setContent { InvoiceFormScreen(viewModel = viewModel) }

        viewModel.fillCompliantInvoice()
        viewModel.processIntent(InvoiceFormIntent.ToggleB2bPenalties(false))
        onNodeWithTag("compliance_scan_btn").performScrollTo().performClick()
        waitForIdle()

        onNodeWithTag("compliance_alert")
            .performScrollTo()
            .assertTextContains(tr(StringKey.COMPLIANCE_ALERT_WARNING_TITLE), substring = true)
        onNodeWithTag("compliance_check_legal")
            .assertTextContains(tr(StringKey.COMPLIANCE_LEGAL_MISSING), substring = true)
    }

    /** Facture conforme : la conformité s'annonce en clair, **sans** bandeau. */
    @Test
    fun aCompliantInvoice_showsNoAlertAtAll() = runComposeUiTest {
        val viewModel = newViewModel()
        setContent { InvoiceFormScreen(viewModel = viewModel) }

        viewModel.fillCompliantInvoice()
        onNodeWithTag("compliance_scan_btn").performScrollTo().performClick()
        waitForIdle()

        onNodeWithTag("compliance_checklist").performScrollTo().assertIsDisplayed()
        onNodeWithTag("compliance_alert").assertDoesNotExist()
        onNodeWithText(tr(StringKey.COMPLIANCE_ALL_PASSED)).performScrollTo().assertIsDisplayed()
    }

    /** Un numéro de TVA absent est un avertissement, pas une faute — il doit se lire comme tel. */
    @Test
    fun anAbsentVatNumber_isReportedAsAWarning() = runComposeUiTest {
        val viewModel = newViewModel(issuerVatNumber = "")
        setContent { InvoiceFormScreen(viewModel = viewModel) }

        viewModel.fillCompliantInvoice()
        onNodeWithTag("compliance_scan_btn").performScrollTo().performClick()
        waitForIdle()

        onNodeWithTag("compliance_check_vat")
            .performScrollTo()
            .assertTextContains(tr(StringKey.COMPLIANCE_VAT_ABSENT), substring = true)
        onNodeWithTag("compliance_alert")
            .performScrollTo()
            .assertTextContains(tr(StringKey.COMPLIANCE_ALERT_WARNING_TITLE), substring = true)
    }

    // ── Invalidation vue depuis l'écran ─────────────────────────────────────

    /**
     * La propriété centrale de l'US, vue de l'écran : après une frappe, la checklist disparaît.
     * Un rapport périmé qui resterait affiché ferait émettre sur la foi d'un contrôle périmé.
     */
    @Test
    fun editingAfterAScan_removesTheChecklistFromTheScreen() = runComposeUiTest {
        val viewModel = newViewModel()
        setContent { InvoiceFormScreen(viewModel = viewModel) }

        viewModel.fillCompliantInvoice()
        onNodeWithTag("compliance_scan_btn").performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag("compliance_checklist").performScrollTo().assertIsDisplayed()

        viewModel.processIntent(InvoiceFormIntent.InvoiceNumberChanged("FAC-2026-0302"))
        waitForIdle()

        onNodeWithTag("compliance_checklist").assertDoesNotExist()
        onNodeWithText(tr(StringKey.COMPLIANCE_NOT_SCANNED)).performScrollTo().assertIsDisplayed()
    }

    // ── Internationalisation ────────────────────────────────────────────────

    @Test
    fun inEnglish_thePanelIsFullyTranslated() = runComposeUiTest {
        val viewModel = newViewModel()
        setContent {
            CompositionLocalProvider(LocalAppLanguage provides AppLanguage.EN) {
                InvoiceFormScreen(viewModel = viewModel)
            }
        }

        onNodeWithText(tr(StringKey.COMPLIANCE_PANEL_TITLE, AppLanguage.EN)).performScrollTo().assertIsDisplayed()
        onNodeWithText(tr(StringKey.COMPLIANCE_SCAN_ACTION, AppLanguage.EN))
            .performScrollTo()
            .assertIsDisplayed()

        onNodeWithTag("compliance_scan_btn").performScrollTo().performClick()
        waitForIdle()

        ComplianceCheck.ordered().forEach { check ->
            onNodeWithTag(CompliancePanelTags.check(check))
                .performScrollTo()
                .assertTextContains(tr(check.titleKey, AppLanguage.EN), substring = true)
        }
    }
}
