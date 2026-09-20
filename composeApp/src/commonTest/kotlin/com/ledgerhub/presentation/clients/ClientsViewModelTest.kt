package com.ledgerhub.presentation.clients

import com.ledgerhub.domain.client.ClientInUseException
import com.ledgerhub.domain.client.ClientRepository
import com.ledgerhub.domain.client.DuplicateClientException
import com.ledgerhub.domain.invoice.Party
import com.ledgerhub.data.sirene.MockSireneLookupService
import com.ledgerhub.domain.sirene.SireneLookupService
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Dépôt en mémoire — mêmes règles que l'implémentation SQLDelight : création refusée sur SIRET
 * connu, suppression refusée dès qu'une facture référence la fiche.
 */
private class FakeClientRepository(
    initial: List<Party> = emptyList(),
    private val invoiceCounts: Map<String, Long> = emptyMap(),
) : ClientRepository {
    val clients = initial.toMutableList()

    override suspend fun fetchClients(): Result<List<Party>> =
        Result.success(clients.sortedBy { it.name.lowercase() })

    override suspend fun searchClients(query: String): Result<List<Party>> =
        Result.success(
            clients.filter { it.name.startsWith(query.trim(), ignoreCase = true) }
                .sortedBy { it.name.lowercase() },
        )

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
        val count = invoiceCounts[siret] ?: 0L
        if (count > 0) return Result.failure(ClientInUseException(siret, count))
        clients.removeAll { it.siret == siret }
        return Result.success(Unit)
    }

    override suspend fun countInvoicesFor(siret: String): Result<Long> =
        Result.success(invoiceCounts[siret] ?: 0L)
}

@OptIn(ExperimentalCoroutinesApi::class)
class ClientsViewModelTest {

    private val validSiret = "78410233600021"

    private fun client(name: String = "Boulangerie Moreau SARL", siret: String = "78410233600021") =
        Party(name = name, siren = siret.take(9), siret = siret, email = "compta@moreau.fr")

    private fun viewModel(
        repository: ClientRepository,
        scheduler: kotlinx.coroutines.test.TestCoroutineScheduler,
        sireneLookupService: SireneLookupService = MockSireneLookupService(simulatedDelayMillis = 0L),
    ) = ClientsViewModel(
        repository = repository,
        sireneLookupService = sireneLookupService,
        dispatcher = StandardTestDispatcher(scheduler),
    )

    private fun fillForm(vm: ClientsViewModel, name: String, siret: String, email: String) {
        vm.processIntent(ClientsIntent.NameChanged(name))
        vm.processIntent(ClientsIntent.SiretChanged(siret))
        vm.processIntent(ClientsIntent.EmailChanged(email))
    }

    // ── Chargement ───────────────────────────────────────────────────────────────────────────

    @Test
    fun load_exposesPersistedClients_sortedByName() = runTest {
        val repository = FakeClientRepository(listOf(client("Zeta SAS", "11111111111111"), client("Alpha SARL", "22222222222222")))
        val vm = viewModel(repository, testScheduler)

        advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(state.isLoading)
        assertEquals(listOf("Alpha SARL", "Zeta SAS"), state.clients.map { it.name })
    }

    @Test
    fun load_withNoClient_exposesEmptyState() = runTest {
        val vm = viewModel(FakeClientRepository(), testScheduler)

        advanceUntilIdle()

        assertTrue(vm.uiState.value.isEmpty)
    }

    // ── Validation du formulaire ─────────────────────────────────────────────────────────────

    @Test
    fun freshForm_computesErrorsButPresentsNone() = runTest {
        val vm = viewModel(FakeClientRepository(), testScheduler)
        advanceUntilIdle()

        vm.processIntent(ClientsIntent.AddClicked)

        val form = assertNotNull(vm.uiState.value.form)
        assertFalse(form.isValid) // la validation tourne
        assertTrue(form.visibleErrors.isEmpty()) // mais rien n'est encore présenté
    }

    @Test
    fun siretShorterThan14Digits_isRejected() = runTest {
        val vm = viewModel(FakeClientRepository(), testScheduler)
        advanceUntilIdle()
        vm.processIntent(ClientsIntent.AddClicked)

        vm.processIntent(ClientsIntent.SiretChanged("1234567890123"))

        assertNotNull(vm.uiState.value.form?.visibleErrors?.get(ClientFormField.SIRET))
    }

    @Test
    fun siretOfExactly14Digits_clearsTheError() = runTest {
        val vm = viewModel(FakeClientRepository(), testScheduler)
        advanceUntilIdle()
        vm.processIntent(ClientsIntent.AddClicked)

        vm.processIntent(ClientsIntent.SiretChanged("1234567890123"))
        vm.processIntent(ClientsIntent.SiretChanged(validSiret))

        assertNull(vm.uiState.value.form?.errors?.get(ClientFormField.SIRET))
    }

    @Test
    fun companyNameShorterThanTwoCharacters_isRejected() = runTest {
        val vm = viewModel(FakeClientRepository(), testScheduler)
        advanceUntilIdle()
        vm.processIntent(ClientsIntent.AddClicked)

        vm.processIntent(ClientsIntent.NameChanged("A"))
        assertNotNull(vm.uiState.value.form?.visibleErrors?.get(ClientFormField.NAME))

        vm.processIntent(ClientsIntent.NameChanged("AB"))
        assertNull(vm.uiState.value.form?.errors?.get(ClientFormField.NAME))
    }

    @Test
    fun invalidEmail_isRejected() = runTest {
        val vm = viewModel(FakeClientRepository(), testScheduler)
        advanceUntilIdle()
        vm.processIntent(ClientsIntent.AddClicked)

        vm.processIntent(ClientsIntent.EmailChanged("pas-un-email"))
        assertNotNull(vm.uiState.value.form?.visibleErrors?.get(ClientFormField.EMAIL))

        vm.processIntent(ClientsIntent.EmailChanged("contact@client.fr"))
        assertNull(vm.uiState.value.form?.errors?.get(ClientFormField.EMAIL))
    }

    @Test
    fun submittingAnEmptyForm_revealsEveryErrorAndPersistsNothing() = runTest {
        val repository = FakeClientRepository()
        val vm = viewModel(repository, testScheduler)
        advanceUntilIdle()
        vm.processIntent(ClientsIntent.AddClicked)

        vm.processIntent(ClientsIntent.FormSubmitted)
        advanceUntilIdle()

        val form = assertNotNull(vm.uiState.value.form)
        assertTrue(form.saveAttempted)
        assertEquals(form.errors, form.visibleErrors)
        assertTrue(repository.clients.isEmpty())
    }

    // ── Création ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun creatingAValidClient_persistsIt_derivesSiren_andClosesTheForm() = runTest {
        val repository = FakeClientRepository()
        val vm = viewModel(repository, testScheduler)
        advanceUntilIdle()
        vm.processIntent(ClientsIntent.AddClicked)
        fillForm(vm, "Boulangerie Moreau SARL", validSiret, "compta@moreau.fr")

        vm.processIntent(ClientsIntent.FormSubmitted)
        advanceUntilIdle()

        val persisted = repository.clients.single()
        assertEquals("Boulangerie Moreau SARL", persisted.name)
        assertEquals(validSiret, persisted.siret)
        assertEquals("784102336", persisted.siren) // règle INSEE : préfixe à 9 chiffres
        assertNull(vm.uiState.value.form)
        assertEquals(1, vm.uiState.value.clients.size)
    }

    @Test
    fun creatingAClientOnAKnownSiret_isRejectedOnTheSiretField() = runTest {
        val repository = FakeClientRepository(listOf(client()))
        val vm = viewModel(repository, testScheduler)
        advanceUntilIdle()
        vm.processIntent(ClientsIntent.AddClicked)
        fillForm(vm, "Autre Raison SAS", validSiret, "autre@client.fr")

        vm.processIntent(ClientsIntent.FormSubmitted)
        advanceUntilIdle()

        val form = assertNotNull(vm.uiState.value.form) // le formulaire reste ouvert
        assertNotNull(form.visibleErrors[ClientFormField.SIRET])
        assertEquals(1, repository.clients.size)
        assertEquals("Boulangerie Moreau SARL", repository.clients.single().name) // rien n'est écrasé
    }

    // ── Édition ──────────────────────────────────────────────────────────────────────────────

    @Test
    fun editingAClient_prefillsTheFormAndMarksItAsEditing() = runTest {
        val vm = viewModel(FakeClientRepository(listOf(client())), testScheduler)
        advanceUntilIdle()

        vm.processIntent(ClientsIntent.EditClicked(client()))

        val form = assertNotNull(vm.uiState.value.form)
        assertTrue(form.isEditing)
        assertEquals(validSiret, form.editedSiret)
        assertEquals("Boulangerie Moreau SARL", form.name)
        assertTrue(form.isValid)
    }

    @Test
    fun savingAnEditedClient_updatesTheRecordInPlace() = runTest {
        val repository = FakeClientRepository(listOf(client()))
        val vm = viewModel(repository, testScheduler)
        advanceUntilIdle()
        vm.processIntent(ClientsIntent.EditClicked(client()))

        vm.processIntent(ClientsIntent.NameChanged("Boulangerie Moreau SAS"))
        vm.processIntent(ClientsIntent.FormSubmitted)
        advanceUntilIdle()

        assertEquals(1, repository.clients.size)
        assertEquals("Boulangerie Moreau SAS", repository.clients.single().name)
    }

    // ── Suppression ──────────────────────────────────────────────────────────────────────────

    @Test
    fun deletingAClientWithoutInvoices_removesIt() = runTest {
        val repository = FakeClientRepository(listOf(client()))
        val vm = viewModel(repository, testScheduler)
        advanceUntilIdle()

        vm.processIntent(ClientsIntent.DeleteClicked(client()))
        vm.processIntent(ClientsIntent.DeleteConfirmed)
        advanceUntilIdle()

        assertTrue(repository.clients.isEmpty())
        assertNull(vm.uiState.value.pendingDeletion)
    }

    @Test
    fun deletingAClientCarryingInvoices_isRefusedAndExplained() = runTest {
        val repository = FakeClientRepository(listOf(client()), invoiceCounts = mapOf(validSiret to 7L))
        val vm = viewModel(repository, testScheduler)
        advanceUntilIdle()

        vm.processIntent(ClientsIntent.DeleteClicked(client()))
        vm.processIntent(ClientsIntent.DeleteConfirmed)
        advanceUntilIdle()

        assertEquals(1, repository.clients.size) // la fiche reste
        val message = assertNotNull(vm.uiState.value.errorMessage)
        assertTrue(message.contains("7"), "Le message doit nommer le nombre de factures : $message")
    }

    @Test
    fun dismissingTheDeletionDialog_leavesTheClientUntouched() = runTest {
        val repository = FakeClientRepository(listOf(client()))
        val vm = viewModel(repository, testScheduler)
        advanceUntilIdle()

        vm.processIntent(ClientsIntent.DeleteClicked(client()))
        vm.processIntent(ClientsIntent.DeleteDismissed)
        advanceUntilIdle()

        assertNull(vm.uiState.value.pendingDeletion)
        assertEquals(1, repository.clients.size)
    }

    // ── Niveau 1 : Autocomplétion SIRET, Doublon harmonisé, Recherche & Auto-dismiss ──

    @Test
    fun siretChanged_with14Digits_triggersSireneLookup_andPrefillsCompanyName() = runTest {
        val mockSirene = com.ledgerhub.data.sirene.MockSireneLookupService(simulatedDelayMillis = 100L)
        val vm = ClientsViewModel(FakeClientRepository(), mockSirene, StandardTestDispatcher(testScheduler))
        advanceUntilIdle()

        vm.processIntent(ClientsIntent.AddClicked)
        val demoSiret = com.ledgerhub.data.sirene.MockSireneLookupService.DEMO_SIRET

        vm.processIntent(ClientsIntent.SiretChanged(demoSiret))

        // Loader actif pendant la résolution
        assertTrue(vm.uiState.value.form?.isSireneResolving == true)

        testScheduler.advanceTimeBy(150L)
        advanceUntilIdle()

        val form = assertNotNull(vm.uiState.value.form)
        assertFalse(form.isSireneResolving)
        assertTrue(form.nameAutoFilled)
        assertEquals(com.ledgerhub.data.sirene.MockSireneLookupService.DEFAULT_COMPANY_NAME, form.name)
    }

    @Test
    fun companyNameChanged_manuallyAfterAutofill_disablesAutoFilledFlag() = runTest {
        val mockSirene = com.ledgerhub.data.sirene.MockSireneLookupService(simulatedDelayMillis = 100L)
        val vm = ClientsViewModel(FakeClientRepository(), mockSirene, StandardTestDispatcher(testScheduler))
        advanceUntilIdle()

        vm.processIntent(ClientsIntent.AddClicked)
        vm.processIntent(ClientsIntent.SiretChanged(com.ledgerhub.data.sirene.MockSireneLookupService.DEMO_SIRET))
        testScheduler.advanceTimeBy(150L)
        advanceUntilIdle()

        assertTrue(vm.uiState.value.form?.nameAutoFilled == true)

        // Modification manuelle
        vm.processIntent(ClientsIntent.NameChanged("Mon Entreprise Personnalisée"))
        assertFalse(vm.uiState.value.form?.nameAutoFilled == true)
        assertEquals("Mon Entreprise Personnalisée", vm.uiState.value.form?.name)
    }

    @Test
    fun duplicateSiret_returnsHarmonizedErrorMessage() = runTest {
        val repository = FakeClientRepository(listOf(client()))
        val vm = viewModel(repository, testScheduler)
        advanceUntilIdle()

        vm.processIntent(ClientsIntent.AddClicked)
        fillForm(vm, "Autre Raison SAS", validSiret, "autre@client.fr")
        vm.processIntent(ClientsIntent.FormSubmitted)
        advanceUntilIdle()

        val form = assertNotNull(vm.uiState.value.form)
        assertEquals("Ce numéro SIRET est déjà associé à un client existant.", form.errors[ClientFormField.SIRET])
    }

    @Test
    fun searchQuery_filtersClientsByNameSiretAndEmail() = runTest {
        val m2i = Party("M2i Formation", "123456789", "12345678900012", "formation@m2i.fr")
        val testClient = Party("Test Solution SAS", "987654321", "98765432100099", "contact@test-solution.com")
        val vm = viewModel(FakeClientRepository(listOf(m2i, testClient)), testScheduler)
        advanceUntilIdle()

        assertEquals(2, vm.uiState.value.filteredClients.size)

        // Saisie de "test" : M2i Formation ne doit plus être affiché
        vm.onSearchQueryChange("test")
        val filtered = vm.uiState.value.filteredClients
        assertEquals(1, filtered.size)
        assertEquals("Test Solution SAS", filtered.first().name)
        assertFalse(filtered.any { it.name == "M2i Formation" })

        // Filtrage par SIRET avec chiffres
        vm.onSearchQueryChange("123 456")
        assertEquals(listOf("M2i Formation"), vm.uiState.value.filteredClients.map { it.name })

        // Filtrage par email
        vm.onSearchQueryChange("test-solution")
        assertEquals(listOf("Test Solution SAS"), vm.uiState.value.filteredClients.map { it.name })

        // Purge
        vm.onClearSearch()
        assertEquals(2, vm.uiState.value.filteredClients.size)
    }

    @Test
    fun searchQueryChanged_filtersClientsByNameSiretOrEmail() = runTest {
        val c1 = Party("Alpha SARL", "111111111", "11111111111111", "alpha@test.fr")
        val c2 = Party("Beta SAS", "222222222", "22222222222222", "contact@beta.com")
        val c3 = Party("Gamma EURL", "333333333", "33333333333333", "gamma@cabinet.fr")
        val vm = viewModel(FakeClientRepository(listOf(c1, c2, c3)), testScheduler)
        advanceUntilIdle()

        assertEquals(3, vm.uiState.value.filteredClients.size)

        // Filtre par nom
        vm.processIntent(ClientsIntent.SearchQueryChanged("beta"))
        assertEquals(listOf("Beta SAS"), vm.uiState.value.filteredClients.map { it.name })
        assertFalse(vm.uiState.value.isSearchEmpty)

        // Filtre par SIRET
        vm.processIntent(ClientsIntent.SearchQueryChanged("3333333"))
        assertEquals(listOf("Gamma EURL"), vm.uiState.value.filteredClients.map { it.name })

        // Filtre par email
        vm.processIntent(ClientsIntent.SearchQueryChanged("alpha@test"))
        assertEquals(listOf("Alpha SARL"), vm.uiState.value.filteredClients.map { it.name })

        // Aucun résultat
        vm.processIntent(ClientsIntent.SearchQueryChanged("inexistant"))
        assertTrue(vm.uiState.value.filteredClients.isEmpty())
        assertTrue(vm.uiState.value.isSearchEmpty)

        // Purge
        vm.processIntent(ClientsIntent.ClearSearch)
        assertEquals(3, vm.uiState.value.filteredClients.size)
        assertFalse(vm.uiState.value.isSearchEmpty)
    }

    @Test
    fun feedbackMessage_autoDismissesAfter3500ms() = runTest {
        val repository = FakeClientRepository(listOf(client()))
        val vm = viewModel(repository, testScheduler)
        advanceUntilIdle()

        // Suppression d'un client déclenchant un feedbackMessage
        vm.processIntent(ClientsIntent.DeleteClicked(client()))
        vm.processIntent(ClientsIntent.DeleteConfirmed)

        // Avant que le délai ne s'écoule, le message est présent
        testScheduler.runCurrent()
        assertEquals("Client supprimé", vm.uiState.value.feedbackMessage)

        // À 3 400 ms, le message est toujours présent
        testScheduler.advanceTimeBy(3400L)
        assertEquals("Client supprimé", vm.uiState.value.feedbackMessage)

        // À 3 500 ms (soit +100 ms), le message disparaît
        testScheduler.advanceTimeBy(100L)
        testScheduler.runCurrent()
        assertNull(vm.uiState.value.feedbackMessage)
    }

    @Test
    fun searchQuery_filtersInstantlyFromFirstCharacter() = runTest {
        val c1 = Party("M2i Formation", "111111111", "11111111111111", "contact@m2i.fr")
        val c2 = Party("Autre Entreprise", "222222222", "22222222222222", "autre@entreprise.fr")
        val vm = viewModel(FakeClientRepository(listOf(c1, c2)), testScheduler)
        advanceUntilIdle()

        assertEquals(2, vm.uiState.value.filteredClients.size)

        // Saisie d'un seul caractère "m" (insensible à la casse)
        vm.processIntent(ClientsIntent.SearchQueryChanged("m"))

        // Seul "M2i Formation" doit être conservé, "Autre Entreprise" est exclu
        val filtered = vm.uiState.value.filteredClients
        assertEquals(1, filtered.size)
        assertEquals("M2i Formation", filtered.single().name)
    }

    @Test
    fun dismissMessage_resetsErrorMessageAndFeedbackMessage() = runTest {
        val vm = viewModel(FakeClientRepository(), testScheduler)
        advanceUntilIdle()

        // Simuler un message d'erreur ou de feedback actif
        vm.processIntent(ClientsIntent.DeleteClicked(client()))
        // Injection d'une erreur via suppression ou déclenchement
        vm.processIntent(ClientsIntent.DismissMessage)

        assertNull(vm.uiState.value.errorMessage)
        assertNull(vm.uiState.value.feedbackMessage)
    }

    @Test
    fun searchQuery_isCaseInsensitive() = runTest {
        val c1 = Party("ORANGE", "380129866", "38012986648625", "test@orange.com")
        val c2 = Party("M2i Formation", "921883740", "92188374000026", "test@m2i.fr")
        val vm = viewModel(FakeClientRepository(listOf(c1, c2)), testScheduler)
        advanceUntilIdle()

        // Test avec "orange", "ORANGE", et "OrAnGe"
        listOf("orange", "ORANGE", "OrAnGe").forEach { query ->
            vm.processIntent(ClientsIntent.SearchQueryChanged(query))
            val filtered = vm.uiState.value.filteredClients
            assertEquals(1, filtered.size, "Échec pour query: $query")
            assertEquals("ORANGE", filtered.single().name)
        }
    }

    @Test
    fun searchQuery_alphanumeric_doesNotMatchSiretDigits() = runTest {
        val c1 = Party("M2i Formation", "921883740", "92188374000026", "test@m2i.fr")
        val c2 = Party("ORANGE", "380129866", "38012986648625", "test@orange.com")
        val c3 = Party("test1", "369085214", "36908521470963", "test1@test.com")
        val vm = viewModel(FakeClientRepository(listOf(c1, c2, c3)), testScheduler)
        advanceUntilIdle()

        assertEquals(3, vm.uiState.value.filteredClients.size)

        // Recherche alphanumérique "test1" : ne doit matcher que "test1" et ne PAS déclencher
        // la recherche du chiffre "1" dans les SIRETs de M2i Formation ou ORANGE
        vm.processIntent(ClientsIntent.SearchQueryChanged("test1"))

        val filtered = vm.uiState.value.filteredClients
        assertEquals(1, filtered.size)
        assertEquals("test1", filtered.single().name)
    }

    @Test
    fun searchQuery_prefixMatching_handlesWordsHyphensApostrophesAndExcludesMiddleSubstrings() = runTest {
        val c1 = Party("ORANGE", "380129866", "38012986648625", "test@orange.com")
        val c2 = Party("M2i Formation", "921883740", "92188374000026", "test@m2i.fr")
        val c3 = Party("Eco-Bat SAS", "111222333", "11122233300011", "contact@eco-bat.fr")
        val c4 = Party("L'Atelier d'Art", "444555666", "44455566600022", "info@latelier.fr")
        val vm = viewModel(FakeClientRepository(listOf(c1, c2, c3, c4)), testScheduler)
        advanceUntilIdle()

        // 1. "or" doit matcher "ORANGE" mais PAS "M2i Formation" (bien que Formation contienne "or")
        vm.processIntent(ClientsIntent.SearchQueryChanged("or"))
        val orResults = vm.uiState.value.filteredClients
        assertEquals(1, orResults.size)
        assertEquals("ORANGE", orResults.single().name)

        // 2. "bat" avec tiret doit trouver "Eco-Bat SAS"
        vm.processIntent(ClientsIntent.SearchQueryChanged("bat"))
        val batResults = vm.uiState.value.filteredClients
        assertEquals(1, batResults.size)
        assertEquals("Eco-Bat SAS", batResults.single().name)

        // 3. "atelier" avec apostrophe doit trouver "L'Atelier d'Art"
        vm.processIntent(ClientsIntent.SearchQueryChanged("atelier"))
        val atelierResults = vm.uiState.value.filteredClients
        assertEquals(1, atelierResults.size)
        assertEquals("L'Atelier d'Art", atelierResults.single().name)
    }
}
