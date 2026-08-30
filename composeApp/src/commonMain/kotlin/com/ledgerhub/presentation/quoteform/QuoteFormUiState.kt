package com.ledgerhub.presentation.quoteform

import com.ledgerhub.domain.invoice.Money
import com.ledgerhub.domain.invoice.Party
import com.ledgerhub.domain.quote.Quote
import com.ledgerhub.presentation.components.QuickClientDraft
import com.ledgerhub.presentation.invoiceform.SubmissionStatus

/** État immuable du formulaire — pattern UDF. Les champs sont stockés en texte brut (saisie utilisateur). */
data class QuoteFormUiState(
    val quoteNumber: String = "",
    val issueDate: String = "",
    val validityDate: String = "",
    val issuerName: String = "",
    val issuerSiren: String = "",
    val issuerSiret: String = "",
    val recipientName: String = "",
    val recipientSiren: String = "",
    val recipientSiret: String = "",
    // ── Sélecteur client dynamique (US-11) ────────────────────────────────────
    /**
     * Texte tapé dans le champ de recherche client. Distinct de [recipientName], qui reste la
     * valeur retenue pour le devis : les deux coïncident après une sélection, mais [clientQuery]
     * suit la frappe même quand elle ne correspond à aucune fiche.
     */
    val clientQuery: String = "",
    /** Fiches filtrées par [clientQuery] — le modèle client du projet est [Party]. */
    val clientSuggestions: List<Party> = emptyList(),
    val isClientDropdownExpanded: Boolean = false,
    val showQuickClientDialog: Boolean = false,
    /** Fiche retenue ; repasse à `null` dès que l'utilisateur modifie un champ client à la main. */
    val selectedClient: Party? = null,
    /**
     * `true` quand un annuaire client est branché. Sans annuaire, le champ raison sociale reste
     * une saisie libre : ni suggestion, ni création rapide (qui n'aurait nulle part où écrire).
     */
    val isClientDirectoryAvailable: Boolean = false,
    /** Brouillon de la modale de création rapide — `null` quand elle est fermée. */
    val quickClientDraft: QuickClientDraft? = null,
    val lines: List<QuoteLineFormState> = listOf(QuoteLineFormState()),
    val errors: Map<QuoteFormField, String> = emptyMap(),
    val totalHt: Money = Money.ZERO,
    val totalVat: Money = Money.ZERO,
    val totalTtc: Money = Money.ZERO,
    val submittedQuote: Quote? = null,
    val submissionStatus: SubmissionStatus = SubmissionStatus.Idle,
) {
    /** Verrouille tous les champs pendant la soumission — évite toute saisie concurrente. */
    val isFormEnabled: Boolean get() = submissionStatus != SubmissionStatus.Loading

    /** Un devis doit garder au moins une ligne — la suppression de la dernière est bloquée. */
    val canRemoveLines: Boolean get() = lines.size > 1 && isFormEnabled

    val isSubmitEnabled: Boolean
        get() = errors.isEmpty() && lines.all { it.errors.isEmpty() } && isFormEnabled

    /**
     * Le bouton vert « + Ajouter comme nouveau client » n'apparaît que si la saisie ne désigne
     * aucune fiche connue : une recherche vide n'est pas un client inconnu, et un client déjà
     * sélectionné n'a pas à être recréé.
     */
    val showAddNewClientButton: Boolean
        get() = isClientDirectoryAvailable &&
            isFormEnabled &&
            selectedClient == null &&
            clientQuery.isNotBlank() &&
            clientSuggestions.isEmpty()
}
