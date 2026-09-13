package com.ledgerhub.presentation.invoiceform

import com.ledgerhub.domain.compliance.ComplianceReport
import com.ledgerhub.domain.i18n.ValidationErrorKey
import com.ledgerhub.domain.invoice.Invoice
import com.ledgerhub.domain.invoice.Money
import com.ledgerhub.domain.invoice.Party
import com.ledgerhub.presentation.components.QuickClientDraft

/**
 * État immuable du formulaire — pattern UDF. Les champs sont stockés en texte brut (saisie
 * utilisateur). Parité Web : une seule section « Informations Client » (le destinataire) ;
 * l'émetteur est l'identité fixe du cabinet ([CabinetIdentity]).
 */
data class InvoiceFormUiState(
    val invoiceNumber: String = "",
    val issueDate: String = "",
    val dueDate: String = "",
    val clientName: String = "",
    val clientSiret: String = "",
    val clientSiren: String = "",
    val clientEmail: String = "",
    // ── Réforme fiscale 2026 (US-27) ──────────────────────────────────────────
    val transactionMode: com.ledgerhub.domain.invoice.TransactionMode = com.ledgerhub.domain.invoice.TransactionMode.E_INVOICING,
    val natureOperation: com.ledgerhub.domain.invoice.NatureOperation = com.ledgerhub.domain.invoice.NatureOperation.PRESTATION_SERVICES,
    val optionTvaDebit: Boolean = false,
    val hasDifferentDeliveryAddress: Boolean = false,
    val deliveryStreet: String = "",
    val deliveryZip: String = "",
    val deliveryCity: String = "",
    val deliveryCountry: String = "France",
    // ── Sélecteur client dynamique (US-11) ────────────────────────────────────
    /**
     * Texte tapé dans le champ de recherche client. Distinct de [clientName], qui reste la valeur
     * retenue pour la facture : les deux coïncident après une sélection, mais [clientQuery] suit
     * la frappe même quand elle ne correspond à aucune fiche.
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
    /**
     * Émetteur porté par la facture. Alimenté par les paramètres fiscaux enregistrés
     * ([com.ledgerhub.domain.settings.TaxSettings]) ; [CabinetIdentity] n'en est plus que le repli
     * tant que rien n'a été enregistré.
     */
    val issuer: Party = CabinetIdentity.party,
    val lines: List<InvoiceLineFormState> = listOf(InvoiceLineFormState()),
    val errors: Map<InvoiceFormField, ValidationErrorKey> = emptyMap(),
    /** Champs déjà saisis par l'utilisateur — conditionne l'affichage des erreurs, pas leur calcul. */
    val touchedFields: Set<InvoiceFormField> = emptySet(),
    /** Passe à `true` à la première tentative d'émission : toutes les erreurs sont alors révélées. */
    val submitAttempted: Boolean = false,
    val totalHt: Money = Money.ZERO,
    val totalVat: Money = Money.ZERO,
    val totalTtc: Money = Money.ZERO,
    /** Toggle « Générer au format légal Factur-X » — activé par défaut (conformité 2026). */
    val generateFacturX: Boolean = true,
    /**
     * Pénalités de retard légales B2B (US-16) — activées par défaut, comme [generateFacturX] :
     * l'article L.441-10 du Code de commerce rend la mention obligatoire entre professionnels,
     * et une omission par défaut produirait silencieusement des factures non conformes. Le
     * décocher reste possible au cas par cas (facture à un particulier).
     */
    val applyB2bPenalties: Boolean = true,
    /** Identifiant du devis d'origine en cas de conversion — piste d'audit fiscale (PAF CGI art. 289-VII-1°). */
    val sourceQuoteId: String? = null,
    val submittedInvoice: Invoice? = null,
    val submissionStatus: SubmissionStatus = SubmissionStatus.Idle,
    /**
     * Rapport du dernier audit de conformité (US-24), ou `null` si aucun n'a été demandé — ou si
     * la facture a changé depuis.
     *
     * L'invalidation à la frappe n'est pas une précaution : un rapport périmé présenté comme
     * actuel ferait émettre une facture sur la foi d'un contrôle qui ne porte plus sur elle. Elle
     * a lieu dans `revalidate()`, sur le chemin que toute modification emprunte déjà.
     */
    val complianceReport: ComplianceReport? = null,
    // ── Mode Dégradé & Continuité Économique (US-29) ──────────────────────────
    val networkState: com.ledgerhub.domain.degraded.DegradedModeNetworkState = com.ledgerhub.domain.degraded.DegradedModeNetworkState.OPERATIONAL,
    val degradedChannel: com.ledgerhub.domain.degraded.DegradedChannel = com.ledgerhub.domain.degraded.DegradedChannel.PDF_SIMPLE,
) {
    /** Indicateur dérivé : mode e-Reporting actif (B2C / International). */
    val isEReporting: Boolean get() = transactionMode == com.ledgerhub.domain.invoice.TransactionMode.E_REPORTING

    /**
     * Une écriture est en cours. Propriété **dérivée** de [submissionStatus] et non champ stocké :
     * une seule source de vérité, impossible à désynchroniser de l'état réel de la soumission.
     */
    val isSubmitting: Boolean get() = submissionStatus == SubmissionStatus.Loading

    /** Verrouille tous les champs pendant la soumission — évite toute saisie concurrente. */
    val isFormEnabled: Boolean get() = !isSubmitting

    /** Une facture doit garder au moins une ligne — la suppression de la dernière est bloquée. */
    val canRemoveLines: Boolean get() = lines.size > 1 && isFormEnabled

    /**
     * Le formulaire est complet et valide — l'écriture peut aboutir. Les boutons d'action ne
     * s'appuient **pas** dessus : ils restent cliquables tant qu'aucune écriture n'est en cours,
     * de sorte qu'un appui sur un formulaire incomplet révèle toutes les erreurs plutôt que de
     * laisser l'utilisateur devant un bouton grisé sans explication (voir [submitAttempted]).
     */
    val isSubmitEnabled: Boolean
        get() = errors.isEmpty() && lines.all { it.errors.isEmpty() } && isFormEnabled

    /**
     * Erreurs effectivement présentées à l'écran. La validation, elle, tourne en permanence sur
     * la totalité des champs (voir [errors]) : un formulaire vierge reste donc non soumettable,
     * mais s'affiche neutre tant que l'utilisateur n'a rien saisi ni tenté d'émettre.
     */
    val visibleErrors: Map<InvoiceFormField, ValidationErrorKey>
        get() = if (submitAttempted) errors else errors.filterKeys { it in touchedFields }

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
