package com.ledgerhub.presentation.quoteform

import com.ledgerhub.domain.invoice.Party
import com.ledgerhub.domain.invoice.VatRate

sealed interface QuoteFormIntent {
    data class QuoteNumberChanged(val value: String) : QuoteFormIntent
    data class IssueDateChanged(val value: String) : QuoteFormIntent
    data class ValidityDateChanged(val value: String) : QuoteFormIntent
    data class IssuerNameChanged(val value: String) : QuoteFormIntent
    data class IssuerSirenChanged(val value: String) : QuoteFormIntent
    data class IssuerSiretChanged(val value: String) : QuoteFormIntent
    /**
     * Saisie libre de la raison sociale du destinataire — équivaut désormais à
     * [OnClientQueryChanged] : depuis US-11 le champ est un sélecteur, la frappe filtre les
     * fiches connues.
     */
    data class RecipientNameChanged(val value: String) : QuoteFormIntent
    data class RecipientSirenChanged(val value: String) : QuoteFormIntent
    data class RecipientSiretChanged(val value: String) : QuoteFormIntent

    // ── Sélecteur client dynamique (US-11) ────────────────────────────────────
    /** Frappe dans le champ de recherche : refiltre les fiches et ouvre la liste. */
    data class OnClientQueryChanged(val value: String) : QuoteFormIntent

    /**
     * Une suggestion est retenue : raison sociale, SIREN et SIRET du destinataire sont repris de
     * la fiche. Le devis ne porte pas d'email — la fiche en garde un, il n'est simplement pas
     * recopié faute de champ où l'écrire.
     */
    data class OnClientSelected(val client: Party) : QuoteFormIntent

    /** Ouvre la modale de création rapide, pré-remplie avec la saisie courante. */
    data object OnOpenQuickClientDialog : QuoteFormIntent

    /** Ferme la modale sans rien persister. */
    data object OnDismissQuickClientDialog : QuoteFormIntent

    /** Saisie dans la modale — les valeurs restent brutes, la validation a lieu à l'enregistrement. */
    data class OnQuickClientFieldChanged(
        val name: String,
        val siret: String,
        val email: String,
    ) : QuoteFormIntent

    /**
     * Enregistre la fiche puis la sélectionne immédiatement dans le devis courant. Refuse et
     * affiche les erreurs si le SIRET (14 chiffres + clé de Luhn), le nom ou l'email sont invalides.
     */
    data class OnSaveQuickClient(
        val name: String,
        val siret: String,
        val email: String,
    ) : QuoteFormIntent

    /** Ajoute une ligne de devis vierge en fin de liste. */
    data object AddLine : QuoteFormIntent

    /** Supprime la ligne à [index] — sans effet s'il ne reste qu'une ligne ou si le formulaire est verrouillé. */
    data class RemoveLine(val index: Int) : QuoteFormIntent

    /**
     * Met à jour les champs de la ligne à [index]. Les valeurs restent en texte brut (comme les
     * autres champs du formulaire) pour permettre une saisie intermédiaire invalide sans perte
     * de caractères — la validation/parsing se fait lors de la revalidation, pas à la frontière
     * de l'intention.
     */
    data class UpdateLine(
        val index: Int,
        val label: String,
        val quantity: String,
        val unitPriceHt: String,
        val vatRate: VatRate,
    ) : QuoteFormIntent

    data object Submit : QuoteFormIntent
}
