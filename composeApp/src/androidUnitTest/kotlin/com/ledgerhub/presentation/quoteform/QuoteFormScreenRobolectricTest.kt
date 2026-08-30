package com.ledgerhub.presentation.quoteform

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.runComposeUiTest
import com.ledgerhub.domain.client.ClientRepository
import com.ledgerhub.domain.client.DuplicateClientException
import com.ledgerhub.domain.invoice.Party
import com.ledgerhub.presentation.components.ClientPickerTags
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Annuaire en mémoire, mêmes règles que l'implémentation SQLDelight. */
private class FakeQuoteClientDirectory(initial: List<Party> = emptyList()) : ClientRepository {
    val clients = initial.toMutableList()

    override suspend fun fetchClients(): Result<List<Party>> =
        Result.success(clients.sortedBy { it.name.lowercase() })

    override suspend fun searchClients(query: String): Result<List<Party>> {
        val prefix = query.trim()
        val matches = if (prefix.isEmpty()) clients else clients.filter {
            it.name.startsWith(prefix, ignoreCase = true)
        }
        return Result.success(matches.sortedBy { it.name.lowercase() })
    }

    override suspend fun createClient(client: Party): Result<Unit> =
        if (clients.any { it.siret == client.siret }) {
            Result.failure(DuplicateClientException(client.siret))
        } else {
            clients += client
            Result.success(Unit)
        }

    override suspend fun updateClient(client: Party): Result<Unit> = Result.success(Unit)
    override suspend fun deleteClient(siret: String): Result<Unit> = Result.success(Unit)
    override suspend fun countInvoicesFor(siret: String): Result<Long> = Result.success(0L)
}

/**
 * Équivalent Android de [QuoteFormScreenTest] (commonTest, qui sert iosTest sans modification).
 * Voir HelloScreenRobolectricTest pour le détail de cette duplication ciblée, imposée par
 * l'impossibilité d'ajouter @RunWith(RobolectricTestRunner) à une classe partagée commonTest.
 */
@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalTestApi::class)
class QuoteFormScreenRobolectricTest {

    @Test
    fun initialState_submitButtonIsDisabled() = runComposeUiTest {
        setContent { QuoteFormScreen(viewModel = QuoteFormViewModel()) }

        onNodeWithTag(QuoteFormTags.SUBMIT_BUTTON).performScrollTo().assertIsDisplayed()
        onNodeWithTag(QuoteFormTags.SUBMIT_BUTTON).assertIsNotEnabled()
    }

    @Test
    fun typingInvalidSiren_displaysFieldError() = runComposeUiTest {
        setContent { QuoteFormScreen(viewModel = QuoteFormViewModel()) }

        onNodeWithTag(QuoteFormTags.ISSUER_SIREN).performScrollTo().performTextInput("123")

        onNodeWithTag(QuoteFormTags.errorTagFor(QuoteFormField.ISSUER_SIREN))
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun typingQuantityAndPrice_updatesTotalTtc() = runComposeUiTest {
        setContent { QuoteFormScreen(viewModel = QuoteFormViewModel()) }

        onNodeWithTag(QuoteFormTags.lineQuantityTag(0)).performScrollTo().performTextInput("2")
        onNodeWithTag(QuoteFormTags.lineUnitPriceTag(0)).performScrollTo().performTextInput("50.00")

        onNodeWithTag(QuoteFormTags.TOTAL_TTC).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun addLineButton_appendsSecondLine_withOwnRemoveButton() = runComposeUiTest {
        setContent { QuoteFormScreen(viewModel = QuoteFormViewModel()) }

        onNodeWithTag(QuoteFormTags.lineRemoveButtonTag(0)).performScrollTo().assertIsNotEnabled()

        onNodeWithTag(QuoteFormTags.ADD_LINE_BUTTON).performScrollTo().performClick()

        onNodeWithTag(QuoteFormTags.lineLabelTag(1)).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun removeLineButton_removesLine() = runComposeUiTest {
        setContent { QuoteFormScreen(viewModel = QuoteFormViewModel()) }

        onNodeWithTag(QuoteFormTags.ADD_LINE_BUTTON).performScrollTo().performClick()
        onNodeWithTag(QuoteFormTags.lineLabelTag(1)).performScrollTo().assertIsDisplayed()

        onNodeWithTag(QuoteFormTags.lineRemoveButtonTag(1)).performScrollTo().performClick()

        onNodeWithTag(QuoteFormTags.lineLabelTag(1)).assertDoesNotExist()
        onNodeWithTag(QuoteFormTags.lineLabelTag(0)).performScrollTo().assertIsDisplayed()
    }

    // ── Sélecteur client (US-11) — parité avec le formulaire de facture ──────

    // SIRET Luhn-valide : la création rapide contrôle la clé, pas seulement la longueur.
    private val boulangerie = Party("Boulangerie Moreau SARL", "784102336", "78410233600004", "compta@moreau.fr")

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun viewModelFor(directory: FakeQuoteClientDirectory) = QuoteFormViewModel(
        dispatcher = UnconfinedTestDispatcher(),
        clientRepository = directory,
    )

    @Test
    fun searchingAnExistingClient_thenSelectingIt_autoFillsSirenAndSiret() = runComposeUiTest {
        setContent { QuoteFormScreen(viewModel = viewModelFor(FakeQuoteClientDirectory(listOf(boulangerie)))) }

        onNodeWithTag(ClientPickerTags.CLIENT_SEARCH_INPUT).performScrollTo().performTextInput("Boul")

        onNodeWithTag(ClientPickerTags.CLIENT_SUGGESTIONS_LIST).performScrollTo().assertIsDisplayed()
        onNodeWithTag(ClientPickerTags.suggestionItem("78410233600004"))
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()

        onNodeWithTag(ClientPickerTags.CLIENT_SEARCH_INPUT)
            .performScrollTo()
            .assertTextContains("Boulangerie Moreau SARL")
        onNodeWithTag(QuoteFormTags.RECIPIENT_SIREN).performScrollTo().assertTextContains("784102336")
        onNodeWithTag(QuoteFormTags.RECIPIENT_SIRET).performScrollTo().assertTextContains("78410233600004")
        // La liste se referme une fois le choix fait.
        onAllNodesWithTag(ClientPickerTags.CLIENT_SUGGESTIONS_LIST).assertCountEquals(0)
    }

    @Test
    fun typingAnUnknownName_revealsTheGreenAddButton() = runComposeUiTest {
        setContent { QuoteFormScreen(viewModel = viewModelFor(FakeQuoteClientDirectory(listOf(boulangerie)))) }

        onNodeWithTag(ClientPickerTags.CLIENT_SEARCH_INPUT).performScrollTo().performTextInput("Client Inconnu SAS")

        onNodeWithTag(ClientPickerTags.ADD_NEW_CLIENT_BTN).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun quickCreatingAClientFromAQuote_closesTheDialog_selectsIt_andPersistsIt() = runComposeUiTest {
        val directory = FakeQuoteClientDirectory()
        setContent { QuoteFormScreen(viewModel = viewModelFor(directory)) }

        onNodeWithTag(ClientPickerTags.CLIENT_SEARCH_INPUT).performScrollTo().performTextInput("Client Inconnu SAS")
        onNodeWithTag(ClientPickerTags.ADD_NEW_CLIENT_BTN).performScrollTo().performClick()

        onNodeWithTag(ClientPickerTags.QUICK_CLIENT_DIALOG).assertIsDisplayed()
        // Le nom déjà tapé est repris : l'utilisateur ne le ressaisit pas.
        onNodeWithTag(ClientPickerTags.QUICK_CLIENT_NAME_INPUT).assertTextContains("Client Inconnu SAS")

        onNodeWithTag(ClientPickerTags.QUICK_CLIENT_SIRET_INPUT).performTextInput("73282932000074")
        onNodeWithTag(ClientPickerTags.QUICK_CLIENT_EMAIL_INPUT).performTextInput("contact@inconnu.fr")
        onNodeWithTag(ClientPickerTags.QUICK_CLIENT_SAVE_BTN).performClick()

        onAllNodesWithTag(ClientPickerTags.QUICK_CLIENT_DIALOG).assertCountEquals(0)
        onNodeWithTag(ClientPickerTags.CLIENT_SEARCH_INPUT).performScrollTo().assertTextContains("Client Inconnu SAS")
        onNodeWithTag(QuoteFormTags.RECIPIENT_SIRET).performScrollTo().assertTextContains("73282932000074")
        assertEquals("Client Inconnu SAS", directory.clients.single().name)
    }

    @Test
    fun quickCreatingWithASiretFailingLuhn_keepsTheDialogOpen_andShowsTheError() = runComposeUiTest {
        val directory = FakeQuoteClientDirectory()
        setContent { QuoteFormScreen(viewModel = viewModelFor(directory)) }

        onNodeWithTag(ClientPickerTags.CLIENT_SEARCH_INPUT).performScrollTo().performTextInput("Client Inconnu SAS")
        onNodeWithTag(ClientPickerTags.ADD_NEW_CLIENT_BTN).performScrollTo().performClick()

        // 14 chiffres mais clé de Luhn fausse.
        onNodeWithTag(ClientPickerTags.QUICK_CLIENT_SIRET_INPUT).performTextInput("78410233600022")
        onNodeWithTag(ClientPickerTags.QUICK_CLIENT_EMAIL_INPUT).performTextInput("contact@inconnu.fr")
        onNodeWithTag(ClientPickerTags.QUICK_CLIENT_SAVE_BTN).performClick()

        onNodeWithTag(ClientPickerTags.QUICK_CLIENT_DIALOG).assertIsDisplayed()
        onNodeWithTag(ClientPickerTags.quickClientError("SIRET")).assertIsDisplayed()
        assertTrue(directory.clients.isEmpty())
    }

    @Test
    fun cancellingTheQuickDialog_closesItWithoutPersisting() = runComposeUiTest {
        val directory = FakeQuoteClientDirectory()
        setContent { QuoteFormScreen(viewModel = viewModelFor(directory)) }

        onNodeWithTag(ClientPickerTags.CLIENT_SEARCH_INPUT).performScrollTo().performTextInput("Client Inconnu SAS")
        onNodeWithTag(ClientPickerTags.ADD_NEW_CLIENT_BTN).performScrollTo().performClick()
        onNodeWithTag(ClientPickerTags.QUICK_CLIENT_DIALOG).assertIsDisplayed()

        onNodeWithTag(ClientPickerTags.QUICK_CLIENT_CANCEL_BTN).performClick()

        onAllNodesWithTag(ClientPickerTags.QUICK_CLIENT_DIALOG).assertCountEquals(0)
        assertTrue(directory.clients.isEmpty())
    }
}
