package com.ledgerhub.presentation.clients

import com.ledgerhub.domain.client.ClientInUseException
import com.ledgerhub.domain.client.ClientRepository
import com.ledgerhub.domain.client.DuplicateClientException
import com.ledgerhub.domain.invoice.FiscalValidation
import com.ledgerhub.domain.invoice.Party
import com.ledgerhub.domain.invoice.ValidationResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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
    data object FormSubmitted : ClientsIntent
    data object FormDismissed : ClientsIntent
    data object FeedbackShown : ClientsIntent
}

/**
 * ViewModel de l'écran Clients — CRUD complet sur les fiches persistées.
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
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

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
                updateForm(ClientFormField.NAME) { it.copy(name = intent.value) }

            is ClientsIntent.SiretChanged ->
                updateForm(ClientFormField.SIRET) { it.copy(siret = intent.value) }

            is ClientsIntent.EmailChanged ->
                updateForm(ClientFormField.EMAIL) { it.copy(email = intent.value) }

            ClientsIntent.FormSubmitted -> submitForm()

            ClientsIntent.FormDismissed ->
                _uiState.update { it.copy(form = null) }

            ClientsIntent.FeedbackShown ->
                _uiState.update { it.copy(feedbackMessage = null, errorMessage = null) }
        }
    }

    private fun load() {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        scope.launch {
            val result = repository.fetchClients()
            _uiState.update { current ->
                result.fold(
                    onSuccess = { clients -> current.copy(isLoading = false, clients = clients) },
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
                    _uiState.update {
                        it.copy(form = null, feedbackMessage = successMessageFor(revalidated.isEditing))
                    }
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
                    _uiState.update { it.copy(pendingDeletion = null, feedbackMessage = DELETED_MESSAGE) }
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

    private fun successMessageFor(editing: Boolean) = if (editing) UPDATED_MESSAGE else CREATED_MESSAGE

    fun onCleared() = scope.cancel()

    private companion object {
        const val CREATED_MESSAGE = "Client ajouté"
        const val UPDATED_MESSAGE = "Client mis à jour"
        const val DELETED_MESSAGE = "Client supprimé"
        const val DUPLICATE_SIRET_MESSAGE = "Un client porte déjà ce SIRET"
        fun clientInUseMessage(count: Long) =
            "Suppression impossible : ce client figure sur $count facture(s) émise(s)"
    }
}
