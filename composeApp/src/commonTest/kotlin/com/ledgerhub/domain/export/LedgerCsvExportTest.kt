package com.ledgerhub.domain.export

import com.ledgerhub.domain.invoice.Invoice
import com.ledgerhub.domain.invoice.InvoiceLine
import com.ledgerhub.domain.invoice.InvoiceStatus
import com.ledgerhub.domain.invoice.Money
import com.ledgerhub.domain.invoice.Party
import com.ledgerhub.domain.invoice.VatRate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Niveau 1 (US-19) — export comptable CSV du grand livre.
 *
 * Un fichier destiné à un tiers : ce sont ses détails de format qui décident s'il s'ouvre
 * correctement chez le comptable, et ils sont donc figés ici. L'échappement en particulier — un
 * point-virgule dans un nom de client décalerait silencieusement toutes les colonnes suivantes,
 * défaut qu'aucune relecture ne rattrape et que seul un contrôle automatique attrape.
 */
class LedgerCsvExportTest {

    private val issuer = Party("Cabinet LedgerHub", "820329331", "82032933100027")

    private fun invoice(
        number: String = "FAC-2026-0401",
        clientName: String = "Boulangerie Moreau SARL",
        status: InvoiceStatus = InvoiceStatus.DEPOSITED,
        unitPriceCents: Long = 10_000,
    ) = Invoice(
        number = number,
        issueDate = "2026-08-01",
        issuer = issuer,
        recipient = Party(clientName, "784102336", "78410233600021"),
        lines = listOf(InvoiceLine("Conseil réglementaire", 2, Money(unitPriceCents), VatRate.TAUX_NORMAL)),
        status = status,
        dueDate = "2026-08-31",
    )

    private fun lines(csv: String) = csv.split("\r\n")

    // ── Structure ───────────────────────────────────────────────────────────

    @Test
    fun theHeaderNamesEveryColumn() {
        val header = lines(LedgerCsvExport.generate(emptyList())).first()

        assertEquals(
            "Numero;Date emission;Date echeance;Client;SIRET client;Total HT;Total TVA;Total TTC;Statut",
            header,
        )
    }

    /** Un fichier entièrement vide se lirait comme un échec d'export, pas comme un grand livre vide. */
    @Test
    fun anEmptyLedger_stillProducesItsHeader() {
        assertEquals(1, lines(LedgerCsvExport.generate(emptyList())).size)
    }

    @Test
    fun oneLinePerInvoice_inTheGivenOrder() {
        val csv = LedgerCsvExport.generate(listOf(invoice("FAC-A"), invoice("FAC-B")))

        val rows = lines(csv)
        assertEquals(3, rows.size)
        assertTrue(rows[1].startsWith("FAC-A;"))
        assertTrue(rows[2].startsWith("FAC-B;"))
    }

    // ── Format des montants ─────────────────────────────────────────────────

    /**
     * Virgule décimale et séparateur point-virgule : c'est ce qu'attend un tableur en locale
     * française. Un CSV à virgules s'y ouvrirait en une seule colonne.
     */
    @Test
    fun amounts_useTheFrenchDecimalComma() {
        val row = lines(LedgerCsvExport.generate(listOf(invoice()))) [1]

        // 2 × 100,00 € HT → 200,00 HT / 40,00 TVA / 240,00 TTC
        assertTrue(row.contains("200,00"), "Total HT absent ou mal formaté : $row")
        assertTrue(row.contains("40,00"), "Total TVA absent ou mal formaté : $row")
        assertTrue(row.contains("240,00"), "Total TTC absent ou mal formaté : $row")
    }

    /** Les centimes sous la dizaine gardent leur zéro : `1,05` et non `1,5`. */
    @Test
    fun centsBelowTen_keepTheirLeadingZero() {
        val row = lines(LedgerCsvExport.generate(listOf(invoice(unitPriceCents = 1)))) [1]

        assertTrue(row.contains("0,02"), "Montant mal formaté : $row")
    }

    // ── Échappement RFC 4180 ────────────────────────────────────────────────

    @Test
    fun aFieldContainingTheSeparator_isQuoted() {
        val row = lines(LedgerCsvExport.generate(listOf(invoice(clientName = "Moreau; Fils")))) [1]

        assertTrue(row.contains("\"Moreau; Fils\""), "Séparateur non échappé : $row")
    }

    @Test
    fun aFieldContainingAQuote_doublesIt() {
        val row = lines(LedgerCsvExport.generate(listOf(invoice(clientName = "Le \"Bon\" Pain")))) [1]

        assertTrue(row.contains("\"Le \"\"Bon\"\" Pain\""), "Guillemet non échappé : $row")
    }

    // ── Contenu métier ──────────────────────────────────────────────────────

    /**
     * Le statut sort en valeur du référentiel, non traduit : c'est ce qu'un rapprochement comptable
     * recoupe, là où un libellé d'écran varierait avec la langue de l'utilisateur.
     */
    @Test
    fun theStatus_isTheReferenceValue_notItsLabel() {
        val row = lines(LedgerCsvExport.generate(listOf(invoice(status = InvoiceStatus.PAID)))) [1]

        assertTrue(row.endsWith("PAID"), "Statut attendu en valeur brute : $row")
    }

    @Test
    fun datesAreExportedAsIso() {
        val row = lines(LedgerCsvExport.generate(listOf(invoice()))) [1]

        assertTrue(row.contains("2026-08-01"), "Date d'émission absente : $row")
        assertTrue(row.contains("2026-08-31"), "Date d'échéance absente : $row")
    }
}
