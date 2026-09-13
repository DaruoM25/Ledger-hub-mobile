package com.ledgerhub.presentation.invoiceform

import com.ledgerhub.domain.invoice.InvoiceStatus
import com.ledgerhub.domain.invoice.Party
import com.ledgerhub.domain.invoice.VatRate

sealed interface InvoiceFormIntent {
    // ── Détails de la facture ──────────────────────────────────────────────────
    data class InvoiceNumberChanged(val value: String) : InvoiceFormIntent
    data class IssueDateChanged(val value: String) : InvoiceFormIntent
    data class DueDateChanged(val value: String) : InvoiceFormIntent

    // ── Informations client ───────────────────────────────────────────────────
    /**
     * Saisie libre de la raison sociale — équivaut désormais à [OnClientQueryChanged] : depuis
     * US-11 le champ est un sélecteur, la frappe filtre les fiches connues.
     */
    data class ClientNameChanged(val value: String) : InvoiceFormIntent
    data class ClientSiretChanged(val value: String) : InvoiceFormIntent
    data class ClientEmailChanged(val value: String) : InvoiceFormIntent

    // ── Sélecteur client dynamique (US-11) ────────────────────────────────────
    /** Frappe dans le champ de recherche : refiltre les fiches et ouvre la liste. */
    data class OnClientQueryChanged(val value: String) : InvoiceFormIntent

    /** Une suggestion est retenue : raison sociale, SIRET et email sont repris de la fiche. */
    data class OnClientSelected(val client: Party) : InvoiceFormIntent

    /** Ouvre la modale de création rapide, pré-remplie avec la saisie courante. */
    data object OnOpenQuickClientDialog : InvoiceFormIntent

    /** Ferme la modale sans rien persister. */
    data object OnDismissQuickClientDialog : InvoiceFormIntent

    /** Saisie dans la modale — les valeurs restent brutes, la validation a lieu à l'enregistrement. */
    data class OnQuickClientFieldChanged(
        val name: String,
        val siret: String,
        val email: String,
    ) : InvoiceFormIntent

    /**
     * Enregistre la fiche puis la sélectionne immédiatement dans la facture courante. Refuse et
     * affiche les erreurs si le SIRET (14 chiffres + clé de Luhn), le nom ou l'email sont invalides.
     */
    data class OnSaveQuickClient(
        val name: String,
        val siret: String,
        val email: String,
    ) : InvoiceFormIntent

    /** Bascule « Générer au format légal Factur-X ». */
    data class ToggleFacturX(val enabled: Boolean) : InvoiceFormIntent

    // ── Réforme fiscale 2026 (US-27) ──────────────────────────────────────────
    /** Bascule du mode de transaction (B2B France vs e-Reporting B2C/Intl). */
    data class TransactionModeChanged(val mode: com.ledgerhub.domain.invoice.TransactionMode) : InvoiceFormIntent
    data class ClientSirenChanged(val value: String) : InvoiceFormIntent
    data class NatureOperationChanged(val value: com.ledgerhub.domain.invoice.NatureOperation) : InvoiceFormIntent
    data class ToggleOptionTvaDebit(val enabled: Boolean) : InvoiceFormIntent
    data class ToggleDifferentDeliveryAddress(val enabled: Boolean) : InvoiceFormIntent
    data class DeliveryStreetChanged(val value: String) : InvoiceFormIntent
    data class DeliveryZipChanged(val value: String) : InvoiceFormIntent
    data class DeliveryCityChanged(val value: String) : InvoiceFormIntent
    data class DeliveryCountryChanged(val value: String) : InvoiceFormIntent

    /**
     * Bascule « Appliquer les pénalités de retard légales (B2B) » (US-16). Commande le pied de
     * page de la facture : mention de l'article L.441-10 quand elle est active, formule de
     * courtoisie sinon. Aucun impact sur les montants — c'est une mention, pas un calcul.
     */
    data class ToggleB2bPenalties(val enabled: Boolean) : InvoiceFormIntent

    /**
     * Lance l'audit de conformité Factur-X 2026 (US-24) sur la facture en cours de saisie.
     *
     * À la demande, et non en continu : un contrôle réglementaire est un geste que l'utilisateur
     * pose avant d'émettre, pas un commentaire permanent sur sa frappe. Le rapport produit est
     * d'ailleurs invalidé à la modification suivante — voir `InvoiceFormUiState.complianceReport`.
     */
    data object ComplianceScanRequested : InvoiceFormIntent

    /** Ajoute une ligne de prestation vierge en fin de liste. */
    data object AddLine : InvoiceFormIntent

    /** Supprime la ligne à [index] — sans effet s'il ne reste qu'une ligne ou si le formulaire est verrouillé. */
    data class RemoveLine(val index: Int) : InvoiceFormIntent

    /**
     * Met à jour les champs de la ligne à [index]. Les valeurs restent en texte brut : la
     * validation/parsing se fait lors de la revalidation, pas à la frontière de l'intention.
     */
    data class UpdateLine(
        val index: Int,
        val label: String,
        val quantity: String,
        val unitPriceHt: String,
        val vatRate: VatRate,
    ) : InvoiceFormIntent

    /**
     * Persiste la facture en **brouillon** ([InvoiceStatus.DRAFT]) : elle reste modifiable et
     * supprimable. Comme [ValidateAndIssue], l'écriture n'a lieu que si le formulaire est valide ;
     * sinon la tentative révèle simplement toutes les erreurs.
     */
    data object SaveDraft : InvoiceFormIntent

    /**
     * Valide et émet la facture ([InvoiceStatus.DEPOSITED]) : elle devient immuable au sens fiscal
     * (plus de modification ni de suppression, annulation par avoir uniquement — voir
     * [Invoice.isEditable][com.ledgerhub.domain.invoice.Invoice.isEditable]).
     */
    data object ValidateAndIssue : InvoiceFormIntent

    // ── Mode Dégradé & Continuité Économique (US-29) ──────────────────────────
    data class NetworkStateChanged(val state: com.ledgerhub.domain.degraded.DegradedModeNetworkState) : InvoiceFormIntent
    data class DegradedChannelChanged(val channel: com.ledgerhub.domain.degraded.DegradedChannel) : InvoiceFormIntent
    data object SubmitDegraded : InvoiceFormIntent
}
