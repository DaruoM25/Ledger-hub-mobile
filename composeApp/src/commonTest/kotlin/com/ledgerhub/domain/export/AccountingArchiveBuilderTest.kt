package com.ledgerhub.domain.export

import com.ledgerhub.domain.invoice.Invoice
import com.ledgerhub.domain.invoice.InvoiceLine
import com.ledgerhub.domain.invoice.InvoiceStatus
import com.ledgerhub.domain.invoice.Money
import com.ledgerhub.domain.invoice.Party
import com.ledgerhub.domain.invoice.VatRate
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Niveau 1 (US-22) — fabrique des trois documents d'export comptable.
 *
 * Un fichier remis à un tiers : ce sont ses détails de format qui décident s'il est recevable, et
 * ils sont donc figés ici. L'équilibre débit/crédit en particulier — un FEC déséquilibré est
 * rejeté par le vérificateur, et aucune relecture d'écran ne le rattrape.
 */
class AccountingArchiveBuilderTest {

    private val issuer = Party("Cabinet LedgerHub", "820329331", "82032933100027")

    private fun invoice(
        number: String = "FAC-2026-0401",
        issueDate: String = "2026-03-04",
        clientName: String = "Boulangerie Moreau SARL",
        unitPriceCents: Long = 10_000,
        vatRate: VatRate = VatRate.TAUX_NORMAL,
    ) = Invoice(
        number = number,
        issueDate = issueDate,
        issuer = issuer,
        recipient = Party(clientName, "784102336", "78410233600021"),
        lines = listOf(InvoiceLine("Conseil réglementaire", 2, Money(unitPriceCents), vatRate)),
        status = InvoiceStatus.DEPOSITED,
        dueDate = "2026-04-03",
    )

    private val exercise = ExportPeriod(from = "2026-01-01", to = "2026-12-31")

    private fun fecLines(content: String) = content.split("\r\n")

    private fun fecColumns(line: String) = line.split("\t")

    // ── Filtrage sur la période, commun aux trois formats ───────────────────

    @Test
    fun onlyTheInvoicesOfThePeriod_areCovered() {
        val invoices = listOf(
            invoice(number = "FAC-2025-0009", issueDate = "2025-12-31"),
            invoice(number = "FAC-2026-0001", issueDate = "2026-01-01"),
            invoice(number = "FAC-2026-0002", issueDate = "2026-12-31"),
            invoice(number = "FAC-2027-0001", issueDate = "2027-01-01"),
        )

        ExportFormat.entries.forEach { format ->
            val archive = AccountingArchiveBuilder.build(format, exercise, invoices)
            assertEquals(2, archive.documentCount, "Périmètre erroné pour $format")
        }
    }

    /** Les trois formats couvrent exactement les mêmes pièces : sinon deux exports se contrediraient. */
    @Test
    fun everyFormat_coversTheSameDocuments() {
        val invoices = listOf(
            invoice(number = "FAC-2026-0002", issueDate = "2026-05-02"),
            invoice(number = "FAC-2026-0001", issueDate = "2026-05-01"),
        )

        val counts = ExportFormat.entries.map {
            AccountingArchiveBuilder.build(it, exercise, invoices).documentCount
        }

        assertEquals(setOf(2), counts.toSet())
    }

    @Test
    fun theArchive_carriesTheMimeTypeOfItsFormat() {
        ExportFormat.entries.forEach { format ->
            val archive = AccountingArchiveBuilder.build(format, exercise, listOf(invoice()))
            assertEquals(format.mimeType, archive.mimeType)
            assertEquals(format, archive.format)
            assertTrue(
                archive.fileName.endsWith("." + format.fileExtension),
                "Extension inattendue : ${archive.fileName}",
            )
        }
    }

    // ── FEC ─────────────────────────────────────────────────────────────────

    @Test
    fun theFecHeader_namesTheEighteenMandatoryColumns() {
        val header = fecLines(
            AccountingArchiveBuilder.build(ExportFormat.FEC_OFFICIAL, exercise, emptyList()).content,
        ).first()

        assertEquals(
            listOf(
                "JournalCode", "JournalLib", "EcritureNum", "EcritureDate",
                "CompteNum", "CompteLib", "CompAuxNum", "CompAuxLib",
                "PieceRef", "PieceDate", "EcritureLib", "Debit", "Credit",
                "EcritureLet", "DateLet", "ValidDate", "Montantdevise", "Idevise",
            ),
            fecColumns(header),
        )
    }

    /** Un fichier vide se lirait comme un échec d'export, pas comme un exercice sans vente. */
    @Test
    fun anEmptyPeriod_stillProducesTheFecHeader() {
        val archive = AccountingArchiveBuilder.build(ExportFormat.FEC_OFFICIAL, exercise, emptyList())

        assertEquals(1, fecLines(archive.content).size)
        assertEquals(0, archive.documentCount)
    }

    /** Le cœur d'un FEC : chaque écriture s'équilibre, sans quoi le fichier est rejeté. */
    @Test
    fun everyEntry_balancesDebitAgainstCredit() {
        val content = AccountingArchiveBuilder
            .build(ExportFormat.FEC_OFFICIAL, exercise, listOf(invoice()))
            .content
        val rows = fecLines(content).drop(1)

        // 200,00 HT + 40,00 de TVA = 240,00 TTC au débit du compte client.
        assertEquals(3, rows.size)
        assertEquals("240,00", fecColumns(rows[0])[11])
        assertEquals("0,00", fecColumns(rows[0])[12])
        assertEquals("0,00", fecColumns(rows[1])[11])
        assertEquals("200,00", fecColumns(rows[1])[12])
        assertEquals("40,00", fecColumns(rows[2])[12])
    }

    @Test
    fun everyEntry_usesTheSalesJournalAndItsPcgAccounts() {
        val rows = fecLines(
            AccountingArchiveBuilder
                .build(ExportFormat.FEC_OFFICIAL, exercise, listOf(invoice()))
                .content,
        ).drop(1)

        assertTrue(rows.all { fecColumns(it)[0] == "VE" })
        assertEquals("411000", fecColumns(rows[0])[4])
        assertEquals("706000", fecColumns(rows[1])[4])
        assertEquals("445710", fecColumns(rows[2])[4])
        // Le compte auxiliaire n'est renseigné que sur la ligne client — c'est sa raison d'être.
        assertEquals("78410233600021", fecColumns(rows[0])[6])
        assertEquals("", fecColumns(rows[1])[6])
    }

    /**
     * Une ligne de TVA à `0,00` n'est pas neutre : elle affirme une TVA collectée nulle sur un
     * compte qui n'aurait pas dû être mouvementé.
     */
    @Test
    fun aZeroRatedInvoice_carriesNoVatEntry() {
        val rows = fecLines(
            AccountingArchiveBuilder
                .build(
                    ExportFormat.FEC_OFFICIAL,
                    exercise,
                    listOf(invoice(vatRate = VatRate.EXONERE)),
                )
                .content,
        ).drop(1)

        assertEquals(2, rows.size)
        assertFalse(rows.any { fecColumns(it)[4] == "445710" })
    }

    /** Le FEC ne connaît aucune date à tirets, et aucun échappement de tabulation. */
    @Test
    fun fecDates_areCompactAndFieldsCarryNoTab() {
        val rows = fecLines(
            AccountingArchiveBuilder
                .build(
                    ExportFormat.FEC_OFFICIAL,
                    exercise,
                    listOf(invoice(clientName = "Moreau\tSARL")),
                )
                .content,
        ).drop(1)

        assertEquals("20260304", fecColumns(rows[0])[3])
        assertEquals("20260304", fecColumns(rows[0])[9])
        assertEquals(18, fecColumns(rows[0]).size)
        assertEquals("Moreau SARL", fecColumns(rows[0])[7])
    }

    /** Les trois lignes d'une même facture portent le même numéro d'écriture. */
    @Test
    fun theEntriesOfOneInvoice_shareTheirEntryNumber() {
        val rows = fecLines(
            AccountingArchiveBuilder
                .build(
                    ExportFormat.FEC_OFFICIAL,
                    exercise,
                    listOf(invoice(number = "FAC-A"), invoice(number = "FAC-B", issueDate = "2026-04-04")),
                )
                .content,
        ).drop(1)

        assertEquals(setOf("00001"), rows.take(3).map { fecColumns(it)[2] }.toSet())
        assertEquals(setOf("00002"), rows.drop(3).map { fecColumns(it)[2] }.toSet())
    }

    /** Nomenclature officielle attendue par l'administration : `<SIREN>FEC<AAAAMMJJ>.txt`. */
    @Test
    fun theFecFileName_followsTheOfficialNaming() {
        val archive = AccountingArchiveBuilder.build(ExportFormat.FEC_OFFICIAL, exercise, listOf(invoice()))

        assertEquals("820329331FEC20261231.txt", archive.fileName)
    }

    @Test
    fun withoutAnyInvoice_theFecFileNameFallsBackOnANeutralSiren() {
        val archive = AccountingArchiveBuilder.build(ExportFormat.FEC_OFFICIAL, exercise, emptyList())

        assertEquals("000000000FEC20261231.txt", archive.fileName)
    }

    // ── Archive Factur-X ────────────────────────────────────────────────────

    @Test
    fun theFacturXArchive_wrapsOneDocumentPerInvoice() {
        val content = AccountingArchiveBuilder
            .build(
                ExportFormat.FACTURX_ARCHIVE,
                exercise,
                listOf(invoice(number = "FAC-2026-0401")),
            )
            .content

        assertTrue(content.startsWith("""<?xml version="1.0" encoding="UTF-8"?>"""))
        assertContains(content, """documentCount="1"""")
        assertContains(content, """number="FAC-2026-0401"""")
        assertContains(content, """fileName="FAC-2026-0401/factur-x.xml"""")
        assertContains(content, "rsm:CrossIndustryInvoice")
        assertTrue(content.trimEnd().endsWith("</FacturXArchive>"))
    }

    /** Un document XML ne porte qu'une déclaration, en tête : celles des pièces sont retirées. */
    @Test
    fun theFacturXArchive_holdsASingleXmlDeclaration() {
        val content = AccountingArchiveBuilder
            .build(
                ExportFormat.FACTURX_ARCHIVE,
                exercise,
                listOf(invoice(number = "FAC-A"), invoice(number = "FAC-B")),
            )
            .content

        assertEquals(1, Regex("""<\?xml""").findAll(content).count())
    }

    // ── Synthèse Excel ──────────────────────────────────────────────────────

    /** La synthèse ne réimplémente rien : c'est le grand livre CSV déjà écrit pour l'US-19. */
    @Test
    fun theExcelSummary_reusesTheLedgerCsvExport() {
        val invoices = listOf(invoice(number = "FAC-2026-0401"))
        val archive = AccountingArchiveBuilder.build(ExportFormat.EXCEL_SUMMARY, exercise, invoices)

        assertEquals(LedgerCsvExport.generate(invoices), archive.content)
        assertEquals("synthese-comptable-2026-01-01_2026-12-31.csv", archive.fileName)
    }

    /** Deux exports de périodes différentes ne doivent pas se recouvrir chez l'utilisateur. */
    @Test
    fun twoDifferentPeriods_produceTwoDifferentFileNames() {
        val first = AccountingArchiveBuilder.build(
            ExportFormat.FACTURX_ARCHIVE,
            ExportPeriod("2026-01-01", "2026-06-30"),
            emptyList(),
        )
        val second = AccountingArchiveBuilder.build(
            ExportFormat.FACTURX_ARCHIVE,
            ExportPeriod("2026-07-01", "2026-12-31"),
            emptyList(),
        )

        assertEquals("archive-facturx-2026-01-01_2026-06-30.xml", first.fileName)
        assertEquals("archive-facturx-2026-07-01_2026-12-31.xml", second.fileName)
    }
}
