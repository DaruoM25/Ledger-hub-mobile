package com.ledgerhub.presentation.invoiceform

import com.ledgerhub.domain.compliance.ComplianceCheck
import com.ledgerhub.domain.compliance.ComplianceStatus
import com.ledgerhub.domain.invoice.Party
import com.ledgerhub.domain.invoice.VatRate
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Niveau 2 (US-24) — l'audit de conformité vu depuis le ViewModel.
 *
 * Le moteur est éprouvé ailleurs (`ComplianceAuditorTest`). Ce qui se joue ici est la **machine à
 * états** : un rapport n'existe que sur demande, il décrit la facture telle qu'elle était au
 * moment du scan, et il disparaît dès qu'elle change. Cette dernière propriété est la plus
 * importante des trois — un rapport périmé présenté comme actuel ferait émettre une facture sur la
 * foi d'un contrôle qui ne porte plus sur elle.
 */
class InvoiceFormComplianceTest {

    private val issuer = Party(
        name = "Cabinet LedgerHub",
        siren = "552100554",
        siret = "55210055400013",
        email = "facturation@ledgerhub.app",
    )

    private val issuerVat =
        "FR" + com.ledgerhub.domain.directory.FrenchVatNumber.computeKey(issuer.siren) + issuer.siren

    private fun viewModel() = InvoiceFormViewModel(issuer = issuer, issuerVatNumber = issuerVat)

    /** Facture complète et conforme, saisie par intentions comme le ferait l'écran. */
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

    // ── Le rapport n'existe que sur demande ─────────────────────────────────

    @Test
    fun atOpening_noReportHasBeenProduced() = runTest {
        assertNull(viewModel().uiState.value.complianceReport)
    }

    /** Saisir ne déclenche aucun audit : le contrôle est un geste, pas un commentaire permanent. */
    @Test
    fun typing_doesNotProduceAReportOnItsOwn() = runTest {
        val vm = viewModel()

        vm.fillCompliantInvoice()

        assertNull(vm.uiState.value.complianceReport)
    }

    @Test
    fun scanning_producesAReportCoveringTheFourChecks() = runTest {
        val vm = viewModel()
        vm.fillCompliantInvoice()

        vm.processIntent(InvoiceFormIntent.ComplianceScanRequested)

        val report = assertNotNull(vm.uiState.value.complianceReport)
        assertEquals(ComplianceCheck.ordered(), report.findings.map { it.check })
    }

    @Test
    fun scanningACompliantInvoice_reportsItCompliant() = runTest {
        val vm = viewModel()
        vm.fillCompliantInvoice()

        vm.processIntent(InvoiceFormIntent.ComplianceScanRequested)

        val report = assertNotNull(vm.uiState.value.complianceReport)
        assertTrue(report.isCompliant, "Constats : ${report.findings}")
    }

    /** Scanner un formulaire vierge est le premier geste possible : il doit dire quoi corriger. */
    @Test
    fun scanningABlankForm_reportsTheBlockingGaps() = runTest {
        val vm = viewModel()

        vm.processIntent(InvoiceFormIntent.ComplianceScanRequested)

        val report = assertNotNull(vm.uiState.value.complianceReport)
        assertEquals(ComplianceStatus.FAILED, report.overallStatus)
        assertEquals(
            listOf(ComplianceCheck.SIRET, ComplianceCheck.FACTURX_STRUCTURE),
            report.failures.map { it.check },
        )
    }

    // ── Invalidation : le cœur de l'US ──────────────────────────────────────

    /**
     * La propriété la plus importante de cette machine à états : **toute modification périme le
     * rapport**. L'invalidation vit dans `revalidate()`, le passage obligé de toute frappe — un
     * chemin parallèle finirait par en oublier un.
     */
    @Test
    fun anyEdit_invalidatesTheReport() = runTest {
        val vm = viewModel()
        vm.fillCompliantInvoice()
        vm.processIntent(InvoiceFormIntent.ComplianceScanRequested)
        assertNotNull(vm.uiState.value.complianceReport)

        vm.processIntent(InvoiceFormIntent.InvoiceNumberChanged("FAC-2026-0302"))

        assertNull(vm.uiState.value.complianceReport, "Le rapport doit périmer avec la facture")
    }

    @Test
    fun editingALine_invalidatesTheReport() = runTest {
        val vm = viewModel()
        vm.fillCompliantInvoice()
        vm.processIntent(InvoiceFormIntent.ComplianceScanRequested)

        vm.processIntent(
            InvoiceFormIntent.UpdateLine(0, "Conseil réglementaire", "3", "100.00", VatRate.TAUX_NORMAL),
        )

        assertNull(vm.uiState.value.complianceReport)
    }

    /** Les deux bascules changent la conformité elle-même : elles doivent périmer le rapport. */
    @Test
    fun togglingTheB2bMentionsOrFacturX_invalidatesTheReport() = runTest {
        val vm = viewModel()
        vm.fillCompliantInvoice()

        vm.processIntent(InvoiceFormIntent.ComplianceScanRequested)
        vm.processIntent(InvoiceFormIntent.ToggleB2bPenalties(false))
        assertNull(vm.uiState.value.complianceReport)

        vm.processIntent(InvoiceFormIntent.ComplianceScanRequested)
        vm.processIntent(InvoiceFormIntent.ToggleFacturX(false))
        assertNull(vm.uiState.value.complianceReport)
    }

    /** Après correction, un nouveau scan reflète la facture corrigée — pas la précédente. */
    @Test
    fun rescanningAfterAFix_reportsTheCorrectedInvoice() = runTest {
        val vm = viewModel()
        vm.fillCompliantInvoice()
        vm.processIntent(InvoiceFormIntent.ToggleB2bPenalties(false))
        vm.processIntent(InvoiceFormIntent.ComplianceScanRequested)
        assertEquals(
            ComplianceStatus.WARNING,
            assertNotNull(vm.uiState.value.complianceReport)
                .findingFor(ComplianceCheck.LEGAL_MENTIONS)?.status,
        )

        vm.processIntent(InvoiceFormIntent.ToggleB2bPenalties(true))
        vm.processIntent(InvoiceFormIntent.ComplianceScanRequested)

        assertEquals(
            ComplianceStatus.PASSED,
            assertNotNull(vm.uiState.value.complianceReport)
                .findingFor(ComplianceCheck.LEGAL_MENTIONS)?.status,
        )
    }

    // ── Innocuité ───────────────────────────────────────────────────────────

    /** Un audit est une lecture : deux scans consécutifs donnent le même rapport. */
    @Test
    fun scanning_isIdempotent() = runTest {
        val vm = viewModel()
        vm.fillCompliantInvoice()

        vm.processIntent(InvoiceFormIntent.ComplianceScanRequested)
        val first = vm.uiState.value.complianceReport
        vm.processIntent(InvoiceFormIntent.ComplianceScanRequested)

        assertEquals(first, vm.uiState.value.complianceReport)
    }

    /**
     * Scanner ne touche **ni la saisie, ni les erreurs, ni les totaux**. Un contrôle qui
     * modifierait ce qu'il contrôle ne serait pas un contrôle.
     */
    @Test
    fun scanning_leavesTheInvoiceUntouched() = runTest {
        val vm = viewModel()
        vm.fillCompliantInvoice()
        val before = vm.uiState.value

        vm.processIntent(InvoiceFormIntent.ComplianceScanRequested)

        val after = vm.uiState.value
        assertEquals(before.copy(complianceReport = after.complianceReport), after)
    }
}
