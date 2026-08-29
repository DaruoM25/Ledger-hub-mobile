package com.ledgerhub.presentation.creditnoteform

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import com.ledgerhub.domain.invoice.Invoice
import com.ledgerhub.domain.invoice.InvoiceLine
import com.ledgerhub.domain.invoice.InvoiceStatus
import com.ledgerhub.domain.invoice.Money
import com.ledgerhub.domain.invoice.Party
import com.ledgerhub.domain.invoice.VatRate
import com.ledgerhub.presentation.invoices.InvoiceDetailScreenTags
import com.ledgerhub.presentation.invoices.InvoiceDetailUiState
import com.ledgerhub.presentation.invoices.InvoiceDetailView
import com.ledgerhub.presentation.invoices.components.InvoiceCard
import com.ledgerhub.presentation.invoices.components.InvoiceCardTags
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test

/**
 * Références croisées US-05 côté interface : le numéro d'avoir attribué et présenté en lecture
 * seule sur le formulaire, la mention de liaison sur la facture parente (détail et liste), et le
 * refus d'un second avoir.
 */
@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalTestApi::class)
class CreditNoteCrossReferenceUiTest {

    private val invoice = Invoice(
        number = "FAC-2026-0100",
        issueDate = "2026-06-24",
        issuer = Party("Cabinet LedgerHub", "820329331", "82032933100027"),
        recipient = Party("Boulangerie Moreau SARL", "784102336", "78410233600021"),
        lines = listOf(
            InvoiceLine("Conseil", quantity = 2, unitPriceHt = Money(50_000), vatRate = VatRate.TAUX_NORMAL),
        ),
        status = InvoiceStatus.CANCELLED,
    )

    private fun formState(
        blocked: String? = null,
        number: String = "AV-2026-0001",
    ) = CreditNoteFormUiState(
        invoiceId = invoice.number,
        originalInvoiceDate = invoice.issueDate,
        issuerName = invoice.issuer.name,
        recipientName = invoice.recipient.name,
        lines = invoice.lines,
        vatBreakdown = invoice.vatBreakdown.map {
            com.ledgerhub.domain.invoice.VatBreakdown(
                rate = it.rate,
                baseHt = Money(-it.baseHt.cents),
                vatAmount = Money(-it.vatAmount.cents),
            )
        },
        totalHt = Money(-invoice.totalHt.cents),
        totalVat = Money(-invoice.totalVat.cents),
        totalTtc = Money(-invoice.totalTtc.cents),
        creditNoteNumber = number,
        blockedByExistingCreditNote = blocked,
    )

    // ── Formulaire d'avoir ───────────────────────────────────────────────────────────────────

    @Test
    fun form_presentsTheAttributedNumber_andTheFullCrossReference() = runComposeUiTest {
        setContent { CreditNoteFormContent(uiState = formState()) }

        onNodeWithTag(CreditNoteFormTags.CREDIT_NOTE_NUMBER).performScrollTo().assertIsDisplayed()
        onNodeWithText("Numéro d'avoir : AV-2026-0001").assertIsDisplayed()
        // Référence croisée : numéro ET date de la facture annulée.
        onNodeWithText("Annule la facture FAC-2026-0100 du 2026-06-24").assertIsDisplayed()
    }

    @Test
    fun form_listsTheCopiedLinesAndTheCreditedVatBases() = runComposeUiTest {
        setContent { CreditNoteFormContent(uiState = formState()) }

        onNodeWithTag(CreditNoteFormTags.LINES, useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        onNodeWithText("2 × Conseil — 500,00 € HT (20 %)").assertIsDisplayed()
        onNodeWithTag(CreditNoteFormTags.VAT_BREAKDOWN, useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        onNodeWithText("Base 20 % : -1 000,00 € — TVA -200,00 €").assertIsDisplayed()
    }

    @Test
    fun form_refusesEmission_whenTheInvoiceIsAlreadyCredited() = runComposeUiTest {
        setContent { CreditNoteFormContent(uiState = formState(blocked = "AV-2026-0007")) }

        onNodeWithTag(CreditNoteFormTags.BLOCKED_BANNER, useUnmergedTree = true)
            .performScrollTo()
            .assertIsDisplayed()
        onNodeWithText("Cette facture a déjà été annulée par l'avoir AV-2026-0007").assertIsDisplayed()
        onNodeWithTag(CreditNoteFormTags.SUBMIT_BUTTON).performScrollTo().assertIsNotEnabled()
    }

    // ── Mention de liaison sur la facture parente ────────────────────────────────────────────

    @Test
    fun detailScreen_showsTheLinkToTheIssuedCreditNote() = runComposeUiTest {
        setContent {
            InvoiceDetailView(
                uiState = InvoiceDetailUiState(
                    isLoading = false,
                    invoice = invoice,
                    creditNoteNumber = "AV-2026-0001",
                ),
            )
        }

        onNodeWithTag(InvoiceDetailScreenTags.CREDIT_NOTE_MENTION, useUnmergedTree = true)
            .performScrollTo()
            .assertIsDisplayed()
        onNodeWithText("Avoir émis : AV-2026-0001").assertIsDisplayed()
    }

    @Test
    fun detailScreen_showsNoMention_whenNoCreditNoteExists() = runComposeUiTest {
        setContent {
            InvoiceDetailView(uiState = InvoiceDetailUiState(isLoading = false, invoice = invoice))
        }

        onNodeWithTag(InvoiceDetailScreenTags.CREDIT_NOTE_MENTION, useUnmergedTree = true)
            .assertDoesNotExist()
    }

    @Test
    fun listCard_showsTheLinkToTheIssuedCreditNote() = runComposeUiTest {
        setContent { InvoiceCard(invoice = invoice, onClick = {}, creditNoteNumber = "AV-2026-0001") }

        onNodeWithTag(InvoiceCardTags.creditNoteTag(invoice.number), useUnmergedTree = true)
            .assertIsDisplayed()
        onNodeWithText("Avoir émis : AV-2026-0001").assertIsDisplayed()
    }

    @Test
    fun listCard_showsNoMention_whenNoCreditNoteExists() = runComposeUiTest {
        setContent { InvoiceCard(invoice = invoice, onClick = {}) }

        onNodeWithTag(InvoiceCardTags.creditNoteTag(invoice.number), useUnmergedTree = true)
            .assertDoesNotExist()
    }
}
