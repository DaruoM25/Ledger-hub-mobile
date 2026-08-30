package com.ledgerhub.presentation.clients

import com.ledgerhub.domain.client.ClientInUseException
import com.ledgerhub.domain.client.ClientRepository
import com.ledgerhub.domain.client.DuplicateClientException
import com.ledgerhub.domain.invoice.Party
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
    ) = ClientsViewModel(repository, StandardTestDispatcher(scheduler))

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
}
