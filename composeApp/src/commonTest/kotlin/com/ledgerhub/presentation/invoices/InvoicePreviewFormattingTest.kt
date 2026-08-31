package com.ledgerhub.presentation.invoices

import com.ledgerhub.domain.i18n.AppLanguage
import com.ledgerhub.domain.i18n.AppTranslations
import com.ledgerhub.domain.i18n.StringKey
import com.ledgerhub.domain.invoice.Invoice
import com.ledgerhub.domain.invoice.InvoiceLine
import com.ledgerhub.domain.invoice.Letterhead
import com.ledgerhub.domain.invoice.Money
import com.ledgerhub.domain.invoice.Party
import com.ledgerhub.domain.invoice.VatRate
import com.ledgerhub.presentation.i18n.formatIsoDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * N1 (US-14) — contrat de calcul et de formatage de l'aperçu A4.
 *
 * L'aperçu ne recalcule rien : il relit [Invoice.totalHt] / [Invoice.totalVat] /
 * [Invoice.totalTtc]. Ces tests verrouillent ce que la feuille doit afficher, indépendamment de
 * son rendu Compose — un aperçu dont les totaux divergeraient de la pièce fiscale serait un
 * document faux, pas un défaut d'affichage.
 */
class InvoicePreviewFormattingTest {

    private val issuer = Party("Roux Expertise", "820329331", "82032933100027", "contact@roux-expertise.fr")
    private val recipient = Party("Boulangerie Moreau SARL", "784102336", "78410233600021")

    private fun invoice(lines: List<InvoiceLine>, dueDate: String = "2026-09-15") = Invoice(
        number = "FAC-2026-0184",
        issueDate = "2026-08-16",
        issuer = issuer,
        recipient = recipient,
        lines = lines,
        dueDate = dueDate,
    )

    // ── Totaux : l'agrégat fait foi ──────────────────────────────────────────────────────────

    /**
     * Cas de référence : trois lignes à 5,5 % dont la TVA unitaire tombe pile sur un
     * demi-centime. Il justifie le choix de colonne imposé par le standard B2B PPF 2026 :
     *
     * - la colonne **HT** affichée est additive — sa somme égale exactement [Invoice.totalHt] ;
     * - une colonne TTC ne l'aurait pas été, `computeVatBreakdown` calculant la TVA sur la base
     *   HT agrégée de chaque taux plutôt qu'en sommant des arrondis de ligne.
     */
    @Test
    fun displayedHtColumn_isAdditive_whereATtcColumnWouldNotHaveBeen() {
        val lines = List(3) {
            InvoiceLine("Prestation $it", quantity = 1, unitPriceHt = Money(1_009), vatRate = VatRate.TAUX_REDUIT)
        }
        val invoice = invoice(lines)

        // Ce que la 5e colonne affiche désormais : le net HT de chaque ligne.
        val sumOfDisplayedLineTotals = lines.fold(Money.ZERO) { acc, line -> acc + line.totalHt }
        assertEquals(
            invoice.totalHt,
            sumOfDisplayedLineTotals,
            "La colonne HT doit se totaliser exactement au Total HT du pied de page",
        )

        assertEquals(Money(3_027), invoice.totalHt)
        // 3027 * 5,5 % = 166,485 -> 166 centimes sur la base agrégée.
        assertEquals(Money(166), invoice.totalVat)
        assertEquals(Money(3_193), invoice.totalTtc)

        // Contre-preuve : ligne à ligne, 1009 * 5,5 % = 55,495 -> 55, soit 3 * 1064 = 3192.
        // Un centime manquerait si la colonne portait le TTC.
        val sumOfLineTtc = lines.fold(Money.ZERO) { acc, line -> acc + line.totalTtc }
        assertEquals(Money(3_192), sumOfLineTtc)
        assertNotEquals(
            sumOfLineTtc,
            invoice.totalTtc,
            "Le cas de test doit produire un écart d'arrondi, sinon il ne prouve rien",
        )
    }

    @Test
    fun totals_areConsistent_htPlusVatEqualsTtc() {
        val invoice = invoice(
            listOf(
                InvoiceLine("Conseil", quantity = 2, unitPriceHt = Money(120_000), vatRate = VatRate.TAUX_NORMAL),
                InvoiceLine("Documentation", quantity = 1, unitPriceHt = Money(4_500), vatRate = VatRate.TAUX_REDUIT),
            ),
        )
        assertEquals(invoice.totalTtc, invoice.totalHt + invoice.totalVat)
    }

    /** Chaque taux présent ouvre exactement une ligne de ventilation — mention Factur-X. */
    @Test
    fun vatBreakdown_hasOneEntryPerDistinctRate() {
        val invoice = invoice(
            listOf(
                InvoiceLine("A", 1, Money(10_000), VatRate.TAUX_NORMAL),
                InvoiceLine("B", 1, Money(20_000), VatRate.TAUX_NORMAL),
                InvoiceLine("C", 1, Money(5_000), VatRate.TAUX_REDUIT),
            ),
        )
        assertEquals(2, invoice.vatBreakdown.size)
        assertEquals(Money(30_000), invoice.vatBreakdown.first { it.rate == VatRate.TAUX_NORMAL }.baseHt)
    }

    // ── Formatage des colonnes ───────────────────────────────────────────────────────────────

    @Test
    fun lineColumns_areFormattedForTheActiveLanguage() {
        val line = InvoiceLine("Prestation de conseil", quantity = 3, unitPriceHt = Money(125_050), vatRate = VatRate.TAUX_NORMAL)

        assertEquals("1 250,50 €".replace(' ', NON_BREAKING_SPACE[0]), line.unitPriceHt.format(AppLanguage.FR))
        assertEquals("€1,250.50", line.unitPriceHt.format(AppLanguage.EN))

        // 5e colonne : net hors taxe, soit quantité x prix unitaire HT (3 x 1 250,50).
        assertEquals(Money(375_150), line.totalHt)
        assertEquals("3 751,50 €".replace(' ', NON_BREAKING_SPACE[0]), line.totalHt.format(AppLanguage.FR))
        assertEquals("€3,751.50", line.totalHt.format(AppLanguage.EN))
    }

    /**
     * En-tête de la 5e colonne. Le libellé est le marqueur visible du standard B2B PPF 2026 :
     * une facture inter-entreprises détaille le HT ligne à ligne et ne fait apparaître la TVA
     * qu'une fois, ventilée par taux, au pied de page.
     */
    @Test
    fun fifthColumnHeader_readsTotalExcludingVat() {
        assertEquals("Total HT", AppTranslations.get(StringKey.PREVIEW_COL_TOTAL_HT, AppLanguage.FR))
        assertEquals("Total excl. VAT", AppTranslations.get(StringKey.PREVIEW_COL_TOTAL_HT, AppLanguage.EN))
    }

    @Test
    fun dates_areFormattedForTheActiveLanguage() {
        assertEquals("16/08/2026", formatIsoDate("2026-08-16", AppLanguage.FR))
        assertEquals("Aug 16, 2026", formatIsoDate("2026-08-16", AppLanguage.EN))
    }

    /**
     * Le parc hérité contient des factures sans échéance ([Invoice.dueDate] vide) : la feuille
     * doit pouvoir omettre la ligne plutôt qu'imprimer une date vide.
     */
    @Test
    fun dueDate_canBeAbsent_onLegacyInvoices() {
        assertTrue(invoice(listOf(InvoiceLine("A", 1, Money(1_000), VatRate.TAUX_NORMAL)), dueDate = "").dueDate.isBlank())
    }

    // ── Papier à en-tête et mentions obligatoires ────────────────────────────────────────────

    @Test
    fun defaultLetterhead_carriesEveryMandatoryPrintedField() {
        val letterhead = Letterhead.Default

        assertTrue(letterhead.issuerAddressLines.isNotEmpty(), "adresse postale obligatoire")
        assertTrue(letterhead.issuerPhone.isNotBlank())
        assertTrue(letterhead.issuerVatNumber.startsWith("FR"), "TVA intracommunautaire française")
        assertTrue(letterhead.iban.isNotBlank())
        assertTrue(letterhead.bic.isNotBlank())
        assertTrue(letterhead.latePenaltyRate.isNotBlank())
    }

    /** Indemnité forfaitaire de recouvrement — 40 €, art. D441-5 du Code de commerce. */
    @Test
    fun fixedRecoveryIndemnity_isFortyEuros() {
        assertEquals(40, Letterhead.Default.fixedRecoveryIndemnityEuros)
        assertEquals(40, Letterhead.DEFAULT_RECOVERY_INDEMNITY_EUROS)
    }

    /**
     * L'en-tête ne porte pas d'identité d'émetteur : celle-ci est gelée sur la facture. Ce test
     * échouerait si un champ de nom y était ajouté, ce qui permettrait de réimprimer une pièce
     * déjà émise sous une autre raison sociale.
     */
    @Test
    fun issuerIdentity_staysOnTheInvoice_notOnTheLetterhead() {
        val invoice = invoice(listOf(InvoiceLine("A", 1, Money(1_000), VatRate.TAUX_NORMAL)))
        assertEquals("Roux Expertise", invoice.issuer.name)
        assertEquals("82032933100027", invoice.issuer.siret)
    }

    // ── Mentions légales i18n ────────────────────────────────────────────────────────────────

    @Test
    fun mandatoryLegalMention_usesTheExactRequiredWording() {
        assertEquals(
            "Membre d'une association agréée, le règlement par chèque et carte bancaire est accepté.",
            AppTranslations.get(StringKey.PREVIEW_LEGAL_ASSOCIATION, AppLanguage.FR),
        )
    }

    @Test
    fun everyPreviewKey_isTranslatedInBothLanguages() {
        val previewKeys = StringKey.entries.filter {
            it.name.startsWith("PREVIEW_") || it == StringKey.ACTION_PREVIEW_INVOICE
        }
        assertTrue(previewKeys.size >= 20, "bloc d'aperçu incomplet : ${previewKeys.size} clés")

        previewKeys.forEach { key ->
            AppLanguage.entries.forEach { language ->
                assertTrue(
                    AppTranslations.get(key, language).isNotBlank(),
                    "Traduction vide pour $key / $language",
                )
            }
        }
    }
}
