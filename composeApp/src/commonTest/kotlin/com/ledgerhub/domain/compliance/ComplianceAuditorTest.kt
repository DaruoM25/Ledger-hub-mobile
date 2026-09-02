package com.ledgerhub.domain.compliance

import com.ledgerhub.domain.i18n.StringKey
import com.ledgerhub.domain.invoice.InvoiceLine
import com.ledgerhub.domain.invoice.Money
import com.ledgerhub.domain.invoice.Party
import com.ledgerhub.domain.invoice.VatRate
import com.ledgerhub.domain.invoice.totalHtOf
import com.ledgerhub.domain.invoice.totalTtcOf
import com.ledgerhub.domain.invoice.totalVatOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Niveau 1 (US-24) — moteur d'audit de conformité Factur-X 2026.
 *
 * Un audit qui se trompe est pire qu'un audit absent : il fait émettre une facture non conforme
 * avec la bénédiction de l'outil, ou bloque une facture parfaitement valide. Les quatre contrôles
 * sont donc éprouvés dans leurs trois issues, et les **arbitrages de gravité** — validés avant
 * écriture — sont figés ici plutôt que laissés à la relecture.
 */
class ComplianceAuditorTest {

    // SIRET de La Poste : dérogation officielle à la clé de Luhn (référentiel INSEE).
    private val laPosteSiret = "35600000000048"

    /** Identifiants satisfaisant la clé de Luhn — vérifié par `LuhnChecksum`. */
    private val issuer = Party(
        name = "Cabinet LedgerHub",
        siren = "552100554",
        siret = "55210055400013",
        email = "facturation@ledgerhub.app",
    )
    private val client = Party(
        name = "Boulangerie Moreau SARL",
        siren = "552100554",
        siret = "55210055400013",
        email = "compta@moreau.fr",
    )

    /** `FR` + clé mod 97 + SIREN de l'émetteur — calculée, jamais devinée. */
    private val issuerVat = "FR" + com.ledgerhub.domain.directory.FrenchVatNumber.computeKey(issuer.siren) + issuer.siren

    private val lines = listOf(
        InvoiceLine("Conseil réglementaire", 2, Money(10_000), VatRate.TAUX_NORMAL),
    )

    private fun subject(
        issuer: Party = this.issuer,
        issuerVatNumber: String = issuerVat,
        client: Party = this.client,
        lines: List<InvoiceLine> = this.lines,
        totalHt: Money = totalHtOf(this.lines),
        totalVat: Money = totalVatOf(this.lines),
        totalTtc: Money = totalTtcOf(this.lines),
        applyB2bPenalties: Boolean = true,
        generateFacturX: Boolean = true,
    ) = ComplianceSubject(
        issuer = issuer,
        issuerVatNumber = issuerVatNumber,
        client = client,
        lines = lines,
        totalHt = totalHt,
        totalVat = totalVat,
        totalTtc = totalTtc,
        applyB2bPenalties = applyB2bPenalties,
        generateFacturX = generateFacturX,
    )

    private fun statusOf(subject: ComplianceSubject, check: ComplianceCheck) =
        ComplianceAuditor.audit(subject).findingFor(check)?.status

    private fun messageOf(subject: ComplianceSubject, check: ComplianceCheck) =
        ComplianceAuditor.audit(subject).findingFor(check)?.messageKey

    // ── Rapport ─────────────────────────────────────────────────────────────

    @Test
    fun theReport_carriesOneFindingPerCheck_inDomainOrder() {
        val report = ComplianceAuditor.audit(subject())

        assertEquals(ComplianceCheck.ordered(), report.findings.map { it.check })
        assertEquals(4, report.findings.size)
    }

    @Test
    fun aFullyCompliantInvoice_passesEveryCheck() {
        val report = ComplianceAuditor.audit(subject())

        assertTrue(report.isCompliant, "Constats : ${report.findings}")
        assertEquals(ComplianceStatus.PASSED, report.overallStatus)
        assertTrue(report.failures.isEmpty())
        assertTrue(report.warnings.isEmpty())
    }

    /** La gravité globale est la **pire** rencontrée, jamais une moyenne ni la première venue. */
    @Test
    fun theOverallStatus_isTheWorstOfTheFindings() {
        val warningOnly = ComplianceAuditor.audit(subject(applyB2bPenalties = false))
        assertEquals(ComplianceStatus.WARNING, warningOnly.overallStatus)

        val warningAndFailure = ComplianceAuditor.audit(
            subject(applyB2bPenalties = false, client = client.copy(siret = "123")),
        )
        assertEquals(ComplianceStatus.FAILED, warningAndFailure.overallStatus)
        assertFalse(warningAndFailure.isCompliant)
    }

    // ── SIRET : longueur bloquante, Luhn en avertissement ───────────────────

    @Test
    fun anIssuerSiretShorterThanFourteenDigits_blocks() {
        val audited = subject(issuer = issuer.copy(siret = "5521005540001"))

        assertEquals(ComplianceStatus.FAILED, statusOf(audited, ComplianceCheck.SIRET))
        assertEquals(StringKey.COMPLIANCE_SIRET_ISSUER_INVALID, messageOf(audited, ComplianceCheck.SIRET))
    }

    @Test
    fun aMissingClientSiret_blocks() {
        val audited = subject(client = client.copy(siret = ""))

        assertEquals(ComplianceStatus.FAILED, statusOf(audited, ComplianceCheck.SIRET))
        assertEquals(StringKey.COMPLIANCE_SIRET_CLIENT_INVALID, messageOf(audited, ComplianceCheck.SIRET))
    }

    @Test
    fun aNonNumericSiret_blocks() {
        val audited = subject(client = client.copy(siret = "5521005540001X"))

        assertEquals(ComplianceStatus.FAILED, statusOf(audited, ComplianceCheck.SIRET))
    }

    /**
     * **Arbitrage figé.** Quatorze chiffres dont la clé est fausse désignent un identifiant
     * plausible mais douteux : c'est un avertissement, pas un blocage. Le jeu de démonstration de
     * l'application en fournit la raison — ses SIRET ne satisfont pas Luhn, et les bloquer
     * afficherait un bandeau rouge sur les propres factures d'exemple du produit.
     */
    @Test
    fun aFourteenDigitSiretWithABadChecksum_onlyWarns() {
        val audited = subject(client = client.copy(siret = "78410233600021"))

        assertEquals(ComplianceStatus.WARNING, statusOf(audited, ComplianceCheck.SIRET))
        assertEquals(StringKey.COMPLIANCE_SIRET_LUHN, messageOf(audited, ComplianceCheck.SIRET))
    }

    /** La dérogation La Poste vient de `LuhnChecksum` : l'auditeur n'en connaît rien, et c'est voulu. */
    @Test
    fun theLaPosteExemption_isHonoured() {
        val audited = subject(client = client.copy(siret = laPosteSiret))

        assertEquals(ComplianceStatus.PASSED, statusOf(audited, ComplianceCheck.SIRET))
    }

    // ── TVA : absente en avertissement, fausse en blocage ───────────────────

    /** **Arbitrage figé** : une entreprise en franchise en base n'a légitimement pas de numéro. */
    @Test
    fun anAbsentVatNumber_onlyWarns() {
        val audited = subject(issuerVatNumber = "")

        assertEquals(ComplianceStatus.WARNING, statusOf(audited, ComplianceCheck.VAT))
        assertEquals(StringKey.COMPLIANCE_VAT_ABSENT, messageOf(audited, ComplianceCheck.VAT))
    }

    @Test
    fun aMalformedVatNumber_blocks() {
        val audited = subject(issuerVatNumber = "FR-123")

        assertEquals(ComplianceStatus.FAILED, statusOf(audited, ComplianceCheck.VAT))
        assertEquals(StringKey.COMPLIANCE_VAT_MALFORMED, messageOf(audited, ComplianceCheck.VAT))
    }

    /** Numéro bien formé mais dont la clé mod 97 ne correspond pas au SIREN qu'il porte. */
    @Test
    fun aVatNumberWithAWrongKey_blocks() {
        val wrongKey = if (issuerVat.substring(2, 4) == "00") "01" else "00"
        val audited = subject(issuerVatNumber = "FR" + wrongKey + issuer.siren)

        assertEquals(ComplianceStatus.FAILED, statusOf(audited, ComplianceCheck.VAT))
        assertEquals(StringKey.COMPLIANCE_VAT_KEY_MISMATCH, messageOf(audited, ComplianceCheck.VAT))
    }

    /**
     * Numéro parfaitement valide — mais celui d'une **autre** société. La clé interne ne suffit
     * donc pas : c'est tout l'objet du second contrôle.
     */
    @Test
    fun aValidVatNumberBelongingToAnotherCompany_blocks() {
        val otherSiren = "356000000"
        val otherVat = "FR" + com.ledgerhub.domain.directory.FrenchVatNumber.computeKey(otherSiren) + otherSiren
        val audited = subject(issuerVatNumber = otherVat)

        assertEquals(ComplianceStatus.FAILED, statusOf(audited, ComplianceCheck.VAT))
        assertEquals(StringKey.COMPLIANCE_VAT_KEY_MISMATCH, messageOf(audited, ComplianceCheck.VAT))
    }

    @Test
    fun aVatNumber_isNormalisedBeforeBeingJudged() {
        val audited = subject(issuerVatNumber = "  " + issuerVat.lowercase() + "  ")

        assertEquals(ComplianceStatus.PASSED, statusOf(audited, ComplianceCheck.VAT))
    }

    // ── Mentions légales ────────────────────────────────────────────────────

    @Test
    fun theB2bMentions_passWhenApplied() {
        assertEquals(ComplianceStatus.PASSED, statusOf(subject(), ComplianceCheck.LEGAL_MENTIONS))
        assertEquals(StringKey.COMPLIANCE_LEGAL_OK, messageOf(subject(), ComplianceCheck.LEGAL_MENTIONS))
    }

    /** **Arbitrage figé** : une facture à un particulier n'a pas à porter la mention L.441-10. */
    @Test
    fun disablingTheB2bMentions_onlyWarns() {
        val audited = subject(applyB2bPenalties = false)

        assertEquals(ComplianceStatus.WARNING, statusOf(audited, ComplianceCheck.LEGAL_MENTIONS))
        assertEquals(StringKey.COMPLIANCE_LEGAL_MISSING, messageOf(audited, ComplianceCheck.LEGAL_MENTIONS))
    }

    // ── Structure Factur-X ──────────────────────────────────────────────────

    @Test
    fun anInvoiceWithoutAnyUsableLine_blocks() {
        val audited = subject(lines = emptyList(), totalHt = Money.ZERO, totalVat = Money.ZERO, totalTtc = Money.ZERO)

        assertEquals(ComplianceStatus.FAILED, statusOf(audited, ComplianceCheck.FACTURX_STRUCTURE))
        assertEquals(StringKey.COMPLIANCE_FACTURX_NO_LINE, messageOf(audited, ComplianceCheck.FACTURX_STRUCTURE))
    }

    /** Un TTC qui ne découle pas de ses lignes est rejeté par la plateforme avant d'être lu. */
    @Test
    fun totalsThatDoNotFollowFromTheLines_block() {
        val audited = subject(totalTtc = Money(999_99))

        assertEquals(ComplianceStatus.FAILED, statusOf(audited, ComplianceCheck.FACTURX_STRUCTURE))
        assertEquals(StringKey.COMPLIANCE_FACTURX_TOTALS, messageOf(audited, ComplianceCheck.FACTURX_STRUCTURE))
    }

    @Test
    fun aTotalHtDivergingFromTheLines_blocks() {
        val audited = subject(totalHt = Money(1))

        assertEquals(ComplianceStatus.FAILED, statusOf(audited, ComplianceCheck.FACTURX_STRUCTURE))
    }

    @Test
    fun disablingFacturXGeneration_onlyWarns() {
        val audited = subject(generateFacturX = false)

        assertEquals(ComplianceStatus.WARNING, statusOf(audited, ComplianceCheck.FACTURX_STRUCTURE))
        assertEquals(StringKey.COMPLIANCE_FACTURX_DISABLED, messageOf(audited, ComplianceCheck.FACTURX_STRUCTURE))
    }

    /**
     * La devise est un **invariant documenté** et non une donnée : le domaine n'en porte aucune.
     * Le test constate l'invariant plutôt que de simuler un contrôle qui n'aurait rien à lire.
     */
    @Test
    fun theCurrency_isTheDocumentedEuroInvariant() {
        assertEquals("EUR", ComplianceAuditor.CURRENCY)
    }

    // ── Formulaire vierge ───────────────────────────────────────────────────

    /**
     * Le cas que l'utilisateur rencontre en premier s'il scanne avant de saisir : deux blocages
     * (identifiants, structure) et aucun faux positif ailleurs.
     */
    @Test
    fun aBlankInvoice_reportsItsBlockingGaps() {
        val blank = ComplianceSubject(
            issuer = Party(name = "", siren = "", siret = "", email = ""),
            issuerVatNumber = "",
            client = Party(name = "", siren = "", siret = "", email = ""),
            lines = emptyList(),
            totalHt = Money.ZERO,
            totalVat = Money.ZERO,
            totalTtc = Money.ZERO,
            applyB2bPenalties = true,
            generateFacturX = true,
        )

        val report = ComplianceAuditor.audit(blank)

        assertEquals(ComplianceStatus.FAILED, report.overallStatus)
        assertEquals(
            listOf(ComplianceCheck.SIRET, ComplianceCheck.FACTURX_STRUCTURE),
            report.failures.map { it.check },
        )
        assertEquals(listOf(ComplianceCheck.VAT), report.warnings.map { it.check })
    }

    /** Un audit est une lecture : deux passages sur le même sujet donnent le même rapport. */
    @Test
    fun auditing_isDeterministic() {
        assertEquals(ComplianceAuditor.audit(subject()), ComplianceAuditor.audit(subject()))
    }
}
