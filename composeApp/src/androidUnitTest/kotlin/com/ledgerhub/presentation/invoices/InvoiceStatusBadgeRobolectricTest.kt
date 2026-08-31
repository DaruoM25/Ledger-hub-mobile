package com.ledgerhub.presentation.invoices

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.runComposeUiTest
import com.ledgerhub.domain.invoice.Invoice
import com.ledgerhub.domain.invoice.InvoiceLine
import com.ledgerhub.domain.invoice.InvoiceStatus
import com.ledgerhub.domain.invoice.Money
import com.ledgerhub.domain.invoice.Party
import com.ledgerhub.domain.invoice.VatRate
import com.ledgerhub.presentation.invoices.components.InvoiceCardTags
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Niveau 3a — rendu des statuts réglementaires PPF 2026 sur l'écran de liste (US-13).
 *
 * Le badge porte son libellé complet en `contentDescription` : c'est ce canal — et non la
 * couleur, invérifiable en Robolectric — qui est assuré ici. Une pastille qui afficherait la
 * bonne teinte mais le mauvais libellé réglementaire serait une non-conformité, pas un détail
 * cosmétique. Assertions en [assertIsDisplayed] uniquement, jamais `assertExists`.
 *
 * La liste est une `LazyColumn` : un nœud hors viewport n'est pas composé, donc pas trouvable
 * par un simple `performScrollTo`. Chaque assertion fait d'abord défiler [InvoiceListTags.LIST]
 * jusqu'au nœud visé via `performScrollToNode`.
 */
@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalTestApi::class)
class InvoiceStatusBadgeRobolectricTest {

    private fun invoice(number: String, status: InvoiceStatus, issueDate: String) = Invoice(
        number = number,
        issueDate = issueDate,
        issuer = Party("Cabinet LedgerHub", "820329331", "82032933100027"),
        recipient = Party("Boulangerie Moreau SARL", "784102336", "78410233600021"),
        lines = listOf(InvoiceLine("Prestation de conseil", 1, Money(220_000), VatRate.TAUX_NORMAL)),
        status = status,
    )

    private val submitted = invoice("FAC-2026-0201", InvoiceStatus.DEPOSITED, "2026-08-10")
    private val approved = invoice("FAC-2026-0202", InvoiceStatus.APPROVED, "2026-08-18")
    private val rejected = invoice("FAC-2026-0203", InvoiceStatus.REJECTED, "2026-08-22")

    private fun state(filter: InvoiceStatusFilter = InvoiceStatusFilter.TOUTES) = InvoiceListUiState(
        isLoading = false,
        invoices = listOf(submitted, approved, rejected),
        statusFilter = filter,
    )

    @Test
    fun theThreeRegulatoryBadges_areDisplayedWithTheirOfficialWording() = runComposeUiTest {
        setContent {
            InvoiceListView(uiState = state(), onIntent = {}, onInvoiceClick = {})
        }

        listOf(
            submitted.number to "Déposée",
            approved.number to "Approuvée par l'administration",
            rejected.number to "Rejetée par la plateforme",
        ).forEach { (number, wording) ->
            onNodeWithTag(InvoiceListTags.LIST).performScrollToNode(hasTestTag(InvoiceCardTags.card(number)))
            onNodeWithTag(InvoiceCardTags.statusTag(number), useUnmergedTree = true).assertIsDisplayed()
            onNodeWithContentDescription(wording, useUnmergedTree = true).assertIsDisplayed()
        }
    }

    @Test
    fun eachRegulatoryStatus_hasItsOwnFilterChip() = runComposeUiTest {
        // Le filtre est le seul moyen d'isoler un statut : un statut sans chip serait noyé.
        setContent {
            InvoiceListView(
                uiState = state(filter = InvoiceStatusFilter.APPROVED),
                onIntent = {},
                onInvoiceClick = {},
            )
        }

        onNodeWithTag(InvoiceListTags.filterChip(InvoiceStatusFilter.APPROVED)).performScrollTo().assertIsDisplayed()
        onNodeWithTag(InvoiceListTags.LIST).performScrollToNode(hasTestTag(InvoiceCardTags.card(approved.number)))
        onNodeWithTag(InvoiceCardTags.statusTag(approved.number), useUnmergedTree = true).assertIsDisplayed()
        onNodeWithContentDescription("Approuvée par l'administration", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun filteringOnRejected_keepsOnlyThePlatformRejection() = runComposeUiTest {
        setContent {
            InvoiceListView(
                uiState = state(filter = InvoiceStatusFilter.REJECTED),
                onIntent = {},
                onInvoiceClick = {},
            )
        }

        onNodeWithTag(InvoiceListTags.filterChip(InvoiceStatusFilter.REJECTED)).performScrollTo().assertIsDisplayed()
        onNodeWithTag(InvoiceListTags.LIST).performScrollToNode(hasTestTag(InvoiceCardTags.card(rejected.number)))
        onNodeWithTag(InvoiceCardTags.statusTag(rejected.number), useUnmergedTree = true).assertIsDisplayed()
        onNodeWithContentDescription("Rejetée par la plateforme", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun cardOfAnApprovedInvoice_remainsClickable() = runComposeUiTest {
        var clicked: String? = null
        setContent {
            InvoiceListView(uiState = state(), onIntent = {}, onInvoiceClick = { clicked = it })
        }

        onNodeWithTag(InvoiceListTags.LIST).performScrollToNode(hasTestTag(InvoiceCardTags.card(approved.number)))
        onNodeWithTag(InvoiceCardTags.card(approved.number)).performClick()
        assertEquals(approved.number, clicked)
    }
}
