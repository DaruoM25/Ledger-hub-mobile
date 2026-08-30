package com.ledgerhub.presentation.invoiceform

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
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
private class FakeClientDirectory(initial: List<Party> = emptyList()) : ClientRepository {
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
 * Niveau 3 — sélecteur client (US-11) rendu sous Robolectric, avec un vrai
 * [InvoiceFormViewModel] : la frappe, le filtrage, la sélection et la création rapide sont
 * exercés de bout en bout.
 */
@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
class ClientPickerRobolectricTest {

    private val boulangerie = Party("Boulangerie Moreau SARL", "784102336", "78410233600004", "compta@moreau.fr")
    private val bouchon = Party("Bouchon Lyonnais SAS", "732829320", "73282932000074", "contact@bouchon.fr")

    private fun directory(vararg clients: Party) = FakeClientDirectory(clients.toList())

    private fun viewModelFor(directory: FakeClientDirectory) = InvoiceFormViewModel(
        dispatcher = UnconfinedTestDispatcher(),
        clientRepository = directory,
    )

    // ── Scénario 1 : recherche, sélection, auto-complétion ──────────────────

    @Test
    fun searchingAnExistingClient_thenSelectingIt_autoFillsSiretAndEmail() = runComposeUiTest {
        setContent { InvoiceFormScreen(viewModel = viewModelFor(directory(boulangerie, bouchon))) }

        onNodeWithTag(ClientPickerTags.CLIENT_SEARCH_INPUT).performScrollTo().performTextInput("Boul")

        onNodeWithTag(ClientPickerTags.CLIENT_SUGGESTIONS_LIST).performScrollTo().assertIsDisplayed()
        onNodeWithTag(ClientPickerTags.suggestionItem("78410233600004"))
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()

        onNodeWithTag(ClientPickerTags.CLIENT_SEARCH_INPUT)
            .performScrollTo()
            .assertTextContains("Boulangerie Moreau SARL")
        onNodeWithTag(InvoiceFormTags.CLIENT_SIRET).performScrollTo().assertTextContains("78410233600004")
        onNodeWithTag(InvoiceFormTags.CLIENT_EMAIL).performScrollTo().assertTextContains("compta@moreau.fr")
        // La liste se referme une fois le choix fait.
        onAllNodesWithTag(ClientPickerTags.CLIENT_SUGGESTIONS_LIST).assertCountEquals(0)
    }

    @Test
    fun typingAPrefix_narrowsTheDisplayedSuggestions() = runComposeUiTest {
        setContent { InvoiceFormScreen(viewModel = viewModelFor(directory(boulangerie, bouchon))) }

        onNodeWithTag(ClientPickerTags.CLIENT_SEARCH_INPUT).performScrollTo().performTextInput("Boulangerie")

        onNodeWithTag(ClientPickerTags.suggestionItem("78410233600004")).performScrollTo().assertIsDisplayed()
        onAllNodesWithTag(ClientPickerTags.suggestionItem("73282932000074")).assertCountEquals(0)
    }

    // ── Scénario 2 : client inconnu → création rapide → sélection ───────────

    @Test
    fun typingAnUnknownName_revealsTheGreenAddButton() = runComposeUiTest {
        setContent { InvoiceFormScreen(viewModel = viewModelFor(directory(boulangerie))) }

        onNodeWithTag(ClientPickerTags.CLIENT_SEARCH_INPUT).performScrollTo().performTextInput("Client Inconnu SAS")

        onNodeWithTag(ClientPickerTags.ADD_NEW_CLIENT_BTN).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun quickCreatingAClient_closesTheDialog_selectsIt_andPersistsIt() = runComposeUiTest {
        val directory = directory(boulangerie)
        setContent { InvoiceFormScreen(viewModel = viewModelFor(directory)) }

        onNodeWithTag(ClientPickerTags.CLIENT_SEARCH_INPUT).performScrollTo().performTextInput("Client Inconnu SAS")
        onNodeWithTag(ClientPickerTags.ADD_NEW_CLIENT_BTN).performScrollTo().performClick()

        onNodeWithTag(ClientPickerTags.QUICK_CLIENT_DIALOG).assertIsDisplayed()
        // Le nom déjà tapé est repris : l'utilisateur ne le ressaisit pas.
        onNodeWithTag(ClientPickerTags.QUICK_CLIENT_NAME_INPUT).assertTextContains("Client Inconnu SAS")

        onNodeWithTag(ClientPickerTags.QUICK_CLIENT_SIRET_INPUT).performTextInput("73282932000074")
        onNodeWithTag(ClientPickerTags.QUICK_CLIENT_EMAIL_INPUT).performTextInput("contact@inconnu.fr")
        onNodeWithTag(ClientPickerTags.QUICK_CLIENT_SAVE_BTN).performClick()

        // La modale se ferme…
        onAllNodesWithTag(ClientPickerTags.QUICK_CLIENT_DIALOG).assertCountEquals(0)
        // …le client est sélectionné dans la facture courante…
        onNodeWithTag(ClientPickerTags.CLIENT_SEARCH_INPUT).performScrollTo().assertTextContains("Client Inconnu SAS")
        onNodeWithTag(InvoiceFormTags.CLIENT_SIRET).performScrollTo().assertTextContains("73282932000074")
        onNodeWithTag(InvoiceFormTags.CLIENT_EMAIL).performScrollTo().assertTextContains("contact@inconnu.fr")
        // …et persisté dans l'annuaire.
        val stored = directory.clients.single { it.siret == "73282932000074" }
        assertEquals("Client Inconnu SAS", stored.name)
    }

    @Test
    fun quickCreatingWithASiretFailingLuhn_keepsTheDialogOpen_andShowsTheError() = runComposeUiTest {
        val directory = directory()
        setContent { InvoiceFormScreen(viewModel = viewModelFor(directory)) }

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
    fun cancellingTheDialog_closesItWithoutPersisting() = runComposeUiTest {
        val directory = directory()
        setContent { InvoiceFormScreen(viewModel = viewModelFor(directory)) }

        onNodeWithTag(ClientPickerTags.CLIENT_SEARCH_INPUT).performScrollTo().performTextInput("Client Inconnu SAS")
        onNodeWithTag(ClientPickerTags.ADD_NEW_CLIENT_BTN).performScrollTo().performClick()
        onNodeWithTag(ClientPickerTags.QUICK_CLIENT_DIALOG).assertIsDisplayed()

        onNodeWithTag(ClientPickerTags.QUICK_CLIENT_CANCEL_BTN).performClick()

        onAllNodesWithTag(ClientPickerTags.QUICK_CLIENT_DIALOG).assertCountEquals(0)
        assertTrue(directory.clients.isEmpty())
    }

    @Test
    fun clearingTheQuery_hidesTheAddButton() = runComposeUiTest {
        setContent { InvoiceFormScreen(viewModel = viewModelFor(directory(boulangerie))) }

        onNodeWithTag(ClientPickerTags.CLIENT_SEARCH_INPUT).performScrollTo().performTextInput("Client Inconnu SAS")
        onNodeWithTag(ClientPickerTags.ADD_NEW_CLIENT_BTN).performScrollTo().assertIsDisplayed()

        onNodeWithTag(ClientPickerTags.CLIENT_SEARCH_INPUT).performTextClearance()

        onAllNodesWithTag(ClientPickerTags.ADD_NEW_CLIENT_BTN).assertCountEquals(0)
    }
}
