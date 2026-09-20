package com.ledgerhub.presentation.clients

import com.ledgerhub.domain.invoice.Party

/** Champ du formulaire de fiche client, pour rattacher une erreur de validation. */
enum class ClientFormField {
    NAME,
    SIRET,
    EMAIL,
}

/**
 * Formulaire d'ajout ou d'édition d'une fiche client.
 *
 * [editedSiret] non nul = édition d'une fiche existante ; le SIRET est alors verrouillé, puisqu'il
 * est l'identité métier et la clé primaire. Changer de SIRET revient à créer un autre client.
 *
 * Comme le formulaire de facture (D-02), les erreurs sont **calculées en permanence** mais ne sont
 * **présentées** qu'une fois le champ saisi ou une validation tentée.
 */
data class ClientFormState(
    val editedSiret: String? = null,
    val name: String = "",
    val siret: String = "",
    val email: String = "",
    val errors: Map<ClientFormField, String> = emptyMap(),
    val touchedFields: Set<ClientFormField> = emptySet(),
    val saveAttempted: Boolean = false,
    val isSaving: Boolean = false,
    val isSireneResolving: Boolean = false,
    val nameAutoFilled: Boolean = false,
) {
    val isEditing: Boolean get() = editedSiret != null

    val isValid: Boolean get() = errors.isEmpty()

    val visibleErrors: Map<ClientFormField, String>
        get() = if (saveAttempted) errors else errors.filterKeys { it in touchedFields }
}

/** État de l'écran Clients — liste persistée + formulaire modal éventuellement ouvert + filtre temps réel. */
data class ClientsUiState(
    val isLoading: Boolean = true,
    val clients: List<Party> = emptyList(),
    val searchQuery: String = "",
    val filteredClients: List<Party> = filterClients(clients, searchQuery),
    val form: ClientFormState? = null,
    /** Fiche dont la suppression attend confirmation. */
    val pendingDeletion: Party? = null,
    val errorMessage: String? = null,
    val feedbackMessage: String? = null,
) {
    val isEmpty: Boolean get() = !isLoading && clients.isEmpty()

    val isSearchEmpty: Boolean get() = !isLoading && searchQuery.isNotBlank() && filteredClients.isEmpty() && clients.isNotEmpty()

    companion object {
        fun filterClients(allClients: List<Party>, query: String): List<Party> {
            val raw = query.trim()
            if (raw.isEmpty()) return allClients

            val normalizedQuery = raw.lowercase()
            val isNumericQuery = raw.all { it.isDigit() || it.isWhitespace() }
            val digitsOnly = raw.filter { it.isDigit() }

            return allClients.filter { client ->
                val nameWords = client.name.lowercase().split("[\\s\\-_']+".toRegex()).filter { it.isNotEmpty() }
                val matchesName = nameWords.any { it.startsWith(normalizedQuery) } ||
                        client.name.lowercase().startsWith(normalizedQuery)

                val emailParts = client.email.lowercase().split("@", ".").filter { it.isNotEmpty() }
                val matchesEmail = emailParts.any { it.startsWith(normalizedQuery) } ||
                        client.email.lowercase().startsWith(normalizedQuery)

                val matchesSiret = isNumericQuery && digitsOnly.isNotEmpty() && client.siret.startsWith(digitsOnly)
                matchesName || matchesEmail || matchesSiret
            }
        }
    }
}
