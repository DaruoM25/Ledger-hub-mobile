package com.ledgerhub.domain.compliance

import com.ledgerhub.domain.i18n.StringKey
import com.ledgerhub.domain.invoice.InvoiceLine
import com.ledgerhub.domain.invoice.Money
import com.ledgerhub.domain.invoice.Party

/**
 * Gravité d'un constat d'audit (US-24).
 *
 * L'ordre de déclaration **est** l'ordre de gravité : `maxOf` sur une liste de constats donne donc
 * le pire d'entre eux, sans table de correspondance à tenir à jour à côté.
 *
 * Trois niveaux et non deux : le cahier des charges demande « avertissement ambre **ou** erreur
 * rouge », donc la gravité est une donnée de domaine — la couleur n'en est que le rendu.
 */
enum class ComplianceStatus {
    /** Exigence satisfaite. */
    PASSED,

    /** Écart admissible : la facture reste émettable, mais l'utilisateur doit savoir. */
    WARNING,

    /** Écart bloquant au regard de la norme 2026. */
    FAILED,
    ;

    val isBlocking: Boolean get() = this == FAILED
}

/**
 * Les quatre contrôles imposés par le cahier des charges US-24.
 *
 * Chaque contrôle porte sa clé de libellé : l'écran affiche un intitulé, il n'en choisit pas la
 * formulation. Le tag de test correspondant est résolu côté présentation par un `when` exhaustif,
 * de sorte qu'un cinquième contrôle ne compilera pas sans déclarer le sien.
 */
enum class ComplianceCheck(val titleKey: StringKey) {
    SIRET(StringKey.COMPLIANCE_CHECK_SIRET_TITLE),
    VAT(StringKey.COMPLIANCE_CHECK_VAT_TITLE),
    LEGAL_MENTIONS(StringKey.COMPLIANCE_CHECK_LEGAL_TITLE),
    FACTURX_STRUCTURE(StringKey.COMPLIANCE_CHECK_FACTURX_TITLE),
    ;

    companion object {
        /** Ordre d'affichage de la checklist — décision de domaine, pas tri d'écran. */
        fun ordered(): List<ComplianceCheck> = entries.toList()
    }
}

/**
 * Constat d'un contrôle.
 *
 * [messageKey] et non un message libre : un motif de non-conformité qui ne serait pas traduisible
 * serait un trou dans la parité FR/EN que `AppTranslationsTest` ne pourrait pas voir.
 */
data class ComplianceFinding(
    val check: ComplianceCheck,
    val status: ComplianceStatus,
    val messageKey: StringKey,
)

/**
 * Rapport d'audit — un constat par contrôle, dans l'ordre du domaine.
 *
 * Toutes les synthèses sont **dérivées** : deux sources de vérité sur la conformité d'une même
 * facture finiraient par se contredire, et c'est précisément le genre de contradiction qu'un
 * panneau d'audit ne peut pas se permettre.
 */
data class ComplianceReport(val findings: List<ComplianceFinding>) {

    /** La pire gravité rencontrée — voir l'ordre de déclaration de [ComplianceStatus]. */
    val overallStatus: ComplianceStatus
        get() = findings.maxOfOrNull { it.status } ?: ComplianceStatus.PASSED

    val isCompliant: Boolean get() = overallStatus == ComplianceStatus.PASSED

    val failures: List<ComplianceFinding> get() = findings.filter { it.status == ComplianceStatus.FAILED }

    val warnings: List<ComplianceFinding> get() = findings.filter { it.status == ComplianceStatus.WARNING }

    /** Constat d'un contrôle donné, `null` s'il n'a pas été évalué. */
    fun findingFor(check: ComplianceCheck): ComplianceFinding? = findings.firstOrNull { it.check == check }
}

/**
 * Ce sur quoi porte l'audit.
 *
 * ## Pourquoi ce n'est pas une `Invoice`
 *
 * `Invoice` exige au moins une ligne valide : une facture en cours de saisie n'en est pas encore
 * une — et c'est exactement à ce moment-là qu'un audit sert. Passer par un sujet dédié rend le
 * moteur éprouvable en `commonTest` sans composer d'interface ni fabriquer une facture valide,
 * comme `AccountingArchiveBuilder` prend ses entrées explicitement (US-22).
 *
 * @param lines lignes **déjà valides** : une ligne incomplète en cours de frappe n'est pas un
 *   manquement réglementaire, c'est une saisie inachevée.
 */
data class ComplianceSubject(
    val issuer: Party,
    val issuerVatNumber: String,
    val client: Party,
    val lines: List<InvoiceLine>,
    val totalHt: Money,
    val totalVat: Money,
    val totalTtc: Money,
    val applyB2bPenalties: Boolean,
    val generateFacturX: Boolean,
)
