package com.ledgerhub.presentation.invoiceform

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import com.ledgerhub.domain.invoice.TransactionMode
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Suite de tests Robolectric réglementaires Réforme 2026 (US-27).
 * Couverture de qualification :
 * - [N2] B2B e-Invoicing : astérisque obligatoire sur SIREN/SIRET et blocage à la soumission si vide.
 * - [N3A] e-Reporting simple : basculement de transaction, retrait de l'astérisque et validation autorisée sans SIREN.
 * - [N3B] e-Reporting + Livraison : déploiement animé du sous-formulaire d'adresse, saisie des champs et défilement scrollbar sans régression.
 */
@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalTestApi::class)
class InvoiceFormRegulationRobolectricTest {

    /**
     * Test N2 : En mode B2B France (e-Invoicing), le SIREN/SIRET est obligatoire.
     * L'astérisque '*' est présent et la soumission sans identifiant est bloquée.
     */
    @Test
    fun testN2_b2bMode_requiresSirenWithAsterisk_andBlocksSubmissionWhenEmpty() = runComposeUiTest {
        val viewModel = InvoiceFormViewModel()
        setContent { InvoiceFormScreen(viewModel = viewModel) }

        // Vérification du mode par défaut : B2B France
        assertEquals(TransactionMode.E_INVOICING, viewModel.uiState.value.transactionMode)

        // Présence de l'astérisque obligatoire sur le libellé SIREN/SIRET
        onNodeWithTag(InvoiceFormTags.CLIENT_SIRET)
            .performScrollTo()
            .assertIsDisplayed()
        onNodeWithText("SIRET (14 chiffres) *")
            .assertIsDisplayed()

        // Remplir la raison sociale et une ligne pour isoler la validation du SIREN
        onNodeWithTag(InvoiceFormTags.CLIENT_NAME)
            .performScrollTo()
            .performTextInput("Acme Corp")
        onNodeWithTag(InvoiceFormTags.CLIENT_EMAIL)
            .performScrollTo()
            .performTextInput("contact@acme.fr")
        onNodeWithTag(InvoiceFormTags.INVOICE_NUMBER)
            .performScrollTo()
            .performTextInput("FAC-2026-001")
        onNodeWithTag(InvoiceFormTags.ISSUE_DATE)
            .performScrollTo()
            .assertIsDisplayed()
        onNodeWithTag(InvoiceFormTags.DUE_DATE)
            .performScrollTo()
            .assertIsDisplayed()

        // Ligne de facturation
        onNodeWithTag(InvoiceFormTags.lineLabelTag(0))
            .performScrollTo()
            .performTextInput("Conseil fiscal")
        onNodeWithTag(InvoiceFormTags.lineQuantityTag(0))
            .performScrollTo()
            .performTextInput("1")
        onNodeWithTag(InvoiceFormTags.lineUnitPriceTag(0))
            .performScrollTo()
            .performTextInput("100.00")

        // Clic sur le bouton de soumission
        onNodeWithTag(InvoiceFormTags.SUBMIT_BUTTON)
            .performScrollTo()
            .performClick()

        // Blocage de soumission : l'erreur sur le SIRET/SIREN est visible
        onNodeWithTag(InvoiceFormTags.errorTagFor(InvoiceFormField.CLIENT_SIRET))
            .performScrollTo()
            .assertIsDisplayed()

        assertNull(viewModel.uiState.value.submittedInvoice)
        assertTrue(viewModel.uiState.value.errors.containsKey(InvoiceFormField.CLIENT_SIRET))
    }

    /**
     * Test N3A : En mode e-Reporting (B2C / International), le SIREN/SIRET devient optionnel.
     * Le basculement retire l'astérisque et permet la validation sans identifiant fiscal.
     */
    @Test
    fun testN3A_eReportingMode_removesAsterisk_andAllowsValidationWithoutSiren() = runComposeUiTest {
        val viewModel = InvoiceFormViewModel()
        setContent { InvoiceFormScreen(viewModel = viewModel) }

        // Bascule vers l'onglet e-Reporting
        onNodeWithTag(InvoiceFormTags.TRANSACTION_MODE_EREPORTING)
            .performScrollTo()
            .selectSegment()

        assertEquals(TransactionMode.E_REPORTING, viewModel.uiState.value.transactionMode)

        // L'astérisque '*' est retiré du libellé
        onNodeWithTag(InvoiceFormTags.CLIENT_SIRET)
            .performScrollTo()
            .assertIsDisplayed()
        onNodeWithText("SIRET (14 chiffres)")
            .assertIsDisplayed()
        onNodeWithText("SIRET (14 chiffres) *")
            .assertDoesNotExist()

        // Remplir les champs obligatoires (hors SIRET)
        onNodeWithTag(InvoiceFormTags.CLIENT_NAME)
            .performScrollTo()
            .performTextInput("Client Particulier")
        onNodeWithTag(InvoiceFormTags.CLIENT_EMAIL)
            .performScrollTo()
            .performTextInput("client@particulier.fr")
        onNodeWithTag(InvoiceFormTags.INVOICE_NUMBER)
            .performScrollTo()
            .performTextInput("FAC-2026-B2C-001")
        onNodeWithTag(InvoiceFormTags.ISSUE_DATE)
            .performScrollTo()
            .assertIsDisplayed()
        onNodeWithTag(InvoiceFormTags.DUE_DATE)
            .performScrollTo()
            .assertIsDisplayed()

        // Ligne de prestation
        onNodeWithTag(InvoiceFormTags.lineLabelTag(0))
            .performScrollTo()
            .performTextInput("Prestation de service")
        onNodeWithTag(InvoiceFormTags.lineQuantityTag(0))
            .performScrollTo()
            .performTextInput("1")
        onNodeWithTag(InvoiceFormTags.lineUnitPriceTag(0))
            .performScrollTo()
            .performTextInput("50.00")

        // Aucune erreur sur le SIRET dans l'état UI
        assertFalse(viewModel.uiState.value.errors.containsKey(InvoiceFormField.CLIENT_SIRET))

        // Clic sur le bouton de soumission dynamique e-Reporting
        onNodeWithTag(InvoiceFormTags.SUBMIT_BUTTON)
            .performScrollTo()
            .assertTextContains("Transmettre en e-Reporting", substring = true)
            .performClick()

        // Le SIRET n'a généré aucune erreur bloquante
        onNodeWithTag(InvoiceFormTags.errorTagFor(InvoiceFormField.CLIENT_SIRET))
            .assertDoesNotExist()
    }

    /**
     * Test N3B : Déploiement du sous-formulaire d'adresse via la case à cocher,
     * saisie des coordonnées de livraison et vérification du scroll.
     */
    @Test
    fun testN3B_eReportingWithDeliveryAddress_togglesSubform_andPreservesScroll() = runComposeUiTest {
        val viewModel = InvoiceFormViewModel()
        setContent { InvoiceFormScreen(viewModel = viewModel) }

        // Bascule vers e-Reporting
        onNodeWithTag(InvoiceFormTags.TRANSACTION_MODE_EREPORTING)
            .performScrollTo()
            .selectSegment()

        // Le sous-formulaire d'adresse n'est pas affiché initialement
        onNodeWithTag(InvoiceFormTags.DELIVERY_STREET).assertDoesNotExist()

        // Cocher « Adresse de livraison différente »
        onNodeWithTag(InvoiceFormTags.DIFFERENT_DELIVERY_ADDRESS_CHECKBOX)
            .performScrollTo()
            .performClick()

        assertTrue(viewModel.uiState.value.hasDifferentDeliveryAddress)

        // Déploiement validé : les champs d'adresse sont accessibles dans la hiérarchie
        onNodeWithTag(InvoiceFormTags.DELIVERY_STREET)
            .performScrollTo()
            .assertIsDisplayed()
            .performTextInput("42 Avenue des Champs-Élysées")

        onNodeWithTag(InvoiceFormTags.DELIVERY_ZIP)
            .performScrollTo()
            .assertIsDisplayed()
            .performTextInput("75008")

        onNodeWithTag(InvoiceFormTags.DELIVERY_CITY)
            .performScrollTo()
            .assertIsDisplayed()
            .performTextInput("Paris")

        onNodeWithTag(InvoiceFormTags.DELIVERY_COUNTRY)
            .performScrollTo()
            .assertIsDisplayed()
            .performTextClearance()
        onNodeWithTag(InvoiceFormTags.DELIVERY_COUNTRY)
            .performTextInput("Belgique")

        // Vérification de la persistance des valeurs saisies dans l'UiState
        assertEquals("42 Avenue des Champs-Élysées", viewModel.uiState.value.deliveryStreet)
        assertEquals("75008", viewModel.uiState.value.deliveryZip)
        assertEquals("Paris", viewModel.uiState.value.deliveryCity)
        assertEquals("Belgique", viewModel.uiState.value.deliveryCountry)

        // Vérification de la non-régression de scroll : les boutons finaux restent atteignables
        onNodeWithTag(InvoiceFormTags.SAVE_DRAFT_BUTTON)
            .performScrollTo()
            .assertIsDisplayed()
        onNodeWithTag(InvoiceFormTags.SUBMIT_BUTTON)
            .performScrollTo()
            .assertIsDisplayed()
    }
}

/** Sélectionne un segment d'un SegmentedButton M3 sous Robolectric. */
private fun SemanticsNodeInteraction.selectSegment(): SemanticsNodeInteraction =
    performSemanticsAction(SemanticsActions.OnClick)
