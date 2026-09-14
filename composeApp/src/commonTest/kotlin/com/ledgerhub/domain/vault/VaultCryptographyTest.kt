package com.ledgerhub.domain.vault

import com.ledgerhub.domain.audit.fingerprintSha256
import com.ledgerhub.domain.audit.sha256Hex
import com.ledgerhub.domain.invoice.Invoice
import com.ledgerhub.domain.invoice.InvoiceLine
import com.ledgerhub.domain.invoice.InvoiceRepository
import com.ledgerhub.domain.invoice.InvoiceStatus
import com.ledgerhub.domain.invoice.Money
import com.ledgerhub.domain.invoice.Party
import com.ledgerhub.domain.invoice.VatRate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class VaultCryptographyTest {

    private val fakeIssuer = Party(name = "Mon Entreprise SAS", siren = "123456789", siret = "12345678901234")
    private val fakeClient = Party(name = "Client Test", siren = "987654321", siret = "98765432109876")

    private val dummyInvoice = Invoice(
        number = "FACT-2026-0001",
        issueDate = "2026-09-14",
        dueDate = "2026-10-14",
        issuer = fakeIssuer,
        recipient = fakeClient,
        status = InvoiceStatus.DEPOSITED,
        lines = listOf(
            InvoiceLine(label = "Prestation informatique", quantity = 1, unitPriceHt = Money(100000), vatRate = VatRate.TAUX_NORMAL)
        ),
    )

    private class InMemoryVaultRepository : VaultRepository {
        val archives = mutableListOf<DigitalArchive>()
        val auditLogs = mutableListOf<PisteAuditEntry>()

        override suspend fun saveArchive(archive: DigitalArchive): Result<Unit> {
            archives.removeAll { it.id == archive.id }
            archives.add(archive)
            return Result.success(Unit)
        }

        override suspend fun fetchArchives(): Result<List<DigitalArchive>> {
            return Result.success(archives.sortedByDescending { it.sealedAt })
        }

        override suspend fun getArchiveByInvoiceNumber(invoiceNumber: String): Result<DigitalArchive?> {
            return Result.success(archives.firstOrNull { it.invoiceNumber == invoiceNumber })
        }

        override suspend fun countSealedDocuments(): Result<Long> {
            return Result.success(archives.size.toLong())
        }

        override suspend fun appendAuditEntry(
            id: String,
            invoiceNumber: String?,
            action: String,
            details: String,
            timestamp: String,
        ): Result<PisteAuditEntry> {
            val previousChecksum = auditLogs.lastOrNull()?.checksum
            val source = listOf(id, invoiceNumber.orEmpty(), action, timestamp, previousChecksum.orEmpty()).joinToString("|")
            val checksum = sha256Hex(source)
            val entry = PisteAuditEntry(
                id = id,
                invoiceNumber = invoiceNumber,
                action = action,
                details = details,
                timestamp = timestamp,
                previousChecksum = previousChecksum,
                checksum = checksum,
            )
            auditLogs.add(entry)
            return Result.success(entry)
        }

        override suspend fun fetchAuditTrail(): Result<List<PisteAuditEntry>> {
            return Result.success(auditLogs.sortedBy { it.timestamp })
        }

        override suspend fun getLatestAuditEntry(): Result<PisteAuditEntry?> {
            return Result.success(auditLogs.lastOrNull())
        }
    }

    private class FakeInvoiceRepository(var invoices: List<Invoice> = emptyList()) : InvoiceRepository {
        override suspend fun submitInvoice(invoice: Invoice): Result<Unit> = Result.success(Unit)
        override suspend fun fetchInvoices(): Result<List<Invoice>> = Result.success(invoices)
    }

    @Test
    fun generateSeal_createsCanonicalSha256AndAppendsAuditLogWithChainedChecksum() = kotlinx.coroutines.test.runTest {
        val repo = InMemoryVaultRepository()
        val invoiceRepo = FakeInvoiceRepository(listOf(dummyInvoice))
        val generateSealUseCase = GenerateInvoiceSealUseCase(repo, invoiceRepo)

        val result = generateSealUseCase(dummyInvoice.number)
        assertTrue(result.isSuccess)
        val archive = result.getOrThrow()

        assertEquals("FACT-2026-0001", archive.invoiceNumber)
        assertEquals(64, archive.payloadHash.length)
        assertEquals(SealStatus.SEALED, archive.status)

        // Verify audit log chaining
        val auditLogs = repo.fetchAuditTrail().getOrThrow()
        assertEquals(1, auditLogs.size)
        val entry1 = auditLogs[0]
        assertEquals("FACT-2026-0001", entry1.invoiceNumber)
        assertEquals("DOCUMENT_SEALED", entry1.action)
        assertEquals(null, entry1.previousChecksum)

        val expectedChecksum1 = sha256Hex("${entry1.id}|${entry1.invoiceNumber}|${entry1.action}|${entry1.timestamp}|")
        assertEquals(expectedChecksum1, entry1.checksum)
    }

    @Test
    fun auditTrail_chainingMultipleEntries_computesSequentialRecursiveChecksums() = kotlinx.coroutines.test.runTest {
        val repo = InMemoryVaultRepository()
        val invoice1 = dummyInvoice.copy(number = "FACT-2026-0001")
        val invoice2 = dummyInvoice.copy(number = "FACT-2026-0002")
        val invoiceRepo = FakeInvoiceRepository(listOf(invoice1, invoice2))
        val generateSealUseCase = GenerateInvoiceSealUseCase(repo, invoiceRepo)

        generateSealUseCase(invoice1.number, "2026-09-14T10:00:00Z")
        generateSealUseCase(invoice2.number, "2026-09-14T10:05:00Z")

        val auditLogs = repo.fetchAuditTrail().getOrThrow()
        assertEquals(2, auditLogs.size)

        val entry1 = auditLogs[0]
        val entry2 = auditLogs[1]

        assertEquals(null, entry1.previousChecksum)
        assertEquals(entry1.checksum, entry2.previousChecksum)

        val expectedChecksum2 = sha256Hex("${entry2.id}|${entry2.invoiceNumber}|${entry2.action}|${entry2.timestamp}|${entry1.checksum}")
        assertEquals(expectedChecksum2, entry2.checksum)
    }

    @Test
    fun verifyVaultIntegrity_detectsTamperingInArchiveDocument() = kotlinx.coroutines.test.runTest {
        val repo = InMemoryVaultRepository()
        val invoiceRepo = FakeInvoiceRepository(listOf(dummyInvoice))
        val generateSealUseCase = GenerateInvoiceSealUseCase(repo, invoiceRepo)
        val verifyIntegrityUseCase = VerifyVaultIntegrityUseCase(repo, invoiceRepo)

        generateSealUseCase(dummyInvoice.number)

        // 1. Initial state is 100% valid
        val initialReport = verifyIntegrityUseCase().getOrThrow()
        assertTrue(initialReport.isCompletelyValid)
        assertTrue(initialReport.isAuditChainValid)
        assertEquals(1, initialReport.verifiedDocuments)
        assertTrue(initialReport.corruptedDocuments.isEmpty())

        // 2. Tamper with archive payload
        val originalArchive = repo.archives.first()
        val tamperedArchive = originalArchive.copy(payloadHash = "corrupted_hash_fake_value")
        repo.archives[0] = tamperedArchive

        val reportAfterTamper = verifyIntegrityUseCase().getOrThrow()
        assertFalse(reportAfterTamper.isCompletelyValid)
        assertEquals(1, reportAfterTamper.corruptedDocuments.size)
        assertEquals("FACT-2026-0001", reportAfterTamper.corruptedDocuments[0])
    }

    @Test
    fun verifyVaultIntegrity_detectsTamperingInAuditChain() = kotlinx.coroutines.test.runTest {
        val repo = InMemoryVaultRepository()
        val invoice1 = dummyInvoice.copy(number = "FACT-2026-0001")
        val invoice2 = dummyInvoice.copy(number = "FACT-2026-0002")
        val invoiceRepo = FakeInvoiceRepository(listOf(invoice1, invoice2))
        val generateSealUseCase = GenerateInvoiceSealUseCase(repo, invoiceRepo)
        val verifyIntegrityUseCase = VerifyVaultIntegrityUseCase(repo, invoiceRepo)

        generateSealUseCase(invoice1.number, "2026-09-14T10:00:00Z")
        generateSealUseCase(invoice2.number, "2026-09-14T10:05:00Z")

        // Tamper with audit log entry 1 checksum
        val originalEntry1 = repo.auditLogs[0]
        repo.auditLogs[0] = originalEntry1.copy(checksum = "corrupted_checksum_fake_hash")

        val report = verifyIntegrityUseCase().getOrThrow()
        assertFalse(report.isCompletelyValid)
        assertFalse(report.isAuditChainValid)
    }
}
