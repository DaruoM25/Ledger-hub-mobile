package com.ledgerhub.domain.compliance

import com.ledgerhub.domain.directory.FrenchVatNumber
import com.ledgerhub.domain.directory.LuhnChecksum
import com.ledgerhub.domain.i18n.StringKey
import com.ledgerhub.domain.invoice.FiscalValidation
import com.ledgerhub.domain.invoice.ValidationResult
import com.ledgerhub.domain.invoice.totalHtOf
import com.ledgerhub.domain.invoice.totalTtcOf
import com.ledgerhub.domain.invoice.totalVatOf

/**
 * Moteur d'audit de conformité Factur-X / norme 2026 (US-24).
 *
 * ## Il orchestre, il ne réimplémente rien
 *
 * La longueur du SIRET vient de [FiscalValidation], sa clé de contrôle de [LuhnChecksum]
 * (dérogation La Poste comprise), le format du numéro de TVA de [FiscalValidation] et sa clé
 * modulo 97 de [FrenchVatNumber], les totaux des fonctions de `InvoiceTotals`. Une seconde
 * implémentation de l'une de ces règles finirait par diverger de la première — et un audit qui
 * contredit le formulaire qu'il audite ne vaut rien.
 *
 * Fonction pure, sans coroutine ni horloge : tout l'audit s'éprouve en `commonTest`.
 */
object ComplianceAuditor {

    /**
     * Devise des factures, **invariant documenté**.
     *
     * Le domaine ne porte aucun champ devise : [com.ledgerhub.domain.invoice.Money] compte des
     * centimes, le formatage est en euros et le générateur Factur-X n'expose pas de code devise
     * variable. Le contrôle porte donc sur une constante plutôt que sur une donnée qui n'existe
     * pas — le prétendre serait une vérification de façade. Rendre la devise configurable
     * toucherait `Invoice`, `FacturXDocument`, le schéma SQLDelight et sa migration : c'est une US
     * à part entière.
     */
    const val CURRENCY: String = "EUR"

    fun audit(subject: ComplianceSubject): ComplianceReport = ComplianceReport(
        findings = listOf(
            auditSiret(subject),
            auditVatNumber(subject),
            auditLegalMentions(subject),
            auditFacturXStructure(subject),
        ),
    )

    // ── SIRET ────────────────────────────────────────────────────────────────

    /**
     * Longueur bloquante, clé de Luhn en avertissement.
     *
     * Ce n'est pas une indulgence : une saisie de moins de 14 chiffres n'identifie **aucun**
     * établissement, alors qu'une clé fausse désigne un identifiant plausible mais douteux. Le
     * jeu de démonstration de l'application en fournit d'ailleurs la preuve — ses SIRET ne
     * satisfont pas Luhn (constaté en US-21), et les traiter comme bloquants afficherait un
     * bandeau rouge sur les propres factures d'exemple du produit.
     */
    private fun auditSiret(subject: ComplianceSubject): ComplianceFinding {
        val issuerSiret = subject.issuer.siret.trim()
        val clientSiret = subject.client.siret.trim()

        if (FiscalValidation.validateSiret(issuerSiret) is ValidationResult.Invalid) {
            return finding(ComplianceCheck.SIRET, ComplianceStatus.FAILED, StringKey.COMPLIANCE_SIRET_ISSUER_INVALID)
        }
        if (FiscalValidation.validateSiret(clientSiret) is ValidationResult.Invalid) {
            return finding(ComplianceCheck.SIRET, ComplianceStatus.FAILED, StringKey.COMPLIANCE_SIRET_CLIENT_INVALID)
        }
        if (!LuhnChecksum.isValidSiret(issuerSiret) || !LuhnChecksum.isValidSiret(clientSiret)) {
            return finding(ComplianceCheck.SIRET, ComplianceStatus.WARNING, StringKey.COMPLIANCE_SIRET_LUHN)
        }
        return finding(ComplianceCheck.SIRET, ComplianceStatus.PASSED, StringKey.COMPLIANCE_SIRET_OK)
    }

    // ── Numéro de TVA intracommunautaire ─────────────────────────────────────

    /**
     * Absent en avertissement, mal formé ou incohérent en bloquant.
     *
     * Une entreprise en **franchise en base** n'a légitimement pas de numéro de TVA
     * intracommunautaire — [FiscalValidation.validateVatNumber] le tient d'ailleurs déjà pour
     * optionnel. En revanche, un numéro présent mais faux part tel quel sur une facture
     * électronique : c'est là que l'erreur coûte.
     *
     * La cohérence est vérifiée deux fois, et ce n'est pas redondant : [FrenchVatNumber.isValid]
     * contrôle que la clé correspond au SIREN **porté par le numéro**, et la comparaison qui suit
     * que ce SIREN est bien celui de l'émetteur. Un numéro parfaitement formé mais appartenant à
     * une autre société passerait le premier contrôle.
     */
    private fun auditVatNumber(subject: ComplianceSubject): ComplianceFinding {
        val vatNumber = subject.issuerVatNumber.trim().uppercase()

        if (vatNumber.isEmpty()) {
            return finding(ComplianceCheck.VAT, ComplianceStatus.WARNING, StringKey.COMPLIANCE_VAT_ABSENT)
        }
        if (FiscalValidation.validateVatNumber(vatNumber, optional = false) is ValidationResult.Invalid) {
            return finding(ComplianceCheck.VAT, ComplianceStatus.FAILED, StringKey.COMPLIANCE_VAT_MALFORMED)
        }
        if (!FrenchVatNumber.isValid(vatNumber)) {
            return finding(ComplianceCheck.VAT, ComplianceStatus.FAILED, StringKey.COMPLIANCE_VAT_KEY_MISMATCH)
        }
        val issuerSiren = subject.issuer.siren.trim()
        if (issuerSiren.isNotEmpty() && vatNumber.substring(VAT_SIREN_START) != issuerSiren) {
            return finding(ComplianceCheck.VAT, ComplianceStatus.FAILED, StringKey.COMPLIANCE_VAT_KEY_MISMATCH)
        }
        return finding(ComplianceCheck.VAT, ComplianceStatus.PASSED, StringKey.COMPLIANCE_VAT_OK)
    }

    // ── Mentions légales obligatoires ────────────────────────────────────────

    /**
     * Avertissement et non blocage : la mention de l'article L.441-10 — pénalités de retard et
     * indemnité forfaitaire de 40 € — est obligatoire **entre professionnels**. Une facture
     * adressée à un particulier n'a pas à la porter, et le produit laisse ce choix au cas par cas
     * (US-16). Le panneau signale donc, il n'interdit pas.
     */
    private fun auditLegalMentions(subject: ComplianceSubject): ComplianceFinding =
        if (subject.applyB2bPenalties) {
            finding(ComplianceCheck.LEGAL_MENTIONS, ComplianceStatus.PASSED, StringKey.COMPLIANCE_LEGAL_OK)
        } else {
            finding(ComplianceCheck.LEGAL_MENTIONS, ComplianceStatus.WARNING, StringKey.COMPLIANCE_LEGAL_MISSING)
        }

    // ── Structure Factur-X ───────────────────────────────────────────────────

    /**
     * Lignes, devise et cohérence des totaux.
     *
     * Les totaux sont **recalculés depuis les lignes** et comparés à ceux que porte le sujet :
     * c'est le seul moyen de détecter qu'un total affiché a divergé de son assiette. Un écart y
     * est bloquant — une facture dont le TTC ne découle pas de ses lignes est rejetée par la
     * plateforme avant même d'être lue.
     */
    private fun auditFacturXStructure(subject: ComplianceSubject): ComplianceFinding {
        if (subject.lines.isEmpty()) {
            return finding(
                ComplianceCheck.FACTURX_STRUCTURE,
                ComplianceStatus.FAILED,
                StringKey.COMPLIANCE_FACTURX_NO_LINE,
            )
        }
        val recomputedHt = totalHtOf(subject.lines)
        val recomputedVat = totalVatOf(subject.lines)
        val recomputedTtc = totalTtcOf(subject.lines)
        val totalsMatch = recomputedHt == subject.totalHt &&
            recomputedVat == subject.totalVat &&
            recomputedTtc == subject.totalTtc &&
            subject.totalHt + subject.totalVat == subject.totalTtc
        if (!totalsMatch) {
            return finding(
                ComplianceCheck.FACTURX_STRUCTURE,
                ComplianceStatus.FAILED,
                StringKey.COMPLIANCE_FACTURX_TOTALS,
            )
        }
        if (!subject.generateFacturX) {
            return finding(
                ComplianceCheck.FACTURX_STRUCTURE,
                ComplianceStatus.WARNING,
                StringKey.COMPLIANCE_FACTURX_DISABLED,
            )
        }
        return finding(ComplianceCheck.FACTURX_STRUCTURE, ComplianceStatus.PASSED, StringKey.COMPLIANCE_FACTURX_OK)
    }

    private fun finding(check: ComplianceCheck, status: ComplianceStatus, messageKey: StringKey) =
        ComplianceFinding(check = check, status = status, messageKey = messageKey)

    /** Un numéro `FRXX999999999` porte son SIREN à partir du 5e caractère. */
    private const val VAT_SIREN_START = 4
}
