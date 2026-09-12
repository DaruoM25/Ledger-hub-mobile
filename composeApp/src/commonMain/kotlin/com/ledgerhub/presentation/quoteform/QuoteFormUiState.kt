package com.ledgerhub.presentation.quoteform

import com.ledgerhub.domain.invoice.Money
import com.ledgerhub.domain.invoice.Party
import com.ledgerhub.domain.quote.Quote
import com.ledgerhub.presentation.components.QuickClientDraft
import com.ledgerhub.presentation.invoiceform.CabinetIdentity
import com.ledgerhub.presentation.invoiceform.SubmissionStatus

/** État immuable du formulaire de devis — pattern UDF. */
data class QuoteFormUiState(
    val quoteNumber: String = "",
    val issueDate: String = "",
    val validityDate: String = "",
    val recipientName: String = "",
    val recipientSiren: String = "",
    val recipientSiret: String = "",
    val recipientEmail: String = "",
    /** Émetteur du devis, issu du cabinet/paramètres fiscaux. */
    val issuer: Party = CabinetIdentity.party,
    // ── Sélecteur client dynamique (US-11) ────────────────────────────────────
    val clientQuery: String = "",
    val clientSuggestions: List<Party> = emptyList(),
    val isClientDropdownExpanded: Boolean = false,
    val showQuickClientDialog: Boolean = false,
    val selectedClient: Party? = null,
    val isClientDirectoryAvailable: Boolean = false,
    val quickClientDraft: QuickClientDraft? = null,
    val lines: List<QuoteLineFormState> = listOf(QuoteLineFormState()),
    val errors: Map<QuoteFormField, String> = emptyMap(),
    /** Champs déjà modifiés par l'utilisateur. */
    val touchedFields: Set<QuoteFormField> = emptySet(),
    /** Passe à `true` à la première tentative d'émission pour révéler toutes les erreurs. */
    val submitAttempted: Boolean = false,
    val totalHt: Money = Money.ZERO,
    val totalVat: Money = Money.ZERO,
    val totalTtc: Money = Money.ZERO,
    val submittedQuote: Quote? = null,
    val submissionStatus: SubmissionStatus = SubmissionStatus.Idle,
) {
    /** Rétrocompatibilité : délégation vers [issuer]. */
    val issuerName: String get() = issuer.name
    val issuerSiren: String get() = issuer.siren
    val issuerSiret: String get() = issuer.siret

    /** Une écriture est en cours. */
    val isSubmitting: Boolean get() = submissionStatus == SubmissionStatus.Loading

    /** Verrouille tous les champs pendant la soumission — évite toute saisie concurrente. */
    val isFormEnabled: Boolean get() = !isSubmitting

    /** Un devis doit garder au moins une ligne — la suppression de la dernière est bloquée. */
    val canRemoveLines: Boolean get() = lines.size > 1 && isFormEnabled

    val isSubmitEnabled: Boolean
        get() = errors.isEmpty() && lines.all { it.errors.isEmpty() } && isFormEnabled

    /** Erreurs visibles dans l'interface (progressive validation). */
    val visibleErrors: Map<QuoteFormField, String>
        get() = if (submitAttempted) errors else errors.filterKeys { it in touchedFields }

    /**
     * Le bouton vert « + Ajouter comme nouveau client » n'apparaît que si la saisie ne désigne
     * aucune fiche connue.
     */
    val showAddNewClientButton: Boolean
        get() = isClientDirectoryAvailable &&
            isFormEnabled &&
            selectedClient == null &&
            clientQuery.isNotBlank() &&
            clientSuggestions.isEmpty()
}

