package com.ledgerhub.presentation.invoiceform

import com.ledgerhub.domain.client.ClientRepository
import com.ledgerhub.domain.client.DuplicateClientException
import com.ledgerhub.domain.invoice.Party
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Dépôt clients en mémoire — mêmes règles que l'implémentation SQLDelight (création refusée sur
 * SIRET connu, recherche par préfixe insensible à la casse).
 */
internal class InMemoryClientRepository(
    initial: List<Party> = emptyList(),
) : ClientRepository {
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

    override suspend fun updateClient(client: Party): Result<Unit> {
        val index = clients.indexOfFirst { it.siret == client.siret }
        if (index >= 0) clients[index] = client
        return Result.success(Unit)
    }

    override suspend fun deleteClient(siret: String): Result<Unit> {
        clients.removeAll { it.siret == siret }
        return Result.success(Unit)
    }

    override suspend fun countInvoicesFor(siret: String): Result<Long> = Result.success(0L)
}

/**
 * Niveau 1 — logique du sélecteur client (US-11) : filtrage à la frappe, auto-remplissage
 * SIRET/email, visibilité du bouton d'ajout, et création rapide validée par la clé de Luhn.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ClientPickerLogicTest {

    // SIRET Luhn-valides (voir LuhnChecksum) — une coquille les rendrait invalides.
    private val boulangerie = Party("Boulangerie Moreau SARL", "784102336", "78410233600004", "compta@moreau.fr")
    private val bouchon = Party("Bouchon Lyonnais SAS", "732829320", "73282932000074", "contact@bouchon.fr")
    private val atelier = Party("Atelier Martin", "443061841", "44306184100005", "hello@atelier-martin.fr")

    private fun newViewModel(vararg clients: Party): Pair<InvoiceFormViewModel, InMemoryClientRepository> {
        val repository = InMemoryClientRepository(clients.toList())
        val viewModel = InvoiceFormViewModel(
            dispatcher = UnconfinedTestDispatcher(),
            clientRepository = repository,
        )
        return viewModel to repository
    }

    // ── Filtrage au fil de la frappe ────────────────────────────────────────

    @Test
    fun initialState_offersEveryKnownClient_beforeAnyKeystroke() = runTest {
        val (viewModel, _) = newViewModel(boulangerie, bouchon, atelier)

        val state = viewModel.uiState.value
        assertEquals(3, state.clientSuggestions.size)
        assertEquals("", state.clientQuery)
        assertNull(state.selectedClient)
    }

    @Test
    fun typingAPrefix_narrowsTheSuggestions() = runTest {
        val (viewModel, _) = newViewModel(boulangerie, bouchon, atelier)

        viewModel.processIntent(InvoiceFormIntent.OnClientQueryChanged("Bou"))

        val state = viewModel.uiState.value
        assertEquals(
            listOf("Bouchon Lyonnais SAS", "Boulangerie Moreau SARL"),
            state.clientSuggestions.map { it.name },
        )
        assertTrue(state.isClientDropdownExpanded)
    }

    @Test
    fun filteringIsCaseInsensitive() = runTest {
        val (viewModel, _) = newViewModel(boulangerie, bouchon, atelier)

        viewModel.processIntent(InvoiceFormIntent.OnClientQueryChanged("BOULANGERIE"))

        assertEquals(listOf("Boulangerie Moreau SARL"), viewModel.uiState.value.clientSuggestions.map { it.name })
    }

    @Test
    fun typingAnUnknownName_emptiesTheSuggestions() = runTest {
        val (viewModel, _) = newViewModel(boulangerie, bouchon)

        viewModel.processIntent(InvoiceFormIntent.OnClientQueryChanged("Client Inconnu SAS"))

        assertTrue(viewModel.uiState.value.clientSuggestions.isEmpty())
    }

    @Test
    fun legacyClientNameChanged_behavesAsAQuery() = runTest {
        val (viewModel, _) = newViewModel(boulangerie, bouchon, atelier)

        viewModel.processIntent(InvoiceFormIntent.ClientNameChanged("Bou"))

        val state = viewModel.uiState.value
        assertEquals("Bou", state.clientQuery)
        assertEquals("Bou", state.clientName)
        assertEquals(2, state.clientSuggestions.size)
    }

    // ── Auto-remplissage SIRET / email ──────────────────────────────────────

    @Test
    fun selectingASuggestion_fillsNameSiretAndEmail() = runTest {
        val (viewModel, _) = newViewModel(boulangerie, bouchon)
        viewModel.processIntent(InvoiceFormIntent.OnClientQueryChanged("Bou"))

        viewModel.processIntent(InvoiceFormIntent.OnClientSelected(boulangerie))

        val state = viewModel.uiState.value
        assertEquals("Boulangerie Moreau SARL", state.clientName)
        assertEquals("Boulangerie Moreau SARL", state.clientQuery)
        assertEquals("78410233600004", state.clientSiret)
        assertEquals("compta@moreau.fr", state.clientEmail)
        assertEquals(boulangerie, state.selectedClient)
        assertFalse(state.isClientDropdownExpanded)
    }

    @Test
    fun selectingASuggestion_clearsTheClientValidationErrors() = runTest {
        val (viewModel, _) = newViewModel(boulangerie)

        viewModel.processIntent(InvoiceFormIntent.OnClientSelected(boulangerie))

        val errors = viewModel.uiState.value.errors
        assertNull(errors[InvoiceFormField.CLIENT_NAME])
        assertNull(errors[InvoiceFormField.CLIENT_SIRET])
        assertNull(errors[InvoiceFormField.CLIENT_EMAIL])
    }

    @Test
    fun editingSiretByHand_afterASelection_breaksTheLinkToTheRecord() = runTest {
        val (viewModel, _) = newViewModel(boulangerie)
        viewModel.processIntent(InvoiceFormIntent.OnClientSelected(boulangerie))
        assertNotNull(viewModel.uiState.value.selectedClient)

        viewModel.processIntent(InvoiceFormIntent.ClientSiretChanged("78410233600099"))

        // Le SIRET saisi reste, mais l'état cesse d'affirmer qu'une fiche est sélectionnée.
        assertEquals("78410233600099", viewModel.uiState.value.clientSiret)
        assertNull(viewModel.uiState.value.selectedClient)
    }

    // ── Visibilité du bouton « + Ajouter comme nouveau client » ──────────────

    @Test
    fun addButton_isHidden_whenTheQueryIsEmpty() = runTest {
        val (viewModel, _) = newViewModel(boulangerie)
        assertFalse(viewModel.uiState.value.showAddNewClientButton)
    }

    @Test
    fun addButton_isHidden_whileSuggestionsRemain() = runTest {
        val (viewModel, _) = newViewModel(boulangerie, bouchon)

        viewModel.processIntent(InvoiceFormIntent.OnClientQueryChanged("Bou"))

        assertFalse(viewModel.uiState.value.showAddNewClientButton)
    }

    @Test
    fun addButton_appears_whenTheQueryMatchesNoRecord() = runTest {
        val (viewModel, _) = newViewModel(boulangerie, bouchon)

        viewModel.processIntent(InvoiceFormIntent.OnClientQueryChanged("Client Inconnu SAS"))

        assertTrue(viewModel.uiState.value.showAddNewClientButton)
    }

    @Test
    fun addButton_isHidden_onceAClientIsSelected() = runTest {
        val (viewModel, _) = newViewModel(boulangerie)
        viewModel.processIntent(InvoiceFormIntent.OnClientSelected(boulangerie))

        assertFalse(viewModel.uiState.value.showAddNewClientButton)
    }

    // ── Création rapide ─────────────────────────────────────────────────────

    @Test
    fun openingTheDialog_prefillsTheNameWithTheTypedText() = runTest {
        val (viewModel, _) = newViewModel(boulangerie)
        viewModel.processIntent(InvoiceFormIntent.OnClientQueryChanged("  Client Inconnu SAS  "))

        viewModel.processIntent(InvoiceFormIntent.OnOpenQuickClientDialog)

        val state = viewModel.uiState.value
        assertTrue(state.showQuickClientDialog)
        assertEquals("Client Inconnu SAS", state.quickClientDraft?.name)
        assertFalse(state.isClientDropdownExpanded)
    }

    @Test
    fun savingAValidQuickClient_persistsSelectsAndClosesTheDialog() = runTest {
        val (viewModel, repository) = newViewModel(boulangerie)
        viewModel.processIntent(InvoiceFormIntent.OnClientQueryChanged("Nouveau Client SAS"))
        viewModel.processIntent(InvoiceFormIntent.OnOpenQuickClientDialog)

        viewModel.processIntent(
            InvoiceFormIntent.OnSaveQuickClient(
                name = "Nouveau Client SAS",
                siret = "73282932000074",
                email = "contact@nouveau.fr",
            ),
        )

        val state = viewModel.uiState.value
        assertFalse(state.showQuickClientDialog)
        assertNull(state.quickClientDraft)
        assertEquals("Nouveau Client SAS", state.clientName)
        assertEquals("73282932000074", state.clientSiret)
        assertEquals("contact@nouveau.fr", state.clientEmail)
        assertNotNull(state.selectedClient)
        // Persisté dans l'annuaire, avec le SIREN dérivé du SIRET (règle INSEE).
        val stored = repository.clients.single { it.siret == "73282932000074" }
        assertEquals("Nouveau Client SAS", stored.name)
        assertEquals("732829320", stored.siren)
    }

    @Test
    fun savingWithASiretFailingTheLuhnKey_isRefused_andKeepsTheDialogOpen() = runTest {
        val (viewModel, repository) = newViewModel()
        viewModel.processIntent(InvoiceFormIntent.OnClientQueryChanged("Nouveau Client SAS"))
        viewModel.processIntent(InvoiceFormIntent.OnOpenQuickClientDialog)

        // 14 chiffres, mais clé de Luhn fausse — la seule longueur ne suffit pas.
        viewModel.processIntent(
            InvoiceFormIntent.OnSaveQuickClient("Nouveau Client SAS", "78410233600022", "contact@nouveau.fr"),
        )

        val state = viewModel.uiState.value
        assertTrue(state.showQuickClientDialog, "la modale reste ouverte pour corriger")
        assertNotNull(state.quickClientDraft?.errors?.get(QuickClientField.SIRET))
        assertTrue(repository.clients.isEmpty())
    }

    @Test
    fun savingWithAnInvalidEmailOrBlankName_reportsTheFaultyFields() = runTest {
        val (viewModel, repository) = newViewModel()
        viewModel.processIntent(InvoiceFormIntent.OnOpenQuickClientDialog)

        viewModel.processIntent(InvoiceFormIntent.OnSaveQuickClient("", "78410233600004", "pas-un-email"))

        val errors = viewModel.uiState.value.quickClientDraft?.errors.orEmpty()
        assertNotNull(errors[QuickClientField.NAME])
        assertNotNull(errors[QuickClientField.EMAIL])
        assertNull(errors[QuickClientField.SIRET])
        assertTrue(repository.clients.isEmpty())
    }

    @Test
    fun savingAnAlreadyKnownSiret_isRefused_withoutOverwritingTheRecord() = runTest {
        val (viewModel, repository) = newViewModel(boulangerie)
        viewModel.processIntent(InvoiceFormIntent.OnOpenQuickClientDialog)

        viewModel.processIntent(
            InvoiceFormIntent.OnSaveQuickClient("Doublon SARL", boulangerie.siret, "doublon@test.fr"),
        )

        val state = viewModel.uiState.value
        assertTrue(state.showQuickClientDialog)
        assertNotNull(state.quickClientDraft?.errors?.get(QuickClientField.SIRET))
        assertEquals(1, repository.clients.size)
        assertEquals("Boulangerie Moreau SARL", repository.clients.single().name)
    }

    @Test
    fun dismissingTheDialog_persistsNothing_andClearsTheDraft() = runTest {
        val (viewModel, repository) = newViewModel()
        viewModel.processIntent(InvoiceFormIntent.OnClientQueryChanged("Client Inconnu SAS"))
        viewModel.processIntent(InvoiceFormIntent.OnOpenQuickClientDialog)

        viewModel.processIntent(InvoiceFormIntent.OnDismissQuickClientDialog)

        val state = viewModel.uiState.value
        assertFalse(state.showQuickClientDialog)
        assertNull(state.quickClientDraft)
        assertTrue(repository.clients.isEmpty())
    }

    // ── Sans annuaire branché, le champ reste une saisie libre ──────────────

    @Test
    fun withoutAClientRepository_theFieldStillAcceptsFreeInput() = runTest {
        val viewModel = InvoiceFormViewModel(dispatcher = UnconfinedTestDispatcher())

        viewModel.processIntent(InvoiceFormIntent.ClientNameChanged("Client Manuel SARL"))

        val state = viewModel.uiState.value
        assertEquals("Client Manuel SARL", state.clientName)
        assertTrue(state.clientSuggestions.isEmpty())
        assertNull(state.errors[InvoiceFormField.CLIENT_NAME])
        // Pas d'annuaire : proposer la création rapide mènerait à une modale sans destination.
        assertFalse(state.showAddNewClientButton)
    }
}
