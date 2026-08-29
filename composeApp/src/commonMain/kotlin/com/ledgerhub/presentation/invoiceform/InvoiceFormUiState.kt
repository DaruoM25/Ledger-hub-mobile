package com.ledgerhub.presentation.invoiceform

import com.ledgerhub.domain.i18n.ValidationErrorKey
import com.ledgerhub.domain.invoice.Invoice
import com.ledgerhub.domain.invoice.Money

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
    val clientEmail: String = "",
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
    val submittedInvoice: Invoice? = null,
    val submissionStatus: SubmissionStatus = SubmissionStatus.Idle,
) {
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
}
