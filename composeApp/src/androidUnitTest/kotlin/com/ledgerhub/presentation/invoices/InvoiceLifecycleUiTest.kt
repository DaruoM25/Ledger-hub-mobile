package com.ledgerhub.presentation.invoices

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import com.ledgerhub.domain.audit.AuditEntry
import com.ledgerhub.domain.invoice.Invoice
import com.ledgerhub.domain.invoice.InvoiceLine
import com.ledgerhub.domain.invoice.InvoiceStatus
import com.ledgerhub.domain.invoice.Money
import com.ledgerhub.domain.invoice.Party
import com.ledgerhub.domain.invoice.VatRate
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Cycle de vie DGFIP et Piste d'Audit Fiable à l'écran (US-07).
 *
 * Les boutons sont dérivés de la machine d'états : ces tests vérifient donc autant ce qui est
 * proposé que ce qui ne l'est **pas** — une action offerte hors des transitions autorisées serait
 * une faille de conformité, pas un simple défaut d'affichage.
 */
@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalTestApi::class)
class InvoiceLifecycleUiTest {

    private fun invoice(status: InvoiceStatus) = Invoice(
        number = "FAC-2026-0137",
        issueDate = "2026-07-12",
        issuer = Party("Cabinet LedgerHub", "820329331", "82032933100027"),
        recipient = Party("Boulangerie Moreau SARL", "784102336", "78410233600021"),
        lines = listOf(InvoiceLine("Prestation", 1, Money(220_000), VatRate.TAUX_NORMAL)),
        status = status,
    )

    private fun state(
        status: InvoiceStatus,
        auditTrail: List<AuditEntry> = emptyList(),
        pendingTransition: InvoiceStatus? = null,
        transitionReason: String = "",
    ) = InvoiceDetailUiState(
        isLoading = false,
        invoice = invoice(status),
        auditTrail = auditTrail,
        pendingTransition = pendingTransition,
        transitionReason = transitionReason,
    )

    // ── Actions contextuelles ────────────────────────────────────────────────────────────────

    @Test
    fun aDraftInvoice_offersOnlyTheDepositAction() = runComposeUiTest {
        setContent { InvoiceDetailView(uiState = state(InvoiceStatus.DRAFT)) }

        onNodeWithTag(InvoiceDetailScreenTags.transitionButton(InvoiceStatus.DEPOSITED))
            .performScrollTo()
            .assertIsEnabled()
        onNodeWithTag(InvoiceDetailScreenTags.transitionButton(InvoiceStatus.PAID)).assertDoesNotExist()
        onNodeWithTag(InvoiceDetailScreenTags.transitionButton(InvoiceStatus.REJECTED)).assertDoesNotExist()
        onNodeWithTag(InvoiceDetailScreenTags.transitionButton(InvoiceStatus.REFUSED)).assertDoesNotExist()
    }

    @Test
    fun aDepositedInvoice_offersPaymentRejectionAndRefusal() = runComposeUiTest {
        setContent { InvoiceDetailView(uiState = state(InvoiceStatus.DEPOSITED)) }

        listOf(InvoiceStatus.PAID, InvoiceStatus.REJECTED, InvoiceStatus.REFUSED).forEach { target ->
            onNodeWithTag(InvoiceDetailScreenTags.transitionButton(target))
                .performScrollTo()
                .assertIsEnabled()
        }
        // Le retour au brouillon n'est pas ouvert depuis DEPOSITED.
        onNodeWithTag(InvoiceDetailScreenTags.transitionButton(InvoiceStatus.DRAFT)).assertDoesNotExist()
    }

    @Test
    fun aRejectedInvoice_offersTheCorrectionBackToDraft() = runComposeUiTest {
        setContent { InvoiceDetailView(uiState = state(InvoiceStatus.REJECTED)) }

        onNodeWithTag(InvoiceDetailScreenTags.transitionButton(InvoiceStatus.DRAFT))
            .performScrollTo()
            .assertIsEnabled()
    }

    @Test
    fun aRefusedInvoice_offersNoUserAction() = runComposeUiTest {
        // Seul l'avoir corrige un refus acheteur : aucune transition directe n'est proposée.
        setContent { InvoiceDetailView(uiState = state(InvoiceStatus.REFUSED)) }

        onNodeWithTag(InvoiceDetailScreenTags.LIFECYCLE_SECTION).assertDoesNotExist()
    }

    @Test
    fun cancellationIsNeverOfferedAsADirectAction() = runComposeUiTest {
        // Arbitrage PO : CANCELLED n'appartient qu'au parcours d'avoir.
        listOf(InvoiceStatus.DEPOSITED, InvoiceStatus.PAID, InvoiceStatus.REFUSED).forEach { status ->
            runComposeUiTest {
                setContent { InvoiceDetailView(uiState = state(status)) }
                onNodeWithTag(InvoiceDetailScreenTags.transitionButton(InvoiceStatus.CANCELLED))
                    .assertDoesNotExist()
            }
        }
    }

    @Test
    fun aCancelledInvoice_offersNoLifecycleSectionAtAll() = runComposeUiTest {
        setContent { InvoiceDetailView(uiState = state(InvoiceStatus.CANCELLED)) }

        onNodeWithTag(InvoiceDetailScreenTags.LIFECYCLE_SECTION).assertDoesNotExist()
    }

    // ── Saisie du motif ──────────────────────────────────────────────────────────────────────

    @Test
    fun clickingATransition_opensTheReasonDialog() = runComposeUiTest {
        var requested: InvoiceStatus? = null
        setContent {
            InvoiceDetailView(
                uiState = state(InvoiceStatus.DEPOSITED),
                onStartTransition = { requested = it },
            )
        }

        onNodeWithTag(InvoiceDetailScreenTags.transitionButton(InvoiceStatus.REJECTED))
            .performScrollTo()
            .performClick()

        assertEquals(InvoiceStatus.REJECTED, requested)
    }

    @Test
    fun aNegativeTransitionWithoutReason_cannotBeConfirmed() = runComposeUiTest {
        setContent {
            InvoiceDetailView(
                uiState = state(InvoiceStatus.DEPOSITED, pendingTransition = InvoiceStatus.REJECTED),
            )
        }

        onNodeWithTag(InvoiceDetailScreenTags.TRANSITION_DIALOG).assertIsDisplayed()
        onNodeWithTag(InvoiceDetailScreenTags.TRANSITION_CONFIRM).assertIsNotEnabled()
    }

    @Test
    fun aNegativeTransitionWithAReason_canBeConfirmed() = runComposeUiTest {
        var confirmed = false
        setContent {
            InvoiceDetailView(
                uiState = state(
                    InvoiceStatus.DEPOSITED,
                    pendingTransition = InvoiceStatus.REJECTED,
                    transitionReason = "SIRET invalide",
                ),
                onConfirmTransition = { confirmed = true },
            )
        }

        onNodeWithTag(InvoiceDetailScreenTags.TRANSITION_CONFIRM).assertIsEnabled().performClick()
        assertEquals(true, confirmed)
    }

    @Test
    fun aPositiveTransition_canBeConfirmedWithoutAReason() = runComposeUiTest {
        setContent {
            InvoiceDetailView(
                uiState = state(InvoiceStatus.DEPOSITED, pendingTransition = InvoiceStatus.PAID),
            )
        }

        onNodeWithTag(InvoiceDetailScreenTags.TRANSITION_CONFIRM).assertIsEnabled()
    }

    @Test
    fun typingAReason_emitsIt() = runComposeUiTest {
        var typed = ""
        setContent {
            InvoiceDetailView(
                uiState = state(InvoiceStatus.DEPOSITED, pendingTransition = InvoiceStatus.REFUSED),
                onTransitionReasonChanged = { typed = it },
            )
        }

        onNodeWithTag(InvoiceDetailScreenTags.TRANSITION_REASON).performTextInput("Prestation non conforme")

        assertEquals("Prestation non conforme", typed)
    }

    // ── Piste d'Audit Fiable ─────────────────────────────────────────────────────────────────

    @Test
    fun anInvoiceWithoutHistory_showsTheEmptyState() = runComposeUiTest {
        setContent { InvoiceDetailView(uiState = state(InvoiceStatus.DRAFT)) }

        onNodeWithTag(InvoiceDetailScreenTags.AUDIT_TRAIL_EMPTY, useUnmergedTree = true)
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun theAuditTrail_rendersEachTransitionWithItsDateAndReason() = runComposeUiTest {
        val trail = listOf(
            AuditEntry("a1", "FAC-2026-0137", InvoiceStatus.DRAFT, InvoiceStatus.DEPOSITED, null, "2026-08-29T10:00:00Z"),
            AuditEntry(
                "a2", "FAC-2026-0137", InvoiceStatus.DEPOSITED, InvoiceStatus.REJECTED,
                "SIRET destinataire invalide", "2026-08-29T10:01:00Z",
            ),
        )
        setContent { InvoiceDetailView(uiState = state(InvoiceStatus.REJECTED, auditTrail = trail)) }

        onNodeWithTag(InvoiceDetailScreenTags.AUDIT_TRAIL, useUnmergedTree = true)
            .performScrollTo()
            .assertIsDisplayed()
        onNodeWithTag(InvoiceDetailScreenTags.auditEntry("a1"), useUnmergedTree = true).assertIsDisplayed()
        onNodeWithTag(InvoiceDetailScreenTags.auditEntry("a2"), useUnmergedTree = true).assertIsDisplayed()
        onNodeWithText("Brouillon  →  Déposée").assertIsDisplayed()
        onNodeWithText("Déposée  →  Rejetée par la plateforme").assertIsDisplayed()
        onNodeWithText("2026-08-29T10:01:00Z").assertIsDisplayed()
        onNodeWithText("SIRET destinataire invalide").assertIsDisplayed()
    }

    @Test
    fun aFirstEntryWithoutPreviousStatus_rendersADash() = runComposeUiTest {
        val trail = listOf(
            AuditEntry("a0", "FAC-2026-0137", null, InvoiceStatus.DRAFT, null, "2026-08-29T09:00:00Z"),
        )
        setContent { InvoiceDetailView(uiState = state(InvoiceStatus.DRAFT, auditTrail = trail)) }

        onNodeWithTag(InvoiceDetailScreenTags.auditEntry("a0"), useUnmergedTree = true).performScrollTo()
        onNodeWithText("—  →  Brouillon").assertIsDisplayed()
    }

    @Test
    fun aTransitionError_isSurfacedToTheUser() = runComposeUiTest {
        setContent {
            InvoiceDetailView(
                uiState = state(InvoiceStatus.DEPOSITED).copy(
                    transitionError = "Transition interdite : une facture PAID ne peut pas passer à DRAFT",
                ),
            )
        }

        onNodeWithTag(InvoiceDetailScreenTags.TRANSITION_ERROR, useUnmergedTree = true)
            .performScrollTo()
            .assertIsDisplayed()
    }
}
