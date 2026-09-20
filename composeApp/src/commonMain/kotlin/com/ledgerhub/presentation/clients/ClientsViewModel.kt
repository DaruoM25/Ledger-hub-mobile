package com.ledgerhub.presentation.clients

import com.ledgerhub.data.sirene.MockSireneLookupService
import com.ledgerhub.domain.client.ClientInUseException
import com.ledgerhub.domain.client.ClientRepository
import com.ledgerhub.domain.client.DuplicateClientException
import com.ledgerhub.domain.invoice.FiscalValidation
import com.ledgerhub.domain.invoice.Party
import com.ledgerhub.domain.invoice.ValidationResult
import com.ledgerhub.domain.sirene.SireneLookupResult
import com.ledgerhub.domain.sirene.SireneLookupService
import com.ledgerhub.domain.sirene.SiretInput
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Intentions de l'écran Clients (UDF). */
sealed interface ClientsIntent {
    data object Load : ClientsIntent
    data object AddClicked : ClientsIntent
    data class EditClicked(val client: Party) : ClientsIntent
    data class DeleteClicked(val client: Party) : ClientsIntent
    data object DeleteConfirmed : ClientsIntent
    data object DeleteDismissed : ClientsIntent
    data class NameChanged(val value: String) : ClientsIntent
    data class SiretChanged(val value: String) : ClientsIntent
    data class EmailChanged(val value: String) : ClientsIntent
    data class SearchQueryChanged(val query: String) : ClientsIntent
    data object ClearSearch : ClientsIntent
    data object FormSubmitted : ClientsIntent
    data object FormDismissed : ClientsIntent
    data object FeedbackShown : ClientsIntent
    data object DismissMessage : ClientsIntent
}

/**
 * ViewModel de l'écran Clients — CRUD complet sur les fiches persistées, autocomplétion SIRENE
 * et filtrage temps réel en mémoire.
 *
 * Convention maison (cf. `InvoiceListViewModel`) : classe simple, [CoroutineScope] +
 * [MutableStateFlow], aucun `androidx.lifecycle` dans commonMain.
 *
 * La validation est intégralement déléguée à [FiscalValidation] : le SIRET à 14 chiffres, la
 * raison sociale et l'adresse de contact suivent les mêmes règles que le formulaire de facture,
 * jamais une seconde implémentation.
 */
class ClientsViewModel(
    private val repository: ClientRepository,
    private val sireneLookupService: SireneLookupService = MockSireneLookupService(),
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    private var lookupJob: Job? = null
    private var feedbackDismissJob: Job? = null

    private val _uiState = MutableStateFlow(ClientsUiState())
    val uiState: StateFlow<ClientsUiState> = _uiState.asStateFlow()

    init {
        processIntent(ClientsIntent.Load)
    }

    fun processIntent(intent: ClientsIntent) {
        when (intent) {
            ClientsIntent.Load -> load()

            ClientsIntent.AddClicked ->
                _uiState.update { it.copy(form = revalidate(ClientFormState())) }

            is ClientsIntent.EditClicked -> _uiState.update {
                // Le SIRET identifie la fiche : en édition il est pré-rempli et verrouillé côté UI.
                it.copy(
                    form = revalidate(
                        ClientFormState(
                            editedSiret = intent.client.siret,
                            name = intent.client.name,
                            siret = intent.client.siret,
                            email = intent.client.email,
                        ),
                    ),
                )
            }

            is ClientsIntent.DeleteClicked ->
                _uiState.update { it.copy(pendingDeletion = intent.client) }

            ClientsIntent.DeleteDismissed ->
                _uiState.update { it.copy(pendingDeletion = null) }

            ClientsIntent.DeleteConfirmed -> confirmDeletion()

            is ClientsIntent.NameChanged ->
                updateForm(ClientFormField.NAME) { it.copy(name = intent.value, nameAutoFilled = false) }

            is ClientsIntent.SiretChanged -> onSiretChanged(intent.value)

            is ClientsIntent.EmailChanged ->
                updateForm(ClientFormField.EMAIL) { it.copy(email = intent.value) }

            is ClientsIntent.SearchQueryChanged -> {
                _uiState.update { current ->
                    current.copy(
                        searchQuery = intent.query,
                        filteredClients = filterClients(current.clients, intent.query),
                    )
                }
            }

            ClientsIntent.ClearSearch -> {
                _uiState.update { current ->
                    current.copy(
                        searchQuery = "",
                        filteredClients = current.clients,
                    )
                }
            }

            ClientsIntent.FormSubmitted -> submitForm()

            ClientsIntent.FormDismissed -> {
                lookupJob?.cancel()
                _uiState.update { it.copy(form = null) }
            }

            ClientsIntent.FeedbackShown, ClientsIntent.DismissMessage -> {
                feedbackDismissJob?.cancel()
                _uiState.update { it.copy(feedbackMessage = null, errorMessage = null) }
            }
        }
    }

    fun onSearchQueryChange(query: String) {
        processIntent(ClientsIntent.SearchQueryChanged(query))
    }

    fun onClearSearch() {
        processIntent(ClientsIntent.ClearSearch)
    }

    private fun matchesClient(client: Party, query: String): Boolean {
        val raw = query.trim()
        if (raw.isEmpty()) return true

        val normalizedQuery = raw.lowercase()

        // Recherche par préfixe sur les mots du nom (espaces, tirets, underscores, apostrophes)
        val nameWords = client.name.lowercase().split("[\\s\\-_']+".toRegex()).filter { it.isNotEmpty() }
        val matchesName = nameWords.any { it.startsWith(normalizedQuery) } ||
                client.name.lowercase().startsWith(normalizedQuery)

        // Recherche email (préfixe du compte ou du domaine)
        val emailParts = client.email.lowercase().split("@", ".").filter { it.isNotEmpty() }
        val matchesEmail = emailParts.any { it.startsWith(normalizedQuery) } ||
                client.email.lowercase().startsWith(normalizedQuery)

        // Recherche SIRET (uniquement numérique et par préfixe)
        val isNumericQuery = raw.all { it.isDigit() || it.isWhitespace() }
        val digitsOnly = raw.filter { it.isDigit() }
        val matchesSiret = isNumericQuery && digitsOnly.isNotEmpty() && client.siret.startsWith(digitsOnly)

        return matchesName || matchesEmail || matchesSiret
    }

    private fun filterClients(allClients: List<Party>, query: String): List<Party> {
        return allClients.filter { matchesClient(it, query) }
    }

    private fun load() {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        scope.launch {
            val result = repository.fetchClients()
            _uiState.update { current ->
                result.fold(
                    onSuccess = { clients ->
                        current.copy(
                            isLoading = false,
                            clients = clients,
                            filteredClients = filterClients(clients, current.searchQuery),
                        )
                    },
                    onFailure = { throwable ->
                        current.copy(
                            isLoading = false,
                            errorMessage = throwable.message ?: "Chargement des clients impossible",
                        )
                    },
                )
            }
        }
    }

    private fun onSiretChanged(value: String) {
        val form = _uiState.value.form ?: return
        if (form.isSaving) return

        val digits = SiretInput.sanitize(value)
        val isComplete = digits.length == SiretInput.LENGTH

        lookupJob?.cancel()

        // Mise à jour de l'état du formulaire
        _uiState.update { current ->
            val curForm = current.form ?: return@update current
            val updated = curForm.copy(
                siret = value,
                isSireneResolving = isComplete && !curForm.isEditing,
                name = if (!isComplete && curForm.nameAutoFilled) "" else curForm.name,
                nameAutoFilled = curForm.nameAutoFilled && isComplete,
                touchedFields = curForm.touchedFields + ClientFormField.SIRET,
            )
            current.copy(form = revalidate(updated))
        }

        if (!isComplete || form.isEditing) return

        lookupJob = scope.launch {
            val result = try {
                Result.success(sireneLookupService.lookup(digits))
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                Result.failure(error)
            }

            _uiState.update { current ->
                val currentForm = current.form ?: return@update current
                if (SiretInput.sanitize(currentForm.siret) != digits) return@update current

                val resolvedCompany = result.getOrNull() as? SireneLookupResult.Verified
                val updatedForm = if (resolvedCompany != null) {
                    val companyName = resolvedCompany.company.companyName
                    currentForm.copy(
                        name = companyName,
                        isSireneResolving = false,
                        nameAutoFilled = true,
                    )
                } else {
                    currentForm.copy(isSireneResolving = false)
                }
                val revalidated = revalidate(updatedForm)
                current.copy(
                    form = if (currentForm.saveAttempted) {
                        revalidated.copy(errors = currentForm.errors, saveAttempted = true)
                    } else {
                        revalidated
                    },
                )
            }
        }
    }

    private fun updateForm(field: ClientFormField, change: (ClientFormState) -> ClientFormState) {
        _uiState.update { current ->
            val form = current.form ?: return@update current
            if (form.isSaving) return@update current
            current.copy(
                form = revalidate(change(form).copy(touchedFields = form.touchedFields + field)),
            )
        }
    }

    /** Recalcule la totalité des erreurs — l'affichage, lui, reste filtré par [ClientFormState.visibleErrors]. */
    private fun revalidate(form: ClientFormState): ClientFormState {
        val errors = mutableMapOf<ClientFormField, String>()
        (FiscalValidation.validateCompanyName(form.name) as? ValidationResult.Invalid)?.let {
            errors[ClientFormField.NAME] = it.reason
        }
        (FiscalValidation.validateSiret(form.siret) as? ValidationResult.Invalid)?.let {
            errors[ClientFormField.SIRET] = it.reason
        }
        (FiscalValidation.validateEmail(form.email) as? ValidationResult.Invalid)?.let {
            errors[ClientFormField.EMAIL] = it.reason
        }
        return form.copy(errors = errors)
    }

    private fun submitForm() {
        val form = _uiState.value.form ?: return
        if (form.isSaving) return

        lookupJob?.cancel()

        val revalidated = revalidate(form).copy(saveAttempted = true)
        if (!revalidated.isValid) {
            _uiState.update { it.copy(form = revalidated) }
            return
        }

        _uiState.update { it.copy(form = revalidated.copy(isSaving = true)) }
        val client = Party(
            name = revalidated.name.trim(),
            // Règle INSEE : le SIREN est le préfixe à 9 chiffres du SIRET, déjà validé.
            siren = revalidated.siret.take(9),
            siret = revalidated.siret,
            email = revalidated.email.trim(),
        )

        scope.launch {
            val result = if (revalidated.isEditing) {
                repository.updateClient(client)
            } else {
                repository.createClient(client)
            }
            result.fold(
                onSuccess = {
                    showTimedFeedback(successMessageFor(revalidated.isEditing))
                    _uiState.update { it.copy(form = null) }
                    load()
                },
                onFailure = { throwable ->
                    // Un SIRET déjà pris est une erreur de saisie : elle se rattache au champ.
                    val duplicate = throwable as? DuplicateClientException
                    _uiState.update { current ->
                        current.copy(
                            form = revalidated.copy(
                                isSaving = false,
                                errors = if (duplicate != null) {
                                    revalidated.errors + (ClientFormField.SIRET to DUPLICATE_SIRET_MESSAGE)
                                } else {
                                    revalidated.errors
                                },
                            ),
                            errorMessage = if (duplicate == null) throwable.message else null,
                        )
                    }
                },
            )
        }
    }

    private fun confirmDeletion() {
        val target = _uiState.value.pendingDeletion ?: return
        scope.launch {
            repository.deleteClient(target.siret).fold(
                onSuccess = {
                    showTimedFeedback(DELETED_MESSAGE)
                    _uiState.update { it.copy(pendingDeletion = null) }
                    load()
                },
                onFailure = { throwable ->
                    val inUse = throwable as? ClientInUseException
                    _uiState.update {
                        it.copy(
                            pendingDeletion = null,
                            errorMessage = inUse?.let { e -> clientInUseMessage(e.invoiceCount) }
                                ?: throwable.message,
                        )
                    }
                },
            )
        }
    }

    private fun showTimedFeedback(message: String) {
        feedbackDismissJob?.cancel()
        _uiState.update { it.copy(feedbackMessage = message) }
        feedbackDismissJob = scope.launch {
            delay(FEEDBACK_AUTO_DISMISS_DELAY_MS)
            _uiState.update { current ->
                if (current.feedbackMessage == message) current.copy(feedbackMessage = null) else current
            }
        }
    }

    private fun successMessageFor(editing: Boolean) = if (editing) UPDATED_MESSAGE else CREATED_MESSAGE

    fun onCleared() {
        lookupJob?.cancel()
        feedbackDismissJob?.cancel()
        scope.cancel()
    }

    companion object {
        const val CREATED_MESSAGE = "Client ajouté"
        const val UPDATED_MESSAGE = "Client mis à jour"
        const val DELETED_MESSAGE = "Client supprimé"
        const val DUPLICATE_SIRET_MESSAGE = "Ce numéro SIRET est déjà associé à un client existant."
        const val FEEDBACK_AUTO_DISMISS_DELAY_MS = 3500L

        fun clientInUseMessage(count: Long) =
            "Suppression impossible : ce client figure sur $count facture(s) émise(s)"
    }
}
